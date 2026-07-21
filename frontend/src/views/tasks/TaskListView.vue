<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { goalApi } from '../../api/goal'
import { subjectApi } from '../../api/subject'
import { taskApi } from '../../api/task'
import type { Goal } from '../../types/goal'
import type { Subject } from '../../types/subject'
import type { StudyTask, TaskStatus } from '../../types/task'
import { formatDate, formatDateTime, minutesLabel, nullableText, priorityMap, taskStatusMap } from '../../utils/display'

const subjects = ref<Subject[]>([]), goals = ref<Goal[]>([]), allGoals = ref<Goal[]>([]), rows = ref<StudyTask[]>([])
const total = ref(0), loading = ref(false), submitting = ref(false), goalLoading = ref(false)
const dialogVisible = ref(false), detailVisible = ref(false), detailLoading = ref(false)
const detail = ref<StudyTask | null>(null), editingId = ref<number | null>(null), formRef = ref<FormInstance>()
const query = reactive<{ page: number; pageSize: number; subjectId?: number; goalId?: number; status?: TaskStatus; priority?: number; plannedDate?: string }>({ page: 1, pageSize: 10 })
const form = reactive({ subjectId: undefined as number | undefined, goalId: undefined as number | undefined, title: '', description: '', priority: 2, estimatedMinutes: 30, plannedDate: '', dueAt: '' })
const subjectNames = computed(() => new Map(subjects.value.map((item) => [item.id, item.name])))
const goalNames = computed(() => new Map(allGoals.value.map((item) => [item.id, item.title])))
const statusMeta = (status: TaskStatus) => taskStatusMap[status]
const rules: FormRules = {
  subjectId: [{ required: true, message: '请选择科目', trigger: 'change' }],
  title: [{ required: true, message: '请输入任务标题', trigger: 'blur' }, { max: 200, message: '标题不能超过 200 个字符', trigger: 'blur' }],
  description: [{ max: 1000, message: '描述不能超过 1000 个字符', trigger: 'blur' }],
  priority: [{ required: true, message: '请选择优先级', trigger: 'change' }],
  estimatedMinutes: [{ required: true, message: '请输入预计时长', trigger: 'blur' }, { type: 'number', min: 1, message: '预计时长必须大于 0', trigger: 'blur' }],
}

async function load() { loading.value = true; try { const data = (await taskApi.list({ ...query })).data.data; rows.value = data.list; total.value = data.total } catch { rows.value = []; total.value = 0 } finally { loading.value = false } }
async function loadSubjects() {
  try {
    subjects.value = (await subjectApi.list()).data.data
    const responses = await Promise.all(subjects.value.map((subject) => goalApi.list({ page: 1, pageSize: 100, subjectId: subject.id })))
    allGoals.value = responses.flatMap((response) => response.data.data.list)
  } catch { subjects.value = []; allGoals.value = [] }
}
async function loadGoals(subjectId?: number) { goals.value = []; if (!subjectId) return; goalLoading.value = true; try { goals.value = (await goalApi.list({ page: 1, pageSize: 100, subjectId })).data.data.list } catch { goals.value = [] } finally { goalLoading.value = false } }
async function loadFilterGoals() { query.goalId = undefined; await loadGoals(query.subjectId) }
async function formSubjectChanged() { form.goalId = undefined; await loadGoals(form.subjectId) }
function resetForm() { editingId.value = null; Object.assign(form, { subjectId: undefined, goalId: undefined, title: '', description: '', priority: 2, estimatedMinutes: 30, plannedDate: '', dueAt: '' }); goals.value = []; nextTick(() => formRef.value?.clearValidate()) }
function openCreate() { resetForm(); dialogVisible.value = true }
async function openEdit(item: StudyTask) { editingId.value = item.id; Object.assign(form, { subjectId: item.subjectId, goalId: item.goalId || undefined, title: item.title, description: item.description || '', priority: item.priority, estimatedMinutes: item.estimatedMinutes, plannedDate: item.plannedDate || '', dueAt: item.dueAt ? item.dueAt.slice(0, 19) : '' }); dialogVisible.value = true; await loadGoals(item.subjectId); nextTick(() => formRef.value?.clearValidate()) }

