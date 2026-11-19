<script setup>
import { computed, reactive, ref } from 'vue'
import { refreshPermissions, userApi } from '../api.js'
import ListPanel from '../components/ListPanel.vue'
import StatusTag from '../components/StatusTag.vue'
import { useAction } from '../composables/useAction.js'
import { useList } from '../composables/useList.js'
import { formatTime } from '../display.js'
import { can, permission, session } from '../session.js'

const { state, reload, goTo } = useList(userApi.list, 10)
const { action, run } = useAction(async () => {
  await Promise.all([reload(), loadRoles()])
  if (selectedUser.value?.id === session.user?.id) {
    void refreshPermissions().catch(() => {})
  }
})

const selectedUser = ref(null)
const roleId = ref()
const roles = reactive({ assigned: [], loading: false, error: '' })
let roleRequest = 0

const validRoleId = computed(() => {
  const id = Number(roleId.value)
  return Number.isInteger(id) && id > 0 && id <= 2147483647
})

function openRoles(user) {
  selectedUser.value = user
  roleId.value = undefined
  roles.assigned = []
  void loadRoles()
}

function closeRoles() {
  if (action.active !== null) return
  selectedUser.value = null
  roleRequest += 1
}

async function loadRoles() {
  if (!selectedUser.value) return
  const userId = selectedUser.value.id
  const currentRequest = ++roleRequest
  roles.loading = true
  roles.error = ''

  try {
    const assigned = await userApi.roles(userId)
    if (currentRequest !== roleRequest) return
    if (!Array.isArray(assigned)) throw new Error('用户角色响应格式异常')
    roles.assigned = assigned
  } catch (error) {
    if (currentRequest === roleRequest) roles.error = error.message
  } finally {
    if (currentRequest === roleRequest) roles.loading = false
  }
}

function changeEnabled(user) {
  const enabling = !user.enable
  const verb = enabling ? '启用' : '禁用'
  void run(`enable-${user.id}`, () => enabling ? userApi.enable(user.id) : userApi.disable(user.id), {
    confirmation: `确认${verb}账号「${user.account}」吗？`,
    success: `账号已${verb}`,
  })
}

function assignRole() {
  if (!selectedUser.value || !validRoleId.value) return
  const userId = selectedUser.value.id
  const id = Number(roleId.value)
  void run('assign', () => userApi.assign(userId, id), {
    success: '角色已分配',
    onSuccess: () => { roleId.value = undefined },
  })
}

function revokeRole(role) {
  const userId = selectedUser.value.id
  void run(`revoke-${role.id}`, () => userApi.revoke(userId, role.id), {
    confirmation: `撤销「${selectedUser.value.account}」的「${role.roleName}」角色吗？`,
    success: '角色已撤销',
  })
}
</script>

<template>
  <section>
    <header class="page-heading">
      <div>
        <h1>用户与角色</h1>
      </div>
    </header>

    <ListPanel :state="state" empty-text="暂无用户" @retry="reload" @page="goTo">
      <table class="data-table">
        <thead>
          <tr>
            <th scope="col">用户 ID</th>
            <th scope="col">账号</th>
            <th scope="col">名称</th>
            <th scope="col">状态</th>
            <th scope="col">创建时间</th>
            <th scope="col">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in state.rows" :key="row.id">
            <td class="identifier">#{{ row.id }}</td>
            <td>
              <span class="cell-primary">{{ row.account }}</span>
              <small v-if="row.id === session.user?.id" class="cell-secondary">当前账号</small>
            </td>
            <td>{{ row.name || '—' }}</td>
            <td><StatusTag :status="row.enable ? 'enabled' : 'disabled'" /></td>
            <td class="time-cell">{{ formatTime(row.createTime) }}</td>
            <td>
              <div class="row-actions">
                <el-button link type="primary" :disabled="action.active !== null" @click="openRoles(row)">查看角色</el-button>
                <el-button
                  v-if="row.id !== session.user?.id && can(row.enable ? permission.userDisable : permission.userEnable)"
                  link
                  :type="row.enable ? 'danger' : 'primary'"
                  :loading="action.active === `enable-${row.id}`"
                  :disabled="action.active !== null"
                  @click="changeEnabled(row)"
                >
                  {{ row.enable ? '禁用' : '启用' }}
                </el-button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </ListPanel>

    <el-dialog
      :model-value="Boolean(selectedUser)"
      :title="selectedUser ? `${selectedUser.account} 的角色` : '用户角色'"
      width="min(530px, calc(100vw - 32px))"
      :close-on-click-modal="action.active === null"
      :close-on-press-escape="action.active === null"
      :show-close="action.active === null"
      @update:model-value="open => { if (!open) closeRoles() }"
    >
      <div v-if="roles.loading" class="dialog-state" role="status">正在加载角色…</div>
      <div v-else-if="roles.error" class="dialog-state" role="alert">
        <p>{{ roles.error }}</p>
        <el-button @click="loadRoles">重试</el-button>
      </div>
      <template v-else-if="selectedUser">
        <h2 class="dialog-subheading">已有角色</h2>
        <p v-if="roles.assigned.length === 0" class="muted">该用户暂无角色。</p>
        <ul v-else class="role-list">
          <li v-for="role in roles.assigned" :key="role.id">
            <div>
              <strong>{{ role.roleName }}</strong>
              <span class="identifier muted">{{ role.roleCode }}</span>
              <small class="cell-secondary identifier">角色 ID：{{ role.id }}</small>
            </div>
            <el-button
              v-if="can(permission.roleRevoke) && selectedUser.id !== session.user?.id"
              link
              type="danger"
              :disabled="action.active !== null"
              :loading="action.active === `revoke-${role.id}`"
              @click="revokeRole(role)"
            >
              撤销
            </el-button>
          </li>
        </ul>

        <div v-if="can(permission.roleAssign)" class="role-assignment">
          <h2 class="dialog-subheading">分配角色</h2>
          <p id="role-id-help" class="muted">输入已有角色的真实 ID，角色是否存在由后端校验。</p>
          <form class="role-assign-form" @submit.prevent="assignRole">
            <el-input
              v-model="roleId"
              type="number"
              min="1"
              max="2147483647"
              step="1"
              aria-label="已有角色 ID"
              aria-describedby="role-id-help"
              placeholder="输入已有角色 ID"
              :disabled="action.active !== null"
            />
            <el-button type="primary" native-type="submit" :disabled="!validRoleId || action.active !== null" :loading="action.active === 'assign'">
              分配
            </el-button>
          </form>
        </div>
      </template>

      <template #footer>
        <el-button :disabled="action.active !== null" @click="closeRoles">关闭</el-button>
      </template>
    </el-dialog>
  </section>
</template>
