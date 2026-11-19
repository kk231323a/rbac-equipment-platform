import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Box, Notebook, RefreshLeft, Stamp, Tickets, User } from '@element-plus/icons-vue'
import { refreshPermissions } from './api.js'
import { can, permission, session } from './session.js'

export const navigation = [
  {
    name: 'equipment', path: 'equipment', label: '设备列表', group: '设备借用', icon: Box,
    description: '查询设备、查看详情和发起借用', permission: permission.equipmentRead,
    component: () => import('./views/EquipmentView.vue'),
  },
  {
    name: 'my-requests', path: 'my-requests', label: '我的借用申请', group: '设备借用', icon: Tickets,
    description: '跟进申请进度、取消申请或确认领用',
    component: () => import('./views/MyRequestsView.vue'),
  },
  {
    name: 'pending', path: 'pending', label: '待审批', group: '设备借用', icon: Stamp,
    description: '审核申请并设置应还时间', permission: permission.borrowApprove,
    component: () => import('./views/BorrowQueueView.vue'), props: { mode: 'pending' },
  },
  {
    name: 'received', path: 'received', label: '归还确认', group: '设备借用', icon: RefreshLeft,
    description: '查看已领用设备并确认归还', permission: permission.borrowReturn,
    component: () => import('./views/BorrowQueueView.vue'), props: { mode: 'received' },
  },
  {
    name: 'users', path: 'users', label: '用户与角色', group: '系统管理', icon: User,
    description: '管理账号启停与用户角色', permission: permission.userRead,
    component: () => import('./views/UsersView.vue'),
  },
  {
    name: 'audit', path: 'audit', label: '审计日志', group: '系统管理', icon: Notebook,
    description: '查询操作结果与审计明细', permission: permission.auditRead,
    component: () => import('./views/AuditView.vue'),
  },
]

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: () => import('./views/LoginView.vue'), meta: { title: '登录' } },
    {
      path: '/',
      component: () => import('./components/AppShell.vue'),
      children: [
        { path: '', name: 'workspace', component: () => import('./views/WorkspaceView.vue'), meta: { title: '工作台' } },
        ...navigation.map(({ name, path, label, permission, component, props }) => ({
          name, path, component, props, meta: { title: label, permission },
        })),
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach(async (to) => {
  if (to.name === 'login') return session.token ? { name: 'workspace' } : true
  if (!session.token) return { name: 'login' }

  try {
    await refreshPermissions()
  } catch {
    if (!session.token) return { name: 'login' }
    if (to.meta.permission) return { name: 'workspace' }
  }

  if (to.meta.permission && !can(to.meta.permission)) {
    ElMessage.warning('权限不足')
    return { name: 'workspace' }
  }
  return true
})

router.afterEach((to) => {
  document.title = `${to.meta.title || '工作台'} · 设备借用平台`
})
