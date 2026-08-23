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
  <main class="auth-page auth-login-page">
    <div class="auth-grid" aria-hidden="true"></div>
    <div class="auth-orbit auth-orbit-left" aria-hidden="true"></div>
    <div class="auth-orbit auth-orbit-right" aria-hidden="true"></div>

    <header class="auth-login-header">
      <router-link class="auth-login-logo" to="/login" aria-label="AI 学习规划 Agent 登录页">
        <span class="logo-symbol" aria-hidden="true">
          <i></i><i></i><i></i>
        </span>
        <strong>AI学习规划Agent</strong>
      </router-link>
      <p>
        还没有账号？
        <router-link to="/register">免费注册 <span aria-hidden="true">→</span></router-link>
      </p>
    </header>

    <section class="auth-login-content">
      <div class="login-hero">
        <span class="hero-eyebrow"><i aria-hidden="true">✦</i> 新一代 AI 学习规划平台</span>
        <h1>让 <em>AI</em> 帮你规划<br />真正可执行的<span>学习路径</span></h1>
        <p class="hero-description">
          基于你的目标、任务与时间，生成个性化学习路径与每日计划，<br class="desktop-break" />
          让每一次学习都更高效、更专注、更可持续。
        </p>

        <div class="hero-features" aria-label="核心能力">
          <article>
            <span class="feature-icon feature-icon-green" aria-hidden="true">
              <svg viewBox="0 0 24 24"><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8ZM19 8v6M22 11h-6" /></svg>
            </span>
            <div><strong>个性化学习路径</strong><small>结合目标与能力，生成专属学习路线</small></div>
          </article>
          <article>
            <span class="feature-icon feature-icon-blue" aria-hidden="true">
              <svg viewBox="0 0 24 24"><path d="M7 2v3M17 2v3M3 9h18M5 4h14a2 2 0 0 1 2 2v14H3V6a2 2 0 0 1 2-2ZM8 13h3v3H8z" /></svg>
            </span>
            <div><strong>每日学习计划</strong><small>任务清晰明确，专注执行不迷茫</small></div>
          </article>
          <article>
            <span class="feature-icon feature-icon-purple" aria-hidden="true">
              <svg viewBox="0 0 24 24"><path d="M4 20v-6h4v6M10 20V9h4v11M16 20V4h4v16M3 20h18" /></svg>
            </span>
            <div><strong>动态进度追踪</strong><small>实时记录学习数据，清晰掌握成长轨迹</small></div>
          </article>
        </div>

        <div class="dashboard-stage" aria-label="学习仪表盘界面预览">
          <div class="dashboard-glow"></div>
          <div class="dashboard-window">
            <div class="dashboard-titlebar">
              <strong>学习仪表盘</strong>
              <span><i></i><i></i><i></i></span>
            </div>
            <div class="dashboard-body">
              <aside class="dashboard-sidebar">
                <strong><span class="mini-logo">A</span> AI学习</strong>
                <span class="active">⌂&nbsp;&nbsp;学习概览</span>
                <span>▣&nbsp;&nbsp;学习计划</span>
                <span>◇&nbsp;&nbsp;任务中心</span>
                <span>▥&nbsp;&nbsp;进度分析</span>
              </aside>
              <div class="dashboard-main">
                <div class="dashboard-section-title">本周学习进度</div>
                <div class="dashboard-metrics">
                  <div class="progress-ring"><strong>72%</strong><small>进度良好</small></div>
                  <div><small>学习时长</small><strong>18.6<span>h</span></strong><i></i></div>
                  <div><small>完成任务</small><strong>24<span>/36</span></strong><i></i></div>
                  <div><small>连续学习</small><strong>7<span>天</span></strong><i></i></div>
                </div>
                <div class="dashboard-lower">
                  <section>
                    <strong>今日任务</strong>
                    <p><i></i><span>复习 Java 集合</span><small>进行中</small></p>
                    <p><i></i><span>练习 MySQL 查询</span><small>45分钟</small></p>
                    <p><i></i><span>整理 Redis 笔记</span><small>待开始</small></p>
                  </section>
                  <section class="ability-chart">
                    <strong>能力雷达</strong>
                    <svg viewBox="0 0 180 112" aria-hidden="true">
                      <polygon points="90,10 155,44 145,94 35,94 25,44" />
                      <polygon points="90,28 135,51 128,83 52,83 45,51" />
                      <polygon class="ability-shape" points="90,22 138,55 118,86 55,78 48,47" />
                      <line x1="90" y1="10" x2="90" y2="94" /><line x1="25" y1="44" x2="145" y2="94" /><line x1="155" y1="44" x2="35" y2="94" />
                    </svg>
                  </section>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <el-card class="auth-card auth-login-card" shadow="never">
        <div class="login-card-accent" aria-hidden="true"></div>
        <span class="login-status"><i></i> SECURE ACCESS</span>
        <h2>欢迎回来</h2>
        <p class="login-subtitle">登录后继续你的个性化学习旅程</p>

        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @keyup.enter="submit">
          <el-form-item label="用户名" prop="username">
            <el-input v-model="form.username" size="large" autocomplete="username" placeholder="请输入用户名">
              <template #prefix>
                <svg class="input-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M20 21a8 8 0 0 0-16 0M12 13a5 5 0 1 0 0-10 5 5 0 0 0 0 10Z" /></svg>
              </template>
            </el-input>
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input v-model="form.password" size="large" type="password" show-password autocomplete="current-password" placeholder="请输入登录密码">
              <template #prefix>
                <svg class="input-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M5 10h14v11H5zM8 10V7a4 4 0 0 1 8 0v3" /></svg>
              </template>
            </el-input>
          </el-form-item>

          <div class="login-security-note"><span aria-hidden="true">◈</span> 登录状态由服务端安全校验</div>
          <el-button type="primary" size="large" :loading="submitting" class="login-submit" @click="submit">
            <span>{{ submitting ? '正在登录...' : '登录进入工作台' }}</span>
            <span v-if="!submitting" class="button-arrow" aria-hidden="true">→</span>
          </el-button>
        </el-form>

        <div class="login-divider"><span>或</span></div>
        <router-link class="register-entry" to="/register">还没有账号？<strong>立即创建账号</strong></router-link>
      </el-card>
    </section>

    <footer class="auth-login-footer">AI STUDY PLANNER · 让规划真正落地</footer>
  </main>
