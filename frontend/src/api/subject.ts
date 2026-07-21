import http from './http'
import type { ApiResponse } from '../types/api'
import type { Subject, SubjectPayload } from '../types/subject'

export const subjectApi = {
  list: () => http.get<ApiResponse<Subject[]>>('/subjects'),
  get: (id: number) => http.get<ApiResponse<Subject>>(`/subjects/${id}`),
  create: (payload: SubjectPayload) => http.post<ApiResponse<Subject>>('/subjects', payload),
  update: (id: number, payload: SubjectPayload) => http.put<ApiResponse<Subject>>(`/subjects/${id}`, payload),
  remove: (id: number) => http.delete<ApiResponse<null>>(`/subjects/${id}`),
}
