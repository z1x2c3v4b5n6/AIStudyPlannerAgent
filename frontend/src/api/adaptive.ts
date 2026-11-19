import http from './http'
import type { ApiResponse } from '../types/api'
import type { JsonValue } from './agent'

export interface AdaptiveProfile {
  window: { from: string; to: string }
  generatedAt: string
  activity: { totalMinutes: number; activeDays: number; averageDailyMinutes: number; recordCount: number }
  trend: { date: string; totalMinutes: number }[]
  subjectTime: { subjectId: number; subjectName: string; totalMinutes: number }[]
  subjects: { id: number; name: string }[]
  tasks: { dueCohort: number; dueCohortCompleted: number; completedInWindow: number; overdueNow: number }
  plans: { pairedItems: number; pairedPlannedMinutes: number; pairedActualMinutes: number; missingCompletedRecords: number }
  paths: { list: { id: number; title: string; progress: number; completedItems: number; totalItems: number }[] }
  goals: { list: { id: number; title: string; status: string }[] }
  recentRecords: { list: { id: number; feedback: string | null }[] }
  cohortCompletionRate: number | null
  pairedDeviationRatio: number | null
  sampleStatus: string
}
export interface Diagnostic {
  diagnosticId: string
  title: string
  severity: string
  description: string
  evidence: Record<string, number>
  sourceReferences: string[]
  recommendation: string
  ruleVersion: string
}
export interface ReviewCandidate {
  itemId: number
  subjectId: number | null
  topic: string
  lastStudy: string | null
  suggestedDate: string | null
  rating: string | null
  reason: string
  reviewTaskId: number | null
}
export interface PlanScheme {
  strategy: 'MINIMAL' | 'PRIORITY' | 'BALANCED'
  objective: string
  scheduled: { task: { id: number; title: string; plannedDate: string | null; minutes: number }; start: string; end: string }[]
  omitted: { taskId: number; title: string; reason: string }[]
  dailyMinutes: Record<string, number>
  changedDates: number
}
interface Preview<T> { id: string; digest: string; expiresAt: string; data: T }
const base = '/adaptive'
export const adaptiveApi = {
  profile: (params: { days?: number; from?: string; to?: string }) =>
    http.get<ApiResponse<AdaptiveProfile>>(base + '/profile', { params }),
  diagnostics: (days: number, dailyMinutes: number, from?: string, to?: string) =>
    http.get<ApiResponse<Diagnostic[]>>(base + '/diagnostics', { params: { days, dailyMinutes, from, to } }),
  plan: (payload: { start: string; days: number; dailyMinutes: number; preferredSubjectId: number | null;
    locked: { start: string; end: string }[] }) =>
    http.post<ApiResponse<Preview<{ schemes: PlanScheme[]; total: number; alreadyScheduled: number }>>>(base + '/plans/preview', payload),
  decidePlan: (proposal: Preview<unknown>, confirm: boolean, strategy: string) =>
    http.post<ApiResponse<{status: string; planIds?: number[]}>>(base + '/plans/' + proposal.id + '/decision',
      {digest: proposal.digest, confirm, strategy}),
  reviews: () => http.get<ApiResponse<ReviewCandidate[]>>(base + '/reviews'),
  feedback: (item: number, rating: string | null, deferredUntil: string | null, dismissed: boolean) =>
    http.put(base + '/reviews/' + item + '/feedback', {rating, deferredUntil, dismissed}),
  reviewPreview: (data: {itemId: number; date: string; minutes: number}[]) =>
    http.post<ApiResponse<Preview<JsonValue>>>(base + '/reviews/preview', data),
  decideReview: (proposal: Preview<unknown>, confirm: boolean) =>
    http.post<ApiResponse<{status: string; taskIds?: number[]}>>(base + '/reviews/' + proposal.id + '/decision', {digest: proposal.digest, confirm}),
}
