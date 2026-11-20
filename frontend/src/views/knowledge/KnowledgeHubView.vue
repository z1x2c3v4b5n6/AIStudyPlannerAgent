<script setup lang="ts">
import RagQuestionPanel from './RagQuestionPanel.vue'
import KnowledgeReviewPanel from './KnowledgeReviewPanel.vue'
import { computed, onMounted, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import {
  knowledgeHubApi as api, type Audit, type Detail, type Difference, type Domain, type Evidence, type Scope,
  type SearchResult, type Signal, type Source, type Version,
} from '../../api/knowledgeHub'

const tab = ref('search')
const reviewer = ref(false)
const canFetch = ref(false)
const busy = ref(false)
const notice = ref('')
const error = ref('')
const query = ref('')
const scope = ref<Scope>('PUBLIC')
const domain = ref<Domain>()
const filterVersion = ref('')
const online = ref(false)
const result = ref<SearchResult>()
const personal = ref<Version[]>([])
const detail = ref<Detail>()
const uploadTitle = ref('')
const uploadText = ref('')
const uploadDomain = ref<Domain>('JAVA_BACKEND')
const feedback = ref<Evidence>()
const category = ref('INCOMPATIBLE')
const queryType = ref('API')
const environment = ref('')
const sources = ref<Source[]>([])
const sourceName = ref('')
const sourceUrl = ref('')
const sourceVersion = ref('')
const sourceDomain = ref<Domain>('JAVA_BACKEND')
const sourceId = ref('')
const source = computed(() => sources.value.find(item => item.id === sourceId.value))
const candidateTitle = ref('')
const candidateText = ref('')
const unitId = ref('')
const candidates = ref<Version[]>([])
const selected = ref<Detail>()
const diff = ref<Difference>()
const history = ref<Audit[]>([])
const signals = ref<Signal[]>([])
const reason = ref('')
const extraction = ref<Awaited<ReturnType<typeof api.extract>>['data']['data']>()
const versionActions: Record<string, [string,string][]> = {
  DRAFT: [['SUBMIT','提交审核']],
  PENDING_REVIEW: [['APPROVE','批准'],['REJECT','拒绝']],
  APPROVED: [['PUBLISH','发布']],
  SUPERSEDED: [['ROLLBACK','回滚到此版本']],
  REJECTED: [['ARCHIVE','归档']],
}
const statuses: Record<string,string> = {
  LOCAL_ONLY:'本次查询采用本地检索', DISABLED:'在线检索未启用', AVAILABLE:'已取得本次查询的官方在线证据',
  NO_SOURCE:'没有匹配的已登记官方入口', RATE_LIMITED:'在线查询频率已达上限',
  NO_RELEVANT_EVIDENCE:'已检查官方页面，未找到相关片段', UNAVAILABLE:'可信来源暂时不可用',
}
const kinds: Record<string,string> = {
  HELPFUL:'有帮助', UNSOLVED:'没有解决问题', INCORRECT:'内容错误',
  STALE:'内容过时', INCOMPATIBLE:'版本不兼容', INCOMPLETE:'内容不完整',
}
async function perform(work: () => Promise<void>) {
  if (busy.value) return
  busy.value=true
  error.value=''
  notice.value=''
  try { await work() }
  catch (cause: unknown) {
    const response=cause as { response?: { data?: { message?: string } } }
    error.value=response.response?.data?.message ?? '请求失败，请稍后重试。'
  } finally { busy.value=false }
}
onMounted(() => perform(async () => {
  const caps=(await api.capabilities()).data.data
  reviewer.value=caps.reviewer
  canFetch.value=caps.onlineEnabled
  personal.value=(await api.personal()).data.data
}))
async function runSearch() {
  if (!query.value.trim()) return
  result.value=undefined
  feedback.value=undefined
  await perform(async () => {
    result.value=(await api.search({query: query.value.trim(),scope:scope.value,domain:domain.value,
      softwareVersion:filterVersion.value.trim()||undefined,online:online.value})).data.data
  })
}
async function readFile(event: Event) {
  const input=event.target as HTMLInputElement
  const file=input.files?.[0]
  if (!file) return
  if (!/\.(md|txt)$/i.test(file.name) || file.size>300000) {
    error.value='请选择 300 KB 以内的 Markdown 或 TXT 文件。'
    input.value=''
    return
  }
  try {
    uploadText.value=new TextDecoder('utf-8',{fatal:true}).decode(await file.arrayBuffer())
    uploadTitle.value=file.name.replace(/\.[^.]+$/,'').slice(0,200)
  } catch { error.value='文件必须为 UTF-8 文本。' }
  input.value=''
}
function importPersonal() {
  return perform(async () => {
    await api.import({title:uploadTitle.value,text:uploadText.value,domain:uploadDomain.value})
    personal.value=(await api.personal()).data.data
    uploadTitle.value=''
    uploadText.value=''
    notice.value='个人资料已导入。'
  })
}
async function remove(item: Version) {
  try { await ElMessageBox.confirm('删除后将移除个人正文和对应检索片段。','删除资料',{type:'warning'}) }
  catch { return }
  await perform(async () => {
    await api.remove(item.documentId)
    personal.value=(await api.personal()).data.data
    if(detail.value?.version.id===item.id) detail.value=undefined
  })
}
function sendFeedback() {
  const target=feedback.value
  if(!target?.versionId) return
  return perform(async () => {
    await api.feedback(target.versionId!,{category:category.value,queryType:queryType.value,
      ordinal:target.ordinal,environment:environment.value.trim()||undefined})
    feedback.value=undefined
    notice.value='已记录反馈。'
  })
}
async function refreshManage() {
  const [sourceResponse,versionResponse,signalResponse]=await Promise.all([api.sources(),api.candidates(),api.signals()])
  sources.value=sourceResponse.data.data
  candidates.value=versionResponse.data.data
  signals.value=signalResponse.data.data
}
function register() {
  return perform(async () => {
    const created=(await api.source({name:sourceName.value,url:sourceUrl.value,domain:sourceDomain.value,
      softwareVersion:sourceVersion.value||undefined})).data.data
    await refreshManage()
    sourceId.value=created.id
    notice.value='已登记官方入口。'
  })
}
function importCandidate(fetchPage: boolean) {
  const entry=source.value
  if(!entry) return
  return perform(async () => {
    const link=unitId.value.trim()||undefined
    const response=fetchPage?await api.refresh(entry.id,link):await api.stage(entry.id,
      {title:candidateTitle.value,text:candidateText.value,domain:entry.domain,softwareVersion:entry.softwareVersion,unitId:link})
    await refreshManage()
    await inspect(response.data.data)
    notice.value='候选版本已读取。'
  })
}
async function inspect(version: Version) {
  const [item,delta,trail]=await Promise.all([api.inspect(version.id),api.diff(version.id),api.history(version.documentId)])
  selected.value=item.data.data
  diff.value=delta.data.data
  history.value=trail.data.data
  extraction.value=undefined
  reason.value=''
}
function decide(action: string) {
  const item=selected.value
  if(!item || !reason.value.trim()) return
  return perform(async () => {
    const version=(await api.decision(item.version.id,action,reason.value)).data.data
    await refreshManage()
    await inspect(version)
    notice.value='审核操作已记录。'
  })
}
async function inspectCase(id: string) {
  tab.value='manage'
  await perform(async () => {
    await refreshManage()
    const row=candidates.value.find(item=>item.id===id)
    await inspect(row || (await api.inspect(id)).data.data.version)
  })
}
</script>

<template>
  <section class="hub">
    <h1>知识中心</h1>
    <el-alert v-if="error" type="error" :title="error" :closable="false" />
    <el-alert v-if="notice" type="success" :title="notice" :closable="false" />
    <el-tabs v-model="tab" @tab-change="(name: string | number) => { if (name==='manage') perform(refreshManage) }">
      <el-tab-pane name="search" label="检索">
        <form class="controls" @submit.prevent="runSearch">
          <el-input v-model="query" maxlength="300" aria-label="知识查询" placeholder="输入技术主题，例如 Spring Boot 自动配置" class="query" />
          <el-select v-model="scope" aria-label="检索范围" class="select">
            <el-option label="公共知识" value="PUBLIC"/><el-option label="个人资料" value="PRIVATE"/><el-option label="综合检索" value="BOTH"/>
          </el-select>
          <el-select v-model="domain" aria-label="技术领域" class="select" clearable placeholder="全部领域">
            <el-option label="Java 后端" value="JAVA_BACKEND"/><el-option label="AI 应用" value="AI_APPLICATION"/>
          </el-select>
          <el-input v-model="filterVersion" maxlength="80" aria-label="软件版本" class="select" placeholder="软件版本（选填）" />
          <el-checkbox v-model="online" :disabled="!canFetch || scope==='PRIVATE'">需要时检查官方入口</el-checkbox>
          <el-button native-type="submit" type="primary" :loading="busy">检索知识</el-button>
        </form>
        <template v-if="result">
          <p class="muted">{{ statuses[result.onlineStatus] || result.onlineStatus }}</p>
          <el-empty v-if="!result.published.length && !result.online.length && !result.structured.length" description="没有匹配到证据" />
          <article v-for="item in [...result.published,...result.online]" :key="(item.versionId || item.url) + ':' + item.ordinal" class="entry">
            <h2>{{ item.title }}</h2>
            <p class="muted">{{ item.scope==='PRIVATE'?'个人资料':item.scope==='ONLINE'?'临时在线证据':'已发布证据' }} · {{ item.heading }} · 第 {{ item.line }} 行
              <template v-if="item.softwareVersion"> · {{ item.softwareVersion }}</template></p>
            <pre>{{ item.excerpt }}</pre>
            <a v-if="item.url" :href="item.url" target="_blank" rel="noopener noreferrer">{{ item.source || '官方来源' }}</a>
            <p class="muted">{{ item.ingestKind==='MANUAL_REVIEW'?'人工登记':'采集或导入' }}于 {{ item.collectedAt }} · 匹配 {{ item.matchedTerms.join('、') }}</p>
            <el-button v-if="item.scope==='PUBLIC'" @click="feedback=item">反馈这条证据</el-button>
          </article>
          <article v-for="row in result.structured" :key="row.unit.id" class="entry">
            <h2>{{ row.unit.topic }}</h2>
            <small>内置知识 · {{ row.unit.id }}</small>
            <ul><li v-for="point in row.unit.keyPoints" :key="point">{{ point }}</li></ul>
          </article>
        </template>
        <el-dialog title="证据反馈" :model-value="!!feedback" @close="feedback=undefined">
          <div class="controls">
            <el-select v-model="category" aria-label="反馈类型" class="select">
              <el-option v-for="(label,value) in kinds" :key="value" :label="label" :value="value"/>
            </el-select>
            <el-select v-model="queryType" aria-label="问题类型" class="select">
              <el-option label="API" value="API"/><el-option label="配置" value="CONFIGURATION"/><el-option label="概念" value="CONCEPT"/>
              <el-option label="排障" value="TROUBLESHOOTING"/><el-option label="其他" value="OTHER"/>
            </el-select>
            <el-input v-model="environment" maxlength="80" placeholder="技术版本，例如 Spring Boot 4.0（选填）" />
          </div>
          <template #footer><el-button type="primary" :loading="busy" @click="sendFeedback">提交反馈</el-button></template>
        </el-dialog>
      </el-tab-pane>

      <el-tab-pane name="rag" label="技术问答" lazy><RagQuestionPanel :can-fetch="canFetch"/></el-tab-pane>
      <el-tab-pane v-if="reviewer" name="review-cases" label="知识复核" lazy><KnowledgeReviewPanel @inspect="inspectCase"/></el-tab-pane>

      <el-tab-pane name="personal" label="个人资料">
        <div class="controls">
          <el-input v-model="uploadTitle" maxlength="200" aria-label="资料标题" placeholder="资料标题" class="query"/>
          <el-select v-model="uploadDomain" class="select" aria-label="资料领域">
            <el-option label="Java 后端" value="JAVA_BACKEND"/><el-option label="AI 应用" value="AI_APPLICATION"/>
          </el-select>
          <label>选择 UTF-8 文本 <input type="file" accept=".txt,.md" @change="readFile"></label>
        </div>
        <el-input v-model="uploadText" type="textarea" maxlength="100000" :rows="6" aria-label="个人资料正文" placeholder="也可粘贴 Markdown 或纯文本" />
        <el-button type="primary" :loading="busy" :disabled="!uploadTitle.trim()||!uploadText.trim()" @click="importPersonal">导入个人资料</el-button>
        <el-empty v-if="!personal.length" description="暂无个人资料" />
        <article v-for="item in personal" :key="item.id" class="entry controls">
          <strong>{{ item.title }}</strong>
          <el-button @click="perform(async () => { detail=(await api.view(item.id)).data.data })">查看原文</el-button>
          <el-button type="danger" plain @click="remove(item)">删除</el-button>
        </article>
        <el-dialog title="个人资料原文" :model-value="!!detail" @close="detail=undefined"><pre>{{ detail?.text }}</pre></el-dialog>
      </el-tab-pane>

      <el-tab-pane v-if="reviewer" name="manage" label="来源与审核">
        <h2>登记官方入口</h2>
        <div class="controls">
          <el-input v-model="sourceName" maxlength="120" aria-label="来源名称" placeholder="来源名称" class="select"/>
          <el-input v-model="sourceUrl" maxlength="500" aria-label="官方文档链接" placeholder="官方文档链接" class="query"/>
          <el-select v-model="sourceDomain" class="select" aria-label="来源领域">
            <el-option label="Java 后端" value="JAVA_BACKEND"/><el-option label="AI 应用" value="AI_APPLICATION"/>
          </el-select>
          <el-input v-model="sourceVersion" maxlength="80" placeholder="软件版本" class="select"/>
          <el-button :loading="busy" :disabled="!sourceName.trim()||!sourceUrl.trim()" @click="register">登记来源</el-button>
        </div>
        <div class="controls">
          <el-select v-model="sourceId" placeholder="选择已登记来源" class="query" aria-label="登记来源选择">
            <el-option v-for="entry in sources" :key="entry.id" :label="entry.name" :value="entry.id"/>
          </el-select>
          <el-input v-model="unitId" maxlength="100" placeholder="内置知识 ID（选填）" class="select"/>
          <el-button :disabled="!source||!canFetch" :loading="busy" @click="importCandidate(true)">检查官方页面</el-button>
        </div>
        <p v-if="source?.lastError" class="muted">{{ source.lastError }} · {{ source.checkedAt }}</p>
        <el-input v-model="candidateTitle" maxlength="200" aria-label="候选标题" placeholder="候选标题"/>
        <el-input v-model="candidateText" type="textarea" maxlength="100000" :rows="5" aria-label="候选正文" placeholder="粘贴已核对并获准使用的文档片段"/>
        <el-button :disabled="!source||!candidateTitle.trim()||!candidateText.trim()" :loading="busy" @click="importCandidate(false)">提交待审核版本</el-button>
        <h2>复核信号</h2>
        <p v-if="!signals.length" class="muted">暂无达到阈值的反馈组。</p>
        <ul><li v-for="signal in signals" :key="signal.versionId + signal.environment + signal.queryType">
          {{ signal.environment }} · {{ signal.queryType }} · {{ signal.users }} 个账户 · {{ signal.versionId }}
        </li></ul>
        <h2>版本记录</h2>
        <div class="controls">
          <el-button v-for="item in candidates" :key="item.id" @click="perform(() => inspect(item))">{{ item.title }} · {{ item.status }}</el-button>
        </div>
        <article v-if="selected" class="entry">
          <h2>{{ selected.version.title }} · {{ selected.version.status }}</h2>
          <details><summary>原始正文</summary><pre>{{ selected.text }}</pre></details>
          <details v-if="diff"><summary>片段差异（新增 {{ diff.addedCount }}，移除 {{ diff.removedCount }}）</summary>
            <h3>新增</h3><pre v-for="chunk in diff.added" :key="chunk.ordinal">{{ chunk.excerpt }}</pre>
            <h3>移除</h3><pre v-for="chunk in diff.removed" :key="chunk.ordinal">{{ chunk.excerpt }}</pre>
          </details>
          <details><summary>审核及发布历史</summary><ul><li v-for="(row,index) in history" :key="index">{{ row.time }} · {{ row.operation }} · {{ row.reason }}</li></ul></details>
          <el-button :loading="busy" @click="perform(async () => { extraction=(await api.extract(selected!.version.id)).data.data })">生成 AI 提取预览</el-button>
          <p v-if="extraction?.status==='DISABLED'">AI 提取未启用。</p>
          <ul v-else-if="extraction"><li v-for="(quote,index) in extraction.suggestions" :key="index">{{ quote.kind }} · 片段 {{ quote.ordinal }} · {{ quote.excerpt }}</li></ul>
          <div v-if="versionActions[selected.version.status]?.length" class="controls">
            <el-input v-model="reason" maxlength="500" aria-label="审核理由" placeholder="填写审核理由" class="query"/>
            <el-button v-for="[action,label] in versionActions[selected.version.status]" :key="action" :disabled="!reason.trim()" :loading="busy" @click="decide(action)">{{ label }}</el-button>
          </div>
        </article>
      </el-tab-pane>
    </el-tabs>
  </section>
</template>

<style scoped>
.hub { display:grid; gap:12px; min-width:0; }
.controls { display:flex; flex-wrap:wrap; align-items:center; gap:10px; margin:12px 0; }
.query { width:min(100%,25rem); }
.select { width:min(100%,11rem); }
.entry { border:1px solid var(--el-border-color); border-radius:8px; padding:14px; margin-top:12px; }
.entry h2 { margin:0 0 6px; font-size:1.1rem; }
.muted { color:var(--el-text-color-secondary); font-size:.9rem; }
pre { white-space:pre-wrap; overflow-wrap:anywhere; background:var(--el-fill-color-light); padding:12px; border-radius:6px; }
details { margin:10px 0; }
@media (max-width:580px) { .controls { align-items:stretch; } .select,.query { width:100%; } }
</style>
