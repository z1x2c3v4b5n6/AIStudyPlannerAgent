<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const mobileMenuVisible = ref(false)
type ThemeMode = 'dark' | 'light'
const storedTheme = localStorage.getItem('ai-study-theme')
const themeMode = ref<ThemeMode>(storedTheme === 'light' ? 'light' : 'dark')
document.documentElement.dataset.theme = themeMode.value

const navItems = [
  { path: '/', label: '学习概览', icon: '▦' },
  { path: '/subjects', label: '科目管理', icon: '▱' },
  { path: '/goals', label: '学习目标', icon: '◎' },
  { path: '/tasks', label: '学习任务', icon: '☑' },
  { path: '/learning-paths', label: '学习路径', icon: '⌁' },
  { path: '/plans', label: '每日计划', icon: '▣' },
  { path: '/records', label: '学习记录', icon: '◷' },
  { path: '/statistics', label: '数据统计', icon: '⌁' }
]
async function logout() {
  await authStore.logout()
  await router.replace({ name: 'login' })
}

function toggleTheme() {
  themeMode.value = themeMode.value === 'dark' ? 'light' : 'dark'
  document.documentElement.dataset.theme = themeMode.value
  localStorage.setItem('ai-study-theme', themeMode.value)
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
          <span class="agent-logo-mark" aria-hidden="true"><i></i><i></i><i></i></span>
          <strong>AI学习规划Agent</strong>
        </router-link>

        <nav class="desktop-top-nav" aria-label="主导航">
          <router-link
            v-for="item in navItems"
            :key="item.path"
            :to="item.path"
            :class="{ active: route.path === item.path }"
          >
            <span class="top-nav-icon" aria-hidden="true">{{ item.icon }}</span>
            {{ item.label }}
          </router-link>
        </nav>

        <div class="topbar-actions">
        <button
          class="theme-mode-toggle"
          type="button"
          :aria-label="themeMode === 'dark' ? '切换到浅色模式' : '切换到深色模式'"
          :title="themeMode === 'dark' ? '切换到浅色模式' : '切换到深色模式'"
          @click="toggleTheme"
        >
          <span aria-hidden="true">{{ themeMode === 'dark' ? '☀' : '☾' }}</span>
          <em>{{ themeMode === 'dark' ? 'Light' : 'Dark' }}</em>
        </button>

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
        <span class="agent-logo-mark" aria-hidden="true"><i></i><i></i><i></i></span>
        <strong>AI学习规划Agent</strong>
      </div>
      <nav class="mobile-nav">
        <router-link
          v-for="item in navItems"
          :key="item.path"
          :to="item.path"
          :class="{ active: route.path === item.path }"
          @click="mobileMenuVisible = false"
        >
          <span class="top-nav-icon" aria-hidden="true">{{ item.icon }}</span>
          {{ item.label }}
        </router-link>
      </nav>
    </el-drawer>
  </div>
</template>

<style scoped>
.app-header { grid-template-columns: 220px minmax(0, 1fr) auto; max-width: 1680px; gap: 14px; }
.agent-logo-mark { position: relative; display: inline-block; flex: 0 0 33px; width: 33px; height: 31px; }
.agent-logo-mark i { position: absolute; bottom: 0; width: 12px; height: 29px; clip-path: polygon(50% 0, 100% 100%, 0 100%); }
.agent-logo-mark i:nth-child(1) { left: 0; background: linear-gradient(#14dfbd, #0bb7dc); }
.agent-logo-mark i:nth-child(2) { left: 10px; height: 31px; background: linear-gradient(#2084ff, #12c8ed); }
.agent-logo-mark i:nth-child(3) { right: 0; height: 20px; background: linear-gradient(#8261ff, #3a76ff); }
.top-nav-icon { display: inline-grid; width: 17px; margin-right: 4px; place-items: center; color: currentcolor; font-size: 15px; opacity: .9; }
.desktop-top-nav a { display: inline-flex; align-items: center; }
.mobile-nav a { display: flex; align-items: center; }
.topbar-actions { display: flex; align-items: center; justify-content: flex-end; gap: 7px; min-width: 0; }
.theme-mode-toggle { display: inline-flex; align-items: center; justify-content: center; gap: 6px; min-height: 34px; padding: 0 10px; border: 1px solid rgb(77 129 199 / 34%); border-radius: 8px; color: #94a9c8; background: rgb(9 31 63 / 72%); cursor: pointer; transition: .2s ease; }
.theme-mode-toggle:hover { border-color: #268cf9; color: #e4f1ff; background: rgb(21 74 139 / 30%); }
.theme-mode-toggle span { color: #44aaff; font-size: 15px; line-height: 1; }
.theme-mode-toggle em { font-size: 10px; font-style: normal; font-weight: 700; letter-spacing: .3px; }
:global(html[data-theme='light']) .theme-mode-toggle { border-color: #d7e2f0; color: #50627d; background: #f6f9fd; }
:global(html[data-theme='light']) .theme-mode-toggle:hover { border-color: #7db4f8; color: #165ebc; background: #edf5ff; }
@media (max-width: 1280px) { .app-header { grid-template-columns: 190px minmax(0, 1fr) auto; }.topbar-brand strong { font-size: 14px; }.top-nav-icon { display: none; } }
@media (max-width: 980px) { .app-header { display: flex; }.topbar-brand strong { font-size: 15px; } }
@media (max-width: 560px) { .theme-mode-toggle em { display: none; }.theme-mode-toggle { width: 34px; padding: 0; } }
</style>
