<script setup>
import { ref } from 'vue'
import { auditApi } from '../api.js'
import ListPanel from '../components/ListPanel.vue'
import StatusTag from '../components/StatusTag.vue'
import { useList } from '../composables/useList.js'
import { formatTime } from '../display.js'

const { state, reload, goTo } = useList(auditApi.list, 10)
const expandedId = ref(null)

const resourceNames = { equipment: '设备', borrow_request: '借用申请', sys_user: '用户' }
const operatorNames = { system: '系统任务', anonymous: '匿名请求' }
const actionNames = {
  'auth:login': '登录',
  'auth:logout': '退出登录',
  'equipment:create': '新增设备',
  'equipment:update': '更新设备',
  'borrow:create': '提交借用申请',
  'borrow:approved': '批准借用',
  'borrow:reject': '拒绝借用',
  'borrow:receive': '确认领用',
  'borrow:return': '确认归还',
  'borrow:cancel': '取消申请',
  'borrow:cancel-expired': '取消超期未领用申请',
  'user:enable': '启用用户',
  'user:disable': '禁用用户',
  'user:role:assign': '分配角色',
  'user:role:revoke': '撤销角色',
}

function toggleDetails(id) {
  expandedId.value = expandedId.value === id ? null : id
}

function formatDetails(details) {
  if (!details) return '—'
  try {
    return JSON.stringify(JSON.parse(details), null, 2)
  } catch {
    return details
  }
}
</script>

<template>
  <section>
    <header class="page-heading">
      <div>
        <h1>审计日志</h1>
      </div>
    </header>

    <ListPanel :state="state" empty-text="暂无审计日志" @retry="reload" @page="goTo">
      <table class="data-table">
        <thead>
          <tr>
            <th scope="col">操作人</th>
            <th scope="col">动作</th>
            <th scope="col">目标资源</th>
            <th scope="col">结果</th>
            <th scope="col">操作时间</th>
            <th scope="col">明细</th>
          </tr>
        </thead>
        <tbody>
          <template v-for="row in state.rows" :key="row.id">
            <tr>
              <td>
                <span class="cell-primary">{{ row.operatorAccount || operatorNames[row.operatorType] || '—' }}</span>
                <small v-if="row.operatorId != null" class="cell-secondary identifier">#{{ row.operatorId }}</small>
              </td>
              <td>{{ actionNames[row.actionCode] || row.actionCode || '—' }}</td>
              <td>
                {{ resourceNames[row.resourceType] || row.resourceType || '—' }}
                <span v-if="row.resourceId != null" class="identifier">#{{ row.resourceId }}</span>
              </td>
              <td><StatusTag :status="row.result" /></td>
              <td class="time-cell">{{ formatTime(row.createTime) }}</td>
              <td>
                <el-button
                  link
                  type="primary"
                  :aria-expanded="expandedId === row.id"
                  :aria-controls="`audit-details-${row.id}`"
                  @click="toggleDetails(row.id)"
                >
                  {{ expandedId === row.id ? '收起' : '展开' }}
                </el-button>
              </td>
            </tr>
            <tr v-if="expandedId === row.id" :id="`audit-details-${row.id}`" class="audit-details">
              <td colspan="6">
                <dl class="detail-grid">
                  <div><dt>动作码</dt><dd class="identifier">{{ row.actionCode || '—' }}</dd></div>
                  <div><dt>请求路径</dt><dd class="identifier">{{ row.requestPath || '—' }}</dd></div>
                  <div v-if="row.errorMessage || row.errorCode != null">
                    <dt>错误信息</dt>
                    <dd>{{ row.errorMessage || '—' }} <span v-if="row.errorCode != null">（{{ row.errorCode }}）</span></dd>
                  </div>
                  <div class="detail-wide"><dt>操作明细</dt><dd><pre>{{ formatDetails(row.details) }}</pre></dd></div>
                </dl>
              </td>
            </tr>
          </template>
        </tbody>
      </table>
    </ListPanel>
  </section>
</template>
