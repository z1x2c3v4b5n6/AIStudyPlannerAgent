import { spawn } from 'node:child_process'
import { createServer } from 'node:http'
import { randomUUID } from 'node:crypto'
import { createRequire } from 'node:module'
import { setTimeout } from 'node:timers/promises'
import assert from 'node:assert/strict'
import { once } from 'node:events'

const database = process.env.DB_URL ?? ''
assert.match(database, /^jdbc:mysql:\/\/(127\.0\.0\.1|localhost):\d+\/[a-zA-Z0-9_]+_test(?:\?|$)/,
  'Use a disposable local MySQL schema ending in _test')
const live = process.env.C8_LIVE_DEEPSEEK === 'true'
if (live && (!process.env.DEEPSEEK_API_KEY || process.env.CI)) throw new Error('Live mode requires a local key outside CI')
const require = createRequire(process.env.C8_PLAYWRIGHT_ROOT ?? new URL('../frontend/package.json', import.meta.url))
const { chromium } = require('playwright')
const api = 'http://localhost:8080/api/v1'
const frontend = 'http://localhost:5173'
const fixturePort = 18081
const providerTrace = []

function modelFixture(request) {
  const last = request.messages.at(-1)
  let message
  if (request.response_format?.type === 'json_object') {
    const topic = 'Spring Boot 自动配置'
    message = { role: 'assistant', content: JSON.stringify({
      title: 'Spring Boot 一周复习', summary: '验证自动配置。',
      stages: [{ stageNo: 1, title: '配置', description: '从条件装配开始', items: [{
        sequenceNo: 1, topic, learningObjective: '解释条件注解如何决定 Bean 的加载并验证配置结果',
        learningMethod: ['创建最小应用，观察条件装配报告'],
        completionCriteria: ['能解释一次条件不匹配并修改配置修复'],
        estimatedMinutes: 30, suggestedDay: 1, prerequisite: null, reason: '完成配置复习',
      }] }],
    }) }
  } else if (last.role === 'tool') {
    message = { role: 'assistant', content: '已查询测试账户的数据。' }
  } else {
    const content = String(last.content ?? '')
    const subject = content.match(/subject=(\d+)/)
    const change = content.match(/edit=(\d+)/)
    let name = 'today_tasks'
    let args = {}
    if (subject) {
      name = 'propose_task_create'
      args = { subjectId: Number(subject[1]), title: 'Agent 实际创建的任务', priority: 2, estimatedMinutes: 30 }
    } else if (change) {
      name = 'open_tasks'
      args = { title: 'Agent 实际创建的任务' }
    } else if (content.includes('目标')) name = 'list_goals'
    else if (content.includes('统计')) name = 'study_statistics'
    message = { role: 'assistant', content: null, tool_calls: [
      { id: randomUUID(), type: 'function', function: { name, arguments: JSON.stringify(args) } },
    ] }
  }
  if (last.role === 'tool') {
    const user = request.messages.findLast(item => item.role === 'user')
    const edit = String(user?.content).match(/edit=(\d+)/)
    if (edit && request.messages.at(-2)?.tool_calls?.[0]?.function?.name === 'open_tasks') {
      message = { role: 'assistant', content: null, tool_calls: [{
        id: randomUUID(), type: 'function', function: {
          name: 'propose_task_update', arguments: JSON.stringify({ taskId: Number(edit[1]), priority: 4 }),
        },
      }] }
    }
  }
  providerTrace.push(message.tool_calls?.[0]?.function?.name ?? (request.response_format ? 'learning_path' : 'answer'))
  return {
    id: randomUUID(), object: 'chat.completion', created: 1, model: request.model,
    choices: [{ index: 0, finish_reason: message.tool_calls ? 'tool_calls' : 'stop', message }],
  }
}

