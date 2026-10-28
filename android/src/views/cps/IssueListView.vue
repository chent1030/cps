<template>
  <view class="cps-page cps-list-page">
    <view class="cps-hero">
      <view class="cps-hero__main">
        <text class="cps-hero__eyebrow">问题闭环工作台</text>
        <text class="cps-hero__title">我的问题</text>
        <view class="cps-hero__stats">
          <text class="cps-hero__stat">待处理 {{ todoCount }}</text>
          <text class="cps-hero__stat">超时 {{ overdueCount }}</text>
        </view>
      </view>
      <button class="u-btn u-btn--primary cps-hero__action" @click="navigateToCreate">＋ 新建问题</button>
    </view>

    <view class="u-card u-card--pad-none cps-tabs-card">
      <view class="u-tabs cps-list-tabs" :style="tabBarStyle">
        <view
          v-for="item in tabs"
          :key="item.value"
          class="u-tabs__item cps-tab"
          :class="{ 'u-tabs__item--active': tab === item.value }"
          :style="tabItemStyle"
          @click="tab = item.value"
        >
          <text class="cps-tab__label">{{ item.label }}</text>
          <text v-if="item.count > 0" class="u-badge cps-tab__badge">{{ item.count > 99 ? '99+' : item.count }}</text>
        </view>
      </view>
    </view>

    <view v-if="currentLoading" class="u-loading">
      <view class="u-loading__dot" />
      <text class="u-loading__text">加载中…</text>
    </view>

    <!-- 我的草稿：服务端暂存列表，内联在页签里 -->
    <view v-if="tab === 'drafts'" class="cps-inline-list">
      <view v-if="!drafts.length && !draftsLoading" class="u-empty">
        <text class="u-empty__icon">📭</text>
        <text class="u-empty__text">暂无草稿——新建问题页点击「暂存」即可保存</text>
      </view>
      <view v-for="d in drafts" :key="d.id" class="cps-inline-list__item">
        <view class="cps-inline-list__info">
          <text class="cps-inline-list__title">{{ d.title || `草稿 #${d.id}` }}</text>
          <text class="cps-inline-list__time">{{ formatDraftTime(d.updatedAt) }}</text>
        </view>
        <view class="cps-inline-list__actions">
          <button class="u-btn u-btn--primary u-btn--sm" @click="restoreDraft(d.id)">继续填写</button>
          <button class="u-btn u-btn--danger u-btn--sm" @click="removeDraft(d.id)">删除</button>
        </view>
      </view>
    </view>

    <!-- 执行中：IN_PROGRESS 巡检记录，可继续执行 -->
    <view v-else-if="tab === 'running'" class="cps-inline-list">
      <view v-if="!records.length && !recordsLoading" class="u-empty">
        <text class="u-empty__icon">📭</text>
        <text class="u-empty__text">暂无执行中的巡检——新建问题选择类型即可开始</text>
      </view>
      <view v-for="r in records" :key="r.id" class="cps-inline-list__item">
        <view class="cps-inline-list__info">
          <text class="cps-inline-list__title">
            {{ typeLabel(r.typeCode) }}<text v-if="r.areaName"> · {{ r.areaName }}</text>
          </text>
          <text class="cps-inline-list__time">
            开始 {{ formatRecordTime(r.startedAt) }}<text v-if="r.areaCode"> · {{ r.areaCode }}</text>
          </text>
        </view>
        <view class="cps-inline-list__actions">
          <button class="u-btn u-btn--primary u-btn--sm" @click="resumeRecord(r.id)">继续执行</button>
        </view>
      </view>
    </view>

    <view v-else class="cps-list-grid">
      <view v-if="!items.length && !loading" class="u-empty cps-list-empty">
        <text class="u-empty__icon">📭</text>
        <text class="u-empty__text">暂无问题</text>
      </view>
      <view class="u-grid">
        <view
          v-for="issue in items"
          :key="issue.id"
          class="u-card cps-issue-card"
          :class="statusMeta[issue.status].rail"
          hover-class="u-tap"
          @click="navigateToDetail(issue.id)"
        >
          <view class="cps-issue-card__head">
            <view class="cps-issue-card__identity">
              <text class="cps-issue-card__eyebrow">巡检单</text>
              <text class="cps-issue-card__no">巡检问题</text>
            </view>
            <view class="cps-issue-card__badges">
              <text class="cps-pill" :class="statusMeta[issue.status].tone">{{ statusMeta[issue.status].label }}</text>
              <text v-if="issue.inspectionTypeCode" class="cps-pill cps-pill--blue">{{ issue.inspectionTypeCode }}</text>
              <text v-if="issue.overdue" class="cps-pill cps-pill--red">超时</text>
            </view>
          </view>

          <text class="cps-issue-card__desc">{{ issue.description }}</text>

          <view class="cps-issue-card__meta">
            <view class="cps-issue-card__meta-item">
              <text class="cps-issue-card__meta-label">位置</text>
              <text class="cps-issue-card__meta-value">{{ issueLocation(issue) }}</text>
            </view>
            <view class="cps-issue-card__meta-item">
              <text class="cps-issue-card__meta-label">分类</text>
              <text class="cps-issue-card__meta-value">{{ issueCategory(issue) }}</text>
            </view>
          </view>

          <view class="cps-issue-card__foot">
            <text class="cps-issue-card__handler">处理人 {{ issue.currentHandlerEmpName || issue.currentHandlerEmpNo || '-' }}</text>
            <text class="cps-issue-card__time">{{ issue.submitTime }}</text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { cpsApi } from '@/api/index'
