<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { planApi } from '../../api/plan'
import type { PlanDetail, PlanItem, PlanItemStatus, PlanStatus } from '../../types/plan'
import { addWallMinutes, shanghaiDateTime, wallTimeMillis } from '../../utils/businessTime'
import { formatDateTime, minutesLabel } from '../../utils/display'
import { calculatePlanProgress, singlePlanSummary } from '../../utils/planProgress.js'

const props = defineProps<{ modelValue: boolean; plan: PlanDetail | null }>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  changed: [plan: PlanDetail]
}>()

const changing = ref<number | null>(null)
const completing = ref(false)
const completionVisible = ref(false)
const completionItem = ref<PlanItem | null>(null)
const completionForm = reactive({
  actualStartAt: '',
  actualEndAt: '',
  feedback: '',
  completeTask: false
})

const actualRange = computed({
  get(): [string, string] | null {
    return completionForm.actualStartAt && completionForm.actualEndAt
      ? [completionForm.actualStartAt, completionForm.actualEndAt]
      : null
  },
  set(value: [string, string] | null) {
    completionForm.actualStartAt = value?.[0] || ''
    completionForm.actualEndAt = value?.[1] || ''
  }
})
const actualMinutes = computed(() => {
  const start = wallTimeMillis(completionForm.actualStartAt)
  const end = wallTimeMillis(completionForm.actualEndAt)
  if (!Number.isFinite(start) || !Number.isFinite(end) || end <= start) return 0
  return Math.floor((end - start) / 60_000)
})

const statusLabel: Record<PlanStatus, string> = {
  CONFIRMED: '已确认',
  COMPLETED: '全部完成',
  PARTIALLY_COMPLETED: '部分完成',
  ABANDONED: '已放弃',
  CANCELLED: '已取消'
}
const itemLabel: Record<PlanItemStatus, string> = {
  PENDING: '待执行',
  COMPLETED: '已完成',
  SKIPPED: '已跳过'
}

const progress = computed(() => calculatePlanProgress(props.plan?.items ?? []))
const totalCount = computed(() => progress.value.totalCount)
const completedCount = computed(() => progress.value.completedCount)
const skippedCount = computed(() => progress.value.skippedCount)
const pendingCount = computed(() => progress.value.pendingCount)
const completionPercentage = computed(() => progress.value.completionPercentage)
const singleItem = computed(() =>
  totalCount.value === 1 ? props.plan?.items[0] ?? null : null
)
const singleItemSummary = computed(() =>
  singleItem.value ? singlePlanSummary(singleItem.value) : ''
)
const completionTitle = computed(() =>
  completionItem.value ? `完成「${completionItem.value.taskTitle}」` : '完成计划项'
)

function openCompletion(item: PlanItem) {
  completionItem.value = item
  completionForm.actualEndAt = shanghaiDateTime()
  completionForm.actualStartAt = addWallMinutes(
    completionForm.actualEndAt,
    -item.plannedMinutes
  )
  completionForm.feedback = ''
  completionForm.completeTask = false
  completionVisible.value = true
}

function validateActualRange() {
  if (!completionForm.actualStartAt || !completionForm.actualEndAt) {
    return '请选择实际学习开始和结束时间'
  }
  if (wallTimeMillis(completionForm.actualEndAt) <= wallTimeMillis(completionForm.actualStartAt)) {
    return '实际学习开始时间必须早于结束时间'
  }
  if (completionForm.actualEndAt > shanghaiDateTime()) {
    return '实际学习结束时间不能晚于当前北京时间'
  }
  if (completionForm.actualStartAt.slice(0, 10) !== completionForm.actualEndAt.slice(0, 10)) {
    return '单次学习记录不能跨越自然日'
  }
  if (actualMinutes.value < 1 || actualMinutes.value > 720) {
    return '实际学习时长必须为1至720分钟'
  }
  return null
}

async function confirmCompletion() {
  if (completing.value || !props.plan || !completionItem.value) return
  const message = validateActualRange()
  if (message) {
    ElMessage.warning(message)
    return
  }
  completing.value = true
  try {
    const detail = (
      await planApi.completeItem(props.plan.id, completionItem.value.id, {
        actualStartAt: completionForm.actualStartAt,
        actualEndAt: completionForm.actualEndAt,
        feedback: completionForm.feedback.trim() || null,
        completeTask: completionForm.completeTask
      })
    ).data.data
    completionVisible.value = false
    emit('changed', detail)
    ElMessage.success('计划项已完成，学习记录已同步')
  } finally {
    completing.value = false
  }
}

