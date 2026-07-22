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
        { path: '', name: 'home', component: () => import('../views/DashboardView.vue') },
        { path: 'subjects', name: 'subjects', component: () => import('../views/subjects/SubjectListView.vue') },
        { path: 'goals', name: 'goals', component: () => import('../views/goals/GoalListView.vue') },
        { path: 'tasks', name: 'tasks', component: () => import('../views/tasks/TaskListView.vue') },
        { path: 'records', name: 'records', component: () => import('../views/records/RecordListView.vue') },
        { path: 'statistics', name: 'statistics', component: () => import('../views/statistics/StatisticsView.vue') },
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