import { computed, ref, watch } from 'vue'





import type {
  CpsInspectionRecord,
  CpsInspectionType,
  CpsIssueDraft,
  CpsIssueListItem,
  CpsIssueTab,
} from '@/types/cps'
import { statusMeta, issueLocation, issueCategory } from '@/utils/format'
import { useListCacheStore } from '@/store/listCache'

type TabKey = CpsIssueTab | 'drafts' | 'running'

interface TabItem {
  value: TabKey
  label: string
  count: number
}

const listCache = useListCacheStore()

const tab = ref<TabKey>('todo')
const items = ref<CpsIssueListItem[]>([])
const loading = ref<boolean>(false)
// 4 个问题页签的角标条数（后端一次聚合）
const counts = ref<Record<CpsIssueTab, number>>({ todo: 0, created: 0, related: 0, closed: 0 })

const todoCount = computed<number>(() => items.value.filter((item) => item.status !== 'CLOSED').length)
const overdueCount = computed<number>(() => items.value.filter((item) => item.overdue).length)

const navigateToCreate = () => {
  uni.navigateTo({ url: '/views/cps/IssueCreateView' })
}

const navigateToDetail = (id: number) => {
  uni.navigateTo({ url: `/views/cps/IssueDetailView?id=${id}` })
}

const isIssueTab = (key: TabKey): key is CpsIssueTab =>
  key === 'todo' || key === 'created' || key === 'related' || key === 'closed'

