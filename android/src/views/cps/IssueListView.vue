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
      <a-button size="large" class="cps-hero__action" @click="navigateToCreate">
        <template #icon><PlusOutlined /></template>
        新建问题
      </a-button>
    </header>

    <a-card :body-style="{ padding: '0' }" class="cps-tabs-card">
      <a-tabs v-model:activeKey="tab" class="cps-list-tabs" @change="onTabChange">
        <a-tab-pane v-for="item in tabs" :key="item.value" :tab="item.label" />
      </a-tabs>
    </a-card>

    <a-spin :spinning="tab === 'drafts' ? draftsLoading : tab === 'running' ? recordsLoading : loading">
      <!-- 我的草稿：服务端暂存列表，内联在页签里 -->
      <div v-if="tab === 'drafts'" class="cps-inline-list">
        <a-empty v-if="!drafts.length && !draftsLoading" description="暂无草稿——新建问题页点击「暂存」即可保存" />
        <div v-for="d in drafts" :key="d.id" class="cps-inline-list__item">
          <div class="cps-inline-list__info">
            <strong class="cps-inline-list__title">{{ d.title || `草稿 #${d.id}` }}</strong>
            <span class="cps-inline-list__time">{{ formatDraftTime(d.updatedAt) }}</span>
          </div>
          <div class="cps-inline-list__actions">
            <a-button type="primary" size="small" @click="restoreDraft(d.id)">继续填写</a-button>
            <a-button size="small" danger @click="removeDraft(d.id)">删除</a-button>
          </div>
        </div>
      </div>

      <!-- 执行中：IN_PROGRESS 巡检记录，可继续执行 -->
      <div v-else-if="tab === 'running'" class="cps-inline-list">
        <a-empty v-if="!records.length && !recordsLoading" description="暂无执行中的巡检——新建问题选择类型即可开始" />
        <div v-for="r in records" :key="r.id" class="cps-inline-list__item">
          <div class="cps-inline-list__info">
            <strong class="cps-inline-list__title">
              {{ typeLabel(r.typeCode) }}<template v-if="r.areaName"> · {{ r.areaName }}</template>
            </strong>
            <span class="cps-inline-list__time">
              开始 {{ formatRecordTime(r.startedAt) }}<template v-if="r.areaCode"> · {{ r.areaCode }}</template>
            </span>
          </div>
          <div class="cps-inline-list__actions">
            <a-button type="primary" size="small" @click="resumeRecord(r.id)">继续执行</a-button>
          </div>
        </div>
      </div>

      <div v-else class="cps-list-grid">
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
                  <strong class="cps-issue-card__no">巡检问题</strong>
                </div>
                <div class="cps-issue-card__badges">
                  <a-tag :color="statusMeta[issue.status].color" class="cps-pill">{{ statusMeta[issue.status].label }}</a-tag>
                  <a-tag v-if="issue.inspectionTypeCode" color="arcoblue" class="cps-pill">{{ issue.inspectionTypeCode }}</a-tag>
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
import { deleteCpsIssueDraft, listMyCpsIssueDrafts } from '@/api/cps/issueDraft'
import { listCpsInspectionTypes } from '@/api/cps/inspectionType'
import { listMyInProgressInspections } from '@/api/cps/inspectionTask'
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
}

const listCache = useListCacheStore()

const tab = ref<TabKey>('todo')
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
  if (isIssueTab(tab.value)) void load()
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
  { value: 'todo', label: '待我处理' },
  { value: 'created', label: '我发起的' },
  { value: 'related', label: '我参与的' },
  { value: 'closed', label: '已关闭' },
  { value: 'drafts', label: draftCount.value ? `我的草稿 (${draftCount.value})` : '我的草稿' },
  { value: 'running', label: recordCount.value ? `执行中 (${recordCount.value})` : '执行中' },
])

const loadDrafts = async () => {
  draftsLoading.value = true
  try {
    drafts.value = await listMyCpsIssueDrafts()
  } catch {
    // 草稿拉取失败不打扰主列表，静默忽略
  } finally {
    draftsLoading.value = false
  }
}

const loadRecords = async () => {
  recordsLoading.value = true
  try {
    records.value = await listMyInProgressInspections()
    if (records.value.length && !typeOptions.value.length) {
      try {
        typeOptions.value = await listCpsInspectionTypes()
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
})

const removeDraft = async (id: number) => {
  try {
    await deleteCpsIssueDraft(id)
    message.success('草稿已删除')
    await loadDrafts()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '删除失败')
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
  margin: 0;
  color: rgba(255, 255, 255, 0.82);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.18em;
}

.cps-hero h1 {
  margin: 0;
  font-size: 24px;
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
  border-radius: var(--cps-radius-lg);
  border: 1px solid var(--cps-border);
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
  border-radius: var(--cps-radius-md);
  background: var(--cps-cloud);
  color: #475569;
  font-size: 13px;
  line-height: 18px;
}

.cps-issue-card__meta b {
  display: block;
  color: var(--cps-teal-deep);
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
  border-top: 1px solid var(--cps-border);
  padding-top: 10px;
  color: var(--cps-muted);
  font-size: 12px;
  font-weight: 600;
}

/* 平板横屏：卡片更紧凑，列表 hero 缩小 */
@media (min-width: 1024px) {
  .cps-page {
    padding: 20px 28px 48px;
  }
  .cps-hero h1 {
    font-size: 26px;
  }
}
</style>
