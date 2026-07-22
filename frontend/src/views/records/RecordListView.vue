<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { recordApi } from '../../api/record'
import { subjectApi } from '../../api/subject'
import { taskApi } from '../../api/task'
import type { RecordPayload, StudyRecord } from '../../types/record'
import type { Subject } from '../../types/subject'
import type { StudyTask } from '../../types/task'
import { formatDateTime, minutesLabel, nullableText } from '../../utils/display'
import { shanghaiDateTime, wallTimeMillis } from '../../utils/businessTime'

const subjects = ref<Subject[]>([])
const rows = ref<StudyRecord[]>([])
const filterTasks = ref<StudyTask[]>([])
const formTasks = ref<StudyTask[]>([])
const total = ref(0)
const loading = ref(false)
const filterTaskLoading = ref(false)
const formTaskLoading = ref(false)
let filterTaskRequest = 0
let formTaskRequest = 0
const submitting = ref(false)
const dialogVisible = ref(false)
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<StudyRecord | null>(null)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()

const filters = reactive({ subjectId: undefined as number | undefined, taskId: undefined as number | undefined, dateRange: null as string[] | null })
const query = reactive({ page: 1, pageSize: 10 })
const form = reactive({
  subjectId: undefined as number | undefined,
  taskId: null as number | null,
  startedAt: '',
  endedAt: '',
  feedback: '',
})

function timeValues() {
  if (!form.startedAt || !form.endedAt) return null
  const start = wallTimeMillis(form.startedAt)
  const end = wallTimeMillis(form.endedAt)
  return Number.isFinite(start) && Number.isFinite(end) ? { start, end, span: end - start } : null
}

function validateTime(_rule: unknown, _value: unknown, callback: (error?: Error) => void) {
  const values = timeValues()
  if (!values) return callback()
  if (values.span <= 0) return callback(new Error('结束时间必须晚于开始时间'))
  if (values.span < 60_000) return callback(new Error('学习时长至少为 1 分钟'))
  if (values.span > 86_400_000) return callback(new Error('单次学习时长不能超过 24 小时'))
  if (form.startedAt.slice(0, 10) !== form.endedAt.slice(0, 10)) return callback(new Error('学习记录不能跨越自然日'))
  if (form.endedAt > shanghaiDateTime()) return callback(new Error('结束时间不能晚于北京时间'))
  callback()
}

const rules: FormRules = {
  subjectId: [{ required: true, message: '请选择科目', trigger: 'change' }],
  startedAt: [{ required: true, message: '请选择开始时间', trigger: 'change' }, { validator: validateTime, trigger: 'change' }],
  endedAt: [{ required: true, message: '请选择结束时间', trigger: 'change' }, { validator: validateTime, trigger: 'change' }],
  feedback: [{ max: 1000, message: '学习反馈不能超过 1000 个字符', trigger: 'blur' }],
}

const durationPreview = computed(() => {
  const values = timeValues()
  if (!values || values.span < 60_000 || values.span > 86_400_000) return '请设置有效的开始和结束时间'
  return minutesLabel(Math.floor(values.span / 60_000))
})

async function loadSubjects() {
  try { subjects.value = (await subjectApi.list()).data.data } catch { subjects.value = [] }
}

async function fetchTasks(subjectId: number, target: typeof filterTasks, currentTaskId?: number | null) {
  const isFilter = target === filterTasks
  const requestId = isFilter ? ++filterTaskRequest : ++formTaskRequest
  const loadingTarget = isFilter ? filterTaskLoading : formTaskLoading
  loadingTarget.value = true
  try {
    const page = (await taskApi.list({ page: 1, pageSize: 100, subjectId })).data.data
    if (requestId !== (isFilter ? filterTaskRequest : formTaskRequest)) return
    let tasks = page.list
    if (currentTaskId && !tasks.some((item) => item.id === currentTaskId)) {
      try {
        const current = (await taskApi.get(currentTaskId)).data.data
        if (requestId !== formTaskRequest) return
        if (current.subjectId === subjectId) tasks = [current, ...tasks]
      } catch { /* 当前关联任务不可访问时维持分页结果 */ }
    }
    target.value = tasks
    if (page.total > 100) ElMessage.warning('当前仅显示前 100 条任务')
  } catch {
    if (requestId === (isFilter ? filterTaskRequest : formTaskRequest)) target.value = []
  } finally {
    if (requestId === (isFilter ? filterTaskRequest : formTaskRequest)) loadingTarget.value = false
  }
}

