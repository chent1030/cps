<template>
  <view class="cps-page cps-rectify-page">
    <view v-if="loading" class="u-loading">
      <view class="u-loading__dot" />
      <text class="u-loading__text">加载中…</text>
    </view>

    <template v-else-if="record">
      <view class="cps-hero">
        <view class="cps-hero__main">
          <text class="cps-hero__eyebrow">巡检整改 · #{{ record.id }}</text>
          <text class="cps-hero__title">{{ typeName }}</text>
          <view class="cps-hero__stats">
            <text class="cps-hero__stat">{{ record.areaName || '整体巡检' }}</text>
            <text v-if="record.score != null" class="cps-hero__stat">得分 {{ record.score }}</text>
            <text v-if="record.reviewRound" class="cps-hero__stat">第 {{ record.reviewRound }} 轮复核</text>
          </view>
        </view>
        <view class="cps-hero__side">
          <text class="u-tag cps-hero__badge" :class="statusBadgeClass(record.recordStatus)">
            {{ statusLabel(record.recordStatus) }}
          </text>
          <button class="u-btn u-btn--sm cps-hero__switch" @click="backToList">返回列表</button>
        </view>
      </view>

      <!-- 记录摘要：类型 / 区域 / 得分 / AI 结论 / 复核意见 -->
      <view class="u-card cps-summary-card">
        <view class="cps-card-title">
          <text class="cps-card-title__main">记录摘要</text>
          <text v-if="record.rectifyEmpName || record.rectifyEmpNo" class="u-tag u-tag--blue">
            整改人 {{ record.rectifyEmpName || record.rectifyEmpNo }}
          </text>
        </view>

        <view class="u-desc">
          <view class="u-desc__row">
            <text class="u-desc__label">巡检类型</text>
            <text class="u-desc__value">{{ typeName }}</text>
          </view>
          <view class="u-desc__row">
            <text class="u-desc__label">区域</text>
            <text class="u-desc__value">
              {{ record.areaName || '整体巡检' }}<text v-if="record.areaCode">（{{ record.areaCode }}）</text>
            </text>
          </view>
          <view class="u-desc__row">
            <text class="u-desc__label">得分</text>
            <text class="u-desc__value">{{ record.score ?? '-' }}</text>
          </view>
          <view class="u-desc__row">
            <text class="u-desc__label">提交时间</text>
            <text class="u-desc__value">{{ formatTime(record.submittedAt) }}</text>
          </view>
        </view>

        <view class="cps-note">
          <text class="cps-note__label">AI 结论</text>
          <text class="cps-note__text">{{ aiConclusion }}</text>
        </view>

        <view class="cps-note">
          <view class="cps-note__head">
            <text class="cps-note__label">复核意见</text>
            <text v-if="reviewMeta" class="cps-note__meta">{{ reviewMeta }}</text>
          </view>
          <text class="cps-note__text">{{ record.reviewOpinion || '暂无复核意见' }}</text>
        </view>
      </view>

      <!-- 待整改：填写三要素 + 上传整改照片 -->
      <template v-if="editable">
        <view class="u-card cps-form-card">
          <view class="cps-card-title">
            <text class="cps-card-title__main">整改内容</text>
          </view>

          <view class="cps-field">
            <view class="cps-field__head">
              <text class="cps-field__label">
                原因分析<text class="cps-field__required">*</text>
              </text>
            </view>
            <textarea
              class="u-textarea"
              :value="form.reason"
              :maxlength="500"
              placeholder="说明问题产生的根本原因"
              auto-height
              @input="onInput('reason', $event)"
            />
          </view>

          <view class="cps-field">
            <view class="cps-field__head">
              <text class="cps-field__label">
                短期措施<text class="cps-field__required">*</text>
              </text>
            </view>
            <textarea
              class="u-textarea"
              :value="form.shortTermMeasure"
              :maxlength="500"
              placeholder="说明立即采取的纠正措施"
              auto-height
              @input="onInput('shortTermMeasure', $event)"
            />
          </view>

          <view class="cps-field">
            <view class="cps-field__head">
              <text class="cps-field__label">
                长期措施<text class="cps-field__required">*</text>
              </text>
            </view>
            <textarea
              class="u-textarea"
              :value="form.longTermMeasure"
              :maxlength="500"
              placeholder="说明防止再发的长期措施"
              auto-height
              @input="onInput('longTermMeasure', $event)"
            />
          </view>

          <view class="cps-field">
            <view class="cps-field__head">
              <text class="cps-field__label">
                整改照片<text class="cps-field__required">*</text>
              </text>
            </view>
            <text class="cps-field__hint">上传整改后照片，支持拍照或从相册选择</text>
            <view class="cps-photo-grid">
              <view
                v-for="(photo, index) in photos"
                :key="photo.id"
                class="cps-photo-tile"
                hover-class="u-tap"
                @click="previewPhoto(index)"
              >
                <image class="cps-photo__img" :src="photoUrl(photo.url)" mode="aspectFill" />
                <text class="cps-photo-del" @click.stop="removePhoto(index)">×</text>
              </view>
              <button
                class="cps-photo-add"
                hover-class="u-tap"
                :disabled="uploading"
                @click="pickPhotos"
              >
                <text class="cps-photo-add__plus">＋</text>
                <text class="cps-photo-add__text">{{ uploading ? '上传中' : '拍照/选择' }}</text>
              </button>
            </view>
          </view>
        </view>

        <view class="cps-submit-bar">
          <text v-if="formHint" class="cps-submit-hint">{{ formHint }}</text>
          <button
            class="u-btn u-btn--primary u-btn--block cps-submit-btn"
            :disabled="submitting || !canSubmit"
            @click="submit"
          >
            {{ submitting ? '提交中…' : '提交整改' }}
          </button>
        </view>
      </template>

      <!-- 非待整改状态：只读说明 -->
      <view v-else class="u-result cps-done-result">
        <text class="u-result__icon">{{ record.recordStatus === 'JUDGED' ? '✅' : 'ℹ️' }}</text>
        <text class="u-result__title">{{ statusLabel(record.recordStatus) }}</text>
        <text class="u-result__desc">{{ statusDesc(record.recordStatus) }}</text>
        <text v-if="record.recordStatus === 'JUDGED' && record.rectifySubmittedAt" class="u-result__desc">
          提交时间：{{ formatTime(record.rectifySubmittedAt) }}
        </text>
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
import type { CpsInspectionRecord, CpsInspectionType, CpsUploadedImage } from '@/types/cps'
import { resolveAssetUrl, resolveUploadedPhotoUrl, resolveUploadSources } from '@/utils/image'

