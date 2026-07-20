import http from './http'
import type { ApiResponse, LoginResult, User } from '../types/auth'

export interface RegisterPayload {
  username: string
  password: string
  nickname: string
}

export interface LoginPayload {
  username: string
  password: string
}

export const authApi = {
  register(payload: RegisterPayload) {
    return http.post<ApiResponse<User>>('/auth/register', payload)
  },
  login(payload: LoginPayload) {
    return http.post<ApiResponse<LoginResult>>('/auth/login', payload)
  },
  logout() {
    return http.post<ApiResponse<null>>('/auth/logout')
  },
  me() {
    return http.get<ApiResponse<User>>('/auth/me')
  },
}

