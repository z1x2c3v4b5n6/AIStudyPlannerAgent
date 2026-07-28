import type { PlanItem, PlanItemStatus } from '../types/plan'

export interface PlanProgress {
  totalCount: number
  completedCount: number
  skippedCount: number
  pendingCount: number
  completionPercentage: number
}

export function calculatePlanProgress(
  items: Array<Pick<PlanItem, 'status'>>
): PlanProgress

export function singlePlanSummary(
  item: Pick<PlanItem, 'status' | 'plannedMinutes'>
): string
