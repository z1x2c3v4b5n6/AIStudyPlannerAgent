import http from './http'
import type { ApiResponse, PageResponse } from '../types/api'
import type { AiPlanDraftResult, CandidateTasksRequest, NaturalLanguagePlanParseRequest, NaturalLanguagePlanParseResult, PlanConfirmRequest, PlanDetail, PlanDraft, PlanDraftRequest, PlanItemStatus, PlanListItem, PlanQuery, PlanStatus, QuickCreatePlanTaskRequest, QuickCreatePlanTaskResult, SelectablePlanTask } from '../types/plan'
const AI_REQUEST_TIMEOUT = 75000
export const planApi = {
  generateDraft:(payload:PlanDraftRequest)=>http.post<ApiResponse<PlanDraft>>('/plans/draft',payload),
  parseNaturalLanguage:(payload:NaturalLanguagePlanParseRequest)=>http.post<ApiResponse<NaturalLanguagePlanParseResult>>('/ai/plans/parse-requirement',payload,{timeout:AI_REQUEST_TIMEOUT}),
  candidateTasks:(payload:CandidateTasksRequest)=>http.post<ApiResponse<SelectablePlanTask[]>>('/ai/plans/candidate-tasks',payload),
  quickCreateTask:(payload:QuickCreatePlanTaskRequest)=>http.post<ApiResponse<QuickCreatePlanTaskResult>>('/ai/plans/quick-create-task',payload),
  generateAiDraft:(payload:PlanDraftRequest)=>http.post<ApiResponse<AiPlanDraftResult>>('/ai/plans/draft',payload,{timeout:AI_REQUEST_TIMEOUT}),
  confirm:(payload:PlanConfirmRequest)=>http.post<ApiResponse<PlanDetail>>('/plans/confirm',payload),
  list:(params:PlanQuery)=>http.get<ApiResponse<PageResponse<PlanListItem>>>('/plans',{params}),
  get:(id:number)=>http.get<ApiResponse<PlanDetail>>(`/plans/${id}`),
  changeStatus:(id:number,status:PlanStatus)=>http.patch<ApiResponse<PlanDetail>>(`/plans/${id}/status`,{status}),
  changeItemStatus:(planId:number,itemId:number,status:PlanItemStatus)=>http.patch<ApiResponse<PlanDetail>>(`/plans/${planId}/items/${itemId}/status`,{status}),
}
