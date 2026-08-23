<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { planApi } from '../../api/plan'
import type {
  NaturalLanguagePlanParseResult,
  PlanDetail,
  PlanDraft,
  PlanDraftItem,
  PlanGeneratorType,
  SelectablePlanTask,
} from '../../types/plan'
import { addWallMinutes, shanghaiDate } from '../../utils/businessTime'
import { formatDateTime, minutesLabel, nullableText, priorityMap } from '../../utils/display'

type GenerationMode = 'AI' | 'RULE'

interface GenerationMeta {
  requestedMode: GenerationMode
  generatorType: PlanGeneratorType
  provider: string | null
  model: string | null
  fallbackUsed: boolean
  fallbackReason: string | null
}

const examples = [
  '帮我安排今天晚上两小时',
  '优先处理快到期的任务',
  '今天状态不好，安排轻松一点',
  '帮我准备 Java 后端面试',
]

const emit = defineEmits<{ confirmed: [plan: PlanDetail] }>()
const naturalLanguage = ref('')
const parsed = ref<NaturalLanguagePlanParseResult | null>(null)
const parsing = ref(false)
const advancedVisible = ref<string[]>([])
const form = reactive({
  planDate: shanghaiDate(),
  startTime: '09:00:00',
  availableMinutes: 120,
  requirement: '',
  preferences: [] as string[],
  selectedSubjectIds: [] as number[],
  selectedTaskIds: [] as number[],
})
const generationMode = ref<GenerationMode>('AI')
const generationMeta = ref<GenerationMeta | null>(null)
const draft = ref<PlanDraft | null>(null)
const original = ref<PlanDraft | null>(null)
const generating = ref(false)
const quickCreating = ref(false)
const confirming = ref(false)
const tasksLoading = ref(false)
const candidateTasks = ref<SelectablePlanTask[]>([])
const scopeVisible = ref(false)

const remaining = computed(() =>
  draft.value ? draft.value.availableMinutes - draft.value.plannedMinutes : 0,
)
const generateButtonText = computed(() =>
  generating.value || quickCreating.value
    ? '正在为你生成计划...'
    : generationMode.value === 'AI'
      ? 'AI生成今日计划'
      : '生成规则计划',
)
const generateDisabled = computed(
  () => !naturalLanguage.value.trim() || parsing.value || generating.value || quickCreating.value,
)
const generateDisabledReason = computed(() => {
  if (!naturalLanguage.value.trim()) return '请先输入你的学习安排，主按钮才可生成计划'
  if (parsing.value) return '正在理解你的学习需求'
  if (generating.value || quickCreating.value) return '正在处理，请勿重复提交'
  return ''
})
const taskGroups = computed(() => {
  const groups = new Map<number, { subjectName: string; subjectColor: string | null; tasks: SelectablePlanTask[] }>()
  candidateTasks.value.forEach((task) => {
    const group = groups.get(task.subjectId) || {
      subjectName: task.subjectName,
      subjectColor: task.subjectColor,
      tasks: [],
    }
    group.tasks.push(task)
    groups.set(task.subjectId, group)
  })
  return [...groups.entries()].map(([subjectId, group]) => ({ subjectId, ...group }))
})
const selectedSubjects = computed(() =>
  parsed.value?.candidateSubjects.filter((subject) =>
    form.selectedSubjectIds.includes(subject.subjectId),
  ) || [],
)
const selectedSubjectLabel = computed(() => {
  const names = selectedSubjects.value.map((subject) => subject.subjectName)
  if (!names.length) return '尚未确定学习科目'
  return names.length > 2 ? `${names.slice(0, 2).join('、')} 等 ${names.length} 个科目` : names.join('、')
})
const scopeHint = computed(() => {
  if (!parsed.value) return '生成后可按需调整科目和任务'
  if (parsed.value.needsSubjectSelection) return '存在科目歧义，请确认学习范围'
  if (form.selectedTaskIds.length) return `已选择 ${form.selectedTaskIds.length} 个任务`
  if (form.selectedSubjectIds.length) return '将优先使用所选科目的推荐任务'
  return 'AI 将根据需求推荐学习范围'
})
const scopeApplyDisabled = computed(
  () => !form.selectedSubjectIds.length || !form.selectedTaskIds.length,
)
const scopeApplyHint = computed(() => {
  if (!form.selectedSubjectIds.length) return '请先选择至少一个有待办任务的科目'
  if (!form.selectedTaskIds.length) return '请加载任务并至少选择一个任务'
  return `将应用 ${form.selectedSubjectIds.length} 个科目、${form.selectedTaskIds.length} 个任务`
})

