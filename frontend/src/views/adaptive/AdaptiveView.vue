<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import { adaptiveApi, type AdaptiveProfile, type Diagnostic, type ReviewCandidate } from '../../api/adaptive'
import { shanghaiDate } from '../../utils/businessTime'
import AgentData from '../agent/AgentData.vue'

const tab = ref('profile')
const profile = ref<AdaptiveProfile>()
const diagnostics = ref<Diagnostic[]>([])
const reviews = ref<ReviewCandidate[]>([])
const days = ref(14)
const from = ref('')
const to = ref('')
const start = ref(shanghaiDate())
const horizon = ref(7)
const dailyMinutes = ref(60)
const subject = ref<number | null>(null)
const lockStart = ref('')
const lockEnd = ref('')
const locks = ref<{ start: string; end: string }[]>([])
const busy = ref(false)
const error = ref('')
const notice = ref('')
const chartEl = ref<HTMLElement>()
let chart: echarts.ECharts | undefined
const plan = ref<Awaited<ReturnType<typeof adaptiveApi.plan>>['data']['data']>()
const reviewProposal = ref<Awaited<ReturnType<typeof adaptiveApi.reviewPreview>>['data']['data']>()
const selected = ref<number[]>([])
const reviewDate = ref(shanghaiDate())
const reviewMinutes = ref(30)
const strategy = ref<'MINIMAL' | 'PRIORITY' | 'BALANCED'>('MINIMAL')
const strategies = { MINIMAL:'最小变更', PRIORITY:'优先级优先', BALANCED:'均衡安排' }
const ratings = { UNFAMILIAR:'不熟悉', FAIR:'一般', FAMILIAR:'比较熟悉', FLUENT:'熟练' }
const severity: Record<string,string> = { HIGH:'高', ATTENTION:'需关注', INFO:'信息', INSUFFICIENT_SAMPLE:'样本不足' }
const activeScheme = computed(() => plan.value?.data.schemes.find(s => s.strategy === strategy.value))
const completed = computed(() => profile.value?.cohortCompletionRate == null ? '样本不足' :
  (100 * profile.value.cohortCompletionRate).toFixed(0) + '%')

async function action(work: () => Promise<void>) {
  if(busy.value) return
  busy.value = true
  error.value = ''
  notice.value = ''
  try { await work() }
  catch (cause: unknown) {
    const known = cause as { response?: { data?: { message?: string } } }
    error.value = known.response?.data?.message ?? '请求失败，请刷新后重试。'
  } finally { busy.value = false }
}
function reload() {
  return action(async () => {
    const range = { days:days.value, from:from.value || undefined, to:to.value || undefined }
    const [p,d,r] = await Promise.all([adaptiveApi.profile(range),
      adaptiveApi.diagnostics(days.value,dailyMinutes.value,range.from,range.to),adaptiveApi.reviews()])
    profile.value=p.data.data
    diagnostics.value=d.data.data
    reviews.value=r.data.data
  })
}
onMounted(reload)
watch([chartEl,profile], ([element,current]) => {
  if (chart && (!element || chart.getDom()!==element)) { chart.dispose(); chart=undefined }
  if (!element || !current) return
  chart ??= echarts.init(element)
  chart.setOption({tooltip:{trigger:'axis'},grid:{top:16,left:42,right:16,bottom:32},
    xAxis:{type:'category',data:current.trend.map(point=>point.date.slice(5))},yAxis:{type:'value',name:'分钟'},
    series:[{type:'line',data:current.trend.map(point=>point.totalMinutes),areaStyle:{opacity:.12}}]})
}, { flush:'post' })
function resize() { chart?.resize() }
onMounted(() => window.addEventListener('resize',resize))
onUnmounted(() => { chart?.dispose(); window.removeEventListener('resize',resize) })

function previewPlan() {
  return action(async () => {
    plan.value=(await adaptiveApi.plan({ start:start.value,days:horizon.value,dailyMinutes:dailyMinutes.value,
      preferredSubjectId:subject.value,locked:locks.value })).data.data
  })
}
function decidePlan(confirm: boolean) {
  const current=plan.value
  if(!current) return
  return action(async () => {
    const result=(await adaptiveApi.decidePlan(current,confirm,strategy.value)).data.data
    notice.value=confirm?`已保存 ${result.planIds?.length ?? 0} 个每日计划。`:'已取消规划提案。'
    plan.value=undefined
  })
}
function lock() {
  if (!lockStart.value || !lockEnd.value || lockEnd.value<=lockStart.value) {
    error.value='请选择有效的保留时段。'
    return
  }
  locks.value.push({start:lockStart.value,end:lockEnd.value})
  lockStart.value=''
  lockEnd.value=''
}
function reviewPreview() {
  return action(async () => {
    reviewProposal.value=(await adaptiveApi.reviewPreview(selected.value.map(itemId => ({
      itemId,date:reviewDate.value,minutes:reviewMinutes.value,
    })))).data.data
  })
}
function decideReview(confirm: boolean) {
  const current=reviewProposal.value
  if(!current) return
  return action(async () => {
    const result=(await adaptiveApi.decideReview(current,confirm)).data.data
    reviews.value=(await adaptiveApi.reviews()).data.data
    selected.value=[]
    reviewProposal.value=undefined
    notice.value=confirm?`已创建 ${result.taskIds?.length ?? 0} 个复习任务。`:'已取消复习提案。'
  })
}
function rate(item: ReviewCandidate,rating: string|null,defer=false,dismiss=false){
  return action(async () => {
    const next=new Date(Date.now()+86400000)
    const date=defer?new Intl.DateTimeFormat('sv-SE',{timeZone:'Asia/Shanghai'}).format(next):null
    await adaptiveApi.feedback(item.itemId,rating,date,dismiss)
    selected.value=selected.value.filter(id=>id!==item.itemId)
    reviews.value=(await adaptiveApi.reviews()).data.data
  })
}
</script>

