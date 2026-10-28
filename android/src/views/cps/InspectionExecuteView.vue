<template>
  <view class="cps-page cps-execute-page">
    <view v-if="loading" class="u-loading">
      <view class="u-loading__dot" />
      <text class="u-loading__text">加载中…</text>
    </view>

    <template v-if="record">
      <view class="cps-hero">
        <view class="cps-hero__main">
          <text class="cps-hero__eyebrow">巡检执行 · #{{ record.id }}</text>
          <text class="cps-hero__title">{{ record.areaName || '整体巡检' }}</text>
          <view class="cps-hero__stats">
            <text class="cps-hero__stat">{{ record.typeCode }}</text>
            <text class="cps-hero__stat">{{ statusLabel(record.recordStatus) }}</text>
            <text v-if="record.areaCode" class="cps-hero__stat">{{ record.areaCode }}</text>
            <text class="cps-hero__stat">进度 {{ doneCount }}/{{ items.length }}</text>
            <text v-if="record.score != null" class="cps-hero__stat">得分 {{ record.score }}</text>
          </view>
        </view>
        <view class="cps-hero__side">
          <text class="u-tag cps-hero__badge" :class="readonly ? 'u-tag--green' : 'u-tag--blue'">
            {{ readonly ? '已锁定' : '执行中' }}
          </text>
          <button v-if="!readonly" class="u-btn u-btn--sm cps-hero__switch" @click="switchType">
            切换巡检类型
          </button>
          <button v-else class="u-btn u-btn--sm cps-hero__switch" @click="backToList">
            返回任务列表
          </button>
        </view>
      </view>

      <!-- 点检项逐项执行 -->
      <view
        v-for="(item, index) in items"
        :key="item.id"
        class="u-card cps-card cps-item-card"
        :class="{ 'cps-item-card--done': isItemDone(item), 'cps-item-card--fail': item.passResult === 'FAIL' }"
      >
        <view class="cps-card-title">
          <view>
            <text class="cps-card-title__sub">第 {{ index + 1 }} 项 · {{ item.itemCode }}</text>
            <text class="cps-card-title__main">{{ item.content }}</text>
          </view>
          <view class="cps-item-badges">
            <text v-for="t in splitTypes(item.checkTypes)" :key="t" class="u-tag" :class="'u-tag--' + typeColor(t)">
              {{ typeLabel(t) }}
            </text>
            <text v-if="item.deductScore" class="u-tag u-tag--red">扣 {{ item.deductScore }}</text>
          </view>
        </view>

        <!-- 拍照判定条件提示 -->
        <text v-if="hasType(item, 'PHOTO') && item.photoCriteria" class="cps-item-criteria">
          📷 判定条件：{{ item.photoCriteria }}（至少 {{ item.photoMinCount ?? 1 }} 张）
        </text>

        <!-- 是否合格 -->
        <view v-if="hasType(item, 'PASS_FAIL')" class="cps-item-row">
          <text class="cps-item-label">是否合格</text>
          <view class="u-seg" :class="{ 'cps-seg--disabled': readonly || savingItem === item.id }">
            <view
              class="u-seg__item"
              :class="{ 'u-seg__item--active': (local[item.id]?.passResult ?? item.passResult ?? '') === 'PASS' }"
              @click="!(readonly || savingItem === item.id) && onPassChange(item, 'PASS')"
            >
              <text>✓ 合格</text>
            </view>
            <view
              class="u-seg__item"
              :class="{ 'u-seg__item--active': (local[item.id]?.passResult ?? item.passResult ?? '') === 'FAIL' }"
              @click="!(readonly || savingItem === item.id) && onPassChange(item, 'FAIL')"
            >
              <text>✗ 不合格</text>
            </view>
          </view>
        </view>

        <!-- 文本说明 -->
        <view v-if="hasType(item, 'TEXT')" class="cps-item-row cps-item-row--stack">
          <text class="cps-item-label">文本说明</text>
          <textarea
            class="u-textarea"
            :value="local[item.id]?.textValue ?? item.textValue ?? ''"
            :maxlength="500"
            :disabled="readonly"
            :placeholder="textHint(item)"
            auto-height
            @input="(e: any) => onTextChange(item, String(e?.detail?.value ?? e?.target?.value ?? ''))"
            @blur="() => void persistItem(item)"
          />
        </view>

        <!-- 拍照 -->
        <view v-if="hasType(item, 'PHOTO')" class="cps-item-row cps-item-row--stack">
          <text class="cps-item-label">
            现场照片
            <text :class="photoCount(item) >= (item.photoMinCount ?? 1) ? 'cps-count cps-count--ok' : 'cps-count cps-count--lack'">
              {{ photoCount(item) }}/{{ item.photoMinCount ?? 1 }}
            </text>
          </text>
          <view class="cps-photo-grid">
            <view
              v-for="(p, pi) in photoList(item)"
              :key="pi"
              class="cps-photo-tile"
              hover-class="u-tap"
              @click="previewPhoto"
              :data-item-id="item.id"
              :data-index="pi"
            >
              <image class="cps-photo__img" :src="photoUrl(p)" mode="aspectFill" />
              <text v-if="!readonly" class="cps-photo-del" @click.stop="removePhoto(item, pi)">×</text>
            </view>
            <button
              v-if="!readonly"
              class="cps-photo-add"
              hover-class="u-tap"
              :disabled="uploadingItem === item.id"
              @click="onPhotoPick"
              :data-item-id="item.id"
            >
              <text class="cps-photo-add__plus">＋</text>
              <text class="cps-photo-add__text">{{ uploadingItem === item.id ? '上传中' : '拍照/选择' }}</text>
            </button>
          </view>
        </view>

        <view v-if="!readonly && (hasType(item, 'TEXT') || hasType(item, 'PHOTO'))" class="cps-item-save">
          <button class="u-btn u-btn--sm" :disabled="savingItem === item.id" @click="persistItem(item)">保存本项</button>
          <text v-if="savedTip[item.id]" class="cps-saved-tip">已保存</text>
        </view>
      </view>

      <!-- 提交栏 -->
      <view v-if="!readonly" class="cps-submit-bar">
        <button
          class="u-btn u-btn--primary u-btn--block cps-submit-btn"
          :disabled="submitting"
          @click="submit"
        >
          提交巡检（{{ doneCount }}/{{ items.length }} 项完成）
        </button>
      </view>

      <!-- 已判定：得分横幅 -->
      <view v-else class="u-result cps-done-result">
        <text class="u-result__icon">✅</text>
        <text class="u-result__title">巡检已完成，得分 {{ record.score ?? '-' }}</text>
        <text class="u-result__desc">提交时间：{{ formatTime(record.submittedAt) }}</text>
      </view>
    </template>
    <view v-else-if="!loading" class="u-empty">
      <text class="u-empty__icon">📭</text>
      <text class="u-empty__text">记录不存在或已删除</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { cpsApi } from '@/api/index'