function clone(value: PlanDraft) {
  return structuredClone(value)
}

function useExample(example: string) {
  naturalLanguage.value = example
  resetSelection()
}

function resetSelection() {
  parsed.value = null
  form.selectedSubjectIds = []
  form.selectedTaskIds = []
  candidateTasks.value = []
}

async function parseRequirement() {
  const text = naturalLanguage.value.trim()
  if (!text) {
    ElMessage.warning('请先告诉 AI 你准备怎么学习')
    return null
  }

  parsing.value = true
  try {
    const result = (
      await planApi.parseNaturalLanguage({
        text,
        fallbackPlanDate: form.planDate || null,
        fallbackStartTime: form.startTime || null,
        fallbackAvailableMinutes: form.availableMinutes || null,
      })
    ).data.data
    parsed.value = result
    if (result.planDate) form.planDate = result.planDate
    if (result.startTime) form.startTime = result.startTime
    if (result.availableMinutes) form.availableMinutes = result.availableMinutes
    form.requirement = result.requirement || text
    form.preferences = [...result.preferences]
    form.selectedSubjectIds = [...result.selectedSubjectIds]
    form.selectedTaskIds = []
    candidateTasks.value = []
    if (result.needsClarification) {
      advancedVisible.value = ['advanced']
      ElMessage.warning('有些信息需要你确认或补充')
    }
    return result
  } catch {
    const fallbackResult: NaturalLanguagePlanParseResult = {
      planDate: form.planDate,
      startTime: form.startTime,
      availableMinutes: form.availableMinutes,
      requirement: text,
      preferences: [],
      topicKeywords: [],
      candidateSubjects: [],
      ambiguousTopics: [],
      needsSubjectSelection: true,
      selectedSubjectIds: [],
      unmatchedKeywords: [],
      needsClarification: true,
      clarificationMessage: 'AI 暂时无法解析，请检查高级设置后继续',
      aiParsed: false,
    }
    parsed.value = fallbackResult
    form.requirement = text
    advancedVisible.value = ['advanced']
    return fallbackResult
  } finally {
    parsing.value = false
  }
}

function selectAllRelatedSubjects() {
  if (!parsed.value) return
  form.selectedSubjectIds = parsed.value.candidateSubjects
    .filter((subject) => subject.pendingTaskCount > 0)
    .map((subject) => subject.subjectId)
}

function selectRecommendedSubjects() {
  if (!parsed.value) return
  form.selectedSubjectIds = parsed.value.candidateSubjects
    .filter((subject) => subject.recommended && subject.pendingTaskCount > 0)
    .map((subject) => subject.subjectId)
}

function toggleSubject(subjectId: number, disabled = false) {
  if (disabled) return
  form.selectedSubjectIds = form.selectedSubjectIds.includes(subjectId)
    ? form.selectedSubjectIds.filter((id) => id !== subjectId)
    : [...form.selectedSubjectIds, subjectId]
  candidateTasks.value = []
  form.selectedTaskIds = []
}

