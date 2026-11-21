import { spawn } from 'node:child_process'
import { createServer } from 'node:http'
import { createServer as reservePort } from 'node:net'
import { existsSync } from 'node:fs'
import { randomUUID } from 'node:crypto'
import { createRequire } from 'node:module'
import { setTimeout } from 'node:timers/promises'
import assert from 'node:assert/strict'
import { once } from 'node:events'
import {assessmentFixture,verifyAssessments} from './c13-e2e.mjs'
import { verifyC12 } from './c12-e2e.mjs'
import { createProviderBudget } from './c12-provider.mjs'
import { verifyKnowledgeHub } from './c10-e2e.mjs'
import { verifyEvidenceRag, verifyLiveRag } from './c11-e2e.mjs'

const database = process.env.DB_URL ?? ''
assert.match(database, /^jdbc:mysql:\/\/(127\.0\.0\.1|localhost):\d+\/[a-zA-Z0-9_]+_test(?:\?|$)/,
  'Use a disposable local MySQL schema ending in _test')
const live = process.env.C8_LIVE_DEEPSEEK === 'true'
if (live && (!process.env.DEEPSEEK_API_KEY || process.env.CI)) throw new Error('Live mode requires a local key outside CI')
if (existsSync('application-local.yml'))
  throw new Error('Acceptance requires no root application-local.yml override')
async function requireVacantPort(port) {
  const socket=reservePort()
  try {
    await new Promise((resolve,reject)=>{
      socket.once('error',reject)
      socket.listen({port,exclusive:true},resolve)
    })
  } catch (failure) {
    throw new Error('Acceptance requires unused port '+port+' ('+failure.code+')')
  }
  await new Promise((resolve,reject)=>socket.close(error=>error?reject(error):resolve()))
}
await Promise.all([8080,5173,18081].map(requireVacantPort))
const require = createRequire(process.env.C8_PLAYWRIGHT_ROOT ?? new URL('../frontend/package.json', import.meta.url))
const { chromium } = require('playwright')
const api = 'http://localhost:8080/api/v1'
const frontend = 'http://localhost:5173'
const fixturePort = 18081
const providerTrace = []
const c12Live = live && process.env.C12_LIVE_DEEPSEEK === 'true'
const providerBudget = c12Live ? createProviderBudget() : null
let pauseReached
let pauseRelease
const paused = new Promise(resolve => {pauseReached=resolve})
const resume = new Promise(resolve => {pauseRelease=resolve})
const c12Gate = {
  async wait() {
    const timeout = AbortSignal.timeout(10000)
    await Promise.race([paused,new Promise((_,reject)=>timeout.addEventListener('abort',()=>{
      reject(new Error('C12 model pause fixture never reached'))
    },{once:true}))])
  },
  release: () => pauseRelease(),
}