async function change(item: PlanItem, status: PlanItemStatus) {
  if (changing.value !== null || !props.plan) return
  changing.value = item.id
  try {
    const detail = (
      await planApi.changeItemStatus(props.plan.id, item.id, status)
    ).data.data
    emit('changed', detail)
    ElMessage.success(status === 'SKIPPED' ? '计划项已跳过' : '计划项已恢复待执行')
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
        <el-descriptions-item label="计划日期">{{ plan.planDate }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ statusLabel[plan.status] }}</el-descriptions-item>
        <el-descriptions-item label="计划时长">
          {{ minutesLabel(plan.plannedMinutes) }} / {{ minutesLabel(plan.availableMinutes) }}
        </el-descriptions-item>
        <el-descriptions-item label="实际学习">
          {{ minutesLabel(plan.actualStudyMinutes) }}
        </el-descriptions-item>
        <el-descriptions-item label="补充要求">{{ plan.requirement || '—' }}</el-descriptions-item>
        <el-descriptions-item label="摘要">{{ plan.summary }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatDateTime(plan.createdAt) }}</el-descriptions-item>
      </el-descriptions>

      <section v-if="singleItem" class="plan-single-status" data-testid="single-plan-status">
        <span>执行状态</span>
        <strong>{{ singleItemSummary }}</strong>
      </section>

      <section v-else-if="totalCount > 1" class="plan-task-progress" data-testid="multi-plan-progress">
        <div class="plan-task-progress-heading">
          <div>
            <span>任务完成进度</span>
            <strong>已完成 {{ completedCount }} / 总任务 {{ totalCount }}</strong>
          </div>
          <strong>{{ completionPercentage }}%</strong>
        </div>
        <el-progress :percentage="completionPercentage" :show-text="false" />
        <div class="plan-task-progress-counts">
          <span>已完成 {{ completedCount }} 项</span>
          <span v-if="pendingCount > 0">待执行 {{ pendingCount }} 项</span>
          <span v-if="skippedCount > 0">已跳过 {{ skippedCount }} 项</span>
        </div>
      </section>

      <div class="plan-timeline">
        <article v-for="item in plan.items" :key="item.id" class="plan-timeline-item">
          <div class="draft-sequence">{{ item.sequenceNo }}</div>
          <div class="plan-item-content">
            <strong>
              <span class="color-dot" :style="{ backgroundColor: item.subjectColor || '#94a3b8' }" />
              {{ item.taskTitle }}
            </strong>
            <p>
              {{ item.startAt.slice(11, 16) }}—{{ item.endAt.slice(11, 16) }}
              · {{ item.subjectName }} · 计划 {{ minutesLabel(item.plannedMinutes) }}
            </p>
            <p v-if="item.status === 'COMPLETED'" class="execution-result">
              {{ item.actualMinutes == null ? '实际时长暂无记录' : `实际 ${minutesLabel(item.actualMinutes)}` }}
              <template v-if="item.feedback"> · {{ item.feedback }}</template>
            </p>
            <small>{{ item.reason }}</small>
          </div>
          <el-tag>{{ itemLabel[item.status] }}</el-tag>
          <div v-if="plan.status !== 'CANCELLED'" class="plan-item-buttons">
            <template v-if="item.status === 'PENDING'">
              <el-button size="small" type="success" @click="openCompletion(item)">完成</el-button>
              <el-button size="small" :loading="changing === item.id" @click="change(item, 'SKIPPED')">跳过</el-button>
            </template>
            <el-button v-else size="small" :loading="changing === item.id" @click="change(item, 'PENDING')">
              恢复待执行
            </el-button>
          </div>
        </article>
      </div>
    </template>
  </el-drawer>

  <el-dialog v-model="completionVisible" :title="completionTitle" width="min(500px, 92vw)" destroy-on-close>
    <div v-if="completionItem" class="completion-dialog-summary">
      <div><span>科目</span><strong>{{ completionItem.subjectName }}</strong></div>
      <div><span>任务</span><strong>{{ completionItem.taskTitle }}</strong></div>
      <div><span>计划时长</span><strong>{{ minutesLabel(completionItem.plannedMinutes) }}</strong></div>
    </div>
    <el-form label-position="top">
      <el-form-item label="实际学习时间（北京时间）" required>
        <el-date-picker
          v-model="actualRange"
          type="datetimerange"
          value-format="YYYY-MM-DDTHH:mm:ss"
          format="YYYY-MM-DD HH:mm"
          range-separator="至"
          start-placeholder="实际开始时间"
          end-placeholder="实际结束时间"
          class="full-width"
        />
      </el-form-item>
      <div class="actual-duration-preview">
        <span>根据时间范围自动计算</span>
        <strong>{{ actualMinutes > 0 ? minutesLabel(actualMinutes) : '请检查时间范围' }}</strong>
      </div>
      <p v-if="validateActualRange()" class="action-disabled-hint">{{ validateActualRange() }}</p>
      <el-form-item label="学习反馈或备注">
        <el-input v-model="completionForm.feedback" type="textarea" :rows="3" maxlength="1000" show-word-limit />
      </el-form-item>
      <el-form-item>
        <el-checkbox v-model="completionForm.completeTask">同时将原学习任务标记为完成</el-checkbox>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :disabled="completing" @click="completionVisible = false">取消</el-button>
      <el-button type="primary" :loading="completing" :disabled="Boolean(validateActualRange())" @click="confirmCompletion">
        确认完成
      </el-button>
    </template>
  </el-dialog>
</template>
