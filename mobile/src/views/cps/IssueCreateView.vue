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
      <section class="cps-detail-card">
        <div class="cps-card-title">
          <div>
            <p>现场证据</p>
            <h2>问题照片</h2>
          </div>
          <span class="cps-count-badge">{{ images.length }}/5</span>
        </div>
        <p class="cps-create-card__hint">最多上传 5 张，仅第 1 张参与 AI 识别</p>

        <div class="cps-uploader">
          <button
            v-for="(image, index) in images"
            :key="image.id"
            type="button"
            class="cps-uploader__preview"
            @tap="previewImage(index)"
          >
            <img class="cps-uploader__image" :src="imagePreviewSources[image.id] || ''" :alt="image.name" />
            <span
              v-if="!uploadingImage"
              class="cps-uploader__delete"
              @tap.stop="removeImage(index)"
            >
              ×
            </span>
          </button>

          <button
            v-if="images.length < 5"
            type="button"
            class="cps-uploader__upload"
            :disabled="uploadingImage"
            @tap="chooseAndUploadImages"
          >
            <span class="cps-uploader__plus">+</span>
            <span class="cps-uploader__text">{{ uploadingImage ? '上传中' : '上传图片' }}</span>
          </button>
        </div>
      </section>

      <section class="cps-detail-card cps-selector-card">
        <div class="cps-card-title">
          <div>
            <p>位置选择</p>
            <h2>工厂 / 区域 / 拉线 / 工序</h2>
          </div>
        </div>

        <div class="cps-selector-list">
          <div class="cps-selector-row">
            <span class="cps-selector-label">工厂</span>
            <div class="cps-choice-grid">
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
              <span v-if="!factories.length" class="cps-muted-text">暂无工厂数据</span>
            </div>
          </div>

          <div class="cps-selector-row">
            <span class="cps-selector-label">区域</span>
            <div class="cps-choice-grid">
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
              <span v-if="!areas.length" class="cps-muted-text">先选择工厂</span>
            </div>
          </div>

          <div v-if="location.area" class="cps-selector-row">
            <span class="cps-selector-label">拉线</span>
            <div class="cps-choice-grid">
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
              <span v-if="!lines.length" class="cps-muted-text">暂无拉线数据</span>
            </div>
          </div>

          <div v-if="location.area" class="cps-selector-row">
            <span class="cps-selector-label">工序</span>
            <div class="cps-choice-grid">
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
              <span v-if="!processes.length" class="cps-muted-text">暂无工序数据</span>
            </div>
          </div>
        </div>
      </section>

      <section class="cps-detail-card cps-selector-card">
        <div class="cps-card-title">
          <div>
            <p>问题分类</p>
            <h2>一级 / 二级</h2>
          </div>
        </div>

        <div class="cps-selector-list">
          <div class="cps-selector-row">
            <span class="cps-selector-label">一级</span>
            <div class="cps-choice-grid">
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
              <span v-if="!level1Categories.length" class="cps-muted-text">暂无一级分类</span>
            </div>
          </div>

          <div class="cps-selector-row">
            <span class="cps-selector-label">二级</span>
            <div class="cps-choice-grid">
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
              <span v-if="!level2Categories.length" class="cps-muted-text">先选择一级分类</span>
            </div>
          </div>
        </div>
      </section>

      <van-notice-bar v-if="inspecting" color="#0F766E" background="#CCFBF1" text="AI 正在识别首张图片，请稍候" />

      <section v-if="aiSuggestionSummary" class="cps-detail-card cps-create-ai">
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
          :model-value="feedbackPersonLabel"
          label="反馈人"
          placeholder="选择反馈人"
          readonly
          is-link
          @click="openFeedbackPicker"
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

  <div v-if="feedbackPicker.visible" class="cps-person-picker-mask" @click.self="closeFeedbackPicker">
    <section class="cps-person-picker" role="dialog" aria-label="选择反馈人">
      <div class="cps-person-picker__head">
        <strong>选择反馈人</strong>
        <button type="button" class="cps-person-picker__close" aria-label="关闭" @tap="closeFeedbackPicker">×</button>
      </div>
      <div class="cps-person-picker__search">
        <input v-model.trim="feedbackPicker.keyword" type="search" placeholder="输入姓名或工号搜索" @keyup.enter="searchFeedbackPeople" />
        <button type="button" :disabled="feedbackPicker.loading" @tap="searchFeedbackPeople">搜索</button>
      </div>
      <div v-if="feedbackPicker.loading" class="cps-person-picker__state">搜索中...</div>
      <div v-else class="cps-person-picker__list">
        <button
          v-for="person in feedbackPickerPeople"
          :key="person.empNo"
          type="button"
          class="cps-person-picker__option"
          @tap="selectFeedbackPerson(person)"
        >
          <strong>{{ person.empName || person.empNo }}</strong>
          <span>{{ person.empNo }}</span>
        </button>
        <p v-if="!feedbackPickerPeople.length" class="cps-person-picker__state">未找到人员，请调整关键词后搜索</p>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'

