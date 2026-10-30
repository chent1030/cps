<template>
  <view class="cps-page cps-tasks-page">
    <view class="cps-hero">
      <view class="cps-hero__main">
        <text class="cps-hero__eyebrow">计划派发 · 待我执行</text>
        <text class="cps-hero__title">我的巡检任务</text>
        <view class="cps-hero__stats">
          <text class="cps-hero__stat">待执行 {{ pendingCount }}</text>
          <text class="cps-hero__stat">执行中 {{ runningCount }}</text>
        </view>
      </view>
      <view class="cps-hero__actions">
        <button class="u-btn u-btn--ghost cps-hero__refresh" :disabled="loading" @click="load">
          {{ loading ? '刷新中…' : '刷新' }}
        </button>
      </view>
    </view>

    <view v-if="loading && !tasks.length" class="u-loading">
      <view class="u-loading__dot" />
      <text class="u-loading__text">加载中…</text>
    </view>

    <view v-else-if="!tasks.length" class="u-empty">
      <text class="u-empty__icon">📭</text>
      <text class="u-empty__text">暂无分配给您的巡检任务</text>
      <text class="u-empty__hint">web 端计划审核通过并建单后，任务会派发给责任人并出现在这里</text>
    </view>

    <view v-else class="cps-task-list">
      <view v-for="task in tasks" :key="task.taskId" class="u-card cps-task-card">
        <view class="cps-task-card__head">
          <view class="cps-task-card__identity">
            <text class="cps-task-card__plan">{{ task.planTitle || '巡检计划' }}</text>
            <text class="cps-task-card__title">{{ task.title || typeLabel(task) }}</text>
          </view>
          <text class="cps-pill" :class="task.taskStatus === 'IN_PROGRESS' ? 'cps-pill--orange' : 'cps-pill--blue'">
            {{ statusLabel(task.taskStatus) }}
          </text>
        </view>

        <view class="cps-task-card__meta">
          <view class="cps-task-card__meta-item">
            <text class="cps-task-card__meta-label">巡检类型</text>
            <text class="cps-task-card__meta-value">{{ typeLabel(task) }}</text>
          </view>
          <view class="cps-task-card__meta-item">
            <text class="cps-task-card__meta-label">区域</text>
            <text class="cps-task-card__meta-value">{{ areaText(task) }}</text>
          </view>
          <view class="cps-task-card__meta-item">
            <text class="cps-task-card__meta-label">计划时间</text>
            <text class="cps-task-card__meta-value">{{ formatTime(task.scheduledAt) }}</text>
          </view>
          <view class="cps-task-card__meta-item">
            <text class="cps-task-card__meta-label">频次</text>
            <text class="cps-task-card__meta-value">{{ frequencyLabel(task.frequency) }}</text>
          </view>
        </view>

        <view v-if="task.acceptanceCriteria || task.evidenceRequirement" class="cps-task-card__note">
          <text v-if="task.acceptanceCriteria" class="cps-task-card__note-line">验收标准：{{ task.acceptanceCriteria }}</text>
          <text v-if="task.evidenceRequirement" class="cps-task-card__note-line">取证要求：{{ task.evidenceRequirement }}</text>
        </view>

        <view class="cps-task-card__foot">
          <text v-if="!isKnownType(task)" class="cps-task-card__legacy">历史类型任务，暂不支持移动端执行</text>
          <text v-else-if="task.zoneFlag === 1" class="cps-task-card__hint">开始执行时需选择巡检区域</text>
          <button
            class="u-btn u-btn--primary"
            :disabled="!isKnownType(task) || startingTaskId === task.taskId"
            @click="startTask(task)"
          >
            {{ startingTaskId === task.taskId ? '正在开始…' : task.taskStatus === 'IN_PROGRESS' ? '继续执行' : '开始执行' }}
          </button>
        </view>
      </view>
    </view>

    <!-- 分区域类型任务：开始前选择区域（只列出任务覆盖区域；未配置覆盖区域时列出该类型全部区域） -->
    <view v-if="areaPickerTask" class="cps-mask" @click="closeAreaPicker">
      <view class="cps-sheet" @click.stop>
        <view class="cps-sheet__head">
          <text class="cps-sheet__title">选择巡检区域</text>
          <text class="cps-sheet__sub">{{ typeLabel(areaPickerTask) }}</text>
        </view>
        <view v-if="areaLoading" class="u-loading">
          <view class="u-loading__dot" />
          <text class="u-loading__text">加载区域…</text>
        </view>
        <view v-else class="cps-sheet__list">
          <view
            v-for="area in areaOptions"
            :key="area.id"
            class="cps-sheet__item"
            hover-class="u-tap"
            @click="confirmArea(area)"
          >
            <view class="cps-sheet__item-main">
              <text class="cps-sheet__item-name">{{ area.areaName }}</text>
              <text class="cps-sheet__item-code">{{ area.areaCode }}</text>
            </view>
            <text class="cps-sheet__item-risk">{{ area.riskLevel || '常规' }}</text>
          </view>
          <view v-if="!areaLoading && !areaOptions.length" class="u-empty">
            <text class="u-empty__icon">📭</text>
            <text class="u-empty__text">该巡检类型暂无可选区域</text>
          </view>
        </view>
        <button class="u-btn u-btn--plain cps-sheet__close" @click="closeAreaPicker">取消</button>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { cpsApi } from '@/api/index'
