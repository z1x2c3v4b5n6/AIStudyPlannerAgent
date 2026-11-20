<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { evidenceRag as api, type RagAnswer, type RagTurn } from '../../api/evidenceRag'
import { knowledgeHubApi, type Detail, type Scope } from '../../api/knowledgeHub'

defineProps<{ canFetch: boolean }>()
const question=ref('')
const version=ref('')
const scope=ref<Scope>('PUBLIC')
const allowOnline=ref(false)
const privateModel=ref(false)
const follow=ref<string>()
const result=ref<RagAnswer>()
const history=ref<RagTurn[]>([])
const busy=ref(false)
const detail=ref<Detail>()
const selectedOrdinal=ref<number>()
const feedback=ref<RagAnswer['citations'][number]>()
const category=ref('UNSOLVED')
const environment=ref('')
const correction=ref(false)
const notice=ref('')
const error=ref('')
const reasons: Record<string,string> = {
  NO_EVIDENCE:'没有足够的相关证据。', AMBIGUOUS_VERSION:'找到了不同版本的资料，请补充准确的软件版本。',
  VERSION_UNKNOWN:'资料适用版本不明。', POSSIBLE_CONFLICT:'资料中存在相反的配置表述，需要人工核对。',
  LATEST_UNVERIFIED:'本次未能核实最新版本。', EVIDENCE_CHANGED:'回答期间知识版本发生变化，请重新提问。',
  AI_DISABLED:'模型未启用。可查看检索到的证据。', AI_BUSY:'模型繁忙。可查看检索到的证据。',
  INVALID_CITATION:'回答未通过引用验证。', MODEL_ABSTAINED:'现有证据不足以支持回答。',
}
async function loadHistory() { history.value=(await api.history()).data.data }
onMounted(() => loadHistory().catch(() => undefined))
async function ask() {
  if(!question.value.trim() || busy.value) return
  busy.value=true
  error.value=''
  result.value=undefined
  try {
    result.value=(await api.answer({query:question.value.trim(),softwareVersion:version.value.trim()||undefined,
      scope:scope.value,allowOnline:allowOnline.value&&scope.value!=='PRIVATE',
      allowPrivateModel:privateModel.value&&scope.value!=='PUBLIC',conversationId:follow.value})).data.data
    follow.value=result.value.conversationId
    await loadHistory()
  } catch { error.value='问答请求失败。' }
  finally { busy.value=false }
}
function followUp(turn: RagTurn) {
  follow.value=turn.id
  version.value=turn.version||''
  question.value=''
}
async function view(id: string,line?: number) {
  try {
    detail.value=(await knowledgeHubApi.view(id)).data.data
    selectedOrdinal.value=detail.value.chunks.find(chunk=>chunk.line===line)?.ordinal
  } catch { error.value='当前引用已不可见，请重新检索。' }
}
async function sendFeedback() {
  const block=feedback.value?.evidence
  if(!block?.versionId || !block.chunkId) return
  const ordinal=Number(block.chunkId.split(':').at(-1))
  try {
    await api.feedback(block.versionId,correction.value,{category:category.value,queryType:'OTHER',
      ordinal,environment:environment.value})
    feedback.value=undefined
    notice.value='反馈已保存。'
  } catch { error.value='反馈提交失败。已有记录时可选择更正。' }
}
</script>

<template>
  <section>
    <form @submit.prevent="ask">
      <el-input v-model="question" maxlength="300" aria-label="技术问题" placeholder="例如，Spring Boot 3.5 的事务为什么没有生效？"/>
      <div class="options">
        <el-input v-model="version" maxlength="80" aria-label="问答软件版本" placeholder="软件版本（例如 3.5）"/>
        <el-select v-model="scope" aria-label="问答知识范围">
          <el-option label="公共知识" value="PUBLIC"/><el-option label="个人资料" value="PRIVATE"/>
          <el-option label="综合范围" value="BOTH"/>
        </el-select>
        <el-checkbox v-if="canFetch" v-model="allowOnline" :disabled="scope==='PRIVATE'">允许在线补充</el-checkbox>
        <el-checkbox v-if="scope!=='PUBLIC'" v-model="privateModel">允许将检索到的个人片段发送给模型</el-checkbox>
        <el-button native-type="submit" type="primary" :loading="busy">提问</el-button>
        <el-button v-if="follow" @click="follow=undefined">开始新问题</el-button>
      </div>
    </form>
    <el-alert v-if="error" :title="error" type="error" :closable="false"/>
    <el-alert v-if="notice" :title="notice" type="success" :closable="false"/>
    <template v-if="result">
      <el-alert v-if="result.fallbackReason" :title="reasons[result.fallbackReason]||'本次无法完成生成，可查看已取得的证据。'"
                type="warning" :closable="false"/>
      <pre v-if="result.answer" class="answer">{{ result.answer }}</pre>
      <p v-if="result.usedVersions.length">适用版本 {{ result.usedVersions.join('、') }}</p>
      <article v-for="(citation,index) in result.citations" :key="citation.evidence.id+index" class="citation">
        <strong>[{{ citation.evidence.id }}] {{ citation.evidence.title }}</strong>
        <p>{{ citation.evidence.scope }} · {{ citation.evidence.softwareVersion || '版本未登记' }}
          <template v-if="citation.evidence.line"> · 第 {{ citation.evidence.line }} 行</template></p>
        <blockquote>{{ citation.quote }}</blockquote>
        <a v-if="citation.evidence.url" :href="citation.evidence.url" target="_blank" rel="noopener noreferrer">原始来源</a>
        <el-button v-if="citation.evidence.versionId" text @click="view(citation.evidence.versionId,citation.evidence.line)">查看引用片段</el-button>
        <el-button v-if="citation.evidence.scope==='PUBLIC'" text @click="feedback=citation">反馈引用</el-button>
      </article>
    </template>
    <details v-if="history.length">
      <summary>问答历史</summary>
      <ul><li v-for="turn in history" :key="turn.id">
        {{ turn.question }} · {{ turn.version || '未指定版本' }} <el-button text @click="followUp(turn)">追问</el-button>
      </li></ul>
    </details>
    <el-dialog title="引用片段" :model-value="!!detail" @close="detail=undefined">
      <pre class="answer">{{ detail?.chunks.find(chunk=>chunk.ordinal===selectedOrdinal)?.excerpt || detail?.text }}</pre>
    </el-dialog>
    <el-dialog title="反馈引用" :model-value="!!feedback" @close="feedback=undefined">
      <div class="options">
        <el-select v-model="category" aria-label="引用反馈类型">
          <el-option label="有帮助" value="HELPFUL"/><el-option label="未解决" value="UNSOLVED"/>
          <el-option label="版本不兼容" value="INCOMPATIBLE"/><el-option label="内容过时" value="STALE"/>
          <el-option label="内容不完整" value="INCOMPLETE"/><el-option label="内容错误" value="INCORRECT"/>
        </el-select>
        <el-input v-model="environment" maxlength="80" placeholder="使用的软件与版本（选填）"/>
        <el-checkbox v-model="correction">更正已有反馈</el-checkbox>
        <el-button @click="sendFeedback">保存反馈</el-button>
      </div>
    </el-dialog>
  </section>
</template>

<style scoped>
.options { display:flex; flex-wrap:wrap; gap:10px; margin:12px 0; align-items:center; }
.options :deep(.el-input), .options :deep(.el-select) { width:min(100%,14rem); }
.answer, blockquote { white-space:pre-wrap; overflow-wrap:anywhere; }
.citation { border:1px solid var(--el-border-color); border-radius:8px; padding:12px; margin:10px 0; }
</style>
