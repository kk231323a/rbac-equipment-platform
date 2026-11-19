import { createApp, watch } from 'vue'
import {
  ElButton,
  ElConfigProvider,
  ElDatePicker,
  ElDialog,
  ElInput,
  ElOption,
  ElPagination,
  ElSelect,
} from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import { router } from './router.js'
import { can, session } from './session.js'
import './styles.css'

const app = createApp(App)
for (const component of [
  ElButton, ElConfigProvider, ElDatePicker, ElDialog, ElInput, ElOption, ElPagination, ElSelect,
]) {
  app.component(component.name, component)
}
app.use(router)
app.mount('#app')

watch(() => session.token, (token, previousToken) => {
  if (!token && previousToken) void router.replace({ name: 'login' })
})

watch(() => session.permissions, (codes) => {
  const required = router.currentRoute.value.meta.permission
  if (codes && required && !can(required)) void router.replace({ name: 'workspace' })
})
