<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { evidenceRag as api, type ReviewCase, type ReviewRun } from '../../api/evidenceRag'
const emit=defineEmits<{ inspect: [id: string] }>()
const rows=ref<ReviewCase[]>([])
const selected=ref<ReviewCase>()
const runs=ref<ReviewRun[]>([])
const query=ref('')
const version=ref('')
const term=ref('')
const outcome=ref('INSUFFICIENT_EVIDENCE')
const busy=ref(false)
const error=ref('')
const labels: Record<string,string> = { OPEN:'待复核', CHECKING:'查询中', CHECKED:'已检查', CANDIDATE_READY:'候选已生成',
  FAILED:'检查失败', CLOSED:'已结案', NO_CONFIRMED_CHANGE:'未确认内容失效', SOURCE_CHANGED:'官方来源内容变化' }

async function load(){rows.value=(await api.cases()).data.data}
onMounted(() => load().catch(() => {error.value='无法读取复核任务。'}))
async function perform(action: () => Promise<void>) {
  if(busy.value) return
  busy.value=true
  error.value=''
  try {await action()}
  catch {error.value='操作失败，请检查状态后重试。'}
  finally {busy.value=false}
}
async function select(row: ReviewCase) {
  selected.value=row
  runs.value=(await api.runs(row.id)).data.data
}
function act(kind: 'check'|'close'|'evaluate') {
  const current=selected.value
  if(!current) return
  return perform(async () => {
    if(kind==='check') selected.value=(await api.recheck(current.id)).data.data
    if(kind==='close') selected.value=(await api.close(current.id,outcome.value)).data.data
    if(kind==='evaluate') await api.evaluate(current.id,{query:query.value,softwareVersion:version.value,expectedTerm:term.value})
    runs.value=(await api.runs(current.id)).data.data
    await load()
  })
}
</script>

<template>
  <section>
    <el-alert v-if="error" type="error" :title="error" :closable="false"/>
    <el-empty v-if="!rows.length" description="暂无已触发的复核任务"/>
    <div class="options">
      <el-button v-for="row in rows" :key="row.id" @click="perform(() => select(row))">
        {{ row.environment }} · {{ row.queryType }} · {{ row.users }} 个账号 · {{ labels[row.state]||row.state }}
      </el-button>
    </div>
    <template v-if="selected">
      <h3>{{ selected.environment }} · {{ labels[selected.state]||selected.state }}</h3>
      <p v-if="selected.outcome">{{ labels[selected.outcome]||selected.outcome }}</p>
      <div class="options">
        <el-button @click="emit('inspect',selected.versionId)">查看原版本</el-button>
        <el-button v-if="selected.candidateId" @click="emit('inspect',selected.candidateId)">查看候选与差异</el-button>
        <el-button v-if="['OPEN','FAILED','CHECKING'].includes(selected.state)" :loading="busy" @click="act('check')">重新检查官方原文</el-button>
      </div>
      <div class="options" v-if="!['CHECKING','CLOSED'].includes(selected.state)">
        <el-select v-model="outcome" aria-label="复核结论">
          <el-option label="确认过时" value="OUTDATED"/><el-option label="环境版本不同" value="VERSION_MISMATCH"/>
          <el-option label="内容不完整" value="INCOMPLETE"/><el-option label="检索选错证据" value="WRONG_EVIDENCE"/>
          <el-option label="未解决问题" value="DID_NOT_SOLVE"/><el-option label="证据不足" value="INSUFFICIENT_EVIDENCE"/>
        </el-select>
        <el-button :loading="busy" @click="act('close')">记录结论并结案</el-button>
      </div>
      <h3>版本回归</h3>
      <div class="options">
        <el-input v-model="query" maxlength="300" aria-label="回归查询" placeholder="回归查询"/>
        <el-input v-model="version" maxlength="80" aria-label="目标版本" placeholder="目标版本"/>
        <el-input v-model="term" maxlength="100" aria-label="预期证据关键词" placeholder="预期证据关键词"/>
        <el-button :disabled="!query.trim()||!version.trim()||!term.trim()" :loading="busy" @click="act('evaluate')">运行回归</el-button>
      </div>
      <article v-for="run in runs" :key="run.id">
        <p>{{ run.query }} · {{ run.time }} · {{ run.report.regression?'发现退化':'本次未检测到退化' }}</p>
        <p>旧版命中 {{ run.report.previous.hit?'是':'否' }} · 当前命中 {{ run.report.current.hit?'是':'否' }}
          · 不相关 {{ run.report.current.unrelated }} · 错版本 {{ run.report.current.wrongVersion }}
          · 可追踪 {{ run.report.current.traceable }}</p>
      </article>
    </template>
  </section>
</template>

<style scoped>
.options { display:flex; flex-wrap:wrap; gap:10px; margin:12px 0; align-items:center; }
.options :deep(.el-input),.options :deep(.el-select) { width:min(100%,14rem); }
</style>
