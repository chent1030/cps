<template>
  <main class="cps-page cps-list-page">
    <header class="cps-list-hero">
      <div class="cps-list-hero__main">
        <p class="cps-list-hero__eyebrow">问题闭环工作台</p>
        <h1>我的问题</h1>
        <div class="cps-list-hero__stats">
          <span>待处理 {{ todoCount }}</span>
          <span>超时 {{ overdueCount }}</span>
        </div>
      </div>
      <button type="button" class="cps-list-hero__action" @click="navigateToCreate">
        新建
      </button>
    </header>

    <section class="cps-list-tabs">
      <van-tabs
        v-model:active="tab"
        sticky
        :swipeable="false"
        color="#14B8A6"
        title-active-color="#0F766E"
        title-inactive-color="#64748B"
      >
        <van-tab v-for="item in tabs" :key="item.value" :name="item.value" :title="item.label" />
      </van-tabs>
    </section>

    <van-loading v-if="loading" class="cps-page-loading" color="#14B8A6">加载中...</van-loading>
    <section v-else class="cps-list">
      <van-empty v-if="!items.length" description="暂无问题" />
      <article
        v-for="item in items"
        :key="item.id"
        class="cps-list-card cps-issue-card"
        :class="issueStatus(item).rail"
        role="button"
        tabindex="0"
        @click="navigateToDetail(item.id)"
        @keydown.enter="navigateToDetail(item.id)"
      >
        <header class="cps-issue-card__head">
          <div class="cps-issue-card__identity">
            <span class="cps-issue-card__eyebrow">巡检单</span>
            <strong class="cps-issue-card__no">{{ item.issueNo }}</strong>
          </div>
          <div class="cps-issue-card__badges">
            <span class="cps-status-pill" :class="issueStatus(item).tone">{{ issueStatus(item).label }}</span>
            <span v-if="item.overdue" class="cps-status-pill cps-status-pill--red">超时</span>
          </div>
        </header>

        <p class="cps-issue-card__desc">{{ item.description }}</p>

        <div class="cps-issue-card__meta">
          <span><b>位置</b>{{ issueLocation(item) }}</span>
          <span><b>分类</b>{{ issueCategory(item) }}</span>
        </div>

        <footer class="cps-issue-card__foot">
          <span>处理人 {{ item.currentHandlerEmpName || item.currentHandlerEmpNo || '-' }}</span>
          <time>{{ item.submitTime }}</time>
        </footer>
      </article>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import { listCpsIssues } from '@/api/cps/issue'
import type { CpsIssueListItem, CpsIssueStatus, CpsIssueTab } from '@/types/cps'

interface TabItem {
  value: CpsIssueTab
  label: string
}

interface StatusMeta {
  label: string
  tone: string
  rail: string
}

const tabs: TabItem[] = [
  { value: 'todo', label: '待我处理' },
  { value: 'created', label: '我发起的' },
  { value: 'related', label: '我参与的' },
  { value: 'closed', label: '已关闭' },
]

const tab = ref<CpsIssueTab>('todo')
const items = ref<CpsIssueListItem[]>([])
const loading = ref<boolean>(false)

const statusMeta: Record<CpsIssueStatus, StatusMeta> = {
  PENDING_FEEDBACK: {
    label: '待反馈',
    tone: 'cps-status-pill--blue',
    rail: 'cps-issue-card--blue',
  },
  PENDING_RECTIFY: {
    label: '待整改',
    tone: 'cps-status-pill--orange',
    rail: 'cps-issue-card--orange',
  },
  PENDING_UPLOAD_PROOF: {
    label: '待传图',
    tone: 'cps-status-pill--orange',
    rail: 'cps-issue-card--orange',
  },
  PENDING_REVIEW: {
    label: '待审核',
    tone: 'cps-status-pill--teal',
    rail: 'cps-issue-card--teal',
  },
  CLOSED: {
    label: '已关闭',
    tone: 'cps-status-pill--green',
    rail: 'cps-issue-card--green',
  },
}

const todoCount = computed<number>(() => items.value.filter((item) => item.status !== 'CLOSED').length)
const overdueCount = computed<number>(() => items.value.filter((item) => item.overdue).length)

const issueStatus = (item: CpsIssueListItem): StatusMeta => {
  return statusMeta[item.status]
}

const issueLocation = (item: CpsIssueListItem): string => {
  return [item.factoryName ?? item.factory, item.areaName ?? item.area, item.lineName ?? item.line, item.processName ?? item.process]
    .filter(Boolean)
    .join(' / ') || '未填写位置'
}