async function loadCandidateTasks() {
  if (!form.selectedSubjectIds.length) {
    return ElMessage.warning('请至少选择一个有待办任务的科目')
  }
  const selectedSubjects = parsed.value?.candidateSubjects.filter((subject) =>
    form.selectedSubjectIds.includes(subject.subjectId),
  ) || []
  if (!selectedSubjects.some((subject) => subject.pendingTaskCount > 0)) {
    return ElMessage.warning('所选科目没有可用的待办任务')
  }

  tasksLoading.value = true
  try {
    candidateTasks.value = (
      await planApi.candidateTasks({
        selectedSubjectIds: form.selectedSubjectIds,
        planDate: form.planDate,
        requirement: nullableText(form.requirement),
      })
    ).data.data
    form.selectedTaskIds = candidateTasks.value
      .filter((task) => task.recommended)
      .map((task) => task.taskId)
    if (!candidateTasks.value.length) {
      ElMessage.warning('所选科目在该日期没有可用任务')
    } else if (!form.selectedTaskIds.length) {
      ElMessage.info('暂无自动推荐任务，请手动勾选要安排的任务')
    }
  } finally {
    tasksLoading.value = false
  }
}

function selectAllTasks() {
  form.selectedTaskIds = candidateTasks.value.map((task) => task.taskId)
}

function clearSelectedTasks() {
  form.selectedTaskIds = []
}

async function openScope() {
  scopeVisible.value = true
  if (form.selectedSubjectIds.length && !candidateTasks.value.length) {
    await loadCandidateTasks()
  }
}

function applyScope() {
  if (!form.selectedSubjectIds.length) {
    return ElMessage.warning('请至少选择一个有待办任务的科目')
  }
  if (candidateTasks.value.length && !form.selectedTaskIds.length) {
    return ElMessage.warning('请至少选择一个要安排的任务')
  }
  scopeVisible.value = false
  ElMessage.success('学习范围已更新')
}

function recalculate() {
  if (!draft.value) return
  let cursor = `${draft.value.planDate}T${draft.value.startTime}`
  draft.value.items.forEach((item, index) => {
    item.sequenceNo = index + 1
    item.startAt = cursor
    item.endAt = addWallMinutes(cursor, item.plannedMinutes)
    cursor = item.endAt
  })
  draft.value.plannedMinutes = draft.value.items.reduce(
    (sum, item) => sum + item.plannedMinutes,
    0,
  )
  draft.value.summary = `共安排 ${draft.value.items.length} 项任务，计划学习 ${draft.value.plannedMinutes} 分钟`
}

function move(index: number, offset: number) {
  if (!draft.value) return
  const target = index + offset
  if (target < 0 || target >= draft.value.items.length) return
  ;[draft.value.items[index], draft.value.items[target]] = [
    draft.value.items[target],
    draft.value.items[index],
  ]
  recalculate()
}

function durationChanged(item: PlanDraftItem) {
  item.plannedMinutes = Math.max(15, Math.round(item.plannedMinutes || 15))
  recalculate()
  if (draft.value && draft.value.plannedMinutes > draft.value.availableMinutes) {
    ElMessage.warning('已安排时长不能超过可用时长')
  }
}

function validateForm() {
  if (!form.planDate) return '请选择计划日期'
  if (!form.startTime) return '请选择开始时间'
  if (form.planDate < shanghaiDate()) return '计划日期不能早于今天'
  if (form.availableMinutes < 1 || form.availableMinutes > 720) {
    return '可用时长必须为 1 至 720 分钟'
  }
  const end = addWallMinutes(`${form.planDate}T${form.startTime}`, form.availableMinutes)
  if (end.slice(0, 10) !== form.planDate) return '计划时间不能跨越自然日'
  return null
}

function quickCreateTopic() {
  const selectedSubject = parsed.value?.candidateSubjects.find((subject) =>
    form.selectedSubjectIds.includes(subject.subjectId),
  )
  const topic =
    selectedSubject?.subjectName ||
    parsed.value?.unmatchedKeywords[0] ||
    parsed.value?.topicKeywords[0] ||
    naturalLanguage.value.trim().slice(0, 100)
  return topic.trim()
}

