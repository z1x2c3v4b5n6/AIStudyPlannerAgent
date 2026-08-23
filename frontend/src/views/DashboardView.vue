<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { goalApi } from '../api/goal'
import { learningPathApi } from '../api/learningPath'
import { planApi } from '../api/plan'
import { statisticsApi } from '../api/statistics'
import { subjectApi } from '../api/subject'
import { taskApi } from '../api/task'
import type { LearningPathDetail, LearningPathListItem } from '../types/learningPath'
import type { PlanDetail, PlanListItem } from '../types/plan'
import type { DailyTrend, StatisticsSummary } from '../types/statistics'
import type { Subject } from '../types/subject'
import type { StudyTask, TodayTasks } from '../types/task'
import { formatDateTime, minutesLabel, priorityMap, taskStatusMap } from '../utils/display'
import { addBusinessDays, inclusiveBusinessDays, shanghaiDate, shanghaiDateTime } from '../utils/businessTime'

const subjects = ref<Subject[]>([])
const activeGoalTotal = ref(0)
const today = ref<TodayTasks>({ tasks: [], totalEstimatedMinutes: 0 })
const upcoming = ref<StudyTask[]>([])
const todayPlan = ref<PlanDetail | null>(null)
const activePath = ref<LearningPathDetail | null>(null)
const weeklySummary = ref<StatisticsSummary | null>(null)
const dailyTrend = ref<DailyTrend[]>([])

const subjectsLoading = ref(false)
const goalsLoading = ref(false)
const todayLoading = ref(false)
const upcomingLoading = ref(false)
const todayPlanLoading = ref(false)
const insightLoading = ref(false)
const subjectsFailed = ref(false)
const goalsFailed = ref(false)
const todayFailed = ref(false)
const upcomingFailed = ref(false)
const changingTaskId = ref<number | null>(null)

const subjectNames = computed(() => new Map(subjects.value.map((item) => [item.id, item.name])))
const currentPlanItem = computed(() => todayPlan.value?.items.find((item) => item.status === 'PENDING') || null)
const greeting = computed(() => {
  const hour = Number(shanghaiDateTime().slice(11, 13))
  if (hour < 6) return '夜深了，记得劳逸结合'
  if (hour < 12) return '早上好，开始今天的学习吧'
  if (hour < 18) return '下午好，继续保持专注'
  return '晚上好，完成今天的学习目标'
})
const completedTodayItems = computed(() => todayPlan.value?.completedItemCount || 0)
const pathProgress = computed(() => Math.round(activePath.value?.progress || 0))
const actualTodayMinutes = computed(() => todayPlan.value?.actualStudyMinutes || 0)
const weekStart = addBusinessDays(shanghaiDate(), -6)

const pathStages = computed(() => {
  if (!activePath.value) return []
  const stages = new Map<number, { stageNo: number; title: string; total: number; completed: number }>()
  activePath.value.items.forEach((item) => {
    const stage = stages.get(item.stageNo) || { stageNo: item.stageNo, title: item.stageTitle, total: 0, completed: 0 }
    stage.total += 1
    if (item.effectiveStatus === 'COMPLETED') stage.completed += 1
    stages.set(item.stageNo, stage)
  })
  return [...stages.values()].sort((a, b) => a.stageNo - b.stageNo).slice(0, 5)
})

const trendPoints = computed(() => {
  if (!dailyTrend.value.length) return ''
  const max = Math.max(...dailyTrend.value.map((item) => item.totalMinutes), 1)
  return dailyTrend.value.map((item, index) => {
    const x = dailyTrend.value.length === 1 ? 300 : 18 + index * (584 / (dailyTrend.value.length - 1))
    const y = 136 - (item.totalMinutes / max) * 102
    return `${x.toFixed(1)},${y.toFixed(1)}`
  }).join(' ')
})

async function loadSubjects() {
  subjectsLoading.value = true
  subjectsFailed.value = false
  try { subjects.value = (await subjectApi.list()).data.data }
  catch { subjectsFailed.value = true; subjects.value = [] }
  finally { subjectsLoading.value = false }
}

