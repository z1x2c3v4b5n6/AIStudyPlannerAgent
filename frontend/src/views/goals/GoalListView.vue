<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { goalApi } from '../../api/goal'
import { subjectApi } from '../../api/subject'
import type { Goal, GoalDetail, GoalStatus } from '../../types/goal'
import type { Subject } from '../../types/subject'
import { formatDate, formatDateTime, goalStatusMap, minutesLabel, nullableText } from '../../utils/display'

const subjects = ref<Subject[]>([])
const rows = ref<Goal[]>([])
const total = ref(0)
const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<GoalDetail | null>(null)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const query = reactive<{ page: number; pageSize: number; subjectId?: number; status?: GoalStatus }>({ page: 1, pageSize: 10 })
const form = reactive({ subjectId: undefined as number | undefined, title: '', description: '', targetMinutes: 60, targetDate: '' })
const subjectNames = computed(() => new Map(subjects.value.map((item) => [item.id, item.name])))
const statusMeta = (status: GoalStatus) => goalStatusMap[status]
const rules: FormRules = {
  subjectId: [{ required: true, message: '请选择科目', trigger: 'change' }],
  title: [{ required: true, message: '请输入目标标题', trigger: 'blur' }, { max: 200, message: '标题不能超过 200 个字符', trigger: 'blur' }],
  description: [{ max: 1000, message: '描述不能超过 1000 个字符', trigger: 'blur' }],
  targetMinutes: [{ required: true, message: '请输入目标时长', trigger: 'blur' }, { type: 'number', min: 1, message: '目标时长必须大于 0', trigger: 'blur' }],
}

async function load() {
  loading.value = true
  try { const data = (await goalApi.list({ ...query })).data.data; rows.value = data.list; total.value = data.total } catch { rows.value = []; total.value = 0 } finally { loading.value = false }
}
async function loadSubjects() { try { subjects.value = (await subjectApi.list()).data.data } catch { subjects.value = [] } }
function resetForm() { editingId.value = null; Object.assign(form, { subjectId: undefined, title: '', description: '', targetMinutes: 60, targetDate: '' }); nextTick(() => formRef.value?.clearValidate()) }
function openCreate() { resetForm(); dialogVisible.value = true }
function openEdit(item: Goal) { editingId.value = item.id; Object.assign(form, { subjectId: item.subjectId, title: item.title, description: item.description || '', targetMinutes: item.targetMinutes, targetDate: item.targetDate || '' }); dialogVisible.value = true; nextTick(() => formRef.value?.clearValidate()) }

async function submit() {
  if (submitting.value || !(await formRef.value?.validate().catch(() => false)) || !form.subjectId) return
  const base = { subjectId: form.subjectId, title: form.title.trim(), description: nullableText(form.description), targetMinutes: form.targetMinutes, targetDate: form.targetDate || null }
  submitting.value = true
  try {
    if (editingId.value) { await goalApi.update(editingId.value, base); ElMessage.success('目标更新成功') }
    else { await goalApi.create({ ...base, status: 'ACTIVE' }); ElMessage.success('目标创建成功') }
    dialogVisible.value = false; await load()
  } catch { /* HTTP 拦截器统一提示 */ } finally { submitting.value = false }
}