const issueCategory = (item: CpsIssueListItem): string => {
  return [item.categoryL1Name, item.categoryL2Name].filter(Boolean).join(' / ') || '未分类'
}

const navigateToCreate = (): void => {
  uni.navigateTo({ url: '/views/cps/IssueCreateView' })
}

const navigateToDetail = (id: number): void => {
  uni.navigateTo({ url: `/views/cps/IssueDetailView?id=${id}` })
}

const load = async (): Promise<void> => {
  loading.value = true
  try {
    items.value = await listCpsIssues({ tab: tab.value, page: 1, pageSize: 20 })
  } finally {
    loading.value = false
  }
}

watch(tab, load, { immediate: true })
</script>

<style scoped>
.cps-page,
.cps-page *,
.cps-page *::before,
.cps-page *::after {
  box-sizing: border-box;
}

.cps-page :deep(*),
.cps-page :deep(*::before),
.cps-page :deep(*::after) {
  box-sizing: border-box;
}

.cps-page {
  width: 100%;
  max-width: 100%;
  min-height: 100dvh;
  margin: 0;
  padding: 28rpx 24rpx 152rpx;
  overflow-x: hidden;
  touch-action: pan-y;
}

.cps-page > * {
  width: 100%;
  max-width: 100%;
  min-width: 0;
}

.cps-list-page {
  display: grid;
  align-content: start;
  gap: 26rpx;
}

.cps-list-hero {
  position: relative;
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 22rpx;
  overflow: hidden;
  border: 2rpx solid rgba(20, 184, 166, 0.22);
  border-radius: 22rpx;
  padding: 34rpx 32rpx 32rpx 40rpx;
  background:
    linear-gradient(135deg, rgba(20, 184, 166, 0.96) 0%, rgba(37, 99, 235, 0.92) 66%, rgba(34, 197, 94, 0.82) 100%),
    #14b8a6;
  box-shadow: 0 24rpx 70rpx rgba(15, 23, 42, 0.15);
  color: #ffffff;
}

.cps-list-hero::before {
  position: absolute;
  top: 28rpx;
  bottom: 28rpx;
  left: 20rpx;
  width: 8rpx;
  border-radius: 999rpx;
  background: #f97316;
  content: "";
}

.cps-list-hero__main {
  display: grid;
  gap: 12rpx;
  min-width: 0;
  max-width: 100%;
}

.cps-list-hero__eyebrow {
  margin: 0;
  color: rgba(255, 255, 255, 0.82);
  font-size: 26rpx;
  font-weight: 900;
  line-height: 36rpx;
}

.cps-list-hero h1 {
  margin: 0;
  color: #ffffff;
  font-size: 46rpx;
  font-weight: 950;
  line-height: 58rpx;
  overflow-wrap: anywhere;
}

.cps-list-hero__stats {
  display: flex;
  flex-wrap: wrap;
  gap: 10rpx;
  min-width: 0;
  max-width: 100%;
}

.cps-list-hero__stats span {
  display: inline-flex;
  align-items: center;
  min-height: 48rpx;
  border-radius: 999rpx;
  padding: 0 16rpx;
  background: rgba(255, 255, 255, 0.16);
  color: #ffffff;
  font-size: 26rpx;
  font-weight: 900;
  line-height: 36rpx;
  white-space: nowrap;
}

.cps-list-hero__action {
  flex: 0 0 auto;
  min-height: 84rpx;
  border: 0;
  border-radius: 999rpx;
  padding: 0 30rpx;
  background: #ffffff;
  color: #0f766e;
  font-size: 32rpx;
  font-weight: 950;
  box-shadow: 0 14rpx 34rpx rgba(15, 23, 42, 0.14);
}

.cps-list-tabs {
  width: 100%;
  min-width: 0;
  max-width: 100%;
  overflow: hidden;
  border: 2rpx solid rgba(20, 184, 166, 0.14);
  border-radius: 18rpx;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 14rpx 42rpx rgba(15, 23, 42, 0.06);
}

.cps-list-tabs :deep(.van-tabs),
.cps-list-tabs :deep(.van-tabs__wrap),
.cps-list-tabs :deep(.van-tabs__content),
.cps-list-tabs :deep(.van-tabs__track),
.cps-list-tabs :deep(.van-tab__panel) {
  width: 100%;
  max-width: 100%;
  overflow-x: hidden;
}

