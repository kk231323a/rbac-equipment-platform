<script setup>
import { computed, reactive, ref } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { borrowApi, equipmentApi } from '../api.js'
import ListPanel from '../components/ListPanel.vue'
import StatusTag from '../components/StatusTag.vue'
import { useAction } from '../composables/useAction.js'
import { useList } from '../composables/useList.js'
import { equipmentStatuses, formatTime } from '../display.js'
import { can, permission } from '../session.js'

const status = ref('')
const { state, reload, goTo } = useList((page) => equipmentApi.list({ ...page, status: status.value }), 20)
const { state: myRequests, reload: reloadMyRequests } = useList(borrowApi.mine)
const pendingByDevice = computed(() => new Map(
  myRequests.rows
    .filter((request) => request.status === 'pending')
    .map((request) => [request.deviceId, request]),
))
const requestsReady = computed(() => !myRequests.loading && !myRequests.error)

async function reloadAll() {
  await Promise.all([reload(), reloadMyRequests()])
}

const { action, run } = useAction(reloadAll)
const dialog = ref('')
const selected = ref(null)
const detail = ref(null)
const detailLoading = ref(false)
const detailError = ref('')
let detailRequest = 0
const form = reactive({ deviceNo: '', name: '', model: '' })
const formError = ref('')

function closeDialog() {
  if (action.active === 'save') return
  dialog.value = ''
  detailRequest += 1
}

function showDetail(row) {
  selected.value = row
  dialog.value = 'detail'
  void loadDetail()
}

async function loadDetail() {
  const currentRequest = ++detailRequest
  detail.value = null
  detailError.value = ''
  detailLoading.value = true
  try {
    const data = await equipmentApi.detail(selected.value.id)
    if (currentRequest === detailRequest) detail.value = data
  } catch (error) {
    if (currentRequest === detailRequest) detailError.value = error.message
  } finally {
    if (currentRequest === detailRequest) detailLoading.value = false
  }
}

function showEditor(row = null) {
  selected.value = row
  form.deviceNo = row?.deviceNo ?? ''
  form.name = row?.name ?? ''
  form.model = row?.model ?? ''
  formError.value = ''
  dialog.value = row ? 'edit' : 'create'
}

function saveEquipment() {
  if (action.active !== null) return
  formError.value = ''
  const creating = dialog.value === 'create'
  if (!can(creating ? permission.equipmentCreate : permission.equipmentUpdate)) {
    formError.value = '权限不足'
    return
  }

  const data = { deviceNo: form.deviceNo.trim(), name: form.name.trim(), model: form.model.trim() }
  if (!data.name || (creating && !data.deviceNo)) {
    formError.value = creating ? '请填写设备编号和名称' : '请填写设备名称'
    return
  }

  const submit = creating ? () => equipmentApi.create(data) : () => equipmentApi.update(selected.value.id, data)
  void run('save', submit, {
    success: creating ? '设备已新增' : '设备已更新',
    onSuccess: () => { dialog.value = '' },
    onError: (message) => { formError.value = message },
  })
}

function requestBorrow(row) {
  void run(`borrow-${row.id}`, () => borrowApi.create(row.id), {
    confirmation: `申请借用「${row.name}」吗？`,
    success: '借用申请已提交',
  })
}
</script>

