export const MAX_LEARNING_PATH_DAYS: number
export const LEARNING_PATH_DURATION_MESSAGE: string

export function maxLearningPathTargetDate(startDate: string): string | null
export function exceedsLearningPathDuration(startDate: string, targetDate: string | null): boolean
