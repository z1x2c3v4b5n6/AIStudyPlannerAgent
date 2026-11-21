import assert from 'node:assert/strict'
import {randomUUID} from 'node:crypto'

export async function verifyEvidenceRag({a,b,account,request,page,frontend,report,providerTrace}) {
  const hub='/knowledge-hub'
  const key='transaction'+randomUUID().replaceAll('-','').slice(0,10)
  const title='Spring Boot '+key
  const entry = await request(a,hub+'/manage/sources','POST',{
    name:title,url:'https://docs.spring.io/spring-boot/3.5/reference/'+key+'.html',
    domain:'JAVA_BACKEND',softwareVersion:'3.5',
  })
  const stage = text => request(a,hub+'/manage/sources/'+entry.id+'/candidate','POST',{
    title,text,domain:'JAVA_BACKEND',softwareVersion:'3.5',
  })
  const manage = (row,decision) => request(a,hub+'/manage/versions/'+row.id+'/decision','POST',
    {decision,reason:'C11 deterministic evidence fixture'})
  const ask=(actor,query,options={},status=200)=>request(actor,hub+'/rag/answer','POST',
    {query,scope:'PUBLIC',...options},status)
  const path=()=>request(a,'/ai/learning-paths/generate','POST',
    {requirement:'学习 Spring Boot '+key, softwareVersion:'3.5',dailyMinutes:60})
  const v1=await stage('# Transaction options\n\n'+key+' requires a public method through the proxy.')
  await path()
  assert.ok(!providerTrace.some(item=>item==='c11-path:'+v1.id))
  const hidden=await ask(a,key,{softwareVersion:'3.5'})
  assert.equal(hidden.fallbackReason,'NO_EVIDENCE')
  await manage(v1,'APPROVE')
  await manage(v1,'PUBLISH')
  const valid=await ask(a,key,{softwareVersion:'3.5'})
  assert.equal(valid.retrievalMode,'GENERATED')
  assert.ok(valid.answer.includes('[E1]'))
  assert.ok(valid.answer.includes('requires a public method through the proxy'))
  assert.ok(!valid.answer.includes('请按照该版本的官方说明核对配置'))
  assert.equal(valid.citations[0].evidence.versionId,v1.id)
  assert.equal(valid.citations[0].evidence.chunkId,v1.id+':1')
  assert.match(valid.citations[0].quote,/Transaction options/)
  await ask(b,key,{conversationId:valid.conversationId},404)
  assert.equal((await ask(a,key,{conversationId:valid.conversationId})).citations[0].evidence.softwareVersion,'3.5')
  assert.equal((await ask(a,key,{softwareVersion:'4.0'})).fallbackReason,'NO_EVIDENCE')
  assert.equal((await ask(a,key+' latest',{softwareVersion:'3.5',allowOnline:true})).fallbackReason,'LATEST_UNVERIFIED')
  assert.equal((await ask(a,key+' unknowncitation',{softwareVersion:'3.5'})).fallbackReason,'INVALID_CITATION')
  await path()
  assert.ok(providerTrace.includes('c11-path:'+v1.id))
  report.push('C-11 citation, version and conversation validation plus published document in Learning Path prompt')

  const feedback={category:'UNSOLVED',environment:'Spring Boot 3.5',queryType:'CONFIGURATION',ordinal:1}
  const vote=(actor,method='POST',expected=200)=>request(actor,hub+'/versions/'+v1.id+'/feedback',method,feedback,expected)
  await vote(b)
  await vote(b,'POST',409)
  await vote(b,'PUT')
  await vote(await account())
  await vote(await account())
  const findCases=async()=>(await request(a,hub+'/manage/cases')).filter(row=>row.versionId===v1.id)
  let cases=await findCases()
  assert.equal(cases.length,1)
  assert.equal(cases[0].users,3)
  const casePath=hub+'/manage/cases/'+cases[0].id
  await request(b,casePath+'/recheck','POST',undefined,403)
  const checked=await request(a,casePath+'/recheck','POST')
  assert.equal(checked.state,'FAILED')
  assert.equal(checked.outcome,'KNOWLEDGE_SOURCE_UNAVAILABLE')
  assert.equal((await findCases()).length,1)

  const v2=await stage('# Transaction options\n\n'+key+' changes the recommended configuration.')
  await manage(v2,'APPROVE')
  await manage(v2,'PUBLISH')
  const updated=await ask(a,key,{softwareVersion:'3.5'})
  assert.equal(updated.citations[0].evidence.versionId,v2.id)
  const evaluation={query:key,softwareVersion:'3.5',expectedTerm:'public method'}
  const after=await request(a,casePath+'/evaluate','POST',evaluation)
  assert.equal(after.previous.hit,true)
  assert.equal(after.current.hit,false)
  assert.equal(after.regression,true)
  const fixed=await request(a,casePath+'/evaluate','POST',{...evaluation,expectedTerm:'recommended configuration'})
  assert.equal(fixed.previous.hit,false)
  assert.equal(fixed.current.hit,true)
  await path()
  assert.ok(providerTrace.includes('c11-path:'+v2.id))
  await manage(v1,'ROLLBACK')
  await request(a,hub+'/versions/'+v2.id,'GET',undefined,404)
  const recovered=await request(a,casePath+'/evaluate','POST',evaluation)
  assert.equal(recovered.current.hit,true)
  assert.equal(recovered.regression,false)
  await path()
  assert.equal(providerTrace.filter(item=>item==='c11-path:'+v1.id).length,2)
  report.push('C-11 three independent feedback votes open one durable case; regression and rollback confirmed')

  const newerLine=await request(a,hub+'/manage/sources','POST',{
    name:title+' v4',url:'https://docs.spring.io/spring-boot/4.0/reference/'+key+'.html',
    domain:'JAVA_BACKEND',softwareVersion:'4.0',
  })
  const v4=await request(a,hub+'/manage/sources/'+newerLine.id+'/candidate','POST',{
    title:title+' v4',text:'# Updated options\n\n'+key+' keeps a separate software-version record.',
    domain:'JAVA_BACKEND',softwareVersion:'4.0',
  })
  await manage(v4,'APPROVE')
  await manage(v4,'PUBLISH')
  assert.equal((await ask(a,key,{softwareVersion:'4.0'})).citations[0].evidence.versionId,v4.id)
  assert.equal((await ask(a,key,{softwareVersion:'3.5'})).citations[0].evidence.versionId,v1.id)
  assert.equal((await ask(a,key)).fallbackReason,'AMBIGUOUS_VERSION')

  const examples=[
    {label:'Java monitor',keyword:'monitorexample'+key,url:'https://docs.oracle.com/en/java/javase/17/docs/'+key+'.html',
      domain:'JAVA_BACKEND',version:'17',sentence:'Each object has an associated monitor.'},
    {label:'Spring AI callbacks',keyword:'callbackexample'+key,url:'https://docs.spring.io/spring-ai/1.1/reference/'+key+'.html',
      domain:'AI_APPLICATION',version:'1.1',sentence:'Register callbacks before calling the model.'},
    {label:'RAG grounding',keyword:'retrievalexample'+key,url:'https://docs.spring.io/spring-ai/1.1/reference/'+key+'-rag.html',
      domain:'AI_APPLICATION',version:'1.1',sentence:'Retrieval supplies evidence to ground the answer.'},
  ]
  for(const example of examples){
    const exampleSource=await request(a,hub+'/manage/sources','POST',{name:example.label,url:example.url,domain:example.domain,
      softwareVersion:example.version})
    const item=await request(a,hub+'/manage/sources/'+exampleSource.id+'/candidate','POST',{title:example.label+' '+example.keyword,
      text:'# Details\n\n'+example.keyword+' '+example.sentence,domain:example.domain,softwareVersion:example.version})
    await manage(item,'APPROVE')
    await manage(item,'PUBLISH')
    const answer=await ask(a,example.keyword,{softwareVersion:example.version})
    assert.equal(answer.retrievalMode,'GENERATED',example.label)
    assert.equal(answer.citations[0].evidence.versionId,item.id)
  }
  report.push('C-11 Spring Boot, Java, Spring AI and RAG cited Q&A examples')

  const privateTerm='private'+key
  const personal=await request(a,hub+'/personal','POST',{title:'Personal '+privateTerm,domain:'JAVA_BACKEND',
    text:'# Private facts\n\n'+privateTerm+' is an owner-only fixture.'})
  assert.equal((await ask(a,privateTerm,{scope:'PRIVATE'})).citations[0].evidence.versionId,personal.id)
  assert.equal((await ask(b,privateTerm,{scope:'PRIVATE',allowPrivateModel:true})).fallbackReason,'NO_EVIDENCE')
  await request(b,hub+'/versions/'+personal.id,'GET',undefined,404)
  await page.goto(frontend+'/knowledge')
  await page.getByRole('tab',{name:'技术问答'}).click()
  await page.getByRole('textbox',{name:'技术问题'}).fill(key)
  await page.getByRole('textbox',{name:'问答软件版本'}).fill('3.5')
  await page.getByRole('button',{name:'提问',exact:true}).click()
  await page.getByRole('link',{name:'原始来源'}).first().waitFor()
  await page.getByRole('button',{name:'查看引用片段'}).first().click()
  await page.getByRole('dialog',{name:'引用片段'}).getByText(/public method/).waitFor()
  report.push('C-11 private RAG isolation and Chromium answer-to-citation interaction')
}

