import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import { randomUUID } from 'node:crypto'
import { mkdtempSync, readFileSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { dirname, join, resolve } from 'node:path'

function databaseHarness(java) {
  const jar=java==='java'?'jar':join(dirname(java),'jar'+(process.platform==='win32'?'.exe':''))
  const artifact=resolve('backend/target/ai-study-planner-agent-0.0.1-SNAPSHOT.jar')
  const connector=execFileSync(jar,['tf',artifact],{encoding:'utf8'}).split(/\r?\n/)
    .find(line=>/^BOOT-INF\/lib\/mysql-connector-j-[a-zA-Z0-9.-]+\.jar$/.test(line))
  assert.ok(connector,'Connector/J must be included in the packaged backend')
  const scratch=mkdtempSync(join(tmpdir(),'c12-jdbc-'))
  try {execFileSync(jar,['xf',artifact,connector],{cwd:scratch})}
  catch(error) {rmSync(scratch,{recursive:true,force:true});throw error}
  const tool=resolve('tests/C12DatabaseFault.java')
  return {
    run: (operation,id,document) => execFileSync(java,['-cp',join(scratch,connector),tool,operation,id,
      ...(document?[document]:[])],{timeout:12000,encoding:'utf8'}).trim(),
    close: () => rmSync(scratch,{recursive:true,force:true}),
  }
}

function snapshot(pid) {
  if(process.platform!=='linux') return null
  try {
    const status=readFileSync('/proc/'+pid+'/status','utf8')
    const stat=readFileSync('/proc/'+pid+'/stat','utf8').split(') ').at(-1).split(' ')
    return {rssKb:Number(status.match(/^VmRSS:\s+(\d+)/m)?.[1] ?? 0), cpuTicks:Number(stat[11])+Number(stat[12])}
  } catch { return null }
}

function measure(values) {
  const sorted=[...values].sort((a,b)=>a-b)
  const quantile=q=>sorted.length?sorted[Math.ceil(sorted.length*q)-1]:null
  return { p50:quantile(.50), p95:quantile(.95) }
}

export async function verifyC12({a,b,request,report,java,backendPid,gate}) {
  const hub='/knowledge-hub'
  const unique='c12publication'+randomUUID().replaceAll('-','').slice(0,12)
  const source=await request(a,hub+'/manage/sources','POST',{name:unique,domain:'JAVA_BACKEND',
    softwareVersion:'3.5',url:'https://docs.spring.io/spring-boot/3.5/reference/'+unique+'.html'})
  const stage=body=>request(a,hub+'/manage/sources/'+source.id+'/candidate','POST',{
    title:'Publication '+unique,text:'# Publication version\n\n'+unique+' '+body,domain:'JAVA_BACKEND',softwareVersion:'3.5',
  })
  const decide=(version,decision,status=200)=>request(a,hub+'/manage/versions/'+version.id+'/decision','POST',
    {decision,reason:'C12 isolated transaction acceptance'},status)
  const first=await stage('states that original service contracts remain available.')
  await decide(first,'APPROVE')
  await decide(first,'PUBLISH')
  const second=await stage('updates the service contract after review.')
  await decide(second,'APPROVE')
  const trigger=randomUUID()
  const mysql=databaseHarness(java)
  const search=(account,query=unique)=>request(account,hub+'/search','POST',{query,scope:'PUBLIC',softwareVersion:'3.5'})
  try {
    mysql.run('install',trigger,first.documentId)
    try {
      await decide(second,'PUBLISH',500)
      assert.equal((await request(a,hub+'/manage/versions/'+first.id)).version.status,'PUBLISHED')
      assert.equal((await request(a,hub+'/manage/versions/'+second.id)).version.status,'APPROVED')
      assert.equal((await search(b)).published[0]?.versionId,first.id)
      const history=await request(a,hub+'/manage/documents/'+first.documentId+'/history')
      assert.equal(history.filter(row=>row.operation==='PUBLISH'&&row.versionId===second.id).length,0)
    } finally {mysql.run('remove',trigger)}
    await decide(second,'PUBLISH')
    assert.equal((await search(b)).published[0]?.versionId,second.id)
    report.push('C-12 MySQL rejects active-version update and rolls back status; publication retries after the fault is cleared')

    const asking=request(b,hub+'/rag/answer','POST',{query:unique+' c12pause',scope:'PUBLIC',softwareVersion:'3.5'})
    try {
      await gate.wait()
      await decide(first,'ROLLBACK')
    } finally {gate.release()}
    const revoked=await asking
    assert.equal(revoked.fallbackReason,'EVIDENCE_CHANGED')
    assert.ok(!revoked.citations.some(item=>item.evidence.versionId===second.id))
    const malformed=await request(b,hub+'/rag/answer','POST',{query:unique+' c12invalidjson',scope:'PUBLIC',softwareVersion:'3.5'})
    assert.equal(malformed.fallbackReason,'INVALID_CITATION')
    await request(b,hub+'/personal','POST',{title:'C12 too large',text:'x'.repeat(100001),domain:'JAVA_BACKEND'},400)
    report.push('C-12 blocked model call revalidates evidence after real rollback; oversized personal upload rejected')

    const before=snapshot(backendPid)
    const connectionsBefore=Number(mysql.run('connections',trigger))
    const readings=[]
    for (let batch=0;batch<6;batch++) {
      readings.push(...await Promise.all(Array.from({length:4},async()=>{
        const started=performance.now()
        try {
          const answer=await request(b,hub+'/rag/answer','POST',{
            query:unique+' c12measure',scope:'PUBLIC',softwareVersion:'3.5',
          })
          return {ok:answer.retrievalMode==='GENERATED',http:performance.now()-started,...answer.latencyInformation}
        } catch {return {ok:false,http:performance.now()-started}}
      })))
    }
    const good=readings.filter(item=>item.ok)
    const after=snapshot(backendPid)
    const connectionsAfter=Number(mysql.run('connections',trigger))
    console.log(JSON.stringify({dataset:'c12-small-concurrency-v1',runs:readings.length,concurrency:4,
      errors:readings.length-good.length,endpointMs:measure(readings.map(row=>row.http)),
      retrievalMs:measure(good.map(row=>row.retrievalMs)),modelFixtureMs:measure(good.map(row=>row.modelMs)),
      onlineMs:null,backendProcess:{before,after},mysqlServerConnections:{before:connectionsBefore,after:connectionsAfter}}))
    assert.equal(good.length,readings.length)
    report.push('C-12 small concurrency probe with stage timings and disposable database telemetry')
  } finally {mysql.close()}
}
