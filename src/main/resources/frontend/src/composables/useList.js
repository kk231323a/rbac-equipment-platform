import { onBeforeUnmount, onMounted, reactive } from 'vue'

export function useList(fetchData, pageSize) {
  const state = reactive({ rows: [], pageNum: 1, pageSize, total: 0, loading: true, error: '' })
  let requestNumber = 0

  async function reload() {
    const currentRequest = ++requestNumber
    state.loading = true
    state.error = ''

    try {
      const data = await fetchData({ pageNum: state.pageNum, pageSize })
      if (currentRequest !== requestNumber) return

      if (!pageSize) {
        if (!Array.isArray(data)) throw new Error('申请列表响应格式异常')
        state.rows = data
        return
      }

      if (!Array.isArray(data?.records) || !Number.isFinite(data.total)) {
        throw new Error('分页响应格式异常')
      }

      const lastPage = Math.max(1, Math.ceil(data.total / pageSize))
      if (state.pageNum > lastPage) {
        state.pageNum = lastPage
        await reload()
        return
      }

      state.rows = data.records
      state.total = data.total
    } catch (error) {
      if (currentRequest === requestNumber) state.error = error.message
    } finally {
      if (currentRequest === requestNumber) state.loading = false
    }
  }

  function goTo(pageNum) {
    state.pageNum = pageNum
    return reload()
  }

  onMounted(reload)
  onBeforeUnmount(() => { requestNumber += 1 })

  return { state, reload, goTo }
}
