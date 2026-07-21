export interface Subject {
  id: number
  name: string
  description: string | null
  color: string | null
  sortOrder: number
  createdAt: string
  updatedAt: string
}

export interface SubjectPayload {
  name: string
  description?: string | null
  color?: string | null
  sortOrder?: number
}
