<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { learningPathApi } from '../../api/learningPath'
import { subjectApi } from '../../api/subject'
import type { Subject } from '../../types/subject'
import type { LearningPathConfirmRequest, LearningPathDetail, LearningPathDraft, LearningPathItem, LearningPathListItem } from '../../types/learningPath'
import { inclusiveBusinessDays, shanghaiDate } from '../../utils/businessTime'
import { exceedsLearningPathDuration, LEARNING_PATH_DURATION_MESSAGE, maxLearningPathTargetDate } from '../../utils/learningPathPeriod.js'

const examples=['我Java基础一般，希望用10天系统复习Java基础，为Java后端面试做准备，每天2小时。','我想用7天补齐Python基础，每天学习90分钟，节奏不要太紧。','为数据库面试设计8天复习路线，重点是MySQL索引和SQL。']
const subjects=ref<Subject[]>([]), paths=ref<LearningPathListItem[]>([]), total=ref(0), page=ref(1)
const generating=ref(false), confirming=ref(false), loading=ref(false), draft=ref<LearningPathDraft|null>(null)
const generatorExpanded=ref(true), generationError=ref<string|null>(null)
const detail=ref<LearningPathDetail|null>(null), detailVisible=ref(false), createTaskId=ref<number|null>(null)
const activeStages=ref<string[]>([]), selectedItem=ref<LearningPathItem|null>(null), itemDetailVisible=ref(false)
const activePathTab=ref<'paths'|'generate'>('paths')
const form=reactive({requirement:'',subjectId:null as number|null,currentLevel:'',targetLevel:'',dailyMinutes:120,startDate:shanghaiDate(),targetDate:'',preferences:''})
const draftItems=computed(()=>draft.value?.stages.flatMap(stage=>stage.items.map(item=>({...item,stageNo:stage.stageNo,stageTitle:stage.title,stageDescription:stage.description})))||[])
const draftDurationDays=computed(()=>{
  if(!draft.value)return 0
  if(draft.value.targetDate)return inclusiveBusinessDays(draft.value.startDate,draft.value.targetDate)
  return Math.max(1,...draftItems.value.map(item=>item.suggestedDay||1))
})
const draftTotalMinutes=computed(()=>draftItems.value.reduce((total,item)=>total+item.estimatedMinutes,0))