async function changeStatus(item: Goal, status: GoalStatus) {
  if (item.status === status) return
  try { await goalApi.changeStatus(item.id, status); ElMessage.success('目标状态已更新'); await load() } catch { /* HTTP 拦截器统一提示 */ }
}
async function showDetail(item: Goal) { detailVisible.value = true; detailLoading.value = true; detail.value = null; try { detail.value = (await goalApi.get(item.id)).data.data } catch { detailVisible.value = false } finally { detailLoading.value = false } }
async function remove(item: Goal) {
  try {
    await ElMessageBox.confirm(`确定删除目标“${item.title}”吗？`, '删除确认', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' })
    await goalApi.remove(item.id); ElMessage.success('目标删除成功')
    if (rows.value.length === 1 && query.page > 1) query.page--
    await load()
  } catch (error) { if (error !== 'cancel' && error !== 'close') { /* HTTP 拦截器统一提示 */ } }
}
function resetFilters() { query.subjectId = undefined; query.status = undefined; query.page = 1; load() }
watch(() => [query.subjectId, query.status], () => { query.page = 1; load() })
onMounted(async () => { await Promise.all([loadSubjects(), load()]) })
</script>

<template>
  <section class="page-section">
    <div class="page-heading"><div><h1>学习目标</h1><p>用明确的时长和日期组织阶段性学习成果。</p></div><el-button type="primary" :disabled="!subjects.length" @click="openCreate">创建目标</el-button></div>
    <el-card shadow="never" class="panel-card">
      <div class="filter-bar"><el-select v-model="query.subjectId" clearable placeholder="全部科目"><el-option v-for="s in subjects" :key="s.id" :label="s.name" :value="s.id" /></el-select><el-select v-model="query.status" clearable placeholder="全部状态"><el-option v-for="(meta, status) in goalStatusMap" :key="status" :label="meta.label" :value="status" /></el-select><el-button @click="resetFilters">重置</el-button></div>
      <el-table v-loading="loading" :data="rows" empty-text="暂无符合条件的学习目标">
        <el-table-column prop="title" label="目标" min-width="180" show-overflow-tooltip />
        <el-table-column label="科目" min-width="120"><template #default="{ row }">{{ subjectNames.get(row.subjectId) || '未知科目' }}</template></el-table-column>
        <el-table-column label="目标时长" width="130"><template #default="{ row }">{{ minutesLabel(row.targetMinutes) }}</template></el-table-column>
        <el-table-column label="目标日期" width="120"><template #default="{ row }">{{ formatDate(row.targetDate) }}</template></el-table-column>
        <el-table-column label="状态" width="130"><template #default="{ row }"><el-dropdown trigger="click" @command="(status: GoalStatus) => changeStatus(row, status)"><el-tag :type="statusMeta(row.status).type" class="clickable-tag">{{ statusMeta(row.status).label }}</el-tag><template #dropdown><el-dropdown-menu><el-dropdown-item v-for="(meta, status) in goalStatusMap" :key="status" :command="status">{{ meta.label }}</el-dropdown-item></el-dropdown-menu></template></el-dropdown></template></el-table-column>
        <el-table-column label="创建时间" width="170"><template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template></el-table-column>
        <el-table-column label="操作" width="190" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="showDetail(row)">详情</el-button><el-button link type="primary" @click="openEdit(row)">编辑</el-button><el-button link type="danger" @click="remove(row)">删除</el-button></template></el-table-column>
      </el-table>
      <div class="pagination"><el-pagination v-model:current-page="query.page" v-model:page-size="query.pageSize" :total="total" :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next" @current-change="load" @size-change="() => { query.page = 1; load() }" /></div>
    </el-card>
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑目标' : '创建目标'" width="min(560px, 92vw)" destroy-on-close @closed="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top"><el-form-item label="所属科目" prop="subjectId"><el-select v-model="form.subjectId" class="full-width"><el-option v-for="s in subjects" :key="s.id" :label="s.name" :value="s.id" /></el-select></el-form-item><el-form-item label="目标标题" prop="title"><el-input v-model="form.title" maxlength="200" show-word-limit /></el-form-item><el-form-item label="描述" prop="description"><el-input v-model="form.description" type="textarea" :rows="3" maxlength="1000" show-word-limit /></el-form-item><div class="form-grid"><el-form-item label="目标时长（分钟）" prop="targetMinutes"><el-input-number v-model="form.targetMinutes" :min="1" /></el-form-item><el-form-item label="目标日期" prop="targetDate"><el-date-picker v-model="form.targetDate" type="date" value-format="YYYY-MM-DD" placeholder="可选" /></el-form-item></div></el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="submitting" @click="submit">保存</el-button></template>
    </el-dialog>
    <el-drawer v-model="detailVisible" title="目标详情" size="min(480px, 92vw)"><div v-loading="detailLoading"><template v-if="detail"><el-descriptions :column="1" border><el-descriptions-item label="标题">{{ detail.goal.title }}</el-descriptions-item><el-descriptions-item label="科目">{{ subjectNames.get(detail.goal.subjectId) || '未知科目' }}</el-descriptions-item><el-descriptions-item label="描述">{{ detail.goal.description || '—' }}</el-descriptions-item><el-descriptions-item label="目标时长">{{ minutesLabel(detail.goal.targetMinutes) }}</el-descriptions-item><el-descriptions-item label="目标日期">{{ formatDate(detail.goal.targetDate) }}</el-descriptions-item><el-descriptions-item label="状态">{{ goalStatusMap[detail.goal.status].label }}</el-descriptions-item><el-descriptions-item label="任务进度">{{ detail.completedTaskCount }} / {{ detail.taskCount }}</el-descriptions-item></el-descriptions><div class="progress-block"><span>完成率</span><el-progress :percentage="Math.round(detail.completionRate)" /></div></template></div></el-drawer>
  </section>
</template>
