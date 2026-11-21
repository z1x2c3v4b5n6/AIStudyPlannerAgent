<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { isAxiosError } from 'axios'
import { agentApi, type AgentAction, type AgentSession, type AgentSummary } from '../../api/agent'
import AgentData from './AgentData.vue'
import AgentWorkflowPanel from './AgentWorkflowPanel.vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../../stores/auth'
import { passAgentPlanDraft } from '../../utils/agentPlanDraft'
import type { PlanDraft } from '../../types/plan'

const router = useRouter()
const auth = useAuthStore()

const sessions = ref<AgentSummary[]>([])
const active = ref<AgentSession>()
const input = ref('')
const busy = ref('')
const mode = ref<'chat' | 'workflow'>('chat')
const sessionPage = ref(1)
const error = ref('')
const bottom = ref<HTMLElement>()
const now = ref(Date.now())
const tools: Record<string, string> = {
  list_subjects: '学习科目', list_goals: '学习目标', today_tasks: '今日任务',
  open_tasks: '未完成任务', study_records: '学习记录', study_statistics: '学习统计',
  learning_paths: '学习路径', search_knowledge: '知识库', unknown: '未知工具',
  get_assessment_history:'测评历史', get_knowledge_performance:'知识表现', list_weak_topics:'薄弱知识点',
  get_wrong_answers:'错题', get_assessment_summary:'测评概览', list_assessment_topics:'测评知识点',
  propose_assessment_practice:'开始新测评', propose_assessment_review:'安排薄弱点复习',
  propose_task_create: '新建任务', propose_task_update: '修改任务', propose_study_plan: '计划草案',
}
const examplePrompts = ['帮我看看今天还有什么任务没有完成。', '明天下午帮我安排两个小时学习 RAG。', '把我的 Spring Boot 任务调整到后天。']
const statusLabels = { PENDING: '待确认', EXECUTING: '执行中', EXECUTED: '已执行', CANCELLED: '已取消', FAILED: '执行失败', EXPIRED: '已过期' }
const pending = computed(() => active.value?.actions.some(action => status(action) === 'PENDING'))
const actionMap = computed(() => new Map(active.value?.actions.map(action => [action.id, action])))
const timer = window.setInterval(() => { now.value = Date.now() }, 1000)
onUnmounted(() => window.clearInterval(timer))

function status(action: AgentAction) {
  return action.status === 'PENDING' && Date.parse(action.expiresAt) <= now.value ? 'EXPIRED' : action.status
}
async function refreshList() {
  sessions.value = (await agentApi.list(sessionPage.value)).data.data
}
async function run(label: string, work: () => Promise<void>) {
  if (busy.value) return
  busy.value = label
  error.value = ''
  try {
    await work()
    await nextTick()
    bottom.value?.scrollIntoView({ block: 'nearest' })
  } catch (cause) {
    error.value = isAxiosError(cause) ? cause.response?.data?.message || '请求未完成，请重试或刷新当前对话。' : '请求未完成，请重试。'
  } finally {
    busy.value = ''
  }
}
onMounted(() => run('正在载入对话', async () => {
  await refreshList()
  const session = sessions.value[0]
  if (session) active.value = (await agentApi.get(session.id)).data.data
}))
function open(id: string) {
  return run('正在载入对话', async () => {
    active.value = (await agentApi.get(id)).data.data
    input.value = ''
  })
}
function create() {
  return run('正在创建对话', async () => {
    active.value = (await agentApi.create()).data.data
    input.value = ''
    sessionPage.value = 1
    await refreshList()
  })
}
function removeSession() {
  if (!active.value || !window.confirm('删除当前对话及其工作流？')) return
  return run('正在删除对话', async () => {
    if (!active.value) return
    await agentApi.remove(active.value.id)
    active.value = undefined
    await refreshList()
  })
}
function changePage(step: number) {
  return run('正在载入历史', async () => {
    const next = sessionPage.value + step
    sessions.value = (await agentApi.list(next)).data.data
    sessionPage.value = next
  })
}
function send() {
  const text = input.value.trim()
  if (!text || pending.value) return
  return run('助手正在查询或调用工具', async () => {
    active.value ??= (await agentApi.create()).data.data
    active.value = (await agentApi.send(active.value.id, text)).data.data
    input.value = ''
    await refreshList()
  })
}
function practiceSession(action: AgentAction) {
  const result=action.result
  return result && typeof result==='object' && !Array.isArray(result) && typeof result.id==='string'?result.id:undefined
}
function reviewPlan(action: AgentAction) {
  if (!auth.user || action.status !== 'EXECUTED' || !action.result) return
  passAgentPlanDraft(auth.user.id, action.result as unknown as PlanDraft)
  void router.push('/plans')
}
function decide(action: AgentAction, confirm: boolean) {
  const current = active.value
  if (!current || status(action) !== 'PENDING') return
  return run(confirm ? '正在执行已确认操作' : '正在取消操作', async () => {
    active.value = (await agentApi.decide(current.id, action, confirm)).data.data
    await refreshList()
  })
}
</script>

