import { reactive } from 'vue'

const storageKey = 'equipment-platform-session'

function readSession() {
  try {
    const stored = JSON.parse(sessionStorage.getItem(storageKey))
    if (typeof stored?.token === 'string' && Number.isInteger(stored.user?.id)) {
      return stored
    }
  } catch {
    // The in-memory session also works when the browser blocks storage.
  }
  return { token: '', user: null }
}

export const session = reactive({
  ...readSession(),
  permissions: null,
  permissionsError: '',
  checkingPermissions: false,
})

export function startSession({ accessToken, id, account, name }) {
  const user = { id, account, name }
  session.user = user
  session.token = accessToken
  session.permissions = null
  session.permissionsError = ''
  session.checkingPermissions = false
  try {
    sessionStorage.setItem(storageKey, JSON.stringify({ token: accessToken, user }))
  } catch {
    // The active tab can still use the in-memory token.
  }
}

export function clearSession(expectedToken) {
  if (session.token !== expectedToken) return
  session.token = ''
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
  userEnable: 'user:enable',
  userDisable: 'user:disable',
  roleAssign: 'user:role:assign',
  roleRevoke: 'user:role:revoke',
  auditRead: 'audit:read',
}

export function can(code) {
  return session.permissions?.includes(code) === true
}
