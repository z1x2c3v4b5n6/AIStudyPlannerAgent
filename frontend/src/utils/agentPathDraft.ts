import type { LearningPathDraft } from '../types/learningPath'

let pending: { owner: number; draft: LearningPathDraft } | undefined

export function passAgentPathDraft(owner: number, draft: LearningPathDraft) {
  pending = { owner, draft }
}

export function takeAgentPathDraft(owner: number | undefined) {
  const entry = pending
  pending = undefined
  return entry?.owner === owner ? entry?.draft : undefined
}