import { inspectCpsImage, transcribeIssueVoice } from '@/api/cps/ai'
import { getCpsAttachmentBase64, uploadCpsAttachment, type CpsAttachmentUploadSource } from '@/api/cps/attachment'
import { createCpsIssue } from '@/api/cps/issue'
import {
  getAreas,
  getCategories,
  getFactories,
  getFeedbackHandler,
  getLines,
  getProcesses,
  searchCpsEmployees,
  type CpsEmployeeOption,
  type CpsOption,
} from '@/api/cps/master'
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
const imagePreviewSources = ref<Record<number, string>>({})
const description = ref('')
const feedbackEmpNo = ref('')
const feedbackPerson = ref<CpsEmployeeOption | null>(null)
const feedbackPicker = ref({
  visible: false,
  keyword: '',
  loading: false,
  results: [] as CpsEmployeeOption[],
})
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

const feedbackPersonLabel = computed(() => {
  if (!feedbackEmpNo.value) return ''
  const name = feedbackPerson.value?.empName
  return name && name !== feedbackEmpNo.value ? `${name} (${feedbackEmpNo.value})` : feedbackEmpNo.value
})
const feedbackPickerPeople = computed(() => feedbackPicker.value.results)

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
    if (!value.factory || !value.area || !value.line || !value.process) {
      feedbackEmpNo.value = ''
      feedbackPerson.value = null
      feedbackPicker.value.results = []
      return
    }
    const handlers = await getFeedbackHandler({
      factory: value.factory,
      area: value.area,
      line: value.line,
      process: value.process,
    })
    feedbackEmpNo.value = ''
    feedbackPerson.value = null
    feedbackPicker.value.results = handlers
  },
)

const openFeedbackPicker = () => {
  feedbackPicker.value = {
    visible: true,
    keyword: '',
    loading: false,
    results: feedbackPicker.value.results,
  }
}

const closeFeedbackPicker = () => {
  feedbackPicker.value.visible = false
}

const searchFeedbackPeople = async () => {
  const keyword = feedbackPicker.value.keyword.trim()
  if (!keyword || feedbackPicker.value.loading) return
  feedbackPicker.value.loading = true
  try {
    const results = await searchCpsEmployees(keyword)
    feedbackPicker.value.results = [...new Map([...feedbackPicker.value.results, ...results].map((person) => [person.empNo, person])).values()]
  } finally {
    feedbackPicker.value.loading = false
  }
}

const selectFeedbackPerson = (person: CpsEmployeeOption) => {
  feedbackEmpNo.value = person.empNo
  feedbackPerson.value = person
  closeFeedbackPicker()
}

watch(
  () => category.value.categoryL1Id,
  async (parentId) => {
    level2Categories.value = parentId ? await getCategories(parentId) : []
  },
)

