<script setup>
import { computed } from 'vue'
import { ArrowRight } from '@element-plus/icons-vue'
import { refreshPermissions } from '../api.js'
import { navigation } from '../router.js'
import { can, session } from '../session.js'

const groups = computed(() => {
  const links = navigation.filter((item) => !item.permission || can(item.permission))
  return ['设备借用', '系统管理']
    .map((label) => ({ label, links: links.filter((item) => item.group === label) }))
    .filter((group) => group.links.length > 0)
})

function retryPermissions() {
  void refreshPermissions().catch(() => {})
}
</script>

<template>
  <section class="workspace">
    <header class="page-heading">
      <div>
        <h1>工作台</h1>
        <p>你好，{{ session.user?.name || session.user?.account }}</p>
      </div>
    </header>

    <div v-if="session.permissionsError" class="warning-banner" role="alert">
      <span>读取账号权限失败。{{ session.permissionsError }}</span>
      <el-button size="small" :loading="session.checkingPermissions" @click="retryPermissions">重试</el-button>
    </div>

    <section v-for="group in groups" :key="group.label" class="workspace-section">
      <h2 class="workspace-section-title">{{ group.label }}</h2>
      <div class="workspace-grid">
        <RouterLink
          v-for="item in group.links"
          :key="item.name"
          class="workspace-link"
          :class="{ 'workspace-link--primary': item.name === 'equipment' }"
          :to="{ name: item.name }"
        >
          <span class="workspace-icon"><component :is="item.icon" aria-hidden="true" /></span>
          <div>
            <h3>{{ item.label }}</h3>
            <p>{{ item.description }}</p>
          </div>
          <ArrowRight class="workspace-arrow" aria-hidden="true" />
        </RouterLink>
      </div>
    </section>
  </section>
</template>
