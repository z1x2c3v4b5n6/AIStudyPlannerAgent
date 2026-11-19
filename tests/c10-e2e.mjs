import assert from 'node:assert/strict'
import { randomUUID } from 'node:crypto'

export async function verifyKnowledgeHub({ a, b, account, request, page, frontend, report }) {
  const hub = '/knowledge-hub'
  const manage = hub + '/manage'
  const caps = await request(a, hub + '/capabilities')
  assert.equal(caps.reviewer, true)
  assert.equal(caps.onlineEnabled, false)
  assert.equal((await request(b, hub + '/capabilities')).reviewer, false)
  await request(b, manage + '/sources', 'GET', undefined, 403)
  const denied = {name:'Internal', url:'https://127.0.0.1/spring-boot/a',domain:'JAVA_BACKEND'}
  await request(a, manage + '/sources', 'POST', denied, 400)

  const stable = await request(b, hub + '/search','POST',{query:'Spring Boot 自动配置',scope:'PUBLIC',online:true})
  assert.ok(stable.structured.length > 0)
  assert.equal(stable.onlineStatus, 'LOCAL_ONLY')
  const live = await request(b, hub + '/search','POST',{query:'Spring Boot 最新',scope:'PUBLIC',online:true})
  assert.equal(live.onlineStatus, 'DISABLED')
  assert.equal(live.online.length, 0)
  report.push('C-10 local-first: existing structured knowledge and explicit disabled-online status')

  const suffix = randomUUID().replaceAll('-','').slice(0,12)
  const key = 'fixturehub' + suffix
  const title = 'Spring Boot ' + key
  const source = await request(a, manage + '/sources','POST',{
    name:title,url:'https://docs.spring.io/spring-boot/reference/' + key + '.html',
    domain:'JAVA_BACKEND',softwareVersion:'3.5',
  })
  const candidatePath = manage + '/sources/' + source.id + '/candidate'
  const payload = {title,domain:'JAVA_BACKEND',softwareVersion:'3.5'}
  const stage = body => request(a,candidatePath,'POST',{...payload,text:'# Options\n\n'+body})
  const search = (user, query, scope='PUBLIC', softwareVersion) =>
    request(user,hub+'/search','POST',{query,scope,softwareVersion})
  const decision = (who, item, action, status=200) => request(who,manage+'/versions/'+item.id+'/decision','POST',
    {decision:action,reason:'integration fixture provenance verified'},status)
  const old = await stage(key + ' previousoption')
  assert.equal(old.status,'PENDING_REVIEW')
  assert.equal((await stage(key + ' previousoption')).id, old.id)
  assert.equal((await search(b,key)).published.length,0)
  await request(b, hub+'/versions/'+old.id, 'GET',undefined,404)
  await decision(b,old,'APPROVE',403)
  await decision(a,old,'PUBLISH',409)
  await decision(a,old,'APPROVE')
  assert.equal((await search(b,key)).published.length,0)
  await decision(a,old,'PUBLISH')
  const first = (await search(b,key)).published[0]
  assert.equal(first.versionId,old.id)
  assert.equal(first.url,source.url)
  assert.equal(first.ordinal,1)
  assert.equal(first.line,1)
  assert.equal((await search(b,key,'PUBLIC','4.0')).published.length,0)
  report.push('C-10 publication: verified roles, unpublished exclusion, cited version and exact version filter')

  const vote = (actor, status=200) => request(actor,hub+'/versions/'+old.id+'/feedback','POST',{
    category:'INCOMPATIBLE',environment:'Spring Boot 3.5',queryType:'CONFIGURATION',ordinal:1,
  }, status)
  await vote(b)
  await vote(b,409)
  await vote(await account())
  assert.equal((await request(a,manage+'/signals')).length,0)
  await vote(await account())
  const signals = await request(a,manage+'/signals')
  assert.ok(signals.some(item=>item.versionId===old.id && item.users===3))
  assert.equal((await search(b,key)).published[0].versionId,old.id)
  report.push('C-10 feedback: three independent accounts trigger review and never change publication')

  const newer=await stage(key + ' replacementoption')
  const difference=await request(a,manage+'/versions/'+newer.id+'/difference')
  assert.equal(difference.activeVersion, old.id)
  assert.ok(difference.added[0].excerpt.includes('replacementoption'))
  assert.ok(difference.removed[0].excerpt.includes('previousoption'))
  assert.equal((await search(b,key)).published[0].versionId,old.id)
  await decision(a,newer,'APPROVE')
  await decision(a,newer,'PUBLISH')
  assert.equal((await search(b,key)).published[0].versionId,newer.id)
  await request(b,hub+'/versions/'+old.id,'GET',undefined,404)
  await decision(a,old,'ROLLBACK')
  assert.equal((await search(b,key)).published[0].versionId,old.id)
  const history=await request(a,manage+'/documents/'+old.documentId+'/history')
  assert.ok(history.some(item=>item.operation==='ROLLBACK' && item.previous===newer.id))
  report.push('C-10 version change: evidence diff, atomic publication, rollback and audit were verified')

  const privateTerm='personalhub'+suffix
  const personal=await request(a,hub+'/personal','POST',
    {title:'Private '+suffix,domain:'JAVA_BACKEND',text:'# Secret note\n\n'+privateTerm})
  assert.equal((await search(a, privateTerm,'PRIVATE')).published[0].versionId,personal.id)
  assert.equal((await search(a, privateTerm,'PUBLIC')).published.length,0)
  assert.equal((await search(b, privateTerm,'BOTH')).published.length,0)
  await request(b,hub+'/versions/'+personal.id,'GET',undefined,404)
  await request(b,hub+'/personal/'+personal.documentId,'DELETE',undefined,404)
  await request(a,manage+'/versions/'+personal.id,'GET',undefined,404)

  await page.goto(frontend+'/knowledge')
  await page.getByRole('heading',{name:'知识中心'}).waitFor()
  await page.getByRole('textbox',{name:'知识查询'}).fill(key)
  await page.getByRole('button',{name:'检索知识'}).click()
  await page.getByRole('heading',{name:title,exact:true}).waitFor()
  await page.getByText(key+' previousoption',{exact:false}).waitFor()
  await page.getByRole('tab',{name:'个人资料'}).click()
  await page.getByText('Private '+suffix,{exact:true}).waitFor()
  await request(a,hub+'/personal/'+personal.documentId,'DELETE')
  assert.equal((await search(a, privateTerm,'PRIVATE')).published.length,0)
  await request(a,hub+'/versions/'+personal.id,'GET',undefined,404)
  report.push('C-10 privacy and Chromium: scoped search, direct access denial, visible evidence and physical chunk deletion')
}
