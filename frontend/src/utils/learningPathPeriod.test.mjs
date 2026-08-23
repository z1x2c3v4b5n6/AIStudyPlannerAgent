import test from 'node:test'
import assert from 'node:assert/strict'
import {
  exceedsLearningPathDuration,
  maxLearningPathTargetDate
} from './learningPathPeriod.js'

test('开始日计入周期时第10个自然日允许提交', () => {
  assert.equal(maxLearningPathTargetDate('2026-08-23'), '2026-09-01')
  assert.equal(exceedsLearningPathDuration('2026-08-23', '2026-09-01'), false)
})

test('第11个自然日在调用生成接口前被拦截', async () => {
  let generateCalls = 0
  const generate = async () => { generateCalls += 1 }
  if (!exceedsLearningPathDuration('2026-08-23', '2026-09-02')) await generate()
  assert.equal(generateCalls, 0)
})

test('未设置目标日期时保持开放周期语义', () => {
  assert.equal(exceedsLearningPathDuration('2026-08-23', null), false)
})
