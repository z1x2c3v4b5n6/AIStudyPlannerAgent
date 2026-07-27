<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { planApi } from '../../api/plan'
import type { PlanDetail, PlanItem, PlanItemStatus, PlanStatus } from '../../types/plan'
import { formatDateTime, minutesLabel } from '../../utils/display'

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
  actualMinutes: 1,
  feedback: '',
  completeTask: false,
})

const statusLabel: Record<PlanStatus, string> = {
  CONFIRMED: '已确认',
  COMPLETED: '全部完成',
  PARTIALLY_COMPLETED: '部分完成',
  ABANDONED: '已放弃',
  CANCELLED: '已取消',
}
const itemLabel: Record<PlanItemStatus, string> = {
  PENDING: '待执行',
  COMPLETED: '已完成',
  SKIPPED: '已跳过',
}
const completionTitle = computed(() =>
  completionItem.value ? `完成「${completionItem.value.taskTitle}」` : '完成计划项',
)

function openCompletion(item: PlanItem) {
  completionItem.value = item
  completionForm.actualMinutes = item.plannedMinutes
  completionForm.feedback = ''
  completionForm.completeTask = false
  completionVisible.value = true
}

async function confirmCompletion() {
  if (!props.plan || !completionItem.value) return
  completing.value = true
  try {
    const detail = (
      await planApi.completeItem(props.plan.id, completionItem.value.id, {
        actualMinutes: completionForm.actualMinutes,
        feedback: completionForm.feedback.trim() || null,
        completeTask: completionForm.completeTask,
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
  if (!props.plan) return
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
    size="min(720px, 94vw)"
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
        <el-descriptions-item label="执行结果">
          完成 {{ plan.completedItemCount }} 项 · 跳过 {{ plan.skippedItemCount }} 项 ·
          待执行 {{ plan.pendingItemCount }} 项
        </el-descriptions-item>
        <el-descriptions-item label="完成率">
          <el-progress :percentage="plan.completionPercentage" />
        </el-descriptions-item>
        <el-descriptions-item label="补充要求">{{ plan.requirement || '—' }}</el-descriptions-item>
        <el-descriptions-item label="摘要">{{ plan.summary }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatDateTime(plan.createdAt) }}</el-descriptions-item>
      </el-descriptions>

      <div class="plan-timeline execution-timeline">
        <article v-for="item in plan.items" :key="item.id" class="plan-timeline-item">
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
              · {{ item.subjectName }} · 计划 {{ minutesLabel(item.plannedMinutes) }}
            </p>
            <p v-if="item.actualMinutes" class="execution-result">
              实际 {{ minutesLabel(item.actualMinutes) }}
              <template v-if="item.feedback"> · {{ item.feedback }}</template>
            </p>
            <small>{{ item.reason }}</small>
          </div>
          <el-tag
            :type="item.status === 'COMPLETED' ? 'success' : item.status === 'SKIPPED' ? 'info' : 'warning'"
          >
            {{ itemLabel[item.status] }}
          </el-tag>
          <div v-if="plan.status !== 'CANCELLED'" class="plan-item-buttons">
            <template v-if="item.status === 'PENDING'">
              <el-button size="small" type="success" @click="openCompletion(item)">
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

  <el-dialog
    v-model="completionVisible"
    :title="completionTitle"
    width="min(500px, 92vw)"
    destroy-on-close
  >
    <div v-if="completionItem" class="completion-dialog-summary">
      <div><span>科目</span><strong>{{ completionItem.subjectName }}</strong></div>
      <div><span>任务</span><strong>{{ completionItem.taskTitle }}</strong></div>
      <div><span>计划时长</span><strong>{{ minutesLabel(completionItem.plannedMinutes) }}</strong></div>
    </div>
    <el-form label-position="top">
      <el-form-item label="实际学习时长（分钟）" required>
        <el-input-number
          v-model="completionForm.actualMinutes"
          :min="1"
          :max="720"
          class="full-width"
        />
      </el-form-item>
      <el-form-item label="学习反馈或备注">
        <el-input
          v-model="completionForm.feedback"
          type="textarea"
          :rows="3"
          maxlength="1000"
          show-word-limit
          placeholder="可填写完成情况、难点或下一步安排"
        />
      </el-form-item>
      <el-form-item>
        <el-checkbox v-model="completionForm.completeTask">
          同时将原学习任务标记为完成
        </el-checkbox>
        <div class="completion-task-hint">
          默认不勾选，适合本次计划只完成任务的一部分；勾选后会同步完成原任务。
        </div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :disabled="completing" @click="completionVisible = false">取消</el-button>
      <el-button type="primary" :loading="completing" @click="confirmCompletion">
        确认完成
      </el-button>
    </template>
  </el-dialog>
</template>