const recordId = ref<number>(0)
const loading = ref(false)
const submitting = ref(false)
const uploading = ref(false)

const record = ref<CpsInspectionRecord | null>(null)
const typeOptions = ref<CpsInspectionType[]>([])
const photos = ref<CpsUploadedImage[]>([])

const form = reactive({
  reason: '',
  shortTermMeasure: '',
  longTermMeasure: '',
})

const formatTime = (t?: string | null) => (t ? t.replace('T', ' ').slice(0, 16) : '-')

const statusLabel = (s: string) =>
  ({
    PENDING: '待执行',
    IN_PROGRESS: '执行中',
    PENDING_REVIEW: '待复核',
    PENDING_RECTIFY: '待整改',
    AWAIT_CONFIRM: '待确认',
    JUDGED: '已结束',
  }[s] ?? s)

const statusBadgeClass = (s: string) => {
  if (s === 'PENDING_RECTIFY') return 'u-tag--orange'
  if (s === 'JUDGED') return 'u-tag--green'
  return 'u-tag--blue'
}

const statusDesc = (s: string) =>
  ({
    PENDING: '巡检尚未开始执行。',
    IN_PROGRESS: '巡检执行中，请先回到「执行中」页签完成巡检。',
    PENDING_REVIEW: '巡检已提交，等待 web 端复核；复核通过后会转入「待整改」。',
    PENDING_RECTIFY: '请填写原因分析、短期措施、长期措施并上传整改照片后提交整改。',
    JUDGED: '整改已提交，流程结束。',
    AWAIT_CONFIRM: '记录已锁定。',
  }[s] ?? '当前状态不可提交整改。')

const typeName = computed(() => {
  const code = record.value?.typeCode
  if (!code) return '-'
  return typeOptions.value.find((t) => t.code === code)?.name ?? code
})

const editable = computed(() => record.value?.recordStatus === 'PENDING_RECTIFY')

