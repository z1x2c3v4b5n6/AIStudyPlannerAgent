import test from 'node:test'
import assert from 'node:assert/strict'
import {
  calculatePlanProgress,
  singlePlanSummary
} from './planProgress.js'

const item = (status, plannedMinutes = 45) => ({ status, plannedMinutes })

test('单任务待执行时使用状态摘要且完成率为0', () => {
  assert.equal(singlePlanSummary(item('PENDING')), '待执行 · 计划45分钟')
  assert.equal(calculatePlanProgress([item('PENDING')]).completionPercentage, 0)
})

test('单任务完成时显示实际学习时长', () => {
  assert.equal(singlePlanSummary(item('COMPLETED')), '已完成 · 实际学习45分钟')
})

test('单任务跳过时显示未产生学习记录', () => {
  assert.equal(singlePlanSummary(item('SKIPPED')), '已跳过 · 未产生学习记录')
})

test('多任务进度只按完成任务计算', () => {
  const progress = calculatePlanProgress([
    item('COMPLETED'),
    item('PENDING'),
    item('SKIPPED')
  ])
  assert.deepEqual(progress, {
    totalCount: 3,
    completedCount: 1,
    skippedCount: 1,
    pendingCount: 1,
    completionPercentage: 33.33
  })
})

test('跳过任务不计入完成率', () => {
  const progress = calculatePlanProgress([
    item('COMPLETED'),
    item('SKIPPED')
  ])
  assert.equal(progress.completedCount, 1)
  assert.equal(progress.skippedCount, 1)
  assert.equal(progress.completionPercentage, 50)
})

test('全部完成时完成率为100', () => {
  const progress = calculatePlanProgress([
    item('COMPLETED'),
    item('COMPLETED')
  ])
  assert.equal(progress.completionPercentage, 100)
})
