<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { planApi } from '../../api/plan'
import PlanDraftPanel from '../../components/plans/PlanDraftPanel.vue'
import PlanHistoryTable from '../../components/plans/PlanHistoryTable.vue'
import PlanDetailDrawer from '../../components/plans/PlanDetailDrawer.vue'
import type { PlanDetail } from '../../types/plan'

const history = ref<InstanceType<typeof PlanHistoryTable>>()
const detail = ref<PlanDetail | null>(null)
const visible = ref(false)
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
  history.value?.load()
  open(plan)
}

onMounted(async () => {
  const planId = Number(route.query.planId)
  if (!Number.isInteger(planId) || planId <= 0) return
  try {
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
        <h1>学习计划</h1>
        <p>描述你今天想怎么学，AI 会从现有待办任务中整理出可执行的时间安排。</p>
      </div>
    </div>
    <PlanDraftPanel @confirmed="confirmed" />
    <div class="history-section-heading">
      <h2>历史学习计划</h2>
      <p>查看已确认计划及执行进度</p>
    </div>
    <PlanHistoryTable ref="history" @detail="open" />
    <PlanDetailDrawer v-model="visible" :plan="detail" @changed="changed" />
  </section>
</template>