async function load(){loading.value=true;try{const data=(await learningPathApi.list(page.value,10)).data.data;paths.value=data.list;total.value=data.total}finally{loading.value=false}}
async function generate(){if(!form.requirement.trim())return ElMessage.warning('请先描述长期学习目标');if(form.startDate<shanghaiDate())return ElMessage.warning('开始日期不能早于今天');if(form.targetDate&&form.startDate>form.targetDate)return ElMessage.warning('目标日期不能早于开始日期');if(exceedsLearningPathDuration(form.startDate,form.targetDate||null))return ElMessage.warning(LEARNING_PATH_DURATION_MESSAGE);if(form.dailyMinutes<15||form.dailyMinutes>720)return ElMessage.warning('每日学习时间必须在15至720分钟之间');generating.value=true;generationError.value=null;try{draft.value=(await learningPathApi.generate({...form,targetDate:form.targetDate||null})).data.data;generatorExpanded.value=false}catch{generationError.value='AI 返回内容不完整或暂时不可用，请重新生成。'}finally{generating.value=false}}
function targetDateDisabled(date:Date){
  if(!form.startDate)return false
  const value=`${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}-${String(date.getDate()).padStart(2,'0')}`
  const maximum=maxLearningPathTargetDate(form.startDate)
  return value<form.startDate||Boolean(maximum&&value>maximum)
}
function removeItem(stageIndex:number,itemIndex:number){if(!draft.value)return;draft.value.stages[stageIndex].items.splice(itemIndex,1);if(!draft.value.stages[stageIndex].items.length)draft.value.stages.splice(stageIndex,1);renumber()}
function move(stageIndex:number,itemIndex:number,offset:number){if(!draft.value)return;const positions=draft.value.stages.flatMap((stage,si)=>stage.items.map((_item,ii)=>({stage,si,ii})));const current=positions.findIndex(position=>position.si===stageIndex&&position.ii===itemIndex),target=current+offset;if(current<0||target<0||target>=positions.length)return;const from=positions[current],to=positions[target];[from.stage.items[from.ii],to.stage.items[to.ii]]=[to.stage.items[to.ii],from.stage.items[from.ii]];renumber()}
function renumber(){let sequence=1;draft.value?.stages.forEach((stage,si)=>{stage.stageNo=si+1;stage.items.forEach(item=>item.sequenceNo=sequence++)})}
function lines(value:string){return value.split('\n').map(line=>line.trim()).filter(Boolean)}
function updateMethods(item:LearningPathDraft['stages'][number]['items'][number],value:string){item.learningMethod=lines(value)}
function updateCriteria(item:LearningPathDraft['stages'][number]['items'][number],value:string){item.completionCriteria=lines(value)}
async function confirm(){if(!draft.value||!draftItems.value.length)return ElMessage.warning('路径至少保留一个知识点');confirming.value=true;try{const d=draft.value;const payload:LearningPathConfirmRequest={subjectId:d.subjectId,title:d.title,summary:d.summary,originalRequirement:d.originalRequirement,currentLevel:d.currentLevel,targetLevel:d.targetLevel,preferences:d.preferences,startDate:d.startDate,targetDate:d.targetDate,dailyMinutes:d.dailyMinutes,items:draftItems.value};const saved=(await learningPathApi.confirm(payload)).data.data;draft.value=null;generatorExpanded.value=true;generationError.value=null;activePathTab.value='paths';ElMessage.success('学习路径已确认');await load();openDetail(saved)}catch{/* 全局 HTTP 拦截器负责提示；失败时保留当前草案 */}finally{confirming.value=false}}
async function open(row:LearningPathListItem){openDetail((await learningPathApi.get(row.id)).data.data)}
function openDetail(value:LearningPathDetail){detail.value=value;activeStages.value=[];detailVisible.value=true}
function openItemDetail(item:LearningPathItem){selectedItem.value=item;itemDetailVisible.value=true}
async function createTask(itemId:number){if(!detail.value||createTaskId.value!==null)return;try{await ElMessageBox.confirm('将按该节点的主题、目标、预计时长和建议日期创建真实待办任务。','创建学习任务');createTaskId.value=itemId;const result=(await learningPathApi.createTask(detail.value.id,itemId)).data.data;ElMessage.success(result.message);detail.value=(await learningPathApi.get(detail.value.id)).data.data;selectedItem.value=detail.value.items.find(item=>item.id===itemId)||null;await load()}catch(error){if(error!=='cancel'&&error!=='close'){/* HTTP拦截器已显示业务错误 */}}finally{createTaskId.value=null}}
async function changeSelectedItemStatus(status:'PENDING'|'SKIPPED'){if(!detail.value||!selectedItem.value)return;detail.value=(await learningPathApi.changeItemStatus(detail.value.id,selectedItem.value.id,status)).data.data;selectedItem.value=detail.value.items.find(item=>item.id===selectedItem.value?.id)||null;await load()}
function statusLabel(status:string){return {ACTIVE:'进行中',PAUSED:'已暂停',COMPLETED:'已完成',CANCELLED:'已取消',PENDING:'待学习',SKIPPED:'已跳过'}[status]||status}
onMounted(async()=>{subjects.value=(await subjectApi.list()).data.data;await load()})
</script>

