export type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED'

export interface StudyTask {
  id: number
  subjectId: number
  goalId: number | null
  title: string
  description: string | null
  priority: number
  status: TaskStatus
  estimatedMinutes: number
  plannedDate: string | null
  dueAt: string | null
  completedAt: string | null
  createdAt: string
  updatedAt: string
}

export interface TodayTasks {
  tasks: StudyTask[]
  totalEstimatedMinutes: number
}

export interface TaskQuery {
  page: number
  pageSize: number
  subjectId?: number
  goalId?: number
  status?: TaskStatus
  priority?: number
  plannedDate?: string
}

export interface TaskPayload {
  subjectId: number
  goalId?: number | null
  title: string
  description?: string | null
  priority: number
  estimatedMinutes: number
  plannedDate?: string | null
  dueAt?: string | null
}
