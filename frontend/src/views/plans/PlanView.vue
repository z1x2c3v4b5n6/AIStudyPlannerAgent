<script setup lang="ts">
import { ref } from 'vue'
import PlanDraftPanel from '../../components/plans/PlanDraftPanel.vue'
import PlanHistoryTable from '../../components/plans/PlanHistoryTable.vue'
import PlanDetailDrawer from '../../components/plans/PlanDetailDrawer.vue'
import type { PlanDetail } from '../../types/plan'

const history = ref<InstanceType<typeof PlanHistoryTable>>()
const detail = ref<PlanDetail | null>(null)
const visible = ref(false)

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
    <PlanHistoryTable ref="history" @detail="open" />
    <PlanDetailDrawer v-model="visible" :plan="detail" @changed="changed" />
  </section>
</template>
