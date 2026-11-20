<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { permissionApi, refreshPermissions, roleApi } from '../api.js'
import { can, permission } from '../session.js'

const roles = ref([])
const permissions = ref([])
const selectedRoleId = ref()
const assignedIds = ref([])
const draftIds = ref([])
const catalog = reactive({ loading: true, error: '' })
const roleState = reactive({ loading: false, error: '' })
const saving = ref(false)
const failures = ref([])
let roleRequest = 0

const selectedRole = computed(() => roles.value.find((role) => role.id === selectedRoleId.value))
const assignedSet = computed(() => new Set(assignedIds.value))
const draftSet = computed(() => new Set(draftIds.value))
const canAssign = computed(() => can(permission.rolePermissionAssign))
const canRevoke = computed(() => can(permission.rolePermissionRevoke))
const changes = computed(() => ({
  added: permissions.value.filter((item) => !assignedSet.value.has(item.id) && draftSet.value.has(item.id)),
  removed: permissions.value.filter((item) => assignedSet.value.has(item.id) && !draftSet.value.has(item.id)),
}))
const dirty = computed(() => changes.value.added.length > 0 || changes.value.removed.length > 0)
const canSave = computed(() => dirty.value
  && !saving.value
  && !roleState.loading
  && !roleState.error
  && changes.value.added.every(() => canAssign.value)
  && changes.value.removed.every(() => canRevoke.value))
const changeSummary = computed(() => dirty.value
  ? `新增 ${changes.value.added.length} 项，撤销 ${changes.value.removed.length} 项`
  : '当前配置未修改')

onMounted(loadCatalog)

async function loadCatalog() {
  roleRequest += 1
  catalog.loading = true
  catalog.error = ''
  failures.value = []

  try {
    const [roleList, permissionList] = await Promise.all([roleApi.list(), permissionApi.list()])
    if (!Array.isArray(roleList)) throw new Error('角色目录响应格式异常')
    if (!Array.isArray(permissionList)) throw new Error('权限目录响应格式异常')
    roles.value = roleList
    permissions.value = permissionList
    selectedRoleId.value = roleList[0]?.id
    assignedIds.value = []
    draftIds.value = []
    if (selectedRoleId.value) await loadRolePermissions()
  } catch (error) {
    catalog.error = error.message
  } finally {
    catalog.loading = false
  }
}

async function loadRolePermissions() {
  const roleId = selectedRoleId.value
  failures.value = []
  if (!roleId) {
    assignedIds.value = []
    draftIds.value = []
    return
  }

  const currentRequest = ++roleRequest
  roleState.loading = true
  roleState.error = ''
  assignedIds.value = []
  draftIds.value = []

  try {
    const assigned = await roleApi.permissions(roleId)
    if (currentRequest !== roleRequest) return
    if (!Array.isArray(assigned)) throw new Error('角色权限响应格式异常')
    assignedIds.value = assigned.map((item) => item.id)
    draftIds.value = [...assignedIds.value]
  } catch (error) {
    if (currentRequest === roleRequest) roleState.error = error.message
  } finally {
    if (currentRequest === roleRequest) roleState.loading = false
  }
}

function permissionDisabled(item) {
  if (saving.value) return true
  return assignedSet.value.has(item.id) ? !canRevoke.value : !canAssign.value
}

function resetDraft() {
  draftIds.value = [...assignedIds.value]
  failures.value = []
}

