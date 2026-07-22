<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const mobileMenuVisible = ref(false)

const navItems = [
  { path: '/', label: '学习概览' },
  { path: '/subjects', label: '科目管理' },
  { path: '/goals', label: '学习目标' },
  { path: '/tasks', label: '学习任务' },
  { path: '/records', label: '学习记录' },
  { path: '/statistics', label: '数据统计' },
]

async function logout() {
  await authStore.logout()
  await router.replace({ name: 'login' })
}
</script>

<template>
  <div class="app-shell">
    <aside class="app-sidebar">
      <div class="brand">AI 学习规划</div>
      <nav>
        <router-link v-for="item in navItems" :key="item.path" :to="item.path" :class="{ active: route.path === item.path }">
          {{ item.label }}
        </router-link>
      </nav>
    </aside>
    <div class="app-main">
      <header class="app-header">
        <button class="mobile-menu-button" type="button" aria-label="打开导航" @click="mobileMenuVisible = true">☰</button>
        <strong>AI 学习规划</strong>
        <div class="user-actions">
          <span>{{ authStore.user?.nickname || authStore.user?.username }}</span>
          <el-button text @click="logout">退出登录</el-button>
        </div>
      </header>
      <main class="content"><router-view /></main>
    </div>
    <el-drawer v-model="mobileMenuVisible" direction="ltr" size="240px" :with-header="false">
      <div class="brand mobile-brand">AI 学习规划</div>
      <nav class="mobile-nav">
        <router-link v-for="item in navItems" :key="item.path" :to="item.path" :class="{ active: route.path === item.path }" @click="mobileMenuVisible = false">
          {{ item.label }}
        </router-link>
      </nav>
    </el-drawer>
  </div>
</template>