const load = async (force = false) => {
  if (!isIssueTab(tab.value)) return
  // 命中缓存（且非强制刷新）时直接回显，再后台静默更新
  const cached = listCache.get(tab.value)
  const fresh = force ? false : listCache.age(tab.value) > 0 && listCache.age(tab.value) < 30_000
  if (cached.length || fresh) {
    items.value = cached
  }
  loading.value = true
  try {
    const result = await cpsApi.listCpsIssues({ tab: tab.value, page: 1, pageSize: 50 })
    items.value = result
    listCache.set(tab.value, result)
  } catch (error) {
    uni.showToast({ title: error instanceof Error ? error.message : '加载失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}

// onShow：从详情页/新建页返回时，当前 tab 列表可能已变化，强制刷新一次
const onShowHandler = () => {
  if (isIssueTab(tab.value)) void load(true)
}

// ============================================================
// Phase B2：服务端草稿入口（页签内联展示）
// ============================================================
const draftsLoading = ref(false)
const drafts = ref<CpsIssueDraft[]>([])
const draftCount = computed(() => drafts.value.length)

// ============================================================
// 执行中分组：IN_PROGRESS 巡检记录（恢复现场）
// ============================================================
const recordsLoading = ref(false)
const records = ref<CpsInspectionRecord[]>([])
const recordCount = computed(() => records.value.length)
const typeOptions = ref<CpsInspectionType[]>([])

const typeLabel = (code: string) => typeOptions.value.find((t) => t.code === code)?.name ?? code

const tabs = computed<TabItem[]>(() => [
  { value: 'todo', label: '待我处理', count: counts.value.todo },
  { value: 'created', label: '我发起的', count: counts.value.created },
  { value: 'related', label: '我参与的', count: counts.value.related },
  { value: 'closed', label: '已关闭', count: counts.value.closed },
  { value: 'drafts', label: '我的草稿', count: draftCount.value },
  { value: 'running', label: '执行中', count: recordCount.value },
])

// 当前页签对应的加载态（替代 a-spin 的 spinning 计算）
const currentLoading = computed(() => {
  if (tab.value === 'drafts') return draftsLoading.value
  if (tab.value === 'running') return recordsLoading.value
  return loading.value
})

const loadCounts = async () => {
  try {
    counts.value = await cpsApi.getCpsIssueCounts()
  } catch {
    // 角标拉取失败不影响列表，静默忽略
  }
}

const loadDrafts = async () => {
  draftsLoading.value = true
  try {
    drafts.value = await cpsApi.listMyCpsIssueDrafts()
  } catch {
    // 草稿拉取失败不打扰主列表，静默忽略
  } finally {
    draftsLoading.value = false
  }
}

const loadRecords = async () => {
  recordsLoading.value = true
  try {
    records.value = await cpsApi.listMyInProgressInspections()
    if (records.value.length && !typeOptions.value.length) {
      try {
        typeOptions.value = await cpsApi.listCpsInspectionTypes()
      } catch {
        // 类型名拿不到时回退显示编码
      }
    }
  } catch {
    // 执行中记录拉取失败不打扰主列表，静默忽略
  } finally {
    recordsLoading.value = false
  }
}

const restoreDraft = (id: number) => {
  uni.navigateTo({ url: `/views/cps/IssueCreateView?draftId=${id}` })
}

const resumeRecord = (id: number) => {
  uni.navigateTo({ url: `/views/cps/InspectionExecuteView?recordId=${id}` })
}

// App 端（app-vue）里 uni-view 的默认布局与 scoped 样式的命中都不稳定，
// 页签横排再用内联样式兜底一次，保证任何环境下都是横向排列。
const tabBarStyle = {
  display: 'flex',
  flexDirection: 'row' as const,
  flexWrap: 'nowrap' as const,
  alignItems: 'center' as const,
}
const tabItemStyle = {
  display: 'inline-flex',
  flexDirection: 'row' as const,
  alignItems: 'center' as const,
  flex: 'none' as const,
}

watch(tab, () => {
  if (isIssueTab(tab.value)) void load()
  else if (tab.value === 'drafts') void loadDrafts()
  else void loadRecords()
}, { immediate: true })

// uni-app 页面生命周期：每次显示页面触发
import { onShow } from '@dcloudio/uni-app'
onShow(() => {
  if (isIssueTab(tab.value)) onShowHandler()
  void loadDrafts()
  void loadRecords()
  void loadCounts()
})

const removeDraft = async (id: number) => {
  try {
    await cpsApi.deleteCpsIssueDraft(id)
    uni.showToast({ title: '草稿已删除', icon: 'success' })
    await loadDrafts()
  } catch (error) {
    uni.showToast({ title: error instanceof Error ? error.message : '删除失败', icon: 'none' })
  }
}

const formatDraftTime = (t?: string) => {
  if (!t) return '-'
  return t.replace('T', ' ').slice(0, 16)
}

const formatRecordTime = (t?: string | null) => {
  if (!t) return '-'
  return t.replace('T', ' ').slice(5, 16)
}
</script>

<style scoped>
/* @inline-uni-controls: 由脚本按页面用到的类内联生成，勿手改 */

/* 该页自包含的 uni 内置控件样式（原全局样式已按页面内联到这里） */
*,
*::before,
*::after {
  box-sizing: border-box;
}

view,
uni-view,
text,
uni-text,
image,
uni-image,
button,
uni-button,
input,
uni-input,
textarea,
uni-textarea,
scroll-view,
uni-scroll-view,
picker,
uni-picker {
  box-sizing: border-box;
}

image,
uni-image {
  display: block;
}

button,
uni-button {
  margin: 0;
  padding: 0;
  background: transparent;
  border: 0;
  border-radius: 0;
  line-height: normal;
  font: inherit;
  color: inherit;
  text-align: center;
}

button::after,
uni-button::after {
  border: none;
}

input,
uni-input,
textarea,
uni-textarea {
  margin: 0;
  padding: 0;
  background: transparent;
  border: 0;
  font: inherit;
  color: inherit;
  outline: none;
}

@keyframes u-pulse {
  0%, 100% { opacity: 0.3; transform: scale(0.8); }
  50% { opacity: 1; transform: scale(1); }
}

switch {
  transform: scale(0.9);
  transform-origin: right center;
}

radio .wx-radio-input,
checkbox .wx-checkbox-input {
  border-radius: 4px;
}

.cps-page {
  --cps-teal: #14b8a6;
  --cps-teal-deep: #0f766e;
  --cps-blue: #2563eb;
  --cps-orange: #f97316;
  --cps-green: #22c55e;
  --cps-red: #ef4444;
  --cps-ink: #0f172a;
  --cps-muted: #64748b;
  --cps-mist: #f0fdfa;
  --cps-cloud: #f8fafc;
  --cps-line: #ccfbf1;
  --cps-ink-2: #334155;
  --cps-faint: #94a3b8;
  --cps-border: #e2e8f0;
  --cps-border-strong: #cbd5e1;
  --cps-info-deep: #1d4ed8;
  --cps-warn-deep: #b45309;
  --cps-success-deep: #15803d;
  --cps-danger-deep: #b91c1c;
  --cps-radius-sm: 8px;
  --cps-radius-md: 10px;
  --cps-radius-lg: 12px;
  --cps-radius-xl: 16px;
  --cps-radius-pill: 999px;
  --cps-shadow-card: 0 1px 2px rgba(15, 23, 42, 0.04), 0 4px 12px rgba(15, 23, 42, 0.04);
  --cps-shadow-hero: 0 12px 32px rgba(15, 23, 42, 0.12);
  --cps-hero-bg: linear-gradient(160deg, #0f766e 0%, #115e59 100%);
  margin: 0;
  background:
    radial-gradient(circle at top left, rgba(20, 184, 166, 0.18), transparent 512px),
    linear-gradient(180deg, #f0fdfa 0%, #f8fafc 42%, #eef2ff 100%);
  color: var(--cps-ink);
  font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif;
  font-size: 16px;
  touch-action: pan-y;
  -webkit-tap-highlight-color: transparent;
}

.u-tap {
  opacity: 0.72;
}

.u-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}

.u-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 44px;
  padding: 0 18px;
  border-radius: 12px;
  font-size: 15px;
  font-weight: 600;
  border: 1px solid #cbd5e1;
  background: #ffffff;
  color: var(--cps-ink);
  transition: opacity 0.15s ease;
}

.u-btn--primary {
  border-color: transparent;
  background: linear-gradient(135deg, #14b8a6 0%, #0e7490 100%);
  color: #ffffff;
}

.u-btn--danger {
  border-color: transparent;
  background: var(--cps-red);
  color: #ffffff;
}

.u-btn--ghost {
  border-color: rgba(255, 255, 255, 0.45);
  background: rgba(255, 255, 255, 0.15);
  color: #ffffff;
}

.u-btn--plain {
  border-color: #e2e8f0;
  background: #f8fafc;
  color: var(--cps-muted);
}

.u-btn--block {
  display: flex;
  width: 100%;
}

.u-btn--sm {
  min-height: 34px;
  padding: 0 12px;
  font-size: 13px;
  border-radius: 9px;
}

.u-btn[disabled],
.u-btn--disabled {
  opacity: 0.5;
}

.u-card {
  background: #ffffff;
  border-radius: 14px;
  border: 1px solid #e2e8f0;
  padding: 16px;
  box-shadow: 0 4px 16px rgba(15, 118, 110, 0.06);
}

.u-card--flat {
  box-shadow: none;
}

.u-card--pad-none {
  padding: 0;
}

.u-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 48px 16px;
}

.u-empty__icon {
  font-size: 34px;
  line-height: 1;
}

.u-empty__text {
  font-size: 14px;
  color: var(--cps-muted);
}

.u-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 18px;
}

.u-loading__text {
  font-size: 14px;
  color: var(--cps-muted);
}

.u-loading__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--cps-teal);
  animation: u-pulse 1s ease-in-out infinite;
}

.u-tabs {
  display: flex;
  flex-direction: row;
  flex-wrap: nowrap;
  align-items: center;
  gap: 4px;
  padding: 6px;
  border-bottom: 1px solid #e2e8f0;
  overflow-x: auto;
  white-space: nowrap;
}

.u-tabs__item {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 6px;
  flex: none;
  padding: 8px 14px;
  border-radius: 10px 10px 0 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--cps-muted);
}

