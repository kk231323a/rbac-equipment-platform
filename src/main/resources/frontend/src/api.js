import { clearSession, session } from './session.js'

class ApiError extends Error {
  constructor(status, message) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

async function request(path, { method = 'GET', params, body, authenticated = true } = {}) {
  const url = new URL(path, window.location.origin)
  for (const [key, value] of Object.entries(params ?? {})) {
    if (value !== undefined && value !== null && value !== '') url.searchParams.set(key, value)
  }

  const token = authenticated ? session.token : ''
  const headers = new Headers({ Accept: 'application/json' })
  if (token) headers.set('Authorization', `Bearer ${token}`)
  if (body !== undefined) headers.set('Content-Type', 'application/json')

  let response
  let payload
  try {
    response = await fetch(url, {
      method,
      headers,
      cache: 'no-store',
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: AbortSignal.timeout(15000),
    })
    payload = await response.json().catch(() => null)
  } catch (error) {
    throw new ApiError(0, error.name === 'TimeoutError' ? '请求超时，请重试' : '无法连接后端，请检查服务是否已启动')
  }

  const status = response.ok ? payload?.code : response.status
  if (status === 401 && authenticated) clearSession(token)
  if (status === 403 && authenticated && path !== '/api/auth/permissions') {
    void refreshPermissions().catch(() => {})
  }

  if (response.ok && status === 200) return payload.data
  if (status === 403 && authenticated) {
    const explanation = payload?.message
    const detail = explanation && explanation !== '无权访问该资源' ? `（${explanation}）` : ''
    throw new ApiError(status, `权限不足${detail}`)
  }
  throw new ApiError(status, payload?.message || (response.ok ? '后端响应格式异常' : `请求失败（HTTP ${response.status}）`))
}

const post = (path, params) => request(path, { method: 'POST', params })

export const authApi = {
  login: (account, password) => request('/api/auth/login', {
    method: 'POST', body: { account, password }, authenticated: false,
  }),
  logout: () => post('/api/auth/logout'),
}

let permissionsRequest

export function refreshPermissions() {
  const token = session.token
  if (!token) return Promise.resolve()
  if (permissionsRequest?.token === token) return permissionsRequest.promise

  session.checkingPermissions = true
  const promise = request('/api/auth/permissions')
    .then((codes) => {
      if (!Array.isArray(codes) || !codes.every((code) => typeof code === 'string')) {
        throw new Error('权限码响应格式异常')
      }
      if (session.token !== token) return
      session.permissions = codes
      session.permissionsError = ''
    })
    .catch((error) => {
      if (session.token === token) {
        session.permissions = null
        session.permissionsError = error.message
      }
      throw error
    })
    .finally(() => {
      if (session.token === token) session.checkingPermissions = false
      if (permissionsRequest?.token === token) permissionsRequest = undefined
    })

  permissionsRequest = { token, promise }
  return promise
}

export const equipmentApi = {
  list: (params) => request('/api/equipment', { params }),
  detail: (id) => request(`/api/equipment/${id}`),
  create: ({ deviceNo, name, model }) => request('/api/equipment/addNew', {
    method: 'POST', body: { deviceNo, name, model },
  }),
  update: (id, { name, model }) => request(`/api/equipment/update/${id}`, {
    method: 'PUT', body: { name, model },
  }),
}

export const borrowApi = {
  mine: () => request('/api/borrow/borrowedList'),
  create: (deviceId) => post('/api/borrow/request', { deviceId }),
  cancel: (requestId) => post('/api/borrow/request/cancel', { requestId }),
  receive: (requestId) => post('/api/borrow/receive', { requestId }),
  pending: (params) => request('/api/borrow/pending', { params }),
  approve: (requestId, dueTime) => post('/api/borrow/approve', { requestId, dueTime }),
  reject: (requestId) => post('/api/borrow/reject', { requestId }),
  received: (params) => request('/api/borrow/received', { params }),
  return: (requestId) => post('/api/borrow/return', { requestId }),
}

export const userApi = {
  list: (params) => request('/api/user', { params }),
  enable: (userId) => post('/api/user/enable', { userId }),
  disable: (userId) => post('/api/user/disable', { userId }),
  roles: (userId) => request('/api/user/roles', { params: { userId } }),
  assign: (userId, roleId) => post('/api/user/role/assign', { userId, roleId }),
  revoke: (userId, roleId) => post('/api/user/role/revoke', { userId, roleId }),
}

export const auditApi = {
  list: (params) => request('/api/audit/logs', { params }),
}
