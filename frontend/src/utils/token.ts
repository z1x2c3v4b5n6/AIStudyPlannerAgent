const TOKEN_KEY = 'ai-study-planner-token'
const TOKEN_NAME_KEY = 'ai-study-planner-token-name'

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function getTokenName(): string {
  return localStorage.getItem(TOKEN_NAME_KEY) || 'satoken'
}

export function saveToken(name: string, value: string): void {
  localStorage.setItem(TOKEN_NAME_KEY, name)
  localStorage.setItem(TOKEN_KEY, value)
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(TOKEN_NAME_KEY)
}