const chooseAndUploadImages = () => {
  const remaining = Math.max(5 - images.value.length, 0)
  if (remaining <= 0 || uploadingImage.value) return

  uni.chooseImage({
    count: remaining,
    sizeType: ['compressed', 'original'],
    sourceType: ['album', 'camera'],
    success: async (result) => {
      const paths = normalizeArray(result.tempFilePaths)
      const tempFiles = normalizeArray(result.tempFiles) as TempFileCandidate[]
      const sources = paths
        .map((path, index) => {
          const tempFile = tempFiles[index]
          if (typeof File !== 'undefined' && tempFile instanceof File) return tempFile
          const localFile = tempFile as UniTempFileLike | undefined
          if (typeof File !== 'undefined' && localFile?.file instanceof File) return localFile.file
          return localFile?.path || path
        })
        .filter(Boolean) as CpsAttachmentUploadSource[]
      if (!sources.length) return

      const shouldInspectFirstImage = images.value.length === 0
      uploadingImage.value = true
      try {
        const next = [...images.value]
        for (const source of sources) {
          const uploaded = await uploadCpsAttachment(source)
          next.push(uploaded)
          void imageToBase64(uploaded)
        }
        images.value = next
        if (shouldInspectFirstImage && next.length > 0) {
          await onFirstImageReady(next[0].id)
        }
      } finally {
        uploadingImage.value = false
      }
    },
    fail: (error) => {
      if ((error.errMsg || '').toLowerCase().includes('cancel')) return
      uni.showToast({
        title: '图片选择失败',
        icon: 'none',
      })
    },
  })
}

const normalizeArray = <T,>(value: T | T[] | undefined) => {
  if (value === undefined) return []
  return Array.isArray(value) ? value : [value]
}

const removeImage = (index: number) => {
  const removed = images.value[index]
  images.value = images.value.filter((_, itemIndex) => itemIndex !== index)
  if (!removed) return
  const { [removed.id]: _, ...remainingSources } = imagePreviewSources.value
  imagePreviewSources.value = remainingSources
}

const previewImage = (index: number) => {
  const target = images.value[index]
  if (!target) return
  const current = imagePreviewSources.value[target.id]
  const urls = images.value.map((image) => imagePreviewSources.value[image.id]).filter(Boolean)
  if (!current || !urls.length || typeof uni === 'undefined' || typeof uni.previewImage !== 'function') return
  uni.previewImage({
    urls,
    current,
  })
}

const imageToBase64 = async (image: CpsUploadedImage) => {
  try {
    const base64 = await getCpsAttachmentBase64(image.url)
    const data = base64.trim()
    const source = data.startsWith('data:')
      ? data
      : data.includes(';base64,')
        ? `data:${data}`
        : `data:image/jpeg;base64,${data.replace(/\s/g, '')}`
    imagePreviewSources.value = { ...imagePreviewSources.value, [image.id]: source }
  } catch {
    uni.showToast({
      title: '图片预览加载失败',
      icon: 'none',
    })
  }
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
      feedbackEmpName: feedbackPerson.value?.empName,
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
  padding: 28rpx 24rpx 220rpx;
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
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: flex-start;
  justify-content: space-between;
  gap: 22rpx;
  min-height: 236rpx;
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
  gap: 14rpx;
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
  padding-top: 4rpx;
}

.cps-create-progress span,
.cps-create-hero__badge,
.cps-count-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
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

.cps-create-hero__badge {
  align-self: start;
  max-width: 180rpx;
  white-space: normal;
  text-align: center;
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
  display: block;
  border: 0;
  padding: 0;
  background: #e2e8f0;
}

.cps-uploader__image {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
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
  border-radius: 999rpx;
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
  grid-template-columns: 126rpx minmax(0, 1fr);
  gap: 16rpx;
  width: 100%;
  min-width: 0;
  padding: 22rpx 28rpx;
  border-bottom: 2rpx solid #f1f5f9;
}

.cps-selector-row:last-child {
  border-bottom: 0;
}

.cps-selector-label {
  padding-top: 10rpx;
  color: #0f172a;
  font-size: 28rpx;
  font-weight: 900;
  line-height: 38rpx;
}

