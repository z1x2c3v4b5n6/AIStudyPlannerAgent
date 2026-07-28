<script setup lang="ts">
import { computed, ref } from 'vue'
import { planApi } from '../../api/plan'
import type { PlanDetail, PlanItem, PlanItemStatus } from '../../types/plan'
import { formatDateTime, minutesLabel } from '../../utils/display'
import {
  calculatePlanProgress,
  singlePlanSummary
} from '../../utils/planProgress.js'

const props = defineProps<{
  modelValue: boolean
  plan: PlanDetail | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  changed: [plan: PlanDetail]
}>()

const changing = ref<number | null>(null)
const statusLabel = {
  CONFIRMED: '已确认',
  COMPLETED: '已完成',
  CANCELLED: '已取消'
} as const
const itemLabel = {
  PENDING: '待执行',
  COMPLETED: '已完成',
  SKIPPED: '已跳过'
} as const

const progress = computed(() => calculatePlanProgress(props.plan?.items ?? []))
const totalCount = computed(() => progress.value.totalCount)
const completedCount = computed(() => progress.value.completedCount)
const skippedCount = computed(() => progress.value.skippedCount)
const pendingCount = computed(() => progress.value.pendingCount)
const completionPercentage = computed(
  () => progress.value.completionPercentage
)
const singleItem = computed(() =>
  totalCount.value === 1 ? props.plan?.items[0] ?? null : null
)

const singleItemSummary = computed(() => {
  const item = singleItem.value
  return item ? singlePlanSummary(item) : ''
})

async function change(item: PlanItem, status: PlanItemStatus) {
  if (!props.plan) return
  changing.value = item.id
  try {
    const detail = (
      await planApi.changeItemStatus(props.plan.id, item.id, status)
    ).data.data
    emit('changed', detail)
  } finally {
    changing.value = null
  }
}
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    title="学习计划详情"
    size="min(640px, 92vw)"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <template v-if="plan">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="计划日期">
          {{ plan.planDate }}
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          {{ statusLabel[plan.status] }}
        </el-descriptions-item>
        <el-descriptions-item label="时长">
          {{ minutesLabel(plan.plannedMinutes) }} /
          {{ minutesLabel(plan.availableMinutes) }}
        </el-descriptions-item>
        <el-descriptions-item label="补充要求">
          {{ plan.requirement || '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="摘要">
          {{ plan.summary }}
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">
          {{ formatDateTime(plan.createdAt) }}
        </el-descriptions-item>
      </el-descriptions>

      <section
        v-if="singleItem"
        class="plan-single-status"
        data-testid="single-plan-status"
      >
        <span>执行状态</span>
        <strong>{{ singleItemSummary }}</strong>
      </section>

      <section
        v-else-if="totalCount > 1"
        class="plan-task-progress"
        data-testid="multi-plan-progress"
      >
        <div class="plan-task-progress-heading">
          <div>
            <span>任务完成进度</span>
            <strong>已完成 {{ completedCount }} / 总任务 {{ totalCount }}</strong>
          </div>
          <strong>{{ completionPercentage }}%</strong>
        </div>
        <el-progress
          :percentage="completionPercentage"
          :show-text="false"
          data-testid="task-completion-progress"
        />
        <div class="plan-task-progress-counts">
          <span>已完成 {{ completedCount }}</span>
          <span v-if="pendingCount > 0">待执行 {{ pendingCount }}</span>
          <span v-if="skippedCount > 0">已跳过 {{ skippedCount }}</span>
        </div>
      </section>

      <div class="plan-timeline">
        <article
          v-for="item in plan.items"
          :key="item.id"
          class="plan-timeline-item"
        >
          <div class="draft-sequence">{{ item.sequenceNo }}</div>
          <div class="plan-item-content">
            <strong>
              <span
                class="color-dot"
                :style="{ backgroundColor: item.subjectColor || '#94a3b8' }"
              />
              {{ item.taskTitle }}
            </strong>
            <p>
              {{ item.startAt.slice(11, 16) }}—{{ item.endAt.slice(11, 16) }}
              · {{ item.subjectName }} · {{ minutesLabel(item.plannedMinutes) }}
            </p>
            <small>{{ item.reason }}</small>
          </div>
          <el-tag>{{ itemLabel[item.status] }}</el-tag>
          <div v-if="plan.status !== 'CANCELLED'" class="plan-item-buttons">
            <template v-if="item.status === 'PENDING'">
              <el-button
                size="small"
                type="success"
                :loading="changing === item.id"
                @click="change(item, 'COMPLETED')"
              >
                完成
              </el-button>
              <el-button
                size="small"
                :loading="changing === item.id"
                @click="change(item, 'SKIPPED')"
              >
                跳过
              </el-button>
            </template>
            <el-button
              v-else
              size="small"
              :loading="changing === item.id"
              @click="change(item, 'PENDING')"
            >
              恢复待执行
            </el-button>
          </div>
        </article>
      </div>
    </template>
  </el-drawer>
</template>
