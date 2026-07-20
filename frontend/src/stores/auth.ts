import { defineStore } from 'pinia'
import { authApi, type LoginPayload, type RegisterPayload } from '../api/auth'
import type { User } from '../types/auth'
import { clearToken, getToken, saveToken } from '../utils/token'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    user: null as User | null,
    initialized: false,
  }),
  getters: {
    isAuthenticated: (state) => Boolean(state.user && getToken()),
  },
  actions: {
    async login(payload: LoginPayload) {
      clearToken()
      this.user = null
      this.initialized = false
      const response = await authApi.login(payload)
      const result = response.data.data
      saveToken(result.tokenName, result.tokenValue)
      this.user = result.user
      this.initialized = true
    },
    async register(payload: RegisterPayload) {
      await authApi.register(payload)
    },
    async restoreUser() {
      if (!getToken()) {
        this.user = null
        this.initialized = true
        return
      }
      try {
        const response = await authApi.me()
        this.user = response.data.data
      } catch {
        this.user = null
        clearToken()
      } finally {
        this.initialized = true
      }
    },
    async logout() {
      try {
        if (getToken()) await authApi.logout()
      } finally {
        this.user = null
        this.initialized = true
        clearToken()
      }
    },
  },
})