async function submit() {
  if (submitting.value || !(await formRef.value?.validate().catch(() => false)) || !form.subjectId) return
  const payload = { subjectId: form.subjectId, goalId: form.goalId || null, title: form.title.trim(), description: nullableText(form.description), priority: form.priority, estimatedMinutes: form.estimatedMinutes, plannedDate: form.plannedDate || null, dueAt: form.dueAt || null }
  submitting.value = true
  try { if (editingId.value) { await taskApi.update(editingId.value, payload); ElMessage.success('任务更新成功') } else { await taskApi.create(payload); ElMessage.success('任务创建成功') } dialogVisible.value = false; await load() } catch { /* HTTP 拦截器统一提示 */ } finally { submitting.value = false }
}
async function changeStatus(item: StudyTask, status: TaskStatus) { if (item.status === status) return; try { await taskApi.changeStatus(item.id, status); ElMessage.success('任务状态已更新'); await load() } catch { /* HTTP 拦截器统一提示 */ } }
async function showDetail(item: StudyTask) { detailVisible.value = true; detailLoading.value = true; detail.value = null; try { detail.value = (await taskApi.get(item.id)).data.data; if (detail.value.goalId) await loadGoals(detail.value.subjectId) } catch { detailVisible.value = false } finally { detailLoading.value = false } }
async function remove(item: StudyTask) { try { await ElMessageBox.confirm(`确定删除任务“${item.title}”吗？`, '删除确认', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }); await taskApi.remove(item.id); ElMessage.success('任务删除成功'); if (rows.value.length === 1 && query.page > 1) query.page--; await load() } catch (error) { if (error !== 'cancel' && error !== 'close') { /* HTTP 拦截器统一提示 */ } } }
function resetFilters() { Object.assign(query, { page: 1, pageSize: query.pageSize, subjectId: undefined, goalId: undefined, status: undefined, priority: undefined, plannedDate: undefined }); goals.value = []; load() }
watch(() => [query.subjectId, query.goalId, query.status, query.priority, query.plannedDate], () => { query.page = 1; load() })
onMounted(async () => { await Promise.all([loadSubjects(), load()]) })
</script>