import type { CpsInspectionArea, CpsInspectionType, CpsMyInspectionTask } from '@/types/cps'

const loading = ref(false)
const tasks = ref<CpsMyInspectionTask[]>([])
const types = ref<CpsInspectionType[]>([])
const startingTaskId = ref<number | null>(null)

const areaPickerTask = ref<CpsMyInspectionTask | null>(null)
const areaOptions = ref<CpsInspectionArea[]>([])
const areaLoading = ref(false)

const pendingCount = computed(() => tasks.value.filter((task) => task.taskStatus !== 'IN_PROGRESS').length)
const runningCount = computed(() => tasks.value.filter((task) => task.taskStatus === 'IN_PROGRESS').length)

const typeLabel = (task: CpsMyInspectionTask) =>
  types.value.find((type) => type.code === task.typeCode)?.name || task.typeName || task.typeCode

/**
 * 类型是否仍然可执行：移动端 start 接口要求巡检类型存在且启用；
 * 历史枚举类型（INSPECT_*）已不在类型表，只能提示到 web 端执行。
 */
const isKnownType = (task: CpsMyInspectionTask) => {
  // 类型字典拉取失败时不阻断（交由 start 接口报错）；拉到字典后历史类型明确禁用
  if (!types.value.length) return true
  return types.value.some((type) => type.code === task.typeCode && type.enabled !== 0)
}

const statusLabel = (status?: string | null) =>
  ({ IN_PROGRESS: '执行中', PENDING: '待执行' }[status ?? ''] ?? status ?? '待执行')

const frequencyLabel = (frequency?: string | null) => {
  const value = (frequency ?? '').trim()
  if (!value) return '一次性'
  return (
    ({ ONCE: '一次性', DAILY: '每日', WEEKLY: '每周', MONTHLY: '每月' } as Record<string, string>)[value.toUpperCase()] ??
    value
  )
}

const formatTime = (time?: string | null) => (time ? time.replace('T', ' ').slice(0, 16) : '-')

const coverageCodes = (task: CpsMyInspectionTask) =>
  (task.coverageAreaCodes ?? '')
    .split(',')
    .map((code) => code.trim().toUpperCase())
    .filter(Boolean)

const areaText = (task: CpsMyInspectionTask) => {
  const codes = coverageCodes(task)
  return codes.length ? codes.join('、') : '整体巡检'
}

const toast = (title: string) => uni.showToast({ title, icon: 'none' })

const openRecord = (recordId: number) => {
  uni.navigateTo({ url: `/views/cps/InspectionExecuteView?recordId=${recordId}` })
}

const load = async () => {
  loading.value = true
  try {
    const [taskList, typeList] = await Promise.all([
      cpsApi.listMyTasks(),
      cpsApi.listCpsInspectionTypes().catch(() => [] as CpsInspectionType[]),
    ])
    tasks.value = taskList
    types.value = typeList
  } catch (error) {
    toast(error instanceof Error ? error.message : '加载我的任务失败')
  } finally {
    loading.value = false
  }
}

