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

const remaining = computed(() =>
  draft.value ? draft.value.availableMinutes - draft.value.plannedMinutes : 0,
)
const generateButtonText = computed(() =>
  generating.value || quickCreating.value
    ? '正在为你生成计划...'
    : generationMode.value === 'AI'
      ? 'AI生成学习计划'
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
const emptyDescription = computed(() =>
  generationMode.value === 'AI'
    ? '描述你想怎么学习，确认 AI 的理解后生成计划'
    : '填写学习需求或高级设置后生成规则计划',
)
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
      return ElMessage.warning('请先确认要学习的科目，或选择“全部相关科目”')
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
        <el-button type="primary" size="large" :loading="parsing" :disabled="parsing || generating" @click="parseRequirement">
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
        <div class="understanding-grid">
          <el-form-item label="日期">
            <el-date-picker v-model="form.planDate" type="date" value-format="YYYY-MM-DD" class="full-width" />
          </el-form-item>
          <el-form-item label="开始时间（北京时间）">
            <el-time-picker v-model="form.startTime" value-format="HH:mm:ss" class="full-width" />
          </el-form-item>
          <el-form-item label="学习时长（分钟）">
            <el-input-number v-model="form.availableMinutes" :min="1" :max="720" class="full-width" />
          </el-form-item>
        </div>
        <el-form-item label="重点内容和顺序">
          <el-input v-model="form.requirement" type="textarea" :rows="2" maxlength="1000" />
        </el-form-item>
        <el-form-item label="节奏或偏好">
          <el-select
            v-model="form.preferences"
            multiple
            filterable
            allow-create
            default-first-option
            placeholder="例如：轻松一点、先难后易"
            class="full-width"
          />
        </el-form-item>
      </div>

      <div v-if="parsed" class="subject-confirmation">
        <div class="selection-section-heading">
          <div>
            <span class="selection-step">2</span>
            <div>
              <strong>确认学习科目</strong>
              <p v-if="parsed.ambiguousTopics.length">
                你提到的“{{ parsed.ambiguousTopics.join('、') }}”可能对应多个科目，请确认。
              </p>
              <p v-else>精确或唯一匹配已默认选择，你仍然可以修改。</p>
            </div>
          </div>
          <span>已选 {{ form.selectedSubjectIds.length }} 个</span>
        </div>

        <el-alert
          v-if="parsed.unmatchedKeywords.length"
          :title="`未找到与“${parsed.unmatchedKeywords.join('、')}”相关的科目，不会使用无关任务。`"
          type="warning"
          :closable="false"
          show-icon
        />
        <div v-if="parsed.unmatchedKeywords.length" class="quick-create-entry">
          <div>
            <strong>没有找到匹配的待办任务</strong>
            <span>可按当前主题快速创建真实科目和任务，并立即生成计划。</span>
          </div>
          <el-button
            type="primary"
            :loading="quickCreating || generating"
            @click="quickCreateAndGenerate"
          >
            一键创建并生成计划
          </el-button>
        </div>

        <div v-if="parsed.candidateSubjects.length" class="subject-selection-actions">
          <el-button size="small" @click="selectAllRelatedSubjects">全部相关科目</el-button>
          <el-button size="small" @click="selectRecommendedSubjects">按当前进度推荐</el-button>
        </div>
        <el-checkbox-group v-model="form.selectedSubjectIds" class="subject-choice-list">
          <label
            v-for="subject in parsed.candidateSubjects"
            :key="subject.subjectId"
            class="subject-choice"
            :class="{ disabled: subject.pendingTaskCount === 0 }"
          >
            <el-checkbox :value="subject.subjectId" :disabled="subject.pendingTaskCount === 0" />
            <span class="color-dot" :style="{ backgroundColor: subject.subjectColor || '#94a3b8' }" />
            <span class="subject-choice-main">
              <strong>{{ subject.subjectName }}</strong>
              <small>{{ subject.matchReason }}</small>
            </span>
            <el-tag v-if="subject.recommended" size="small" type="success">推荐</el-tag>
            <span class="subject-task-count">
              {{ subject.pendingTaskCount ? `${subject.pendingTaskCount} 个待办任务` : '没有可用任务' }}
            </span>
          </label>
        </el-checkbox-group>
        <el-empty
          v-if="!parsed.candidateSubjects.length"
          description="没有匹配的科目，可使用上方一键创建，或调整学习主题"
          :image-size="70"
        />
        <el-button
          type="primary"
          plain
          :loading="tasksLoading"
          :disabled="!form.selectedSubjectIds.length"
          @click="loadCandidateTasks"
        >
          确认科目并查看任务
        </el-button>
      </div>

      <div v-if="candidateTasks.length || tasksLoading" v-loading="tasksLoading" class="task-confirmation">
        <div class="selection-section-heading">
          <div>
            <span class="selection-step">3</span>
            <div><strong>选择要安排的任务</strong><p>推荐只是排序依据，最终由你决定哪些任务进入计划。</p></div>
          </div>
          <span>已选 {{ form.selectedTaskIds.length }} / {{ candidateTasks.length }}</span>
        </div>
        <div class="task-selection-actions">
          <el-button size="small" @click="selectAllTasks">全选</el-button>
          <el-button size="small" @click="clearSelectedTasks">清空</el-button>
        </div>
        <el-checkbox-group v-model="form.selectedTaskIds" class="selectable-task-groups">
          <section v-for="group in taskGroups" :key="group.subjectId" class="selectable-task-group">
            <header>
              <span class="color-dot" :style="{ backgroundColor: group.subjectColor || '#94a3b8' }" />
              <strong>{{ group.subjectName }}</strong>
              <small>{{ group.tasks.length }} 个可用任务</small>
            </header>
            <label v-for="task in group.tasks" :key="task.taskId" class="selectable-task">
              <el-checkbox :value="task.taskId" />
              <span class="selectable-task-main">
                <strong>{{ task.taskTitle }}</strong>
                <small>
                  {{ task.goalTitle || '未关联目标' }} · 预计 {{ minutesLabel(task.estimatedMinutes) }}
                  <template v-if="task.deadline"> · 截止 {{ formatDateTime(task.deadline) }}</template>
                </small>
                <em>{{ task.matchReason }}</em>
              </span>
              <el-tag v-if="task.recommended" size="small" type="success">推荐</el-tag>
              <el-tag size="small" :type="priorityMap[task.priority].type">
                {{ priorityMap[task.priority].label }}
              </el-tag>
            </label>
          </section>
        </el-checkbox-group>
      </div>

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
          class="generate-plan-primary"
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

  <div class="plan-preview-layout">
    <el-card shadow="never" class="panel-card plan-preview-card">
      <template #header>
        <div class="card-header">
          <div>
            <strong>计划草案</strong>
            <small v-if="draft">可继续调整顺序和时长</small>
          </div>
          <el-button v-if="original" text @click="draft = clone(original)">恢复生成结果</el-button>
        </div>
      </template>
      <el-empty v-if="!draft" :description="emptyDescription" />
      <template v-else>
        <el-alert
          v-if="generationMeta?.fallbackUsed"
          :title="generationMeta.fallbackReason || 'AI 暂时不可用，已使用规则生成可用草案'"
          type="warning"
          :closable="false"
          show-icon
          class="plan-fallback-alert"
        />
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
              <strong>
                <span class="color-dot" :style="{ backgroundColor: item.subjectColor || '#94a3b8' }" />
                {{ item.taskTitle }}
              </strong>
              <p>{{ item.startAt.slice(11, 16) }}—{{ item.endAt.slice(11, 16) }} · {{ item.subjectName }}</p>
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
          class="plan-confirm"
          :disabled="!draft.items.length || draft.plannedMinutes > draft.availableMinutes"
          :loading="confirming"
          @click="confirm"
        >
          确认并保存计划
        </el-button>
      </template>
    </el-card>
  </div>
</template>
