import assert from 'node:assert/strict'
import { randomUUID } from 'node:crypto'

const topic='spring-transaction'
const premise='In proxy mode, only external method calls coming in through the proxy are intercepted. This means that self-invocation does not enable a new transaction.'
const answer='only external method calls coming in through the proxy are intercepted'
const sourceText=premise+' An existing outer transaction has to be evaluated separately.'
let sequence=0

export function assessmentFixture(input) {
  if (input?.topic?.id && input?.kind && Array.isArray(input.untrustedEvidence)) {
    const evidence=input.untrustedEvidence[0]
    const quote=evidence.text.includes(premise)?premise:evidence.text
    const referenceAnswer=quote.includes(answer)?answer:quote
    const stems=[
      '默认代理模式中，哪类方法调用能够触发拦截',
      '应沿着哪条调用路径排查事务切面是否参与',
      '对于没有经过代理的内部调用，哪条描述正确',
      '分辨直接调用和代理拦截时，应先核对哪项',
      '调用关系变化后，哪个代理模式约定依然成立',
    ]
    const number=++sequence
    return { questions:Array.from({length:input.count},(_,i)=>({
      stem:`${stems[i]}？（隔离测试场景 ${number}）`, evidenceId:evidence.id,
      definition:input.kind==='SHORT_ANSWER'?{
        options:[],correctIndex:null,referenceAnswer,quote,explanation:'对象内直接调用不会第二次穿过代理。',
        rubric:[{concept:'代理边界',accepted:['只经过代理','external calls']},{concept:'自调用',accepted:['绕过代理','self-invocation']}],
        commonErrors:['把自调用当作外部代理调用。'],
      }:{
        options:[{text:referenceAnswer,errorType:null},{text:'所有内部直接调用都会新开启事务。',errorType:'CONDITION_ERROR'},
          {text:'每一个线程都自动进入事务。',errorType:'CONCEPT_CONFUSION'},{text:'该规则只适用于其他版本。',errorType:'VERSION_ERROR'}],
        correctIndex:0,referenceAnswer,quote,explanation:'默认代理模式只拦截经过代理进入的方法调用。',
        rubric:[],commonErrors:[],
      },
    })) }
  }
  if (input?.approvedTeachingNotes) {
    const note=input.approvedTeachingNotes[0]
    return {selection:note?[{noteId:note.noteId,evidenceId:note.evidenceId,quote:input.untrustedEvidence.find(row=>row.id===note.evidenceId).text}]:[]}
  }
  if (Array.isArray(input?.rubric) && input.answer) {
    return {criteria:input.rubric.map((_,index)=>({index,status:'UNCERTAIN',quote:''})),factError:'UNCERTAIN',version:'UNCERTAIN'}
  }
}

