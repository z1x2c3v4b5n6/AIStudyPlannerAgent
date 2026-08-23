<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { subjectApi } from '../../api/subject'
import type { Subject, SubjectPayload } from '../../types/subject'
import { formatDateTime, nullableText } from '../../utils/display'

const subjects = ref<Subject[]>([])
const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const keyword = ref('')
const formRef = ref<FormInstance>()
const form = reactive({ name: '', description: '', color: '#409EFF', sortOrder: 0 })
const visibleSubjects = computed(() => {
  const value = keyword.value.trim().toLowerCase()
  if (!value) return subjects.value
  return subjects.value.filter((item) => `${item.name} ${item.description || ''}`.toLowerCase().includes(value))
})
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
  <section class="page-section subject-tech-page">
    <div class="page-heading"><div><h1>科目管理</h1><p>维护学习内容的分类，目标和任务将归属到具体科目。</p></div><el-button type="primary" @click="openCreate">创建科目</el-button></div>
    <div class="subject-tech-toolbar">
      <el-input v-model="keyword" clearable placeholder="搜索科目名称或描述"><template #prefix>⌕</template></el-input>
      <span class="subject-count">共 {{ visibleSubjects.length }} 个学习科目</span>
    </div>
    <div v-loading="loading" class="subject-card-grid">
      <article v-for="item in visibleSubjects" :key="item.id" class="subject-tech-card" :style="{ '--subject-color': item.color || '#2588ff' }">
        <span class="subject-tech-icon">{{ item.name.slice(0, 1).toUpperCase() }}</span>
        <div class="subject-tech-main">
          <h3>{{ item.name }}</h3>
          <p>{{ item.description || '暂未填写科目描述' }}</p>
          <small>排序 {{ item.sortOrder }} · 创建于 {{ formatDateTime(item.createdAt) }}</small>
        </div>
        <div class="subject-tech-actions"><el-button link type="primary" @click="openEdit(item)">编辑</el-button><el-button link type="danger" @click="remove(item)">删除</el-button></div>
      </article>
      <el-empty v-if="!loading && !visibleSubjects.length" :description="keyword ? '没有匹配的学习科目' : '暂无科目，点击右上角创建'" />
    </div>
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
