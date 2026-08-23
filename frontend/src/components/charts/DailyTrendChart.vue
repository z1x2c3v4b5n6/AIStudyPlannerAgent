<script setup lang="ts">
import { computed } from 'vue'
import type { EChartsCoreOption } from 'echarts/core'
import type { DailyTrend } from '../../types/statistics'
import { minutesLabel } from '../../utils/display'
import BaseChart from './BaseChart.vue'

const props = defineProps<{ data: DailyTrend[] }>()
const option = computed<EChartsCoreOption>(() => ({
  color: ['#315cf4', '#22d3ee'],
  backgroundColor: 'transparent',
  textStyle: { color: '#7f93b1' },
  tooltip: {
    trigger: 'axis',
    formatter: (items: Array<{ axisValue: string; seriesName: string; value: number }>) => {
      const first = items[0]
      const minutes = items.find((item) => item.seriesName === '学习时长')?.value || 0
      const count = items.find((item) => item.seriesName === '记录数')?.value || 0
      return `${first?.axisValue || ''}<br/>学习时长：${minutesLabel(minutes)}<br/>记录数：${count}`
    },
  },
  legend: { data: ['学习时长', '记录数'], bottom: 0, textStyle: { color: '#7f93b1' } },
  grid: { left: 42, right: 38, top: 24, bottom: 54, containLabel: true },
  xAxis: {
    type: 'category', data: props.data.map((item) => item.date.slice(5)), boundaryGap: true,
    axisLine: { lineStyle: { color: '#27466d' } }, axisTick: { lineStyle: { color: '#27466d' } }, axisLabel: { color: '#7085a3' },
  },
  yAxis: [
    { type: 'value', name: '分钟', minInterval: 1, nameTextStyle: { color: '#7085a3' }, axisLabel: { color: '#7085a3' }, splitLine: { lineStyle: { color: '#173154', type: 'dashed' } } },
    { type: 'value', name: '条', minInterval: 1, nameTextStyle: { color: '#7085a3' }, axisLabel: { color: '#7085a3' }, splitLine: { show: false } },
  ],
  series: [
    {
      name: '学习时长', type: 'line', smooth: true, symbolSize: 7,
      data: props.data.map((item) => item.totalMinutes),
      lineStyle: { width: 3, shadowBlur: 10, shadowColor: 'rgba(49,92,244,.24)' },
      areaStyle: { opacity: 0.12 },
    },
    {
      name: '记录数', type: 'bar', yAxisIndex: 1, barMaxWidth: 22,
      data: props.data.map((item) => item.recordCount),
      itemStyle: { borderRadius: [5, 5, 0, 0] },
    },
  ],
}))
</script>

<template><BaseChart :option="option" /></template>