<template>
  <section class="adaptive-page">
    <h1>学习洞察</h1>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-alert v-if="notice" :title="notice" type="success" :closable="false" />
    <el-tabs v-model="tab">
      <el-tab-pane label="学习画像" name="profile">
        <div class="controls">
          <el-select v-model="days" aria-label="统计天数" style="width:8rem">
            <el-option v-for="day in [7,14,30]" :key="day" :value="day" :label="`最近 ${day} 天`"/>
          </el-select>
          <label>开始日期 <input v-model="from" type="date"></label>
          <label>结束日期 <input v-model="to" type="date"></label>
          <el-button :loading="busy" @click="reload">更新统计</el-button>
        </div>
        <el-empty v-if="!profile && !busy" description="暂无画像数据" />
        <template v-if="profile">
          <p>{{ profile.window.from }} 至 {{ profile.window.to }}</p>
          <div class="numbers">
            <div><small>实际学习</small><strong>{{ profile.activity.totalMinutes }} 分钟</strong></div>
            <div><small>活跃天数</small><strong>{{ profile.activity.activeDays }} 天</strong></div>
            <div><small>窗口内完成</small><strong>{{ profile.tasks.completedInWindow }} 项</strong></div>
            <div><small>截止任务完成率</small><strong>{{ completed }}</strong></div>
          </div>
          <p v-if="profile.sampleStatus==='INSUFFICIENT_SAMPLE'">记录不足以推断学习趋势。</p>
          <h2>最近学习时长</h2>
          <div v-if="tab==='profile'" ref="chartEl" class="chart" aria-label="每日实际学习分钟趋势"></div>
          <h2>各科目学习时间</h2>
          <ul><li v-for="row in profile.subjectTime" :key="row.subjectId">{{ row.subjectName }} · {{ row.totalMinutes }} 分钟</li></ul>
          <h2>计划与实际</h2>
          <p v-if="profile.plans.pairedItems">{{ profile.plans.pairedItems }} 项可配对。计划 {{ profile.plans.pairedPlannedMinutes }} 分钟，实际 {{ profile.plans.pairedActualMinutes }} 分钟。</p>
          <p v-else>当前窗口缺少带实际记录的计划项。</p>
          <h2>学习路径</h2>
          <ul><li v-for="path in profile.paths.list" :key="path.id">{{ path.title }} · {{ path.completedItems }}/{{ path.totalItems }} 节点</li></ul>
        </template>
      </el-tab-pane>
      <el-tab-pane label="诊断" name="diagnostics">
        <div class="controls"><label>每日可用分钟 <el-input-number v-model="dailyMinutes" :min="15" :max="720" /></label>
        <el-button :loading="busy" @click="reload">重算诊断</el-button></div>
        <article v-for="item in diagnostics" :key="item.diagnosticId" class="card">
          <h2>{{ item.title }} <el-tag>{{ severity[item.severity] || item.severity }}</el-tag></h2>
          <p>{{ item.description }}。{{ item.recommendation }}</p>
          <details><summary>查看数据依据</summary><AgentData :value="item.evidence" /><p>{{ item.sourceReferences.join('、') }} · {{ item.ruleVersion }}</p></details>
        </article>
      </el-tab-pane>
      <el-tab-pane label="动态规划" name="plan">
        <div class="controls">
          <label>开始日期 <input v-model="start" type="date"></label>
          <label>安排天数 <el-input-number v-model="horizon" :min="1" :max="7" /></label>
          <label>每日分钟 <el-input-number v-model="dailyMinutes" :min="15" :max="720" /></label>
          <el-select v-model="subject" clearable placeholder="优先科目" style="width:10rem">
            <el-option v-for="item in profile?.subjects" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </div>
        <div class="controls">
          <label>保留开始 <input v-model="lockStart" type="datetime-local"></label>
          <label>保留结束 <input v-model="lockEnd" type="datetime-local"></label>
          <el-button @click="lock">保留时段</el-button>
        </div>
        <div v-for="(item,i) in locks" :key="i" class="controls">{{ item.start }} 至 {{ item.end }}<el-button text @click="locks.splice(i,1)">移除</el-button></div>
        <el-button type="primary" :loading="busy" @click="previewPlan">比较三个方案</el-button>
        <section v-if="plan" class="card">
          <el-radio-group v-model="strategy">
            <el-radio-button v-for="item in plan.data.schemes" :key="item.strategy" :value="item.strategy">
              {{ strategies[item.strategy] }} · {{ item.scheduled.length }} 项
            </el-radio-button>
          </el-radio-group>
          <p>{{ activeScheme?.objective }}。已有安排保留 {{ plan.data.alreadyScheduled }} 项，候选共 {{ plan.data.total }} 项。</p>
          <p>变更日期 {{ activeScheme?.changedDates }} 项。</p>
          <details open>
            <summary>查看安排与差异</summary>
            <ul><li v-for="item in activeScheme?.scheduled" :key="item.task.id">
              {{ item.task.title }} · {{ item.task.plannedDate ?? '未安排' }} → {{ item.start }} 至 {{ item.end }} · 原估计 {{ item.task.minutes }} 分钟
            </li></ul>
          </details>
          <details v-if="activeScheme?.omitted.length"><summary>未安排 {{ activeScheme.omitted.length }} 项</summary>
            <ul><li v-for="item in activeScheme.omitted" :key="item.taskId">{{ item.title }} · {{ item.reason }}</li></ul>
          </details>
          <div class="controls">
            <el-button type="primary" :disabled="!activeScheme?.scheduled.length" :loading="busy" @click="decidePlan(true)">确认并保存此方案</el-button>
            <el-button :disabled="busy" @click="decidePlan(false)">取消方案</el-button>
            <router-link to="/plans">查看每日计划</router-link>
          </div>
        </section>
      </el-tab-pane>
      <el-tab-pane label="复习建议" name="reviews">
        <el-empty v-if="!reviews.length" description="暂无可关联的学习路径节点"/>
        <el-checkbox-group v-model="selected">
          <article v-for="item in reviews" :key="item.itemId" class="card">
            <el-checkbox :value="item.itemId" :disabled="!!item.reviewTaskId || !item.suggestedDate || !item.subjectId">{{ item.topic }}</el-checkbox>
            <p>{{ item.reason }} · 最后学习 {{ item.lastStudy ?? '暂无记录' }} · 建议 {{ item.suggestedDate ?? '待补证据' }}</p>
            <p v-if="item.reviewTaskId">已有复习任务 #{{ item.reviewTaskId }}</p>
            <div class="controls">
              <el-select :model-value="item.rating" placeholder="可补充自评" clearable style="width:10rem" @change="(value: typeof item.rating) => rate(item,value || null)">
                <el-option v-for="(label,value) in ratings" :key="value" :label="label" :value="value"/>
              </el-select>
              <el-button :disabled="busy" @click="rate(item,item.rating,true)">明天再看</el-button>
              <el-button :disabled="busy" @click="rate(item,item.rating,false,true)">不适用</el-button>
            </div>
          </article>
        </el-checkbox-group>
        <div class="controls">
          <label>复习日期 <input v-model="reviewDate" type="date"></label>
          <label>每项分钟 <el-input-number v-model="reviewMinutes" :min="15" :max="120"/></label>
          <el-button :disabled="!selected.length" :loading="busy" @click="reviewPreview">预览复习任务</el-button>
        </div>
        <section v-if="reviewProposal" class="card">
          <AgentData :value="reviewProposal.data"/>
          <el-button type="primary" :loading="busy" @click="decideReview(true)">确认创建所选任务</el-button>
          <el-button :disabled="busy" @click="decideReview(false)">取消</el-button>
          <router-link to="/tasks">查看任务</router-link>
        </section>
      </el-tab-pane>
    </el-tabs>
  </section>
</template>

<style scoped>
.adaptive-page { display:grid; gap:.8rem; }
.controls { display:flex; gap:.7rem; flex-wrap:wrap; align-items:center; margin:.65rem 0; }
.numbers { display:grid; grid-template-columns:repeat(auto-fit,minmax(10rem,1fr)); gap:.7rem; }
.numbers > div,.card { padding:1rem; border:1px solid var(--el-border-color); border-radius:10px; background:var(--el-bg-color); }
.numbers strong { display:block; font-size:1.35rem; }
.card { margin:.7rem 0; }
.card h2 { margin:0; font-size:1.1rem; }
.chart { width:100%; height:260px; }
input[type=date],input[type=datetime-local] { padding:.35rem; color:var(--el-text-color-primary); background:var(--el-bg-color); border:1px solid var(--el-border-color); border-radius:5px; }
</style>
