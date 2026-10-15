<template>
  <main class="cps-page cps-rank-page">
    <header class="cps-rank-hero">
      <div class="cps-rank-hero__main">
        <p class="cps-rank-hero__eyebrow">周评分</p>
        <h1>本周排名</h1>
        <p class="cps-rank-hero__sub">
          自然周（周一 00:00 至周日 23:59）：<strong>{{ weekRangeLabel }}</strong>
        </p>
      </div>
      <button
        type="button"
        class="cps-rank-hero__refresh"
        :disabled="loading"
        @click="reload"
      >
        刷新
      </button>
    </header>

    <van-loading v-if="loading" class="cps-page-loading" color="#14B8A6">
      加载中...
    </van-loading>
    <van-empty v-else-if="!records.length" description="本周暂无评分数据" />
    <section v-else class="cps-rank-list">
      <article
        v-for="row in records"
        :key="row.id"
        class="cps-rank-card"
        :class="{ 'cps-rank-card--top': (row.rank ?? 99) <= 3 }"
      >
        <div class="cps-rank-card__rank">
          <span class="cps-rank-card__rank-no">#{{ row.rank ?? '-' }}</span>
          <span v-if="(row.rank ?? 99) === 1" class="cps-rank-card__medal">🥇</span>
          <span v-else-if="(row.rank ?? 99) === 2" class="cps-rank-card__medal">🥈</span>
          <span v-else-if="(row.rank ?? 99) === 3" class="cps-rank-card__medal">🥉</span>
        </div>
        <div class="cps-rank-card__body">
          <div class="cps-rank-card__name">{{ row.empName || row.empNo }}</div>
          <div class="cps-rank-card__meta">
            <span>工号 {{ row.empNo }}</span>
            <span v-if="row.regionSupervisorName">· {{ row.regionSupervisorName }}</span>
            <span>· 点检 {{ row.roomCheckCount }} 单</span>
          </div>
        </div>
        <div class="cps-rank-card__score" :data-tone="scoreTone(row.totalScore)">
          {{ row.totalScore }}
        </div>
      </article>
    </section>

    <van-notice-bar
      v-if="error"
      class="cps-rank-notice"
      type="danger"
      :text="error"
      left-icon="warning-o"
    />
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import { listWeeklyScores } from '@/api/cps/score'
import type { CpsWeeklyScoreItem } from '@/api/cps/score'

/** B3 周评分排名（PRD §32 / §24）：按总分 DESC 展示本区域本周排名。 */

const props = withDefaults(
  defineProps<{
    /** 自然周周一日期（YYYY-MM-DD）。默认取本周一。 */
    weekStartDate?: string
    /** 区域督导 ID（可选；null=全部）。 */
    regionSupervisorId?: number
  }>(),
  { weekStartDate: undefined, regionSupervisorId: undefined },
)

const records = ref<CpsWeeklyScoreItem[]>([])
const loading = ref(false)
const error = ref('')

const naturalWeekStart = (date: Date): string => {
  const d = new Date(date.getTime())
  const dow = d.getDay() // 0=Sun..6=Sat
  const diff = dow === 0 ? -6 : 1 - dow
  d.setDate(d.getDate() + diff)
  return d.toISOString().slice(0, 10)
}

const weekStart = computed(() => {
  if (props.weekStartDate) return props.weekStartDate
  return naturalWeekStart(new Date())
})

const weekRangeLabel = computed(() => {
  const start = weekStart.value
  const endDate = new Date(start + 'T00:00:00Z')
  endDate.setUTCDate(endDate.getUTCDate() + 6)
  return `${start} 至 ${endDate.toISOString().slice(0, 10)}`
})

const scoreTone = (score: number): 'high' | 'mid' | 'low' => {
  if (score >= 85) return 'high'
  if (score >= 60) return 'mid'
  return 'low'
}

const fetchRanking = async () => {
  loading.value = true
  error.value = ''
  try {
    const page = await listWeeklyScores(
      weekStart.value,
      props.regionSupervisorId,
      1,
      50,
    )
    records.value = Array.isArray(page?.records) ? page.records : []
  } catch (caught) {
    error.value =
      caught instanceof Error ? caught.message : '加载失败，请稍后重试'
    records.value = []
  } finally {
    loading.value = false
  }
}

const reload = () => {
  void fetchRanking()
}

onMounted(() => {
  void fetchRanking()
})
</script>

<style scoped>
.cps-rank-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 16px;
  background: #f8fafc;
  min-height: 100vh;
}

.cps-rank-hero {
  background: linear-gradient(135deg, #0f766e 0%, #0e7490 100%);
  color: white;
  border-radius: 16px;
  padding: 20px;
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.cps-rank-hero__eyebrow {
  font-size: 12px;
  opacity: 0.85;
  margin: 0 0 4px;
}

.cps-rank-hero__main h1 {
  font-size: 22px;
  margin: 0 0 8px;
}

.cps-rank-hero__sub {
  margin: 0;
  font-size: 13px;
  opacity: 0.9;
}

.cps-rank-hero__refresh {
  background: rgba(255, 255, 255, 0.15);
  color: white;
  border: 1px solid rgba(255, 255, 255, 0.4);
  border-radius: 18px;
  padding: 6px 14px;
  font-size: 13px;
  cursor: pointer;
}

.cps-rank-hero__refresh:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.cps-rank-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.cps-rank-card {
  background: white;
  border-radius: 14px;
  padding: 14px 16px;
  display: grid;
  grid-template-columns: 64px 1fr 64px;
  gap: 12px;
  align-items: center;
  box-shadow: 0 4px 16px rgba(15, 118, 110, 0.06);
}

.cps-rank-card--top {
  border: 1.5px solid #14b8a6;
}

.cps-rank-card__rank {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.cps-rank-card__rank-no {
  font-size: 18px;
  font-weight: 700;
  color: #0f766e;
}

.cps-rank-card__medal {
  font-size: 22px;
  margin-top: 2px;
}

.cps-rank-card__name {
  font-size: 15px;
  font-weight: 600;
  color: #0f172a;
}

.cps-rank-card__meta {
  font-size: 12px;
  color: #64748b;
  margin-top: 4px;
}

.cps-rank-card__score {
  font-size: 24px;
  font-weight: 700;
  text-align: right;
}

.cps-rank-card__score[data-tone='high'] {
  color: #14b8a6;
}

.cps-rank-card__score[data-tone='mid'] {
  color: #d97706;
}

.cps-rank-card__score[data-tone='low'] {
  color: #dc2626;
}

.cps-rank-notice {
  border-radius: 12px;
}
</style>