const aiConclusion = computed(() => {
  const r = record.value
  if (!r) return '-'
  const overall =
    ({ PASS: '合格', PARTIAL: '部分合格', PROBLEM: '存在问题', SUBMITTED: '已提交 AI 初审' } as Record<string, string>)[
      r.aiOverall ?? ''
    ] ?? r.aiOverall
  const parts: string[] = []
  if (overall) parts.push(`结论：${overall}`)
  if (r.aiScore != null) parts.push(`AI 得分：${r.aiScore}`)
  if (r.aiReason) parts.push(r.aiReason)
  return parts.length ? parts.join(' · ') : '暂无 AI 结论'
})

/** 复核人 · 轮次 · 时间 */
const reviewMeta = computed(() => {
  const r = record.value
  if (!r) return ''
  const parts: string[] = []
  if (r.reviewEmpName || r.reviewEmpNo) parts.push(r.reviewEmpName || r.reviewEmpNo || '')
  if (r.reviewRound) parts.push(`第 ${r.reviewRound} 轮`)
  if (r.reviewedAt) parts.push(formatTime(r.reviewedAt))
  return parts.join(' · ')
})

const formHint = computed(() => {
  if (!form.reason.trim()) return '请填写原因分析'
  if (!form.shortTermMeasure.trim()) return '请填写短期措施'
  if (!form.longTermMeasure.trim()) return '请填写长期措施'
  if (!photos.value.length) return '请至少上传 1 张整改照片'
  return ''
})

const canSubmit = computed(() => !formHint.value)

/** 图片 src：绝对 URL 原样，历史 /api 代理路径补 base */
const photoUrl = (url: string) => resolveAssetUrl(url)

const onInput = (field: 'reason' | 'shortTermMeasure' | 'longTermMeasure', event: unknown) => {
  const detail = event as
    | { detail?: { value?: string | number }; target?: { value?: string | number } }
    | undefined
  form[field] = String(detail?.detail?.value ?? detail?.target?.value ?? '')
}

