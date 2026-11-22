<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { assessmentApi as api, type BankEntry, type GenerationForm, type Managed, type State, type Topic } from '../../api/assessment'
import { knowledgeHubApi } from '../../api/knowledgeHub'
import { bankText, difficultyText } from './assessmentLabels'

const props=defineProps<{ topics: Topic[]; activeUnit?: string; activeVersion?: string }>()
const emit=defineEmits<{ published: [] }>()
const form=reactive<GenerationForm>({unitId:props.activeUnit??'',softwareVersion:props.activeVersion??'',
  difficulty:'BASIC',kind:'SINGLE_CHOICE',count:5,publicBank:false})
const reviewer=ref(false)
const busy=ref(false)
const notice=ref('')
const items=ref<BankEntry[]>([])
const current=ref<Managed>()
const reason=ref('')
const transitions: Record<State,State[]>={DRAFT:['PENDING_REVIEW','ARCHIVED'],PENDING_REVIEW:['APPROVED','REJECTED'],
  APPROVED:['PUBLISHED','REJECTED','ARCHIVED'],PUBLISHED:['ARCHIVED'],REJECTED:['ARCHIVED'],ARCHIVED:[]}
const actions=computed(()=>current.value?transitions[current.value.question.state]:[])
const reasons:Record<string,string>={DRAFT:'候选已保存，请逐项检查题干、答案和来源。',AI_DISABLED:'当前模型未配置。',
  NO_EVIDENCE:'没有相符版本的公开证据。请先发布适用的可信资料。',DATA_CONFLICT:'存在重复题目，未保存本次批次。',
  INVALID_CANDIDATES:'候选没有通过结构或答案依据校验。',MODEL_ABSTAINED:'模型未返回足够的有依据候选。',
  KNOWLEDGE_CONFLICT:'证据在生成期间发生变化。请重新检索。'}
async function refresh() { items.value=(await api.manage()).data.data }
onMounted(async()=>{
  try {
    reviewer.value=(await knowledgeHubApi.capabilities()).data.data.reviewer
    await refresh()
  } catch { /* Exposed by the HTTP client. */ }
})
async function perform(fn:()=>Promise<void>) {
  if(busy.value) return
  busy.value=true
  try { await fn() } catch { /* Exposed by the HTTP client. */ }
  finally { busy.value=false }
}
function generate() {
  return perform(async()=>{
    const {data:{data}}=await api.generate(form)
    notice.value=reasons[data.status] ?? data.status
    await refresh()
  })
}
function inspect(row:BankEntry) {
  return perform(async()=>{
    current.value=(await api.inspect(row.versionId)).data.data
    reason.value=''
  })
}
function decide(state:State) {
  const selected=current.value
  if(!selected || !reason.value.trim()) return
  return perform(async()=>{
    current.value=(await api.decide(selected.question.versionId,state,reason.value.trim())).data.data
    reason.value=''
    await refresh()
    emit('published')
  })
}
</script>

<template>
  <section>
    <form class="bank-form" @submit.prevent="generate">
      <label>出题知识点<el-select v-model="form.unitId" filterable aria-label="出题知识点">
        <el-option v-for="topic in topics" :key="topic.id" :label="topic.title" :value="topic.id"/>
      </el-select></label>
      <label>来源软件版本<el-input v-model="form.softwareVersion" maxlength="80" aria-label="出题软件版本"/></label>
      <label>题型<el-select v-model="form.kind"><el-option label="单选" value="SINGLE_CHOICE"/><el-option label="简答" value="SHORT_ANSWER"/></el-select></label>
      <label>难度<el-select v-model="form.difficulty"><el-option v-for="(label,key) in difficultyText" :key="key" :label="label" :value="key"/></el-select></label>
      <label>题数<el-input-number v-model="form.count" :min="3" :max="10"/></label>
      <el-checkbox v-if="reviewer" v-model="form.publicBank">生成公共题库草稿</el-checkbox>
      <el-button native-type="submit" type="primary" :loading="busy" :disabled="!form.unitId || !form.softwareVersion.trim()">生成候选题</el-button>
    </form>
    <el-alert v-if="notice" :title="notice" :closable="false" type="info"/>
    <el-empty v-if="!items.length" description="没有可管理的题目"/>
    <ul v-else class="bank-list">
      <li v-for="row in items" :key="row.versionId">
        <el-button link type="primary" @click="inspect(row)">{{ row.stem }}</el-button>
        <el-tag>{{ bankText[row.state] }} · {{ row.publicBank?'公共':'个人' }} · v{{ row.revision }}</el-tag>
        <el-tag v-if="row.needsSourceReview" type="warning">来源需复核</el-tag>
      </li>
    </ul>
    <el-dialog title="核验题目与来源" :model-value="!!current" @close="current=undefined" width="min(94vw, 740px)">
      <template v-if="current">
        <h3>{{ current.question.stem }}</h3>
        <ul v-if="current.definition.options">
          <li v-for="(option,i) in current.definition.options" :key="i">{{ option.text }} <strong v-if="i===current.definition.correctIndex">正确选项</strong></li>
        </ul>
        <p>参考答案 {{ current.definition.referenceAnswer }}</p>
        <p>解析 {{ current.definition.explanation }}</p>
        <p v-for="criterion in current.definition.rubric" :key="criterion.concept">{{ criterion.concept }} · {{ criterion.accepted.join('、') }}</p>
        <blockquote>{{ current.definition.quote }}</blockquote>
        <p v-if="current.source">
          {{ current.source.title }} · {{ current.source.softwareVersion }} · 第 {{ current.source.line }} 行
          <a v-if="current.source.url" :href="current.source.url" target="_blank" rel="noopener noreferrer">打开来源</a>
        </p>
        <el-input v-if="actions.length" v-model="reason" maxlength="500" placeholder="记录审核理由" aria-label="审核理由"/>
        <div class="bank-form">
          <el-button v-for="state in actions" :key="state" :loading="busy" :disabled="!reason.trim()" @click="decide(state)">{{ bankText[state] }}</el-button>
        </div>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.bank-form { display:flex; gap:.8rem; flex-wrap:wrap; align-items:end; margin:.8rem 0; }
.bank-form label { display:grid; gap:.25rem; width:min(100%,13rem); }
.bank-list { padding-left:1rem; }
.bank-list li { display:flex; gap:.6rem; flex-wrap:wrap; margin:.7rem 0; }
.bank-list :deep(button span) { white-space:normal; overflow-wrap:anywhere; text-align:left; }
blockquote { white-space:pre-wrap; overflow-wrap:anywhere; }
</style>
