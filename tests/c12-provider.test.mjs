import test from 'node:test'
import assert from 'node:assert/strict'
import { createProviderBudget } from './c12-provider.mjs'

const config = { C12_MAX_CALLS:'1', C12_MAX_TOKENS:'10000', C12_MAX_COST:'1',
  C12_INPUT_PRICE_PER_MILLION:'1', C12_OUTPUT_PRICE_PER_MILLION:'2',
  DEEPSEEK_MODEL:'test-model', DEEPSEEK_API_KEY:'synthetic-local-value' }
const request = { model:'test-model', messages:[{role:'user',content:'synthetic fixture'}], max_tokens:24 }
test('forwards only one bounded attempt including overlapping requests',async()=>{
  const budget=createProviderBudget(config)
  let calls=0
  const stub=async()=>{calls++;return new Response(JSON.stringify({model:'test-model',usage:{prompt_tokens:6,completion_tokens:9}}))}
  const results=await Promise.all([budget.forward(request,stub),budget.forward(request,stub)])
  assert.deepEqual(results.map(x=>x.event.category),['OK','BUDGET_EXHAUSTED'])
  assert.equal(calls,1)
  assert.equal(budget.summary().outputTokens,9)
  assert.ok(budget.summary().reservedTokens>budget.summary().inputTokens)
})
test('refuses nonmatching model and over-cap outputs before any call',async()=>{
  const budget=createProviderBudget(config)
  const never=()=>{throw new Error('must never contact network')}
  assert.equal((await budget.forward({...request,model:'other'},never)).event.category,'MODEL_MISMATCH')
  assert.equal((await budget.forward({...request,max_tokens:99999},never)).event.category,'OUTPUT_CAP_REQUIRED')
  assert.equal(budget.summary().attempts,0)
})
test('keeps failed transport reservations and classifies timeout',async()=>{
  const budget=createProviderBudget(config)
  const timeout=async()=>{throw new DOMException('fixture timeout','TimeoutError')}
  assert.equal((await budget.forward(request,timeout)).event.category,'PROVIDER_TIMEOUT')
  assert.equal((await budget.forward(request,timeout)).event.category,'BUDGET_EXHAUSTED')
  assert.equal(budget.summary().failures,1)
})
