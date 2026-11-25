<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Box, Close, Grid, Lock, Menu, SwitchButton } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { authApi } from '../api.js'
import { navigation } from '../router.js'
import { can, clearSession, getLoginVersion, session } from '../session.js'

const route = useRoute()
const mobileMenuOpen = ref(false)
const loggingOut = ref(false)
const passwordDialogOpen = ref(false)
const changingPassword = ref(false)
const passwordForm = reactive({ oldPassword: '', newPassword: '', confirmation: '' })
const passwordError = ref('')
const displayName = computed(() => session.user?.name || session.user?.account || '')
const groups = computed(() => ['设备借用', '系统管理'].map((label) => ({
  label,
  links: navigation.filter((item) => item.group === label && (!item.permission || can(item.permission))),
})).filter((group) => group.links.length > 0))

watch(() => route.name, () => { mobileMenuOpen.value = false })

function resetPasswordForm() {
  Object.assign(passwordForm, { oldPassword: '', newPassword: '', confirmation: '' })
  passwordError.value = ''
}

async function changePassword() {
  const account = session.user?.account
  if (changingPassword.value || loggingOut.value || !passwordDialogOpen.value || !account) return

  const { oldPassword, newPassword, confirmation } = passwordForm
  passwordError.value = ''
  if (!oldPassword.trim() || oldPassword.length > 72) {
    passwordError.value = '请输入旧密码（最多 72 个字符）'
    return
  }
  if (!newPassword.trim() || newPassword.length < 6 || newPassword.length > 72 || /[^\p{ASCII}]/u.test(newPassword)) {
    passwordError.value = '新密码应为 6 到 72 个 ASCII 字符'
    return
  }
  if (newPassword !== confirmation) {
    passwordError.value = '两次输入的新密码不一致'
    return
  }

  changingPassword.value = true
  const loginVersion = getLoginVersion()
  try {
    await authApi.changePassword({ account, oldPassword, newPassword })
    if (getLoginVersion() !== loginVersion) return

    resetPasswordForm()
    passwordDialogOpen.value = false
    clearSession(session.token)
    ElMessage.success('密码已修改，请使用新密码重新登录')
  } catch (error) {
    if (getLoginVersion() === loginVersion) passwordError.value = error.message
  } finally {
    changingPassword.value = false
  }
}

async function logout() {
  if (loggingOut.value || changingPassword.value) return
  loggingOut.value = true
  const loginVersion = getLoginVersion()
  try {
    await authApi.logout()
    if (getLoginVersion() === loginVersion) {
      clearSession(session.token)
    }
  } catch (error) {
    if (error.status !== 401) ElMessage.error(error.message)
  } finally {
    loggingOut.value = false
  }
}
</script>

<template>
  <div class="app-shell" @keydown.esc="mobileMenuOpen = false">
    <button
      v-if="mobileMenuOpen"
      type="button"
      class="sidebar-backdrop"
      aria-label="关闭导航"
      @click="mobileMenuOpen = false"
    ></button>
    <aside id="platform-sidebar" class="sidebar" :class="{ 'sidebar--open': mobileMenuOpen }">
      <RouterLink class="brand" :to="{ name: 'workspace' }" @click="mobileMenuOpen = false">
        <span class="brand-mark"><Box aria-hidden="true" /></span>
        <span>设备借用平台</span>
      </RouterLink>

      <nav aria-label="主导航">
        <RouterLink class="nav-link" :to="{ name: 'workspace' }" @click="mobileMenuOpen = false">
          <Grid aria-hidden="true" /> 工作台
        </RouterLink>
        <div v-for="group in groups" :key="group.label" class="nav-group">
          <p>{{ group.label }}</p>
          <RouterLink
            v-for="item in group.links"
            :key="item.name"
            class="nav-link"
            :to="{ name: item.name }"
            @click="mobileMenuOpen = false"
          >
            <component :is="item.icon" aria-hidden="true" /> {{ item.label }}
          </RouterLink>
        </div>
      </nav>
    </aside>

    <div class="app-main">
      <header class="topbar">
        <div class="topbar-leading">
          <button
            type="button"
            class="mobile-menu-button"
            aria-controls="platform-sidebar"
            :aria-expanded="mobileMenuOpen"
            :aria-label="mobileMenuOpen ? '关闭导航' : '打开导航'"
            @click="mobileMenuOpen = !mobileMenuOpen"
          >
            <Close v-if="mobileMenuOpen" aria-hidden="true" />
            <Menu v-else aria-hidden="true" />
          </button>
          <nav class="breadcrumbs" aria-label="当前位置">
            <RouterLink v-if="route.name !== 'workspace'" :to="{ name: 'workspace' }">工作台</RouterLink>
            <span v-if="route.name !== 'workspace'" class="breadcrumb-divider" aria-hidden="true">/</span>
            <span aria-current="page">{{ route.meta.title }}</span>
          </nav>
        </div>
        <div class="account-controls">
          <span class="account-avatar" aria-hidden="true">{{ displayName.slice(0, 1) }}</span>
          <span class="account-name" :title="displayName">{{ displayName }}</span>
          <el-button
            class="account-password-button"
            text
            :icon="Lock"
            :disabled="!session.user?.account || loggingOut || changingPassword"
            @click="passwordDialogOpen = true"
          >
            修改密码
          </el-button>
          <el-button text :icon="SwitchButton" :loading="loggingOut" :disabled="changingPassword" @click="logout">
            退出
          </el-button>
        </div>
      </header>

      <main class="page-content">
        <RouterView v-slot="{ Component }">
          <component :is="Component" :key="route.name" />
        </RouterView>
      </main>
    </div>

    <el-dialog
      v-model="passwordDialogOpen"
      title="修改密码"
      width="min(440px, calc(100vw - 32px))"
      :close-on-click-modal="!changingPassword"
      :close-on-press-escape="!changingPassword"
      :show-close="!changingPassword"
      @close="resetPasswordForm"
    >
      <form id="password-change-form" class="dialog-form" @submit.prevent="changePassword">
        <div class="field">
          <label for="password-account">账号</label>
          <el-input id="password-account" :model-value="session.user?.account" autocomplete="username" readonly />
        </div>
        <div class="field">
          <label for="password-old">旧密码</label>
          <el-input
            id="password-old"
            v-model="passwordForm.oldPassword"
            type="password"
            autocomplete="current-password"
            show-password
            :disabled="changingPassword"
          />
        </div>
        <div class="field">
          <label for="password-new">新密码</label>
          <el-input
            id="password-new"
            v-model="passwordForm.newPassword"
            type="password"
            autocomplete="new-password"
            aria-describedby="password-new-hint"
            show-password
            :disabled="changingPassword"
          />
          <p id="password-new-hint" class="password-hint">6 到 72 个 ASCII 字符，可使用英文字母、数字和常用符号。</p>
        </div>
        <div class="field">
          <label for="password-confirmation">确认新密码</label>
          <el-input
            id="password-confirmation"
            v-model="passwordForm.confirmation"
            type="password"
            autocomplete="new-password"
            show-password
            :disabled="changingPassword"
          />
        </div>
        <p v-if="passwordError" class="form-error" role="alert">{{ passwordError }}</p>
      </form>
      <template #footer>
        <el-button :disabled="changingPassword" @click="passwordDialogOpen = false">取消</el-button>
        <el-button
          type="primary"
          native-type="submit"
          form="password-change-form"
          :loading="changingPassword"
        >
          确认修改
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>