const previewPhoto = (index: number) => {
  const urls = photos.value.map((photo) => resolveAssetUrl(photo.url)).filter(Boolean)
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

const removePhoto = (index: number) => {
  photos.value.splice(index, 1)
}

const uploadPhotos = async (sources: ReturnType<typeof resolveUploadSources>) => {
  if (!sources.length) return
  uploading.value = true
  try {
    for (const source of sources) {
      const uploaded = await cpsApi.uploadCpsAttachment(source)
      // MinIO 上传后 url 是可直接访问的绝对地址，原样保存与回显。
      photos.value.push({ id: uploaded.id, url: resolveUploadedPhotoUrl(uploaded), name: uploaded.name })
    }
  } catch (error) {
    uni.showToast({ title: error instanceof Error ? error.message : '照片上传失败', icon: 'none' })
  } finally {
    uploading.value = false
  }
}

// 拍照/选图：uni 原生 chooseImage（App 端调起系统相机/相册）
const pickPhotos = () => {
  uni.chooseImage({
    count: 9,
    sizeType: ['compressed'],
    sourceType: ['camera', 'album'],
    success: (result) => {
      void uploadPhotos(resolveUploadSources(result))
    },
    fail: (err) => {
      const message = String((err as { errMsg?: string } | undefined)?.errMsg ?? '')
      if (/cancel/i.test(message)) return
      uni.showToast({ title: message || '拍照失败', icon: 'none' })
    },
  })
}

const load = async () => {
  if (!recordId.value) return
  loading.value = true
  try {
    const detail = await cpsApi.getInspectionRecord(recordId.value)
    record.value = detail.record
  } catch (error) {
    uni.showToast({ title: error instanceof Error ? error.message : '加载巡检记录失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}

const loadTypes = async () => {
  try {
    typeOptions.value = await cpsApi.listCpsInspectionTypes()
  } catch {
    // 类型字典拿不到时回退显示编码
  }
}

const backToList = () => {
  const pages = typeof getCurrentPages === 'function' ? getCurrentPages() : []
  if (pages.length > 1) {
    uni.navigateBack()
  } else {
    uni.redirectTo({ url: '/views/cps/IssueListView' })
  }
}

const submit = async () => {
  if (!record.value || submitting.value) return
  if (!canSubmit.value) {
    uni.showToast({ title: formHint.value || '请完善整改内容', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    const result = await cpsApi.submitRectify(record.value.id, {
      reason: form.reason.trim(),
      shortTermMeasure: form.shortTermMeasure.trim(),
      longTermMeasure: form.longTermMeasure.trim(),
      attachmentIds: photos.value.map((photo) => photo.id),
    })
    record.value = result
    uni.showToast({ title: '整改已提交，流程结束', icon: 'success', duration: 1500 })
    // 等 toast 完整展示后再回列表（列表 onShow 会重新拉取待整改分组）
    setTimeout(() => {
      backToList()
    }, 1500)
  } catch (error) {
    uni.showToast({ title: error instanceof Error ? error.message : '提交整改失败', icon: 'none' })
  } finally {
    submitting.value = false
  }
}

onLoad((query?: Record<string, string | string[] | undefined>) => {
  const raw = Array.isArray(query?.recordId) ? query?.recordId[0] : query?.recordId
  recordId.value = Number(raw)
  void load()
  void loadTypes()
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

.u-btn[disabled] {
  opacity: 0.5;
}

.u-textarea {
  width: 100%;
  min-height: 96px;
  padding: 10px 14px;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  background: #ffffff;
  font-size: 15px;
  line-height: 1.6;
  color: var(--cps-ink);
}

.u-card {
  background: #ffffff;
  border-radius: 14px;
  border: 1px solid #e2e8f0;
  padding: 16px;
  box-shadow: 0 4px 16px rgba(15, 118, 110, 0.06);
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

.u-tag--green {
  background: #dcfce7;
  color: #15803d;
}

.u-tag--orange {
  background: #ffedd5;
  color: #c2410c;
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

.u-desc {
  display: grid;
}

.u-desc__row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 8px 0;
  border-bottom: 1px solid #f1f5f9;
}

.u-desc__row:last-child {
  border-bottom: none;
}

.u-desc__label {
  flex: 0 0 76px;
  color: var(--cps-muted);
  font-size: 13px;
}

.u-desc__value {
  flex: 1;
  color: var(--cps-ink);
  font-size: 13px;
  font-weight: 600;
  word-break: break-all;
}
/* @end-inline-uni-controls */

.cps-page {
  width: 100%;
  max-width: 960px;
  margin: 0 auto;
  padding: 16px 20px 140px;
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

.cps-summary-card,
.cps-form-card {
  margin-bottom: 12px;
}

.cps-card-title {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--cps-border);
}
.cps-card-title__main { color: var(--cps-ink); font-size: 16px; font-weight: 800; }
.cps-card-title__hint { color: var(--cps-faint); font-size: 12px; font-weight: 600; }

.cps-note {
  display: grid;
  gap: 4px;
  margin-top: 10px;
  padding: 10px 12px;
  border-radius: var(--cps-radius-md);
  background: var(--cps-cloud);
}
.cps-note__head { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 8px; }
.cps-note__label { color: var(--cps-teal-deep); font-size: 12px; font-weight: 800; }
.cps-note__meta { color: var(--cps-faint); font-size: 12px; }
.cps-note__text { color: var(--cps-ink-2); font-size: 13px; line-height: 1.6; }

.cps-field {
  display: grid;
  gap: 6px;
  padding: 10px 0;
}
.cps-field + .cps-field { border-top: 1px dashed var(--cps-border); }
.cps-field__head { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.cps-field__label { color: var(--cps-ink); font-size: 14px; font-weight: 700; }
.cps-field__required { margin-left: 2px; color: var(--cps-red); }
.cps-field__count { color: #16a34a; font-size: 12px; font-weight: 800; }
.cps-field__count--lack { color: var(--cps-red); }
.cps-field__hint { color: var(--cps-faint); font-size: 12px; line-height: 1.5; }

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

.cps-submit-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 99;
  display: grid;
  gap: 6px;
  max-width: 960px;
  margin: 0 auto;
  padding: 12px 20px max(12px, env(safe-area-inset-bottom));
  background: linear-gradient(180deg, rgba(248, 250, 252, 0), #f8fafc 40%);
}
.cps-submit-hint { color: var(--cps-warn-deep, #b45309); font-size: 12px; font-weight: 700; text-align: center; }
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