<template>
  <section class="page-section">
    <div class="page-heading"><div><h1>学习任务</h1><p>拆分可执行任务，安排学习日期、时长和优先级。</p></div><el-button type="primary" :disabled="!subjects.length" @click="openCreate">创建任务</el-button></div>
    <el-card shadow="never" class="panel-card">
      <div class="filter-bar"><el-select v-model="query.subjectId" clearable placeholder="全部科目" @change="loadFilterGoals"><el-option v-for="s in subjects" :key="s.id" :label="s.name" :value="s.id" /></el-select><el-select v-model="query.goalId" clearable :disabled="!query.subjectId" :loading="goalLoading" placeholder="全部目标"><el-option v-for="g in goals" :key="g.id" :label="g.title" :value="g.id" /></el-select><el-select v-model="query.status" clearable placeholder="全部状态"><el-option v-for="(meta, status) in taskStatusMap" :key="status" :label="meta.label" :value="status" /></el-select><el-select v-model="query.priority" clearable placeholder="全部优先级"><el-option v-for="(meta, level) in priorityMap" :key="level" :label="meta.label" :value="Number(level)" /></el-select><el-date-picker v-model="query.plannedDate" type="date" value-format="YYYY-MM-DD" placeholder="计划日期" /><el-button @click="resetFilters">重置</el-button></div>
      <el-table v-loading="loading" :data="rows" empty-text="暂无符合条件的学习任务">
        <el-table-column prop="title" label="任务" min-width="180" show-overflow-tooltip /><el-table-column label="科目" min-width="110"><template #default="{ row }">{{ subjectNames.get(row.subjectId) || '未知科目' }}</template></el-table-column><el-table-column label="目标" min-width="140" show-overflow-tooltip><template #default="{ row }">{{ row.goalId ? (goalNames.get(row.goalId) || `目标 #${row.goalId}`) : '—' }}</template></el-table-column><el-table-column label="优先级" width="90"><template #default="{ row }"><el-tag :type="priorityMap[row.priority].type">{{ priorityMap[row.priority].label }}</el-tag></template></el-table-column><el-table-column label="状态" width="125"><template #default="{ row }"><el-dropdown trigger="click" @command="(status: TaskStatus) => changeStatus(row, status)"><el-tag :type="statusMeta(row.status).type" class="clickable-tag">{{ statusMeta(row.status).label }}</el-tag><template #dropdown><el-dropdown-menu><el-dropdown-item v-for="(meta, status) in taskStatusMap" :key="status" :command="status">{{ meta.label }}</el-dropdown-item></el-dropdown-menu></template></el-dropdown></template></el-table-column><el-table-column label="预计时长" width="110"><template #default="{ row }">{{ minutesLabel(row.estimatedMinutes) }}</template></el-table-column><el-table-column label="计划日期" width="115"><template #default="{ row }">{{ formatDate(row.plannedDate) }}</template></el-table-column><el-table-column label="截止时间" width="165"><template #default="{ row }">{{ formatDateTime(row.dueAt) }}</template></el-table-column><el-table-column label="操作" width="190" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="showDetail(row)">详情</el-button><el-button link type="primary" @click="openEdit(row)">编辑</el-button><el-button link type="danger" @click="remove(row)">删除</el-button></template></el-table-column>
      </el-table>
      <div class="pagination"><el-pagination v-model:current-page="query.page" v-model:page-size="query.pageSize" :total="total" :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next" @current-change="load" @size-change="() => { query.page = 1; load() }" /></div>
    </el-card>
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑任务' : '创建任务'" width="min(620px, 94vw)" destroy-on-close @closed="resetForm"><el-form ref="formRef" :model="form" :rules="rules" label-position="top"><div class="form-grid"><el-form-item label="所属科目" prop="subjectId"><el-select v-model="form.subjectId" class="full-width" @change="formSubjectChanged"><el-option v-for="s in subjects" :key="s.id" :label="s.name" :value="s.id" /></el-select></el-form-item><el-form-item label="所属目标（可选）" prop="goalId"><el-select v-model="form.goalId" clearable class="full-width" :disabled="!form.subjectId" :loading="goalLoading"><el-option v-for="g in goals" :key="g.id" :label="g.title" :value="g.id" /></el-select></el-form-item></div><el-form-item label="任务标题" prop="title"><el-input v-model="form.title" maxlength="200" show-word-limit /></el-form-item><el-form-item label="描述" prop="description"><el-input v-model="form.description" type="textarea" :rows="3" maxlength="1000" show-word-limit /></el-form-item><div class="form-grid"><el-form-item label="优先级" prop="priority"><el-select v-model="form.priority" class="full-width"><el-option v-for="(meta, level) in priorityMap" :key="level" :label="meta.label" :value="Number(level)" /></el-select></el-form-item><el-form-item label="预计时长（分钟）" prop="estimatedMinutes"><el-input-number v-model="form.estimatedMinutes" :min="1" /></el-form-item><el-form-item label="计划日期" prop="plannedDate"><el-date-picker v-model="form.plannedDate" type="date" value-format="YYYY-MM-DD" placeholder="可选" /></el-form-item><el-form-item label="截止时间" prop="dueAt"><el-date-picker v-model="form.dueAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="可选" /></el-form-item></div></el-form><template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="submitting" @click="submit">保存</el-button></template></el-dialog>
    <el-drawer v-model="detailVisible" title="任务详情" size="min(500px, 92vw)"><div v-loading="detailLoading"><el-descriptions v-if="detail" :column="1" border><el-descriptions-item label="标题">{{ detail.title }}</el-descriptions-item><el-descriptions-item label="科目">{{ subjectNames.get(detail.subjectId) || '未知科目' }}</el-descriptions-item><el-descriptions-item label="目标">{{ detail.goalId ? (goalNames.get(detail.goalId) || `目标 #${detail.goalId}`) : '—' }}</el-descriptions-item><el-descriptions-item label="描述">{{ detail.description || '—' }}</el-descriptions-item><el-descriptions-item label="优先级">{{ priorityMap[detail.priority].label }}</el-descriptions-item><el-descriptions-item label="状态">{{ taskStatusMap[detail.status].label }}</el-descriptions-item><el-descriptions-item label="预计时长">{{ minutesLabel(detail.estimatedMinutes) }}</el-descriptions-item><el-descriptions-item label="计划日期">{{ formatDate(detail.plannedDate) }}</el-descriptions-item><el-descriptions-item label="截止时间">{{ formatDateTime(detail.dueAt) }}</el-descriptions-item><el-descriptions-item label="完成时间">{{ formatDateTime(detail.completedAt) }}</el-descriptions-item></el-descriptions></div></el-drawer>
  </section>
</template>
