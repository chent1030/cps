<template>
  <main class="cps-page cps-create-page">
    <header class="cps-hero">
      <div class="cps-hero__main">
        <p class="cps-hero__eyebrow">CPS 现场巡检</p>
        <h1>新建问题</h1>
        <div class="cps-create-progress">
          <a-tag v-for="item in progressItems" :key="item.label" :color="item.done ? 'cyan' : 'default'" class="cps-progress-tag">
            {{ item.label }}
          </a-tag>
        </div>
      </div>
      <a-tag color="blue" class="cps-hero__badge">AI 辅助</a-tag>
    </header>

    <a-row :gutter="[16, 16]">
      <!-- 左列：照片 + 描述 -->
      <a-col :xs="{ span: 24 }" :lg="{ span: 12 }">
        <a-card class="cps-card" :body-style="{ padding: '16px' }">
          <template #title>
            <div class="cps-card-title">
              <div>
                <p class="cps-card-title__sub">现场证据</p>
                <h2 class="cps-card-title__main">问题照片</h2>
              </div>
              <a-tag color="blue">{{ draft.images.length }}/5</a-tag>
            </div>
          </template>
          <p class="cps-create-card__hint">最多上传 5 张，仅第 1 张参与 AI 识别</p>

          <a-upload
            list-type="picture-card"
            :file-list="uploadFileList"
            :max-count="5"
            :custom-request="customRequest"
            :before-upload="beforeUpload"
            :show-upload-list="false"
            multiple
            accept="image/*"
          >
            <div v-if="draft.images.length < 5" class="cps-uploader__trigger">
              <span class="cps-uploader__plus">+</span>
              <span class="cps-uploader__text">{{ uploading ? '上传中' : '上传图片' }}</span>
            </div>
          </a-upload>

          <div class="cps-uploader__grid">
            <div
              v-for="(image, index) in draft.images"
              :key="image.id"
              class="cps-uploader__item"
              @click="onPreviewImage(index)"
            >
              <img :src="draft.imagePreviewSources[image.id] || ''" :alt="image.name" />
              <span v-if="!uploading" class="cps-uploader__delete" @click.stop="onRemoveImage(index)">×</span>
              <span class="cps-uploader__index">{{ index + 1 }}</span>
            </div>
          </div>
        </a-card>

        <a-card class="cps-card" :body-style="{ padding: '16px' }" style="margin-top: 16px">
          <template #title>
            <div class="cps-card-title">
              <div>
                <p class="cps-card-title__sub">问题描述</p>
                <h2 class="cps-card-title__main">现场情况</h2>
              </div>
            </div>
          </template>
          <a-textarea
            v-model:value="draft.description"
            :rows="5"
            :maxlength="500"
            show-count
            placeholder="请输入现场问题描述"
          />
          <div class="cps-desc-toolbar">
            <a-button
              size="small"
              :loading="transcribing"
              :disabled="transcribing"
              :danger="recording"
              @click="onVoiceClick"
            >
              <template #icon>
                <AudioOutlined v-if="!recording && !transcribing" />
              </template>
              <span v-if="transcribing">识别中</span>
              <span v-else-if="recording">停止录音</span>
              <span v-else>语音输入</span>
            </a-button>
            <span v-if="recording" class="cps-voice-hint">正在录音，再次点击「停止录音」结束并识别…</span>
          </div>
        </a-card>
      </a-col>

      <!-- 右列：位置 + 分类 + 派发 -->
      <a-col :xs="{ span: 24 }" :lg="{ span: 12 }">
        <a-card class="cps-card" :body-style="{ padding: '16px' }">
          <template #title>
            <div class="cps-card-title">
              <div>
                <p class="cps-card-title__sub">位置选择</p>
                <h2 class="cps-card-title__main">工厂 / 区域 / 拉线 / 工序</h2>
              </div>
            </div>
          </template>

          <div class="cps-selector">
            <div class="cps-selector-row">
              <span class="cps-selector-label">工厂</span>
              <a-radio-group :value="draft.location.factory" button-style="solid" class="cps-selector-group" @change="(e: any) => onSelectFactory(e.target.value)">
                <a-radio-button v-for="item in draft.factories" :key="item.value" :value="String(item.value)">{{ item.label }}</a-radio-button>
              </a-radio-group>
              <span v-if="!draft.factories.length" class="cps-muted">暂无工厂数据</span>
            </div>

            <div class="cps-selector-row">
              <span class="cps-selector-label">区域</span>
              <a-radio-group :value="draft.location.area" button-style="solid" class="cps-selector-group" @change="(e: any) => onSelectArea(e.target.value)">
                <a-radio-button v-for="item in draft.areas" :key="item.value" :value="String(item.value)">{{ item.label }}</a-radio-button>
              </a-radio-group>
              <span v-if="!draft.areas.length" class="cps-muted">先选择工厂</span>
            </div>

            <div v-if="draft.location.area" class="cps-selector-row">
              <span class="cps-selector-label">拉线</span>
              <a-radio-group :value="draft.location.line" button-style="solid" class="cps-selector-group" @change="(e: any) => onSelectLine(e.target.value)">
                <a-radio-button v-for="item in draft.lines" :key="item.value" :value="String(item.value)">{{ item.label }}</a-radio-button>
              </a-radio-group>
              <span v-if="!draft.lines.length" class="cps-muted">暂无拉线数据</span>
            </div>

            <div v-if="draft.location.area" class="cps-selector-row">
              <span class="cps-selector-label">工序</span>
              <a-radio-group :value="draft.location.process" button-style="solid" class="cps-selector-group" @change="(e: any) => onSelectProcess(e.target.value)">
                <a-radio-button v-for="item in draft.processes" :key="item.value" :value="String(item.value)">{{ item.label }}</a-radio-button>
              </a-radio-group>
              <span v-if="!draft.processes.length" class="cps-muted">暂无工序数据</span>
            </div>
          </div>
        </a-card>

        <a-alert v-if="inspecting" type="info" show-icon message="AI 正在识别首张图片，请稍候" style="margin-top: 16px" />

        <a-card v-if="aiSuggestionSummary" class="cps-card cps-create-ai" :body-style="{ padding: '16px' }" style="margin-top: 16px">
          <template #title>
            <div class="cps-card-title">
              <div>
                <p class="cps-card-title__sub">AI 建议</p>
                <h2 class="cps-card-title__main">原因措施</h2>
              </div>
              <a-tag color="blue">可修改</a-tag>
            </div>
          </template>
          <pre class="cps-create-ai__pre">{{ aiSuggestionSummary }}</pre>
        </a-card>

        <a-card class="cps-card" :body-style="{ padding: '16px' }" style="margin-top: 16px">
          <template #title>
            <div class="cps-card-title">
              <div>
                <p class="cps-card-title__sub">派发信息</p>
                <h2 class="cps-card-title__main">反馈人</h2>
              </div>
            </div>
          </template>
          <a-form layout="vertical">
            <a-form-item label="反馈人">
              <a-input :value="feedbackPersonLabel" placeholder="选择反馈人" read-only @click="feedbackPickerOpen = true">
                <template #suffix><UserOutlined /></template>
              </a-input>
            </a-form-item>
          </a-form>
        </a-card>
      </a-col>
    </a-row>

    <div class="cps-submit-bar">
      <a-button type="primary" size="large" block :loading="submitting" :disabled="!canSubmit" class="cps-submit-btn" @click="submit">
        提交并派发
      </a-button>
    </div>

    <!-- 反馈人选择弹窗（内联） -->
    <a-modal
      :open="feedbackPickerOpen"
      title="选择反馈人"
      :width="isLandscape ? 520 : '100%'"
      :centered="isLandscape"
      :footer="null"
      :destroy-on-close="false"
      @update:open="(v: boolean) => (feedbackPickerOpen = v)"
    >
      <a-input-search
        v-model:value="feedbackKeyword"
        placeholder="输入姓名或工号搜索"
        allow-clear
        enter-button="搜索"
        :loading="feedbackSearching"
        class="cps-person-picker__search"
        @search="onSearchFeedback"
      />
      <a-spin :spinning="feedbackSearching">
        <div class="cps-person-picker__body">
          <a-empty v-if="!feedbackPeople.length" description="未找到人员，请调整关键词后搜索" />
          <a-radio-group :value="draft.feedbackEmpNo" class="cps-person-picker__group" @change="(e: any) => onSelectFeedback(e.target.value)">
            <a-radio v-for="person in feedbackPeople" :key="person.empNo" :value="person.empNo" class="cps-person-picker__option">
              <strong>{{ person.empName || person.empNo }}</strong>
              <span>{{ person.empNo }}</span>
            </a-radio>
          </a-radio-group>
        </div>
      </a-spin>
    </a-modal>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { AudioOutlined, UserOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import type { UploadProps } from 'ant-design-vue'

import { inspectCpsImage, startVoiceRecording, stopVoiceRecording } from '@/api/cps/ai'
import { createCpsIssue } from '@/api/cps/issue'
import {
  getAreas,
  getFactories,
  getFeedbackHandler,
  getLines,
  getProcesses,
  searchCpsEmployees,
  type CpsEmployeeOption,
} from '@/api/cps/master'
import { getCpsAttachmentBase64, uploadCpsAttachment } from '@/api/cps/attachment'
import type { CpsUploadedImage } from '@/types/cps'
import { useFormDraftStore } from '@/store/formDraft'
import { toImageDataUrl } from '@/utils/image'

const draft = useFormDraftStore()

const submitting = ref(false)
const inspecting = ref(false)
const uploading = ref(false)

// 反馈人选择器状态（内联）
const feedbackPickerOpen = ref(false)
const feedbackKeyword = ref('')
const feedbackSearching = ref(false)
const feedbackSearched = ref(false)
const feedbackSearchResults = ref<CpsEmployeeOption[]>([])
const feedbackCandidates = ref<CpsEmployeeOption[]>([])

// 横竖屏断点：lg 以上视为横屏（平板宽屏）
const isLandscape = ref(false)
const updateOrientation = () => {
  isLandscape.value = window.innerWidth >= 992
}
if (typeof window !== 'undefined') {
  updateOrientation()
  window.addEventListener('resize', updateOrientation)
}

const aiSuggestionSummary = computed(() => {
  if (!draft.aiSuggestion) return ''
  return [
    draft.aiSuggestion.reasonSuggestion ? `原因建议：${draft.aiSuggestion.reasonSuggestion}` : '',
    draft.aiSuggestion.measureSuggestion ? `措施建议：${draft.aiSuggestion.measureSuggestion}` : '',
  ]
    .filter(Boolean)
    .join('\n')
})

const feedbackPersonLabel = computed(() => {
  if (!draft.feedbackEmpNo) return ''
  const name = draft.feedbackPerson?.empName
  return name && name !== draft.feedbackEmpNo ? `${name} (${draft.feedbackEmpNo})` : draft.feedbackEmpNo
})

// 未搜索时展示候选；搜索后展示搜索结果（合并去重）
const feedbackPeople = computed<CpsEmployeeOption[]>(() => {
  if (feedbackSearched.value) {
    return [
      ...feedbackSearchResults.value,
      ...feedbackCandidates.value.filter((c) => !feedbackSearchResults.value.some((s) => s.empNo === c.empNo)),
    ]
  }
  return feedbackCandidates.value
})

const uploadFileList = computed(() =>
  draft.images.map((image) => ({
    uid: String(image.id),
    name: image.name,
    status: 'done' as const,
    url: draft.imagePreviewSources[image.id],
  })),
)

interface ProgressItem {
  label: string
  done: boolean
}
const progressItems = computed<ProgressItem[]>(() => [
  { label: '照片', done: draft.images.length >= 1 && draft.images.length <= 5 },
  { label: '位置', done: Boolean(draft.location.factory && draft.location.area && draft.location.line && draft.location.process) },
  { label: 'AI分类', done: Boolean(draft.aiSuggestion?.aiCategoryL1Id && draft.aiSuggestion?.aiCategoryL2Id) },
  { label: '描述', done: Boolean(draft.description.trim()) },
])

const canSubmit = computed(
  () =>
    draft.images.length >= 1 &&
    draft.images.length <= 5 &&
    Boolean(draft.location.factory) &&
    Boolean(draft.location.area) &&
    Boolean(draft.location.line) &&
    Boolean(draft.location.process) &&
    Boolean(draft.aiSuggestion?.aiCategoryL1Id) &&
    Boolean(draft.aiSuggestion?.aiCategoryL2Id) &&
    Boolean(draft.description.trim()) &&
    Boolean(draft.feedbackEmpNo.trim()) &&
    !submitting.value,
)

// 位置完整后预填反馈人候选（第一次：不自动选中，仅预填列表，沿用源码行为）
watch(
  () => ({ ...draft.location }),
  async (value) => {
    if (!value.factory || !value.area || !value.line || !value.process) {
      draft.feedbackEmpNo = ''
      draft.feedbackPerson = null
      feedbackCandidates.value = []
      return
    }
    const handlers = await getFeedbackHandler({
      factory: value.factory,
      area: value.area,
      line: value.line,
      process: value.process,
    })
    draft.feedbackEmpNo = ''
    draft.feedbackPerson = null
    feedbackCandidates.value = handlers
  },
)

const onSelectFactory = async (factory: string) => {
  draft.location = { factory, area: '', line: '', process: '' }
  draft.areas = await getAreas(factory)
  draft.lines = []
  draft.processes = []
}
const onSelectArea = async (area: string) => {
  draft.location = { ...draft.location, area, line: '', process: '' }
  draft.lines = await getLines(draft.location.factory, area)
  draft.processes = await getProcesses(draft.location.factory, area)
}
const onSelectLine = async (line: string) => {
  draft.location = { ...draft.location, line, process: '' }
  draft.processes = await getProcesses(draft.location.factory, draft.location.area, line || undefined)
}
const onSelectProcess = (process: string) => {
  draft.location = { ...draft.location, process }
}

const onSearchFeedback = async () => {
  const kw = feedbackKeyword.value.trim()
  if (!kw || feedbackSearching.value) return
  feedbackSearching.value = true
  try {
    feedbackSearchResults.value = await searchCpsEmployees(kw)
    feedbackSearched.value = true
  } finally {
    feedbackSearching.value = false
  }
}

const onSelectFeedback = (empNo: string) => {
  const person = feedbackPeople.value.find((p) => p.empNo === empNo)
  if (person) {
    draft.feedbackEmpNo = empNo
    draft.feedbackPerson = person
    feedbackPickerOpen.value = false
  }
}

watch(feedbackPickerOpen, (v) => {
  if (v) {
    feedbackKeyword.value = ''
    feedbackSearched.value = false
    feedbackSearchResults.value = []
  }
})

// 图片上传
const beforeUpload: UploadProps['beforeUpload'] = (_file, fileList) => {
  const remaining = Math.max(5 - draft.images.length, 0)
  if (fileList.length > remaining) return false
  return true
}

const customRequest: UploadProps['customRequest'] = async (options) => {
  const { file, onSuccess, onError } = options
  const shouldInspectFirst = draft.images.length === 0
  uploading.value = true
  try {
    const uploaded = await uploadCpsAttachment(file as File)
    draft.images = [...draft.images, uploaded]
    void imageToBase64(uploaded)
    onSuccess?.({}, file as any)
    if (shouldInspectFirst) {
      await onFirstImageReady(uploaded.id)
    }
  } catch (error) {
    onError?.(error as any)
  } finally {
    uploading.value = false
  }
}

const onRemoveImage = (index: number) => {
  draft.images = draft.images.filter((_, i) => i !== index)
}

const onPreviewImage = (index: number) => {
  const target = draft.images[index]
  if (!target) return
  const urls = draft.images.map((i) => draft.imagePreviewSources[i.id]).filter(Boolean)
  if (typeof uni !== 'undefined' && typeof uni.previewImage === 'function') {
    uni.previewImage({ urls, current: draft.imagePreviewSources[target.id] })
  }
}

const imageToBase64 = async (image: CpsUploadedImage) => {
  try {
    const base64 = await getCpsAttachmentBase64(image.url)
    draft.imagePreviewSources = { ...draft.imagePreviewSources, [image.id]: toImageDataUrl(base64) }
  } catch {
    message.error('图片预览加载失败')
  }
}

const onFirstImageReady = async (fileId: number) => {
  inspecting.value = true
  try {
    // 创建阶段仅保存 AI 识别结果，分类由反馈回复节点确认/修正
    draft.aiSuggestion = await inspectCpsImage(fileId)
  } finally {
    inspecting.value = false
  }
}

// 语音输入：点击开始录音、再点击结束并转写
const recording = ref(false)
const transcribing = ref(false)

const onVoiceClick = async () => {
  // 正在录音 → 结束并转写
  if (recording.value) {
    recording.value = false
    transcribing.value = true
    try {
      const text = await stopVoiceRecording()
      if (text.trim()) {
        draft.description = [draft.description.trim(), text.trim()].filter(Boolean).join('\n')
      } else {
        message.info('未识别到语音内容')
      }
    } catch (error) {
      message.error(error instanceof Error ? error.message : '语音识别失败')
    } finally {
      transcribing.value = false
    }
    return
  }
  // 未录音 → 开始
  if (transcribing.value) return
  const started = await startVoiceRecording()
  if (!started) {
    message.error('当前环境不支持语音录入')
    return
  }
  recording.value = true
}

const submit = async () => {
  if (!canSubmit.value) return
  submitting.value = true
  try {
    const result = await createCpsIssue({
      factory: draft.location.factory,
      area: draft.location.area,
      line: draft.location.line,
      process: draft.location.process,
      aiCategoryL1Id: draft.aiSuggestion?.aiCategoryL1Id ?? null,
      aiCategoryL2Id: draft.aiSuggestion?.aiCategoryL2Id ?? null,
      // 创建时直接采用 AI 分类，后续在反馈回复节点由反馈人员确认/修正
      categoryL1Id: draft.aiSuggestion?.aiCategoryL1Id!,
      categoryL2Id: draft.aiSuggestion?.aiCategoryL2Id!,
      description: draft.description.trim(),
      feedbackEmpNo: draft.feedbackEmpNo.trim(),
      feedbackEmpName: draft.feedbackPerson?.empName,
      issueAttachmentIds: draft.images.map((image) => image.id),
      aiSuggestion: draft.aiSuggestion ?? undefined,
    })
    draft.reset()
    uni.redirectTo({ url: `/views/cps/IssueDetailView?id=${result.issueId}` })
  } catch (error) {
    message.error(error instanceof Error ? error.message : '提交失败')
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  if (!draft.factories.length) {
    draft.factories = await getFactories()
  }
})
</script>

<style scoped>
.cps-page {
  width: 100%;
  max-width: 1280px;
  margin: 0 auto;
  padding: 16px 20px 120px;
}

.cps-hero {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
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

.cps-create-progress {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 4px;
}

.cps-progress-tag {
  border-radius: 999px !important;
  font-weight: 700 !important;
}

.cps-hero__badge {
  align-self: flex-start;
  border-radius: 999px !important;
  font-weight: 800 !important;
}

.cps-card {
  border-radius: 14px;
  border: 1px solid rgba(20, 184, 166, 0.16);
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.06);
}

.cps-card-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.cps-card-title__sub {
  margin: 0;
  color: #0f766e;
  font-size: 12px;
  font-weight: 800;
}

.cps-card-title__main {
  margin: 2px 0 0;
  color: #0f172a;
  font-size: 18px;
  font-weight: 800;
}

.cps-create-card__hint {
  margin: 0 0 12px;
  color: #64748b;
  font-size: 13px;
}

.cps-desc-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-top: 10px;
}