function modelFixture(request) {
  const last = request.messages.at(-1)
  let message
  let rag
  try { rag=JSON.parse(last.content) } catch { /* Agent turns may be ordinary text. */ }
  const assessment=assessmentFixture(rag)
  if (assessment) {
    message={role:'assistant',content:JSON.stringify(assessment)}
  } else if (rag?.question && Array.isArray(rag.untrustedEvidence)) {
    const evidence=rag.untrustedEvidence[0]
    const content={claims:evidence?[{statement:'请按照该版本的官方说明核对配置。',
      evidenceId:rag.question.includes('unknowncitation')?'E999':evidence.id,quote:evidence.text.slice(0,120)}]:[]}
    message={role:'assistant',content:(rag.question.includes('c12invalidjson') || rag.question.includes('c121-invalid-model'))?'not-json':JSON.stringify(content)}
  } else if (request.response_format?.type === 'json_object') {
    for(const block of rag?.untrustedDocumentEvidence || []) providerTrace.push('c11-path:'+block.versionId)
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
    if (content.includes('c12invalidtool')) {
      name='propose_task_update'
      args={taskId:-1}
    } else if (subject) {
      name = 'propose_task_create'
      args = { subjectId: Number(subject[1]), title: 'Agent 实际创建的任务', priority: 2, estimatedMinutes: 30 }
    } else if (change) {
      name = 'open_tasks'
      args = { title: 'Agent 实际创建的任务' }
    } else if (content.includes('c13-weak-topics')) {
      name='list_weak_topics'
      args={unitId:'spring-transaction'}
    } else if (content.includes('调取学习画像')) {
      name = 'get_learning_profile'
      args = {days: 14}
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
      const last=request.messages.at(-1)
      if (last?.role==='user' && String(last.content).includes('c12pause')) {
        pauseReached()
        await resume
      }
      res.writeHead(200, { 'content-type': 'application/json' }).end(JSON.stringify(modelFixture(request)))
      return
    }
    if (providerBudget) {
      let parsed
      const last=request.messages.at(-1)
      if (last?.role==='user') {
        try { parsed=JSON.parse(last.content) } catch { /* Plain chat prompt. */ }
      }
      if (parsed?.question?.includes('c121-invalid-model') || String(request.messages.findLast(message=>message.role==='user')?.content).includes('c12invalidtool')) {
        providerTrace.push({category:'LOCAL_INJECTED_FAULT'})
        res.writeHead(200,{'content-type':'application/json'}).end(JSON.stringify(modelFixture(request)))
        return
      }
      for (const item of parsed?.untrustedDocumentEvidence ?? [])
        providerTrace.push({category:'LEARNING_PATH_EVIDENCE',versionId:item.versionId})
      const forwarded = await providerBudget.forward(request)
      providerTrace.push(forwarded.event)
      res.writeHead(forwarded.status, { 'content-type': 'application/json' }).end(JSON.stringify(forwarded.data))
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
  SPRING_APPLICATION_JSON: '{}',
  SPRING_DATASOURCE_URL: database,
  SPRING_DATASOURCE_USERNAME: process.env.DB_USERNAME || 'root',
  SPRING_DATASOURCE_PASSWORD: process.env.DB_PASSWORD || '',
  SPRING_FLYWAY_URL: database,
  SPRING_FLYWAY_USER: process.env.DB_USERNAME || 'root',
  SPRING_FLYWAY_PASSWORD: process.env.DB_PASSWORD || '',
  SERVER_PORT: '8080',
  DEEPSEEK_API_KEY: randomUUID(),
  DEEPSEEK_BASE_URL: `http://127.0.0.1:${fixturePort}`,
  DEEPSEEK_COMPLETIONS_PATH: '/chat/completions',
  APP_CORS_ALLOWED_ORIGIN: frontend,
  APP_KNOWLEDGE_ONLINE_ENABLED: 'false',
  APP_KNOWLEDGE_EXTRACTION_ENABLED: 'false',
  APP_KNOWLEDGE_FEEDBACK_THRESHOLD: '3',
}
function launchBackend() {
  return spawn(java, ['-jar', 'backend/target/ai-study-planner-agent-0.0.1-SNAPSHOT.jar'], {
    env: backendEnv, stdio: ['ignore', 'inherit', 'inherit'],
  })
}
let backend = launchBackend()
const preview = spawn(process.execPath, ['node_modules/vite/bin/vite.js', 'preview', '--host', 'localhost', '--port', '5173', '--strictPort'], {
  cwd: 'frontend', stdio: ['ignore', 'inherit', 'inherit'],
  env: process.env,
})
let browser
let completed = false
let failureCategory = null
let phase = 'service_start'
async function ready(url) {
  for (let n = 0; n < 90; n++) {
    const child=url.startsWith(api)?backend:preview
    if(child.exitCode!==null || child.signalCode!==null) throw new Error('Acceptance child exited before readiness')
    try {
      const response=await fetch(url,{signal:AbortSignal.timeout(2500)})
      if(response.status===(url.startsWith(api)?401:200)) return
    } catch { /* Wait for the Spring or Vue listener. */ }
    await setTimeout(500)
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
  if (providerBudget) {
    const readback = await providerBudget.forward({ model: process.env.DEEPSEEK_MODEL,
      messages: [{ role: 'user', content: 'Return only the JSON object {"ready":true}.' }],
      max_tokens: 256, response_format: { type: 'json_object' } })
    providerTrace.push(readback.event)
    assert.equal(readback.status, 200)
    assert.equal(JSON.parse(readback.data.choices[0].message.content).ready, true)
  }
  await ready(api + '/auth/me')
  await ready(frontend)
  phase = 'business_scenarios'
  const a = await account()
  const b = await account()
  backendEnv.APP_KNOWLEDGE_REVIEWERS = String(a.credentials.user.id)
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
    const profileTool = (await chat('调取学习画像')).messages.at(-1).tools[0]
    assert.equal(profileTool.name, 'get_learning_profile')
    assert.equal(profileTool.data.activity.totalMinutes, 30)
    const pending = (await chat('创建任务 subject=' + subject.id)).actions.at(-1)
    await decideAction(pending, b)
    const responses = await Promise.all([decideAction(pending), decideAction(pending)])
    const actionResults = responses.map(res => res.actions.at(-1).result.id)
    assert.equal(actionResults[0], actionResults[1])
    const edit = (await chat('修改任务 edit=' + actionResults[0])).actions.at(-1)
    const concurrent=await request(a,'/agent/sessions','POST')
    const concurrentPath='/agent/sessions/'+concurrent.id
    const sameEdit=(await request(a,concurrentPath+'/messages','POST',{text:'修改任务 edit='+actionResults[0]})).actions.at(-1)
    assert.equal(edit.status,'PENDING')
    assert.equal(sameEdit.status,'PENDING')
    const racing=await Promise.all([
      decideAction(edit),
      request(a,concurrentPath+'/actions/'+sameEdit.id+'/decision','POST',{confirm:true,digest:sameEdit.digest}),
    ])
    assert.deepEqual(racing.map(result=>result.actions.at(-1).status).sort(),['EXECUTED','FAILED'])
    assert.equal((await request(a, '/tasks/' + actionResults[0])).priority, 4)
    report.push('C-12 two separately approved edits to the same task: one executes, the stale proposal fails')
    const denied=await chat('c12invalidtool')
    assert.ok(denied.messages.at(-1).tools.some(tool=>tool.name==='propose_task_update'&&!tool.success))
    assert.equal(denied.actions.filter(item=>item.status==='PENDING').length,0)
    report.push('C-7: goal/stat reads, create/update, cross-account denial, durable duplicate receipt')
  }

  if (live) {
    const reply = await request(a, base + '/messages', 'POST', { text: '请查询我的未完成任务，使用工具读取实际数据' })
    assert.ok(reply.messages.at(-1).tools.some(tool => tool.success && ['open_tasks', 'today_tasks'].includes(tool.name)))
    report.push('Live provider: task tool selection and actual tool response verified')
    const profileReply = await request(a, base + '/messages', 'POST', {text:
      '请调用 get_learning_profile 查询当前账户最近14天的学习画像，并根据工具结果总结。'})
    assert.ok(profileReply.messages.at(-1).tools.some(tool =>
      tool.name === 'get_learning_profile' && tool.success && tool.data?.activity?.totalMinutes === 30))
    report.push('Live provider: C-9 profile tool selection, accepted arguments and actual tool result verified')
  }

  if (providerBudget) {
    const editDay=date(3)
    const target=await request(a,'/tasks','POST',{subjectId:subject.id,title:'C12 Live Approval Fixture',priority:2,estimatedMinutes:30,plannedDate:date()})
    const current=await request(a,'/tasks/'+target.id)
    const reply=await request(a,base+'/messages','POST',{ text:
      '请先调用 open_tasks 查询标题“C12 Live Approval Fixture”的任务，再调用 propose_task_update 生成日期变更草案。将真实任务的 plannedDate 改为 '+editDay+'。保留其他字段，等待我确认。' })
    const successful=reply.messages.at(-1).tools.filter(tool=>tool.success)
    assert.ok(successful.some(tool=>tool.name==='open_tasks'))
    assert.ok(reply.actions.some(item=>item.type==='propose_task_update' && item.status==='PENDING'))
    const pending=reply.actions.findLast(item=>item.type==='propose_task_update' && item.status==='PENDING')
    assert.equal((await request(a,'/tasks/'+target.id)).plannedDate,current.plannedDate)
    const confirm=()=>request(a,base+'/actions/'+pending.id+'/decision','POST',{confirm:true,digest:pending.digest})
    const firstApproval=await confirm()
    const secondApproval=await confirm()
    assert.equal(firstApproval.actions.find(action=>action.id===pending.id)?.status,'EXECUTED')
    assert.deepEqual(firstApproval.actions.find(action=>action.id===pending.id),secondApproval.actions.find(action=>action.id===pending.id))
    assert.equal((await request(a,'/tasks/'+target.id)).plannedDate,editDay)
    const invalid=await request(a,base+'/messages','POST',{text:'c12invalidtool'})
    assert.ok(invalid.messages.at(-1).tools.some(tool=>tool.name==='propose_task_update'&&!tool.success))
    assert.equal(invalid.actions.filter(action=>action.status==='PENDING').length,0)
    report.push('C-12.1 local invalid tool payload is rejected inside the optional live suite')
    report.push('C-12 live model: structured JSON, multi-tool read and user-approved update with duplicate receipt')
  }

  phase = 'chromium_start'
  browser = await chromium.launch({ headless: true, executablePath: process.env.C8_CHROMIUM_PATH || undefined })
  phase = 'business_and_browser_scenarios'
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
  await page.getByTestId('workflow-history').click()
  await page.getByRole('option', { name: '帮我检查这周有哪些任务还没有完成，按紧急程度整理一下。', exact: true }).first().click()
  await page.getByText('汇总结果', { exact: true }).waitFor()
  await page.getByRole('textbox', { name: '工作流需求' }).fill('后天下午重新安排重要任务一小时')
  await page.getByRole('button', { name: '运行工作流' }).click()
  await page.getByRole('heading', { name: '核对授权范围' }).waitFor()
  await page.getByRole('button', { name: '拒绝', exact: true }).click()
  await page.locator('.workflow-panel header .el-tag').getByText('已取消', { exact: true }).waitFor()
  assert.equal((await request(a, '/tasks/' + task.id)).plannedDate, date(1))
  const adaptiveTask = (title, plannedDate) => request(a, '/tasks', 'POST', {
    subjectId: subject.id, title, priority: 4, estimatedMinutes: 30, plannedDate,
  })
  const profile = await request(a, '/adaptive/profile?days=14')
  assert.equal(profile.activity.totalMinutes, 30)
  const custom = await request(a, '/adaptive/profile?from=' + date(-1) + '&to=' + date(-1))
  assert.equal(custom.activity.totalMinutes, 30)
  assert.deepEqual(custom.window, {from: date(-1), to: date(-1)})
  assert.equal((await request(b, '/adaptive/profile?days=14')).activity.totalMinutes, 0)
  assert.ok((await request(a, '/adaptive/diagnostics?days=14&dailyMinutes=60')).length >= 4)
  report.push('C-9 profile and diagnostic rules: real record data and isolated empty account')

  const adaptive = await adaptiveTask('C-9 多日安全测试', date(2))
  const before = await request(a, '/plans')
  const preview = await request(a, '/adaptive/plans/preview', 'POST', {start: date(2), days: 2, dailyMinutes: 60})
  assert.equal(preview.data.schemes.length, 3)
  assert.ok(preview.data.schemes.some(scheme => scheme.scheduled.some(item => item.task.id === adaptive.id)))
  assert.deepEqual(await request(a, '/plans'), before)
  const choice = {digest: preview.digest, strategy: 'BALANCED', confirm: true}
  const planDecision = '/adaptive/plans/' + preview.id + '/decision'
  await request(b, planDecision, 'POST', choice, 404)
  const [first, repeated] = await Promise.all([request(a, planDecision, 'POST', choice), request(a, planDecision, 'POST', choice)])
  assert.equal(first.status, 'EXECUTED')
  assert.ok(first.planIds.length > 0)
  assert.deepEqual(first.planIds, repeated.planIds)
  const committedPlans = await Promise.all(first.planIds.map(id => request(a, '/plans/' + id)))
  assert.ok(committedPlans.flatMap(plan => plan.items).some(row => row.taskId === adaptive.id))
  const staleTask = await adaptiveTask('C-9 截止时间变化测试', date(4))
  const stale = await request(a, '/adaptive/plans/preview', 'POST', {start: date(4), days: 1, dailyMinutes: 60})
  assert.ok(stale.data.schemes.find(scheme => scheme.strategy === 'MINIMAL').scheduled.some(item => item.task.id === staleTask.id))
  await request(a, '/tasks/' + staleTask.id, 'PUT', {
    subjectId: subject.id, title: staleTask.title, priority: 4, estimatedMinutes: 30,
    plannedDate: date(4), dueAt: date(4) + 'T18:00:00',
  })
  await request(a, '/adaptive/plans/' + stale.id + '/decision', 'POST',
    {digest: stale.digest, strategy: 'MINIMAL', confirm: true}, 409)
  report.push('C-9 planning: no pre-approval business write, owner check, concurrent durable replay')

  const candidate = (await request(a, '/adaptive/reviews')).find(row => row.subjectId === subject.id)
  assert.ok(candidate)
  const feedback = {rating: 'UNFAMILIAR', dismissed: false}
  await request(b, '/adaptive/reviews/' + candidate.itemId + '/feedback', 'PUT', feedback, 404)
  await request(a, '/adaptive/reviews/' + candidate.itemId + '/feedback', 'PUT', feedback)
  const reviewSelection = [{itemId: candidate.itemId, date: date(1), minutes: 20}]
  const reviewPreview = await request(a, '/adaptive/reviews/preview', 'POST', reviewSelection)
  const reviewDecision = '/adaptive/reviews/' + reviewPreview.id + '/decision'
  const reviewChoice = {digest: reviewPreview.digest, confirm: true}
  await request(b, reviewDecision, 'POST', reviewChoice, 404)
  const review = await request(a, reviewDecision, 'POST', reviewChoice)
  assert.equal((await request(a, '/tasks/' + review.taskIds[0])).title, '复习：' + candidate.topic)
  assert.deepEqual((await request(a, reviewDecision, 'POST', reviewChoice)).taskIds, review.taskIds)
  await request(a, '/adaptive/reviews/preview', 'POST', reviewSelection, 409)
  report.push('C-9 review: owned feedback, explicit confirmation, actual task, duplicate cycle rejected')

  await adaptiveTask('C-9 工作流待安排任务', date())
  let combined = await start('分析我最近两周的学习问题，安排下周计划并给我复习建议')
  const combinedUrl = '/agent/workflows/' + combined.id
  combined = await request(a, combinedUrl + '/auto-advance', 'POST')
  assert.equal(combined.status, 'WAITING_INPUT')
  await request(b, combinedUrl + '/clarification', 'POST', {dailyMinutes: 60}, 404)
  await request(a, combinedUrl + '/clarification', 'POST', {dailyMinutes: 60, subjectId: subject.id})
  combined = await request(a, combinedUrl + '/auto-advance', 'POST')
  assert.equal(combined.status, 'WAITING_APPROVAL')
  assert.equal(readyStep(combined, 'reviewCandidates').status, 'DONE')
  assert.equal(readyStep(combined, 'diagnostics').status, 'DONE')
  const eventOptions = actor => ({headers: {[actor.credentials.tokenName]: actor.credentials.tokenValue,
    accept: 'text/event-stream, application/json'}, signal: AbortSignal.timeout(18000)})
  const denied = await fetch(api + combinedUrl + '/events', eventOptions(b))
  assert.equal(denied.status, 404)
  const eventResponse = await fetch(api + combinedUrl + '/events?after=' + (combined.revision - 1), eventOptions(a))
  assert.equal(eventResponse.status, 200)
  assert.match(eventResponse.headers.get('content-type'), /^text\/event-stream/)
  const reader = eventResponse.body.getReader()
  let events = ''
  while (!events.includes('data:')) {
    const {done, value} = await reader.read()
    if (done) break
    events += new TextDecoder().decode(value)
  }
  assert.ok(events.includes('id:' + combined.revision))
  await reader.cancel()
  const repeatState = await request(a, combinedUrl + '/auto-advance', 'POST')
  assert.equal(repeatState.revision, combined.revision)
  await request(a, combinedUrl + '/decision', 'POST', {
    digest: readyStep(combined, 'adaptiveApproval').result.digest, confirm: false,
  })
  assert.equal((await request(a, combinedUrl + '/auto-advance', 'POST')).status, 'CANCELLED')
  report.push('C-9 combined: clarify, stop at approval, SSE revision recovery, denial and safe cancel')

  const runAdaptive = async text => {
    const workflow = await start(text)
    return request(a, '/agent/workflows/' + workflow.id + '/auto-advance', 'POST')
  }
  const diagnosisRun = await runAdaptive('分析我最近两周的学习情况，告诉我有哪些问题。')
  assert.equal(diagnosisRun.status, 'SUCCEEDED')
  assert.equal(readyStep(diagnosisRun, 'learningProfile').result.activity.totalMinutes, 30)
  assert.equal(readyStep(diagnosisRun, 'diagnostics').result.length, 4)
  const reviewRun = await runAdaptive('帮我找一下最近需要复习的内容。')
  assert.equal(reviewRun.status, 'SUCCEEDED')
  assert.ok(readyStep(reviewRun, 'reviewCandidates').result.some(row => row.reviewTaskId === review.taskIds[0]))
  report.push('C-9 standalone diagnosis and review workflows read actual user data')

  const complete = await runAdaptive('分析最近学习问题，安排下周计划，每天一小时，并给我复习建议')
  assert.equal(complete.status, 'WAITING_APPROVAL')
  const completionUrl = '/agent/workflows/' + complete.id
  const approvalBody = {digest: readyStep(complete, 'adaptiveApproval').result.digest, strategy: 'MINIMAL', confirm: true}
  const priorProcess = backend
  priorProcess.kill()
  await once(priorProcess, 'exit')
  backend = launchBackend()
  await ready(api + '/auth/me')
  assert.equal((await request(a, completionUrl)).status, 'WAITING_APPROVAL')
  await request(b, completionUrl + '/decision', 'POST', approvalBody, 404)
  await request(a, completionUrl + '/decision', 'POST', approvalBody)
  await Promise.all([
    request(a, completionUrl + '/auto-advance', 'POST'),
    request(a, completionUrl + '/auto-advance', 'POST'),
  ])
  const outcome = await request(a, completionUrl)
  assert.equal(outcome.status, 'SUCCEEDED')
  const write = readyStep(outcome, 'adaptiveApply')
  assert.equal(write.attempts, 1)
  assert.equal(write.result.status, 'EXECUTED')
  assert.ok(write.result.planIds.length > 0)
  for (const id of write.result.planIds) assert.ok((await request(a, '/plans/' + id)).items.length)
  report.push('C-9 combined approved workflow: exactly-once apply and persisted plan IDs')

  await page.goto(frontend + '/adaptive')
  await page.getByRole('heading', {name: '学习洞察'}).waitFor()
  await page.getByRole('tab', {name: '复习建议'}).click()
  await page.getByText(candidate.topic, {exact: true}).waitFor()
  report.push('C-9 Chromium: authenticated learning insights and actual review data')
  await verifyKnowledgeHub({a,b,account,request,page,frontend,report})
  if (!live) {
    await verifyEvidenceRag({a,b,account,request,page,frontend,report,providerTrace})
    if (process.env.C12_VERIFY_DB_FAULT==='true') await verifyC12({a,b,request,report,java,backendPid:backend.pid,gate:c12Gate,page,frontend})
  }
  if (live && process.env.C11_LIVE_DEEPSEEK==='true') await verifyLiveRag({a,request,report,providerTrace,extended:Boolean(providerBudget)})
  if(!live || process.env.C13_LIVE_ASSESSMENT==='true') {
    await verifyAssessments({a,b,request,page,frontend,report,date,live,restart:async()=>{
      const closed=once(backend,'exit')
      backend.kill()
      await closed
      backend=launchBackend()
      await ready(api+'/auth/me')
    }})
  }
  assert.deepEqual(pageErrors, [])
  completed = true
  report.push('Chromium: real login, DB-backed workflow, reload/history, approval preview and rejection')
} catch (error) {
  failureCategory = phase==='chromium_start' ? 'CHROMIUM_START'
    : phase==='service_start' ? 'SERVICE_READINESS'
    : error instanceof assert.AssertionError ? 'BUSINESS_ASSERTION' : 'SCENARIO_RUNTIME'
  throw error
} finally {
  console.log(JSON.stringify({ dataset: 'c8-c13-e2e-v1', mode: live ? 'live-provider' : 'local-provider-fixture',
    status: completed ? 'PASS' : 'FAIL', failureCategory, commit: process.env.GITHUB_SHA ?? null, passedChecks: report.length, report, providerTrace, budget: providerBudget?.summary(), billing: live ? 'use returned usage and account pricing' : 'zero' }))
  await browser?.close()
  backend.kill()
  preview.kill()
  fixture?.close()
}
