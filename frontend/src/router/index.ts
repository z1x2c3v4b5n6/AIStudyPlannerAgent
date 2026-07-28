import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { getToken } from '../utils/token'
import LoginView from '../views/auth/LoginView.vue'
import RegisterView from '../views/auth/RegisterView.vue'
import AppLayout from '../layouts/AppLayout.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: AppLayout,
      meta: { requiresAuth: true },
      children: [
        { path: '', name: 'home', component: () => import('../views/DashboardView.vue'), meta: { title: '学习概览', description: '聚焦今天，稳步推进你的学习安排' } },
        { path: 'subjects', name: 'subjects', component: () => import('../views/subjects/SubjectListView.vue'), meta: { title: '科目管理', description: '整理学习方向和科目结构' } },
        { path: 'goals', name: 'goals', component: () => import('../views/goals/GoalListView.vue'), meta: { title: '学习目标', description: '把长期方向拆成清晰目标' } },
        { path: 'tasks', name: 'tasks', component: () => import('../views/tasks/TaskListView.vue'), meta: { title: '学习任务', description: '管理待办、进度与截止时间' } },
        { path: 'plans', name: 'plans', component: () => import('../views/plans/PlanView.vue'), meta: { title: '学习计划', description: '让 AI 把学习想法整理成可执行安排' } },
        { path: 'records', name: 'records', component: () => import('../views/records/RecordListView.vue'), meta: { title: '学习记录', description: '记录实际投入与学习反馈' } },
        { path: 'statistics', name: 'statistics', component: () => import('../views/statistics/StatisticsView.vue'), meta: { title: '数据统计', description: '回顾学习趋势和时间分布' } },
      ],
    },
    { path: '/login', name: 'login', component: LoginView, meta: { guestOnly: true } },
    { path: '/register', name: 'register', component: RegisterView, meta: { guestOnly: true } },
  ],
})

router.beforeEach(async (to) => {
  const authStore = useAuthStore()
  if (getToken() && !authStore.initialized) await authStore.restoreUser()

  if (to.meta.requiresAuth && !authStore.isAuthenticated) return { name: 'login' }
  if (to.meta.guestOnly && authStore.isAuthenticated) return { name: 'home' }
  return true
})

export default router
