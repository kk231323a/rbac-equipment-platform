import vue from '@vitejs/plugin-vue'
import { defineConfig, loadEnv } from 'vite'

export default defineConfig(({ mode }) => {
  const { RBAC_API_TARGET } = loadEnv(mode, process.cwd(), 'RBAC_')
  const host = '127.0.0.1'
  const proxy = {
    '/api': {
      target: RBAC_API_TARGET || 'http://127.0.0.1:8080',
      changeOrigin: true,
    },
  }

  return {
    plugins: [vue()],
    server: { host, proxy },
    preview: { host, proxy },
  }
})
