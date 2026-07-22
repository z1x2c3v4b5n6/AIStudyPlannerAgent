<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { statisticsApi } from '../../api/statistics'
import DailyTrendChart from '../../components/charts/DailyTrendChart.vue'
import SubjectDistributionChart from '../../components/charts/SubjectDistributionChart.vue'
import type { DailyTrend, StatisticsSummary, SubjectDistribution } from '../../types/statistics'
import { minutesLabel } from '../../utils/display'
import { addBusinessDays, inclusiveBusinessDays, shanghaiDate } from '../../utils/businessTime'

const dateRange = ref<string[] | null>(null)
const appliedRange = reactive({ startDate: '', endDate: '' })
const summary = ref<StatisticsSummary | null>(null)
const trend = ref<DailyTrend[]>([])
const distribution = ref<SubjectDistribution[]>([])
const summaryLoading = ref(false), trendLoading = ref(false), distributionLoading = ref(false)
const summaryFailed = ref(false), trendFailed = ref(false), distributionFailed = ref(false)
const fallbackColors = ['#2563eb', '#16a34a', '#d97706', '#7c3aed', '#0891b2', '#dc2626']
let requestVersion = 0

function rangeForDays(days: number) {
  const end = shanghaiDate()
  return [addBusinessDays(end, -days + 1), end]
}

function validateRange(range: string[] | null): range is string[] {
  if (!range || range.length !== 2) { ElMessage.warning('请选择完整的日期范围'); return false }
  const days = inclusiveBusinessDays(range[0], range[1])
  if (days < 1) { ElMessage.warning('开始日期不能晚于结束日期'); return false }
  if (days > 366) { ElMessage.warning('统计日期范围不能超过 366 天'); return false }
  return true
}

function params() { return { startDate: appliedRange.startDate, endDate: appliedRange.endDate } }

async function loadSummary(version = requestVersion) { summaryLoading.value = true; summaryFailed.value = false; try { const data = (await statisticsApi.summary(params())).data.data; if (version === requestVersion) summary.value = data } catch { if (version === requestVersion) { summaryFailed.value = true; summary.value = null } } finally { if (version === requestVersion) summaryLoading.value = false } }
async function loadTrend(version = requestVersion) { trendLoading.value = true; trendFailed.value = false; try { const data = (await statisticsApi.dailyTrend(params())).data.data; if (version === requestVersion) trend.value = data } catch { if (version === requestVersion) { trendFailed.value = true; trend.value = [] } } finally { if (version === requestVersion) trendLoading.value = false } }
async function loadDistribution(version = requestVersion) { distributionLoading.value = true; distributionFailed.value = false; try { const data = (await statisticsApi.subjectDistribution(params())).data.data; if (version === requestVersion) distribution.value = data } catch { if (version === requestVersion) { distributionFailed.value = true; distribution.value = [] } } finally { if (version === requestVersion) distributionLoading.value = false } }
function loadAll() { const version = ++requestVersion; loadSummary(version); loadTrend(version); loadDistribution(version) }

function applyRange(range: string[] | null) {
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
