<template>
  <main class="cps-page cps-create-page">
    <header class="cps-create-hero">
      <div class="cps-create-hero__main">
        <p class="cps-create-hero__eyebrow">CPS 现场巡检</p>
        <h1>新建问题</h1>
        <div class="cps-create-progress" aria-label="填报进度">
          <span v-for="item in progressItems" :key="item.label" :class="{ 'is-done': item.done }">
            {{ item.label }}
          </span>
        </div>
      </div>
      <span class="cps-create-hero__badge">AI 辅助</span>
    </header>

    <van-form class="cps-create-form" @submit="submit">
      <section class="cps-detail-card cps-create-card">
        <div class="cps-card-title">
          <div>
            <p>现场证据</p>
            <h2>问题照片</h2>
          </div>
          <span class="cps-count-badge">{{ images.length }}/5</span>
        </div>
        <p class="cps-create-card__hint">最多上传 5 张，仅第 1 张参与 AI 识别</p>
        <ImageUploader v-model="images" :max="5" @first-image-ready="onFirstImageReady" />
      </section>

      <section class="cps-create-card-stack">
        <LocationSelector v-model="location" />
        <CategorySelector v-model="category" />
      </section>

      <van-notice-bar v-if="inspecting" color="#0F766E" background="#CCFBF1" text="AI 正在识别首张图片，请稍候" />

      <section v-if="aiSuggestionSummary" class="cps-detail-card cps-create-card cps-create-ai">
        <div class="cps-card-title">
          <div>
            <p>AI 建议</p>
            <h2>原因措施</h2>
          </div>
          <span class="cps-count-badge">可修改</span>
        </div>
        <pre>{{ aiSuggestionSummary }}</pre>
      </section>

      <van-cell-group inset title="问题描述" class="cps-create-cell-group">
        <van-field
          v-model="description"
          rows="5"
          autosize
          type="textarea"
          placeholder="请输入现场问题描述"
          show-word-limit
          maxlength="500"
        >
          <template #right-icon>
            <button
              type="button"
              class="cps-voice-button"
              aria-label="语音转文字"
              data-test="voice-transcribe"
              @click.stop.prevent="appendVoiceText"
            >
              <svg aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M12 3a3 3 0 0 0-3 3v6a3 3 0 0 0 6 0V6a3 3 0 0 0-3-3Z" />
                <path d="M19 11a7 7 0 0 1-14 0" />
                <path d="M12 18v3" />
                <path d="M8 21h8" />
              </svg>
            </button>
          </template>
        </van-field>
      </van-cell-group>

      <van-cell-group inset title="派发信息" class="cps-create-cell-group">
        <van-field
          v-model="feedbackEmpNo"
          label="反馈人"
          placeholder="按工厂、区域、拉线、工序自动匹配，可手动修改"
        />
      </van-cell-group>

      <div class="cps-sticky-submit">
        <van-button
          data-test="submit"
          native-type="submit"
          round
          block
          type="primary"
          class="cps-primary-button"
          :loading="submitting"
          :disabled="!canSubmit"
        >
          提交并派发
        </van-button>
      </div>
    </van-form>
  </main>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import { inspectCpsImage, transcribeIssueVoice } from '@/api/cps/ai'
import { createCpsIssue } from '@/api/cps/issue'
import { getFeedbackHandler } from '@/api/cps/master'
import CategorySelector from '@/components/cps/CategorySelector.vue'
import ImageUploader from '@/components/cps/ImageUploader.vue'
import LocationSelector from '@/components/cps/LocationSelector.vue'
import type { CpsAiSuggestionPayload, CpsUploadedImage } from '@/types/cps'

interface ProgressItem {
  label: string
  done: boolean
}

const location = ref({
  factory: '',
  area: '',
  line: '',
  process: '',
})

const category = ref({
  categoryL1Id: null as number | null,
  categoryL2Id: null as number | null,
})

const images = ref<CpsUploadedImage[]>([])
const description = ref<string>('')
const feedbackEmpNo = ref<string>('')
const submitting = ref<boolean>(false)
const inspecting = ref<boolean>(false)
const aiSuggestion = ref<CpsAiSuggestionPayload | null>(null)

