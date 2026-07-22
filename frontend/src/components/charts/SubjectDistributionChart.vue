<script setup lang="ts">
import { computed } from 'vue'
import type { EChartsCoreOption } from 'echarts/core'
import type { SubjectDistribution } from '../../types/statistics'
import { minutesLabel } from '../../utils/display'
import BaseChart from './BaseChart.vue'

const props = defineProps<{ data: SubjectDistribution[] }>()
const fallbackColors = ['#2563eb', '#16a34a', '#d97706', '#7c3aed', '#0891b2', '#dc2626']
const option = computed<EChartsCoreOption>(() => ({
  tooltip: {
    trigger: 'item',
    formatter: (item: { name: string; data: { value: number; recordCount: number; percentage: number } }) =>
      `${item.name}<br/>学习时长：${minutesLabel(item.data.value)}<br/>记录数：${item.data.recordCount}<br/>占比：${item.data.percentage}%`,
  },
  legend: { type: 'scroll', bottom: 0 },
  series: [{
    name: '科目分布', type: 'pie', radius: ['43%', '68%'], center: ['50%', '43%'],
    label: { formatter: '{b}\n{d}%' },
    data: props.data.map((item, index) => ({
      name: item.subjectName, value: item.totalMinutes, recordCount: item.recordCount,
      percentage: item.percentage, itemStyle: { color: item.subjectColor || fallbackColors[index % fallbackColors.length] },
    })),
  }],
}))
</script>

<template><BaseChart :option="option" /></template>
