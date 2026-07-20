<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useAuthStore } from '../../stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const formRef = ref<FormInstance>()
const submitting = ref(false)
const form = reactive({ username: '', nickname: '', password: '', confirmPassword: '' })
const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_]{3,50}$/, message: '用户名须为3到50位字母、数字或下划线', trigger: 'blur' },
  ],
  nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
  password: [{ required: true, min: 8, max: 72, message: '密码长度须为8到72位', trigger: 'blur' }],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    { validator: (_rule, value, callback) => value === form.password ? callback() : callback(new Error('两次密码不一致')), trigger: 'blur' },
  ],
}

async function submit() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  submitting.value = true
  try {
    await authStore.register({ username: form.username, password: form.password, nickname: form.nickname })
    ElMessage.success('注册成功，请登录')
    await router.replace({ name: 'login' })
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
      <h1>创建账号</h1>
      <p class="subtitle">第一阶段仅提供基础认证功能</p>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="用户名" prop="username"><el-input v-model="form.username" /></el-form-item>
        <el-form-item label="昵称" prop="nickname"><el-input v-model="form.nickname" /></el-form-item>
        <el-form-item label="密码" prop="password"><el-input v-model="form.password" type="password" show-password /></el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword"><el-input v-model="form.confirmPassword" type="password" show-password /></el-form-item>
        <el-button type="primary" :loading="submitting" class="full-button" @click="submit">注册</el-button>
      </el-form>
      <p class="switch-link">已有账号？<router-link to="/login">返回登录</router-link></p>
    </el-card>
  </main>
</template>