.cps-voice-hint {
  margin: 0;
  color: #0f766e;
  font-size: 12px;
  font-weight: 600;
}

/* 图片上传（内联） */
.cps-uploader__trigger {
  display: grid;
  align-items: center;
  justify-items: center;
  gap: 4px;
  width: 100%;
  height: 100%;
  color: #0f766e;
}

.cps-create-page :deep(.ant-upload.ant-upload-select) {
  width: 100%;
  height: auto;
  margin-bottom: 12px;
}

.cps-create-page :deep(.ant-upload.ant-upload-select .ant-upload) {
  width: 100%;
  padding: 18px;
  border-radius: 12px;
  background: #ecfeff;
  border: 1px dashed #67e8f9;
}

.cps-uploader__plus {
  font-size: 28px;
  line-height: 1;
}

.cps-uploader__text {
  font-size: 13px;
  font-weight: 700;
}

.cps-uploader__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  gap: 12px;
}

.cps-uploader__item {
  position: relative;
  width: 100%;
  aspect-ratio: 1;
  border-radius: 12px;
  overflow: hidden;
  background: #e2e8f0;
  cursor: pointer;
}

.cps-uploader__item img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cps-uploader__delete {
  position: absolute;
  top: 6px;
  right: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.68);
  color: #fff;
  font-size: 16px;
  font-weight: 900;
  line-height: 1;
}