import { computed, reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'



import type { CpsInspectionRecord, CpsInspectionRecordItem } from '@/types/cps'
import { resolveAssetUrl, resolveUploadSources } from '@/utils/image'

interface PhotoEntry {
  url: string
  name: string
}

const recordId = ref<number>(0)
const loading = ref(false)
const submitting = ref(false)
const savingItem = ref<number | null>(null)
const uploadingItem = ref<number | null>(null)

const record = ref<CpsInspectionRecord | null>(null)
const items = ref<CpsInspectionRecordItem[]>([])

/** 本地编辑缓冲：itemId → 未保存的修改 */
const local = reactive<Record<number, { passResult?: string | null; textValue?: string | null; photos?: PhotoEntry[] }>>({})
const savedTip = reactive<Record<number, boolean>>({})

const readonly = computed(() => record.value?.recordStatus === 'JUDGED' || record.value?.recordStatus === 'AWAIT_CONFIRM')

const splitTypes = (types?: string) => (types ?? '').split(',').filter(Boolean)
const hasType = (item: CpsInspectionRecordItem, t: string) => splitTypes(item.checkTypes).includes(t)
const typeLabel = (t: string) => ({ PASS_FAIL: '合格判定', TEXT: '文本', PHOTO: '拍照' }[t] ?? t)
const typeColor = (t: string) => (t === 'PHOTO' ? 'orange' : t === 'TEXT' ? 'blue' : 'green')
const textHint = (item: CpsInspectionRecordItem) => `请描述现场情况（${item.itemCode}）`

const statusLabel = (s: string) =>
  ({ PENDING: '待执行', IN_PROGRESS: '执行中', AWAIT_CONFIRM: '待确认', JUDGED: '已判定' }[s] ?? s)

const formatTime = (t?: string | null) => (t ? t.replace('T', ' ').slice(0, 16) : '-')

/**
 * Phase D3：切换巡检类型。
 * 当前记录保持 IN_PROGRESS 落库（不丢已填内容），跳回类型选择页；
 * 之后可在「我的问题 → 我的草稿」或再次新建时恢复本记录。
 */
const switchType = () => {
  if (typeof uni !== 'undefined' && typeof uni.showModal === 'function') {
    uni.showModal({
      title: '切换巡检类型',
      content: '当前记录将保存为「执行中」，已填内容不会丢失，可稍后从巡检执行页继续。确认切换？',
      confirmText: '切换',
      cancelText: '留下继续',
      success: (res) => {
        if (res.confirm) {
          uni.redirectTo({ url: '/views/cps/IssueCreateView' })
        }
      },
    })
    return
  }
  uni.redirectTo({ url: '/views/cps/IssueCreateView' })
}

const backToList = () => {
  const pages = typeof getCurrentPages === 'function' ? getCurrentPages() : []
  if (pages.length > 1) {
    uni.navigateBack()
  } else {
    uni.redirectTo({ url: '/views/cps/IssueListView' })
  }
}

/** 照片列表：本地缓冲优先，其次远端快照（photoObjectKey 逗号分隔） */
const photoList = (item: CpsInspectionRecordItem): PhotoEntry[] => {
  const buffered = local[item.id]?.photos
  if (buffered) return buffered
  if (!item.photoObjectKey) return []
  const keys = item.photoObjectKey.split(',').filter(Boolean)
  const names = (item.photoFileName ?? '').split(',').filter(Boolean)
  return keys.map((k, i) => ({ url: k, name: names[i] ?? `照片${i + 1}` }))
}

/** 图片 src：后端存的是站内相对路径，显示前补成绝对地址 */
const photoUrl = (entry: PhotoEntry) => resolveAssetUrl(entry.url)

const photoCount = (item: CpsInspectionRecordItem) => photoList(item).length

const isItemDone = (item: CpsInspectionRecordItem) => {
  const needPass = hasType(item, 'PASS_FAIL')
  const needText = hasType(item, 'TEXT')
  const needPhoto = hasType(item, 'PHOTO')
  const pass = local[item.id]?.passResult ?? item.passResult
  const text = local[item.id]?.textValue ?? item.textValue
  if (needPass && !pass) return false
  if (needText && !(text ?? '').trim()) return false
  if (needPhoto && photoCount(item) < (item.photoMinCount ?? 1)) return false
  return true
}

const doneCount = computed(() => items.value.filter(isItemDone).length)

const load = async () => {
  if (!recordId.value) return
  loading.value = true
  try {
    const detail = await cpsApi.getInspectionRecord(recordId.value)
    record.value = detail.record
    items.value = detail.items
  } catch (error) {
    uni.showToast({ title: error instanceof Error ? error.message : '加载巡检记录失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}

// ===== 单项编辑 =====
const onPassChange = (item: CpsInspectionRecordItem, value: string) => {
  local[item.id] = { ...local[item.id], passResult: value }
  void persistItem(item)
}

const onTextChange = (item: CpsInspectionRecordItem, value: string) => {
  local[item.id] = { ...local[item.id], textValue: value }
}

/** uni 相册/拍照选择：返回可上传的源（File 或临时路径），用户取消时返回空数组 */
const uploadItemPhotos = async (
  item: CpsInspectionRecordItem,
  sources: ReturnType<typeof resolveUploadSources>,
) => {
  if (!sources.length) return
  uploadingItem.value = item.id
  try {
    const current = [...photoList(item)]
    for (const source of sources) {
      const uploaded = await cpsApi.uploadCpsAttachment(source)
      current.push({ url: uploaded.url, name: uploaded.name })
    }
    local[item.id] = { ...local[item.id], photos: current }
    await persistItem(item)
  } catch (error) {
    uni.showToast({ title: error instanceof Error ? error.message : '照片上传失败', icon: 'none' })
  } finally {
    uploadingItem.value = null
  }
}

// 拍照/选图：直接用 uni 原生 uni.chooseImage（App 端会调起系统相机/相册）
const onPhotoPick = (event: unknown) => {
  const dataset = readDataset(event)
  const item = items.value.find((record) => record.id === Number(dataset.itemId ?? 0))
  if (!item) return
  uni.chooseImage({
    count: 9,
    sizeType: ['compressed'],
    sourceType: ['camera', 'album'],
    success: (result) => {
      void uploadItemPhotos(item, resolveUploadSources(result))
    },
    fail: (err) => {
      const message = String((err as { errMsg?: string } | undefined)?.errMsg ?? '')
      if (/cancel/i.test(message)) return
      uni.showToast({ title: message || '拍照失败', icon: 'none' })
    },
  })
}

const removePhoto = async (item: CpsInspectionRecordItem, index: number) => {
  const current = photoList(item).filter((_, i) => i !== index)
  local[item.id] = { ...local[item.id], photos: current }
  await persistItem(item)
}


type PhotoTapDataset = { list?: string; index?: number | string; itemId?: number | string }

const readDataset = (event: unknown): PhotoTapDataset =>
  ((event as { currentTarget?: { dataset?: Record<string, unknown> } } | undefined)?.currentTarget?.dataset ??
    {}) as PhotoTapDataset

// uni 原生图片预览：urls 必须是绝对地址，current 用下标（避免 filter 后错位）
const previewImages = (sources: string[], index: number) => {
  const urls = sources.map((item) => resolveAssetUrl(item)).filter(Boolean)
  if (!urls.length) {
    uni.showToast({ title: '图片还没加载出来', icon: 'none' })
    return
  }
  const current = Math.min(Math.max(index, 0), urls.length - 1)
  uni.previewImage({
    urls,
    current,
    indicator: 'number',
    loop: true,
    fail: () => uni.showToast({ title: '图片打不开，检查图片地址配置', icon: 'none' }),
  })
}

// 点开巡检项照片：uni 原生预览
const previewPhoto = (event: unknown) => {
  const dataset = readDataset(event)
  const item = items.value.find((record) => record.id === Number(dataset.itemId ?? 0))
  if (!item) return
  previewImages(
    photoList(item).map((photo) => photo.url),
    Number(dataset.index ?? 0),
  )
}

/** 保存单项到服务端（合格/文本/照片一起提交） */
const persistItem = async (item: CpsInspectionRecordItem) => {
  if (readonly.value) return
  savingItem.value = item.id
  try {
    const photos = photoList(item)
    await cpsApi.saveInspectionItemResult(item.id, {
      passResult: (local[item.id]?.passResult ?? item.passResult ?? null) as 'PASS' | 'FAIL' | null,
      textValue: local[item.id]?.textValue ?? item.textValue ?? null,
      photoObjectKey: photos.length ? photos.map((p) => p.url).join(',') : null,
      photoFileName: photos.length ? photos.map((p) => p.name).join(',') : null,
      photoCount: photos.length,
    })
    // 合并回本地 item，清缓冲
    const saved = local[item.id]
    if (saved?.passResult) item.passResult = saved.passResult as 'PASS' | 'FAIL'
    if (saved?.textValue != null) item.textValue = saved.textValue
    if (saved?.photos) {
      item.photoObjectKey = saved.photos.map((p) => p.url).join(',')
      item.photoFileName = saved.photos.map((p) => p.name).join(',')
      item.photoCount = saved.photos.length
    }
    delete local[item.id]
    savedTip[item.id] = true
    setTimeout(() => { savedTip[item.id] = false }, 1500)
  } catch (error) {
    uni.showToast({ title: error instanceof Error ? error.message : '保存失败', icon: 'none' })
  } finally {
    savingItem.value = null
  }
}

// ===== 提交 =====
const submit = async () => {
  if (!record.value || submitting.value) return
  const missing = items.value.filter((i) => !isItemDone(i))
  if (missing.length) {
    uni.showToast({ title: `还有 ${missing.length} 项未完成：${missing[0].content.slice(0, 20)}…`, icon: 'none' })
    return
  }
  submitting.value = true
  try {
    const result = await cpsApi.submitInspection(record.value.id)
    record.value = result
    uni.showToast({ title: `提交成功，得分 ${result.score ?? '-'}`, icon: 'success' })
  } catch (error) {
    uni.showToast({ title: error instanceof Error ? error.message : '提交失败', icon: 'none' })
  } finally {
    submitting.value = false
  }
}

onLoad((query?: Record<string, string | string[] | undefined>) => {
  const raw = Array.isArray(query?.recordId) ? query?.recordId[0] : query?.recordId
  recordId.value = Number(raw)
  void load()
})
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

.u-input,
.u-textarea {
  width: 100%;
  min-height: 44px;
  padding: 10px 14px;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  background: #ffffff;
  font-size: 15px;
  line-height: 22px;
  color: var(--cps-ink);
}

.u-textarea {
  min-height: 96px;
  line-height: 1.6;
}

.u-input--error,
.u-textarea--error {
  border-color: var(--cps-red);
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

.u-tag {
  display: inline-flex;
  align-items: center;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  line-height: 18px;
  white-space: nowrap;
}

.u-tag--blue {
  background: #dbeafe;
  color: #1d4ed8;
}

.u-tag--teal {
  background: #ccfbf1;
  color: #0f766e;
}

.u-tag--green {
  background: #dcfce7;
  color: #15803d;
}

.u-tag--orange {
  background: #ffedd5;
  color: #c2410c;
}

.u-tag--red {
  background: #fee2e2;
  color: #b91c1c;
}

.u-tag--gray {
  background: #e2e8f0;
  color: #475569;
}

.u-tag--purple {
  background: #ede9fe;
  color: #6d28d9;
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

.u-seg {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 4px;
  padding: 4px;
  border-radius: 12px;
  background: #f1f5f9;
}

.u-seg__item {
  padding: 8px 14px;
  border-radius: 9px;
  font-size: 13px;
  font-weight: 600;
  color: var(--cps-muted);
}

.u-seg__item--active {
  background: #ffffff;
  color: var(--cps-teal-deep);
  box-shadow: 0 1px 4px rgba(15, 23, 42, 0.12);
}

.u-result {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 28px 16px;
  text-align: center;
}

.u-result__icon {
  font-size: 40px;
  line-height: 1;
}

.u-result__title {
  font-size: 16px;
  font-weight: 700;
  color: var(--cps-ink, #0f172a);
}

.u-result__desc {
  font-size: 13px;
  color: var(--cps-muted, #64748b);
  line-height: 1.6;
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
  max-width: 960px;
  margin: 0 auto;
  padding: 16px 20px 120px;
}

.cps-hero {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  border-radius: var(--cps-radius-xl);
  padding: 20px 24px;
  background: var(--cps-hero-bg);
  box-shadow: var(--cps-shadow-hero);
  color: #fff;
  margin-bottom: 16px;
}
.cps-hero__main { display: grid; gap: 6px; }
.cps-hero__eyebrow { display: block; margin: 0; color: rgba(255, 255, 255, 0.82); font-size: 12px; font-weight: 700; letter-spacing: 0.18em; }
.cps-hero__title { display: block; margin: 0; font-size: 22px; font-weight: 800; }
.cps-hero__stats { display: flex; flex-wrap: wrap; gap: 8px; }
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
.cps-hero__badge { align-self: flex-start; font-weight: 800; }

/* Phase D3：hero 右侧（状态徽标 + 切换类型/返回按钮） */
.cps-hero__side {
  display: grid;
  justify-items: end;
  gap: 8px;
}

.cps-hero__switch {
  border-radius: var(--cps-radius-pill);
  background: rgba(255, 255, 255, 0.16);
  border: 1px solid rgba(255, 255, 255, 0.55);
  color: #fff;
  font-size: 12px;
  font-weight: 700;
}

.cps-card {
  border-radius: var(--cps-radius-lg);
  border: 1px solid var(--cps-border);
  box-shadow: var(--cps-shadow-card);
}
.cps-item-card + .cps-item-card { margin-top: 12px; }
.cps-item-card--done { border-left: 4px solid #22c55e; }
.cps-item-card--fail { border-left: 4px solid #ef4444; }

.cps-card-title {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--cps-border);
}
.cps-card-title__sub { display: block; margin: 0; color: var(--cps-teal-deep); font-size: 12px; font-weight: 800; }
.cps-card-title__main { display: block; margin: 2px 0 0; color: var(--cps-ink); font-size: 16px; font-weight: 800; }
.cps-item-badges { display: flex; flex-wrap: wrap; gap: 4px; justify-content: flex-end; }

.cps-item-criteria {
  display: block;
  margin: 0 0 10px;
  padding: 8px 10px;
  border-radius: var(--cps-radius-md);
  background: #fff7ed;
  color: #9a3412;
  font-size: 13px;
  line-height: 19px;
}

.cps-item-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 0;
}
.cps-item-row--stack { display: grid; gap: 6px; }
.cps-item-label { color: #0f172a; font-size: 14px; font-weight: 700; flex-shrink: 0; }

/* 不合格/保存中等禁用态（u-seg 全局样式之外的页面态） */
.cps-seg--disabled { opacity: 0.5; }

.cps-count { margin-left: 6px; font-size: 12px; font-weight: 800; }
.cps-count--ok { color: #16a34a; }
.cps-count--lack { color: #ef4444; }

.cps-photo-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(96px, 1fr));
  gap: 8px;
}
.cps-photo-tile {
  position: relative;
  width: 100%;
  aspect-ratio: 1;
  border-radius: 10px;
  overflow: hidden;
  background: #e2e8f0;
}
.cps-photo__img { width: 100%; height: 100%; }
.cps-photo-del {
  position: absolute;
  top: 4px;
  right: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.68);
  color: #fff;
  font-size: 14px;
  font-weight: 900;
}
.cps-photo-add {
  display: grid;
  align-items: center;
  justify-items: center;
  gap: 2px;
  width: 100%;
  aspect-ratio: 1;
  border-radius: 10px;
  border: 1px dashed #99f6e4;
  background: var(--cps-mist);
  color: #0f766e;
}
.cps-photo-add__plus { font-size: 22px; line-height: 1; }
.cps-photo-add__text { font-size: 12px; font-weight: 700; }

.cps-item-save { display: flex; align-items: center; gap: 8px; margin-top: 8px; }
.cps-saved-tip { color: #16a34a; font-size: 12px; font-weight: 700; }

.cps-submit-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 99;
  max-width: 960px;
  margin: 0 auto;
  padding: 12px 20px max(12px, env(safe-area-inset-bottom));
  background: linear-gradient(180deg, rgba(248, 250, 252, 0), #f8fafc 40%);
}
.cps-submit-btn {
  height: 48px;
  border-radius: var(--cps-radius-pill);
  background: var(--cps-teal-deep);
  border: none;
  font-size: 16px;
  font-weight: 700;
}

.cps-done-result { margin-top: 16px; }
</style>