.cps-choice-grid {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  gap: 12rpx;
  overflow: hidden;
}

.cps-choice-button {
  display: inline-flex;
  flex: 0 1 auto;
  align-items: center;
  justify-content: center;
  min-width: 136rpx;
  max-width: 100%;
  min-height: 68rpx;
  border: 2rpx solid #cbd5e1;
  border-radius: 999rpx;
  padding: 10rpx 20rpx;
  background: #ffffff;
  color: #334155;
  font-size: 28rpx;
  font-weight: 800;
  line-height: 36rpx;
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

.cps-person-picker-mask {
  position: fixed;
  z-index: 1000;
  inset: 0;
  display: flex;
  align-items: flex-end;
  background: rgba(15, 23, 42, 0.48);
}

.cps-person-picker {
  width: 100%;
  max-height: 78dvh;
  border-radius: 28rpx 28rpx 0 0;
  padding: 28rpx 24rpx calc(28rpx + env(safe-area-inset-bottom));
  background: #ffffff;
  box-shadow: 0 -16rpx 48rpx rgba(15, 23, 42, 0.16);
}

.cps-person-picker__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24rpx;
  margin-bottom: 24rpx;
}

.cps-person-picker__head strong {
  min-width: 0;
  color: #0f172a;
  font-size: 34rpx;
  font-weight: 900;
  line-height: 46rpx;
  overflow-wrap: anywhere;
}

.cps-person-picker__close {
  display: inline-flex;
  flex: 0 0 56rpx;
  align-items: center;
  justify-content: center;
  width: 56rpx;
  height: 56rpx;
  border: 0;
  border-radius: 50%;
  padding: 0;
  background: #f1f5f9;
  color: #475569;
  font-size: 42rpx;
  font-weight: 400;
  line-height: 56rpx;
}

.cps-person-picker__search {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 16rpx;
  margin-bottom: 20rpx;
}

.cps-person-picker__search input {
  min-width: 0;
  height: 76rpx;
  border: 2rpx solid #cbd5e1;
  border-radius: 12rpx;
  padding: 0 20rpx;
  outline: none;
  background: #f8fafc;
  color: #0f172a;
  font-size: 28rpx;
  line-height: 76rpx;
}

.cps-person-picker__search input:focus {
  border-color: #14b8a6;
  background: #ffffff;
}

.cps-person-picker__search button {
  min-width: 112rpx;
  height: 76rpx;
  border: 0;
  border-radius: 12rpx;
  padding: 0 22rpx;
  background: #0f766e;
  color: #ffffff;
  font-size: 28rpx;
  font-weight: 800;
  line-height: 76rpx;
}

.cps-person-picker__search button:disabled {
  opacity: 0.6;
}

.cps-person-picker__list {
  max-height: calc(78dvh - 214rpx - env(safe-area-inset-bottom));
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
}

.cps-person-picker__option {
  display: grid;
  gap: 6rpx;
  width: 100%;
  min-height: 112rpx;
  border: 0;
  border-bottom: 2rpx solid #e2e8f0;
  padding: 20rpx 12rpx;
  background: #ffffff;
  color: #0f172a;
  text-align: left;
}

.cps-person-picker__option:active {
  background: #f0fdfa;
}

.cps-person-picker__option strong,
.cps-person-picker__option span {
  min-width: 0;
  overflow-wrap: anywhere;
}

.cps-person-picker__option strong {
  font-size: 30rpx;
  font-weight: 850;
  line-height: 40rpx;
}

.cps-person-picker__option span {
  color: #64748b;
  font-size: 26rpx;
  line-height: 36rpx;
}

.cps-person-picker__state {
  margin: 0;
  padding: 44rpx 20rpx;
  color: #64748b;
  font-size: 28rpx;
  line-height: 40rpx;
  text-align: center;
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
  position: fixed;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 99;
  width: 100vw;
  max-width: 100vw;
  padding: 24rpx 24rpx max(24rpx, env(safe-area-inset-bottom));
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
