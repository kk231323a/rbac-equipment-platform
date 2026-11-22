<script setup>
import { computed, reactive, ref } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { refreshPermissions, roleApi, userApi } from '../api.js'
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
const roleDirectory = reactive({ items: [], loading: false, error: '' })
const createDialog = ref(false)
const createForm = reactive({ account: '', name: '', password: '' })
const createError = ref('')
let roleRequest = 0
let roleDirectoryRequest = 0

const availableRoles = computed(() => roleDirectory.items.filter((role) =>
  !roles.assigned.some((assigned) => assigned.id === role.id)))
const validRoleId = computed(() => availableRoles.value.some((role) => role.id === roleId.value))

function openRoles(user) {
  selectedUser.value = user
  roleId.value = undefined
  roles.assigned = []
  roleDirectory.items = []
  roleDirectory.error = ''
  void loadRoles()
  if (can(permission.roleAssign)) void loadRoleDirectory()
}

function closeRoles() {
  if (action.active !== null) return
  selectedUser.value = null
  roleRequest += 1
  roleDirectoryRequest += 1
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

async function loadRoleDirectory() {
  const currentRequest = ++roleDirectoryRequest
  roleDirectory.loading = true
  roleDirectory.error = ''

  try {
    const directory = await roleApi.list()
    if (currentRequest !== roleDirectoryRequest) return
    if (!Array.isArray(directory)) throw new Error('角色目录响应格式异常')
    roleDirectory.items = directory
  } catch (error) {
    if (currentRequest === roleDirectoryRequest) roleDirectory.error = error.message
  } finally {
    if (currentRequest === roleDirectoryRequest) roleDirectory.loading = false
  }
}

function resetCreateForm() {
  createForm.account = ''
  createForm.name = ''
  createForm.password = ''
  createError.value = ''
}

function openCreate() {
  resetCreateForm()
  createDialog.value = true
}

function closeCreate() {
  if (action.active === 'create') return
  createDialog.value = false
  createError.value = ''
}

function createUser() {
  if (action.active !== null) return
  createError.value = ''
  if (!can(permission.userCreate)) {
    createError.value = '权限不足'
    return
  }

  const data = {
    account: createForm.account,
    name: createForm.name,
    password: createForm.password,
  }
  if (!/^[A-Za-z0-9_]{3,50}$/.test(data.account)) {
    createError.value = '账号应为3到50位字母、数字或下划线'
    return
  }
  if (!data.name.trim()) {
    createError.value = '姓名不能为空'
    return
  }
  if (data.name.length > 50) {
    createError.value = '姓名不能超过50个字符'
    return
  }
  if (!data.password.trim()) {
    createError.value = '初始密码不能为空'
    return
  }
  if (data.password.length < 6 || data.password.length > 72) {
    createError.value = '密码长度应为6到72个字符'
    return
  }
  if (new TextEncoder().encode(data.password).length > 72) {
    createError.value = '密码不能超过72个UTF-8字节'
    return
  }

  void run('create', () => userApi.create(data), {
    success: '用户已创建',
    onSuccess: () => {
      resetCreateForm()
      createDialog.value = false
    },
    onError: (message) => { createError.value = message },
  })
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
      <el-button v-if="can(permission.userCreate)" type="primary" :icon="Plus" :disabled="action.active !== null" @click="openCreate">
        创建用户
      </el-button>
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
      :model-value="createDialog"
      title="创建用户"
      width="min(530px, calc(100vw - 32px))"
      :close-on-click-modal="action.active !== 'create'"
      :close-on-press-escape="action.active !== 'create'"
      :show-close="action.active !== 'create'"
      @update:model-value="open => { if (!open) closeCreate() }"
    >
      <form id="create-user-form" class="dialog-form" autocomplete="off" @submit.prevent="createUser">
        <div class="field">
          <label for="create-user-account">账号</label>
          <el-input
            id="create-user-account"
            v-model="createForm.account"
            maxlength="50"
            required
            autocomplete="off"
            placeholder="3–50 位字母、数字或下划线"
            :disabled="action.active === 'create'"
          />
        </div>
        <div class="field">
          <label for="create-user-name">姓名</label>
          <el-input
            id="create-user-name"
            v-model="createForm.name"
            maxlength="50"
            required
            autocomplete="off"
            placeholder="最长 50 个字符"
            :disabled="action.active === 'create'"
          />
        </div>
        <div class="field">
          <label for="create-user-password">初始密码</label>
          <el-input
            id="create-user-password"
            v-model="createForm.password"
            type="password"
            show-password
            maxlength="72"
            required
            autocomplete="new-password"
            placeholder="8–72 个字符"
            :disabled="action.active === 'create'"
          />
          <small class="muted">同时不能超过 72 个 UTF-8 字节；新用户默认启用且没有角色。</small>
        </div>
        <p v-if="createError" class="form-error" role="alert">{{ createError }}</p>
      </form>
      <template #footer>
        <el-button :disabled="action.active === 'create'" @click="closeCreate">取消</el-button>
        <el-button type="primary" native-type="submit" form="create-user-form" :loading="action.active === 'create'">
          创建
        </el-button>
      </template>
    </el-dialog>

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
          <p id="role-select-help" class="muted">从真实角色目录中选择，已分配的角色不会重复出现。</p>
          <div v-if="roleDirectory.error" class="role-directory-error" role="alert">
            <p class="form-error">{{ roleDirectory.error }}</p>
            <el-button :loading="roleDirectory.loading" @click="loadRoleDirectory">重试角色目录</el-button>
          </div>
          <form v-else class="role-assign-form" @submit.prevent="assignRole">
            <el-select
              v-model="roleId"
              filterable
              aria-label="要分配的角色"
              aria-describedby="role-select-help"
              placeholder="选择要分配的角色"
              no-data-text="暂无可分配角色"
              :loading="roleDirectory.loading"
              :disabled="action.active !== null || roleDirectory.loading"
            >
              <el-option
                v-for="role in availableRoles"
                :key="role.id"
                :label="`${role.roleName}（${role.roleCode}）`"
                :value="role.id"
              />
            </el-select>
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
