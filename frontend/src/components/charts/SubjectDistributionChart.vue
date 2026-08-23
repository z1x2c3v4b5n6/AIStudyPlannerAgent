<script setup lang="ts">
import { computed } from 'vue'
import type { EChartsCoreOption } from 'echarts/core'
import type { SubjectDistribution } from '../../types/statistics'
import { minutesLabel } from '../../utils/display'
import BaseChart from './BaseChart.vue'

const props = defineProps<{ data: SubjectDistribution[] }>()
const fallbackColors = ['#315cf4', '#06b6d4', '#7c3aed', '#10b981', '#f59e0b', '#ec4899']
const option = computed<EChartsCoreOption>(() => ({
  backgroundColor: 'transparent',
  textStyle: { color: '#7f93b1' },
  tooltip: {
    trigger: 'item',
    formatter: (item: { name: string; data: { value: number; recordCount: number; percentage: number } }) =>
      `${item.name}<br/>学习时长：${minutesLabel(item.data.value)}<br/>记录数：${item.data.recordCount}<br/>占比：${item.data.percentage}%`,
  },
  legend: { type: 'scroll', bottom: 0, textStyle: { color: '#7f93b1' }, pageTextStyle: { color: '#7f93b1' } },
  series: [{
    name: '科目分布', type: 'pie', radius: ['45%', '70%'], center: ['50%', '43%'],
    itemStyle: { borderColor: '#07172f', borderWidth: 3, borderRadius: 5 },
    emphasis: { scaleSize: 8, itemStyle: { shadowBlur: 18, shadowColor: 'rgba(49,92,244,.2)' } },
    label: { formatter: '{b}\n{d}%', color: '#9fb2cc' },
    data: props.data.map((item, index) => ({
      name: item.subjectName, value: item.totalMinutes, recordCount: item.recordCount,
      percentage: item.percentage, itemStyle: { color: item.subjectColor || fallbackColors[index % fallbackColors.length] },
    })),
  }],
}))
</script>

<template><BaseChart :option="option" /></template>