async function loadGoals() {
  goalsLoading.value = true
  goalsFailed.value = false
  try { activeGoalTotal.value = (await goalApi.list({ page: 1, pageSize: 1, status: 'ACTIVE' })).data.data.total }
  catch { goalsFailed.value = true; activeGoalTotal.value = 0 }
  finally { goalsLoading.value = false }
}

async function loadToday() {
  todayLoading.value = true
  todayFailed.value = false
  try { today.value = (await taskApi.today()).data.data }
  catch { todayFailed.value = true; today.value = { tasks: [], totalEstimatedMinutes: 0 } }
  finally { todayLoading.value = false }
}

async function loadUpcoming() {
  upcomingLoading.value = true
  upcomingFailed.value = false
  try { upcoming.value = (await taskApi.upcoming(7)).data.data }
  catch { upcomingFailed.value = true; upcoming.value = [] }
  finally { upcomingLoading.value = false }
}

async function loadTodayPlan() {
  todayPlanLoading.value = true
  try {
    const result = (await planApi.list({ page: 1, pageSize: 20, startDate: shanghaiDate(), endDate: shanghaiDate() })).data.data
    const selected = result.list.find((plan: PlanListItem) => plan.status !== 'CANCELLED')
    todayPlan.value = selected ? (await planApi.get(selected.id)).data.data : null
  } catch { todayPlan.value = null }
  finally { todayPlanLoading.value = false }
}

async function loadInsights() {
  insightLoading.value = true
  try {
    const [summaryResult, trendResult, pathResult] = await Promise.all([
      statisticsApi.summary({ startDate: weekStart, endDate: shanghaiDate() }),
      statisticsApi.dailyTrend({ startDate: weekStart, endDate: shanghaiDate() }),
      learningPathApi.list(1, 10),
    ])
    weeklySummary.value = summaryResult.data.data
    dailyTrend.value = trendResult.data.data
    const path = pathResult.data.data.list.find((item: LearningPathListItem) => item.status === 'ACTIVE')
    activePath.value = path ? (await learningPathApi.get(path.id)).data.data : null
  } catch {
    weeklySummary.value = null
    dailyTrend.value = []
    activePath.value = null
  } finally { insightLoading.value = false }
}

async function changeStatus(task: StudyTask, status: 'IN_PROGRESS' | 'COMPLETED') {
  if (changingTaskId.value) return
  changingTaskId.value = task.id
  try {
    await taskApi.changeStatus(task.id, status)
    ElMessage.success(status === 'COMPLETED' ? '任务已完成' : '任务已开始')
    await Promise.all([loadToday(), loadTodayPlan()])
  } catch {
    // The HTTP interceptor displays the server message.
  } finally { changingTaskId.value = null }
}

function scheduleLabel(task: StudyTask) {
  if (!task.plannedDate || task.plannedDate === shanghaiDate()) return '今日'
  const days = inclusiveBusinessDays(task.plannedDate, shanghaiDate()) - 1
  return days > 0 ? `逾期 ${days} 天` : task.plannedDate
}

function shortDate(date: string) { return date.slice(5).replace('-', '/') }

onMounted(() => {
  loadSubjects()
  loadGoals()
  loadToday()
  loadUpcoming()
  loadTodayPlan()
  loadInsights()
})
</script>

