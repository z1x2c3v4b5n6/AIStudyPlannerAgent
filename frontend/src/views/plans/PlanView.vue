<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { planApi } from '../../api/plan'
import PlanDraftPanel from '../../components/plans/PlanDraftPanel.vue'
import PlanHistoryTable from '../../components/plans/PlanHistoryTable.vue'
import PlanDetailDrawer from '../../components/plans/PlanDetailDrawer.vue'
import type { PlanDetail } from '../../types/plan'

const history = ref<InstanceType<typeof PlanHistoryTable>>()
const detail = ref<PlanDetail | null>(null)
const visible = ref(false)
const activeTab = ref<'today' | 'history'>('today')
const route = useRoute()
const router = useRouter()

function open(plan: PlanDetail) {
  detail.value = plan
  visible.value = true
}

function changed(plan: PlanDetail) {
  detail.value = plan
  history.value?.load()
}

function confirmed(plan: PlanDetail) {
  if (history.value) history.value.load()
  open(plan)
}

async function switchToHistory() {
  activeTab.value = 'history'
  await nextTick()
  await history.value?.load()
}

function handleTabChange(name: string | number) {
  if (name === 'history') void switchToHistory()
}

onMounted(async () => {
  const planId = Number(route.query.planId)
  if (!Number.isInteger(planId) || planId <= 0) return
  try {
    activeTab.value = 'history'
    await nextTick()
    open((await planApi.get(planId)).data.data)
  } finally {
    await router.replace({ path: '/plans' })
  }
})
</script>

<template>
  <section class="page-section">
    <div class="page-heading">
      <div>
        <h1>每日计划</h1>
        <p>安排今天什么时候学；长期学习顺序请前往“学习路径”。</p>
      </div>
    </div>
    <el-tabs v-model="activeTab" class="plan-workspace-tabs" @tab-change="handleTabChange">
      <el-tab-pane label="今日计划" name="today">
        <PlanDraftPanel @confirmed="confirmed" />
      </el-tab-pane>
      <el-tab-pane label="历史计划" name="history" lazy>
        <div class="history-section-heading compact">
          <h2>历史学习计划</h2>
          <p>查看已确认计划及执行进度</p>
        </div>
        <PlanHistoryTable ref="history" @detail="open" />
      </el-tab-pane>
    </el-tabs>
    <PlanDetailDrawer v-model="visible" :plan="detail" @changed="changed" />
  </section>
</template>