async function quickCreateAndGenerate() {
  const validationMessage = validateForm()
  if (validationMessage) {
    advancedVisible.value = ['advanced']
    return ElMessage.warning(validationMessage)
  }
  const topicName = quickCreateTopic()
  if (!topicName) return ElMessage.warning('未识别出学习主题，请补充要学习的内容')
  const taskTitle = `学习${topicName}`.slice(0, 200)
  const estimatedMinutes = Math.min(720, Math.max(1, form.availableMinutes))

  try {
    await ElMessageBox.confirm(
      `将创建：\n科目：${topicName}\n任务：${taskTitle}\n预计时长：${estimatedMinutes}分钟\n计划日期：${form.planDate}`,
      '一键创建并生成计划',
      {
        confirmButtonText: '创建并继续',
        cancelButtonText: '取消',
        type: 'info',
        customClass: 'quick-create-confirm',
      },
    )
  } catch {
    return
  }

  quickCreating.value = true
  let taskCreated = false
  try {
    const result = (
      await planApi.quickCreateTask({
        topicName,
        taskTitle,
        planDate: form.planDate,
        estimatedMinutes,
        requirement: nullableText(form.requirement || naturalLanguage.value),
      })
    ).data.data
    taskCreated = true
    form.selectedSubjectIds = [result.subjectId]
    form.selectedTaskIds = [result.taskId]
    candidateTasks.value = [
      {
        taskId: result.taskId,
        taskTitle: result.taskTitle,
        subjectId: result.subjectId,
        subjectName: result.subjectName,
        subjectColor: '#409EFF',
        goalTitle: null,
        estimatedMinutes,
        deadline: null,
        priority: 2,
        status: 'TODO',
        matchReason: result.createdTask ? '根据当前学习要求一键创建' : '复用已有同名待办任务',
        recommended: true,
      },
    ]
    await submitGeneration()
  } catch {
    if (taskCreated) {
      ElMessage.warning('任务已创建并保留，但计划生成失败，请稍后点击主按钮重新生成')
    }
  } finally {
    quickCreating.value = false
  }
}

async function submitGeneration() {
  const requestedMode = generationMode.value
  const preferenceText = form.preferences.length
    ? `偏好：${form.preferences.join('、')}`
    : ''
  const requirement = [form.requirement.trim(), preferenceText].filter(Boolean).join('；')
  const payload = {
    planDate: form.planDate,
    startTime: form.startTime,
    availableMinutes: form.availableMinutes,
    requirement: nullableText(requirement || naturalLanguage.value),
    selectedSubjectIds: form.selectedSubjectIds,
    selectedTaskIds: form.selectedTaskIds,
  }

  generating.value = true
  try {
    if (requestedMode === 'AI') {
      const result = (await planApi.generateAiDraft(payload)).data.data
      draft.value = clone(result.draft)
      original.value = clone(result.draft)
      generationMeta.value = {
        requestedMode,
        generatorType: result.generatorType,
        provider: result.provider,
        model: result.model,
        fallbackUsed: result.fallbackUsed,
        fallbackReason: result.fallbackReason,
      }
    } else {
      const result = (await planApi.generateDraft(payload)).data.data
      draft.value = clone(result)
      original.value = clone(result)
      generationMeta.value = {
        requestedMode,
        generatorType: 'RULE',
        provider: null,
        model: null,
        fallbackUsed: false,
        fallbackReason: null,
      }
    }
  } finally {
    generating.value = false
  }
}

async function generate() {
  const validationMessage = validateForm()
  if (validationMessage) {
    advancedVisible.value = ['advanced']
    return ElMessage.warning(validationMessage)
  }

  if (!parsed.value) {
    const result = await parseRequirement()
    if (!result) return
  }

  if (!form.selectedSubjectIds.length) {
    if (parsed.value?.ambiguousTopics.length && parsed.value.candidateSubjects.length > 1) {
      scopeVisible.value = true
      return ElMessage.warning('请在右侧抽屉中确认要学习的科目')
    }
    if (parsed.value?.candidateSubjects.length === 1) {
      form.selectedSubjectIds = [parsed.value.candidateSubjects[0].subjectId]
    } else {
      return quickCreateAndGenerate()
    }
  }

  if (!candidateTasks.value.length) {
    tasksLoading.value = true
    try {
      candidateTasks.value = (
        await planApi.candidateTasks({
          selectedSubjectIds: form.selectedSubjectIds,
          planDate: form.planDate,
          requirement: nullableText(form.requirement),
        })
      ).data.data
    } finally {
      tasksLoading.value = false
    }
  }

  if (!candidateTasks.value.length) return quickCreateAndGenerate()
  if (!form.selectedTaskIds.length) {
    const recommended = candidateTasks.value.filter((task) => task.recommended)
    form.selectedTaskIds = (recommended.length ? recommended : candidateTasks.value.slice(0, 1))
      .map((task) => task.taskId)
  }
  await submitGeneration()
}

