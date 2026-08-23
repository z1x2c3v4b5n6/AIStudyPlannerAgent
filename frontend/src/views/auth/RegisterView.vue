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
    { pattern: /^[A-Za-z0-9_]{3,50}$/, message: '用户名须为 3 至 50 位字母、数字或下划线', trigger: 'blur' },
  ],
  nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
  password: [{ required: true, min: 8, max: 72, message: '密码长度须为 8 至 72 位', trigger: 'blur' }],
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
  <main class="auth-page register-portal">
    <div class="register-grid" aria-hidden="true"></div>
    <div class="register-orbit" aria-hidden="true"></div>

    <header class="register-header">
      <router-link class="register-logo" to="/login" aria-label="AI 学习规划 Agent">
        <span class="register-logo-symbol" aria-hidden="true"><i></i><i></i><i></i></span>
        <strong>AI学习规划Agent</strong>
      </router-link>
      <p>已有账号？<router-link to="/login">去登录 <span aria-hidden="true">→</span></router-link></p>
    </header>

    <section class="register-content">
      <div class="register-hero">
        <span class="register-eyebrow"><i aria-hidden="true">✦</i> 新一代 AI 学习规划平台</span>
        <h1>让 <em>AI</em> 帮你规划<br />真正可执行的<span>学习路径</span></h1>
        <p>从目标拆解到每日执行，从真实学习记录到数据复盘，<br />把复杂的学习安排交给 AI，把专注留给自己。</p>

        <div class="register-benefits">
          <article><span class="benefit-green" aria-hidden="true">⌁</span><div><strong>个性化学习路径</strong><small>根据目标和当前水平拆解进阶阶段</small></div></article>
          <article><span class="benefit-blue" aria-hidden="true">▣</span><div><strong>每日执行计划</strong><small>结合真实任务与可用时间合理安排</small></div></article>
          <article><span class="benefit-purple" aria-hidden="true">⌁</span><div><strong>学习数据闭环</strong><small>记录实际学习，持续看见成长轨迹</small></div></article>
        </div>

        <div class="path-preview" aria-label="学习路径预览">
          <div class="path-preview-head"><span><i></i> AI LEARNING PATH</span><strong>Java 后端工程师进阶之路</strong><em>72%</em></div>
          <div class="path-preview-track"><i></i></div>
          <div class="path-preview-stages">
            <article class="done"><span>01</span><strong>Java 基础</strong><small>核心语法与集合</small><em>已完成</em></article>
            <b aria-hidden="true">→</b>
            <article class="done"><span>02</span><strong>框架进阶</strong><small>Spring 与 MyBatis</small><em>已完成</em></article>
            <b aria-hidden="true">→</b>
            <article class="active"><span>03</span><strong>中间件与存储</strong><small>Redis、MySQL</small><em>进行中</em></article>
            <b aria-hidden="true">→</b>
            <article><span>04</span><strong>项目实战</strong><small>工程能力提升</small><em>待解锁</em></article>
          </div>
          <div class="path-preview-data"><span>今日专注 <strong>120</strong> 分钟</span><span>连续学习 <strong>7</strong> 天</span><span>累计完成 <strong>24</strong> 项</span></div>
        </div>
      </div>

      <el-card class="register-card" shadow="never">
        <div class="register-card-dots" aria-hidden="true"></div>
        <span class="register-status"><i></i> CREATE YOUR WORKSPACE</span>
        <h2>创建你的账号</h2>
        <p class="register-subtitle">开始你的个性化学习之旅</p>

        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @keyup.enter="submit">
          <div class="register-form-grid">
            <el-form-item label="用户名" prop="username">
              <el-input v-model="form.username" size="large" autocomplete="username" placeholder="3—50 位字母、数字或下划线">
                <template #prefix><span class="field-icon" aria-hidden="true">♙</span></template>
              </el-input>
            </el-form-item>
            <el-form-item label="昵称" prop="nickname">
              <el-input v-model="form.nickname" size="large" autocomplete="nickname" placeholder="请输入展示昵称">
                <template #prefix><span class="field-icon" aria-hidden="true">✦</span></template>
              </el-input>
            </el-form-item>
          </div>
          <el-form-item label="密码" prop="password">
            <el-input v-model="form.password" size="large" type="password" show-password autocomplete="new-password" placeholder="至少 8 位，建议包含字母和数字">
              <template #prefix><span class="field-icon" aria-hidden="true">▢</span></template>
            </el-input>
          </el-form-item>
          <el-form-item label="确认密码" prop="confirmPassword">
            <el-input v-model="form.confirmPassword" size="large" type="password" show-password autocomplete="new-password" placeholder="请再次输入密码">
              <template #prefix><span class="field-icon" aria-hidden="true">▢</span></template>
            </el-input>
          </el-form-item>
          <p class="register-privacy"><span aria-hidden="true">✓</span> 注册即表示你同意安全使用本地学习数据</p>
          <el-button type="primary" size="large" :loading="submitting" class="register-submit" @click="submit">
            <span>{{ submitting ? '正在创建账号...' : '创建账号' }}</span><b v-if="!submitting" aria-hidden="true">→</b>
          </el-button>
        </el-form>

        <div class="register-divider"><span>或</span></div>
        <router-link class="login-entry" to="/login">已有账号？<strong>返回登录</strong></router-link>
      </el-card>
    </section>
    <footer class="register-footer">AI STUDY PLANNER · 让规划真正落地</footer>
  </main>
