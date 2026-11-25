import { clearSession, getLoginVersion, replaceTokens, session } from './session.js'

class ApiError extends Error {
  constructor(status, message) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

let tokenRefreshRequest

function sessionIsCurrent(version) {
  return getLoginVersion() === version
}

async function request(path, options = {}) {
  const authenticated = options.authenticated ?? true
  const context = {
    authenticated,
    version: authenticated ? getLoginVersion() : 0,
    token: authenticated ? session.token : '',
  }
  return executeRequest(path, options, context, true)
}

async function executeRequest(path, options, context, allowRefresh) {
  const { method = 'GET', params, body } = options
  const url = new URL(path, window.location.origin)
  for (const [key, value] of Object.entries(params ?? {})) {
    if (value !== undefined && value !== null && value !== '') url.searchParams.set(key, value)
  }

  const headers = new Headers({ Accept: 'application/json' })
  if (context.token) headers.set('Authorization', `Bearer ${context.token}`)
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
  const accessTokenExpired = response.status === 401 && payload?.code === 40101
  if (context.authenticated && accessTokenExpired && allowRefresh) {
    const token = await resolveExpiredToken(context)
    return executeRequest(path, options, { ...context, token }, false)
  }

  if (status === 401 && context.authenticated) clearSession(context.token)
  if (status === 403 && context.authenticated && path !== '/api/auth/permissions') {
    void refreshPermissions().catch(() => {})
  }

  if (response.ok && status === 200) return payload.data
  if (status === 403 && context.authenticated) {
    const explanation = payload?.message
    const detail = explanation && explanation !== '无权访问该资源' ? `（${explanation}）` : ''
    throw new ApiError(status, `权限不足${detail}`)
  }
  throw new ApiError(status, payload?.message || (response.ok ? '后端响应格式异常' : `请求失败（HTTP ${response.status}）`))
}

async function resolveExpiredToken(context) {
  const { version, token } = context
  if (!sessionIsCurrent(version)) throw new ApiError(401, '登录状态已变更，请重新操作')

  if (session.token !== token) {
    if (session.token) return session.token
    throw new ApiError(401, '登录状态已变更，请重新登录')
  }

  const refreshToken = session.refreshToken
  if (!refreshToken) {
    clearSession(token)
    throw new ApiError(401, '登录已过期，请重新登录')
  }

  const newToken = await refreshTokens(version, token, refreshToken)
  if (!sessionIsCurrent(version) || !session.token) {
    throw new ApiError(401, '登录状态已变更，请重新登录')
  }
  return newToken
}

function refreshTokens(version, accessToken, refreshToken) {
  if (tokenRefreshRequest?.version === version
    && tokenRefreshRequest.accessToken === accessToken
    && tokenRefreshRequest.refreshToken === refreshToken) {
    return tokenRefreshRequest.promise
  }

  const promise = request('/api/auth/refresh', {
    method: 'POST', body: { refreshToken }, authenticated: false,
  })
    .then((data) => {
      if (typeof data?.accessToken !== 'string' || !data.accessToken
        || typeof data.refreshToken !== 'string' || !data.refreshToken) {
        throw new Error('刷新响应缺少有效凭证')
      }
      if (!sessionIsCurrent(version)) throw new ApiError(401, '登录状态已变更，请重新操作')

      if (session.token !== accessToken || session.refreshToken !== refreshToken) {
        if (session.token) return session.token
        throw new ApiError(401, '登录状态已变更，请重新登录')
      }

      replaceTokens({
        expectedToken: accessToken,
        expectedRefreshToken: refreshToken,
        accessToken: data.accessToken,
        refreshToken: data.refreshToken,
      })
      return data.accessToken
    })
    .catch((error) => {
      if (!sessionIsCurrent(version)) throw new ApiError(401, '登录状态已变更，请重新操作')
      if (error.status === 401) {
        clearSession(accessToken)
        throw error
      }
      if (error.status === 503 && !error.message.includes('稍后重试')) {
        throw new ApiError(503, '登录续期失败，请稍后重试')
      }
      throw error
    })
    .finally(() => {
      if (tokenRefreshRequest?.promise === promise) tokenRefreshRequest = undefined
    })

  tokenRefreshRequest = { version, accessToken, refreshToken, promise }
  return promise
}

const post = (path, params) => request(path, { method: 'POST', params })

export const authApi = {
  login: (account, password) => request('/api/auth/login', {
    method: 'POST', body: { account, password }, authenticated: false,
  }),
  logout: () => post('/api/auth/logout'),
  // The endpoint verifies the old password. A rejection here must keep the current session.
  changePassword: ({ account, oldPassword, newPassword }) => request('/api/user/password/change', {
    method: 'POST', body: { account, oldPassword, newPassword }, authenticated: false,
  }),
}

let permissionsRequest

export function refreshPermissions() {
  const token = session.token
  const version = getLoginVersion()
  if (!token) return Promise.resolve()
  if (permissionsRequest?.version === version) return permissionsRequest.promise

  session.checkingPermissions = true
  const promise = request('/api/auth/permissions')
    .then((codes) => {
      if (!Array.isArray(codes) || !codes.every((code) => typeof code === 'string')) {
        throw new Error('权限码响应格式异常')
      }
      if (!sessionIsCurrent(version)) return
      session.permissions = codes
      session.permissionsError = ''
    })
    .catch((error) => {
      if (sessionIsCurrent(version)) {
        session.permissions = null
        session.permissionsError = error.message
      }
      throw error
    })
    .finally(() => {
      if (sessionIsCurrent(version)) session.checkingPermissions = false
      if (permissionsRequest?.version === version) permissionsRequest = undefined
    })

  permissionsRequest = { version, promise }
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
  create: ({ account, name, password }) => request('/api/user/create', {
    method: 'POST', body: { account, name, password },
  }),
  enable: (userId) => post('/api/user/enable', { userId }),
  disable: (userId) => post('/api/user/disable', { userId }),
  roles: (userId) => request('/api/user/roles', { params: { userId } }),
  assign: (userId, roleId) => post('/api/user/role/assign', { userId, roleId }),
  revoke: (userId, roleId) => post('/api/user/role/revoke', { userId, roleId }),
}

export const roleApi = {
  list: () => request('/api/roles'),
  permissions: (roleId) => request(`/api/roles/${roleId}/permissions`),
}

export const permissionApi = {
  list: () => request('/api/permissions'),
  assign: (roleId, permissionId) => post('/api/permissions/assign', { roleId, permissionId }),
  revoke: (roleId, permissionId) => post('/api/permissions/revoke', { roleId, permissionId }),
}

export const auditApi = {
  list: (params) => request('/api/audit/logs', { params }),
}