<template>
  <section class="page-section learning-path-page">
    <div class="page-heading path-page-heading">
      <div>
        <div class="path-eyebrow"><span></span> AI LEARNING ENGINE</div>
        <h1>学习路径</h1>
        <p>把长期目标拆成由浅入深的阶段、知识点、学习方法和完成标准。</p>
      </div>
      <div class="path-hero-visual" aria-hidden="true">
        <span class="path-orbit path-orbit-one"></span>
        <span class="path-orbit path-orbit-two"></span>
        <span class="path-core">AI</span>
        <i></i><i></i><i></i>
      </div>
    </div>
    <el-tabs v-model="activePathTab" class="plan-workspace-tabs path-workspace-tabs">
      <el-tab-pane label="我的学习路径" name="paths">
        <div class="history-section-heading path-list-heading"><div><div class="path-section-kicker">ACTIVE ROADMAPS</div><h2>我的学习路径</h2></div><p>节点转为真实任务后，可进入现有每日学习计划。</p></div>
        <div v-loading="loading" class="learning-path-list"><el-empty v-if="!paths.length" description="还没有学习路径"><el-button type="primary" @click="activePathTab='generate'">AI生成学习路径</el-button></el-empty><el-card v-for="path in paths" :key="path.id" shadow="hover" class="learning-path-list-card" @click="open(path)"><div class="path-list-main"><div class="path-list-icon"><span>{{ path.title.slice(0,1) }}</span></div><div><div class="path-list-label">{{ path.subjectName||'UNASSIGNED SUBJECT' }}</div><h3>{{ path.title }}</h3><p>{{ path.stageCount }} 个阶段 · {{ path.totalItems }} 个知识节点</p><small>{{ path.startDate }} → {{ path.targetDate||'长期进阶' }}</small></div></div><div class="path-list-progress"><div><el-tag effect="dark">{{ statusLabel(path.status) }}</el-tag><b>{{ path.progress }}%</b></div><el-progress :percentage="path.progress" :show-text="false" :stroke-width="7" /><small>已完成 {{ path.completedItems }} / {{ path.totalItems }}</small></div></el-card></div>
        <el-pagination v-if="total>10" v-model:current-page="page" :total="total" :page-size="10" @change="load" />
      </el-tab-pane>
      <el-tab-pane label="AI生成学习路径" name="generate">
    <div v-if="generationError" class="path-generate-error" role="alert">
      <el-alert title="学习路线生成失败" :description="generationError" type="error" :closable="false" show-icon />
      <el-button type="primary" :loading="generating" @click="generate">重新生成</el-button>
    </div>

    <el-card v-if="!draft||generatorExpanded" class="path-generate-card" shadow="never">
      <div class="path-generate-heading">
        <div class="path-ai-icon"><span>✦</span></div>
        <div><div class="path-ai-state"><i></i> AI 路径规划器已就绪</div><h2>你想系统学习什么？</h2><p>描述目标、基础和周期，AI 将为你构建可执行的知识图谱。</p></div>
      </div>
      <div class="path-prompt-shell"><el-input v-model="form.requirement" type="textarea" :rows="4" maxlength="2000" show-word-limit placeholder="例如：我Java基础一般，希望用10天系统复习Java基础，为Java后端面试做准备，每天2小时。" /></div>
      <div class="path-examples"><button v-for="example in examples" :key="example" @click="form.requirement=example">{{ example }}</button></div>
      <el-collapse class="path-settings"><el-collapse-item title="附加设置">
        <el-form label-position="top" class="path-settings-grid">
          <el-form-item label="关联科目"><el-select v-model="form.subjectId" clearable placeholder="可稍后选择"><el-option v-for="s in subjects" :key="s.id" :label="s.name" :value="s.id" /></el-select></el-form-item>
          <el-form-item label="每日时间"><el-input-number v-model="form.dailyMinutes" :min="15" :max="720" /></el-form-item>
          <el-form-item label="开始日期"><el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" /></el-form-item>
          <el-form-item label="目标日期"><el-date-picker v-model="form.targetDate" type="date" value-format="YYYY-MM-DD" clearable :disabled-date="targetDateDisabled" /><small class="muted">单条路径最多10个自然日</small></el-form-item>
          <el-form-item label="当前水平"><el-input v-model="form.currentLevel" /></el-form-item>
          <el-form-item label="目标水平"><el-input v-model="form.targetLevel" /></el-form-item>
          <el-form-item label="学习偏好" class="wide"><el-input v-model="form.preferences" /></el-form-item>
        </el-form>
      </el-collapse-item></el-collapse>
      <div class="path-generate-footer"><span><i></i> 生成内容仅作为草案，确认后才会保存</span><div class="path-generate-actions"><el-button v-if="draft" @click="generatorExpanded=false">取消修改</el-button><el-button type="primary" class="primary-action path-generate-button" :disabled="!form.requirement.trim()||generating" :loading="generating" @click="generate">{{ generating?'正在为你设计学习路线...':'✦ AI生成学习路线' }}</el-button></div></div>
    </el-card>

    <el-card v-else shadow="never" class="path-generate-summary">
      <div>
        <div class="path-section-kicker">AI GENERATED ROADMAP</div>
        <h2>{{ draft.title }}</h2>
        <p>{{ draftDurationDays }}天 · {{ draftItems.length }}个知识点 · {{ draftTotalMinutes }}分钟</p>
      </div>
      <div class="path-generate-summary-actions">
        <el-button @click="generatorExpanded=true">修改生成条件</el-button>
        <el-button type="primary" :loading="generating" @click="generate">重新生成</el-button>
      </div>
    </el-card>

    <el-card v-if="draft" shadow="never" class="path-draft-card">
      <div class="path-draft-heading"><div><div class="path-section-kicker">EDITABLE ROADMAP DRAFT</div><el-input v-model="draft.title" class="path-title-input" /><div class="path-draft-metrics"><span><b>{{ draft.stages.length }}</b> 阶段</span><span><b>{{ draftItems.length }}</b> 知识点</span><span><b>{{ draftTotalMinutes }}</b> 分钟</span></div></div><el-button type="primary" class="primary-action" :loading="confirming" @click="confirm">确认学习路径</el-button></div>
      <div class="path-stage-track"><section v-for="(stage,si) in draft.stages" :key="stage.stageNo" class="path-stage"><div class="path-stage-node"><span>{{ stage.stageNo }}</span></div><div class="path-stage-title"><span>PHASE {{ String(stage.stageNo).padStart(2,'0') }}</span><el-input v-model="stage.title" /></div><el-input v-model="stage.description" type="textarea" :rows="2" placeholder="阶段说明" />
        <article v-for="(item,ii) in stage.items" :key="item.sequenceNo" class="path-item-card">
          <div class="path-item-head"><div class="path-day-chip"><i></i> DAY {{ item.suggestedDay||'--' }}</div><div><el-button link @click="move(si,ii,-1)">上移</el-button><el-button link @click="move(si,ii,1)">下移</el-button><el-button link type="danger" @click="removeItem(si,ii)">删除</el-button></div></div>
          <el-input v-model="item.topic" class="path-topic-input" /><label>学习目标</label><el-input v-model="item.learningObjective" type="textarea" :rows="2" /><div class="path-item-columns"><div><label>建议学习方式（每行一项，建议最多3项）</label><el-input :model-value="item.learningMethod.join('\n')" type="textarea" :rows="3" @update:model-value="(value:string)=>updateMethods(item,value)" /></div><div><label>完成标准（每行一项，建议最多3项）</label><el-input :model-value="item.completionCriteria.join('\n')" type="textarea" :rows="3" @update:model-value="(value:string)=>updateCriteria(item,value)" /></div></div><div class="path-item-schedule"><el-input-number v-model="item.estimatedMinutes" :min="15" :max="1440" /><span class="muted"> 分钟</span><el-input-number v-model="item.suggestedDay" :min="1" /><span class="muted">建议 Day</span></div>
          <el-collapse class="path-item-more"><el-collapse-item title="更多设置（前置知识与为什么学）"><div class="path-item-more-fields"><label>前置知识</label><el-input v-model="item.prerequisite" clearable /><label>为什么学这个</label><el-input v-model="item.reason" type="textarea" :rows="2" /></div></el-collapse-item></el-collapse>
        </article>
      </section></div>
    </el-card>
      </el-tab-pane>
    </el-tabs>

    <el-drawer v-model="detailVisible" class="learning-path-detail-drawer" title="学习路径详情" size="min(720px, 94vw)" :show-close="true" :close-on-press-escape="true">
      <template v-if="detail"><div class="path-detail-summary"><div class="path-section-kicker">LEARNING PATH STATUS</div><h2>{{ detail.title }}</h2><p>{{ detail.summary }}</p><div class="path-detail-progress"><b>{{ detail.progress }}%</b><el-progress :percentage="detail.progress" :show-text="false" :stroke-width="8" /></div><span>已完成 {{ detail.completedItems }} / {{ detail.totalItems }}，跳过 {{ detail.skippedItems }}</span></div>
        <el-collapse v-model="activeStages" class="path-stage-collapse">
          <el-collapse-item v-for="stageNo in [...new Set(detail.items.map(i=>i.stageNo))]" :key="stageNo" :name="String(stageNo)">
            <template #title>
              <div class="path-stage-summary">
                <span>PHASE {{ String(stageNo).padStart(2,'0') }}</span>
                <strong>{{ detail.items.find(i=>i.stageNo===stageNo)?.stageTitle }}</strong>
                <small>{{ detail.items.filter(i=>i.stageNo===stageNo&&i.effectiveStatus==='COMPLETED').length }} / {{ detail.items.filter(i=>i.stageNo===stageNo).length }} 完成</small>
              </div>
            </template>
            <div class="path-node-list">
              <button v-for="item in detail.items.filter(i=>i.stageNo===stageNo)" :key="item.id" type="button" class="path-node-summary" @click="openItemDetail(item)">
                <span class="path-node-status" :class="item.effectiveStatus.toLowerCase()">{{ item.effectiveStatus==='COMPLETED'?'✓':item.effectiveStatus==='SKIPPED'?'—':'○' }}</span>
                <span class="path-node-day">Day {{ item.suggestedDay||'--' }}</span>
                <strong>{{ item.topic }}</strong>
                <small>{{ item.estimatedMinutes }} min</small>
              </button>
            </div>
          </el-collapse-item>
        </el-collapse>
      </template>
    </el-drawer>

    <el-drawer v-model="itemDetailVisible" title="学习节点详情" size="min(560px, 94vw)" append-to-body>
      <template v-if="selectedItem">
        <div class="path-node-detail-heading">
          <div><span>DAY {{ selectedItem.suggestedDay||'--' }}</span><h2>{{ selectedItem.topic }}</h2></div>
          <el-tag :type="selectedItem.effectiveStatus==='COMPLETED'?'success':selectedItem.effectiveStatus==='SKIPPED'?'info':'warning'">{{ statusLabel(selectedItem.effectiveStatus) }}</el-tag>
        </div>
        <section class="path-node-detail-section"><h3>学习目标</h3><p>{{ selectedItem.learningObjective }}</p></section>
        <section class="path-node-detail-section"><h3>学习方法</h3><ol><li v-for="method in selectedItem.learningMethod" :key="method">{{ method }}</li></ol></section>
        <section class="path-node-detail-section"><h3>完成标准</h3><ul><li v-for="criterion in selectedItem.completionCriteria" :key="criterion">{{ criterion }}</li></ul></section>
        <section v-if="selectedItem.prerequisite" class="path-node-detail-section"><h3>前置知识</h3><p>{{ selectedItem.prerequisite }}</p></section>
        <section v-if="selectedItem.reason" class="path-node-detail-section"><h3>为什么学这个</h3><p>{{ selectedItem.reason }}</p></section>
        <div class="path-node-detail-meta"><span>预计时间</span><strong>{{ selectedItem.estimatedMinutes }} 分钟</strong></div>
        <div class="path-item-actions path-node-detail-actions">
          <template v-if="selectedItem.taskId"><el-tag type="success">已关联：{{ selectedItem.taskTitle }}</el-tag><el-button type="primary" plain @click="$router.push('/tasks')">查看任务</el-button><el-button type="primary" plain @click="$router.push('/plans')">安排每日计划</el-button></template>
          <template v-else><el-button type="primary" class="primary-action" :loading="createTaskId===selectedItem.id" @click="createTask(selectedItem.id)">创建学习任务</el-button><el-button v-if="selectedItem.effectiveStatus==='PENDING'" @click="changeSelectedItemStatus('SKIPPED')">跳过节点</el-button><el-button v-else @click="changeSelectedItemStatus('PENDING')">恢复待学习</el-button></template>
        </div>
      </template>
    </el-drawer>
  </section>
</template>