</template>

<style scoped>
.register-portal { display: block; min-height: 100vh; padding: 0; overflow-x: hidden; color: #f3f8ff; background: radial-gradient(circle at 65% 35%, rgb(20 91 208 / 17%), transparent 30%), radial-gradient(circle at 8% 77%, rgb(0 211 179 / 9%), transparent 27%), linear-gradient(125deg, #020711, #030b1b 52%, #020814); }
.register-portal::before, .register-portal::after { display: none; }
.register-grid { position: fixed; inset: 0; pointer-events: none; background-image: linear-gradient(rgb(42 107 202 / 4%) 1px, transparent 1px), linear-gradient(90deg, rgb(42 107 202 / 4%) 1px, transparent 1px); background-size: 54px 54px; mask-image: linear-gradient(to bottom, transparent, #000 28%, #000 75%, transparent); }
.register-grid::after { position: absolute; top: 13%; right: 3%; width: 160px; height: 120px; content: ''; opacity: .35; background-image: radial-gradient(circle, #1266da 1.5px, transparent 1.5px); background-size: 14px 14px; }
.register-orbit { position: fixed; right: -280px; bottom: -110px; width: 820px; height: 280px; border: 1px solid rgb(31 104 255 / 18%); border-radius: 50%; pointer-events: none; transform: rotate(-11deg); box-shadow: 0 0 0 55px rgb(31 104 255 / 2%), 0 0 0 110px rgb(31 104 255 / 2%); }
.register-header { position: relative; z-index: 3; display: flex; align-items: center; justify-content: space-between; height: 78px; padding: 0 clamp(24px, 3.5vw, 66px); column-gap: clamp(48px, 12vw, 220px); border-bottom: 1px solid rgb(115 153 215 / 18%); background: rgb(2 7 17 / 62%); backdrop-filter: blur(18px); }
.register-logo { display: flex; flex-shrink: 0; align-items: center; gap: 13px; color: #f6f9ff; font-size: clamp(18px, 1.55vw, 25px); text-decoration: none; letter-spacing: -.5px; }
.register-logo-symbol { position: relative; display: inline-block; width: 36px; height: 34px; }.register-logo-symbol i { position: absolute; bottom: 0; width: 13px; height: 32px; clip-path: polygon(50% 0, 100% 100%, 0 100%); }.register-logo-symbol i:nth-child(1) { left: 0; background: linear-gradient(#15e0bf, #06b6d4); }.register-logo-symbol i:nth-child(2) { left: 11px; height: 34px; background: linear-gradient(#2579ff, #0fc8ef); }.register-logo-symbol i:nth-child(3) { right: 0; height: 21px; background: linear-gradient(#8059ff, #367cff); }
.register-header p { flex-shrink: 0; margin: 0 0 0 auto; color: #aeb9cd; font-size: 15px; }.register-header p a { margin-left: 6px; color: #20e2bb; font-weight: 650; text-decoration: none; }.register-header p a span { display: inline-block; margin-left: 5px; transition: transform .2s ease; }.register-header p a:hover span { transform: translateX(4px); }
.register-content { position: relative; z-index: 2; display: grid; grid-template-columns: minmax(0, 1.4fr) minmax(440px, .82fr); align-items: center; gap: clamp(45px, 7vw, 110px); width: min(1480px, calc(100% - 80px)); min-height: calc(100vh - 108px); margin: 0 auto; padding: 30px 0 46px; }
.register-hero { min-width: 0; }.register-eyebrow { display: inline-flex; align-items: center; gap: 9px; padding: 8px 17px; border: 1px solid #00cfa9; border-radius: 999px; color: #e6fff8; background: rgb(1 29 38 / 64%); font-size: 14px; }.register-eyebrow i { color: #23e8c1; font-style: normal; }
.register-hero h1 { margin: 22px 0 13px; color: #f7f9fd; font-size: clamp(38px, 3.3vw, 58px); line-height: 1.25; letter-spacing: -2px; }.register-hero h1 em { color: #24ddbd; font-style: normal; }.register-hero h1 span { margin-left: .22em; color: #2d89ff; }.register-hero > p { margin: 0; color: #99a8c1; font-size: 16px; line-height: 1.75; }
.register-benefits { display: grid; grid-template-columns: repeat(3, 1fr); gap: 18px; margin: 24px 0; }.register-benefits article { display: flex; align-items: center; gap: 11px; min-width: 0; }.register-benefits article > span { display: grid; flex: 0 0 43px; width: 43px; height: 43px; place-items: center; border: 1px solid; border-radius: 50%; font-size: 18px; }.benefit-green { border-color: rgb(0 218 181 / 45%) !important; color: #22dfbd; background: rgb(0 184 155 / 8%); }.benefit-blue { border-color: rgb(37 116 255 / 55%) !important; color: #3486ff; background: rgb(37 116 255 / 8%); }.benefit-purple { border-color: rgb(125 84 255 / 55%) !important; color: #8c66ff; background: rgb(125 84 255 / 8%); }.register-benefits div { display: grid; min-width: 0; gap: 3px; }.register-benefits strong { color: #edf3ff; font-size: 13px; white-space: nowrap; }.register-benefits small { color: #7e90ad; font-size: 10px; line-height: 1.4; }
.path-preview { position: relative; width: 92%; padding: 19px; overflow: hidden; border: 1px solid rgb(33 127 255 / 58%); border-radius: 14px; background: linear-gradient(145deg, rgb(7 28 63 / 95%), rgb(4 17 41 / 96%)); box-shadow: 0 22px 65px rgb(0 39 119 / 45%), inset 0 0 35px rgb(30 101 220 / 7%); }.path-preview::before { position: absolute; inset: 0; content: ''; background: linear-gradient(90deg, transparent, rgb(36 127 255 / 5%), transparent); transform: skewX(-25deg) translateX(-70%); }.path-preview-head { position: relative; display: grid; grid-template-columns: 1fr auto; gap: 4px; }.path-preview-head > span { grid-column: 1 / -1; color: #238bff; font-size: 8px; letter-spacing: 1.5px; }.path-preview-head > span i { display: inline-block; width: 5px; height: 5px; margin-right: 6px; border-radius: 50%; background: #1addba; box-shadow: 0 0 7px #1addba; }.path-preview-head strong { color: #dce9fb; font-size: 14px; }.path-preview-head em { color: #17dab5; font-size: 14px; font-style: normal; font-weight: 750; }.path-preview-track { height: 4px; margin: 12px 0 15px; overflow: hidden; border-radius: 5px; background: #112544; }.path-preview-track i { display: block; width: 72%; height: 100%; background: linear-gradient(90deg, #1a7fff, #17d8b5); box-shadow: 0 0 10px #168fe6; }
.path-preview-stages { display: grid; grid-template-columns: 1fr 18px 1fr 18px 1fr 18px 1fr; align-items: center; gap: 5px; }.path-preview-stages > b { color: #315f99; font-size: 13px; text-align: center; }.path-preview-stages article { display: grid; min-height: 88px; padding: 10px; border: 1px solid rgb(52 100 163 / 33%); border-radius: 8px; background: rgb(5 20 47 / 75%); }.path-preview-stages article > span { color: #48607f; font-size: 8px; }.path-preview-stages article strong { margin-top: 4px; color: #aebed4; font-size: 10px; }.path-preview-stages article small { color: #647896; font-size: 8px; }.path-preview-stages article em { margin-top: auto; color: #5d7190; font-size: 7px; font-style: normal; }.path-preview-stages article.done { border-color: rgb(18 210 177 / 32%); background: rgb(7 77 74 / 23%); }.path-preview-stages article.done em { color: #16d7b4; }.path-preview-stages article.active { border-color: #217df3; box-shadow: inset 0 0 20px rgb(28 112 227 / 12%); }.path-preview-stages article.active em { color: #298fff; }.path-preview-data { display: flex; gap: 22px; margin-top: 15px; padding-top: 12px; border-top: 1px solid rgb(57 103 166 / 23%); color: #6d819e; font-size: 8px; }.path-preview-data strong { color: #25a0f7; }
.register-card { position: relative; width: 100%; max-width: 520px; justify-self: end; overflow: visible; border: 1px solid rgb(27 117 255 / 76%); border-radius: 15px; background: linear-gradient(145deg, rgb(8 27 61 / 95%), rgb(4 17 42 / 96%)); box-shadow: 0 28px 80px rgb(0 0 0 / 42%), 0 0 48px rgb(20 93 226 / 14%), inset 0 0 55px rgb(33 101 206 / 7%); }.register-card::before { display: none; }.register-card :deep(.el-card__body) { padding: clamp(28px, 2.7vw, 42px); }.register-card-dots { position: absolute; top: 10px; right: 10px; width: 120px; height: 80px; opacity: .18; background-image: radial-gradient(circle, #2386ff 1px, transparent 1px); background-size: 11px 11px; }.register-status { display: inline-flex; align-items: center; gap: 7px; color: #5f83b4; font-size: 9px; letter-spacing: 1.7px; }.register-status i { width: 6px; height: 6px; border-radius: 50%; background: #1fe0ba; box-shadow: 0 0 8px #1fe0ba; }.register-card h2 { margin: 13px 0 5px; color: #f4f8ff; font-size: 26px; }.register-subtitle { margin: 0 0 20px; color: #8798b5; font-size: 13px; }.register-form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }.register-card :deep(.el-form-item) { margin-bottom: 15px; }.register-card :deep(.el-form-item__label) { height: auto; padding-bottom: 7px; color: #dce6f8; font-size: 12px; line-height: 1.2; }.register-card :deep(.el-input__wrapper) { min-height: 45px; padding: 0 13px; border: 1px solid rgb(80 119 181 / 30%); border-radius: 8px; background: rgb(8 22 49 / 88%); box-shadow: none; transition: border-color .2s ease, box-shadow .2s ease; }.register-card :deep(.el-input__wrapper:hover) { border-color: rgb(58 132 245 / 58%); }.register-card :deep(.el-input__wrapper.is-focus) { border-color: #247cff; box-shadow: 0 0 0 3px rgb(36 124 255 / 12%); }.register-card :deep(.el-input__inner) { color: #edf5ff; font-size: 13px; }.register-card :deep(.el-input__inner::placeholder) { color: #647692; }.field-icon { width: 18px; margin-right: 5px; color: #7f94b5; text-align: center; }.register-privacy { display: flex; align-items: center; gap: 7px; margin: 0 0 14px; color: #7084a3; font-size: 11px; }.register-privacy span { color: #1cceae; }.register-card .register-submit { position: relative; display: flex; width: 100%; min-height: 48px; border: 0; border-radius: 8px; color: white; background: linear-gradient(100deg, #225dff, #238ef6 50%, #20d8b3); box-shadow: 0 12px 30px rgb(27 103 241 / 27%); font-weight: 700; }.register-card .register-submit:hover { color: white; filter: brightness(1.08); transform: translateY(-1px); }.register-submit b { position: absolute; right: 20px; font-size: 20px; }.register-divider { display: flex; align-items: center; gap: 14px; margin: 20px 0 14px; color: #6c7e99; font-size: 12px; }.register-divider::before, .register-divider::after { flex: 1; height: 1px; content: ''; background: rgb(94 122 164 / 24%); }.login-entry { display: block; padding: 12px; border: 1px solid rgb(94 122 164 / 28%); border-radius: 8px; color: #8798b5 !important; text-align: center; text-decoration: none; }.login-entry strong { margin-left: 7px; color: #21ddb8; }.login-entry:hover { border-color: rgb(32 211 177 / 46%); background: rgb(32 211 177 / 4%); }.register-footer { position: absolute; z-index: 2; right: 0; bottom: 11px; left: 0; color: #3f526e; font-size: 9px; text-align: center; letter-spacing: 2px; }
@media (max-width: 1180px) { .register-content { grid-template-columns: minmax(0, 1fr) minmax(430px, .9fr); gap: 40px; width: min(1120px, calc(100% - 48px)); }.register-hero h1 { font-size: 42px; }.register-benefits small { display: none; }.path-preview { width: 100%; }.path-preview-stages article { padding: 8px; } }
@media (max-width: 940px) { .register-content { grid-template-columns: 1fr; width: min(620px, calc(100% - 40px)); padding: 45px 0 68px; }.register-hero { text-align: center; }.register-hero > p br { display: none; }.register-benefits { max-width: 570px; margin-right: auto; margin-left: auto; text-align: left; }.path-preview { display: none; }.register-card { max-width: 550px; justify-self: center; }.register-footer { position: relative; padding-bottom: 18px; } }
@media (max-width: 620px) { .register-header { height: 66px; padding: 0 18px; column-gap: 20px; }.register-logo { gap: 7px; font-size: 16px; }.register-logo-symbol { width: 28px; height: 27px; transform: scale(.82); transform-origin: left center; }.register-header p { font-size: 12px; text-align: right; }.register-content { width: calc(100% - 28px); min-height: calc(100vh - 66px); padding: 34px 0 60px; gap: 27px; }.register-eyebrow { padding: 7px 13px; font-size: 12px; }.register-hero h1 { margin-top: 17px; font-size: clamp(31px, 10vw, 40px); line-height: 1.24; }.register-hero > p { font-size: 14px; }.register-benefits { grid-template-columns: 1fr; max-width: 290px; gap: 9px; }.register-benefits article { padding: 7px 9px; border: 1px solid rgb(91 129 191 / 14%); border-radius: 9px; background: rgb(5 20 45 / 46%); }.register-benefits article > span { flex-basis: 36px; width: 36px; height: 36px; }.register-benefits small { display: block; }.register-card :deep(.el-card__body) { padding: 26px 20px; }.register-form-grid { grid-template-columns: 1fr; gap: 0; } }
@media (max-width: 390px) { .register-header p { max-width: 105px; }.register-hero h1 { font-size: 30px; } }
</style>