.cps-uploader__index {
  position: absolute;
  bottom: 6px;
  right: 6px;
  min-width: 22px;
  height: 22px;
  border-radius: 999px;
  padding: 0 6px;
  background: rgba(15, 23, 42, 0.72);
  color: #fff;
  font-size: 12px;
  font-weight: 900;
  line-height: 22px;
  text-align: center;
}

/* 位置/分类选择（内联） */
.cps-selector {
  display: grid;
  gap: 12px;
}

.cps-selector-row {
  display: grid;
  grid-template-columns: 64px minmax(0, 1fr);
  align-items: start;
  gap: 12px;
}

.cps-selector-label {
  padding-top: 6px;
  color: #0f172a;
  font-size: 14px;
  font-weight: 700;
}

.cps-selector-group {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.cps-selector-group :deep(.ant-radio-button-wrapper) {
  border-radius: 999px;
  border: 1px solid #cbd5e1;
  height: 36px;
  line-height: 34px;
  padding: 0 16px;
}

.cps-selector-group :deep(.ant-radio-button-wrapper:not(:first-child))::before {
  display: none;
}

.cps-muted {
  color: #64748b;
  font-size: 14px;
}

.cps-create-ai {
  border-left: 4px solid #2563eb;
}

.cps-create-ai__pre {
  margin: 0;
  padding: 12px;
  border-radius: 10px;
  background: #f0fdfa;
  color: #0f172a;
  font-family: inherit;
  font-size: 14px;
  line-height: 22px;
  white-space: pre-wrap;
  word-break: break-word;
}

/* 人员选择弹窗（内联） */
.cps-person-picker__search {
  margin-bottom: 16px;
}

.cps-person-picker__body {
  max-height: 52vh;
  overflow-y: auto;
  padding-right: 4px;
}

.cps-person-picker__group {
  display: grid;
  gap: 8px;
  width: 100%;
}

.cps-person-picker__option {
  display: grid;
  grid-template-columns: 24px 1fr;
  align-items: center;
  width: 100%;
  min-height: 56px;
  padding: 8px 12px;
  border-radius: 10px;
  background: #f8fafc;
}

.cps-person-picker__option :deep(.ant-radio + span) {
  display: grid;
  gap: 2px;
  padding-right: 8px;
}

.cps-person-picker__option :deep(strong) {
  color: #0f172a;
  font-size: 15px;
  font-weight: 700;
  line-height: 22px;
}

.cps-person-picker__option :deep(span:not(.ant-radio)) {
  color: #64748b;
  font-size: 13px;
  line-height: 18px;
}

.cps-submit-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 99;
  max-width: 1280px;
  margin: 0 auto;
  padding: 12px 20px max(12px, env(safe-area-inset-bottom));
  background: linear-gradient(180deg, rgba(248, 250, 252, 0), #f8fafc 40%);
}

.cps-submit-btn {
  height: 52px !important;
  border-radius: 999px !important;
  background: linear-gradient(135deg, #14b8a6 0%, #2563eb 100%) !important;
  border: none !important;
  font-size: 18px !important;
  font-weight: 800 !important;
}

@media (min-width: 1024px) {
  .cps-page {
    padding: 20px 28px 120px;
  }
  .cps-hero h1 {
    font-size: 24px;
  }
}
</style>
