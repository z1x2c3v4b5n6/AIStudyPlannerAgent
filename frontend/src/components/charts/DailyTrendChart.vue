<script setup lang="ts">
import { computed } from 'vue'
import type { EChartsCoreOption } from 'echarts/core'
import type { DailyTrend } from '../../types/statistics'
import { minutesLabel } from '../../utils/display'
import BaseChart from './BaseChart.vue'

const props = defineProps<{ data: DailyTrend[] }>()
const option = computed<EChartsCoreOption>(() => ({
  color: ['#2563eb', '#93c5fd'],
  tooltip: {
    trigger: 'axis',
    formatter: (items: Array<{ axisValue: string; seriesName: string; value: number }>) => {
      const first = items[0]
      const minutes = items.find((item) => item.seriesName === '学习时长')?.value || 0
      const count = items.find((item) => item.seriesName === '记录数')?.value || 0
      return `${first?.axisValue || ''}<br/>学习时长：${minutesLabel(minutes)}<br/>记录数：${count}`
    },
  },
  legend: { data: ['学习时长', '记录数'], bottom: 0 },
  grid: { left: 42, right: 38, top: 24, bottom: 54, containLabel: true },
  xAxis: { type: 'category', data: props.data.map((item) => item.date.slice(5)), boundaryGap: true },
  yAxis: [
    { type: 'value', name: '分钟', minInterval: 1 },
    { type: 'value', name: '条', minInterval: 1 },
  ],
  series: [
    { name: '学习时长', type: 'line', smooth: true, symbolSize: 7, data: props.data.map((item) => item.totalMinutes), areaStyle: { opacity: 0.08 } },
    { name: '记录数', type: 'bar', yAxisIndex: 1, barMaxWidth: 22, data: props.data.map((item) => item.recordCount) },
  ],
}))
</script>

<template><BaseChart :option="option" /></template>