const fixture = createServer(async (req, res) => {
  try {
    const parts = []
    for await (const part of req) parts.push(part)
    const request = JSON.parse(Buffer.concat(parts).toString('utf8'))
    if (!live) {
      res.writeHead(200, { 'content-type': 'application/json' }).end(JSON.stringify(modelFixture(request)))
      return
    }
    const started = performance.now()
    const upstream = await fetch('https://api.deepseek.com/chat/completions', {
      method: 'POST', headers: { authorization: 'Bearer ' + process.env.DEEPSEEK_API_KEY,
        'content-type': 'application/json' }, body: JSON.stringify(request), signal: AbortSignal.timeout(85000),
    })
    const payload = await upstream.json()
    providerTrace.push({ model: payload.model ?? request.model, temperature: request.temperature, maxTokens: request.max_tokens,
      status: upstream.status, usage: payload.usage ?? null, latencyMs: performance.now() - started,
      tools: payload.choices?.flatMap(choice => choice.message.tool_calls?.map(call => call.function.name) ?? []) ?? [] })
    res.writeHead(upstream.status, { 'content-type': 'application/json' }).end(JSON.stringify(payload))
  } catch {
    res.writeHead(400).end()
  }
})
if (fixture) await new Promise(resolve => fixture.listen(fixturePort, '127.0.0.1', resolve))
const java = process.env.JAVA_HOME ? `${process.env.JAVA_HOME}/bin/java` : 'java'
const backendEnv = {
  ...process.env, AI_ENABLED: 'true',
  DEEPSEEK_API_KEY: live ? process.env.DEEPSEEK_API_KEY : randomUUID(),
  DEEPSEEK_BASE_URL: `http://127.0.0.1:${fixturePort}`,
  DEEPSEEK_COMPLETIONS_PATH: '/chat/completions',
  APP_CORS_ALLOWED_ORIGIN: frontend,
}
function launchBackend() {
  return spawn(java, ['-jar', 'backend/target/ai-study-planner-agent-0.0.1-SNAPSHOT.jar'], {
    env: backendEnv, stdio: ['ignore', 'inherit', 'inherit'],
  })
}
let backend = launchBackend()
const preview = spawn(process.execPath, ['node_modules/vite/bin/vite.js', 'preview', '--host', 'localhost', '--port', '5173'], {
  cwd: 'frontend', stdio: ['ignore', 'inherit', 'inherit'],
  env: process.env,
})
let browser
async function ready(url) {
  for (let n = 0; n < 90; n++) {
    try { await fetch(url); return } catch { await setTimeout(500) }
  }
  throw new Error(`Startup timed out at ${url}`)
}
async function call(path, creds, method = 'GET', body, expected = 200) {
  const response = await fetch(api + path, {
    method, headers: { ...(creds ? { [creds.tokenName]: creds.tokenValue } : {}), 'content-type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  const result = await response.json()
  assert.equal(response.status, expected, `${method} ${path} returned ${result.code}: ${result.message}`)
  return result.data
}
function date(offset = 0) {
  return new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Shanghai' })
    .format(new Date(Date.now() + offset * 86400000))
}
async function account() {
  const username = 'c8_' + randomUUID().replaceAll('-', '').slice(0, 12)
  const password = randomUUID().slice(0, 20)
  await call('/auth/register', null, 'POST', { username, password, nickname: '集成测试' })
  return { username, password, credentials: await call('/auth/login', null, 'POST', { username, password }) }
}
const request = (account, path, method, body, status) => call(path, account.credentials, method, body, status)
function readyStep(run, id) { return run.nodes.find(node => node.id === id) }
async function drain(account, run) {
  for (let i = 0; i < 16 && run.status === 'READY'; i++) {
    run = await request(account, `/agent/workflows/${run.id}/advance`, 'POST')
  }
  return run
}
const report = []
try {
  await ready(api + '/auth/me')
  await ready(frontend)
  const a = await account()
  const b = await account()
  const subject = await request(a, '/subjects', 'POST', { name: 'Spring Boot', color: '#2a7f62', sortOrder: 0 })
  await request(a, '/goals', 'POST', { subjectId: subject.id, title: '复习自动配置', targetMinutes: 120, status: 'ACTIVE' })
  const task = await request(a, '/tasks', 'POST', {
    subjectId: subject.id, title: 'Spring Boot 条件装配', priority: 4, estimatedMinutes: 60, plannedDate: date(),
  })
  await request(a, '/records', 'POST', {
    subjectId: subject.id, startedAt: date(-1) + 'T12:00:00', endedAt: date(-1) + 'T12:30:00',
  })
  assert.equal((await request(a, '/statistics/summary')).totalMinutes, 30)
  const sessions = '/agent/sessions'
  const created = await request(a, sessions, 'POST')
  const base = sessions + '/' + created.id
  await request(b, base, 'GET', undefined, 404)
  const start = input => request(a, base + '/workflows', 'POST', { text: input })
  const decide = (run, confirm) => request(a, `/agent/workflows/${run.id}/decision`, 'POST', {
    digest: readyStep(run, 'approval').result.digest, confirm,
  })

  let run = await drain(a, await start('帮我检查这周有哪些任务还没有完成，按紧急程度整理一下。'))
  assert.equal(run.status, 'SUCCEEDED')
  assert.ok(readyStep(run, 'tasks').result.items.some(item => item.id === task.id))
  report.push('A: real weekly task returned')

  run = await drain(a, await start('我明天只有两个小时，把重要任务重新安排一下。'))
  assert.equal(run.status, 'WAITING_APPROVAL')
  const savedRun = run.id
  const oldProcess = backend
  oldProcess.kill()
  await once(oldProcess, 'exit')
  backend = launchBackend()
  await ready(api + '/auth/me')
  run = await request(a, '/agent/workflows/' + savedRun)
  assert.equal(run.status, 'WAITING_APPROVAL')
  assert.equal((await request(a, '/tasks/' + task.id)).plannedDate, date())
  await Promise.all([decide(run, true), decide(run, true)])
  const advanced = await Promise.all([
    request(a, '/agent/workflows/' + savedRun + '/advance', 'POST'),
    request(a, '/agent/workflows/' + savedRun + '/advance', 'POST'),
  ])
  run = await drain(a, advanced.find(item => item.nodes.some(node => node.id === 'planDraft' && node.status === 'DONE')) ?? advanced[0])
  assert.equal(run.status, 'SUCCEEDED')
  assert.equal(readyStep(run, 'apply').attempts, 1)
  assert.equal((await request(a, '/tasks/' + task.id)).plannedDate, date(1))
  const draft = readyStep(run, 'planDraft').result
  const savedPlan = await request(a, '/plans/confirm', 'POST', {
    draftId: draft.draftId, planDate: draft.planDate, availableMinutes: draft.availableMinutes,
    plannedMinutes: draft.plannedMinutes, requirement: draft.requirement, summary: draft.summary,
    items: draft.items.map(({ sequenceNo, taskId, startAt, endAt, plannedMinutes, reason }) => ({
      sequenceNo, taskId, startAt, endAt, plannedMinutes, reason,
    })),
  })
  assert.ok(savedPlan.id)
  report.push('B: restart at approval; concurrent requests; task update and plan persisted')

  run = await drain(a, await start('我最近学习进度怎么样，哪些内容落后了？'))
  assert.equal(run.status, 'SUCCEEDED')
  assert.equal(readyStep(run, 'statistics').result.totalMinutes, 30)
  report.push('C: real records, statistics, and learning paths read')

  run = await drain(a, await start('我想用一周复习 Spring Boot，你帮我分析已有任务，然后安排学习路线。'))
  assert.equal(run.status, 'WAITING_APPROVAL')
  run = await drain(a, await decide(run, true))
  assert.equal(run.status, 'SUCCEEDED')
  const pathDraft = readyStep(run, 'pathDraft').result
  assert.ok(pathDraft.stages.length)
  const savedPath = await request(a, '/learning-paths/confirm', 'POST', {
    ...pathDraft, stages: undefined, totalMinutes: undefined,
    items: pathDraft.stages.flatMap(stage => stage.items.map(item => ({
      ...item, stageNo: stage.stageNo, stageTitle: stage.title, stageDescription: stage.description,
    }))),
  })
  assert.ok(savedPath.id)
  report.push('D: provider response passed real validator and path was confirmed')

  if (!live) {
    const chat = text => request(a, base + '/messages', 'POST', { text })
    const decideAction = (action, actor = a) => request(actor, base + '/actions/' + action.id + '/decision',
      'POST', { confirm: true, digest: action.digest }, actor === a ? 200 : 404)
    assert.equal((await chat('学习目标')).messages.at(-1).tools[0].data.list[0].title, '复习自动配置')
    assert.equal((await chat('学习统计')).messages.at(-1).tools[0].data.totalMinutes, 30)
    const pending = (await chat('创建任务 subject=' + subject.id)).actions.at(-1)
    await decideAction(pending, b)
    const responses = await Promise.all([decideAction(pending), decideAction(pending)])
    const actionResults = responses.map(res => res.actions.at(-1).result.id)
    assert.equal(actionResults[0], actionResults[1])
    const edit = (await chat('修改任务 edit=' + actionResults[0])).actions.at(-1)
    await decideAction(edit)
    assert.equal((await request(a, '/tasks/' + actionResults[0])).priority, 4)
    report.push('C-7: goal/stat reads, create/update, cross-account denial, durable duplicate receipt')
  }

  if (live) {
    const reply = await request(a, base + '/messages', 'POST', { text: '请查询我的未完成任务，使用工具读取实际数据' })
    assert.ok(reply.messages.at(-1).tools.some(tool => tool.success && ['open_tasks', 'today_tasks'].includes(tool.name)))
    report.push('Live provider: task tool selection and actual tool response verified')
  }

  browser = await chromium.launch({ headless: true, executablePath: process.env.C8_CHROMIUM_PATH || undefined })
  const page = await browser.newPage()
  const pageErrors = []
  page.on('pageerror', error => pageErrors.push(error.message))
  await page.goto(frontend + '/login')
  await page.getByPlaceholder('请输入用户名').fill(a.username)
  await page.getByPlaceholder('请输入登录密码').fill(a.password)
  await page.locator('.login-submit').click()
  await page.waitForURL(frontend + '/')
  await page.goto(frontend + '/agent')
  await page.getByText('工作流', { exact: true }).click()
  await page.getByRole('button', { name: '任务整理', exact: true }).click()
  await page.getByRole('button', { name: '运行工作流' }).click()
  await page.locator('.workflow-panel header .el-tag').getByText('已完成', { exact: true }).waitFor()
  await page.reload()
  await page.getByText('工作流', { exact: true }).click()
  await page.getByPlaceholder('历史工作流').click()
  await page.getByText('帮我检查这周有哪些任务还没有完成，按紧急程度整理一下。', { exact: true }).last().click()
  await page.getByText('汇总结果', { exact: true }).waitFor()
  await page.getByRole('textbox', { name: '工作流需求' }).fill('后天下午重新安排重要任务一小时')
  await page.getByRole('button', { name: '运行工作流' }).click()
  await page.getByRole('heading', { name: '核对授权范围' }).waitFor()
  await page.getByRole('button', { name: '拒绝', exact: true }).click()
  await page.locator('.workflow-panel header .el-tag').getByText('已取消', { exact: true }).waitFor()
  assert.equal((await request(a, '/tasks/' + task.id)).plannedDate, date(1))
  assert.deepEqual(pageErrors, [])
  report.push('Chromium: real login, DB-backed workflow, reload/history, approval preview and rejection')
} finally {
  console.log(JSON.stringify({ dataset: 'c8-e2e-v1', mode: live ? 'live-provider' : 'local-provider-fixture',
    passedChecks: report.length, report, providerTrace, billing: live ? 'use returned usage and account pricing' : 'zero' }))
  await browser?.close()
  backend.kill()
  preview.kill()
  fixture?.close()
}