<template>
  <section class="dashboard-command-center">
    <div class="dashboard-ambient dashboard-ambient-one" aria-hidden="true"></div>
    <div class="dashboard-ambient dashboard-ambient-two" aria-hidden="true"></div>

    <header class="command-heading">
      <div>
        <span class="command-kicker">LEARNING COMMAND CENTER</span>
        <h1>{{ greeting }} <span aria-hidden="true">👋</span></h1>
        <p>坚持学习的每一天，都将让你更接近目标</p>
      </div>
      <button class="command-primary" type="button" @click="$router.push('/plans')">
        <span aria-hidden="true">✦</span> AI 生成学习计划 <b aria-hidden="true">→</b>
      </button>
    </header>

    <div class="command-metrics">
      <article v-loading="insightLoading" class="metric-tile metric-blue">
        <span class="metric-icon" aria-hidden="true">◷</span>
        <div><small>本周学习时长</small><strong>{{ minutesLabel(weeklySummary?.totalMinutes || 0) }}</strong><p>最近 7 天真实学习记录</p></div>
        <svg viewBox="0 0 96 32" aria-hidden="true"><polyline points="1,28 15,20 28,23 42,9 55,17 69,5 82,11 95,3" /></svg>
      </article>
      <article v-loading="todayPlanLoading" class="metric-tile metric-green">
        <span class="metric-icon" aria-hidden="true">✓</span>
        <div><small>今日已完成</small><strong>{{ completedTodayItems }}<em> 项</em></strong><p>实际学习 {{ minutesLabel(actualTodayMinutes) }}</p></div>
        <svg viewBox="0 0 96 32" aria-hidden="true"><polyline points="1,27 15,25 28,18 42,23 55,12 69,15 82,5 95,10" /></svg>
      </article>
      <article v-loading="goalsLoading" class="metric-tile metric-purple">
        <span class="metric-icon" aria-hidden="true">◆</span>
        <div><small>进行中目标</small><strong>{{ goalsFailed ? '—' : activeGoalTotal }}<em> 个</em></strong><p>持续推进长期学习方向</p></div>
        <svg viewBox="0 0 96 32" aria-hidden="true"><polyline points="1,26 15,23 28,20 42,22 55,15 69,20 82,8 95,12" /></svg>
      </article>
      <article v-loading="insightLoading" class="metric-tile metric-cyan">
        <span class="metric-icon" aria-hidden="true">◉</span>
        <div><small>学习路径完成率</small><strong>{{ pathProgress }}<em>%</em></strong><p>{{ activePath?.title || '暂无进行中的路径' }}</p></div>
        <span class="metric-ring" :style="{ '--progress': `${pathProgress * 3.6}deg` }"><i></i></span>
      </article>
    </div>

    <div class="command-main-grid">
      <article v-loading="todayPlanLoading" class="command-panel today-command-panel">
        <header><div><span class="panel-icon">▣</span><strong>今日学习计划</strong></div><button type="button" @click="$router.push('/plans')">查看完整计划 →</button></header>
        <template v-if="todayPlan">
          <div class="today-task-list">
            <div v-for="(item, index) in todayPlan.items.slice(0, 5)" :key="item.id" :class="['today-task-entry', `status-${item.status.toLowerCase()}`]">
              <span class="task-index">{{ index + 1 }}</span>
              <span class="task-state" aria-hidden="true">{{ item.status === 'COMPLETED' ? '✓' : item.status === 'SKIPPED' ? '–' : '○' }}</span>
              <div><strong>{{ item.taskTitle }}</strong><small>{{ item.subjectName }} · {{ item.startAt.slice(11, 16) }}—{{ item.endAt.slice(11, 16) }}</small></div>
              <em>{{ item.status === 'COMPLETED' ? '已完成' : item.status === 'SKIPPED' ? '已跳过' : '待执行' }}</em>
              <span>{{ item.plannedMinutes }} 分钟</span>
            </div>
          </div>
          <footer class="today-plan-footer">
            <p>今日计划 <strong>{{ todayPlan.items.length }}</strong> 项 · 已完成 <strong>{{ todayPlan.completedItemCount }}</strong> 项</p>
            <button type="button" @click="$router.push({ path: '/plans', query: { planId: todayPlan.id } })">{{ currentPlanItem ? '继续执行' : '查看结果' }} <span>▶</span></button>
          </footer>
        </template>
        <div v-else class="command-empty">
          <span>✦</span><strong>今天还没有学习计划</strong><p>让 AI 根据你的任务与可用时间生成清晰安排。</p>
          <button type="button" @click="$router.push('/plans')">生成今日计划</button>
        </div>
      </article>

      <article v-loading="insightLoading" class="command-panel path-command-panel">
        <header><div><span class="panel-icon">⌁</span><strong>AI 学习路径</strong></div><button type="button" @click="$router.push('/learning-paths')">查看完整路径 →</button></header>
        <template v-if="activePath">
          <div class="path-title-row"><div><strong>{{ activePath.title }}</strong><small>{{ activePath.subjectName || '综合学习方向' }}</small></div><span>总体进度 <b>{{ pathProgress }}%</b></span></div>
          <div class="path-progress-track"><i :style="{ width: `${pathProgress}%` }"></i></div>
          <div class="path-stage-tabs">
            <span v-for="stage in pathStages" :key="stage.stageNo" :class="{ active: stage.completed < stage.total && stage.completed > 0, done: stage.completed === stage.total }">
              <small>阶段 {{ stage.stageNo }}</small><strong>{{ stage.title }}</strong><em>{{ stage.completed }}/{{ stage.total }}</em>
            </span>
          </div>
          <div class="path-next-items">
            <div v-for="item in activePath.items.filter((entry) => entry.effectiveStatus !== 'COMPLETED').slice(0, 3)" :key="item.id">
              <span>{{ item.effectiveStatus === 'SKIPPED' ? '–' : '○' }}</span><p><strong>{{ item.topic }}</strong><small>{{ item.estimatedMinutes }} 分钟 · {{ item.stageTitle }}</small></p>
            </div>
          </div>
        </template>
        <div v-else class="command-empty compact"><span>⌁</span><strong>还没有进行中的学习路径</strong><p>从长期目标出发，让 AI 为你拆解阶段与学习主题。</p><button type="button" @click="$router.push('/learning-paths')">规划学习路径</button></div>
      </article>

      <article v-loading="insightLoading" class="command-panel trend-command-panel">
        <header><div><span class="panel-icon">⌁</span><strong>学习进度分析</strong></div><button type="button" @click="$router.push('/statistics')">近 7 天⌄</button></header>
        <div class="trend-summary"><span>学习时长趋势</span><strong>日均 {{ minutesLabel(Math.round(weeklySummary?.averageDailyMinutes || 0)) }}</strong></div>
        <div v-if="dailyTrend.length" class="trend-chart">
          <svg viewBox="0 0 620 168" preserveAspectRatio="none" aria-label="最近七天学习时长趋势">
            <defs><linearGradient id="trendFill" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#1978ff" stop-opacity=".45"/><stop offset="1" stop-color="#1978ff" stop-opacity="0"/></linearGradient></defs>
            <line v-for="y in [34, 68, 102, 136]" :key="y" x1="18" :y1="y" x2="602" :y2="y" />
            <polygon :points="`${trendPoints} 602,150 18,150`" />
            <polyline class="trend-line" :points="trendPoints" />
            <circle v-for="(point, index) in trendPoints.split(' ')" :key="index" :cx="point.split(',')[0]" :cy="point.split(',')[1]" r="4" />
          </svg>
          <div class="trend-labels"><span v-for="item in dailyTrend" :key="item.date">{{ shortDate(item.date) }}</span></div>
        </div>
        <div v-else class="mini-empty">最近 7 天暂无学习记录</div>
        <div class="trend-facts">
          <div><span>活跃学习</span><strong>{{ weeklySummary?.activeDays || 0 }} 天</strong></div>
          <div><span>学习记录</span><strong>{{ weeklySummary?.recordCount || 0 }} 条</strong></div>
          <div><span>本周总计</span><strong>{{ minutesLabel(weeklySummary?.totalMinutes || 0) }}</strong></div>
        </div>
      </article>
    </div>

    <div class="command-secondary-grid">
      <article v-loading="todayLoading" class="command-panel task-command-panel">
        <header><div><span class="panel-icon">◇</span><strong>今日待办 / 推荐执行</strong></div><button type="button" @click="$router.push('/tasks')">查看全部任务 →</button></header>
        <el-alert v-if="todayFailed" type="error" title="今日待办加载失败" :closable="false" show-icon><el-button link type="primary" @click="loadToday">重新加载</el-button></el-alert>
        <div v-else-if="today.tasks.length" class="compact-task-list">
          <div v-for="task in today.tasks.slice(0, 5)" :key="task.id">
            <span class="task-color" :style="{ background: subjects.find((item) => item.id === task.subjectId)?.color || '#2584ff' }"></span>
            <div><strong>{{ task.title }}</strong><small>{{ subjectNames.get(task.subjectId) || '未命名科目' }} · {{ scheduleLabel(task) }}</small></div>
            <em>{{ priorityMap[task.priority].label }}</em><span>{{ task.estimatedMinutes }} 分钟</span>
            <button v-if="task.status === 'TODO'" type="button" :disabled="changingTaskId === task.id" @click="changeStatus(task, 'IN_PROGRESS')">开始</button>
            <button v-else type="button" :disabled="changingTaskId === task.id" @click="changeStatus(task, 'COMPLETED')">完成</button>
          </div>
        </div>
        <div v-else class="mini-empty">今天没有待完成任务，保持得很好。</div>
      </article>

      <article v-loading="upcomingLoading" class="command-panel upcoming-command-panel">
        <header><div><span class="panel-icon">⚑</span><strong>临期任务提醒</strong></div><button type="button" @click="$router.push('/tasks')">任务中心 →</button></header>
        <el-alert v-if="upcomingFailed" type="error" title="临期任务加载失败" :closable="false" show-icon><el-button link type="primary" @click="loadUpcoming">重新加载</el-button></el-alert>
        <div v-else-if="upcoming.length" class="upcoming-list">
          <div v-for="(task, index) in upcoming.slice(0, 4)" :key="task.id">
            <span :class="`reminder-dot reminder-${index % 3}`">{{ index === 0 ? '!' : '◆' }}</span>
            <p><strong>{{ task.title }}</strong><small>{{ subjectNames.get(task.subjectId) || '未命名科目' }} · {{ formatDateTime(task.dueAt) }}</small></p>
            <el-tag size="small" :type="priorityMap[task.priority].type">{{ priorityMap[task.priority].label }}</el-tag>
          </div>
        </div>
        <div v-else class="mini-empty">未来 7 天暂无临期任务</div>
      </article>
    </div>
  </section>