.u-tabs__item--active {
  color: var(--cps-teal-deep);
  background: var(--cps-mist);
  box-shadow: inset 0 -2px 0 var(--cps-teal);
}

.u-badge {
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 9px;
  background: var(--cps-red, #ef4444);
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  line-height: 18px;
  text-align: center;
}

.u-badge--dot {
  width: 8px;
  height: 8px;
  min-width: 0;
  padding: 0;
  border-radius: 50%;
}

.cps-pill {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  line-height: 18px;
}

.cps-pill--blue {
  background: #dbeafe;
  color: #1d4ed8;
}

.cps-pill--orange {
  background: #ffedd5;
  color: #c2410c;
}

.cps-pill--green {
  background: #dcfce7;
  color: #15803d;
}

.cps-pill--teal {
  background: #ccfbf1;
  color: #0f766e;
}

.cps-pill--gray {
  background: #e2e8f0;
  color: #475569;
}

.cps-pill--red {
  background: #fee2e2;
  color: #b91c1c;
}

.cps-status-pill {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 10px;
  border-radius: var(--cps-radius-pill);
  font-size: 12px;
  font-weight: 600;
  line-height: 18px;
  white-space: nowrap;
}

.cps-status-pill--blue {
  background: #dbeafe;
  color: #1d4ed8;
}

.cps-status-pill--orange {
  background: #ffedd5;
  color: #c2410c;
}

.cps-status-pill--green {
  background: #dcfce7;
  color: #15803d;
}

.cps-status-pill--teal {
  background: #ccfbf1;
  color: #0f766e;
}

.cps-status-pill--gray {
  background: #e2e8f0;
  color: #475569;
}

.cps-status-pill--red {
  background: #fee2e2;
  color: #b91c1c;
}
/* @end-inline-uni-controls */

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
  border-radius: var(--cps-radius-xl);
  padding: 20px 24px;
  background: var(--cps-hero-bg);
  box-shadow: var(--cps-shadow-hero);
  color: #fff;
  margin-bottom: 16px;
}