/** 开始执行：已知进行中任务优先恢复对应记录，避免为同一计划任务重复建记录 */
const startTask = async (task: CpsMyInspectionTask) => {
  if (startingTaskId.value) return
  if (!isKnownType(task)) {
    toast('该任务类型为历史类型，请到 web 端执行')
    return
  }
  if (task.taskStatus === 'IN_PROGRESS') {
    try {
      const records = await cpsApi.listMyInProgressInspections()
      const resumed = records.find((record) => record.planTaskId === task.taskId)
      if (resumed) {
        openRecord(resumed.id)
        return
      }
    } catch {
      // 恢复失败时继续走新建流程
    }
  }
  if (task.zoneFlag === 1) {
    await openAreaPicker(task)
    return
  }
  await doStart(task, null)
}

const openAreaPicker = async (task: CpsMyInspectionTask) => {
  areaPickerTask.value = task
  areaOptions.value = []
  areaLoading.value = true
  try {
    const all = await cpsApi.listInspectionAreas(task.typeCode)
    const codes = new Set(coverageCodes(task))
    const matched = codes.size
      ? all.filter((area) => codes.has(String(area.areaCode ?? '').trim().toUpperCase()))
      : all
    areaOptions.value = matched.length ? matched : all
  } catch (error) {
    toast(error instanceof Error ? error.message : '加载巡检区域失败')
    areaPickerTask.value = null
  } finally {
    areaLoading.value = false
  }
}

const closeAreaPicker = () => {
  if (startingTaskId.value) return
  areaPickerTask.value = null
  areaOptions.value = []
}

const confirmArea = (area: CpsInspectionArea) => {
  const task = areaPickerTask.value
  if (!task) return
  void doStart(task, area)
}

const doStart = async (task: CpsMyInspectionTask, area: CpsInspectionArea | null) => {
  if (startingTaskId.value) return
  startingTaskId.value = task.taskId
  try {
    const record = await cpsApi.startInspection({
      typeCode: task.typeCode,
      areaId: area ? area.id : undefined,
      planTaskId: task.taskId,
      planId: task.planId ?? undefined,
    })
    areaPickerTask.value = null
    areaOptions.value = []
    openRecord(record.id)
  } catch (error) {
    toast(error instanceof Error ? error.message : '开始执行失败')
  } finally {
    startingTaskId.value = null
  }
}

onShow(() => {
  void load()
})
</script>

<style scoped>
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
uni-button {
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

.cps-page {
  --cps-teal: #14b8a6;
  --cps-teal-deep: #0f766e;
  --cps-blue: #2563eb;
  --cps-orange: #f97316;
  --cps-ink: #0f172a;
  --cps-muted: #64748b;
  --cps-line: #e2e8f0;
  min-height: 100vh;
  padding: 16px;
  background: linear-gradient(180deg, #f0fdfa 0%, #f8fafc 42%, #eef2ff 100%);
  color: var(--cps-ink);
  font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif;
  font-size: 15px;
}

.u-tap {
  opacity: 0.72;
}

.u-card {
  background: #ffffff;
  border-radius: 14px;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04), 0 4px 12px rgba(15, 23, 42, 0.04);
}

.u-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 44px;
  padding: 0 18px;
  border-radius: 12px;
  border: 1px solid #cbd5e1;
  background: #ffffff;
  color: var(--cps-ink);
  font-size: 15px;
  font-weight: 600;
}