</template>

<style scoped>
.dashboard-command-center {
  position: relative;
  min-height: calc(100vh - 68px);
  margin: -28px -30px -40px;
  padding: 25px 30px 42px;
  overflow: hidden;
  color: #dceaff;
  background:
    linear-gradient(rgb(32 94 173 / 4%) 1px, transparent 1px),
    linear-gradient(90deg, rgb(32 94 173 / 4%) 1px, transparent 1px),
    radial-gradient(circle at 15% 8%, rgb(12 96 215 / 12%), transparent 28%),
    #030a18;
  background-size: 42px 42px, 42px 42px, auto, auto;
}
.dashboard-ambient { position: absolute; border-radius: 50%; pointer-events: none; filter: blur(90px); }
.dashboard-ambient-one { top: 180px; left: -130px; width: 380px; height: 380px; background: rgb(0 111 255 / 9%); }
.dashboard-ambient-two { right: -160px; bottom: 60px; width: 450px; height: 450px; background: rgb(0 219 183 / 6%); }
.command-heading, .command-metrics, .command-main-grid, .command-secondary-grid { position: relative; z-index: 1; max-width: 1580px; margin-right: auto; margin-left: auto; }
.command-heading { display: flex; align-items: center; justify-content: space-between; gap: 28px; margin-bottom: 18px; }
.command-kicker { color: #277bd3; font-size: 9px; font-weight: 750; letter-spacing: 2.3px; }
.command-heading h1 { margin: 5px 0 2px; color: #f6f9ff; font-size: clamp(24px, 2.1vw, 32px); letter-spacing: -.8px; }
.command-heading h1 span { font-size: .8em; }
.command-heading p { margin: 0; color: #8191ad; font-size: 14px; }
.command-primary { display: flex; align-items: center; gap: 9px; min-height: 44px; padding: 0 20px; border: 1px solid #2079f7; border-radius: 9px; color: #fff; background: linear-gradient(105deg, #1558dc, #1685f5 56%, #11caaa); box-shadow: 0 10px 28px rgb(18 111 235 / 23%); cursor: pointer; font-weight: 700; }
.command-primary span { color: #6fffe1; }.command-primary b { margin-left: 10px; font-size: 18px; }.command-primary:hover { filter: brightness(1.1); transform: translateY(-1px); }
.command-metrics { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 14px; margin-bottom: 14px; }
.metric-tile { position: relative; display: grid; grid-template-columns: 46px 1fr 90px; align-items: center; min-height: 112px; padding: 18px 20px; overflow: hidden; border: 1px solid rgb(37 101 183 / 45%); border-radius: 12px; background: linear-gradient(135deg, rgb(7 24 55 / 92%), rgb(4 16 39 / 92%)); box-shadow: inset 0 0 28px rgb(25 96 201 / 5%); }
.metric-tile::before { position: absolute; inset: 0 auto 0 0; width: 2px; content: ''; background: currentcolor; opacity: .8; box-shadow: 0 0 14px currentcolor; }
.metric-icon { display: grid; width: 36px; height: 36px; place-items: center; border-radius: 50%; color: currentcolor; background: color-mix(in srgb, currentcolor 15%, transparent); box-shadow: 0 0 18px color-mix(in srgb, currentcolor 25%, transparent); font-size: 18px; font-weight: 800; }
.metric-tile > div { display: grid; gap: 2px; min-width: 0; }.metric-tile small { color: #a8b7cf; font-size: 12px; }.metric-tile strong { color: #f3f7ff; font-size: 25px; line-height: 1.1; }.metric-tile strong em { color: #93a4be; font-size: 12px; font-style: normal; }.metric-tile p { overflow: hidden; margin: 2px 0 0; color: #687b99; font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }
.metric-tile > svg { width: 88px; overflow: visible; }.metric-tile polyline { fill: none; stroke: currentcolor; stroke-width: 1.6; filter: drop-shadow(0 0 4px currentcolor); }.metric-blue { color: #2382ff; }.metric-green { color: #12dcb7; }.metric-purple { color: #905dff; }.metric-cyan { color: #10d4cd; }
.metric-ring { display: grid; width: 58px; height: 58px; margin-left: auto; place-items: center; border-radius: 50%; background: conic-gradient(currentcolor var(--progress), #10213c 0); box-shadow: 0 0 18px color-mix(in srgb, currentcolor 17%, transparent); }.metric-ring i { width: 43px; height: 43px; border-radius: 50%; background: #07152d; }
.command-main-grid { display: grid; grid-template-columns: 1.04fr 1.2fr 1fr; gap: 14px; margin-bottom: 14px; }
.command-secondary-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }
.command-panel { min-width: 0; min-height: 330px; overflow: hidden; border: 1px solid rgb(30 103 198 / 49%); border-radius: 12px; background: linear-gradient(145deg, rgb(5 23 52 / 96%), rgb(3 15 37 / 96%)); box-shadow: inset 0 1px rgb(109 169 255 / 5%), 0 12px 32px rgb(0 0 0 / 15%); }
.command-panel > header { display: flex; align-items: center; justify-content: space-between; gap: 10px; min-height: 49px; padding: 0 17px; border-bottom: 1px solid rgb(40 96 173 / 30%); }.command-panel > header > div { display: flex; align-items: center; gap: 9px; }.command-panel > header strong { color: #e6efff; font-size: 15px; }.command-panel > header button { border: 0; color: #238bff; background: none; cursor: pointer; font-size: 11px; white-space: nowrap; }.panel-icon { color: #e2ebfb; font-size: 16px; }
.today-task-list { padding: 8px 14px 4px; }.today-task-entry { display: grid; grid-template-columns: 24px 18px minmax(0, 1fr) auto auto; align-items: center; gap: 8px; min-height: 48px; padding: 7px 8px; border-bottom: 1px solid rgb(49 95 157 / 23%); }.task-index { display: grid; width: 21px; height: 21px; place-items: center; border-radius: 5px; color: #8da4c3; background: rgb(54 96 157 / 18%); font-size: 10px; }.task-state { color: #6180a8; }.today-task-entry.status-completed .task-state { color: #16d9b5; }.today-task-entry.status-skipped { opacity: .55; }.today-task-entry > div { display: grid; min-width: 0; gap: 2px; }.today-task-entry > div strong { overflow: hidden; color: #d9e6fa; font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }.today-task-entry > div small { overflow: hidden; color: #637896; font-size: 9px; text-overflow: ellipsis; white-space: nowrap; }.today-task-entry em { padding: 3px 7px; border-radius: 4px; color: #2696ee; background: rgb(34 137 230 / 10%); font-size: 8px; font-style: normal; }.today-task-entry > span:last-child { color: #91a4c2; font-size: 9px; white-space: nowrap; }
.today-plan-footer { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 12px 18px; }.today-plan-footer p { margin: 0; color: #8294b0; font-size: 10px; }.today-plan-footer p strong { color: #26a1ff; }.today-plan-footer button, .command-empty button { min-width: 122px; min-height: 34px; border: 0; border-radius: 7px; color: white; background: linear-gradient(100deg, #1769f5, #11d3af); cursor: pointer; font-weight: 700; }.today-plan-footer button span { margin-left: 12px; color: #03152a; }
.path-title-row { display: flex; align-items: flex-end; justify-content: space-between; gap: 12px; padding: 17px 18px 8px; }.path-title-row > div { display: grid; gap: 3px; }.path-title-row strong { color: #edf4ff; font-size: 14px; }.path-title-row small, .path-title-row > span { color: #7186a6; font-size: 9px; }.path-title-row b { color: #13d9b4; }.path-progress-track { height: 4px; margin: 0 18px 14px; overflow: hidden; border-radius: 5px; background: #112644; }.path-progress-track i { display: block; height: 100%; border-radius: inherit; background: linear-gradient(90deg, #1687ff, #19dbb7); box-shadow: 0 0 10px #16a3dc; }
.path-stage-tabs { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 6px; padding: 0 16px; }.path-stage-tabs > span { display: grid; min-height: 59px; padding: 8px; border: 1px solid rgb(45 91 153 / 38%); border-radius: 7px; color: #7184a3; background: rgb(8 27 59 / 65%); }.path-stage-tabs > span.done { border-color: rgb(21 207 174 / 35%); color: #14cfad; background: rgb(8 92 87 / 22%); }.path-stage-tabs > span.active { border-color: #1d78ed; color: #2e94ff; box-shadow: inset 0 0 15px rgb(23 111 225 / 12%); }.path-stage-tabs small { font-size: 7px; }.path-stage-tabs strong { overflow: hidden; margin: 3px 0; color: #aebed5; font-size: 8px; text-overflow: ellipsis; white-space: nowrap; }.path-stage-tabs em { margin-top: auto; font-size: 7px; font-style: normal; text-align: right; }
.path-next-items { display: grid; grid-template-columns: repeat(3, 1fr); gap: 7px; padding: 15px 16px; }.path-next-items > div { display: flex; gap: 7px; min-width: 0; min-height: 68px; padding: 9px; border: 1px solid rgb(41 90 158 / 25%); border-radius: 7px; background: rgb(5 20 47 / 72%); }.path-next-items > div > span { color: #1e8cff; }.path-next-items p { display: grid; min-width: 0; align-content: center; gap: 4px; margin: 0; }.path-next-items strong { overflow: hidden; color: #bdcce0; font-size: 9px; text-overflow: ellipsis; white-space: nowrap; }.path-next-items small { color: #627796; font-size: 7px; }
.trend-summary { display: flex; align-items: center; justify-content: space-between; padding: 16px 18px 0; color: #8295b4; font-size: 10px; }.trend-summary strong { color: #2394ff; font-size: 10px; }.trend-chart { padding: 5px 15px 0; }.trend-chart svg { width: 100%; height: 150px; overflow: visible; }.trend-chart line { stroke: rgb(55 97 156 / 24%); stroke-width: 1; }.trend-chart polygon { fill: url(#trendFill); }.trend-chart .trend-line { fill: none; stroke: #2486ff; stroke-width: 3; filter: drop-shadow(0 0 5px #126de0); }.trend-chart circle { fill: #eaf5ff; stroke: #167cff; stroke-width: 3; }.trend-labels { display: flex; justify-content: space-between; color: #637793; font-size: 8px; }.trend-facts { display: grid; grid-template-columns: repeat(3, 1fr); margin: 17px 16px 0; border-top: 1px solid rgb(43 91 155 / 27%); }.trend-facts div { display: grid; gap: 4px; padding: 12px 8px; text-align: center; }.trend-facts span { color: #647896; font-size: 8px; }.trend-facts strong { color: #bed0e9; font-size: 10px; }
.command-empty { display: grid; min-height: 280px; padding: 30px; place-content: center; justify-items: center; text-align: center; }.command-empty.compact { min-height: 270px; }.command-empty > span { color: #1d8fff; font-size: 30px; }.command-empty strong { margin-top: 9px; color: #d6e5fa; }.command-empty p { max-width: 280px; margin: 7px 0 17px; color: #6f84a4; font-size: 11px; line-height: 1.6; }
.command-secondary-grid .command-panel { min-height: 235px; }.compact-task-list, .upcoming-list { padding: 7px 17px 12px; }.compact-task-list > div { display: grid; grid-template-columns: 8px minmax(0, 1fr) auto auto 54px; align-items: center; gap: 10px; min-height: 38px; border-bottom: 1px solid rgb(46 91 152 / 22%); }.task-color { width: 7px; height: 7px; border-radius: 2px; box-shadow: 0 0 7px currentcolor; }.compact-task-list > div > div { display: grid; min-width: 0; gap: 2px; }.compact-task-list strong { overflow: hidden; color: #bdcde3; font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }.compact-task-list small { color: #637895; font-size: 8px; }.compact-task-list em { padding: 2px 6px; border-radius: 4px; color: #268fe8; background: rgb(35 130 213 / 10%); font-size: 8px; font-style: normal; }.compact-task-list > div > span:nth-last-child(2) { color: #8295b2; font-size: 9px; }.compact-task-list button { border: 0; color: #15d4b0; background: transparent; cursor: pointer; font-size: 9px; }.compact-task-list button:disabled { color: #546780; cursor: wait; }
.upcoming-list > div { display: grid; grid-template-columns: 30px minmax(0, 1fr) auto; align-items: center; gap: 10px; min-height: 44px; border-bottom: 1px solid rgb(46 91 152 / 22%); }.reminder-dot { display: grid; width: 27px; height: 27px; place-items: center; border-radius: 50%; font-size: 11px; }.reminder-0 { color: #ffbb19; background: rgb(255 175 0 / 16%); box-shadow: 0 0 12px rgb(255 175 0 / 12%); }.reminder-1 { color: #14dbb7; background: rgb(20 219 183 / 13%); }.reminder-2 { color: #238cff; background: rgb(35 140 255 / 13%); }.upcoming-list p { display: grid; min-width: 0; gap: 3px; margin: 0; }.upcoming-list strong { overflow: hidden; color: #bfcee2; font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }.upcoming-list small { color: #667b98; font-size: 8px; }.mini-empty { display: grid; min-height: 155px; place-items: center; color: #617694; font-size: 11px; }
.command-panel :deep(.el-loading-mask) { background: rgb(3 14 34 / 82%); }.command-panel :deep(.el-alert) { margin: 14px; }.command-panel :deep(.el-tag) { border-color: rgb(60 117 194 / 22%); background: rgb(30 87 163 / 10%); }
@media (max-width: 1280px) { .command-metrics { grid-template-columns: repeat(2, 1fr); }.command-main-grid { grid-template-columns: 1fr 1fr; }.trend-command-panel { grid-column: 1 / -1; }.trend-chart svg { height: 180px; } }
@media (max-width: 900px) { .command-heading { align-items: flex-start; }.command-main-grid, .command-secondary-grid { grid-template-columns: 1fr; }.trend-command-panel { grid-column: auto; } }
@media (max-width: 720px) { .dashboard-command-center { margin: -18px -14px; padding: 20px 14px 34px; }.command-heading { display: grid; }.command-primary { width: 100%; justify-content: center; }.command-metrics { grid-template-columns: 1fr; }.metric-tile { grid-template-columns: 44px 1fr 72px; }.metric-tile > svg { width: 68px; }.command-panel { min-height: auto; }.today-task-entry { grid-template-columns: 22px 18px minmax(0, 1fr) auto; }.today-task-entry > span:last-child { display: none; }.today-plan-footer { align-items: flex-start; flex-direction: column; }.today-plan-footer button { width: 100%; }.path-stage-tabs { grid-template-columns: repeat(2, 1fr); }.path-next-items { grid-template-columns: 1fr; }.compact-task-list > div { grid-template-columns: 8px minmax(0, 1fr) auto 50px; padding: 6px 0; }.compact-task-list em { display: none; } }
@media (max-width: 420px) { .command-heading h1 { font-size: 22px; }.metric-tile { padding: 16px 14px; }.command-panel > header { padding: 0 12px; }.path-stage-tabs { grid-template-columns: 1fr 1fr; }.today-task-entry em { display: none; }.today-task-entry { grid-template-columns: 20px 16px minmax(0, 1fr); }.compact-task-list > div > span:nth-last-child(2) { display: none; }.compact-task-list > div { grid-template-columns: 8px minmax(0, 1fr) 48px; } }
</style>
