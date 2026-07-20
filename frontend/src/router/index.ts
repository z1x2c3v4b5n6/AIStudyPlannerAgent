import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { getToken } from '../utils/token'
import LoginView from '../views/auth/LoginView.vue'
import RegisterView from '../views/auth/RegisterView.vue'
import HomeView from '../views/HomeView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: HomeView, meta: { requiresAuth: true } },
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
