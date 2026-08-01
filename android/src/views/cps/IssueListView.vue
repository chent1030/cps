<template>
  <main class="cps-page cps-list-page">
    <header class="cps-hero">
      <div class="cps-hero__main">
        <p class="cps-hero__eyebrow">问题闭环工作台</p>
        <h1>我的问题</h1>
        <div class="cps-hero__stats">
          <span>待处理 {{ todoCount }}</span>
          <span>超时 {{ overdueCount }}</span>
        </div>
      </div>
      <a-button type="primary" size="large" class="cps-hero__action" @click="navigateToCreate">
        <template #icon><plus-outlined /></template>
        新建
      </a-button>
    </header>

    <a-card :body-style="{ padding: '0' }" class="cps-tabs-card">
      <a-tabs v-model:activeKey="tab" class="cps-list-tabs" @change="onTabChange">
        <a-tab-pane v-for="item in tabs" :key="item.value" :tab="item.label" />
      </a-tabs>
    </a-card>

    <a-spin :spinning="loading">
      <div class="cps-list-grid">
        <a-empty v-if="!items.length && !loading" description="暂无问题" class="cps-list-empty" />
        <a-row :gutter="[16, 16]">
          <a-col
            v-for="issue in items"
            :key="issue.id"
            :xs="{ span: 24 }"
            :sm="{ span: 12 }"
            :lg="{ span: 8 }"
            :xl="{ span: 6 }"
          >
            <a-card hoverable class="cps-issue-card" :class="statusMeta[issue.status].rail" @click="navigateToDetail(issue.id)">
              <div class="cps-issue-card__head">
                <div class="cps-issue-card__identity">
                  <span class="cps-issue-card__eyebrow">巡检单</span>
                  <strong class="cps-issue-card__no">{{ issue.issueNo }}</strong>
                </div>
                <div class="cps-issue-card__badges">
                  <a-tag :color="statusMeta[issue.status].color" class="cps-pill">{{ statusMeta[issue.status].label }}</a-tag>
                  <a-tag v-if="issue.overdue" color="red" class="cps-pill">超时</a-tag>
                </div>
              </div>

              <p class="cps-issue-card__desc">{{ issue.description }}</p>

              <div class="cps-issue-card__meta">
                <span><b>位置</b>{{ issueLocation(issue) }}</span>
                <span><b>分类</b>{{ issueCategory(issue) }}</span>
              </div>

              <div class="cps-issue-card__foot">
                <span>处理人 {{ issue.currentHandlerEmpName || issue.currentHandlerEmpNo || '-' }}</span>
                <time>{{ issue.submitTime }}</time>
              </div>
            </a-card>
          </a-col>
        </a-row>
      </div>
    </a-spin>
  </main>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'

import { listCpsIssues } from '@/api/cps/issue'
import type { CpsIssueListItem, CpsIssueTab } from '@/types/cps'
import { statusMeta, issueLocation, issueCategory } from '@/utils/format'
import { useListCacheStore } from '@/store/listCache'

interface TabItem {
  value: CpsIssueTab
  label: string
}

const tabs: TabItem[] = [
  { value: 'todo', label: '待我处理' },
  { value: 'created', label: '我发起的' },
  { value: 'related', label: '我参与的' },
  { value: 'closed', label: '已关闭' },
]

const listCache = useListCacheStore()

const tab = ref<CpsIssueTab>('todo')
const items = ref<CpsIssueListItem[]>([])
const loading = ref<boolean>(false)

const todoCount = computed<number>(() => items.value.filter((item) => item.status !== 'CLOSED').length)
const overdueCount = computed<number>(() => items.value.filter((item) => item.overdue).length)

const navigateToCreate = () => {
  uni.navigateTo({ url: '/views/cps/IssueCreateView' })
}

const navigateToDetail = (id: number) => {
  uni.navigateTo({ url: `/views/cps/IssueDetailView?id=${id}` })
}

const load = async (force = false) => {
  // 命中缓存（且非强制刷新）时直接回显，再后台静默更新
  const cached = listCache.get(tab.value)
  const fresh = force ? false : listCache.age(tab.value) > 0 && listCache.age(tab.value) < 30_000
  if (cached.length || fresh) {
    items.value = cached
  }
  loading.value = true
  try {
    const result = await listCpsIssues({ tab: tab.value, page: 1, pageSize: 50 })
    items.value = result
    listCache.set(tab.value, result)
  } catch (error) {
    message.error(error instanceof Error ? error.message : '加载失败')
  } finally {
    loading.value = false
  }
}

const onTabChange = () => {
  void load()
}

