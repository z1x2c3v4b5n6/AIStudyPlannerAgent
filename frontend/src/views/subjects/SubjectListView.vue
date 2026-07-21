<script setup lang="ts">
import { nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { subjectApi } from '../../api/subject'
import type { Subject, SubjectPayload } from '../../types/subject'
import { formatDateTime, nullableText } from '../../utils/display'

const subjects = ref<Subject[]>([])
const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const form = reactive({ name: '', description: '', color: '#409EFF', sortOrder: 0 })
const rules: FormRules = {
  name: [{ required: true, message: '请输入科目名称', trigger: 'blur' }, { max: 100, message: '名称不能超过 100 个字符', trigger: 'blur' }],
  description: [{ max: 500, message: '描述不能超过 500 个字符', trigger: 'blur' }],
  sortOrder: [{ required: true, message: '请输入排序值', trigger: 'blur' }],
}

async function loadSubjects() {
  loading.value = true
  try { subjects.value = (await subjectApi.list()).data.data } catch { subjects.value = [] } finally { loading.value = false }
}

function resetForm() {
  editingId.value = null
  Object.assign(form, { name: '', description: '', color: '#409EFF', sortOrder: 0 })
  nextTick(() => formRef.value?.clearValidate())
}

function openCreate() { resetForm(); dialogVisible.value = true }
function openEdit(item: Subject) {
  editingId.value = item.id
  Object.assign(form, { name: item.name, description: item.description || '', color: item.color || '#409EFF', sortOrder: item.sortOrder })
  dialogVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
}

async function submit() {
  if (submitting.value || !(await formRef.value?.validate().catch(() => false))) return
  const payload: SubjectPayload = { name: form.name.trim(), description: nullableText(form.description), color: form.color || null, sortOrder: form.sortOrder }
  submitting.value = true
  try {
    if (editingId.value) { await subjectApi.update(editingId.value, payload); ElMessage.success('科目更新成功') }
    else { await subjectApi.create(payload); ElMessage.success('科目创建成功') }
    dialogVisible.value = false
    await loadSubjects()
  } catch { /* HTTP 拦截器统一提示 */ } finally { submitting.value = false }
}

async function remove(item: Subject) {
  try {
    await ElMessageBox.confirm(`确定删除科目“${item.name}”吗？`, '删除确认', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' })
    await subjectApi.remove(item.id)
    ElMessage.success('科目删除成功')
    await loadSubjects()
  } catch (error) { if (error !== 'cancel' && error !== 'close') { /* HTTP 拦截器统一提示 */ } }
}

onMounted(loadSubjects)
</script>

<template>
  <section class="page-section">
    <div class="page-heading"><div><h1>科目管理</h1><p>维护学习内容的分类，目标和任务将归属到具体科目。</p></div><el-button type="primary" @click="openCreate">创建科目</el-button></div>
    <el-card shadow="never" class="panel-card">
      <el-table v-loading="loading" :data="subjects" empty-text="暂无科目，点击右上角创建">
        <el-table-column label="科目" min-width="150"><template #default="{ row }"><span class="color-dot" :style="{ backgroundColor: row.color || '#a8abb2' }" />{{ row.name }}</template></el-table-column>
        <el-table-column prop="description" label="描述" min-width="220" show-overflow-tooltip><template #default="{ row }">{{ row.description || '—' }}</template></el-table-column>
        <el-table-column prop="sortOrder" label="排序" width="90" />
        <el-table-column label="创建时间" width="170"><template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template></el-table-column>
        <el-table-column label="操作" width="150" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="openEdit(row)">编辑</el-button><el-button link type="danger" @click="remove(row)">删除</el-button></template></el-table-column>
      </el-table>
    </el-card>
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑科目' : '创建科目'" width="min(520px, 92vw)" destroy-on-close @closed="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="科目名称" prop="name"><el-input v-model="form.name" maxlength="100" show-word-limit /></el-form-item>
        <el-form-item label="描述" prop="description"><el-input v-model="form.description" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>
        <div class="form-grid"><el-form-item label="颜色" prop="color"><el-color-picker v-model="form.color" /></el-form-item><el-form-item label="排序值" prop="sortOrder"><el-input-number v-model="form.sortOrder" :step="1" :precision="0" /></el-form-item></div>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="submitting" @click="submit">保存</el-button></template>
    </el-dialog>
  </section>
</template>