const aiSuggestionSummary = computed<string>(() => {
  if (!aiSuggestion.value) return ''
  return [
    aiSuggestion.value.reasonSuggestion ? `原因建议：${aiSuggestion.value.reasonSuggestion}` : '',
    aiSuggestion.value.measureSuggestion ? `措施建议：${aiSuggestion.value.measureSuggestion}` : '',
  ]
    .filter(Boolean)
    .join('\n')
})

const progressItems = computed<ProgressItem[]>(() => [
  { label: '照片', done: images.value.length >= 1 && images.value.length <= 5 },
  { label: '位置', done: Boolean(location.value.factory && location.value.area && location.value.line && location.value.process) },
  { label: '分类', done: Boolean(category.value.categoryL1Id && category.value.categoryL2Id) },
  { label: '描述', done: Boolean(description.value.trim()) },
])

const canSubmit = computed<boolean>(
  () =>
    images.value.length >= 1 &&
    images.value.length <= 5 &&
    Boolean(location.value.factory) &&
    Boolean(location.value.area) &&
    Boolean(location.value.line) &&
    Boolean(location.value.process) &&
    Boolean(category.value.categoryL1Id) &&
    Boolean(category.value.categoryL2Id) &&
    Boolean(description.value.trim()) &&
    Boolean(feedbackEmpNo.value.trim()) &&
    !submitting.value,
)

watch(
  () => ({ ...location.value }),
  async (value): Promise<void> => {
    if (!value.factory || !value.area || !value.line || !value.process) return
    const handler = await getFeedbackHandler({
      factory: value.factory,
      area: value.area,
      line: value.line,
      process: value.process,
    })
    feedbackEmpNo.value = handler.empNo
  },
)

const onFirstImageReady = async (fileId: number): Promise<void> => {
  inspecting.value = true
  try {
    const suggestion = await inspectCpsImage(fileId)
    aiSuggestion.value = suggestion
    category.value.categoryL1Id = suggestion.aiCategoryL1Id
    category.value.categoryL2Id = suggestion.aiCategoryL2Id
  } finally {
    inspecting.value = false
  }
}

const appendVoiceText = async (): Promise<void> => {
  const text = await transcribeIssueVoice()
  if (!text.trim()) return
  description.value = [description.value.trim(), text.trim()].filter(Boolean).join('\n')
}

const submit = async (): Promise<void> => {
  if (!canSubmit.value) return
  submitting.value = true
  try {
    const result = await createCpsIssue({
      factory: location.value.factory,
      area: location.value.area,
      line: location.value.line,
      process: location.value.process,
      aiCategoryL1Id: aiSuggestion.value?.aiCategoryL1Id ?? null,
      aiCategoryL2Id: aiSuggestion.value?.aiCategoryL2Id ?? null,
      categoryL1Id: category.value.categoryL1Id!,
      categoryL2Id: category.value.categoryL2Id!,
      description: description.value.trim(),
      feedbackEmpNo: feedbackEmpNo.value.trim(),
      issueAttachmentIds: images.value.map((image) => image.id),
      aiSuggestion: aiSuggestion.value ?? undefined,
    })
    uni.navigateTo({ url: `/views/cps/IssueDetailView?id=${result.issueId}` })
  } finally {
    submitting.value = false
  }
}

defineExpose({
  images,
  location,
  category,
  description,
  feedbackEmpNo,
  canSubmit,
  aiSuggestion,
  aiSuggestionSummary,
  onFirstImageReady,
  appendVoiceText,
  submit,
})
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
  height: 100vh;
  min-height: 100dvh;
  margin: 0;
  padding: 28rpx 24rpx 152rpx;
  overflow-x: hidden;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  touch-action: pan-y;
}

.cps-page > * {
  width: 100%;
  max-width: 100%;
  min-width: 0;
}

.cps-create-page,
.cps-create-form,
.cps-create-card-stack {
  display: grid;
  align-content: start;
  gap: 26rpx;
}

