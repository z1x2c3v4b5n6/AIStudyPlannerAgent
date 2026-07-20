export interface User {
  id: number
  username: string
  nickname: string
  createdAt: string
}

export interface LoginResult {
  tokenName: string
  tokenValue: string
  timeout: number
  user: User
}

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
  timestamp: number
}

