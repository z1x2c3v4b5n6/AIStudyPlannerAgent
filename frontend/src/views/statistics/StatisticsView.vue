<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { statisticsApi } from '../../api/statistics'
import DailyTrendChart from '../../components/charts/DailyTrendChart.vue'
import SubjectDistributionChart from '../../components/charts/SubjectDistributionChart.vue'
import type { DailyTrend, StatisticsSummary, SubjectDistribution } from '../../types/statistics'
import { minutesLabel } from '../../utils/display'

const dateRange = ref<string[]>([])
const appliedRange = reactive({ startDate: '', endDate: '' })
const summary = ref<StatisticsSummary | null>(null)
const trend = ref<DailyTrend[]>([])
const distribution = ref<SubjectDistribution[]>([])
const summaryLoading = ref(false), trendLoading = ref(false), distributionLoading = ref(false)
const summaryFailed = ref(false), trendFailed = ref(false), distributionFailed = ref(false)
const fallbackColors = ['#2563eb', '#16a34a', '#d97706', '#7c3aed', '#0891b2', '#dc2626']

function localDateString(date: Date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function rangeForDays(days: number) {
  const end = new Date()
  const start = new Date(end.getFullYear(), end.getMonth(), end.getDate() - days + 1)
  return [localDateString(start), localDateString(end)]
}

function validateRange(range: string[]) {
  if (range.length !== 2) { ElMessage.warning('请选择完整的日期范围'); return false }
  const start = new Date(`${range[0]}T00:00:00`).getTime()
  const end = new Date(`${range[1]}T00:00:00`).getTime()
  if (start > end) { ElMessage.warning('开始日期不能晚于结束日期'); return false }
  if (Math.round((end - start) / 86_400_000) + 1 > 366) { ElMessage.warning('统计日期范围不能超过 366 天'); return false }
  return true
}

function params() { return { startDate: appliedRange.startDate, endDate: appliedRange.endDate } }

async function loadSummary() { summaryLoading.value = true; summaryFailed.value = false; try { summary.value = (await statisticsApi.summary(params())).data.data } catch { summaryFailed.value = true; summary.value = null } finally { summaryLoading.value = false } }
async function loadTrend() { trendLoading.value = true; trendFailed.value = false; try { trend.value = (await statisticsApi.dailyTrend(params())).data.data } catch { trendFailed.value = true; trend.value = [] } finally { trendLoading.value = false } }
async function loadDistribution() { distributionLoading.value = true; distributionFailed.value = false; try { distribution.value = (await statisticsApi.subjectDistribution(params())).data.data } catch { distributionFailed.value = true; distribution.value = [] } finally { distributionLoading.value = false } }
function loadAll() { loadSummary(); loadTrend(); loadDistribution() }

function applyRange(range: string[]) {
  if (!validateRange(range)) return
  dateRange.value = [...range]
  appliedRange.startDate = range[0]
  appliedRange.endDate = range[1]
  loadAll()
}

function applyCustomRange() { applyRange(dateRange.value) }
const hasTrendData = computed(() => trend.value.some((item) => item.totalMinutes > 0 || item.recordCount > 0))

onMounted(() => applyRange(rangeForDays(7)))
</script>

<template>
  <section class="page-section">
    <div class="page-heading"><div><h1>数据统计</h1><p>从学习时长、活跃天数和科目投入了解近期学习节奏。</p></div></div>
    <el-card shadow="never" class="panel-card range-panel">
      <div class="range-actions"><span class="range-label">统计范围</span><el-button-group><el-button @click="applyRange(rangeForDays(7))">最近 7 天</el-button><el-button @click="applyRange(rangeForDays(30))">最近 30 天</el-button><el-button @click="applyRange(rangeForDays(90))">最近 90 天</el-button></el-button-group><el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" /><el-button type="primary" @click="applyCustomRange">查询</el-button></div>
    </el-card>

    <div v-loading="summaryLoading" class="statistics-section">
      <el-alert v-if="summaryFailed" title="统计概览加载失败" type="error" :closable="false" show-icon><el-button link type="primary" @click="loadSummary">重新加载</el-button></el-alert>
      <div v-else class="statistics-grid">
        <el-card shadow="never" class="stat-card"><span>总学习时长</span><strong class="minutes-value">{{ minutesLabel(summary?.totalMinutes || 0) }}</strong></el-card>
        <el-card shadow="never" class="stat-card"><span>学习记录数</span><strong>{{ summary?.recordCount || 0 }}</strong></el-card>
        <el-card shadow="never" class="stat-card"><span>活跃天数</span><strong>{{ summary?.activeDays || 0 }}</strong></el-card>
        <el-card shadow="never" class="stat-card"><span>日均学习时长</span><strong class="minutes-value">{{ minutesLabel(Math.round(summary?.averageDailyMinutes || 0)) }}</strong></el-card>
        <el-card shadow="never" class="stat-card"><span>活跃日均时长</span><strong class="minutes-value">{{ minutesLabel(Math.round(summary?.averageMinutesPerActiveDay || 0)) }}</strong></el-card>
      </div>
    </div>

    <div class="statistics-chart-grid">
      <el-card shadow="never" class="panel-card chart-panel"><template #header><div class="card-header"><strong>每日学习趋势</strong><span class="muted">{{ appliedRange.startDate }} 至 {{ appliedRange.endDate }}</span></div></template><div v-loading="trendLoading"><el-alert v-if="trendFailed" title="每日趋势加载失败" type="error" :closable="false" show-icon><el-button link type="primary" @click="loadTrend">重新加载</el-button></el-alert><el-empty v-else-if="!hasTrendData" description="当前范围暂无学习记录" :image-size="80" /><DailyTrendChart v-else :data="trend" /></div></el-card>
      <el-card shadow="never" class="panel-card chart-panel"><template #header><div class="card-header"><strong>科目学习时长分布</strong><span class="muted">按总时长排序</span></div></template><div v-loading="distributionLoading"><el-alert v-if="distributionFailed" title="科目分布加载失败" type="error" :closable="false" show-icon><el-button link type="primary" @click="loadDistribution">重新加载</el-button></el-alert><el-empty v-else-if="!distribution.length" description="当前范围暂无科目数据" :image-size="80" /><template v-else><SubjectDistributionChart :data="distribution" /><div class="distribution-list"><div v-for="(item, index) in distribution" :key="item.subjectId" class="distribution-item"><span class="color-dot" :style="{ backgroundColor: item.subjectColor || fallbackColors[index % fallbackColors.length] }" /><div><strong>{{ item.subjectName }}</strong><small>{{ item.recordCount }} 条记录</small></div><span>{{ minutesLabel(item.totalMinutes) }}</span><b>{{ item.percentage }}%</b></div></div></template></div></el-card>
    </div>
  </section>
</template>
