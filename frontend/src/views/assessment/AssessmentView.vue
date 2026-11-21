<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { init, type ECharts } from 'echarts'
import { assessmentApi as api, type Attempt, type BankEntry, type Difficulty, type Mode, type Session, type Topic } from '../../api/assessment'
import { subjectApi } from '../../api/subject'
import { learningPathApi } from '../../api/learningPath'
import { adaptiveApi, type ReviewCandidate } from '../../api/adaptive'
import AssessmentAttempt from './AssessmentAttempt.vue'
import AssessmentBank from './AssessmentBank.vue'
import { difficultyText, errorText, levelText, modeText, statusText } from './assessmentLabels'

const route=useRoute()
const router=useRouter()
const topics=ref<Topic[]>([])
const subjects=ref<{id:number; name:string}[]>([])
const paths=ref<{id:number; title:string}[]>([])
const bank=ref<BankEntry[]>([])
const attempt=ref<Attempt>()
const history=ref<Session[]>([])
const wrong=ref<Awaited<ReturnType<typeof api.wrong>>['data']['data']>([])
const performance=ref<Awaited<ReturnType<typeof api.performance>>['data']['data']>()
const progress=ref<Awaited<ReturnType<typeof api.reviewProgress>>['data']['data']>()
const reviews=ref<ReviewCandidate[]>([])
const tab=ref(route.query.tab==='bank'?'bank':route.query.tab==='wrong'?'wrong':'practice')
const mode=ref<Mode>(route.query.tab==='wrong'?'WRONG_ANSWERS':'TOPIC')
const unit=ref(typeof route.query.unit==='string'?route.query.unit:undefined)
const version=ref(typeof route.query.version==='string'?route.query.version:'')
const unitScope=ref<string[]>([])
const subject=ref<number>()
const path=ref<number>()
const difficulty=ref<Difficulty>()
const count=ref(5)
const page=ref(1)
const wrongPage=ref(1)
const days=ref(30)
const busy=ref(false)
const error=ref('')
const chartTarget=ref<HTMLDivElement>()
let chart: ECharts|undefined
const selectedOrdinal=computed(()=>Number(route.query.ordinal) || undefined)
const availableVersions=computed(()=>[...new Set(bank.value.filter(q=>!unit.value || q.unitId===unit.value).map(q=>q.softwareVersion))])
const canStart=computed(()=>mode.value==='LEARNING_PATH'?!!path.value:mode.value==='TOPIC'?!!unit.value:true)

async function perform(fn:()=>Promise<void>) {
  if(busy.value) return
  busy.value=true
  error.value=''
  try { await fn() } catch { error.value='操作未完成。请检查当前权限、题库版本或测评状态。' }
  finally { busy.value=false }
}
async function refresh() {
  const [h,w,p,r,re]=await Promise.all([api.history(page.value),api.wrong({page:wrongPage.value,subjectId:subject.value,unitId:unit.value}),
    api.performance({days:days.value,subjectId:subject.value,unitId:unit.value}),api.reviewProgress(),adaptiveApi.reviews()])
  history.value=h.data.data
  wrong.value=w.data.data
  performance.value=p.data.data
  progress.value=r.data.data
  reviews.value=re.data.data.filter(item=>item.reason.startsWith('近期 ') && item.reviewTaskId==null)
}
async function refreshBank() { bank.value=(await api.bank()).data.data }
async function loadSession() {
  const id=route.query.session
  attempt.value=undefined
  if(typeof id!=='string') return
  try { attempt.value=(await api.get(id)).data.data; tab.value='practice' }
  catch { error.value='测评会话不存在或当前账号不可访问。' }
}
onMounted(async()=>{
  await loadSession()
  await perform(async()=>{
    const [t,s,p]=await Promise.all([api.topics(),subjectApi.list(),learningPathApi.list(1,50),refreshBank(),refresh()])
    topics.value=t.data.data
    subjects.value=s.data.data
    paths.value=p.data.data.list
  })
})
watch(()=>route.query.session,loadSession)
watch([days,subject,unit],()=>{
  wrongPage.value=1
  void perform(refresh)
})
watch([chartTarget,performance,tab],()=>{
  if(!chartTarget.value || tab.value!=='performance') return
  if(chart && chart.getDom()!==chartTarget.value) { chart.dispose();chart=undefined }
  chart ??= init(chartTarget.value)
  const values=performance.value?.topics.slice(0,10)??[]
  chart.setOption({grid:{left:'6%',right:'4%',bottom:'30%',containLabel:true},tooltip:{trigger:'axis'},
    legend:{data:['首次答对','首次答错']},xAxis:{type:'category',axisLabel:{interval:0,rotate:28},
      data:values.map(t=>`${t.topic} ${t.softwareVersion} (n=${t.newQuestions.total})`)},
    yAxis:{type:'value',minInterval:1},
    series:[{name:'首次答对',type:'bar',stack:'answers',color:'#147d72',data:values.map(t=>t.newQuestions.correct)},
      {name:'首次答错',type:'bar',stack:'answers',color:'#bf7243',data:values.map(t=>t.newQuestions.total-t.newQuestions.correct)}]})
  chart.resize()
},{flush:'post'})
const resize=()=>chart?.resize()
onMounted(()=>window.addEventListener('resize',resize))
onBeforeUnmount(()=>{window.removeEventListener('resize',resize);chart?.dispose()})
function view(id: string, ordinal?: number) {
  tab.value='practice'
  void router.replace({query:{session:id,ordinal}})
}
function update(next: Attempt) {
  const changed=attempt.value?.session.status!==next.session.status
  attempt.value=next
  if(changed) void perform(refresh)
}
function start() {
  return perform(async()=>{
    const {data:{data}}=await api.start({mode:mode.value,subjectId:subject.value,
      pathId:mode.value==='LEARNING_PATH'?path.value:undefined,
      unitId:mode.value==='TOPIC'||mode.value==='WRONG_ANSWERS'?unit.value:undefined,
      scope:mode.value==='COMPREHENSIVE'?unitScope.value:undefined,
      softwareVersion:version.value.trim()||undefined,difficulty:difficulty.value,count:count.value})
    view(data.id)
    await refresh()
  })
}
function followWrong(unitId: string,softwareVersion: string) {
  unit.value=unitId
  version.value=softwareVersion
  mode.value='WRONG_ANSWERS'
  attempt.value=undefined
  tab.value='practice'
  void router.replace({query:{unit:unitId,version:softwareVersion}})
}
</script>

