import http from './http'
import type { ApiResponse, PageResponse } from '../types/api'
import type { StudyTask, TaskPayload, TaskQuery, TaskStatus, TodayTasks } from '../types/task'

export const taskApi = {
  list: (params: TaskQuery) => http.get<ApiResponse<PageResponse<StudyTask>>>('/tasks', { params }),
  get: (id: number) => http.get<ApiResponse<StudyTask>>(`/tasks/${id}`),
  today: () => http.get<ApiResponse<TodayTasks>>('/tasks/today'),
  upcoming: (days = 7) => http.get<ApiResponse<StudyTask[]>>('/tasks/upcoming', { params: { days } }),
  create: (payload: TaskPayload) => http.post<ApiResponse<StudyTask>>('/tasks', payload),
  update: (id: number, payload: TaskPayload) => http.put<ApiResponse<StudyTask>>(`/tasks/${id}`, payload),
  changeStatus: (id: number, status: TaskStatus) => http.patch<ApiResponse<StudyTask>>(`/tasks/${id}/status`, { status }),
  remove: (id: number) => http.delete<ApiResponse<null>>(`/tasks/${id}`),
}
