export interface StudyRecord {
  id: number
  subjectId: number
  subjectName: string
  subjectColor: string | null
  taskId: number | null
  taskTitle: string | null
  startedAt: string
  endedAt: string
  durationMinutes: number
  feedback: string | null
  createdAt: string
  updatedAt: string
}

export interface RecordQuery {
  page: number
  pageSize: number
  subjectId?: number
  taskId?: number
  startDate?: string
  endDate?: string
}

export interface RecordPayload {
  subjectId: number
  taskId?: number | null
  startedAt: string
  endedAt: string
  feedback?: string | null
}
