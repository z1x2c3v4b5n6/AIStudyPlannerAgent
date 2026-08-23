import http from './http'
import type { ApiResponse, PageResponse } from '../types/api'
import type { LearningPathConfirmRequest, LearningPathDetail, LearningPathDraft, LearningPathGenerateRequest, LearningPathItemStatus, LearningPathListItem, LearningPathTaskResult } from '../types/learningPath'

const AI_TIMEOUT = 75000
export const learningPathApi = {
  generate: (payload:LearningPathGenerateRequest) => http.post<ApiResponse<LearningPathDraft>>('/ai/learning-paths/generate', payload, { timeout: AI_TIMEOUT }),
  confirm: (payload:LearningPathConfirmRequest) => http.post<ApiResponse<LearningPathDetail>>('/learning-paths/confirm', payload),
  list: (page=1,pageSize=10) => http.get<ApiResponse<PageResponse<LearningPathListItem>>>('/learning-paths',{params:{page,pageSize}}),
  get: (id:number) => http.get<ApiResponse<LearningPathDetail>>(`/learning-paths/${id}`),
  createTask: (pathId:number,itemId:number) => http.post<ApiResponse<LearningPathTaskResult>>(`/learning-paths/${pathId}/items/${itemId}/create-task`),
  changeItemStatus: (pathId:number,itemId:number,status:LearningPathItemStatus) => http.patch<ApiResponse<LearningPathDetail>>(`/learning-paths/${pathId}/items/${itemId}/status`,{status})
}
