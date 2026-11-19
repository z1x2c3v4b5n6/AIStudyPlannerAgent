<script setup lang="ts">
import type { JsonValue } from '../../api/agent'

defineProps<{ value: JsonValue }>()
const labels: Record<string, string> = {
  id: '编号', subjectId: '科目编号', goalId: '目标编号', taskId: '任务编号',
  title: '标题', taskTitle: '任务', name: '名称', description: '说明',
  priority: '优先级', estimatedMinutes: '预计分钟', plannedDate: '计划日期',
  dueAt: '截止时间', startAt: '开始时间', endAt: '结束时间', startTime: '开始时间',
  planDate: '计划日期', availableMinutes: '可用分钟', plannedMinutes: '计划分钟',
  summary: '摘要', status: '状态', items: '计划项', tasks: '任务', list: '结果',
  total: '共计', totalEstimatedMinutes: '预计总分钟', topic: '主题', keyPoints: '要点',
  before: '原值', after: '新值', message: '说明', code: '错误码',
  requirement: '学习需求', subjectName: '科目', totalMinutes: '总分钟',
}
const states: Record<string, string> = { TODO: '待办', IN_PROGRESS: '进行中', COMPLETED: '已完成', CANCELLED: '已取消' }
</script>

<template>
  <span v-if="value === null || value === undefined">—</span>
  <ul v-else-if="Array.isArray(value) && value.length" class="result-list">
    <li v-for="(item, index) in value" :key="index"><AgentData :value="item" /></li>
  </ul>
  <span v-else-if="Array.isArray(value)">暂无结果</span>
  <dl v-else-if="typeof value === 'object'" class="result-fields">
    <template v-for="(item, key) in value" :key="key">
      <dt>{{ labels[key] || key }}</dt>
      <dd><AgentData :value="item" /></dd>
    </template>
  </dl>
  <span v-else>{{ typeof value === 'boolean' ? (value ? '是' : '否') : states[String(value)] || value }}</span>
</template>

<style scoped>
.result-list { margin: 0; padding-left: 1.2rem; }
.result-list > li + li { margin-top: 0.6rem; }
.result-fields { display: grid; grid-template-columns: minmax(5rem, max-content) minmax(0, 1fr); gap: 0.25rem 0.6rem; margin: 0; }
.result-fields dt { font-size: 0.86rem; color: var(--el-text-color-secondary); overflow-wrap: anywhere; }
.result-fields dd { margin: 0; min-width: 0; overflow-wrap: anywhere; }
@media (max-width: 540px) { .result-fields { grid-template-columns: minmax(0, 1fr); } }
</style>