.cps-list-tabs :deep(.van-tab) {
  min-width: 0;
  flex: 1 1 0;
  padding-right: 0;
  padding-left: 0;
}

.cps-list-tabs :deep(.van-tab__text) {
  display: block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cps-page-loading {
  display: flex;
  justify-content: center;
  margin-top: 64rpx;
}

.cps-list {
  display: grid;
  width: 100%;
  min-width: 0;
  max-width: 100%;
  gap: 26rpx;
}

.cps-list-card {
  width: 100%;
  min-width: 0;
  max-width: 100%;
  border: 2rpx solid rgba(20, 184, 166, 0.16);
  border-radius: 16rpx;
  padding: 32rpx;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 20rpx 60rpx rgba(15, 23, 42, 0.08);
}

.cps-issue-card {
  position: relative;
  display: grid;
  gap: 24rpx;
  border-radius: 20rpx;
  padding: 32rpx 30rpx 30rpx 40rpx;
  overflow: hidden;
}

.cps-issue-card:active {
  transform: scale(0.99);
}

.cps-issue-card::before {
  position: absolute;
  top: 0;
  bottom: 0;
  left: 0;
  width: 10rpx;
  content: "";
  background: #14b8a6;
}

.cps-issue-card--blue::before {
  background: linear-gradient(180deg, #2563eb, #60a5fa);
}

.cps-issue-card--teal::before {
  background: linear-gradient(180deg, #14b8a6, #2dd4bf);
}

.cps-issue-card--orange::before {
  background: linear-gradient(180deg, #f97316, #fdba74);
}

.cps-issue-card--green::before {
  background: linear-gradient(180deg, #22c55e, #86efac);
}

.cps-issue-card__head,
.cps-issue-card__foot {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20rpx;
  min-width: 0;
  max-width: 100%;
}

.cps-issue-card__identity {
  min-width: 0;
}

.cps-issue-card__eyebrow {
  display: block;
  margin-bottom: 6rpx;
  color: #64748b;
  font-size: 24rpx;
  font-weight: 800;
  line-height: 34rpx;
}

.cps-issue-card__no {
  display: block;
  min-width: 0;
  color: #0f172a;
  font-size: 34rpx;
  font-weight: 900;
  line-height: 46rpx;
  overflow-wrap: anywhere;
}

.cps-issue-card__badges {
  display: flex;
  flex: 0 1 auto;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10rpx;
  min-width: 0;
  max-width: 100%;
}

.cps-status-pill {
  display: inline-flex;
  align-items: center;
  min-height: 52rpx;
  border-radius: 999rpx;
  padding: 0 18rpx;
  font-size: 26rpx;
  font-weight: 900;
  line-height: 36rpx;
  white-space: nowrap;
}

.cps-status-pill--blue {
  background: #dbeafe;
  color: #1d4ed8;
}

.cps-status-pill--teal {
  background: #ccfbf1;
  color: #0f766e;
}

.cps-status-pill--orange {
  background: #ffedd5;
  color: #c2410c;
}

.cps-status-pill--green {
  background: #dcfce7;
  color: #15803d;
}

.cps-status-pill--red {
  background: #fee2e2;
  color: #b91c1c;
}

.cps-issue-card__desc {
  margin: 0;
  color: #0f172a;
  font-size: 32rpx;
  font-weight: 700;
  line-height: 50rpx;
  overflow-wrap: anywhere;
}

.cps-issue-card__meta {
  display: grid;
  gap: 12rpx;
  padding: 20rpx;
  border-radius: 16rpx;
  background: linear-gradient(135deg, #f0fdfa 0%, #eff6ff 100%);
  color: #475569;
  font-size: 28rpx;
  font-weight: 700;
  line-height: 40rpx;
}

.cps-issue-card__meta span {
  display: grid;
  gap: 4rpx;
  min-width: 0;
  overflow-wrap: anywhere;
}

.cps-issue-card__meta b {
  color: #0f766e;
  font-size: 24rpx;
  font-weight: 950;
  line-height: 32rpx;
}

.cps-issue-card__foot {
  align-items: center;
  border-top: 2rpx solid #ccfbf1;
  padding-top: 20rpx;
  color: #64748b;
  font-size: 28rpx;
  font-weight: 700;
  line-height: 40rpx;
}

.cps-issue-card__foot time {
  flex: 0 1 auto;
  overflow-wrap: anywhere;
}
</style>
