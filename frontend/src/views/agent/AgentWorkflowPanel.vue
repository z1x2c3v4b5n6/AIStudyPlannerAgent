<script setup lang="ts">
import { computed, onUnmounted, ref, toRaw, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../../stores/auth'
import { workflowApi, type JsonValue, type WorkflowRun } from '../../api/agent'
import type { PlanDraft } from '../../types/plan'
import type { LearningPathDraft } from '../../types/learningPath'
import { passAgentPlanDraft } from '../../utils/agentPlanDraft'
import { passAgentPathDraft } from '../../utils/agentPathDraft'
import AgentData from './AgentData.vue'
import { subjectApi } from '../../api/subject'

const props = defineProps<{ sessionId: string }>()
const router = useRouter()
const auth = useAuthStore()
const input = ref('')
const run = ref<WorkflowRun>()
const runs = ref<WorkflowRun[]>([])
const page = ref(1)
const busy = ref(false)
const changing = ref(false)
const error = ref('')
const budget = ref(60)
const preferred = ref<number | null>(null)
const choices = ref<{id:number;name:string}[]>([])
const strategy = ref('MINIMAL')
const strategies = {MINIMAL:'最小变更',PRIORITY:'优先级优先',BALANCED:'均衡安排'}
let sequence = 0
const examples = [
  '帮我检查这周有哪些任务还没有完成，按紧急程度整理一下。',
  '我明天只有两个小时，把重要任务重新安排一下。',
  '我最近学习进度怎么样，哪些内容落后了？',
  '我想用一周复习 Spring Boot，你帮我分析已有任务，然后安排学习路线。',
  '分析我最近两周的学习情况，告诉我有哪些问题。',
  '我下周每天只有一个小时，把我的任务重新安排一下。',
  '帮我找一下最近需要复习的内容。',
  '我最近 Spring Boot 任务落后了，帮我分析问题，然后安排下周计划，再看看 RAG 有没有需要复习的知识。',
]
const statuses: Record<string, string> = {
  READY: '可继续', RUNNING: '运行中', PAUSED: '已暂停', WAITING_INPUT: '待补充信息', WAITING_APPROVAL: '待确认',
  SUCCEEDED: '已完成', FAILED: '失败', CANCELLED: '已取消', TIMED_OUT: '已超时',
  WAITING: '待确认', DONE: '已完成', SKIPPED: '已跳过',
}
const labels: Record<string, string> = {
  start: '开始', plan: '确定执行计划', tasks: '查询任务', calendar: '检查时间冲突',
  adjustment: '生成调整预览', condition: '检查安排条件', approval: '用户确认',
  apply: '执行任务变更', planDraft: '生成每日计划草案', pathDraft: '生成学习路径草案',
  records: '学习记录', statistics: '学习统计', paths: '已有学习路径', subjects: '科目',
  knowledge: '查询本地知识库', result: '汇总结果', end: '结束',
  clarify:'补充预算', learningProfile:'个人学习画像', diagnostics:'诊断依据', reviewCandidates:'复习候选',
  adaptivePreview:'比较多日规划', adaptiveCondition:'检查可用方案', adaptiveApproval:'选择并批准方案',
  adaptiveApply:'保存每日计划', adaptiveResult:'汇总学习建议',
}
const approval = computed(() => run.value?.nodes.find(node => node.status === 'WAITING' && node.kind === 'APPROVAL'))
const adaptiveApproval = computed(() => approval.value?.id === 'adaptiveApproval')
const selectedScheme = computed(() => {
  const preview = object(approval.value?.result).preview
  return Array.isArray(preview) ? preview.find(item => object(item).strategy === strategy.value) : null
})
const canApprove = computed(() => {
  const scheduled=object(selectedScheme.value ?? null).scheduled
  return !adaptiveApproval.value || (Array.isArray(scheduled) && scheduled.length>0)
})
const active = computed(() => !!run.value && ['READY','RUNNING','PAUSED','WAITING_INPUT','WAITING_APPROVAL'].includes(run.value.status))
const committed = computed(() => run.value?.nodes.some(node => (node.id === 'apply' || node.id === 'adaptiveApply') && node.status === 'DONE'))
const planDraft = computed(() => run.value?.nodes.find(node => node.id === 'planDraft')?.result)
const pathDraft = computed(() => run.value?.status === 'SUCCEEDED'
  ? run.value.nodes.find(node => node.id === 'pathDraft')?.result : null)

function object(value: JsonValue | undefined) {
  return value && typeof value === 'object' && !Array.isArray(value) ? value : {}
}
async function refreshList() {
  const session = props.sessionId
  const selectedPage = page.value
  const rows = (await workflowApi.list(session, selectedPage)).data.data
  if (session === props.sessionId && selectedPage === page.value) runs.value = rows
}
watch(() => props.sessionId, async () => {
  sequence++
  busy.value = false
  run.value = undefined
  page.value = 1
  await refreshList().catch(() => { error.value = '无法读取工作流历史。' })
}, { immediate: true })
onUnmounted(() => { sequence++ })

async function drive(id: string) {
  const current = ++sequence
  busy.value = true
  error.value = ''
  try {
    for (let i = 0; i < 16 && current === sequence; i++) {
      const next = (await workflowApi.advance(id)).data.data
      if (current !== sequence) return
      run.value = next
      if (next.status === 'WAITING_INPUT') {
        choices.value = (await subjectApi.list()).data.data
      }
      if (next.status !== 'READY') break
    }
    if (current === sequence) await refreshList()
  } catch {
    if (current === sequence) error.value = '执行连接中断。刷新后可查看已保存的进度。'
  } finally {
    if (current === sequence) busy.value = false
  }
}
async function start() {
  if (!input.value.trim() || busy.value || changing.value) return
  changing.value = true
  error.value = ''
  try {
    run.value = (await workflowApi.start(props.sessionId, input.value.trim())).data.data
    input.value = ''
    void drive(run.value.id)
  } catch { error.value = '无法创建工作流，请核对需求类型、日期和可用时间。' }
  finally { changing.value = false }
}
async function select(id: string) {
  if (busy.value || changing.value) return
  changing.value = true
  error.value = ''
  try {
    run.value = (await workflowApi.get(id)).data.data
    if (run.value.status==='WAITING_INPUT') choices.value = (await subjectApi.list()).data.data
  }
  catch { error.value = '无法读取工作流。' }
  finally { changing.value = false }
}
async function decide(confirm: boolean) {
  if (!run.value || !approval.value || changing.value) return
  const digest = object(approval.value.result).digest
  if (typeof digest !== 'string') return
  changing.value = true
  error.value = ''
  try {
    run.value = (await workflowApi.decide(run.value.id, digest, confirm, adaptiveApproval.value ? strategy.value : undefined)).data.data
    if (confirm && run.value.status === 'READY') void drive(run.value.id)
    else await refreshList()
  } catch { error.value = '提交决定失败，请刷新状态。' }
  finally { changing.value = false }
}
async function clarify() {
  if (!run.value || changing.value) return
  changing.value = true
  error.value = ''
  try {
    run.value = (await workflowApi.clarify(run.value.id,budget.value,preferred.value)).data.data
    if(run.value.status==='READY') void drive(run.value.id)
  } catch { error.value = '无法提交预算或科目，请刷新后重试。' }
  finally { changing.value = false }
}
async function control(command: 'PAUSE' | 'RESUME' | 'CANCEL') {
  if (!run.value || changing.value) return
  const id = run.value.id
  sequence++
  busy.value = false
  changing.value = true
  error.value = ''
  try {
    run.value = (await workflowApi.control(id, command)).data.data
    if (command === 'RESUME' && run.value.status === 'READY') void drive(id)
    else await refreshList()
  } catch { error.value = '更新状态失败，请刷新后重试。' }
  finally { changing.value = false }
}
function openDraft(kind: 'daily' | 'path') {
  const owner = auth.user?.id
  if (!owner) return
  if (kind === 'daily' && planDraft.value) {
    passAgentPlanDraft(owner, structuredClone(toRaw(planDraft.value)) as unknown as PlanDraft)
    void router.push('/plans')
  } else if (kind === 'path' && pathDraft.value) {
    passAgentPathDraft(owner, structuredClone(toRaw(pathDraft.value)) as unknown as LearningPathDraft)
    void router.push('/learning-paths')
  }
}
async function turnPage(step: number) {
  page.value += step
  try { await refreshList() }
  catch { page.value -= step; error.value = '无法读取工作流历史。' }
}
</script>

<template>
  <section class="workflow-panel">
    <form class="compose" @submit.prevent="start">
      <el-input v-model="input" type="textarea" :rows="2" maxlength="2000" aria-label="工作流需求" placeholder="描述要完成的学习任务" />
      <el-button type="primary" native-type="submit" :disabled="!input.trim() || busy || changing">运行工作流</el-button>
    </form>
    <div class="actions">
      <el-button v-for="(example, index) in examples" :key="example" size="small" @click="input = example">
        {{ ['任务整理', '重新安排', '进度分析', '长期路线', '两周诊断', '下周规划', '复习建议', '综合调整'][index] }}
      </el-button>
    </div>
    <div v-if="runs.length || page > 1" class="actions" aria-label="历史工作流">
      <el-select data-testid="workflow-history" aria-label="历史工作流" :model-value="run?.id" placeholder="历史工作流" :disabled="busy || changing" style="width: min(28rem, 100%)" @change="select">
        <el-option v-for="row in runs" :key="row.id" :value="row.id" :label="row.input" />
      </el-select>
      <el-button :disabled="page === 1 || busy" @click="turnPage(-1)">上一页</el-button>
      <el-button :disabled="runs.length < 10 || busy" @click="turnPage(1)">下一页</el-button>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false"/>
    <p v-if="busy" role="status">正在执行当前步骤…</p>
    <section v-if="run">
      <header class="actions">
        <strong>{{ run.input }}</strong><el-tag>{{ statuses[run.status] }}</el-tag>
      </header>
      <div class="actions">
        <el-button :disabled="busy || changing" @click="select(run.id)">刷新状态</el-button>
        <el-button v-if="run.status === 'READY'" :disabled="busy || changing" @click="drive(run.id)">继续执行</el-button>
        <el-button v-if="run.status === 'PAUSED'" :disabled="changing" @click="control('RESUME')">恢复或安全重试</el-button>
        <el-button v-if="run.status === 'READY'" :disabled="changing" @click="control('PAUSE')">暂停</el-button>
        <el-button v-if="active" type="danger" plain :disabled="changing" @click="control('CANCEL')">取消工作流</el-button>
      </div>
      <el-alert v-if="committed" title="任务变更已提交。后续失败或取消时，已提交的变更仍有效。" type="success" :closable="false" />
      <ol class="nodes">
        <li v-for="node in run.nodes" :key="node.id">
          <strong>{{ labels[node.operation] || node.operation }}</strong> · {{ statuses[node.status] }}
          <span v-if="node.attempts"> · 尝试 {{ node.attempts }} 次</span>
          <p v-if="node.error" class="failure">{{ node.error }}</p>
          <details v-if="node.result && node.kind !== 'APPROVAL'"><summary>查看结果</summary><AgentData :value="node.result" /></details>
        </li>
      </ol>
      <section v-if="run.status==='WAITING_INPUT'" class="approval">
        <h3>补充每天可学习时间</h3>
        <div class="actions">
          <el-input-number v-model="budget" :min="15" :max="720" aria-label="每日可用分钟"/>
          <el-select v-model="preferred" clearable placeholder="可选优先科目" style="width:12rem">
            <el-option v-for="item in choices" :key="item.id" :label="item.name" :value="item.id"/>
          </el-select>
          <el-button type="primary" :disabled="changing" @click="clarify">继续规划</el-button>
        </div>
      </section>
      <section v-if="approval" class="approval">
        <h3>核对授权范围</h3>
        <template v-if="adaptiveApproval">
          <el-radio-group v-model="strategy">
            <el-radio-button v-for="(label,value) in strategies" :key="value" :value="value">{{ label }}</el-radio-button>
          </el-radio-group>
          <AgentData :value="selectedScheme ?? null"/>
        </template>
        <AgentData v-else :value="object(approval.result).preview ?? null" />
        <div class="actions">
          <el-button type="primary" :disabled="changing || !canApprove" @click="decide(true)">批准此工作流操作</el-button>
          <el-button :disabled="changing" @click="decide(false)">拒绝</el-button>
        </div>
      </section>
      <div class="actions">
        <el-button v-if="planDraft" type="primary" @click="openDraft('daily')">编辑每日计划草案</el-button>
        <el-button v-if="pathDraft" type="primary" @click="openDraft('path')">编辑学习路径草案</el-button>
        <el-button v-if="committed" @click="router.push(run?.scenario==='ADAPTIVE'?'/plans':'/tasks')">查看已保存结果</el-button>
        <router-link v-if="run.scenario==='ADAPTIVE'" to="/adaptive">打开学习洞察与复习</router-link>
      </div>
    </section>
  </section>
</template>

<style scoped>
.workflow-panel { display: grid; gap: .8rem; }
.compose { display: grid; grid-template-columns: minmax(0,1fr) auto; gap: .6rem; align-items: end; }
.actions { display: flex; flex-wrap: wrap; align-items: center; gap: .55rem; margin: .6rem 0; }
.actions .el-button { margin-left: 0; }
.nodes { padding-left: 1.4rem; }
.nodes li { padding: .5rem 0; border-bottom: 1px solid var(--el-border-color); overflow-wrap: anywhere; }
.nodes details { margin-top: .4rem; }
.approval { padding: 1rem; border: 1px solid var(--el-color-primary); border-radius: 8px; }
.failure { color: var(--el-color-danger); }
@media (max-width: 600px) { .compose { grid-template-columns: minmax(0,1fr); } }
</style>