export async function verifyLiveRag({a,request,report,providerTrace,extended=false}) {
  const root='/knowledge-hub'
  const unique='proxyfixture'+randomUUID().replaceAll('-','').slice(0,10)
  const source=await request(a,root+'/manage/sources','POST',{name:'Synthetic transaction evidence',domain:'JAVA_BACKEND',
    url:'https://docs.spring.io/spring-boot/3.5/reference/'+unique+'.html',softwareVersion:'3.5'})
  const version=await request(a,root+'/manage/sources/'+source.id+'/candidate','POST',{title:'Transaction proxy '+unique,
    text:'# Synthetic fixture\n\n'+unique+' applies when a call goes through the transaction proxy.',
    domain:'JAVA_BACKEND',softwareVersion:'3.5'})
  for(const decision of ['APPROVE','PUBLISH'])
    await request(a,root+'/manage/versions/'+version.id+'/decision','POST',{decision,reason:'isolated real-provider test fixture'})
  const answer=await request(a,root+'/rag/answer','POST',{query:unique+' 的事务代理什么时候生效？',softwareVersion:'3.5',scope:'PUBLIC'})
  assert.equal(answer.retrievalMode,'GENERATED',JSON.stringify({fallbackReason:answer.fallbackReason}))
  assert.ok(answer.citations.some(citation=>citation.evidence.versionId===version.id))
  console.log(JSON.stringify({probe:'c11-real-deepseek',model:answer.modelInformation,tokens:answer.tokenUsage,
    latency:answer.latencyInformation,citations:answer.citations.length}))
  if (extended) {
    const path=await request(a,'/ai/learning-paths/generate','POST',{
      requirement:'学习 Spring Boot 3.5 事务代理与 '+unique, softwareVersion:'3.5', dailyMinutes:60,
    })
    assert.ok(path.stages.length>0)
    assert.ok(providerTrace.some(event=>event.category==='LEARNING_PATH_EVIDENCE'&&event.versionId===version.id))
    const malformed=await request(a,root+'/rag/answer','POST',{
      query:unique+' c121-invalid-model',softwareVersion:'3.5',scope:'PUBLIC',
    })
    assert.equal(malformed.fallbackReason,'INVALID_CITATION')
    report.push('C-12.1 live Learning Path consumed published evidence; local malformed-response check downgraded')
  }
  report.push('C-11 live DeepSeek: cited answer verified against isolated synthetic material')
}
