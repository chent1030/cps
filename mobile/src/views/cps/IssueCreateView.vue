<template>
  <view class="cps-page cps-create-page">
    <view class="cps-create-hero">
      <view class="cps-create-hero__main">
        <text class="cps-create-hero__eyebrow">CPS 现场巡检</text>
        <text class="cps-create-hero__title">新建问题</text>
        <view class="cps-create-progress" aria-label="填报进度">
          <text v-for="item in progressItems" :key="item.label" :class="{ 'is-done': item.done }">
            {{ item.label }}
          </text>
        </view>
      </view>
      <text class="cps-create-hero__badge">AI 辅助</text>
    </view>

    <van-form class="cps-create-form" @submit="submit">
      <view class="cps-detail-card cps-create-card">
        <view class="cps-card-title">
          <view>
            <text class="cps-card-title__eyebrow">现场证据</text>
            <text class="cps-card-title__heading">问题照片</text>
          </view>
          <text class="cps-count-badge">{{ images.length }}/5</text>
        </view>
        <text class="cps-create-card__hint">最多上传 5 张，仅第 1 张参与 AI 识别</text>

        <view class="cps-uploader">
          <view
            v-for="(image, index) in images"
            :key="image.id"
            class="cps-uploader__preview"
            @tap="previewImage(index)"
          >
            <image class="cps-uploader__image" :src="image.url" mode="aspectFill" />
            <button
              v-if="!uploadingImage"
              type="button"
              class="cps-uploader__delete"
              @tap.stop="removeImage(index)"
            >
              ×
            </button>
          </view>

          <button
            v-if="images.length < 5"
            type="button"
            class="cps-uploader__upload"
            :disabled="uploadingImage"
            @tap="chooseAndUploadImages"
          >
            <text class="cps-uploader__plus">+</text>
            <text class="cps-uploader__text">{{ uploadingImage ? '上传中' : '上传图片' }}</text>
          </button>
        </view>
      </view>

      <view class="cps-detail-card cps-selector-card">
        <view class="cps-card-title">
          <view>
            <text class="cps-card-title__eyebrow">位置选择</text>
            <text class="cps-card-title__heading">工厂 / 区域 / 拉线 / 工序</text>
          </view>
        </view>

        <view class="cps-selector-list">
          <view class="cps-selector-row">
            <text class="cps-selector-label">工厂</text>
            <view class="cps-choice-grid">
              <button
                v-for="item in factories"
                :key="item.value"
                type="button"
                class="cps-choice-button"
                :class="{ 'is-selected': location.factory === String(item.value) }"
                @tap="selectFactory(item.value)"
              >
                {{ item.label }}
              </button>
              <text v-if="!factories.length" class="cps-muted-text">暂无工厂数据</text>
            </view>
          </view>

          <view class="cps-selector-row">
            <text class="cps-selector-label">区域</text>
            <view class="cps-choice-grid">
              <button
                v-for="item in areas"
                :key="item.value"
                type="button"
                class="cps-choice-button"
                :class="{ 'is-selected': location.area === String(item.value) }"
                @tap="selectArea(item.value)"
              >
                {{ item.label }}
              </button>
              <text v-if="!areas.length" class="cps-muted-text">先选择工厂</text>
            </view>
          </view>

          <view v-if="location.area" class="cps-selector-row">
            <text class="cps-selector-label">拉线</text>
            <view class="cps-choice-grid">
              <button
                v-for="item in lines"
                :key="item.value"
                type="button"
                class="cps-choice-button"
                :class="{ 'is-selected': location.line === String(item.value) }"
                @tap="selectLine(item.value)"
              >
                {{ item.label }}
              </button>
              <text v-if="!lines.length" class="cps-muted-text">暂无拉线数据</text>
            </view>
          </view>

          <view v-if="location.area" class="cps-selector-row">
            <text class="cps-selector-label">工序</text>
            <view class="cps-choice-grid">
              <button
                v-for="item in processes"
                :key="item.value"
                type="button"
                class="cps-choice-button"
                :class="{ 'is-selected': location.process === String(item.value) }"
                @tap="selectProcess(item.value)"
              >
                {{ item.label }}
              </button>
              <text v-if="!processes.length" class="cps-muted-text">暂无工序数据</text>
            </view>
          </view>
        </view>
      </view>

      <view class="cps-detail-card cps-selector-card">
        <view class="cps-card-title">
          <view>
            <text class="cps-card-title__eyebrow">问题分类</text>
            <text class="cps-card-title__heading">一级 / 二级</text>
          </view>
        </view>

        <view class="cps-selector-list">
          <view class="cps-selector-row">
            <text class="cps-selector-label">一级</text>
            <view class="cps-choice-grid">
              <button
                v-for="item in level1Categories"
                :key="item.value"
                type="button"
                class="cps-choice-button cps-choice-button--blue"
                :class="{ 'is-selected': category.categoryL1Id === Number(item.value) }"
                @tap="selectCategoryL1(item.value)"
              >
                {{ item.label }}
              </button>
              <text v-if="!level1Categories.length" class="cps-muted-text">暂无一级分类</text>
            </view>
          </view>

          <view class="cps-selector-row">
            <text class="cps-selector-label">二级</text>
            <view class="cps-choice-grid">
              <button
                v-for="item in level2Categories"
                :key="item.value"
                type="button"
                class="cps-choice-button"
                :class="{ 'is-selected': category.categoryL2Id === Number(item.value) }"
                @tap="selectCategoryL2(item.value)"
              >
                {{ item.label }}
              </button>
              <text v-if="!level2Categories.length" class="cps-muted-text">先选择一级分类</text>
            </view>
          </view>
        </view>
      </view>

      <van-notice-bar v-if="inspecting" color="#0F766E" background="#CCFBF1" text="AI 正在识别首张图片，请稍候" />

      <view v-if="aiSuggestionSummary" class="cps-detail-card cps-create-card cps-create-ai">
        <view class="cps-card-title">
          <view>
            <text class="cps-card-title__eyebrow">AI 建议</text>
            <text class="cps-card-title__heading">原因措施</text>
          </view>
          <text class="cps-count-badge">可修改</text>
        </view>
        <text class="cps-ai-text">{{ aiSuggestionSummary }}</text>
      </view>

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
              @tap.stop.prevent="appendVoiceText"
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

      <view class="cps-sticky-submit">
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
      </view>
    </van-form>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'

