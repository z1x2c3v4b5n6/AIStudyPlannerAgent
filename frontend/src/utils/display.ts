import type { TagProps } from 'element-plus'
import type { GoalStatus } from '../types/goal'
import type { TaskStatus } from '../types/task'

type TagType = TagProps['type']

export const goalStatusMap: Record<GoalStatus, { label: string; type: TagType }> = {
  ACTIVE: { label: '进行中', type: 'primary' },
  COMPLETED: { label: '已完成', type: 'success' },
  CANCELLED: { label: '已取消', type: 'info' },
}

export const taskStatusMap: Record<TaskStatus, { label: string; type: TagType }> = {
  TODO: { label: '待开始', type: 'info' },
  IN_PROGRESS: { label: '进行中', type: 'primary' },
  COMPLETED: { label: '已完成', type: 'success' },
  CANCELLED: { label: '已取消', type: 'warning' },
}

export const priorityMap: Record<number, { label: string; type: TagType }> = {
  1: { label: '低', type: 'info' },
  2: { label: '中', type: 'primary' },
  3: { label: '高', type: 'warning' },
  4: { label: '紧急', type: 'danger' },
}

export function formatDateTime(value: string | null | undefined) {
  if (!value) return '—'
  return value.replace('T', ' ').slice(0, 16)
}

export function formatDate(value: string | null | undefined) {
  return value || '—'
}

export function minutesLabel(minutes: number) {
  if (minutes < 60) return `${minutes} 分钟`
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  return rest ? `${hours} 小时 ${rest} 分钟` : `${hours} 小时`
}

export function nullableText(value: string) {
  const normalized = value.trim()
  return normalized || null
}