<template>
  <section class="assessment">
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false"/>
    <el-tabs v-model="tab">
      <el-tab-pane label="练习与历史" name="practice">
        <AssessmentAttempt v-if="attempt" :attempt="attempt" :requested-ordinal="selectedOrdinal" @update="update"/>
        <el-button v-if="attempt" class="new" @click="router.replace({query:{}})">返回测评首页</el-button>
        <template v-else>
          <el-card>
            <form class="filters" @submit.prevent="start">
              <label>测评模式<el-select v-model="mode" aria-label="测评模式">
                <el-option v-for="(label,key) in modeText" :key="key" :label="label" :value="key"/>
              </el-select></label>
              <label>学习科目<el-select v-model="subject" clearable aria-label="测评科目">
                <el-option v-for="row in subjects" :key="row.id" :label="row.name" :value="row.id"/>
              </el-select></label>
              <label v-if="mode==='LEARNING_PATH'">学习路径<el-select v-model="path" aria-label="测评学习路径">
                <el-option v-for="row in paths" :key="row.id" :label="row.title" :value="row.id"/>
              </el-select></label>
              <label v-else-if="mode==='COMPREHENSIVE'">知识范围<el-select v-model="unitScope" multiple filterable :multiple-limit="12">
                <el-option v-for="row in topics" :key="row.id" :label="row.title" :value="row.id"/>
              </el-select></label>
              <label v-else>知识点<el-select v-model="unit" filterable clearable aria-label="测评知识点">
                <el-option v-for="row in topics" :key="row.id" :label="row.title" :value="row.id"/>
              </el-select></label>
              <label>软件版本<el-input v-model="version" maxlength="80" aria-label="测评软件版本" :placeholder="availableVersions.join('、') || '按题库适用版本'"/></label>
              <label>难度<el-select v-model="difficulty" clearable aria-label="测评难度">
                <el-option v-for="(label,key) in difficultyText" :key="key" :label="label" :value="key"/>
              </el-select></label>
              <label>题数<el-input-number v-model="count" :min="1" :max="20" aria-label="测评题数"/></label>
              <el-button type="primary" native-type="submit" :disabled="!canStart" :loading="busy">开始测评</el-button>
            </form>
            <p v-if="!bank.length">当前没有可练习的题目。可在题库中基于已发布知识生成草稿。</p>
          </el-card>
          <el-card>
            <h2>最近测评</h2>
            <p v-if="performance">最近 {{ days }} 天错题 {{ performance.incorrect }} 道，待复核 {{ performance.needsReview }} 道。</p>
            <el-empty v-if="!history.length" description="暂无测评记录"/>
            <ul v-else class="rows">
              <li v-for="row in history" :key="row.id">
                <span>{{ modeText[row.mode] }} · {{ row.startedAt.replace('T',' ') }} · {{ statusText[row.status] }}</span>
                <el-button link type="primary" @click="view(row.id)">{{ row.status==='IN_PROGRESS'?'继续答题':'查看测评' }}</el-button>
              </li>
            </ul>
            <div class="filters"><el-button :disabled="page===1||busy" @click="page--;perform(refresh)">上一页</el-button>
              <el-button :disabled="history.length<20||busy" @click="page++;perform(refresh)">下一页</el-button></div>
          </el-card>
        </template>
      </el-tab-pane>
      <el-tab-pane label="错题本" name="wrong">
        <div class="filters">
          <label>按科目筛选<el-select v-model="subject" clearable><el-option v-for="row in subjects" :key="row.id" :label="row.name" :value="row.id"/></el-select></label>
          <label>按知识点筛选<el-select v-model="unit" filterable clearable><el-option v-for="row in topics" :key="row.id" :label="row.title" :value="row.id"/></el-select></label>
        </div>
        <el-empty v-if="!wrong.length" description="这个范围内暂无确定判错的题目"/>
        <ul v-else class="rows">
          <li v-for="row in wrong" :key="`${row.sessionId}/${row.ordinal}`">
            <span>{{ row.topic }} · {{ row.softwareVersion }} · {{ errorText[row.errorType || ''] || '未标注错因' }} · {{ row.submittedAt.slice(0,10) }}</span>
            <el-button link type="primary" @click="view(row.sessionId,row.ordinal)">查看错题解析</el-button>
            <el-button link type="primary" @click="followWrong(row.unitId,row.softwareVersion)">练习同类题</el-button>
          </li>
        </ul>
        <div class="filters"><el-button :disabled="wrongPage===1||busy" @click="wrongPage--;perform(refresh)">上一页</el-button>
          <el-button :disabled="wrong.length<20||busy" @click="wrongPage++;perform(refresh)">下一页</el-button></div>
      </el-tab-pane>
      <el-tab-pane label="知识表现" name="performance">
        <div class="filters"><label>统计范围<el-select v-model="days" aria-label="表现统计范围">
          <el-option v-for="d in [7,14,30,90]" :key="d" :label="`最近 ${d} 天`" :value="d"/>
        </el-select></label></div>
        <el-empty v-if="!performance?.topics.length" description="暂无可用于知识表现的公共题库判分记录"/>
        <template v-else>
          <div v-if="tab==='performance'" ref="chartTarget" class="chart" aria-label="首次作答按知识点分布"/>
          <p v-if="performance.truncated">数据量达到展示上限，此处展示最近记录。</p>
          <ul class="rows">
            <li v-for="row in performance.topics" :key="`${row.unitId}/${row.softwareVersion}`">
              <strong>{{ row.topic }} · {{ row.softwareVersion }} · {{ levelText[row.level] }}</strong>
              <span>首次 {{ row.newQuestions.correct }}/{{ row.newQuestions.total }} · 复练 {{ row.repeats.correct }}/{{ row.repeats.total }}
                · 连续新题答错 {{ row.consecutiveErrors }} 道</span>
              <span v-for="(score,degree) in row.byDifficulty" :key="degree">{{ difficultyText[degree] }} {{ score.correct }}/{{ score.total }}</span>
            </li>
          </ul>
        </template>
        <h3>测评驱动的复习</h3>
        <p v-if="progress">最近 {{ progress.accepted }} 个已确认复习任务，{{ progress.completed }} 个已完成。</p>
        <ul v-if="reviews.length" class="rows">
          <li v-for="row in reviews" :key="row.itemId"><strong>{{ row.topic }}</strong><span>{{ row.reason }}</span></li>
        </ul>
        <router-link to="/adaptive">打开学习洞察，预览和确认复习任务</router-link>
        <h3>复习前后新题表现</h3>
        <p v-if="!progress?.effects.length">暂无有实际复习记录的可比较样本。</p>
        <ul v-else class="rows">
          <li v-for="row in progress.effects" :key="`${row.taskId}/${row.version}/${row.difficulty}`">
            {{ topics.find(topic=>topic.id===row.unitId)?.title || row.unitId }} · {{ row.version }} · {{ difficultyText[row.difficulty] }}
            · 复习前 {{ row.before.correct }}/{{ row.before.total }} · 复习后 {{ row.after.correct }}/{{ row.after.total }}
            <el-tag>{{ row.sufficient?'可观察的变化':'样本不足' }}</el-tag>
          </li>
        </ul>
      </el-tab-pane>
      <el-tab-pane label="题库与出题" name="bank" lazy>
        <AssessmentBank :topics="topics" :active-unit="unit" :active-version="version" @published="perform(refreshBank)"/>
      </el-tab-pane>
    </el-tabs>
  </section>
</template>

<style scoped>
.assessment :deep(.el-card) { margin:0 0 .85rem; }
.filters { display:flex; flex-wrap:wrap; align-items:end; gap:.75rem; margin:.7rem 0; }
.filters label { display:grid; gap:.3rem; width:min(100%,12rem); }
.filters :deep(.el-button) { margin-left:0; }
.rows { padding-left:0; list-style:none; }
.rows li { display:flex; flex-wrap:wrap; gap:.6rem; align-items:center; padding:.6rem 0; border-bottom:1px solid var(--el-border-color-light); }
.chart { height:360px; width:100%; }
.new { margin-bottom:.8rem; }
</style>
