import http from './http'
import type { ApiResponse, PageResponse } from '../types/api'
import type { RecordPayload, RecordQuery, StudyRecord } from '../types/record'

export const recordApi = {
  list: (params: RecordQuery) => http.get<ApiResponse<PageResponse<StudyRecord>>>('/records', { params }),
  get: (id: number) => http.get<ApiResponse<StudyRecord>>(`/records/${id}`),
  create: (payload: RecordPayload) => http.post<ApiResponse<StudyRecord>>('/records', payload),
  update: (id: number, payload: RecordPayload) => http.put<ApiResponse<StudyRecord>>(`/records/${id}`, payload),
  remove: (id: number) => http.delete<ApiResponse<null>>(`/records/${id}`),
}
