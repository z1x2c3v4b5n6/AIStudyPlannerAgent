export function calculatePlanProgress(items) {
  const totalCount = items.length
  const completedCount = items.filter((item) => item.status === 'COMPLETED').length
  const skippedCount = items.filter((item) => item.status === 'SKIPPED').length
  const pendingCount = items.filter((item) => item.status === 'PENDING').length
  const completionPercentage =
    totalCount === 0
      ? 0
      : Math.round((completedCount * 10000) / totalCount) / 100

  return {
    totalCount,
    completedCount,
    skippedCount,
    pendingCount,
    completionPercentage
  }
}

export function singlePlanSummary(item) {
  if (item.status === 'COMPLETED') {
    return `已完成 · 实际学习${item.plannedMinutes}分钟`
  }
  if (item.status === 'SKIPPED') {
    return '已跳过 · 未产生学习记录'
  }
  return `待执行 · 计划${item.plannedMinutes}分钟`
}
