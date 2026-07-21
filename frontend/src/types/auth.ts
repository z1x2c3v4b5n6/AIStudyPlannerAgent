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