.u-btn--primary {
  border-color: transparent;
  background: linear-gradient(135deg, #14b8a6 0%, #0e7490 100%);
  color: #ffffff;
}

.u-btn--ghost {
  border-color: rgba(255, 255, 255, 0.5);
  background: rgba(255, 255, 255, 0.16);
  color: #ffffff;
}

.u-btn--plain {
  border-color: #e2e8f0;
  background: #f8fafc;
  color: var(--cps-muted);
}

.u-btn[disabled] {
  opacity: 0.5;
}

.cps-hero {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  border-radius: 16px;
  padding: 20px 24px;
  background: linear-gradient(160deg, #0f766e 0%, #115e59 100%);
  box-shadow: 0 12px 32px rgba(15, 23, 42, 0.12);
  color: #fff;
}

.cps-hero__main {
  display: grid;
  gap: 6px;
}

.cps-hero__eyebrow {
  color: rgba(255, 255, 255, 0.82);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.18em;
}

.cps-hero__title {
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
  border-radius: 999px;
  padding: 0 10px;
  background: rgba(255, 255, 255, 0.16);
  font-size: 13px;
  font-weight: 700;
}

.cps-hero__actions {
  display: flex;
  align-items: center;
  align-self: center;
}

.cps-task-list {
  display: grid;
  gap: 12px;
  margin-top: 16px;
}

.cps-task-card {
  display: grid;
  gap: 12px;
  padding: 16px;
}

.cps-task-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.cps-task-card__identity {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.cps-task-card__plan {
  color: var(--cps-muted);
  font-size: 12px;
  font-weight: 700;
}

.cps-task-card__title {
  font-size: 17px;
  font-weight: 800;
  line-height: 1.35;
}

.cps-pill {
  display: inline-flex;
  align-items: center;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  line-height: 18px;
  white-space: nowrap;
}

.cps-pill--blue {
  background: #dbeafe;
  color: #1d4ed8;
}

.cps-pill--orange {
  background: #ffedd5;
  color: #c2410c;
}

.cps-task-card__meta {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(130px, 1fr));
  gap: 10px;
}

.cps-task-card__meta-item {
  display: grid;
  gap: 2px;
  padding: 8px 10px;
  border-radius: 10px;
  background: #f8fafc;
}

.cps-task-card__meta-label {
  color: var(--cps-muted);
  font-size: 12px;
}

.cps-task-card__meta-value {
  font-size: 14px;
  font-weight: 700;
}

.cps-task-card__note {
  display: grid;
  gap: 4px;
  padding: 10px 12px;
  border-radius: 10px;
  background: #f0fdfa;
}

.cps-task-card__note-line {
  color: #0f766e;
  font-size: 13px;
  line-height: 1.5;
}

.cps-task-card__foot {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.cps-task-card__hint {
  color: var(--cps-muted);
  font-size: 12px;
}

.cps-task-card__legacy {
  color: #b91c1c;
  font-size: 12px;
  font-weight: 700;
}

.u-loading {
  display: grid;
  justify-items: center;
  gap: 8px;
  padding: 32px 0;
  color: var(--cps-muted);
}

.u-loading__dot {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  border: 3px solid #99f6e4;
  border-top-color: #0f766e;
  animation: u-spin 0.8s linear infinite;
}

.u-loading__text {
  font-size: 13px;
}

@keyframes u-spin {
  to {
    transform: rotate(360deg);
  }
}

.u-empty {
  display: grid;
  justify-items: center;
  gap: 8px;
  padding: 48px 16px;
  text-align: center;
}

.u-empty__icon {
  font-size: 32px;
}

.u-empty__text {
  color: var(--cps-ink);
  font-size: 15px;
  font-weight: 700;
}

.u-empty__hint {
  color: var(--cps-muted);
  font-size: 12px;
  line-height: 1.6;
}

.cps-mask {
  position: fixed;
  inset: 0;
  z-index: 900;
  display: flex;
  align-items: flex-end;
  background: rgba(15, 23, 42, 0.45);
}

.cps-sheet {
  display: grid;
  gap: 12px;
  width: 100%;
  max-height: 70vh;
  padding: 18px 16px calc(18px + env(safe-area-inset-bottom));
  border-radius: 18px 18px 0 0;
  background: #ffffff;
}

.cps-sheet__head {
  display: grid;
  gap: 2px;
}

.cps-sheet__title {
  font-size: 17px;
  font-weight: 800;
}

.cps-sheet__sub {
  color: var(--cps-muted);
  font-size: 12px;
}

.cps-sheet__list {
  display: grid;
  gap: 8px;
  overflow-y: auto;
}

.cps-sheet__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 52px;
  padding: 10px 12px;
  border: 1px solid var(--cps-line);
  border-radius: 12px;
}

.cps-sheet__item-main {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.cps-sheet__item-name {
  font-size: 15px;
  font-weight: 700;
}

.cps-sheet__item-code {
  color: var(--cps-muted);
  font-size: 12px;
}

.cps-sheet__item-risk {
  padding: 2px 8px;
  border-radius: 999px;
  background: #f1f5f9;
  color: var(--cps-muted);
  font-size: 12px;
  white-space: nowrap;
}

.cps-sheet__close {
  width: 100%;
}
</style>