// onShow：从详情页返回时，当前 tab 列表可能已变化，强制刷新一次
const onShowHandler = () => {
  void load(true)
}

watch(tab, () => load(), { immediate: true })

// uni-app 页面生命周期：每次显示页面触发
import { onShow } from '@dcloudio/uni-app'
onShow(() => {
  if (items.value.length) onShowHandler()
})
</script>

<style scoped>
.cps-page {
  width: 100%;
  max-width: 1280px;
  margin: 0 auto;
  padding: 16px 20px 40px;
}

.cps-hero {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  overflow: hidden;
  border: 1px solid rgba(20, 184, 166, 0.22);
  border-radius: 16px;
  padding: 20px 24px 20px 28px;
  background: linear-gradient(135deg, rgba(20, 184, 166, 0.96) 0%, rgba(37, 99, 235, 0.92) 66%, rgba(34, 197, 94, 0.82) 100%);
  box-shadow: 0 12px 32px rgba(15, 23, 42, 0.15);
  color: #fff;
  margin-bottom: 16px;
}

.cps-hero__main {
  display: grid;
  gap: 6px;
}

.cps-hero__eyebrow {
  margin: 0;
  color: rgba(255, 255, 255, 0.82);
  font-size: 13px;
  font-weight: 800;
}

.cps-hero h1 {
  margin: 0;
  font-size: 28px;
  font-weight: 800;
}

.cps-hero__stats {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.cps-hero__stats span {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  border-radius: 999px;
  padding: 0 10px;
  background: rgba(255, 255, 255, 0.18);
  font-size: 13px;
  font-weight: 700;
}

.cps-hero__action {
  background: #fff !important;
  color: #0f766e !important;
  border: none !important;
  font-weight: 800 !important;
}

.cps-tabs-card {
  margin-bottom: 16px;
  border-radius: 12px;
  overflow: hidden;
}

.cps-list-tabs :deep(.ant-tabs-nav) {
  margin: 0;
  padding: 0 16px;
}

.cps-list-grid {
  min-height: 120px;
}

.cps-list-empty {
  padding: 48px 0;
}

.cps-issue-card {
  position: relative;
  overflow: hidden;
  border-radius: 12px;
  border: 1px solid rgba(20, 184, 166, 0.16);
  transition: transform 120ms ease;
}

.cps-issue-card:hover {
  transform: translateY(-2px);
}

.cps-issue-card::before {
  position: absolute;
  top: 0;
  bottom: 0;
  left: 0;
  width: 5px;
  content: '';
  background: #14b8a6;
}

.cps-rail--blue::before {
  background: linear-gradient(180deg, #2563eb, #60a5fa);
}
.cps-rail--teal::before {
  background: linear-gradient(180deg, #14b8a6, #2dd4bf);
}
.cps-rail--orange::before {
  background: linear-gradient(180deg, #f97316, #fdba74);
}
.cps-rail--green::before {
  background: linear-gradient(180deg, #22c55e, #86efac);
}

.cps-issue-card :deep(.ant-card-body) {
  padding: 16px 16px 16px 20px;
}

.cps-issue-card__head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 10px;
}

.cps-issue-card__eyebrow {
  display: block;
  margin-bottom: 2px;
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.cps-issue-card__no {
  display: block;
  color: #0f172a;
  font-size: 16px;
  font-weight: 800;
  word-break: break-all;
}

.cps-issue-card__badges {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  justify-content: flex-end;
}

.cps-pill {
  border-radius: 999px !important;
  font-weight: 700 !important;
  margin: 0 !important;
}

.cps-issue-card__desc {
  margin: 0 0 10px;
  color: #0f172a;
  font-size: 14px;
  font-weight: 600;
  line-height: 22px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.cps-issue-card__meta {
  display: grid;
  gap: 6px;
  padding: 10px;
  border-radius: 10px;
  background: linear-gradient(135deg, #f0fdfa 0%, #eff6ff 100%);
  color: #475569;
  font-size: 13px;
  line-height: 18px;
}

.cps-issue-card__meta b {
  display: block;
  color: #0f766e;
  font-size: 11px;
  font-weight: 800;
}

.cps-issue-card__foot {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 10px;
  border-top: 1px solid #e2e8f0;
  padding-top: 10px;
  color: #64748b;
  font-size: 12px;
  font-weight: 600;
}

/* 平板横屏：卡片更紧凑，列表 hero 缩小 */
@media (min-width: 1024px) {
  .cps-page {
    padding: 20px 28px 48px;
  }
  .cps-hero h1 {
    font-size: 24px;
  }
}
</style>
