import { reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

export function useAction(reload) {
  const action = reactive({ active: null })

  async function run(key, submit, { confirmation, success, onSuccess, onError } = {}) {
    if (action.active !== null) return
    action.active = key

    try {
      if (confirmation) {
        await ElMessageBox.confirm(confirmation, '请确认操作', {
          type: 'warning', confirmButtonText: '确认', cancelButtonText: '取消',
        })
      }
      await submit()
      onSuccess?.()
      if (success) ElMessage.success(success)
      await reload()
    } catch (error) {
      if (error === 'cancel' || error === 'close' || error.status === 401) return
      if (onError) onError(error.message, error.status)
      if (!onError || error.status === 403 || error.status === 409) ElMessage.error(error.message)
      if (error.status === 409) await reload()
    } finally {
      action.active = null
    }
  }

  return { action, run }
}
