export const equipmentStatuses = [
  { value: 'available', label: '可借用' },
  { value: 'reserved', label: '已预留' },
  { value: 'borrowed', label: '已借出' },
]

export const statusDisplay = {
  available: ['可借用', 'green'],
  reserved: ['已预留', 'amber'],
  borrowed: ['已借出', 'blue'],
  pending: ['待审批', 'amber'],
  approved: ['待领用', 'blue'],
  received: ['已领用', 'teal'],
  returned: ['已归还', 'green'],
  rejected: ['已拒绝', 'red'],
  cancel: ['已取消', 'gray'],
  enabled: ['已启用', 'green'],
  disabled: ['已禁用', 'gray'],
  success: ['成功', 'green'],
  failure: ['失败', 'red'],
}

export function formatTime(value) {
  if (!value) return '—'
  return value.replace('T', ' ').replace(/\.\d+$/, '')
}