.cps-hero__main {
  display: grid;
  gap: 6px;
}

.cps-hero__eyebrow {
  display: block;
  color: rgba(255, 255, 255, 0.82);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.18em;
}

.cps-hero__title {
  display: block;
  font-size: 24px;
  font-weight: 800;
  line-height: 1.3;
}

.cps-hero__stats {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.cps-hero__stat {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  border-radius: var(--cps-radius-pill);
  padding: 0 10px;
  background: rgba(255, 255, 255, 0.16);
  font-size: 13px;
  font-weight: 700;
}

.cps-hero__action {
  /* hero 为 flex-start，按钮单独在交叉轴居中，与左侧标题区对齐 */
  align-self: center;
  background: #fff !important;
  color: var(--cps-teal-deep) !important;
  border: none !important;
  font-weight: 800 !important;
}

/* 页签内联列表（我的草稿 / 执行中） */
.cps-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.cps-tab__label {
  line-height: 1;
}

.cps-tab__badge {
  margin-left: 6px;
}

.cps-inline-list {
  display: grid;
  gap: 10px;
}

.cps-inline-list__item {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 12px 14px;
  border: 1px solid var(--cps-border);
  border-radius: var(--cps-radius-md);
  background: var(--cps-cloud);
}

.cps-inline-list__info {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.cps-inline-list__title {
  color: #0f172a;
  font-size: 14px;
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cps-inline-list__time {
  color: #94a3b8;
  font-size: 12px;
}

.cps-inline-list__actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.cps-tabs-card {
  margin-bottom: 16px;
  border-radius: var(--cps-radius-lg);
  overflow: hidden;
}

.cps-list-tabs {
  border-bottom: none;
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
  border-radius: var(--cps-radius-lg);
  border: 1px solid var(--cps-border);
  padding: 16px 16px 16px 20px;
  transition: transform 120ms ease;
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
  background: #2563eb;
}
.cps-rail--teal::before {
  background: #14b8a6;
}
.cps-rail--orange::before {
  background: #f97316;
}
.cps-rail--green::before {
  background: #22c55e;
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

.cps-issue-card__desc {
  display: -webkit-box;
  margin: 0 0 10px;
  color: #0f172a;
  font-size: 14px;
  font-weight: 600;
  line-height: 22px;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.cps-issue-card__meta {
  display: grid;
  gap: 6px;
  padding: 10px;
  border-radius: var(--cps-radius-md);
  background: var(--cps-cloud);
  color: #475569;
  font-size: 13px;
  line-height: 18px;
}

.cps-issue-card__meta-item {
  display: block;
}

.cps-issue-card__meta-label {
  display: block;
  color: var(--cps-teal-deep);
  font-size: 11px;
  font-weight: 800;
}

.cps-issue-card__meta-value {
  display: block;
  color: #475569;
}

.cps-issue-card__foot {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 10px;
  border-top: 1px solid var(--cps-border);
  padding-top: 10px;
  color: var(--cps-muted);
  font-size: 12px;
  font-weight: 600;
}

.cps-issue-card__time {
  color: var(--cps-muted);
}

/* 平板横屏：卡片更紧凑，列表 hero 缩小 */
@media (min-width: 1024px) {
  .cps-page {
    padding: 20px 28px 48px;
  }
  .cps-hero__title {
    font-size: 26px;
  }
}
</style>