async function save() {
  if (!canSave.value) return
  const roleId = selectedRoleId.value
  const operations = [
    ...changes.value.added.map((item) => ({ item, type: 'assign' })),
    ...changes.value.removed.map((item) => ({ item, type: 'revoke' })),
  ]
  const failed = []
  let succeeded = 0
  saving.value = true
  failures.value = []

  try {
    for (const { item, type } of operations) {
      try {
        if (type === 'assign') await permissionApi.assign(roleId, item.id)
        else await permissionApi.revoke(roleId, item.id)
        succeeded += 1
      } catch (error) {
        failed.push({ id: item.id, name: item.permissionName, message: error.message, status: error.status })
        if (error.status === 401) break
      }
    }

    await Promise.allSettled([loadRolePermissions(), refreshPermissions()])
    failures.value = failed

    if (failed.some((item) => item.status === 401)) return
    if (failed.length === 0) ElMessage.success('角色权限已更新')
    else if (succeeded > 0) ElMessage.warning(`已完成 ${succeeded} 项，${failed.length} 项失败，已刷新为后端实际配置`)
    else ElMessage.error('权限变更失败，已刷新为后端实际配置')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <section>
    <header class="page-heading">
      <div>
        <h1>角色权限</h1>
        <p>按角色查看权限，并提交权限授予或撤销。</p>
      </div>
    </header>

    <div class="list-panel role-permission-panel">
      <div v-if="catalog.loading" class="panel-state" role="status">正在加载角色与权限目录…</div>
      <div v-else-if="catalog.error" class="panel-state" role="alert">
        <p>{{ catalog.error }}</p>
        <el-button @click="loadCatalog">重试</el-button>
      </div>
      <template v-else>
        <div class="role-permission-toolbar">
          <div class="role-select-field">
            <label for="role-permission-select">配置角色</label>
            <el-select
              id="role-permission-select"
              v-model="selectedRoleId"
              placeholder="选择角色"
              no-data-text="暂无角色"
              :disabled="saving"
              @change="loadRolePermissions"
            >
              <el-option
                v-for="role in roles"
                :key="role.id"
                :label="`${role.roleName}（${role.roleCode}）`"
                :value="role.id"
              />
            </el-select>
          </div>
          <el-button :disabled="!selectedRoleId || saving" :loading="roleState.loading" @click="loadRolePermissions">
            刷新当前角色
          </el-button>
        </div>

        <div v-if="roles.length === 0" class="panel-state">暂无角色</div>
        <div v-else-if="roleState.loading" class="panel-state" role="status">正在加载角色权限…</div>
        <div v-else-if="roleState.error" class="panel-state" role="alert">
          <p>{{ roleState.error }}</p>
          <el-button @click="loadRolePermissions">重试</el-button>
        </div>
        <div v-else-if="permissions.length === 0" class="panel-state">暂无权限定义</div>
        <template v-else>
          <div class="permission-content">
            <div class="permission-heading">
              <div>
                <h2>{{ selectedRole?.roleName }}的权限</h2>
                <p class="muted">已勾选 {{ draftIds.length }} 项，后端原有 {{ assignedIds.length }} 项。</p>
              </div>
              <span class="identifier muted">{{ selectedRole?.roleCode }}</span>
            </div>

            <el-checkbox-group v-model="draftIds" class="permission-grid" aria-label="角色权限选项">
              <el-checkbox
                v-for="item in permissions"
                :key="item.id"
                :value="item.id"
                :disabled="permissionDisabled(item)"
                class="permission-option"
                :class="{ 'permission-option--assigned': assignedSet.has(item.id) }"
              >
                <span class="permission-option__name">{{ item.permissionName }}</span>
                <span class="permission-option__code identifier">{{ item.permissionCode }}</span>
              </el-checkbox>
            </el-checkbox-group>

            <div v-if="failures.length" class="permission-failures" role="alert">
              <strong>以下权限未成功变更，页面已刷新为后端实际配置：</strong>
              <ul>
                <li v-for="failure in failures" :key="failure.id">{{ failure.name }}：{{ failure.message }}</li>
              </ul>
            </div>
          </div>

          <div class="permission-actions">
            <div>
              <span>{{ changeSummary }}</span>
              <small v-if="!canAssign && !canRevoke" class="cell-secondary">当前账号只能查看角色权限。</small>
              <small v-else-if="dirty" class="cell-secondary">保存将逐项提交；若部分失败，会重新读取实际配置。</small>
            </div>
            <div class="permission-action-buttons">
              <el-button :disabled="!dirty || saving" @click="resetDraft">重置</el-button>
              <el-button type="primary" :disabled="!canSave" :loading="saving" @click="save">保存变更</el-button>
            </div>
          </div>
        </template>
      </template>
    </div>
  </section>
</template>
