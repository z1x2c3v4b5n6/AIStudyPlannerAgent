import http from './http'
import type { ApiResponse, PageResponse } from '../types/api'
import type { Goal, GoalCreatePayload, GoalDetail, GoalQuery, GoalStatus, GoalUpdatePayload } from '../types/goal'

export const goalApi = {
  list: (params: GoalQuery) => http.get<ApiResponse<PageResponse<Goal>>>('/goals', { params }),
  get: (id: number) => http.get<ApiResponse<GoalDetail>>(`/goals/${id}`),
  create: (payload: GoalCreatePayload) => http.post<ApiResponse<Goal>>('/goals', payload),
  update: (id: number, payload: GoalUpdatePayload) => http.put<ApiResponse<Goal>>(`/goals/${id}`, payload),
  changeStatus: (id: number, status: GoalStatus) => http.patch<ApiResponse<Goal>>(`/goals/${id}/status`, { status }),
  remove: (id: number) => http.delete<ApiResponse<null>>(`/goals/${id}`),
}
