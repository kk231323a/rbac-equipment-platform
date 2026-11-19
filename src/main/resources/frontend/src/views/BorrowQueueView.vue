<script setup>
import { ref, watch } from 'vue'
import { borrowApi } from '../api.js'
import ListPanel from '../components/ListPanel.vue'
import StatusTag from '../components/StatusTag.vue'
import { useAction } from '../composables/useAction.js'
import { useList } from '../composables/useList.js'
import { formatTime } from '../display.js'
import { can, permission } from '../session.js'

const props = defineProps({ mode: { type: String, required: true } })
const pending = props.mode === 'pending'
const { state, reload, goTo } = useList(pending ? borrowApi.pending : borrowApi.received, 20)
const { action, run } = useAction(reload)
const approval = ref(null)
const dueTime = ref('')
const formError = ref('')

watch([() => can(permission.borrowApprove), () => action.active], ([allowed, active]) => {
  if (!allowed && active !== 'approve') approval.value = null
})

function submitWithPermission(code, submit) {
  if (!can(code)) {
    const error = new Error('权限不足')
    error.status = 403
    throw error
  }
  return submit()
}

function openApproval(row) {
  if (!can(permission.borrowApprove)) return
  approval.value = row
  dueTime.value = ''
  formError.value = ''
}

function closeApproval() {
  if (action.active !== 'approve') approval.value = null
}

function approve() {
  if (action.active !== null || !approval.value || !can(permission.borrowApprove)) return
  formError.value = ''
  if (!dueTime.value) {
    formError.value = '请选择应还时间'
    return
  }
  const requestId = approval.value.requestId
  void run('approve', () => submitWithPermission(permission.borrowApprove, () => borrowApi.approve(requestId, dueTime.value)), {
    success: '申请已批准',
    onSuccess: () => { approval.value = null },
    onError: (message, status) => {
      if (status === 409) approval.value = null
      else formError.value = message
    },
  })
}

function reject(row) {
  if (!can(permission.borrowApprove)) return
  void run(`reject-${row.requestId}`, () => submitWithPermission(permission.borrowApprove, () => borrowApi.reject(row.requestId)), {
    confirmation: `拒绝借用申请 #${row.requestId} 吗？`,
    success: '申请已拒绝',
  })
}

function confirmReturn(row) {
  if (!can(permission.borrowReturn)) return
  void run(`return-${row.requestId}`, () => submitWithPermission(permission.borrowReturn, () => borrowApi.return(row.requestId)), {
    confirmation: `确认已收到申请 #${row.requestId} 归还的设备吗？`,
    success: '已确认归还',
  })
}
</script>

<template>
  <section>
    <header class="page-heading">
      <div>
        <h1>{{ pending ? '待审批' : '归还确认' }}</h1>
        <p>{{ pending ? '批准时需填写应还时间。' : '收到设备后，由有归还权限的账号确认归还。' }}</p>
      </div>
    </header>

    <ListPanel :state="state" :empty-text="pending ? '暂无待审批申请' : '暂无已领用申请'" @retry="reload" @page="goTo">
      <table class="data-table">
        <thead>
          <tr>
            <th scope="col">申请 ID</th>
            <th scope="col">设备</th>
            <th scope="col">借用人</th>
            <th v-if="pending" scope="col">设备状态</th>
            <th scope="col">{{ pending ? '申请时间' : '领用时间' }}</th>
            <th v-if="!pending" scope="col">应还时间</th>
            <th scope="col">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in state.rows" :key="row.requestId">
            <td class="identifier">#{{ row.requestId }}</td>
            <td>
              <span class="cell-primary">{{ row.deviceName || '—' }}</span>
              <small v-if="row.deviceNo" class="cell-secondary identifier">{{ row.deviceNo }}</small>
            </td>
            <td>{{ row.borrowerName || row.borrowerId }}</td>
            <td v-if="pending"><StatusTag :status="row.deviceStatus" /></td>
            <td class="time-cell">{{ formatTime(pending ? row.createTime : row.borrowTime) }}</td>
            <td v-if="!pending" class="time-cell">{{ formatTime(row.dueTime) }}</td>
            <td>
              <div v-if="pending" class="row-actions">
                <el-button v-if="can(permission.borrowApprove)" link type="primary" :disabled="action.active !== null" @click="openApproval(row)">批准</el-button>
                <el-button
                  v-if="can(permission.borrowApprove)"
                  link
                  type="danger"
                  :loading="action.active === `reject-${row.requestId}`"
                  :disabled="action.active !== null"
                  @click="reject(row)"
                >
                  拒绝
                </el-button>
              </div>
              <el-button
                v-else-if="can(permission.borrowReturn)"
                link
                type="primary"
                :loading="action.active === `return-${row.requestId}`"
                :disabled="action.active !== null"
                @click="confirmReturn(row)"
              >
                确认归还
              </el-button>
            </td>
          </tr>
        </tbody>
      </table>
    </ListPanel>

    <el-dialog
      v-if="pending"
      :model-value="Boolean(approval)"
      title="批准借用申请"
      width="min(480px, calc(100vw - 32px))"
      :close-on-click-modal="action.active !== 'approve'"
      :close-on-press-escape="action.active !== 'approve'"
      :show-close="action.active !== 'approve'"
      @update:model-value="open => { if (!open) closeApproval() }"
    >
      <form v-if="approval" id="approval-form" class="dialog-form" @submit.prevent="approve">
        <dl class="detail-grid readonly-fields">
          <div><dt>申请 ID</dt><dd class="identifier">#{{ approval.requestId }}</dd></div>
          <div><dt>设备</dt><dd>{{ approval.deviceName || approval.deviceNo || approval.deviceId }}</dd></div>
        </dl>
        <div class="field">
          <label for="approval-due-time">应还时间</label>
          <el-date-picker
            id="approval-due-time"
            v-model="dueTime"
            type="datetime"
            format="YYYY-MM-DD HH:mm:ss"
            value-format="YYYY-MM-DD[T]HH:mm:ss"
            placeholder="选择应还日期和时间"
            :disabled="action.active === 'approve' || !can(permission.borrowApprove)"
          />
        </div>
        <p v-if="formError" class="form-error" role="alert">{{ formError }}</p>
      </form>

      <template #footer>
        <el-button :disabled="action.active === 'approve'" @click="closeApproval">取消</el-button>
        <el-button
          v-if="can(permission.borrowApprove)"
          type="primary"
          native-type="submit"
          form="approval-form"
          :loading="action.active === 'approve'"
        >
          批准
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>