<template>
  <section>
    <header class="page-heading">
      <div>
        <h1>设备列表</h1>
      </div>
      <el-button v-if="can(permission.equipmentCreate)" type="primary" :icon="Plus" :disabled="action.active !== null" @click="showEditor()">
        新增设备
      </el-button>
    </header>

    <div v-if="myRequests.error" class="warning-banner" role="alert">
      <span>读取我的借用申请失败：{{ myRequests.error }}。为避免重复申请，暂时无法发起借用。</span>
      <el-button :loading="myRequests.loading" @click="reloadMyRequests">重试</el-button>
    </div>

    <div class="toolbar">
      <label for="equipment-status">设备状态</label>
      <el-select id="equipment-status" v-model="status" class="status-filter" placeholder="全部状态" @change="goTo(1)">
        <el-option value="" label="全部状态" />
        <el-option v-for="option in equipmentStatuses" :key="option.value" v-bind="option" />
      </el-select>
    </div>

    <ListPanel :state="state" empty-text="暂无符合条件的设备" @retry="reload" @page="goTo">
      <table class="data-table">
        <thead>
          <tr>
            <th scope="col">设备编号</th>
            <th scope="col">设备名称</th>
            <th scope="col">型号</th>
            <th scope="col">状态</th>
            <th scope="col">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in state.rows" :key="row.id">
            <td class="identifier">{{ row.deviceNo }}</td>
            <td class="cell-primary">{{ row.name }}</td>
            <td>{{ row.model || '—' }}</td>
            <td><StatusTag :status="row.status" /></td>
            <td>
              <div class="row-actions">
                <el-button link type="primary" @click="showDetail(row)">详情</el-button>
                <el-button v-if="can(permission.equipmentUpdate)" link type="primary" :disabled="action.active !== null" @click="showEditor(row)">
                  编辑
                </el-button>
                <el-button
                  v-if="can(permission.borrowCreate) && row.status === 'available'"
                  link
                  type="primary"
                  :loading="action.active === `borrow-${row.id}`"
                  :disabled="action.active !== null || !requestsReady || pendingByDevice.has(row.id)"
                  @click="requestBorrow(row)"
                >
                  {{ pendingByDevice.has(row.id) ? '已申请，待审批' : '申请借用' }}
                </el-button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </ListPanel>

    <el-dialog
      :model-value="Boolean(dialog)"
      :title="dialog === 'detail' ? '设备详情' : dialog === 'edit' ? '编辑设备' : '新增设备'"
      width="min(540px, calc(100vw - 32px))"
      :close-on-click-modal="action.active !== 'save'"
      :close-on-press-escape="action.active !== 'save'"
      :show-close="action.active !== 'save'"
      @update:model-value="open => { if (!open) closeDialog() }"
    >
      <template v-if="dialog === 'detail'">
        <div v-if="detailLoading" class="dialog-state" role="status">正在加载设备详情…</div>
        <div v-else-if="detailError" class="dialog-state" role="alert">
          <p>{{ detailError }}</p>
          <el-button @click="loadDetail">重试</el-button>
        </div>
        <dl v-else-if="detail" class="detail-grid">
          <div><dt>设备编号</dt><dd class="identifier">{{ detail.deviceNo }}</dd></div>
          <div><dt>设备名称</dt><dd>{{ detail.name }}</dd></div>
          <div><dt>型号</dt><dd>{{ detail.model || '—' }}</dd></div>
          <div><dt>状态</dt><dd><StatusTag :status="detail.status" /></dd></div>
          <div><dt>创建时间</dt><dd>{{ formatTime(detail.createTime) }}</dd></div>
          <div><dt>更新时间</dt><dd>{{ formatTime(detail.updateTime) }}</dd></div>
        </dl>
      </template>

      <form v-else-if="dialog" id="equipment-form" class="dialog-form" @submit.prevent="saveEquipment">
        <dl v-if="dialog === 'edit'" class="detail-grid readonly-fields">
          <div><dt>设备编号</dt><dd class="identifier">{{ selected.deviceNo }}</dd></div>
          <div><dt>状态</dt><dd><StatusTag :status="selected.status" /></dd></div>
        </dl>
        <div v-else class="field">
          <label for="device-number">设备编号</label>
          <el-input id="device-number" v-model="form.deviceNo" maxlength="32" required :disabled="action.active === 'save'" />
        </div>
        <div class="field">
          <label for="device-name">设备名称</label>
          <el-input id="device-name" v-model="form.name" maxlength="50" required :disabled="action.active === 'save'" />
        </div>
        <div class="field">
          <label for="device-model">型号 <span class="field-optional">选填</span></label>
          <el-input id="device-model" v-model="form.model" maxlength="30" :disabled="action.active === 'save'" />
        </div>
        <p v-if="formError" class="form-error" role="alert">{{ formError }}</p>
      </form>

      <template #footer>
        <el-button :disabled="action.active === 'save'" @click="closeDialog">{{ dialog === 'detail' ? '关闭' : '取消' }}</el-button>
        <el-button
          v-if="dialog && dialog !== 'detail'"
          type="primary"
          native-type="submit"
          form="equipment-form"
          :loading="action.active === 'save'"
        >
          保存
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>