</template>

<style scoped>
.auth-login-page {
  display: block;
  min-height: 100vh;
  padding: 0;
  overflow-x: hidden;
  color: #f4f8ff;
  background:
    radial-gradient(circle at 67% 39%, rgb(20 89 207 / 18%), transparent 30%),
    radial-gradient(circle at 7% 75%, rgb(0 209 190 / 10%), transparent 28%),
    linear-gradient(125deg, #020711 0%, #030a19 48%, #020814 100%);
}

.auth-login-page::before,
.auth-login-page::after { display: none; }

.auth-grid {
  position: fixed;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  background-image:
    linear-gradient(rgb(42 107 202 / 4%) 1px, transparent 1px),
    linear-gradient(90deg, rgb(42 107 202 / 4%) 1px, transparent 1px);
  background-size: 54px 54px;
  mask-image: linear-gradient(to bottom, transparent, #000 30%, #000 72%, transparent);
}

.auth-grid::after {
  position: absolute;
  right: 3.5%;
  top: 12%;
  width: 160px;
  height: 120px;
  content: '';
  opacity: .4;
  background-image: radial-gradient(circle, #1266da 1.5px, transparent 1.5px);
  background-size: 14px 14px;
}

.auth-orbit {
  position: fixed;
  z-index: 0;
  width: 720px;
  height: 260px;
  border: 1px solid rgb(31 104 255 / 20%);
  border-radius: 50%;
  pointer-events: none;
}

.auth-orbit::before,
.auth-orbit::after {
  position: absolute;
  inset: 24px -50px;
  border: 1px solid rgb(31 104 255 / 13%);
  border-radius: inherit;
  content: '';
}

.auth-orbit::after { inset: 54px -100px; }
.auth-orbit-left { left: -230px; bottom: -115px; transform: rotate(13deg); }
.auth-orbit-right { right: -350px; bottom: -70px; transform: rotate(-13deg); }

.auth-login-header {
  position: relative;
  z-index: 3;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 78px;
  padding: 0 clamp(24px, 3.5vw, 66px);
  border-bottom: 1px solid rgb(115 153 215 / 18%);
  background: rgb(2 7 17 / 62%);
  backdrop-filter: blur(18px);
  column-gap: clamp(48px, 12vw, 220px);
}

.auth-login-logo {
  display: inline-flex;
  align-items: center;
  gap: 13px;
  color: #f6f9ff;
  font-size: clamp(18px, 1.55vw, 25px);
  text-decoration: none;
  letter-spacing: -.5px;
  flex-shrink: 0;
}

.logo-symbol { position: relative; display: inline-block; width: 36px; height: 34px; }
.logo-symbol i { position: absolute; bottom: 0; width: 13px; height: 32px; clip-path: polygon(50% 0, 100% 100%, 0 100%); }
.logo-symbol i:nth-child(1) { left: 0; background: linear-gradient(#15e0bf, #06b6d4); }
.logo-symbol i:nth-child(2) { left: 11px; height: 34px; background: linear-gradient(#2579ff, #0fc8ef); }
.logo-symbol i:nth-child(3) { right: 0; height: 21px; background: linear-gradient(#8059ff, #367cff); }

.auth-login-header p { flex-shrink: 0; margin: 0 0 0 auto; color: #aeb9cd; font-size: 15px; }
.auth-login-header p a { margin-left: 6px; color: #20e2bb; font-weight: 650; text-decoration: none; }
.auth-login-header p a span { display: inline-block; margin-left: 5px; transition: transform .2s ease; }
.auth-login-header p a:hover span { transform: translateX(4px); }

.auth-login-content {
  position: relative;
  z-index: 2;
  display: grid;
  grid-template-columns: minmax(0, 1.45fr) minmax(390px, .82fr);
  align-items: center;
  gap: clamp(42px, 7vw, 116px);
  width: min(1480px, calc(100% - 80px));
  min-height: calc(100vh - 114px);
  margin: 0 auto;
  padding: 32px 0 48px;
}

.login-hero { min-width: 0; padding-left: 2px; }
.hero-eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  padding: 8px 17px;
  border: 1px solid #00cfa9;
  border-radius: 999px;
  color: #e6fff8;
  background: rgb(1 29 38 / 64%);
  font-size: 14px;
  box-shadow: 0 0 22px rgb(0 207 169 / 8%);
}
.hero-eyebrow i { color: #23e8c1; font-style: normal; }

.login-hero h1 {
  margin: 22px 0 13px;
  color: #f7f9fd;
  font-size: clamp(38px, 3.4vw, 60px);
  line-height: 1.25;
  letter-spacing: -2px;
  text-shadow: 0 4px 24px rgb(0 0 0 / 35%);
}
.login-hero h1 em { color: #24ddbd; font-style: normal; }
.login-hero h1 span { margin-left: .22em; color: #2d89ff; }
.hero-description { margin: 0; color: #99a8c1; font-size: 16px; line-height: 1.75; }

.hero-features { display: grid; grid-template-columns: repeat(3, 1fr); gap: 22px; margin-top: 24px; }
.hero-features article { display: flex; align-items: center; gap: 13px; min-width: 0; }
.feature-icon { display: grid; flex: 0 0 48px; width: 48px; height: 48px; place-items: center; border: 1px solid; border-radius: 50%; }
.feature-icon svg { width: 23px; height: 23px; fill: none; stroke: currentcolor; stroke-linecap: round; stroke-linejoin: round; stroke-width: 1.7; }
.feature-icon-green { border-color: rgb(0 218 181 / 45%); color: #22dfbd; background: rgb(0 184 155 / 8%); }
.feature-icon-blue { border-color: rgb(37 116 255 / 55%); color: #3486ff; background: rgb(37 116 255 / 8%); }
.feature-icon-purple { border-color: rgb(125 84 255 / 55%); color: #8c66ff; background: rgb(125 84 255 / 8%); }
.hero-features article div { display: grid; min-width: 0; gap: 3px; }
.hero-features strong { color: #edf3ff; font-size: 15px; white-space: nowrap; }
.hero-features small { color: #8493ae; font-size: 12px; line-height: 1.45; }

.dashboard-stage { position: relative; width: 82%; margin: 30px auto 0; perspective: 1100px; }
.dashboard-stage::after {
  position: absolute;
  right: -11%;
  bottom: -10%;
  left: -11%;
  height: 31%;
  border: 1px solid rgb(29 103 255 / 54%);
  border-radius: 50%;
  content: '';
  box-shadow: 0 0 22px #095eff, inset 0 0 26px rgb(26 105 255 / 22%), 0 0 0 18px rgb(18 86 229 / 8%), 0 0 0 34px rgb(18 86 229 / 4%);
  transform: rotateX(67deg);
}
.dashboard-glow { position: absolute; inset: 18% 8% 0; border-radius: 50%; background: #0866ff; filter: blur(65px); opacity: .24; }
.dashboard-window {
  position: relative;
  z-index: 2;
  overflow: hidden;
  border: 1px solid #278cff;
  border-radius: 15px;
  background: linear-gradient(145deg, rgb(7 25 59 / 97%), rgb(4 15 38 / 98%));
  box-shadow: 0 18px 70px rgb(0 36 112 / 60%), 0 0 25px rgb(31 116 255 / 28%), inset 0 0 30px rgb(49 108 255 / 8%);
  transform: rotateY(6deg) rotateX(2deg) rotateZ(1.2deg);
  transform-origin: bottom center;
}
.dashboard-titlebar { display: flex; align-items: center; justify-content: space-between; height: 34px; padding: 0 15px; border-bottom: 1px solid rgb(61 119 208 / 20%); color: #d8e8ff; font-size: 11px; }
.dashboard-titlebar span { display: flex; gap: 5px; }
.dashboard-titlebar i { width: 6px; height: 6px; border: 1px solid #789bd0; border-radius: 50%; }
.dashboard-body { display: grid; grid-template-columns: 120px 1fr; min-height: 250px; }
.dashboard-sidebar { display: flex; flex-direction: column; gap: 9px; padding: 15px 10px; border-right: 1px solid rgb(61 119 208 / 20%); color: #7385a7; font-size: 9px; }
.dashboard-sidebar strong { display: flex; align-items: center; gap: 6px; margin-bottom: 6px; color: #cfe5ff; }
.mini-logo { display: grid; width: 17px; height: 17px; place-items: center; border-radius: 5px; color: #021421; background: #15ddbc; font-size: 8px; }
.dashboard-sidebar > span { padding: 7px 8px; border-radius: 5px; }
.dashboard-sidebar > span.active { color: #1fe3cf; background: linear-gradient(90deg, rgb(16 135 208 / 35%), rgb(17 68 161 / 25%)); }
.dashboard-main { padding: 14px; }
.dashboard-section-title { color: #c8dcf7; font-size: 10px; font-weight: 650; }
.dashboard-metrics { display: grid; grid-template-columns: 110px repeat(3, 1fr); align-items: center; gap: 8px; margin-top: 8px; padding: 10px; border: 1px solid rgb(53 107 188 / 18%); border-radius: 9px; background: rgb(5 19 46 / 75%); }
.progress-ring { display: grid; width: 72px; height: 72px; margin: auto; place-content: center; border: 8px solid #152e50; border-top-color: #23dfbe; border-right-color: #23dfbe; border-bottom-color: #23dfbe; border-radius: 50%; text-align: center; box-shadow: 0 0 13px rgb(35 223 190 / 22%); }
.progress-ring strong { color: white; font-size: 19px; }
.progress-ring small { color: #8eabc8; font-size: 6px; }
.dashboard-metrics > div:not(.progress-ring) { position: relative; display: grid; gap: 3px; align-self: stretch; padding: 9px 6px; }
.dashboard-metrics small { color: #7085a6; font-size: 7px; }
.dashboard-metrics > div > strong { color: #30a3ff; font-size: 15px; }
.dashboard-metrics > div > strong span { margin-left: 2px; color: #8aa1bd; font-size: 7px; }
.dashboard-metrics > div > i { position: absolute; right: 3px; bottom: 8px; left: 3px; height: 10px; border-top: 1px solid #167ce9; clip-path: polygon(0 70%, 18% 10%, 36% 75%, 55% 32%, 72% 65%, 86% 16%, 100% 60%, 100% 100%, 0 100%); background: linear-gradient(rgb(26 133 255 / 25%), transparent); }
.dashboard-lower { display: grid; grid-template-columns: 1.25fr .75fr; gap: 9px; margin-top: 9px; }
.dashboard-lower section { min-height: 105px; padding: 10px; border: 1px solid rgb(53 107 188 / 18%); border-radius: 8px; background: rgb(5 19 46 / 60%); }
.dashboard-lower section > strong { color: #bcd3ef; font-size: 9px; }
.dashboard-lower p { display: grid; grid-template-columns: 8px 1fr auto; align-items: center; gap: 5px; margin: 7px 0 0; color: #9bb0cb; font-size: 7px; }
.dashboard-lower p i { width: 6px; height: 6px; border: 1px solid #16d8c0; border-radius: 2px; }
.dashboard-lower p small { padding: 2px 4px; border-radius: 5px; color: #36a8ef; background: rgb(31 128 224 / 12%); font-size: 6px; }
.ability-chart svg { display: block; width: 100%; height: 80px; margin: 3px auto 0; }
.ability-chart polygon, .ability-chart line { fill: none; stroke: rgb(65 130 224 / 35%); stroke-width: 1; }
.ability-chart .ability-shape { fill: rgb(22 119 255 / 28%); stroke: #168bff; }

.auth-login-card {
  position: relative;
  width: 100%;
  max-width: 500px;
  justify-self: end;
  overflow: visible;
  border: 1px solid rgb(27 117 255 / 76%);
  border-radius: 15px;
  background: linear-gradient(145deg, rgb(8 27 61 / 95%), rgb(4 17 42 / 96%));
  box-shadow: 0 28px 80px rgb(0 0 0 / 42%), 0 0 48px rgb(20 93 226 / 14%), inset 0 0 55px rgb(33 101 206 / 7%);
}
.auth-login-card::before { display: none; }
.auth-login-card :deep(.el-card__body) { padding: clamp(30px, 3.2vw, 46px); }
.login-card-accent { position: absolute; inset: 10px 10px auto auto; width: 120px; height: 80px; opacity: .18; background-image: radial-gradient(circle, #2386ff 1px, transparent 1px); background-size: 11px 11px; pointer-events: none; }
.login-status { display: inline-flex; align-items: center; gap: 7px; color: #5f83b4; font-size: 9px; letter-spacing: 1.7px; }
.login-status i { width: 6px; height: 6px; border-radius: 50%; background: #1fe0ba; box-shadow: 0 0 8px #1fe0ba; }
.auth-login-card h2 { margin: 14px 0 6px; color: #f4f8ff; font-size: 27px; letter-spacing: -.5px; }
.login-subtitle { margin: 0 0 26px; color: #8798b5; font-size: 14px; }
.auth-login-card :deep(.el-form-item) { margin-bottom: 20px; }
.auth-login-card :deep(.el-form-item__label) { height: auto; padding-bottom: 8px; color: #dce6f8; font-size: 13px; line-height: 1.3; }
.auth-login-card :deep(.el-input__wrapper) { min-height: 48px; padding: 0 14px; border: 1px solid rgb(80 119 181 / 30%); border-radius: 8px; background: rgb(8 22 49 / 88%); box-shadow: none; transition: border-color .2s ease, box-shadow .2s ease; }
.auth-login-card :deep(.el-input__wrapper:hover) { border-color: rgb(58 132 245 / 58%); }
.auth-login-card :deep(.el-input__wrapper.is-focus) { border-color: #247cff; box-shadow: 0 0 0 3px rgb(36 124 255 / 12%); }
.auth-login-card :deep(.el-input__inner) { color: #edf5ff; font-size: 14px; }
.auth-login-card :deep(.el-input__inner::placeholder) { color: #647692; }
.auth-login-card :deep(.el-input__password) { color: #7689a8; }
.input-icon { width: 19px; height: 19px; margin-right: 7px; fill: none; stroke: #7f94b5; stroke-linecap: round; stroke-linejoin: round; stroke-width: 1.6; }
.login-security-note { display: flex; align-items: center; gap: 7px; margin: -2px 0 16px; color: #7084a3; font-size: 12px; }
.login-security-note span { color: #1cceae; }
.auth-login-card .login-submit {
  display: flex;
  width: 100%;
  min-height: 49px;
  border: 0;
  border-radius: 8px;
  color: #fff;
  background: linear-gradient(100deg, #225dff 0%, #238ef6 50%, #20d8b3 100%);
  box-shadow: 0 12px 30px rgb(27 103 241 / 27%);
  font-size: 15px;
  font-weight: 700;
  transition: transform .2s ease, box-shadow .2s ease, filter .2s ease;
}
.auth-login-card .login-submit:hover { color: #fff; transform: translateY(-1px); filter: brightness(1.08); box-shadow: 0 15px 34px rgb(27 103 241 / 37%); }
.auth-login-card .login-submit.is-loading { color: #fff; }
.button-arrow { position: absolute; right: 20px; font-size: 20px; font-weight: 400; }
.login-divider { display: flex; align-items: center; gap: 14px; margin: 25px 0 17px; color: #6c7e99; font-size: 12px; }
.login-divider::before, .login-divider::after { flex: 1; height: 1px; content: ''; background: rgb(94 122 164 / 24%); }
.register-entry { display: block; padding: 13px; border: 1px solid rgb(94 122 164 / 28%); border-radius: 8px; color: #8798b5 !important; text-align: center; text-decoration: none; transition: border-color .2s ease, background .2s ease; }
.register-entry strong { margin-left: 7px; color: #21ddb8; font-weight: 650; }
.register-entry:hover { border-color: rgb(32 211 177 / 46%); background: rgb(32 211 177 / 4%); }

.auth-login-footer { position: absolute; z-index: 2; right: 0; bottom: 12px; left: 0; color: #3f526e; font-size: 9px; text-align: center; letter-spacing: 2px; pointer-events: none; }

@media (max-width: 1220px) {
  .auth-login-content { grid-template-columns: minmax(0, 1.2fr) minmax(380px, .8fr); gap: 42px; width: min(1140px, calc(100% - 48px)); }
  .login-hero h1 { font-size: 43px; }
  .hero-features { gap: 12px; }
  .hero-features small { display: none; }
  .dashboard-stage { width: 92%; }
}

@media (max-width: 940px) {
  .auth-login-content { grid-template-columns: 1fr; width: min(620px, calc(100% - 40px)); padding: 50px 0 70px; }
  .login-hero { text-align: center; }
  .hero-description br { display: none; }
  .hero-features { max-width: 590px; margin-right: auto; margin-left: auto; text-align: left; }
  .dashboard-stage { display: none; }
  .auth-login-card { max-width: 540px; justify-self: center; }
  .auth-login-footer { position: relative; padding-bottom: 18px; }
}

@media (max-width: 620px) {
  .auth-login-header { height: 66px; padding: 0 18px; }
  .auth-login-logo { gap: 8px; font-size: 16px; }
  .logo-symbol { width: 28px; height: 27px; transform: scale(.82); transform-origin: left center; }
  .auth-login-header p { font-size: 12px; }
  .auth-login-header p > a { display: inline-block; }
  .auth-login-content { width: calc(100% - 28px); min-height: calc(100vh - 66px); padding: 35px 0 60px; gap: 28px; }
  .hero-eyebrow { padding: 7px 13px; font-size: 12px; }
  .login-hero h1 { margin-top: 17px; font-size: clamp(32px, 10vw, 41px); line-height: 1.24; letter-spacing: -1.4px; }
  .login-hero h1 span { display: inline; margin-left: .16em; }
  .hero-description { font-size: 14px; }
  .hero-features { grid-template-columns: 1fr; max-width: 290px; gap: 10px; margin-top: 20px; }
  .hero-features article { padding: 8px 10px; border: 1px solid rgb(91 129 191 / 14%); border-radius: 10px; background: rgb(5 20 45 / 46%); }
  .feature-icon { flex-basis: 38px; width: 38px; height: 38px; }
  .feature-icon svg { width: 19px; height: 19px; }
  .hero-features small { display: block; }
  .auth-login-card :deep(.el-card__body) { padding: 27px 21px; }
  .auth-login-card h2 { font-size: 24px; }
}

@media (max-width: 390px) {
  .auth-login-header p { max-width: 116px; text-align: right; }
  .auth-login-header p > a { margin-top: 2px; }
  .login-hero h1 { font-size: 31px; }
  .hero-description { padding: 0 4px; }
}

@media (prefers-reduced-motion: reduce) {
  .auth-login-page *, .auth-login-page *::before, .auth-login-page *::after { scroll-behavior: auto !important; transition-duration: .01ms !important; }
}
</style>