<template>
  <section class="assistant-layout">
    <aside class="session-nav" aria-label="对话历史">
      <el-button type="primary" :disabled="!!busy" @click="create">新对话</el-button>
      <button v-for="session in sessions" :key="session.id" class="session-link" type="button"
              :aria-current="active?.id === session.id ? 'true' : undefined" :disabled="!!busy" @click="open(session.id)">
        {{ session.title }}
      </button>
      <el-button v-if="sessionPage > 1" :disabled="!!busy" @click="changePage(-1)">上一页</el-button>
      <el-button v-if="sessions.length === 10" :disabled="!!busy" @click="changePage(1)">下一页</el-button>
    </aside>
    <main class="assistant-main">
      <header>
        <h1>{{ active?.title || 'AI 学习助手' }}</h1>
        <div>
          <el-button v-if="active" size="small" :disabled="!!busy" @click="open(active.id)">刷新对话</el-button>
          <el-button v-if="active" size="small" :disabled="!!busy" @click="removeSession">删除对话</el-button>
        </div>
      </header>
      <el-radio-group v-model="mode" aria-label="助手模式">
        <el-radio-button value="chat">聊天</el-radio-button>
        <el-radio-button value="workflow">工作流</el-radio-button>
      </el-radio-group>
      <AgentWorkflowPanel v-if="mode === 'workflow' && active" :session-id="active.id" />
      <el-button v-else-if="mode === 'workflow'" :disabled="!!busy" @click="create">新建对话以运行工作流</el-button>
      <template v-if="mode === 'chat'">
      <div v-if="!active?.messages.length" class="examples">
        <el-button v-for="prompt in examplePrompts" :key="prompt" :disabled="!!busy" @click="input = prompt">{{ prompt }}</el-button>
      </div>
      <article v-for="(message, index) in active?.messages" :key="index" class="message" :class="message.role">
        <strong>{{ message.role === 'user' ? '你' : '学习助手' }}</strong>
        <p>{{ message.text }}</p>
        <details v-for="(tool, toolIndex) in message.tools" :key="toolIndex" class="tool-result">
          <summary>{{ tools[tool.name] || tool.name }} · {{ tool.success ? '工具结果' : '调用失败' }}</summary>
          <AgentData :value="tool.data" />
        </details>
        <template v-if="message.actionId">
          <section v-for="action in [actionMap.get(message.actionId)]" :key="message.actionId" class="action">
            <template v-if="action">
              <div class="action-title"><strong>{{ tools[action.type] }} · {{ action.target }}</strong><el-tag>{{ statusLabels[status(action)] }}</el-tag></div>
              <router-link v-if="action.type==='propose_assessment_practice' && practiceSession(action)"
                :to="{path:'/assessments',query:{session:practiceSession(action)}}">打开测评</router-link>
              <AgentData :value="action.preview" />
              <div v-if="status(action) === 'PENDING'" class="action-controls">
                <el-button type="primary" :disabled="!!busy" @click="decide(action, true)">确认执行</el-button>
                <el-button :disabled="!!busy" @click="decide(action, false)">取消</el-button>
              </div>
              <el-alert v-if="action.errorMessage" :title="action.errorMessage" type="error" :closable="false" />
              <details v-if="action.result" open class="tool-result"><summary>执行结果</summary><AgentData :value="action.result" /></details>
              <el-button v-if="action.type === 'propose_study_plan' && action.result" type="primary" link @click="reviewPlan(action)">在每日计划中编辑草案</el-button>
              <el-button v-else-if="action.result" type="primary" link @click="$router.push('/tasks')">查看任务列表</el-button>
            </template>
          </section>
        </template>
      </article>
      <p v-if="busy" role="status" class="progress">{{ busy }}…</p>
      <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
      <div ref="bottom"></div>
      <form class="composer" @submit.prevent="send">
        <el-input v-model="input" type="textarea" aria-label="学习需求" placeholder="输入学习需求"
                  :rows="3" :maxlength="2000" show-word-limit :disabled="!!busy" />
        <el-button type="primary" native-type="submit" :disabled="!input.trim() || !!busy || pending">发送</el-button>
      </form>
      <p v-if="pending" class="progress">确认或取消待处理操作后可继续对话。</p>
      </template>
    </main>
  </section>
</template>

<style scoped>
.assistant-layout { display: grid; grid-template-columns: minmax(10rem, 15rem) minmax(0, 1fr); gap: 1.2rem; }
.session-nav, .assistant-main { background: var(--el-bg-color); border: 1px solid var(--el-border-color); border-radius: 12px; padding: 1.1rem; }
.session-nav { display: flex; flex-direction: column; gap: 0.65rem; align-self: start; max-height: 65vh; overflow: auto; }
.session-link { text-align: left; color: var(--el-text-color-primary); border: 0; border-radius: 6px; background: var(--el-fill-color-light); padding: 0.65rem; cursor: pointer; overflow-wrap: anywhere; }
.session-link[aria-current] { color: var(--el-color-primary); outline: 1px solid var(--el-color-primary); }
.assistant-main { display: flex; flex-direction: column; gap: 0.9rem; min-height: 55vh; }
.assistant-main header, .action-title { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: center; gap: 0.5rem; }
h1 { margin: 0; font-size: 1.35rem; }
.examples { display: flex; flex-wrap: wrap; gap: 0.5rem; }
.examples .el-button { margin: 0; max-width: 100%; white-space: normal; height: auto; }
.message { padding: 0.85rem; border-radius: 8px; background: var(--el-fill-color-light); width: fit-content; max-width: 100%; overflow-wrap: anywhere; }
.message.user { align-self: end; background: var(--el-color-primary-light-9); }
.message p { white-space: pre-wrap; margin: 0.4rem 0 0; }
.tool-result { margin-top: 0.6rem; padding: 0.45rem; border: 1px solid var(--el-border-color); border-radius: 6px; }
.tool-result summary { cursor: pointer; margin-bottom: 0.3rem; }
.action { display: grid; gap: 0.6rem; margin-top: 0.7rem; padding: 0.8rem; border: 1px solid var(--el-color-primary); border-radius: 7px; }
.composer { display: flex; align-items: end; gap: 0.65rem; margin-top: auto; }
.progress { color: var(--el-text-color-secondary); margin: 0; }
@media (max-width: 720px) { .assistant-layout { grid-template-columns: 1fr; } .session-nav { max-height: 13rem; } }
</style>
