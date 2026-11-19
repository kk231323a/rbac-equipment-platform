<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Box, Close, Grid, Menu, SwitchButton } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { authApi } from '../api.js'
import { navigation } from '../router.js'
import { can, clearSession, session } from '../session.js'

const route = useRoute()
const mobileMenuOpen = ref(false)
const loggingOut = ref(false)
const displayName = computed(() => session.user?.name || session.user?.account || '')
const groups = computed(() => ['设备借用', '系统管理'].map((label) => ({
  label,
  links: navigation.filter((item) => item.group === label && (!item.permission || can(item.permission))),
})).filter((group) => group.links.length > 0))

watch(() => route.name, () => { mobileMenuOpen.value = false })

async function logout() {
  if (loggingOut.value) return
  loggingOut.value = true
  const token = session.token
  try {
    await authApi.logout()
    clearSession(token)
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
          <el-button text :icon="SwitchButton" :loading="loggingOut" @click="logout">退出</el-button>
        </div>
      </header>

      <main class="page-content">
        <RouterView v-slot="{ Component }">
          <component :is="Component" :key="route.name" />
        </RouterView>
      </main>
    </div>
  </div>
</template>