async function loadRecords() {
  loading.value = true
  try {
    const params = {
      page: query.page,
      pageSize: query.pageSize,
      subjectId: filters.subjectId,
      taskId: filters.taskId,
      startDate: filters.dateRange?.[0] || undefined,
      endDate: filters.dateRange?.[1] || undefined,
    }
    const page = (await recordApi.list(params)).data.data
    rows.value = page.list
    total.value = page.total
  } catch { rows.value = []; total.value = 0 } finally { loading.value = false }
}

async function filterSubjectChanged() {
  filterTaskRequest++
  filters.taskId = undefined
  filterTasks.value = []
  if (filters.subjectId) await fetchTasks(filters.subjectId, filterTasks)
}

async function formSubjectChanged() {
  formTaskRequest++
  form.taskId = null
  formTasks.value = []
  if (form.subjectId) await fetchTasks(form.subjectId, formTasks)
}

function search() { query.page = 1; loadRecords() }
function resetFilters() {
  Object.assign(filters, { subjectId: undefined, taskId: undefined, dateRange: null })
  filterTasks.value = []
  query.page = 1
  loadRecords()
}

function resetForm() {
  editingId.value = null
  Object.assign(form, { subjectId: undefined, taskId: null, startedAt: '', endedAt: '', feedback: '' })
  formTasks.value = []
  nextTick(() => formRef.value?.clearValidate())
}

function openCreate() { resetForm(); dialogVisible.value = true }

async function openEdit(item: StudyRecord) {
  editingId.value = item.id
  form.subjectId = item.subjectId
  form.taskId = null
  form.startedAt = item.startedAt.slice(0, 19)
  form.endedAt = item.endedAt.slice(0, 19)
  form.feedback = item.feedback || ''
  dialogVisible.value = true
  await fetchTasks(item.subjectId, formTasks, item.taskId)
  form.taskId = item.taskId
  nextTick(() => formRef.value?.clearValidate())
}

async function submit() {
  if (submitting.value || !(await formRef.value?.validate().catch(() => false)) || !form.subjectId) return
  const payload: RecordPayload = {
    subjectId: form.subjectId,
    taskId: form.taskId,
    startedAt: form.startedAt,
    endedAt: form.endedAt,
    feedback: nullableText(form.feedback),
  }
  submitting.value = true
  try {
    if (editingId.value) { await recordApi.update(editingId.value, payload); ElMessage.success('学习记录更新成功') }
    else { await recordApi.create(payload); ElMessage.success('学习记录创建成功') }
    dialogVisible.value = false
    await loadRecords()
  } catch { /* HTTP 拦截器统一提示 */ } finally { submitting.value = false }
}

async function showDetail(item: StudyRecord) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try { detail.value = (await recordApi.get(item.id)).data.data } catch { detailVisible.value = false } finally { detailLoading.value = false }
}

