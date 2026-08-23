export const MAX_LEARNING_PATH_DAYS = 10
export const LEARNING_PATH_DURATION_MESSAGE = '单次学习路径最长支持10天，长期目标建议拆分为多个阶段。'

function utcDate(value) {
  const [year, month, day] = value.split('-').map(Number)
  return Date.UTC(year, month - 1, day)
}

function formatUtcDate(value) {
  return new Date(value).toISOString().slice(0, 10)
}

export function maxLearningPathTargetDate(startDate) {
  return startDate ? formatUtcDate(utcDate(startDate) + (MAX_LEARNING_PATH_DAYS - 1) * 86_400_000) : null
}

export function exceedsLearningPathDuration(startDate, targetDate) {
  if (!startDate || !targetDate) return false
  return Math.round((utcDate(targetDate) - utcDate(startDate)) / 86_400_000) + 1 > MAX_LEARNING_PATH_DAYS
}
