<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { goalApi } from '../api/goal'
import { subjectApi } from '../api/subject'
import { taskApi } from '../api/task'
import type { Subject } from '../types/subject'
import type { StudyTask, TodayTasks } from '../types/task'
import { formatDateTime, minutesLabel, priorityMap, taskStatusMap } from '../utils/display'
import { inclusiveBusinessDays, shanghaiDate } from '../utils/businessTime'

const subjects = ref<Subject[]>([]), activeGoalTotal = ref(0), today = ref<TodayTasks>({ tasks: [], totalEstimatedMinutes: 0 }), upcoming = ref<StudyTask[]>([])
const subjectsLoading = ref(false), goalsLoading = ref(false), todayLoading = ref(false), upcomingLoading = ref(false)
const subjectsFailed = ref(false), goalsFailed = ref(false), todayFailed = ref(false), upcomingFailed = ref(false)
const changingTaskId = ref<number | null>(null)
const subjectNames = computed(() => new Map(subjects.value.map((item) => [item.id, item.name])))

async function loadSubjects() { subjectsLoading.value = true; subjectsFailed.value = false; try { subjects.value = (await subjectApi.list()).data.data } catch { subjectsFailed.value = true; subjects.value = [] } finally { subjectsLoading.value = false } }
async function loadGoals() { goalsLoading.value = true; goalsFailed.value = false; try { activeGoalTotal.value = (await goalApi.list({ page: 1, pageSize: 1, status: 'ACTIVE' })).data.data.total } catch { goalsFailed.value = true; activeGoalTotal.value = 0 } finally { goalsLoading.value = false } }
async function loadToday() { todayLoading.value = true; todayFailed.value = false; try { today.value = (await taskApi.today()).data.data } catch { todayFailed.value = true; today.value = { tasks: [], totalEstimatedMinutes: 0 } } finally { todayLoading.value = false } }
async function loadUpcoming() { upcomingLoading.value = true; upcomingFailed.value = false; try { upcoming.value = (await taskApi.upcoming(7)).data.data } catch { upcomingFailed.value = true; upcoming.value = [] } finally { upcomingLoading.value = false } }
async function changeStatus(task: StudyTask, status: 'IN_PROGRESS' | 'COMPLETED') { if (changingTaskId.value) return; changingTaskId.value = task.id; try { await taskApi.changeStatus(task.id, status); ElMessage.success(status === 'COMPLETED' ? '任务已完成' : '任务已开始'); await loadToday() } catch { /* HTTP 拦截器统一提示 */ } finally { changingTaskId.value = null } }
function scheduleLabel(task: StudyTask) {
  if (!task.plannedDate || task.plannedDate === shanghaiDate()) return '今日'
  const days = inclusiveBusinessDays(task.plannedDate, shanghaiDate()) - 1
  return days > 0 ? `逾期 ${days} 天` : task.plannedDate
}
onMounted(() => { loadSubjects(); loadGoals(); loadToday(); loadUpcoming() })
</script>

<template>
  <section class="page-section">
    <div class="page-heading"><div><h1>学习概览</h1><p>聚焦今天要做的事，并留意即将到期的任务。</p></div></div>
    <div class="stat-grid">
      <el-card v-loading="subjectsLoading" shadow="never" class="stat-card"><span>学习科目</span><strong>{{ subjectsFailed ? '—' : subjects.length }}</strong><small v-if="subjectsFailed">加载失败</small></el-card>
      <el-card v-loading="goalsLoading" shadow="never" class="stat-card"><span>进行中目标</span><strong>{{ goalsFailed ? '—' : activeGoalTotal }}</strong><small v-if="goalsFailed">加载失败</small></el-card>
      <el-card v-loading="todayLoading" shadow="never" class="stat-card"><span>今日待办</span><strong>{{ todayFailed ? '—' : today.tasks.length }}</strong><small v-if="todayFailed">加载失败</small></el-card>
      <el-card v-loading="todayLoading" shadow="never" class="stat-card"><span>待办预计学习</span><strong class="minutes-value">{{ todayFailed ? '—' : minutesLabel(today.totalEstimatedMinutes) }}</strong><small v-if="todayFailed">加载失败</small></el-card>
    </div>
    <div class="dashboard-grid">
      <el-card shadow="never" class="panel-card dashboard-panel"><template #header><div class="card-header"><strong>今日待办</strong><el-button text type="primary" @click="$router.push('/tasks')">查看全部</el-button></div></template><div v-loading="todayLoading"><el-alert v-if="todayFailed" type="error" title="今日待办加载失败" :closable="false" show-icon><el-button link type="primary" @click="loadToday">重新加载</el-button></el-alert><el-empty v-else-if="!today.tasks.length" description="没有待完成任务" :image-size="80" /><div v-else class="task-stack"><article v-for="task in today.tasks" :key="task.id" class="task-row"><div><strong>{{ task.title }}</strong><p>{{ subjectNames.get(task.subjectId) || '未知科目' }} · {{ minutesLabel(task.estimatedMinutes) }}</p></div><div class="task-row-meta"><el-tag :type="task.plannedDate && task.plannedDate < shanghaiDate() ? 'danger' : 'success'">{{ scheduleLabel(task) }}</el-tag><el-tag :type="priorityMap[task.priority].type">{{ priorityMap[task.priority].label }}</el-tag><el-tag :type="taskStatusMap[task.status].type">{{ taskStatusMap[task.status].label }}</el-tag><el-button v-if="task.status === 'TODO'" size="small" :loading="changingTaskId === task.id" @click="changeStatus(task, 'IN_PROGRESS')">开始</el-button><el-button type="primary" size="small" :loading="changingTaskId === task.id" @click="changeStatus(task, 'COMPLETED')">完成</el-button></div></article></div></div></el-card>
      <el-card shadow="never" class="panel-card dashboard-panel"><template #header><div class="card-header"><strong>即将截止</strong><span class="muted">未来 7 天</span></div></template><div v-loading="upcomingLoading"><el-alert v-if="upcomingFailed" type="error" title="即将截止任务加载失败" :closable="false" show-icon><el-button link type="primary" @click="loadUpcoming">重新加载</el-button></el-alert><el-empty v-else-if="!upcoming.length" description="未来 7 天暂无到期任务" :image-size="80" /><div v-else class="task-stack"><article v-for="task in upcoming" :key="task.id" class="task-row"><div><strong>{{ task.title }}</strong><p>{{ subjectNames.get(task.subjectId) || '未知科目' }} · {{ formatDateTime(task.dueAt) }}</p></div><el-tag :type="priorityMap[task.priority].type">{{ priorityMap[task.priority].label }}</el-tag></article></div></div></el-card>
    </div>
  </section>
</template>
