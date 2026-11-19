<script setup>
import { borrowApi } from '../api.js'
import ListPanel from '../components/ListPanel.vue'
import StatusTag from '../components/StatusTag.vue'
import { useAction } from '../composables/useAction.js'
import { useList } from '../composables/useList.js'
import { formatTime } from '../display.js'
import { can, permission } from '../session.js'

const { state, reload } = useList(borrowApi.mine)
const { action, run } = useAction(reload)

function cancel(row) {
  void run(`cancel-${row.id}`, () => borrowApi.cancel(row.id), {
    confirmation: `取消借用申请 #${row.id} 吗？`,
    success: '申请已取消',
  })
}

function receive(row) {
  void run(`receive-${row.id}`, () => borrowApi.receive(row.id), {
    confirmation: `确认已领取申请 #${row.id} 对应的设备吗？`,
    success: '已确认领用',
  })
}
</script>

<template>
  <section>
    <header class="page-heading">
      <div>
        <h1>我的借用申请</h1>
      </div>
    </header>

    <ListPanel :state="state" empty-text="暂无借用申请" @retry="reload">
      <table class="data-table">
        <thead>
          <tr>
            <th scope="col">申请 ID</th>
            <th scope="col">设备 ID</th>
            <th scope="col">申请状态</th>
            <th scope="col">申请时间</th>
            <th scope="col">应还时间</th>
            <th scope="col">领用时间</th>
            <th scope="col">归还时间</th>
            <th scope="col">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in state.rows" :key="row.id">
            <td class="identifier">#{{ row.id }}</td>
            <td class="identifier">{{ row.deviceId }}</td>
            <td><StatusTag :status="row.status" /></td>
            <td class="time-cell">{{ formatTime(row.createTime) }}</td>
            <td class="time-cell">{{ formatTime(row.dueTime) }}</td>
            <td class="time-cell">{{ formatTime(row.borrowTime) }}</td>
            <td class="time-cell">{{ formatTime(row.returnTime) }}</td>
            <td>
              <div class="row-actions">
                <el-button
                  v-if="row.status === 'approved'"
                  type="primary"
                  link
                  :loading="action.active === `receive-${row.id}`"
                  :disabled="action.active !== null"
                  @click="receive(row)"
                >
                  确认领用
                </el-button>
                <el-button
                  v-if="can(permission.borrowCreate) && ['pending', 'approved'].includes(row.status)"
                  type="danger"
                  link
                  :loading="action.active === `cancel-${row.id}`"
                  :disabled="action.active !== null"
                  @click="cancel(row)"
                >
                  取消申请
                </el-button>
                <span v-if="row.status !== 'approved' && (row.status !== 'pending' || !can(permission.borrowCreate))" class="muted">—</span>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </ListPanel>
  </section>
</template>
