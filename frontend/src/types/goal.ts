export type GoalStatus = 'ACTIVE' | 'COMPLETED' | 'CANCELLED'

export interface Goal {
  id: number
  subjectId: number
  title: string
  description: string | null
  targetMinutes: number
  targetDate: string | null
  status: GoalStatus
  createdAt: string
  updatedAt: string
}

export interface GoalDetail {
  goal: Goal
  taskCount: number
  completedTaskCount: number
  completionRate: number
}

export interface GoalQuery {
  page: number
  pageSize: number
  subjectId?: number
  status?: GoalStatus
}

export interface GoalCreatePayload {
  subjectId: number
  title: string
  description?: string | null
  targetMinutes: number
  targetDate?: string | null
  status: GoalStatus
}

export type GoalUpdatePayload = Omit<GoalCreatePayload, 'status'>
