<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const authStore = useAuthStore()

async function logout() {
  await authStore.logout()
  await router.replace({ name: 'login' })
}
</script>

<template>
  <main class="home-page">
    <header>
      <strong>AI 学习规划</strong>
      <el-button plain @click="logout">退出登录</el-button>
    </header>
    <section class="welcome-card">
      <h1>你好，{{ authStore.user?.nickname }}</h1>
      <p>认证基础已经就绪。学习业务将在后续阶段实现。</p>
    </section>
  </main>
</template>

