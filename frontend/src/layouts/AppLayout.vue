<script setup lang="ts">
import { computed, ref } from 'vue'
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
  { path: '/plans', label: '学习计划' },
  { path: '/records', label: '学习记录' },
  { path: '/statistics', label: '数据统计' }
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
        <button
          class="mobile-menu-button"
          type="button"
          aria-label="打开导航"
          @click="mobileMenuVisible = true"
        >
          ☰
        </button>

        <router-link to="/" class="topbar-brand">
          <span class="brand-mark">AI</span>
          <strong>AI学习规划</strong>
        </router-link>

        <nav class="desktop-top-nav" aria-label="主导航">
          <router-link
            v-for="item in navItems"
            :key="item.path"
            :to="item.path"
            :class="{ active: route.path === item.path }"
          >
            {{ item.label }}
          </router-link>
        </nav>

        <el-dropdown trigger="click">
          <button class="user-menu-button" type="button">
            <span class="user-avatar">
              {{ (authStore.user?.nickname || authStore.user?.username || '用').slice(0, 1) }}
            </span>
            <span class="user-name">
              {{ authStore.user?.nickname || authStore.user?.username }}
            </span>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>

    <main class="content">
      <router-view />
    </main>

    <el-drawer
      v-model="mobileMenuVisible"
      direction="ltr"
      size="260px"
      :with-header="false"
    >
      <div class="mobile-brand">
        <span class="brand-mark">AI</span>
        <strong>AI学习规划</strong>
      </div>
      <nav class="mobile-nav">
        <router-link
          v-for="item in navItems"
          :key="item.path"
          :to="item.path"
          :class="{ active: route.path === item.path }"
          @click="mobileMenuVisible = false"
        >
          {{ item.label }}
        </router-link>
      </nav>
    </el-drawer>
  </div>
</template>