async function remove(item: StudyRecord) {
  try {
    await ElMessageBox.confirm('删除后统计数据也会同步变化，确定继续吗？', '删除学习记录', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' })
    await recordApi.remove(item.id)
    ElMessage.success('学习记录删除成功')
    if (rows.value.length === 1 && query.page > 1) query.page--
    await loadRecords()
  } catch (error) { if (error !== 'cancel' && error !== 'close') { /* HTTP 拦截器统一提示 */ } }
}

onMounted(async () => { await Promise.all([loadSubjects(), loadRecords()]) })
</script>

<template>
  <section class="page-section">
    <div class="page-heading"><div><h1>学习记录</h1><p>记录实际学习时间和完成情况，为后续统计与 AI 规划提供数据。</p></div><el-button type="primary" :disabled="!subjects.length" @click="openCreate">新增学习记录</el-button></div>
    <el-card shadow="never" class="panel-card">
      <div class="filter-bar record-filter">
        <el-select v-model="filters.subjectId" clearable placeholder="全部科目" @change="filterSubjectChanged"><el-option v-for="item in subjects" :key="item.id" :label="item.name" :value="item.id" /></el-select>
        <el-select v-model="filters.taskId" clearable :disabled="!filters.subjectId" :loading="filterTaskLoading" placeholder="全部任务"><el-option v-for="item in filterTasks" :key="item.id" :label="item.title" :value="item.id" /></el-select>
        <el-date-picker v-model="filters.dateRange" type="daterange" value-format="YYYY-MM-DD" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" />
        <el-button type="primary" @click="search">查询</el-button><el-button @click="resetFilters">重置</el-button>
      </div>
      <div class="table-scroll"><el-table v-loading="loading" :data="rows" empty-text="暂无符合条件的学习记录">
        <el-table-column label="科目" min-width="140"><template #default="{ row }"><span class="color-dot" :style="{ backgroundColor: row.subjectColor || '#94a3b8' }" />{{ row.subjectName }}</template></el-table-column>
        <el-table-column label="关联任务" min-width="170" show-overflow-tooltip><template #default="{ row }">{{ row.taskTitle || '未关联任务' }}</template></el-table-column>
        <el-table-column label="开始时间" width="165"><template #default="{ row }">{{ formatDateTime(row.startedAt) }}</template></el-table-column>
        <el-table-column label="结束时间" width="165"><template #default="{ row }">{{ formatDateTime(row.endedAt) }}</template></el-table-column>
        <el-table-column label="实际时长" width="125"><template #default="{ row }"><strong>{{ minutesLabel(row.durationMinutes) }}</strong></template></el-table-column>
        <el-table-column prop="feedback" label="学习反馈" min-width="200" show-overflow-tooltip><template #default="{ row }">{{ row.feedback || '—' }}</template></el-table-column>
        <el-table-column label="操作" width="190" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="showDetail(row)">详情</el-button><el-button link type="primary" @click="openEdit(row)">编辑</el-button><el-button link type="danger" @click="remove(row)">删除</el-button></template></el-table-column>
      </el-table></div>
      <div class="pagination"><el-pagination v-model:current-page="query.page" v-model:page-size="query.pageSize" :total="total" :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next" @current-change="loadRecords" @size-change="() => { query.page = 1; loadRecords() }" /></div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑学习记录' : '新增学习记录'" width="min(620px, 92vw)" destroy-on-close @closed="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <div class="form-grid"><el-form-item label="科目" prop="subjectId"><el-select v-model="form.subjectId" class="full-width" @change="formSubjectChanged"><el-option v-for="item in subjects" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item><el-form-item label="关联任务（可选）" prop="taskId"><el-select v-model="form.taskId" clearable class="full-width" :disabled="!form.subjectId" :loading="formTaskLoading" placeholder="不关联任务"><el-option v-for="item in formTasks" :key="item.id" :label="item.title" :value="item.id" /></el-select></el-form-item></div>
        <div class="form-grid"><el-form-item label="开始时间" prop="startedAt"><el-date-picker v-model="form.startedAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" class="full-width" /></el-form-item><el-form-item label="结束时间" prop="endedAt"><el-date-picker v-model="form.endedAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" class="full-width" /></el-form-item></div>
        <div class="record-duration-preview"><span>预计记录时长（北京时间）</span><strong>{{ durationPreview }}</strong><small>不能跨越自然日；最终时长以后端计算为准</small></div>
        <el-form-item label="学习反馈" prop="feedback"><el-input v-model="form.feedback" type="textarea" :rows="4" maxlength="1000" show-word-limit placeholder="可选；清空后将删除原反馈" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="submitting" @click="submit">保存</el-button></template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="学习记录详情" size="min(500px, 92vw)"><div v-loading="detailLoading"><el-descriptions v-if="detail" :column="1" border><el-descriptions-item label="科目"><span class="color-dot" :style="{ backgroundColor: detail.subjectColor || '#94a3b8' }" />{{ detail.subjectName }}</el-descriptions-item><el-descriptions-item label="关联任务">{{ detail.taskTitle || '未关联任务' }}</el-descriptions-item><el-descriptions-item label="开始时间">{{ formatDateTime(detail.startedAt) }}</el-descriptions-item><el-descriptions-item label="结束时间">{{ formatDateTime(detail.endedAt) }}</el-descriptions-item><el-descriptions-item label="实际时长">{{ minutesLabel(detail.durationMinutes) }}</el-descriptions-item><el-descriptions-item label="学习反馈">{{ detail.feedback || '—' }}</el-descriptions-item><el-descriptions-item label="创建时间">{{ formatDateTime(detail.createdAt) }}</el-descriptions-item><el-descriptions-item label="更新时间">{{ formatDateTime(detail.updatedAt) }}</el-descriptions-item></el-descriptions></div></el-drawer>
  </section>
</template>
