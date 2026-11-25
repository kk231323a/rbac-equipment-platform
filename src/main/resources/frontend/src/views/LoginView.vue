<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, Box, Lock, User } from '@element-plus/icons-vue'
import { authApi } from '../api.js'
import { startSession } from '../session.js'

const router = useRouter()
const account = ref('')
const password = ref('')
const error = ref('')
const submitting = ref(false)

async function login() {
  if (submitting.value) return
  error.value = ''

  const trimmedAccount = account.value.trim()
  if (!trimmedAccount || !password.value.trim()) {
    error.value = '请输入账号和密码'
    return
  }

  submitting.value = true
  try {
    const data = await authApi.login(trimmedAccount, password.value)
    if (typeof data?.accessToken !== 'string' || !data.accessToken
      || typeof data.refreshToken !== 'string' || !data.refreshToken
      || !Number.isInteger(data.id)) {
      throw new Error('登录响应缺少有效的账号或凭证')
    }
    startSession(data)
    password.value = ''
    await router.replace({ name: 'workspace' })
  } catch (failure) {
    error.value = failure.message
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="login-page">
    <div class="login-brand">
      <div class="login-brand-signature">
        <span class="brand-mark"><Box aria-hidden="true" /></span>
        <span>设备借用平台</span>
      </div>
      <div class="login-brand-content">
        <p class="login-brand-title">设备借用<br />管理平台</p>
        <p class="login-brand-copy">设备台账、借用审批与操作审计</p>
      </div>
    </div>

    <section class="login-panel" aria-labelledby="login-title">
      <form class="login-form" @submit.prevent="login">
        <div class="login-mobile-brand"><Box aria-hidden="true" /> 设备借用平台</div>
        <h1 id="login-title">账号登录</h1>
        <div class="field">
          <label for="login-account">账号</label>
          <el-input
            id="login-account"
            v-model="account"
            :prefix-icon="User"
            :disabled="submitting"
            autocomplete="username"
            placeholder="输入账号"
            size="large"
          />
        </div>
        <div class="field">
          <label for="login-password">密码</label>
          <el-input
            id="login-password"
            v-model="password"
            type="password"
            :prefix-icon="Lock"
            :disabled="submitting"
            autocomplete="current-password"
            placeholder="输入密码"
            size="large"
            show-password
          />
        </div>
        <p v-if="error" class="form-error" role="alert">{{ error }}</p>
        <el-button type="primary" native-type="submit" size="large" class="login-submit" :loading="submitting">
          登录
          <ArrowRight v-if="!submitting" aria-hidden="true" />
        </el-button>
      </form>
    </section>
  </main>
</template>