.cps-create-hero {
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

.cps-create-hero::before {
  position: absolute;
  top: 28rpx;
  bottom: 28rpx;
  left: 20rpx;
  width: 8rpx;
  border-radius: 999rpx;
  background: #f97316;
  content: "";
}

.cps-create-hero__main {
  display: grid;
  gap: 12rpx;
  min-width: 0;
  max-width: 100%;
}

.cps-create-hero__eyebrow {
  margin: 0;
  color: rgba(255, 255, 255, 0.82);
  font-size: 26rpx;
  font-weight: 900;
  line-height: 36rpx;
}

.cps-create-hero h1 {
  margin: 0;
  color: #ffffff;
  font-size: 46rpx;
  font-weight: 950;
  line-height: 58rpx;
  overflow-wrap: anywhere;
}

.cps-create-progress {
  display: flex;
  flex-wrap: wrap;
  gap: 10rpx;
  min-width: 0;
  max-width: 100%;
}

.cps-create-progress span,
.cps-create-hero__badge,
.cps-count-badge {
  display: inline-flex;
  align-items: center;
  min-height: 48rpx;
  border-radius: 999rpx;
  padding: 0 16rpx;
  font-size: 26rpx;
  font-weight: 900;
  line-height: 36rpx;
  white-space: nowrap;
}

.cps-create-progress span,
.cps-create-hero__badge {
  background: rgba(255, 255, 255, 0.16);
  color: #ffffff;
}

.cps-create-progress span.is-done {
  background: #ccfbf1;
  color: #0f766e;
}

.cps-detail-card {
  display: grid;
  width: 100%;
  min-width: 0;
  max-width: 100%;
  gap: 24rpx;
  overflow: hidden;
  border: 2rpx solid rgba(20, 184, 166, 0.16);
  border-radius: 20rpx;
  padding: 30rpx;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 18rpx 52rpx rgba(15, 23, 42, 0.08);
}

.cps-card-title {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 18rpx;
  min-width: 0;
}

.cps-card-title p {
  margin: 0;
  color: #0f766e;
  font-size: 26rpx;
  font-weight: 900;
  line-height: 36rpx;
}

.cps-card-title h2 {
  margin: 4rpx 0 0;
  color: #0f172a;
  font-size: 36rpx;
  font-weight: 950;
  line-height: 46rpx;
}

.cps-count-badge {
  min-height: 52rpx;
  background: #dbeafe;
  color: #1d4ed8;
}

.cps-create-card__hint {
  margin: 0;
  color: #64748b;
  font-size: 28rpx;
  font-weight: 800;
  line-height: 40rpx;
}

.cps-create-ai {
  border-left: 8rpx solid #2563eb;
}

.cps-create-ai pre {
  min-width: 0;
  margin: 0;
  border-radius: 16rpx;
  padding: 20rpx;
  background: #f0fdfa;
  color: #0f172a;
  font-family: inherit;
  font-size: 30rpx;
  font-weight: 700;
  line-height: 46rpx;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.cps-create-cell-group {
  width: 100%;
  min-width: 0;
}

.cps-create-cell-group :deep(.van-cell) {
  align-items: flex-start;
  min-width: 0;
  max-width: 100%;
  overflow: hidden;
}

.cps-create-cell-group :deep(.van-field__label) {
  width: 176rpx;
  padding-top: 16rpx;
  color: #0f172a;
  font-weight: 800;
}

.cps-create-cell-group :deep(.van-cell__value),
.cps-create-cell-group :deep(.van-field__body),
.cps-create-cell-group :deep(.van-field__control) {
  min-width: 0;
  max-width: 100%;
}

.cps-voice-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 88rpx;
  height: 88rpx;
  min-width: 88rpx;
  border: 2rpx solid #ccfbf1;
  border-radius: 999rpx;
  margin-left: 14rpx;
  background: #ecfeff;
  color: #0f766e;
  box-shadow: 0 10rpx 24rpx rgba(20, 184, 166, 0.14);
}

.cps-voice-button svg {
  width: 42rpx;
  height: 42rpx;
}

.cps-sticky-submit {
  position: sticky;
  bottom: 0;
  z-index: 10;
  width: 100%;
  max-width: 100%;
  padding: 28rpx 24rpx max(24rpx, env(safe-area-inset-bottom));
  background: linear-gradient(180deg, rgba(248, 250, 252, 0), #f8fafc 34%, #f8fafc 100%);
  overflow: hidden;
}

.cps-primary-button {
  min-height: 108rpx;
  border: 0;
  border-radius: 999rpx;
  background: linear-gradient(135deg, #14b8a6 0%, #2563eb 100%);
  box-shadow: 0 18rpx 44rpx rgba(37, 99, 235, 0.22);
  font-size: 36rpx;
  font-weight: 900;
}

.cps-primary-button:disabled {
  background: #cbd5e1;
  box-shadow: none;
}
</style>
