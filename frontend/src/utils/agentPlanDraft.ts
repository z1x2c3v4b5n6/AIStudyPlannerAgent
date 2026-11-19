import type { PlanDraft } from '../types/plan'

let pending: { owner: number; draft: PlanDraft } | undefined

export function passAgentPlanDraft(owner: number, draft: PlanDraft) {
  pending = { owner, draft }
}

export function takeAgentPlanDraft(owner: number | undefined) {
  const current = pending
  pending = undefined
  return current?.owner === owner ? current?.draft : undefined
}
