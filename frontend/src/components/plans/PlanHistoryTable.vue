<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { planApi } from '../../api/plan'
import type { PlanDetail, PlanListItem, PlanStatus } from '../../types/plan'
import { minutesLabel } from '../../utils/display'

const emit = defineEmits<{
  detail: [plan: PlanDetail]
}>()

const rows = ref<PlanListItem[]>([])
const total = ref(0)
const loading = ref(false)
const dateRange = ref<string[] | null>(null)
const query = reactive({
  page: 1,
  pageSize: 10,
  status: undefined as PlanStatus | undefined
})

const labels: Record<PlanStatus, string> = {
  CONFIRMED: '已确认',
  COMPLETED: '全部完成',
  PARTIALLY_COMPLETED: '部分完成',
  ABANDONED: '已放弃',
  CANCELLED: '已取消'
}

async function load() {
  loading.value = true
  try {
    const page = (
      await planApi.list({
        page: query.page,
        pageSize: query.pageSize,
        startDate: dateRange.value?.[0] || undefined,
        endDate: dateRange.value?.[1] || undefined,
        status: query.status
      })
    ).data.data
    rows.value = page.list
    total.value = page.total
  } finally {
    loading.value = false
  }
}

async function detail(row: PlanListItem) {
  emit('detail', (await planApi.get(row.id)).data.data)
}

async function cancel(row: PlanListItem) {
  try {
    await ElMessageBox.confirm('取消后不能继续更新计划项，确定吗？', '取消计划', {
      type: 'warning'
    })
    await planApi.changeStatus(row.id, 'CANCELLED')
    ElMessage.success('计划已取消')
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      // 请求异常由统一响应拦截器处理。
    }
  }
}

function search() {
  query.page = 1
  load()
}

function reset() {
  dateRange.value = null
  query.status = undefined
  query.page = 1
  load()
}

function goToGenerator() {
  document.querySelector('.natural-plan-entry')?.scrollIntoView({
    behavior: 'smooth',
    block: 'start'
  })
}

defineExpose({ load })
onMounted(load)
</script>

<template>
  <el-card shadow="never" class="panel-card plan-history">
    <div class="filter-bar history-filter-bar">
      <el-date-picker
        v-model="dateRange"
        type="daterange"
        value-format="YYYY-MM-DD"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
      />
      <el-select v-model="query.status" clearable placeholder="全部状态">
        <el-option
          v-for="(label, status) in labels"
          :key="status"
          :label="label"
          :value="status"
        />
      </el-select>
      <el-button type="primary" @click="search">查询</el-button>
      <el-button @click="reset">重置</el-button>
    </div>

    <div class="table-scroll">
      <el-table v-loading="loading" :data="rows">
        <el-table-column prop="planDate" label="计划日期" width="115" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag>{{ labels[row.status as PlanStatus] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="执行情况" min-width="210">
          <template #default="{ row }">
            <div class="history-counts">
              <span>完成 {{ row.completedItemCount }}</span>
              <span>待执行 {{ row.pendingItemCount }}</span>
              <span>跳过 {{ row.skippedItemCount }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="计划时长" width="110">
          <template #default="{ row }">
            {{ minutesLabel(row.plannedMinutes) }}
          </template>
        </el-table-column>
        <el-table-column label="实际学习" width="110">
          <template #default="{ row }">
            {{ minutesLabel(row.actualStudyMinutes) }}
          </template>
        </el-table-column>
        <el-table-column label="完成进度" min-width="160">
          <template #default="{ row }">
            <el-progress :percentage="row.completionPercentage" />
          </template>
        </el-table-column>
        <el-table-column prop="summary" label="摘要" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="detail(row)">查看详情</el-button>
            <el-button
              v-if="row.status === 'CONFIRMED'"
              link
              type="danger"
              @click="cancel(row)"
            >
              取消
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="history-empty">
            <strong>还没有历史学习计划</strong>
            <span>生成并确认计划后，可在这里查看执行进度。</span>
            <el-button type="primary" @click="goToGenerator">生成学习计划</el-button>
          </div>
        </template>
      </el-table>
    </div>

    <div v-if="total > 0" class="pagination">
      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @change="load"
      />
    </div>
  </el-card>
</template>
