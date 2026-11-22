<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessageBox } from 'element-plus'
import { assessmentApi as api, type Attempt } from '../../api/assessment'
import { statusText, gradeText, errorText } from './assessmentLabels'

const props=defineProps<{ attempt: Attempt; requestedOrdinal?: number }>()
const emit=defineEmits<{ update: [value: Attempt] }>()
const index=ref(0)
const answer=ref('')
const busy=ref(false)
const saved=ref('')
const item=computed(()=>props.attempt.items[index.value])
const mark=computed(()=>props.attempt.marks.find(m=>m.ordinal===item.value?.ordinal))
const open=computed(()=>props.attempt.session.status==='IN_PROGRESS')
const dirty=computed(()=>answer.value.trim()!==(item.value?.answer ?? ''))
watch(()=>[props.attempt.session.id,props.requestedOrdinal] as const,()=>{
  const target=props.attempt.items.findIndex(i=>i.ordinal===props.requestedOrdinal)
  index.value=target<0?0:target
},{immediate:true})
watch(item,()=>{answer.value=item.value?.answer ?? ''},{immediate:true})

async function action(run:()=>Promise<void>) {
  if(busy.value) return
  busy.value=true
  try { await run() } catch { /* The HTTP client displays the API error. */ }
  finally { busy.value=false }
}
async function persist() {
  if(!dirty.value || !item.value?.available || !answer.value.trim()) return
  const result=await api.save(props.attempt.session.id,item.value.ordinal,answer.value.trim())
  emit('update', result.data.data)
  saved.value='答案已暂存'
}
function go(next: number) {
  return action(async()=>{
    await persist()
    index.value=next
    saved.value=''
  })
}
function finish(cancel: boolean) {
  return action(async()=>{
    try {
      await ElMessageBox.confirm(cancel?'取消后将无法继续答题。':'提交后将锁定答案，未答题目会标记为未作答。',
        cancel?'取消测评':'确认提交',{type:'warning',confirmButtonText:cancel?'取消测评':'提交答案',cancelButtonText:'继续答题'})
    } catch { return }
    if(!cancel) await persist()
    const response=await (cancel?api.cancel(props.attempt.session.id):api.submit(props.attempt.session.id))
    emit('update',response.data.data)
  })
}
function advisory() {
  return action(async()=>{
    try { await ElMessageBox.confirm('将此简答和公开评分材料发送给当前配置的模型。','获取简答辅助反馈') } catch { return }
    if(item.value) emit('update',(await api.assist(props.attempt.session.id,item.value.ordinal)).data.data)
  })
}
</script>

<template>
  <el-card v-if="item" class="attempt">
    <div class="row">
      <strong>{{ statusText[attempt.session.status] }}</strong>
      <span>已答 {{ attempt.items.filter(row=>row.answer).length }} / {{ attempt.total }}</span>
      <span v-if="open">截至 {{ attempt.session.expiresAt.replace('T',' ') }}</span>
    </div>
    <el-alert v-if="attempt.session.status==='SUBMITTED'" type="info" :closable="false"
      :title="`有效判分 ${attempt.scored}/${attempt.total} · 单选正确 ${attempt.correct}/${attempt.scored || '无样本'} · 待复核 ${attempt.needsReview}`"/>
    <nav class="row" aria-label="题目导航">
      <el-button v-for="(entry,i) in attempt.items" :key="entry.ordinal" :type="i===index?'primary':'default'"
        :disabled="busy" @click="go(i)">{{ entry.ordinal }}<span v-if="entry.answer"> ✓</span></el-button>
    </nav>
    <h2>{{ item.ordinal }}. {{ item.stem || '原题来源已不可用' }}</h2>
    <p>{{ item.kind==='SINGLE_CHOICE'?'单选':'简答' }} · 版本 {{ item.softwareVersion }}</p>
    <el-radio-group v-if="item.kind==='SINGLE_CHOICE'" v-model="answer" class="choices" :disabled="!open || !item.available || busy">
      <el-radio v-for="(option,i) in item.options" :key="i" :value="String(i)">{{ option }}</el-radio>
    </el-radio-group>
    <el-input v-else v-model="answer" type="textarea" :rows="5" maxlength="2000" show-word-limit
      aria-label="简答作答" :disabled="!open || !item.available || busy"/>
    <el-alert v-if="!item.available && open" type="warning" title="来源已变化，这道题不能继续作答。" :closable="false"/>
    <div v-if="open" class="row">
      <el-button :disabled="!dirty || !answer.trim() || !item.available" :loading="busy" @click="action(persist)">暂存答案</el-button>
      <span v-if="saved" role="status">{{ saved }}</span>
      <el-button type="primary" :loading="busy" @click="finish(false)">提交测评</el-button>
      <el-button :disabled="busy" @click="finish(true)">取消测评</el-button>
    </div>
    <section v-if="mark" class="mark">
      <h3>{{ gradeText[mark.grade] || mark.grade }}</h3>
      <p v-if="mark.errorType">{{ errorText[mark.errorType] || mark.errorType }}</p>
      <p v-if="mark.correctAnswer"><strong>参考答案</strong> {{ mark.correctAnswer }}</p>
      <p v-if="mark.explanation"><strong>审核解析</strong> {{ mark.explanation }}</p>
      <ul v-if="mark.rubric?.length"><li v-for="criterion in mark.rubric" :key="criterion.concept">{{ criterion.concept }} · {{ criterion.accepted.join('、') }}</li></ul>
      <details v-if="mark.source">
        <summary>{{ mark.source.title }} · {{ mark.source.softwareVersion }} · 第 {{ mark.source.line }} 行{{ mark.source.current?'':'（历史版本）' }}</summary>
        <blockquote>{{ mark.source.excerpt }}</blockquote>
        <a v-if="mark.source.url" :href="mark.source.url" target="_blank" rel="noopener noreferrer">打开知识来源</a>
      </details>
      <el-button v-if="item.kind==='SHORT_ANSWER' && item.answer && item.available && mark.source?.url && !mark.assistance?.rubricFeedback"
        :loading="busy" @click="advisory">获取模型辅助反馈</el-button>
      <p v-if="mark.assistance?.reason">待复核 · {{ mark.assistance.reason }}</p>
      <ul v-if="mark.assistance?.rubricFeedback" aria-label="模型辅助反馈">
        <li v-for="criterion in mark.assistance.rubricFeedback.criteria" :key="criterion.index">
          {{ mark.rubric?.[criterion.index]?.concept }} · {{ gradeText[criterion.status] }} {{ criterion.quote }}
        </li>
      </ul>
    </section>
  </el-card>
</template>

<style scoped>
.row { display:flex; gap:.6rem; flex-wrap:wrap; align-items:center; margin:.7rem 0; }
.row :deep(.el-button) { margin-left:0; }
.choices { display:grid; gap:.65rem; }
.choices :deep(.el-radio) { min-height:2rem; height:auto; white-space:normal; align-items:start; margin:0; }
.mark { border-top:1px solid var(--el-border-color); margin-top:1rem; padding-top:.6rem; }
blockquote { white-space:pre-wrap; overflow-wrap:anywhere; }
</style>
