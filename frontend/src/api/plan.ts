import http from './http'
import type { ApiResponse, PageResponse } from '../types/api'
import type { PlanConfirmRequest, PlanDetail, PlanDraft, PlanDraftRequest, PlanItemStatus, PlanListItem, PlanQuery, PlanStatus } from '../types/plan'
export const planApi = {
  generateDraft:(payload:PlanDraftRequest)=>http.post<ApiResponse<PlanDraft>>('/plans/draft',payload),
  confirm:(payload:PlanConfirmRequest)=>http.post<ApiResponse<PlanDetail>>('/plans/confirm',payload),
  list:(params:PlanQuery)=>http.get<ApiResponse<PageResponse<PlanListItem>>>('/plans',{params}),
  get:(id:number)=>http.get<ApiResponse<PlanDetail>>(`/plans/${id}`),
  changeStatus:(id:number,status:PlanStatus)=>http.patch<ApiResponse<PlanDetail>>(`/plans/${id}/status`,{status}),
  changeItemStatus:(planId:number,itemId:number,status:PlanItemStatus)=>http.patch<ApiResponse<PlanDetail>>(`/plans/${planId}/items/${itemId}/status`,{status}),
}
