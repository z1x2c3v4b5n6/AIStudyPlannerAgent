<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useAuthStore } from '../../stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const formRef = ref<FormInstance>()
const submitting = ref(false)
const form = reactive({ username: '', password: '' })
const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function submit() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  submitting.value = true
  try {
    await authStore.login(form)
    ElMessage.success('登录成功')
    await router.replace({ name: 'home' })
  } catch {
    // The HTTP interceptor displays the server message.
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="auth-page">
    <el-card class="auth-card" shadow="never">
      <div class="auth-brand"><span class="brand-mark" aria-hidden="true">AI</span><div><strong>AI 学习规划</strong><small>智能学习工作台</small></div></div>
      <h1>欢迎回来</h1>
      <p class="subtitle">登录后继续你的学习安排</p>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @keyup.enter="submit">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password autocomplete="current-password" />
        </el-form-item>
        <el-button type="primary" size="large" :loading="submitting" class="full-button" @click="submit">登录</el-button>
      </el-form>
      <p class="switch-link">还没有账号？<router-link to="/register">立即注册</router-link></p>
    </el-card>
  </main>
</template>