export async function verifyAssessments({a,b,request,page,frontend,report,date,restart,live=false}) {
  const path='/assessments'
  const hub='/knowledge-hub'
  const suffix=randomUUID().slice(0,8)
  const makeSource=await request(a,hub+'/manage/sources','POST',{
    name:'Spring 事务代理验收 '+suffix,domain:'JAVA_BACKEND',softwareVersion:'6.2',
    url:`https://docs.spring.io/spring-framework/reference/6.2/c13-${suffix}/data-access/transaction/declarative/annotations.html`,
  })
  const stage=(text)=>request(a,hub+'/manage/sources/'+makeSource.id+'/candidate','POST',{
    title:'Spring Transaction Proxy 事务代理',domain:'JAVA_BACKEND',softwareVersion:'6.2',unitId:topic,text,
  })
  const publishSource=async (row)=>{
    for (const decision of ['APPROVE','PUBLISH']) await request(a,hub+'/manage/versions/'+row.id+'/decision','POST',
      {decision,reason:'隔离验收中的可信资料夹具'})
  }
  const original=await stage('# Spring transaction proxy\n\n'+sourceText)
  const generated=(kind='SINGLE_CHOICE',count=5)=>request(a,path+'/generate','POST',{
    unitId:topic,softwareVersion:'6.2',kind,difficulty:'BASIC',count,publicBank:true,
  })
  assert.equal((await generated()).status,'NO_EVIDENCE')
  await publishSource(original)
  const choices=await generated()
  if(live && choices.status!=='DRAFT') {
    report.push(`C-13 live candidate generation failed: ${choices.status} ${JSON.stringify(choices.trace)}`)
    assert.fail('Real provider candidates did not pass draft validation')
  }
  assert.equal(choices.status,'DRAFT')
  assert.equal(choices.questions.length,5)
  for(const row of choices.questions) {
    assert.equal(row.source.versionId,original.id)
    assert.equal(row.question.state,'DRAFT')
  }
  const decision=(actor,id,state,expected)=>request(actor,path+'/manage/questions/'+id+'/decision','POST',
    {state,rationale:'逐条复核版本、题干、唯一答案及引用'},expected)
  const publish=async (rows)=>{
    for(const {question:{versionId}} of rows) {
      for(const state of ['PENDING_REVIEW','APPROVED','PUBLISHED']) await decision(a,versionId,state)
    }
  }
  const first=choices.questions[0]
  await request(b,path+'/manage/questions/'+first.question.versionId,'GET',undefined,404)
  await decision(b,first.question.versionId,'PUBLISHED',404)
  await decision(a,first.question.versionId,'PUBLISHED',409)
  assert.equal((await request(b,path+'/questions?unitId='+topic)).length,0)
  await publish(choices.questions)

  const subject=await request(a,'/subjects','POST',{name:'Spring 事务 '+suffix,color:'#287769',sortOrder:0})
  await request(a,'/learning-paths/confirm','POST',{
    subjectId:subject.id,title:'Spring 事务评估 '+suffix,originalRequirement:'复习声明式事务',startDate:date(),dailyMinutes:60,
    items:[{sequenceNo:1,stageNo:1,stageTitle:'事务',topic:'Spring事务',learningObjective:'解释事务代理边界',
      learningMethod:['画出经过代理的调用路径'],completionCriteria:['说明自调用与外部调用的区别'],estimatedMinutes:30,suggestedDay:1}],
  })
  const start=(count=5)=>request(a,path+'/sessions','POST',{mode:'TOPIC',subjectId:subject.id,unitId:topic,softwareVersion:'6.2',count})
  const s=await start()
  await request(b,path+'/sessions/'+s.id,'GET',undefined,404)
  await request(b,path+'/sessions/'+s.id+'/submit','POST',undefined,404)
  let attempt=await request(a,path+'/sessions/'+s.id)
  assert.equal(attempt.items.length,5)
  assert.deepEqual(attempt.marks,[])
  assert.ok(attempt.items.every(row=>!('correctIndex' in row) && !('rubric' in row) && !('source' in row)))
  const save=(id,ordinal,selected)=>request(a,path+'/sessions/'+id+'/items/'+ordinal,'PUT',{answer:selected})
  const correctIndex=ordinal=>choices.questions.find(q=>q.question.versionId===attempt.items[ordinal-1].versionId).definition.correctIndex
  const wrongIndex=ordinal=>String((correctIndex(ordinal)+1)%attempt.items[ordinal-1].options.length)
  const wrongAnswer=wrongIndex(1)
  await save(s.id,1,wrongAnswer)
  if (restart) {
    await restart()
    assert.equal((await request(a,path+'/sessions/'+s.id)).items[0].answer,wrongAnswer)
  }
  await page.goto(frontend+'/assessments?session='+s.id)
  await page.getByRole('button',{name:'暂存答案',exact:true}).waitFor()
  await page.reload()
  await page.locator('.el-radio.is-checked').waitFor()
  const options=page.locator('input[type=radio]')
  assert.equal(await options.nth(Number(wrongAnswer)).isChecked(),true)
  for(const ordinal of [2,3]) await save(s.id,ordinal,wrongIndex(ordinal))
  const submit=()=>request(a,path+'/sessions/'+s.id+'/submit','POST')
  const [left,right]=await Promise.all([submit(),submit()])
  assert.deepEqual(left,right)
  assert.equal(left.total,5)
  assert.equal(left.scored,3)
  assert.equal(left.correct,0)
  assert.equal(left.marks[0].source.versionId,original.id)
  await request(a,path+'/sessions/'+s.id+'/items/1','PUT',{answer:'0'},409)
  await page.reload()
  await page.getByText('审核解析',{exact:true}).waitFor()
  await page.getByRole('tab',{name:'错题本',exact:true}).click()
  await page.getByRole('button',{name:'查看错题解析',exact:true}).first().waitFor()
  const weak=await request(a,path+'/performance?unitId='+topic+'&subjectId='+subject.id)
  assert.equal(weak.topics[0].level,'NEEDS_PRACTICE')
  assert.deepEqual(weak.topics[0].newQuestions,{total:3,correct:0})
  assert.equal(weak.topics[0].consecutiveErrors,3)
  report.push('C-13 five grounded drafts, reviewer-only publication, Chromium resume, concurrent submit and three distinct errors')

  const ask=(softwareVersion='6.2')=>request(a,hub+'/rag/explain','POST',{
    query:'Spring 事务 proxy self-invocation transaction',softwareVersion,style:'EXAMPLE',
  })
  const teaching=await ask()
  assert.equal(teaching.mode,'GROUNDED_EXPLANATION')
  assert.equal(teaching.lessons[0].exampleStatus,'ILLUSTRATIVE_UNVERIFIED')
  assert.equal(teaching.citations[0].evidence.versionId,original.id)
  assert.equal((await ask('7.0')).mode,'EVIDENCE_ONLY')
  const beforeCount=(await request(a,'/tasks?subjectId='+subject.id)).total
  const agent=await request(a,'/agent/sessions','POST')
  if(!live) {
    const reply=await request(a,'/agent/sessions/'+agent.id+'/messages','POST',{text:'c13-weak-topics'})
    const result=reply.messages.at(-1).tools[0]
    assert.equal(result.name,'list_weak_topics')
    assert.equal(result.data[0].consecutiveErrors,3)
  }
  const runPath='/agent/workflows/'
  const workflow=async(text)=>request(a,'/agent/sessions/'+agent.id+'/workflows','POST',{text})
  const auto=id=>request(a,runPath+id+'/auto-advance','POST')
  let assess=await workflow('我最近学了 Spring 事务，检测一下掌握情况，开始测评')
  assess=await auto(assess.id)
  assert.equal(assess.status,'WAITING_APPROVAL')
  let approval=assess.nodes.find(n=>n.id==='assessmentApproval').result
  await request(a,runPath+assess.id+'/decision','POST',{confirm:true,digest:approval.digest})
  assess=await auto(assess.id)
  assert.equal(assess.status,'SUCCEEDED')
  const practice=assess.nodes.find(n=>n.id==='assessmentApply').result
  assert.ok(practice.url.includes(practice.id))
  await request(a,path+'/sessions/'+practice.id+'/cancel','POST')

  let review=await workflow('Spring 事务有哪些薄弱点，帮我安排下周复习，每天30分钟')
  review=await auto(review.id)
  assert.equal(review.status,'WAITING_APPROVAL',JSON.stringify(review.nodes))
  approval=review.nodes.find(n=>n.id==='assessmentApproval').result
  assert.ok(approval.preview[0].candidate.reason.includes('连续错 3 题'))
  assert.equal((await request(a,'/tasks?subjectId='+subject.id)).total,beforeCount)
  await request(b,runPath+review.id+'/decision','POST',{confirm:true,digest:approval.digest},404)
  await request(a,runPath+review.id+'/decision','POST',{confirm:true,digest:approval.digest})
  review=await auto(review.id)
  assert.equal(review.status,'SUCCEEDED')
  const applied=review.nodes.find(n=>n.id==='assessmentApply').result
  const taskId=applied.taskIds[0]
  assert.equal((await request(a,'/tasks/'+taskId)).estimatedMinutes,30)
  await request(a,runPath+review.id+'/decision','POST',{confirm:true,digest:approval.digest})
  assert.equal((await request(a,'/tasks?subjectId='+subject.id)).total,beforeCount+1)
  report.push('C-13 evidence-grounded explanation and version refusal; Agent practice and review require approval and apply once')

  // A closed review task and an actual record are both required before comparing first attempts.
  const clock=new Intl.DateTimeFormat('sv-SE',{timeZone:'Asia/Shanghai',year:'numeric',month:'2-digit',day:'2-digit',
    hour:'2-digit',minute:'2-digit',second:'2-digit'})
  const now=Date.now()
  await request(a,'/records','POST',{
    subjectId:subject.id,taskId,startedAt:clock.format(new Date(now-900000)).replace(' ','T'),
    endedAt:clock.format(new Date(now-1000)).replace(' ','T'),
  })
  await request(a,'/tasks/'+taskId+'/status','PATCH',{status:'COMPLETED'})
  const newChoices=await generated()
  assert.equal(newChoices.status,'DRAFT')
  await publish(newChoices.questions)
  const next=await start(3)
  const nextAttempt=await request(a,path+'/sessions/'+next.id)
  for(const item of nextAttempt.items) {
    const gradeKey=newChoices.questions.find(q=>q.question.versionId===item.versionId)
    assert.ok(gradeKey)
    await save(next.id,item.ordinal,String(gradeKey.definition.correctIndex))
  }
  assert.equal((await request(a,path+'/sessions/'+next.id+'/submit','POST')).correct,3)
  const observed=await request(a,path+'/review-progress')
  assert.equal(observed.completed,1)
  const effect=observed.effects.find(row=>row.taskId===taskId && row.version==='6.2' && row.difficulty==='BASIC')
  assert.deepEqual(effect.before,{total:3,correct:0})
  assert.deepEqual(effect.after,{total:3,correct:3})
  assert.equal(effect.sufficient,true)
  report.push('C-13 review task linked to real record; first-attempt pre/post counts 0/3 → 3/3 on different questions')

  const short=await generated('SHORT_ANSWER',3)
  assert.equal(short.status,'DRAFT')
  await publish(short.questions)
  const mix=await start(3)
  const beforeWithdraw=await request(a,path+'/sessions/'+mix.id)
  const free=beforeWithdraw.items.find(q=>q.kind==='SHORT_ANSWER')
  assert.ok(free)
  await page.goto(frontend+'/assessments?session='+mix.id+'&ordinal='+free.ordinal)
  await page.getByRole('textbox',{name:'简答作答'}).fill('对象内直接调用未经过事务代理。')
  await page.getByRole('button',{name:'提交测评',exact:true}).click()
  await page.getByRole('button',{name:'提交答案',exact:true}).click()
  await page.getByText('审核解析',{exact:true}).waitFor()
  let shortResult=await request(a,path+'/sessions/'+mix.id)
  assert.equal(shortResult.needsReview,1)
  assert.equal(shortResult.scored,0)
  shortResult=await request(a,path+'/sessions/'+mix.id+'/items/'+free.ordinal+'/assistance','POST')
  assert.equal(shortResult.marks.find(q=>q.ordinal===free.ordinal).grade,'NEEDS_REVIEW')
  assert.equal(shortResult.scored,0)

  const secret=await request(a,hub+'/personal','POST',{title:'隔离笔记 '+suffix,domain:'JAVA_BACKEND',unitId:topic,
    softwareVersion:'6.2',text:'# Private\n\n'+sourceText})
  const draft={unitId:topic,kind:'SINGLE_CHOICE',difficulty:'BASIC',softwareVersion:'6.2',
    stem:'个人私有题 '+suffix+' 验证其访问权限？',definition:choices.questions[0].definition,
    sourceVersion:secret.id,sourceOrdinal:1,publicBank:false}
  await request(b,path+'/manage/questions','POST',draft,409)
  await request(a,path+'/manage/questions','POST',{...draft,publicBank:true},409)
  const own=await request(a,path+'/manage/questions','POST',draft)
  await publish([own])
  await request(b,path+'/manage/questions/'+own.question.versionId,'GET',undefined,404)
  assert.ok(!(await request(b,path+'/questions?unitId='+topic)).some(q=>q.versionId===own.question.versionId))
  const revision={...draft,stem:'审核修订后的代理调用题 '+suffix+'？',sourceVersion:original.id,sourceOrdinal:1,publicBank:true}
  const rev=await request(a,path+'/manage/questions/'+first.question.questionId+'/revision','POST',revision)
  await publish([rev])
  const archived=await request(a,path+'/sessions/'+s.id)
  assert.deepEqual(archived.marks,left.marks)
  assert.equal(archived.items[0].versionId,left.items[0].versionId)
  const pending=await request(b,path+'/sessions','POST',{mode:'TOPIC',unitId:topic,softwareVersion:'6.2',count:1})
  await request(b,path+'/sessions/'+pending.id+'/items/1','PUT',{answer:'0'})
  const replacement=await stage('# Spring transaction proxy\n\nThis replacement no longer documents proxy self-invocation.')
  await publishSource(replacement)
  const invalidated=await request(b,path+'/sessions/'+pending.id+'/submit','POST')
  assert.equal(invalidated.scored,0)
  assert.equal(invalidated.needsReview,1)
  assert.ok((await request(a,path+'/manage/questions')).some(q=>q.needsSourceReview))
  assert.ok(!(await request(b,path+'/questions?unitId='+topic)).some(q=>q.versionId===rev.question.versionId))
  const history=await request(a,path+'/sessions/'+s.id)
  assert.equal(history.marks[0].source.current,false)
  assert.equal(history.correct,left.correct)
  report.push('C-13 advisory-only short answers, private isolation, immutable history after question revision and source withdrawal')
  if(live) report.push(`C-13 real generation trace: ${JSON.stringify(choices.trace)}`)
}
