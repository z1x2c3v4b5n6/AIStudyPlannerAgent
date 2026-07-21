export interface ApiResponse<T> {
  code: number
  message: string
  data: T
  timestamp: number
}

export interface PageResponse<T> {
  list: T[]
  page: number
  pageSize: number
  total: number
}
