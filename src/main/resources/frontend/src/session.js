import { reactive } from 'vue'

const storageKey = 'equipment-platform-session'

function readSession() {
  try {
    const stored = JSON.parse(sessionStorage.getItem(storageKey))
    if (typeof stored?.token === 'string' && Number.isInteger(stored.user?.id)) {
      return {
        token: stored.token,
        refreshToken: typeof stored.refreshToken === 'string' ? stored.refreshToken : '',
        user: stored.user,
      }
    }
  } catch {
    // The in-memory session also works when the browser blocks storage.
  }
  return { token: '', refreshToken: '', user: null }
}

let loginVersion = 0

export function getLoginVersion() {
  return loginVersion
}

export const session = reactive({
  ...readSession(),
  permissions: null,
  permissionsError: '',
  checkingPermissions: false,
})

function persistSession() {
  try {
    sessionStorage.setItem(storageKey, JSON.stringify({
      token: session.token,
      refreshToken: session.refreshToken,
      user: session.user,
    }))
  } catch {
    // The active tab can still use the in-memory token.
  }
}

export function startSession({ accessToken, refreshToken, id, account, name }) {
  loginVersion += 1
  const user = { id, account, name }
  session.user = user
  session.token = accessToken
  session.refreshToken = refreshToken
  session.permissions = null
  session.permissionsError = ''
  session.checkingPermissions = false
  persistSession()
}

export function replaceTokens({ expectedToken, expectedRefreshToken, accessToken, refreshToken }) {
  if (session.token !== expectedToken || session.refreshToken !== expectedRefreshToken) return false
  session.token = accessToken
  session.refreshToken = refreshToken
  persistSession()
  return true
}

export function clearSession(expectedToken) {
  if (session.token !== expectedToken) return
  loginVersion += 1
  session.token = ''
  session.refreshToken = ''
  session.user = null
  session.permissions = null
  session.permissionsError = ''
  session.checkingPermissions = false
  try {
    sessionStorage.removeItem(storageKey)
  } catch {
    // The in-memory session has already been cleared.
  }
}

export const permission = {
  equipmentRead: 'equipment:read',
  equipmentCreate: 'equipment:create',
  equipmentUpdate: 'equipment:update',
  borrowCreate: 'borrow:create',
  borrowApprove: 'borrow:approved',
  borrowReturn: 'borrow:return',
  userRead: 'user:read',
  userCreate: 'user:create',
  userEnable: 'user:enable',
  userDisable: 'user:disable',
  roleAssign: 'user:role:assign',
  roleRevoke: 'user:role:revoke',
  rolePermissionAssign: 'role:permission:assign',
  rolePermissionRevoke: 'role:permission:revoke',
  auditRead: 'audit:read',
}

export function can(code) {
  return session.permissions?.includes(code) === true
}