async function confirm() {
  if (!draft.value?.items.length) return ElMessage.warning('空草案不能确认')
  if (draft.value.plannedMinutes > draft.value.availableMinutes) {
    return ElMessage.warning('已安排时长超过可用时长')
  }
  confirming.value = true
  try {
    const plan = (
      await planApi.confirm({
        draftId: draft.value.draftId,
        planDate: draft.value.planDate,
        availableMinutes: draft.value.availableMinutes,
        plannedMinutes: draft.value.plannedMinutes,
        requirement: draft.value.requirement,
        summary: draft.value.summary,
        items: draft.value.items.map(
          ({ sequenceNo, taskId, startAt, endAt, plannedMinutes, reason }) => ({
            sequenceNo,
            taskId,
            startAt,
            endAt,
            plannedMinutes,
            reason,
          }),
        ),
      })
    ).data.data
    ElMessage.success('学习计划已保存')
    draft.value = null
    original.value = null
    generationMeta.value = null
    parsed.value = null
    emit('confirmed', plan)
  } finally {
    confirming.value = false
  }
}
</script>

<template>
  <div class="natural-plan-entry">
    <el-card shadow="never" class="panel-card natural-plan-card">
      <div class="natural-plan-heading">
        <div>
          <span class="ai-kicker">AI STUDY PLANNER</span>
          <h2>告诉 AI 你准备怎么学习</h2>
          <p>用一句话描述时间、重点和节奏，AI 会先整理需求，再从你的待办任务中安排计划。</p>
        </div>
        <el-radio-group v-model="generationMode" :disabled="generating || parsing" class="plan-mode-switch">
          <el-radio-button value="AI">AI 智能规划</el-radio-button>
          <el-radio-button value="RULE">规则规划</el-radio-button>
        </el-radio-group>
      </div>

      <el-input
        v-model="naturalLanguage"
        type="textarea"
        :rows="5"
        maxlength="1000"
        resize="none"
        placeholder="例如：我明天上午 9 点开始学习 3 小时，优先复习 Java 集合和并发，节奏轻松一点。"
        class="natural-plan-input"
        @input="resetSelection"
      />

      <div class="prompt-examples">
        <span>试试这样说</span>
        <button v-for="example in examples" :key="example" type="button" @click="useExample(example)">
          {{ example }}
        </button>
      </div>

      <div class="natural-plan-actions">
        <el-button type="primary" plain :loading="parsing" :disabled="parsing || generating" @click="parseRequirement">
          理解我的需求
        </el-button>
        <span>优先使用已有待办；没有匹配内容时，可经你确认后一键创建科目和任务。</span>
      </div>

      <div v-if="parsed" class="plan-understanding">
        <div class="understanding-title">
          <div><span class="understanding-check">✓</span><strong>AI 理解的需求</strong></div>
          <el-tag v-if="parsed.needsClarification" type="warning">需要确认</el-tag>
          <el-tag v-else type="success">信息完整</el-tag>
        </div>
        <el-alert
          v-if="parsed.clarificationMessage"
          :title="parsed.clarificationMessage"
          :type="parsed.needsClarification ? 'warning' : 'info'"
          :closable="false"
          show-icon
        />
        <div class="understanding-summary">
          <span><small>日期</small><strong>{{ form.planDate }}</strong></span>
          <span><small>开始</small><strong>{{ form.startTime.slice(0, 5) }}</strong></span>
          <span><small>可用时长</small><strong>{{ minutesLabel(form.availableMinutes) }}</strong></span>
          <span class="understanding-focus">
            <small>重点与节奏</small>
            <strong>{{ form.requirement || form.preferences.join('、') || '按任务优先级合理安排' }}</strong>
          </span>
        </div>
      </div>

      <div v-if="parsed" class="scope-summary-card">
        <div class="scope-summary-main">
          <span class="scope-icon" aria-hidden="true">◎</span>
          <div>
            <small>学习范围</small>
            <strong>{{ selectedSubjectLabel }}</strong>
            <p>{{ scopeHint }}</p>
          </div>
        </div>
        <el-button type="primary" plain @click="openScope">调整学习范围</el-button>
      </div>

      <el-alert
        v-if="parsed?.unmatchedKeywords.length"
        :title="`没有找到与“${parsed.unmatchedKeywords.join('、')}”相关的待办任务，不会使用无关内容。`"
        type="warning"
        :closable="false"
        show-icon
        class="compact-plan-alert"
      >
        <el-button link type="primary" :loading="quickCreating || generating" @click="quickCreateAndGenerate">
          一键创建并生成计划
        </el-button>
      </el-alert>

      <el-collapse v-model="advancedVisible" class="plan-advanced">
        <el-collapse-item title="高级设置" name="advanced">
          <p class="advanced-hint">当自然语言信息不完整时，这些值会作为明确兜底；生成前仍可随时修改。</p>
          <div class="understanding-grid">
            <el-form-item label="计划日期">
              <el-date-picker v-model="form.planDate" type="date" value-format="YYYY-MM-DD" class="full-width" />
            </el-form-item>
            <el-form-item label="开始时间（北京时间）">
              <el-time-picker v-model="form.startTime" value-format="HH:mm:ss" class="full-width" />
            </el-form-item>
            <el-form-item label="可用时长（分钟）">
              <el-input-number v-model="form.availableMinutes" :min="1" :max="720" class="full-width" />
            </el-form-item>
          </div>
          <el-form-item label="重点内容和顺序">
            <el-input v-model="form.requirement" type="textarea" :rows="2" maxlength="1000" show-word-limit />
          </el-form-item>
          <el-form-item label="节奏或偏好">
            <el-select v-model="form.preferences" multiple filterable allow-create default-first-option placeholder="例如：轻松一点、先难后易" class="full-width" />
          </el-form-item>
        </el-collapse-item>
      </el-collapse>

      <div class="generate-plan-row">
        <div>
          <strong>{{ form.planDate }} · {{ form.startTime.slice(0, 5) }}</strong>
          <span>可用 {{ minutesLabel(form.availableMinutes) }}</span>
        </div>
        <el-button
          type="primary"
          size="large"
          class="generate-plan-primary primary-action"
          :loading="generating || quickCreating"
          :disabled="generateDisabled"
          @click="generate"
        >
          {{ generateButtonText }}
        </el-button>
      </div>
      <p v-if="generateDisabledReason" class="plan-action-reason">
        {{ generateDisabledReason }}
      </p>
      <p v-else class="plan-action-reason ready">
        点击后会自动解析需求、推荐任务；遇到歧义或无匹配内容时再请你确认。
      </p>
    </el-card>
  </div>

  <el-drawer
    v-model="scopeVisible"
    title="调整学习范围"
    size="min(620px, 94vw)"
    class="scope-drawer"
    destroy-on-close
  >
    <div v-if="parsed" v-loading="tasksLoading" class="scope-drawer-content">
      <section class="scope-drawer-section">
        <div class="selection-section-heading">
          <div>
            <span class="selection-step">1</span>
            <div>
              <strong>选择学习科目</strong>
              <p v-if="parsed.ambiguousTopics.length">“{{ parsed.ambiguousTopics.join('、') }}”对应多个科目，请由你确认。</p>
              <p v-else>AI 已按主题匹配，你可以随时修改。</p>
            </div>
          </div>
          <span>已选 {{ form.selectedSubjectIds.length }} 个</span>
        </div>
        <div v-if="parsed.candidateSubjects.length" class="subject-selection-actions">
          <el-button size="small" @click="selectAllRelatedSubjects">全部相关科目</el-button>
          <el-button size="small" @click="selectRecommendedSubjects">按当前进度推荐</el-button>
        </div>
        <div class="subject-choice-list">
          <div
            v-for="subject in parsed.candidateSubjects"
            :key="subject.subjectId"
            class="subject-choice"
            :class="{ disabled: subject.pendingTaskCount === 0, selected: form.selectedSubjectIds.includes(subject.subjectId) }"
            role="checkbox"
            :aria-checked="form.selectedSubjectIds.includes(subject.subjectId)"
            :aria-disabled="subject.pendingTaskCount === 0"
            :tabindex="subject.pendingTaskCount === 0 ? -1 : 0"
            @click="toggleSubject(subject.subjectId, subject.pendingTaskCount === 0)"
            @keydown.space.prevent="toggleSubject(subject.subjectId, subject.pendingTaskCount === 0)"
          >
            <el-checkbox
              :model-value="form.selectedSubjectIds.includes(subject.subjectId)"
              :disabled="subject.pendingTaskCount === 0"
              aria-label="选择科目"
              @click.stop
              @change="toggleSubject(subject.subjectId, subject.pendingTaskCount === 0)"
            />
            <span class="color-dot" :style="{ backgroundColor: subject.subjectColor || '#94a3b8' }" />
            <span class="subject-choice-main"><strong>{{ subject.subjectName || '未命名科目' }}</strong><small>{{ subject.matchReason || '与当前学习主题相关' }}</small></span>
            <el-tag v-if="subject.recommended" size="small" type="success">推荐</el-tag>
            <span class="subject-task-count">{{ subject.pendingTaskCount ? `${subject.pendingTaskCount} 个待办任务` : '没有可用任务' }}</span>
          </div>
        </div>
        <el-empty v-if="!parsed.candidateSubjects.length" description="没有匹配科目，可返回修改学习主题" :image-size="70" />
        <el-button type="primary" class="primary-action" :loading="tasksLoading" :disabled="!form.selectedSubjectIds.length" @click="loadCandidateTasks">
          加载所选科目的任务
        </el-button>
        <p v-if="!form.selectedSubjectIds.length" class="action-disabled-hint">请先勾选至少一个有待办任务的科目</p>
      </section>

      <section v-if="candidateTasks.length" class="scope-drawer-section">
        <div class="selection-section-heading">
          <div><span class="selection-step">2</span><div><strong>选择计划任务</strong><p>推荐原因仅供参考，最终范围由你决定。</p></div></div>
          <span>已选 {{ form.selectedTaskIds.length }} / {{ candidateTasks.length }}</span>
        </div>
        <div class="task-selection-actions">
          <el-button size="small" @click="selectAllTasks">全选</el-button>
          <el-button size="small" @click="clearSelectedTasks">清空</el-button>
        </div>
        <el-checkbox-group v-model="form.selectedTaskIds" class="selectable-task-groups">
          <section v-for="group in taskGroups" :key="group.subjectId" class="selectable-task-group">
            <header><span class="color-dot" :style="{ backgroundColor: group.subjectColor || '#94a3b8' }" /><strong>{{ group.subjectName }}</strong><small>{{ group.tasks.length }} 个可用任务</small></header>
            <label v-for="task in group.tasks" :key="task.taskId" class="selectable-task">
              <el-checkbox :value="task.taskId" />
              <span class="selectable-task-main">
                <strong>{{ task.taskTitle }}</strong>
                <small>{{ task.goalTitle || '未关联目标' }} · 预计 {{ minutesLabel(task.estimatedMinutes) }}<template v-if="task.deadline"> · 截止 {{ formatDateTime(task.deadline) }}</template></small>
                <em>{{ task.matchReason }}</em>
              </span>
              <el-tag v-if="task.recommended" size="small" type="success">推荐</el-tag>
              <el-tag size="small" :type="priorityMap[task.priority].type">{{ priorityMap[task.priority].label }}</el-tag>
            </label>
          </section>
        </el-checkbox-group>
      </section>
    </div>
    <template #footer>
      <div class="scope-drawer-footer">
        <span>{{ scopeApplyHint }}</span>
        <div><el-button @click="scopeVisible = false">稍后调整</el-button><el-button type="primary" class="primary-action" :disabled="scopeApplyDisabled" @click="applyScope">应用调整</el-button></div>
      </div>
    </template>
  </el-drawer>

  <div v-if="draft" class="plan-preview-layout">
    <el-card shadow="never" class="panel-card plan-preview-card">
      <template #header>
        <div class="card-header">
          <div>
            <strong>计划草案</strong>
            <small v-if="draft">可继续调整顺序和时长</small>
          </div>
          <div v-if="original" class="draft-header-actions">
            <el-button text @click="openScope">调整范围</el-button>
            <el-button text :loading="generating" @click="generate">重新生成</el-button>
            <el-button text @click="draft = clone(original)">恢复生成结果</el-button>
          </div>
        </div>
      </template>
      <template v-if="draft">
        <el-alert
          v-if="generationMeta?.fallbackUsed"
          :title="generationMeta.fallbackReason || 'AI 暂时不可用，已使用规则生成可用草案'"
          type="warning"
          :closable="false"
          show-icon
          class="plan-fallback-alert"
        />
        <div class="draft-context-bar">
          <span><small>日期</small>{{ draft.planDate }}</span>
          <span><small>开始</small>{{ draft.startTime.slice(0, 5) }}</span>
          <span><small>范围</small>{{ selectedSubjectLabel }}</span>
        </div>
        <div class="plan-capacity">
          <div><span>可用时长</span><strong>{{ minutesLabel(draft.availableMinutes) }}</strong></div>
          <div><span>已安排</span><strong>{{ minutesLabel(draft.plannedMinutes) }}</strong></div>
          <div><span>剩余</span><strong :class="{ warning: remaining < 0 }">{{ minutesLabel(remaining) }}</strong></div>
        </div>
        <div class="draft-summary"><strong>{{ draft.summary }}</strong></div>
        <el-empty v-if="!draft.items.length" :description="draft.summary" />
        <div v-else class="draft-items plan-draft-timeline">
          <article v-for="(item, index) in draft.items" :key="item.taskId" class="draft-item">
            <div class="draft-sequence">{{ item.sequenceNo }}</div>
            <div class="draft-item-content">
              <span class="draft-subject"><span class="color-dot" :style="{ backgroundColor: item.subjectColor || '#94a3b8' }" />{{ item.subjectName }}</span>
              <strong>{{ item.taskTitle }}</strong>
              <p>{{ item.startAt.slice(11, 16) }}—{{ item.endAt.slice(11, 16) }} · 计划 {{ minutesLabel(item.plannedMinutes) }}</p>
              <small>{{ item.reason }}</small>
            </div>
            <el-input-number
              v-model="item.plannedMinutes"
              :min="15"
              :max="draft.availableMinutes"
              size="small"
              @change="durationChanged(item)"
            />
            <div class="draft-actions">
              <el-button text :disabled="index === 0" @click="move(index, -1)">上移</el-button>
              <el-button text :disabled="index === draft.items.length - 1" @click="move(index, 1)">下移</el-button>
            </div>
          </article>
        </div>
        <el-button
          type="primary"
          class="plan-confirm primary-action"
          :disabled="!draft.items.length || draft.plannedMinutes > draft.availableMinutes"
          :loading="confirming"
          @click="confirm"
        >
          确认并保存计划
        </el-button>
        <p v-if="!draft.items.length || draft.plannedMinutes > draft.availableMinutes" class="action-disabled-hint">
          {{ !draft.items.length ? '草案没有可保存的任务' : '已安排时长超过可用时长，请先调整' }}
        </p>
      </template>
    </el-card>
  </div>
</template>
