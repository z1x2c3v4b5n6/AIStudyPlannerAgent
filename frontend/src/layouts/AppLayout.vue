<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const mobileMenuVisible = ref(false)

const navGroups = [
  { label: '学习工作台', items: [{ path: '/', label: '学习概览', icon: '⌂' }] },
  {
    label: '学习管理',
    items: [
      { path: '/subjects', label: '科目管理', icon: '◫' },
      { path: '/goals', label: '学习目标', icon: '◎' },
      { path: '/tasks', label: '学习任务', icon: '✓' },
    ],
  },
  {
    label: '规划执行',
    items: [
      { path: '/plans', label: '学习计划', icon: '✦' },
      { path: '/records', label: '学习记录', icon: '◷' },
    ],
  },
  { label: '数据分析', items: [{ path: '/statistics', label: '数据统计', icon: '▥' }] },
]
const navItems = navGroups.flatMap((group) => group.items)
const mainNavItems = navItems
const overflowNavItems = navItems.slice(5)
const displayName = computed(() => authStore.user?.nickname || authStore.user?.username || '学习者')
const initials = computed(() => displayName.value.slice(0, 1).toUpperCase())

async function logout() {
  await authStore.logout()
  await router.replace({ name: 'login' })
}
</script>

<template>
  <div class="app-shell">
    <header class="app-topbar">
      <div class="app-header">
        <button class="mobile-menu-button" type="button" aria-label="打开导航菜单" @click="mobileMenuVisible = true">☰</button>
        <div class="brand header-brand">
          <span class="brand-mark" aria-hidden="true">AI</span>
          <span><strong>AI 学习规划</strong></span>
        </div>
        <nav class="desktop-top-nav" aria-label="主导航">
          <router-link v-for="item in mainNavItems" :key="item.path" :to="item.path" :class="{ active: route.path === item.path }">
            <span class="nav-icon" aria-hidden="true">{{ item.icon }}</span>
            <span>{{ item.label }}</span>
          </router-link>
          <el-dropdown v-if="overflowNavItems.length" trigger="click" class="more-nav-dropdown">
            <button class="more-nav-button" type="button" :class="{ active: overflowNavItems.some((item) => route.path === item.path) }">
              更多 <span aria-hidden="true">⌄</span>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item v-for="item in overflowNavItems" :key="item.path" @click="router.push(item.path)">
                  <span class="dropdown-nav-item"><span aria-hidden="true">{{ item.icon }}</span>{{ item.label }}</span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </nav>
        <div class="user-actions">
          <el-dropdown trigger="click">
            <button class="user-menu-button" type="button" aria-label="打开用户菜单">
              <span class="user-avatar" aria-hidden="true">{{ initials }}</span>
              <span class="user-name">{{ displayName }}</span>
              <span aria-hidden="true">⌄</span>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
    </header>
    <div class="app-main">
      <main class="content"><router-view /></main>
    </div>
    <el-drawer v-model="mobileMenuVisible" direction="ltr" size="min(300px, 86vw)" :with-header="false">
      <div class="brand mobile-brand">
        <span class="brand-mark" aria-hidden="true">AI</span>
        <span><strong>AI 学习规划</strong><small>智能学习工作台</small></span>
      </div>
      <nav class="mobile-nav" aria-label="移动端主导航">
        <section v-for="group in navGroups" :key="group.label" class="nav-group">
          <span class="nav-group-title">{{ group.label }}</span>
          <router-link v-for="item in group.items" :key="item.path" :to="item.path" :class="{ active: route.path === item.path }" @click="mobileMenuVisible = false">
            <span class="nav-icon" aria-hidden="true">{{ item.icon }}</span>
            <span>{{ item.label }}</span>
          </router-link>
        </section>
      </nav>
    </el-drawer>
  </div>
</template>