import { inspectCpsImage, transcribeIssueVoice } from '@/api/cps/ai'
import { uploadCpsAttachment, type CpsAttachmentUploadSource } from '@/api/cps/attachment'
import { createCpsIssue } from '@/api/cps/issue'
import { getAreas, getCategories, getFactories, getFeedbackHandler, getLines, getProcesses, type CpsOption } from '@/api/cps/master'
import type { CpsAiSuggestionPayload, CpsUploadedImage } from '@/types/cps'

interface ProgressItem {
  label: string
  done: boolean
}

interface UniTempFileLike {
  path?: string
  file?: File
}

type TempFileCandidate = File | UniTempFileLike

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

const factories = ref<CpsOption[]>([])
const areas = ref<CpsOption[]>([])
const lines = ref<CpsOption[]>([])
const processes = ref<CpsOption[]>([])
const level1Categories = ref<CpsOption[]>([])
const level2Categories = ref<CpsOption[]>([])
const images = ref<CpsUploadedImage[]>([])
const description = ref('')
const feedbackEmpNo = ref('')
const submitting = ref(false)
const inspecting = ref(false)
const uploadingImage = ref(false)
const aiSuggestion = ref<CpsAiSuggestionPayload | null>(null)

const aiSuggestionSummary = computed(() => {
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

const canSubmit = computed(
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
  async (value) => {
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

watch(
  () => category.value.categoryL1Id,
  async (parentId) => {
    level2Categories.value = parentId ? await getCategories(parentId) : []
  },
)

const chooseAndUploadImages = async () => {
  const remaining = Math.max(5 - images.value.length, 0)
  if (remaining <= 0 || uploadingImage.value) return

  const result = await chooseImages(remaining)
  if (!result) return

  const sources = resolveUploadSources(result).slice(0, remaining)
  if (!sources.length) return

  uploadingImage.value = true
  try {
    const next = [...images.value]
    for (const source of sources) {
      const uploaded = await uploadCpsAttachment(source)
      next.push(uploaded)
    }
    images.value = next
    if (next.length > 0 && images.value.length === next.length && next.length === sources.length) {
      await onFirstImageReady(next[0].id)
    }
  } finally {
    uploadingImage.value = false
  }
}

const chooseImages = (count: number) => {
  return new Promise((resolve: (value: UniApp.ChooseImageSuccessCallbackResult | null) => void, reject) => {
    uni.chooseImage({
      count,
      sizeType: ['compressed', 'original'],
      sourceType: ['album', 'camera'],
      success: resolve,
      fail(error) {
        if ((error.errMsg || '').toLowerCase().includes('cancel')) {
          resolve(null)
          return
        }
        reject(new Error(error.errMsg || 'chooseImage failed'))
      },
    })
  })
}

const resolveUploadSources = (result: UniApp.ChooseImageSuccessCallbackResult) => {
  const paths = normalizeArray(result.tempFilePaths)
  const tempFiles = normalizeArray(result.tempFiles) as TempFileCandidate[]
  return paths
    .map((path, index) => {
      const tempFile = tempFiles[index]
      if (typeof File !== 'undefined' && tempFile instanceof File) return tempFile
      const localFile = tempFile as UniTempFileLike | undefined
      if (typeof File !== 'undefined' && localFile?.file instanceof File) return localFile.file
      return localFile?.path || path
    })
    .filter(Boolean) as CpsAttachmentUploadSource[]
}

const normalizeArray = <T,>(value: T | T[] | undefined) => {
  if (value === undefined) return []
  return Array.isArray(value) ? value : [value]
}

const removeImage = (index: number) => {
  images.value = images.value.filter((_, itemIndex) => itemIndex !== index)
}

const previewImage = (index: number) => {
  const urls = images.value.map((image) => image.url)
  if (!urls.length || typeof uni === 'undefined' || typeof uni.previewImage !== 'function') return
  uni.previewImage({
    urls,
    current: urls[index],
  })
}

const selectFactory = async (factoryValue: CpsOption['value']) => {
  const factory = String(factoryValue)
  location.value = {
    factory,
    area: '',
    line: '',
    process: '',
  }
  areas.value = await getAreas(factory)
  lines.value = []
  processes.value = []
}

const selectArea = async (areaValue: CpsOption['value']) => {
  const area = String(areaValue)
  location.value = {
    ...location.value,
    area,
    line: '',
    process: '',
  }
  lines.value = await getLines(location.value.factory, area)
  processes.value = await getProcesses(location.value.factory, area)
}

const selectLine = async (lineValue: CpsOption['value']) => {
  const line = String(lineValue)
  location.value = {
    ...location.value,
    line,
    process: '',
  }
  processes.value = await getProcesses(location.value.factory, location.value.area, line || undefined)
}

const selectProcess = (processValue: CpsOption['value']) => {
  location.value = {
    ...location.value,
    process: String(processValue),
  }
}

const selectCategoryL1 = (categoryId: CpsOption['value']) => {
  category.value = {
    categoryL1Id: Number(categoryId),
    categoryL2Id: null,
  }
}

const selectCategoryL2 = (categoryId: CpsOption['value']) => {
  category.value = {
    ...category.value,
    categoryL2Id: Number(categoryId),
  }
}

const onFirstImageReady = async (fileId: number) => {
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

const appendVoiceText = async () => {
  const text = await transcribeIssueVoice()
  if (!text.trim()) return
  description.value = [description.value.trim(), text.trim()].filter(Boolean).join('\n')
}

const submit = async () => {
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

onMounted(async () => {
  factories.value = await getFactories()
  level1Categories.value = await getCategories()
})

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
.cps-create-form {
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
  color: rgba(255, 255, 255, 0.82);
  font-size: 26rpx;
  font-weight: 900;
  line-height: 36rpx;
}

.cps-create-hero__title {
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

.cps-create-progress text,
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

.cps-create-progress text,
.cps-create-hero__badge {
  background: rgba(255, 255, 255, 0.16);
  color: #ffffff;
}

.cps-create-progress text.is-done {
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

.cps-card-title__eyebrow {
  display: block;
  color: #0f766e;
  font-size: 26rpx;
  font-weight: 900;
  line-height: 36rpx;
}

.cps-card-title__heading {
  display: block;
  margin-top: 4rpx;
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
  color: #64748b;
  font-size: 28rpx;
  font-weight: 800;
  line-height: 40rpx;
}

.cps-uploader,
.cps-choice-grid {
  display: grid;
  width: 100%;
  min-width: 0;
  max-width: 100%;
  gap: 16rpx;
}

.cps-uploader {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.cps-uploader__preview,
.cps-uploader__upload {
  position: relative;
  width: 100%;
  aspect-ratio: 1;
  min-width: 0;
  min-height: 168rpx;
  overflow: hidden;
  border-radius: 18rpx;
}

.cps-uploader__preview {
  background: #e2e8f0;
}

.cps-uploader__image {
  display: block;
  width: 100%;
  height: 100%;
}

.cps-uploader__delete {
  position: absolute;
  top: 8rpx;
  right: 8rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44rpx;
  height: 44rpx;
  min-width: 44rpx;
  border: 0;
  border-radius: 999rpx;
  padding: 0;
  background: rgba(15, 23, 42, 0.68);
  color: #ffffff;
  font-size: 32rpx;
  font-weight: 900;
  line-height: 44rpx;
}

.cps-uploader__upload {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  border: 2rpx dashed #67e8f9;
  padding: 18rpx;
  background: #ecfeff;
  color: #0f766e;
}

.cps-uploader__plus {
  font-size: 58rpx;
  font-weight: 400;
  line-height: 58rpx;
}

.cps-uploader__text {
  font-size: 28rpx;
  font-weight: 800;
  line-height: 36rpx;
  text-align: center;
}

.cps-selector-card {
  padding: 30rpx 0 0;
}

.cps-selector-card .cps-card-title {
  padding: 0 30rpx;
}

.cps-selector-list {
  display: grid;
  width: 100%;
  min-width: 0;
  border-top: 2rpx solid #ccfbf1;
}

.cps-selector-row {
  display: grid;
  grid-template-columns: 150rpx minmax(0, 1fr);
  gap: 20rpx;
  width: 100%;
  min-width: 0;
  padding: 28rpx 30rpx;
  border-bottom: 2rpx solid #f1f5f9;
}

.cps-selector-row:last-child {
  border-bottom: 0;
}

.cps-selector-label {
  padding-top: 20rpx;
  color: #0f172a;
  font-size: 30rpx;
  font-weight: 900;
  line-height: 42rpx;
}

.cps-choice-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  overflow: hidden;
}

.cps-choice-button {
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 0;
  max-width: 100%;
  min-height: 96rpx;
  border: 2rpx solid #cbd5e1;
  border-radius: 18rpx;
  padding: 16rpx 18rpx;
  background: #ffffff;
  color: #334155;
  font-size: 32rpx;
  font-weight: 800;
  line-height: 42rpx;
  text-align: center;
  overflow-wrap: anywhere;
}

.cps-choice-button:active {
  transform: scale(0.98);
}

.cps-choice-button.is-selected {
  border-color: #14b8a6;
  background: linear-gradient(135deg, #ccfbf1 0%, #dbeafe 100%);
  color: #0f766e;
  box-shadow: 0 10rpx 28rpx rgba(20, 184, 166, 0.18);
}

.cps-choice-button--blue.is-selected {
  border-color: #2563eb;
  color: #1d4ed8;
}

.cps-muted-text {
  color: #64748b;
  font-size: 30rpx;
  font-weight: 800;
  line-height: 44rpx;
}

.cps-create-ai {
  border-left: 8rpx solid #2563eb;
}

.cps-ai-text {
  min-width: 0;
  border-radius: 16rpx;
  padding: 20rpx;
  background: #f0fdfa;
  color: #0f172a;
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
