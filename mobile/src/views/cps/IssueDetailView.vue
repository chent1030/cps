<template>
  <main v-if="detail" class="cps-page cps-detail-page">
    <header class="cps-detail-hero">
      <div class="cps-detail-hero__top">
        <div class="cps-detail-hero__identity">
          <p class="cps-detail-hero__eyebrow">巡检问题档案</p>
          <h1>{{ detail.issueNo }}</h1>
        </div>
        <div class="cps-detail-hero__badges">
          <span class="cps-status-pill" :class="detailStatus.tone">{{ detailStatus.label }}</span>
          <span v-if="detail.overdue" class="cps-status-pill cps-status-pill--red">超时</span>
        </div>
      </div>
      <p class="cps-detail-hero__desc">{{ detail.description }}</p>
      <div class="cps-detail-hero__meta" aria-label="问题位置与分类">
        <span>{{ issueLocation }}</span>
        <span>{{ issueCategory }}</span>
      </div>
    </header>

    <section class="cps-detail-card">
      <div class="cps-card-title">
        <div>
          <p>基础信息</p>
          <h2>处理责任</h2>
        </div>
      </div>
      <div class="cps-info-grid">
        <div><span>提交人</span><strong>{{ detail.creatorEmpNo || '-' }}</strong></div>
        <div><span>当前处理人</span><strong>{{ detail.currentHandlerEmpName || detail.currentHandlerEmpNo || '-' }}</strong></div>
        <div><span>反馈人</span><strong>{{ detail.feedbackEmpNo || '-' }}</strong></div>
        <div><span>责任人</span><strong>{{ detail.responsibleEmpNo || '-' }}</strong></div>
        <div><span>上传人</span><strong>{{ detail.proofEmpNo || '-' }}</strong></div>
        <div><span>审核人</span><strong>{{ detail.reviewerEmpNo || '-' }}</strong></div>
      </div>
      <div class="cps-detail-time">
        <span>提交时间 {{ detail.submitTime || '-' }}</span>
        <span v-if="detail.closeTime">关闭时间 {{ detail.closeTime }}</span>
      </div>
    </section>

    <section class="cps-detail-card">
      <div class="cps-card-title">
        <div>
          <p>照片档案</p>
          <h2>问题现场</h2>
        </div>
        <span class="cps-count-badge">{{ detail.issueAttachments.length }} 张</span>
      </div>
      <div v-if="hasIssueAttachments" class="cps-photo-grid cps-photo-grid--issue">
        <button
          v-for="(image, index) in detail.issueAttachments"
          :key="image.id"
          type="button"
          class="cps-photo-tile"
          :class="{ 'cps-photo-tile--lead': detail.issueAttachments.length > 1 && index === 0 }"
          :aria-label="`预览问题现场照片 ${index + 1}`"
          @click="previewAttachments(detail.issueAttachments, index)"
        >
          <img :src="attachmentImageSources[image.id] || ''" :alt="image.fileName" />
          <span>{{ index + 1 }}/{{ detail.issueAttachments.length }}</span>
        </button>
      </div>
      <div v-else class="cps-empty-proof">当前问题还没有现场照片</div>
    </section>

    <section class="cps-detail-card">
      <div class="cps-card-title">
        <div>
          <p>整改凭证</p>
          <h2>复核照片</h2>
        </div>
        <span class="cps-count-badge cps-count-badge--green">{{ detail.proofAttachments.length }} 张</span>
      </div>
      <div v-if="detail.proofAttachments.length" class="cps-photo-grid">
        <button
          v-for="(image, index) in detail.proofAttachments"
          :key="image.id"
          type="button"
          class="cps-photo-tile"
          :aria-label="`预览整改凭证照片 ${index + 1}`"
          @click="previewAttachments(detail.proofAttachments, index)"
        >
          <img :src="attachmentImageSources[image.id] || ''" :alt="image.fileName" />
          <span>{{ index + 1 }}/{{ detail.proofAttachments.length }}</span>
        </button>
      </div>
      <div v-else class="cps-empty-proof">当前节点还没有上传整改照片</div>
    </section>

    <section class="cps-detail-card">
      <div class="cps-card-title">
        <div>
          <p>AI 与分类</p>
          <h2>原因措施</h2>
        </div>
      </div>
      <div class="cps-ai-panel">
        <div>
          <span>AI 分类</span>
          <strong>{{ aiCategory }}</strong>
        </div>
        <div>
          <span>人工分类</span>
          <strong>{{ issueCategory }}</strong>
        </div>
        <p><b>AI 原因</b>{{ detail.aiSuggestion?.reasonSuggestion || '-' }}</p>
        <p><b>AI 措施</b>{{ detail.aiSuggestion?.measureSuggestion || '-' }}</p>
        <p><b>人工原因</b>{{ detail.reasonAnalysis || '-' }}</p>
        <p><b>人工措施</b>{{ detail.correctiveMeasure || '-' }}</p>
      </div>
    </section>

    <section class="cps-detail-card">
      <div class="cps-card-title">
        <div>
          <p>节点处理</p>
          <h2>当前操作</h2>
        </div>
        <span class="cps-count-badge">按节点</span>
      </div>

      <van-cell-group inset>
        <template v-if="showFeedbackFields">
          <van-field v-model="actionForm.reasonAnalysis" rows="3" autosize type="textarea" label="原因分析" placeholder="填写原因分析" />
          <van-field v-model="actionForm.correctiveMeasure" rows="3" autosize type="textarea" label="整改措施" placeholder="填写整改措施" />
          <van-field data-testid="responsible-employee-field" :model-value="personLabel(actionForm.responsibleEmpNo)" label="责任员工" placeholder="选择责任员工" readonly is-link @click="openPersonPicker('responsibleEmpNo', '选择责任员工')" />
        </template>

        <template v-if="showRectifyFields">
          <van-field v-model="actionForm.rectifyRemark" rows="3" autosize type="textarea" label="整改说明" placeholder="填写整改说明" />
          <van-field data-testid="proof-employee-field" :model-value="personLabel(actionForm.proofEmpNo)" label="上传人" placeholder="选择上传人" readonly is-link @click="openPersonPicker('proofEmpNo', '选择上传人')" />
        </template>

        <template v-if="showProofFields">
          <van-field data-testid="reviewer-employee-field" :model-value="personLabel(actionForm.reviewerEmpNo)" label="审核人" placeholder="选择审核人" readonly is-link @click="openPersonPicker('reviewerEmpNo', '选择审核人')" />
          <div class="cps-proof-upload">
            <p>整改照片</p>
            <div class="cps-proof-uploader">
              <button
                v-for="(image, index) in proofImages"
                :key="image.id"
                type="button"
                class="cps-proof-uploader__preview"
                @click="previewProofImage(index)"
              >
                <img :src="attachmentImageSources[image.id] || ''" :alt="image.name" />
                <span v-if="!proofUploading" class="cps-proof-uploader__delete" @click.stop="removeProofImage(index)">×</span>
              </button>
              <button
                v-if="proofImages.length < 5"
                type="button"
                class="cps-proof-uploader__upload"
                :disabled="proofUploading"
                @click="chooseAndUploadProofImages"
              >
                <span class="cps-proof-uploader__plus">+</span>
                <span>{{ proofUploading ? '上传中' : '上传整改图片' }}</span>
              </button>
            </div>
          </div>
        </template>

        <template v-if="showReviewFields">
          <van-field v-model="actionForm.reviewOpinion" rows="3" autosize type="textarea" label="审核意见" placeholder="填写审核意见" />
        </template>

        <van-field v-if="showTransferField" :model-value="personLabel(actionForm.targetEmpNo)" label="转办目标" placeholder="选择转办目标" readonly is-link @click="openPersonPicker('targetEmpNo', '选择转办目标')" />
        <van-field v-model="actionForm.comment" rows="3" autosize type="textarea" label="流转备注" placeholder="填写备注" />
      </van-cell-group>

      <div class="cps-inline-action-panel" :aria-label="`当前状态 ${detailStatus.label}`">
        <button
          v-for="action in workflowActions"
          :key="action"
          type="button"
          data-testid="workflow-action"
          class="cps-inline-action-panel__button"
          :class="{
            'cps-inline-action-panel__button--danger': action === 'REVIEW_REJECT',
            'cps-inline-action-panel__button--success': action === 'REVIEW_CLOSE',
          }"
          @click="runAction(action)"
        >
          {{ actionLabels[action] }}
        </button>
      </div>
    </section>

    <section class="cps-detail-card">
      <div class="cps-card-title">
        <div>
          <p>流转记录</p>
          <h2>处理轨迹</h2>
        </div>
      </div>
      <div v-if="!detail.flowLogs.length" class="cps-flow-empty">暂无流转记录</div>
      <ol v-else class="cps-flow-timeline">
        <li v-for="(log, index) in detail.flowLogs" :key="`${log.action}-${log.createdAt}`" class="cps-flow-timeline__item">
          <span class="cps-flow-timeline__dot" :class="{ 'is-last': index === detail.flowLogs.length - 1 }" />
          <div class="cps-flow-timeline__content">
            <div class="cps-flow-timeline__head">
              <strong>{{ actionLabel(log.action) }}</strong>
              <time>{{ log.createdAt }}</time>
            </div>
            <p>{{ flowStatusLabel(log.fromStatus) }} 至 {{ flowStatusLabel(log.toStatus) }}</p>
            <p>操作人：{{ log.operatorEmpNo }}</p>
            <p v-if="log.comment" class="cps-flow-timeline__comment">{{ log.comment }}</p>
          </div>
        </li>
      </ol>
    </section>
  </main>
  <main v-else class="cps-page">
    <van-loading class="cps-page-loading" color="#14B8A6">加载中...</van-loading>
  </main>

  <div v-if="personPicker.visible" class="cps-person-picker-mask" @click.self="closePersonPicker">
    <section class="cps-person-picker" role="dialog" :aria-label="personPicker.title">
      <div class="cps-person-picker__head">
        <strong>{{ personPicker.title }}</strong>
        <button type="button" class="cps-person-picker__close" aria-label="关闭" @click="closePersonPicker">×</button>
      </div>
      <div class="cps-person-picker__search">
        <input v-model.trim="personPicker.keyword" type="search" placeholder="输入姓名或工号搜索" @keyup.enter="searchPeople" />
        <button type="button" :disabled="personPicker.loading" @click="searchPeople">搜索</button>
      </div>
      <div v-if="personPicker.loading" class="cps-person-picker__state">搜索中...</div>
      <div v-else class="cps-person-picker__list">
        <button
          v-for="person in pickerPeople"
          :key="person.empNo"
          type="button"
          class="cps-person-picker__option"
          @click="selectPerson(person)"
        >
          <strong>{{ person.empName || person.empNo }}</strong>
          <span>{{ person.empNo }}</span>
        </button>
        <p v-if="!pickerPeople.length" class="cps-person-picker__state">未找到人员，请调整关键词后搜索</p>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'

import { getCpsAttachmentBase64, uploadCpsAttachment, type CpsAttachmentUploadSource } from '@/api/cps/attachment'
import { executeCpsIssueAction, getCpsIssueDetail } from '@/api/cps/issue'
import { searchCpsEmployees, type CpsEmployeeOption } from '@/api/cps/master'
import type {
  CpsAttachment,
  CpsIssueAction,
  CpsIssueActionRequest,
  CpsIssueDetail,
  CpsIssueStatus,
  CpsUploadedImage,
} from '@/types/cps'

interface DetailStatusMeta {
  label: string
  tone: string
}

interface UniTempFileLike {
  path?: string
  file?: File
}

type TempFileCandidate = File | UniTempFileLike
type PersonField = 'responsibleEmpNo' | 'proofEmpNo' | 'reviewerEmpNo' | 'targetEmpNo'

type CpsIssueDetailCore = Omit<CpsIssueDetail, 'issueAttachments' | 'proofAttachments' | 'aiSuggestion' | 'availableActions' | 'flowLogs'>

interface CpsIssueDetailApiResponse {
  issue?: CpsIssueDetailCore
  issueAttachments?: CpsAttachment[]
  proofAttachments?: CpsAttachment[]
  aiSuggestion?: CpsIssueDetail['aiSuggestion']
  availableActions?: CpsIssueAction[]
  flowLogs?: CpsIssueDetail['flowLogs']
}

const detail = ref<CpsIssueDetail | null>(null)
const issueId = ref<number>(0)
const proofImages = ref<CpsUploadedImage[]>([])
const proofUploading = ref(false)
const attachmentImageSources = ref<Record<number, string>>({})
const selectedPeople = ref<Record<string, CpsEmployeeOption>>({})
const personPicker = ref({
  visible: false,
  title: '',
  field: 'responsibleEmpNo' as PersonField,
  keyword: '',
  loading: false,
  searched: false,
  results: [] as CpsEmployeeOption[],
})
const actionForm = ref({
  reasonAnalysis: '',
  correctiveMeasure: '',
  responsibleEmpNo: '',
  proofEmpNo: '',
  reviewerEmpNo: '',
  rectifyRemark: '',
  reviewOpinion: '',
  targetEmpNo: '',
  comment: '',
})

const showFeedbackFields = computed<boolean>(() => detail.value?.status === 'PENDING_FEEDBACK')
const showRectifyFields = computed<boolean>(() => detail.value?.status === 'PENDING_RECTIFY')
const showProofFields = computed<boolean>(() => detail.value?.status === 'PENDING_UPLOAD_PROOF')
const showReviewFields = computed<boolean>(() => detail.value?.status === 'PENDING_REVIEW')
const workflowActions = computed<CpsIssueAction[]>(() => {
  if (!detail.value) return []
  return detail.value.availableActions.length ? detail.value.availableActions : statusDefaultActions[detail.value.status]
})
const showTransferField = computed<boolean>(() => workflowActions.value.includes('TRANSFER'))

const statusMeta: Record<CpsIssueStatus, DetailStatusMeta> = {
  PENDING_FEEDBACK: { label: '待反馈', tone: 'cps-status-pill--blue' },
  PENDING_RECTIFY: { label: '待整改', tone: 'cps-status-pill--orange' },
  PENDING_UPLOAD_PROOF: { label: '待传图', tone: 'cps-status-pill--orange' },
  PENDING_REVIEW: { label: '待审核', tone: 'cps-status-pill--teal' },
  CLOSED: { label: '已关闭', tone: 'cps-status-pill--green' },
}

const actionLabels: Record<CpsIssueAction, string> = {
  REPLY_ASSIGN: '回复并指派',
  RECTIFY: '完成整改',
  UPLOAD_PROOF: '上传凭证',
  REVIEW_CLOSE: '审核通过',
  REVIEW_REJECT: '审核退回',
  TRANSFER: '转办',
}

const statusDefaultActions: Record<CpsIssueStatus, CpsIssueAction[]> = {
  PENDING_FEEDBACK: ['REPLY_ASSIGN', 'TRANSFER'],
  PENDING_RECTIFY: ['RECTIFY', 'TRANSFER'],
  PENDING_UPLOAD_PROOF: ['UPLOAD_PROOF', 'TRANSFER'],
  PENDING_REVIEW: ['REVIEW_CLOSE', 'REVIEW_REJECT', 'TRANSFER'],
  CLOSED: [],
}

const flowStatusLabels: Record<CpsIssueStatus, string> = {
  PENDING_FEEDBACK: '待反馈',
  PENDING_RECTIFY: '待整改',
  PENDING_UPLOAD_PROOF: '待传图',
  PENDING_REVIEW: '待审核',
  CLOSED: '已关闭',
}

const detailStatus = computed<DetailStatusMeta>(() => (detail.value ? statusMeta[detail.value.status] : statusMeta.PENDING_FEEDBACK))
const issueLocation = computed<string>(() =>
  detail.value
    ? [
        detail.value.factoryName ?? detail.value.factory,
        detail.value.areaName ?? detail.value.area,
        detail.value.lineName ?? detail.value.line,
        detail.value.processName ?? detail.value.process,
      ]
        .filter(Boolean)
        .join(' / ') || '-'
    : '-',
)
const issueCategory = computed<string>(() =>
  detail.value ? [detail.value.categoryL1Name, detail.value.categoryL2Name].filter(Boolean).join(' / ') || '-' : '-',
)
const aiCategory = computed<string>(() =>
  detail.value?.aiSuggestion
    ? [detail.value.aiSuggestion.aiCategoryL1Name, detail.value.aiSuggestion.aiCategoryL2Name].filter(Boolean).join(' / ') || '-'
    : '-',
)
const hasIssueAttachments = computed<boolean>(() => Boolean(detail.value?.issueAttachments.length))
const knownPeople = computed<CpsEmployeeOption[]>(() => {
  if (!detail.value) return []
  const people = [
    { empNo: detail.value.creatorEmpNo, empName: detail.value.creatorEmpNo },
    { empNo: detail.value.feedbackEmpNo, empName: detail.value.feedbackEmpNo },
    { empNo: detail.value.responsibleEmpNo, empName: detail.value.responsibleEmpNo },
    { empNo: detail.value.proofEmpNo, empName: detail.value.proofEmpNo },
    { empNo: detail.value.reviewerEmpNo, empName: detail.value.reviewerEmpNo },
    { empNo: detail.value.currentHandlerEmpNo, empName: detail.value.currentHandlerEmpName || detail.value.currentHandlerEmpNo },
  ].filter((person): person is CpsEmployeeOption => Boolean(person.empNo))
  return [...new Map(people.map((person) => [person.empNo, person])).values()]
})
const pickerPeople = computed(() => (personPicker.value.searched ? personPicker.value.results : knownPeople.value))

const normalizeIssueDetail = (response: CpsIssueDetailApiResponse & Partial<CpsIssueDetail>): CpsIssueDetail => {
  if (!response.issue) {
    return {
      ...response,
      issueAttachments: response.issueAttachments ?? [],
      proofAttachments: response.proofAttachments ?? [],
      aiSuggestion: response.aiSuggestion ?? null,
      availableActions: response.availableActions ?? [],
      flowLogs: response.flowLogs ?? [],
    } as CpsIssueDetail
  }

  return {
    ...response.issue,
    issueAttachments: response.issueAttachments ?? [],
    proofAttachments: response.proofAttachments ?? [],
    aiSuggestion: response.aiSuggestion ?? null,
    availableActions: response.availableActions ?? [],
    flowLogs: response.flowLogs ?? [],
  }
}

const load = async () => {
  if (!issueId.value) return
  const response = (await getCpsIssueDetail(issueId.value)) as CpsIssueDetailApiResponse & Partial<CpsIssueDetail>
  detail.value = normalizeIssueDetail(response)
  proofImages.value = (detail.value.proofAttachments ?? []).map((item) => ({
    id: item.id,
    url: item.fileUrl,
    name: item.fileName,
  }))
  attachmentImageSources.value = {}
  void loadAttachmentImageSources([...detail.value.issueAttachments, ...detail.value.proofAttachments])
}

const buildActionPayload = (action: CpsIssueAction) => {
  return {
    action,
    reasonAnalysis: actionForm.value.reasonAnalysis || undefined,
    correctiveMeasure: actionForm.value.correctiveMeasure || undefined,
    responsibleEmpNo: actionForm.value.responsibleEmpNo || undefined,
    proofEmpNo: actionForm.value.proofEmpNo || undefined,
    reviewerEmpNo: actionForm.value.reviewerEmpNo || undefined,
    rectifyRemark: actionForm.value.rectifyRemark || undefined,
    reviewOpinion: actionForm.value.reviewOpinion || undefined,
    proofAttachmentIds: action === 'UPLOAD_PROOF' ? proofImages.value.map((image) => image.id) : undefined,
    targetEmpNo: actionForm.value.targetEmpNo || undefined,
    comment: actionForm.value.comment || undefined,
  }
}

const personLabel = (empNo: string) => {
  if (!empNo) return ''
  const person = selectedPeople.value[empNo] ?? knownPeople.value.find((item) => item.empNo === empNo)
  return person && person.empName !== person.empNo ? `${person.empName} (${empNo})` : empNo
}

const openPersonPicker = (field: PersonField, title: string) => {
  personPicker.value = {
    visible: true,
    title,
    field,
    keyword: '',
    loading: false,
    searched: false,
    results: [],
  }
}

const closePersonPicker = () => {
  personPicker.value.visible = false
}

const searchPeople = async () => {
  const keyword = personPicker.value.keyword.trim()
  if (!keyword || personPicker.value.loading) return
  personPicker.value.loading = true
  try {
    personPicker.value.results = await searchCpsEmployees(keyword)
    personPicker.value.searched = true
  } finally {
    personPicker.value.loading = false
  }
}

const selectPerson = (person: CpsEmployeeOption) => {
  actionForm.value[personPicker.value.field] = person.empNo
  selectedPeople.value = { ...selectedPeople.value, [person.empNo]: person }
  closePersonPicker()
}

const runAction = async (action: CpsIssueAction) => {
  await executeCpsIssueAction(issueId.value, buildActionPayload(action))
  await load()
}

const previewAttachments = (attachments: CpsAttachment[], startPosition: number) => {
  const target = attachments[startPosition]
  if (!target) return
  const current = attachmentImageSources.value[target.id]
  const urls = attachments.map((image) => attachmentImageSources.value[image.id]).filter(Boolean)
  if (!current || !urls.length || typeof uni === 'undefined' || typeof uni.previewImage !== 'function') return
  uni.previewImage({
    urls,
    current,
  })
}

const toImageDataUrl = (value: string) => {
  const data = value.trim()
  if (data.startsWith('data:')) return data
  if (data.includes(';base64,')) return `data:${data}`
  return `data:image/jpeg;base64,${data.replace(/\s/g, '')}`
}

const loadAttachmentImageSources = async (attachments: CpsAttachment[]) => {
  const resolved = await Promise.all(
    attachments.map(async (attachment) => {
      try {
        const base64 = await getCpsAttachmentBase64(attachment.fileUrl)
        return [attachment.id, toImageDataUrl(base64)] as const
      } catch {
        return null
      }
    }),
  )
  const sources = Object.fromEntries(resolved.filter((source): source is readonly [number, string] => source !== null))
  attachmentImageSources.value = { ...attachmentImageSources.value, ...sources }
}

const normalizeArray = <T,>(value: T | T[] | undefined) => {
  if (value === undefined) return []
  return Array.isArray(value) ? value : [value]
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

const chooseAndUploadProofImages = () => {
  const remaining = Math.max(5 - proofImages.value.length, 0)
  if (remaining <= 0 || proofUploading.value) return

  uni.chooseImage({
    count: remaining,
    sizeType: ['compressed', 'original'],
    sourceType: ['album', 'camera'],
    success: async (result) => {
      const sources = resolveUploadSources(result).slice(0, remaining)
      if (!sources.length) return

      proofUploading.value = true
      try {
        const next = [...proofImages.value]
        for (const source of sources) {
          const uploaded = await uploadCpsAttachment(source)
          next.push(uploaded)
          void loadAttachmentImageSources([
            {
              id: uploaded.id,
              fileUrl: uploaded.url,
              fileName: uploaded.name,
            },
          ])
        }
        proofImages.value = next
      } finally {
        proofUploading.value = false
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

const removeProofImage = (index: number) => {
  proofImages.value = proofImages.value.filter((_, itemIndex) => itemIndex !== index)
}

const previewProofImage = (index: number) => {
  const target = proofImages.value[index]
  if (!target) return
  const current = attachmentImageSources.value[target.id]
  const urls = proofImages.value.map((image) => attachmentImageSources.value[image.id]).filter(Boolean)
  if (!current || !urls.length || typeof uni === 'undefined' || typeof uni.previewImage !== 'function') return
  uni.previewImage({
    urls,
    current,
  })
}

const actionLabel = (action: string) => actionLabels[action as CpsIssueAction] ?? action

const flowStatusLabel = (status: CpsIssueStatus | null) => (status ? flowStatusLabels[status] : '开始')

onLoad((query?: Record<string, string | string[] | undefined>) => {
  const rawId = Array.isArray(query?.id) ? query?.id[0] : query?.id
  issueId.value = Number(rawId)
  void load()
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

.cps-detail-page {
  display: flex;
  flex-direction: column;
  gap: 28rpx;
}

.cps-detail-page > * {
  flex: 0 0 auto;
}

.cps-detail-hero,
.cps-detail-card {
  width: 100%;
  min-width: 0;
  max-width: 100%;
  overflow: hidden;
}

.cps-detail-hero {
  position: relative;
  display: grid;
  gap: 24rpx;
  border: 2rpx solid rgba(20, 184, 166, 0.22);
  border-radius: 22rpx;
  padding: 34rpx 32rpx 32rpx 40rpx;
  background:
    linear-gradient(135deg, rgba(20, 184, 166, 0.95) 0%, rgba(37, 99, 235, 0.92) 62%, rgba(34, 197, 94, 0.86) 100%),
    #14b8a6;
  box-shadow: 0 24rpx 70rpx rgba(15, 23, 42, 0.15);
  color: #ffffff;
}

.cps-detail-hero::before {
  position: absolute;
  top: 28rpx;
  bottom: 28rpx;
  left: 20rpx;
  width: 8rpx;
  border-radius: 999rpx;
  background: #f97316;
  content: "";
}

.cps-detail-hero__top,
.cps-card-title {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18rpx;
  min-width: 0;
}

.cps-detail-hero__identity {
  min-width: 0;
}

.cps-detail-hero__eyebrow,
.cps-card-title p {
  margin: 0;
  color: rgba(255, 255, 255, 0.82);
  font-size: 26rpx;
  font-weight: 900;
  line-height: 36rpx;
}

.cps-detail-hero h1 {
  margin: 8rpx 0 0;
  font-size: 42rpx;
  font-weight: 950;
  line-height: 54rpx;
  overflow-wrap: anywhere;
}

.cps-detail-hero__badges {
  display: flex;
  flex: 0 1 auto;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10rpx;
  min-width: 0;
}

.cps-detail-hero__desc {
  margin: 0;
  color: rgba(255, 255, 255, 0.96);
  font-size: 34rpx;
  font-weight: 800;
  line-height: 52rpx;
  overflow-wrap: anywhere;
}

.cps-detail-hero__meta {
  display: grid;
  gap: 12rpx;
  min-width: 0;
}

.cps-detail-hero__meta span {
  min-width: 0;
  border: 2rpx solid rgba(255, 255, 255, 0.22);
  border-radius: 16rpx;
  padding: 16rpx 18rpx;
  background: rgba(255, 255, 255, 0.16);
  color: #ffffff;
  font-size: 28rpx;
  font-weight: 800;
  line-height: 40rpx;
  overflow-wrap: anywhere;
}

.cps-detail-card {
  display: grid;
  gap: 24rpx;
  border: 2rpx solid rgba(20, 184, 166, 0.16);
  border-radius: 20rpx;
  padding: 30rpx;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 18rpx 52rpx rgba(15, 23, 42, 0.08);
}

.cps-card-title {
  align-items: center;
}

.cps-card-title p {
  color: #0f766e;
}

.cps-card-title h2 {
  margin: 4rpx 0 0;
  color: #0f172a;
  font-size: 36rpx;
  font-weight: 950;
  line-height: 46rpx;
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

.cps-info-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16rpx;
  min-width: 0;
}

.cps-info-grid div,
.cps-ai-panel div,
.cps-empty-proof {
  min-width: 0;
  border-radius: 16rpx;
  background: linear-gradient(135deg, #f0fdfa 0%, #eff6ff 100%);
}

.cps-info-grid div {
  display: grid;
  gap: 8rpx;
  padding: 18rpx;
}

.cps-info-grid span,
.cps-ai-panel span {
  color: #64748b;
  font-size: 26rpx;
  font-weight: 800;
  line-height: 36rpx;
}

.cps-info-grid strong,
.cps-ai-panel strong {
  color: #0f172a;
  font-size: 32rpx;
  font-weight: 900;
  line-height: 44rpx;
  overflow-wrap: anywhere;
}

.cps-detail-time {
  display: grid;
  gap: 8rpx;
  border-top: 2rpx solid #ccfbf1;
  padding-top: 18rpx;
  color: #64748b;
  font-size: 28rpx;
  font-weight: 700;
  line-height: 40rpx;
}

.cps-detail-time span {
  min-width: 0;
  overflow-wrap: anywhere;
}

.cps-count-badge {
  display: inline-flex;
  align-items: center;
  min-height: 52rpx;
  border-radius: 999rpx;
  padding: 0 18rpx;
  background: #dbeafe;
  color: #1d4ed8;
  font-size: 26rpx;
  font-weight: 900;
  line-height: 36rpx;
  white-space: nowrap;
}

.cps-count-badge--green {
  background: #dcfce7;
  color: #15803d;
}

.cps-photo-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14rpx;
  min-width: 0;
}

.cps-photo-tile {
  position: relative;
  display: block;
  min-width: 0;
  border: 0;
  border-radius: 18rpx;
  padding: 0;
  aspect-ratio: 1;
  background: #e2e8f0;
  overflow: hidden;
}

.cps-photo-tile--lead {
  grid-column: span 2;
  grid-row: span 2;
}

.cps-photo-tile img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cps-photo-tile span {
  position: absolute;
  right: 10rpx;
  bottom: 10rpx;
  min-height: 42rpx;
  border-radius: 999rpx;
  padding: 0 14rpx;
  background: rgba(15, 23, 42, 0.72);
  color: #ffffff;
  font-size: 24rpx;
  font-weight: 900;
  line-height: 42rpx;
}

.cps-empty-proof {
  padding: 30rpx 24rpx;
  color: #64748b;
  font-size: 30rpx;
  font-weight: 800;
  line-height: 44rpx;
  text-align: center;
}

.cps-ai-panel {
  display: grid;
  gap: 16rpx;
  min-width: 0;
}

.cps-ai-panel div {
  display: grid;
  gap: 8rpx;
  padding: 18rpx 20rpx;
}

.cps-ai-panel p {
  display: grid;
  gap: 8rpx;
  min-width: 0;
  margin: 0;
  border-left: 8rpx solid #14b8a6;
  border-radius: 16rpx;
  padding: 18rpx 20rpx;
  background: #f8fafc;
  color: #334155;
  font-size: 30rpx;
  font-weight: 700;
  line-height: 46rpx;
  overflow-wrap: anywhere;
}

.cps-ai-panel b {
  color: #0f172a;
  font-size: 28rpx;
  font-weight: 950;
  line-height: 38rpx;
}

.cps-detail-card :deep(.van-cell-group--inset) {
  width: 100%;
  max-width: 100%;
  margin: 0;
  overflow: hidden;
  border: 2rpx solid #dbeafe;
  border-radius: 18rpx;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: none;
}

.cps-detail-card :deep(.van-cell) {
  align-items: flex-start;
  min-width: 0;
  max-width: 100%;
  overflow: hidden;
}

.cps-detail-card :deep(.van-field__label) {
  width: 176rpx;
  padding-top: 16rpx;
  color: #0f172a;
  font-weight: 800;
}

.cps-detail-card :deep(.van-cell__value),
.cps-detail-card :deep(.van-field__body),
.cps-detail-card :deep(.van-field__control) {
  min-width: 0;
  max-width: 100%;
}

.cps-proof-upload {
  display: grid;
  gap: 18rpx;
  padding: 26rpx;
}

.cps-proof-upload p {
  margin: 0;
  color: #0f172a;
  font-size: 32rpx;
  font-weight: 900;
  line-height: 44rpx;
}

.cps-proof-uploader {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16rpx;
  width: 100%;
  min-width: 0;
}

.cps-proof-uploader__preview,
.cps-proof-uploader__upload {
  position: relative;
  width: 100%;
  min-width: 0;
  min-height: 168rpx;
  border-radius: 18rpx;
  aspect-ratio: 1;
  overflow: hidden;
}

.cps-proof-uploader__preview {
  display: block;
  border: 0;
  padding: 0;
  background: #e2e8f0;
}

.cps-proof-uploader__preview img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cps-proof-uploader__delete {
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

.cps-proof-uploader__upload {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  border: 2rpx dashed #67e8f9;
  padding: 18rpx;
  background: #ecfeff;
  color: #0f766e;
  font-size: 28rpx;
  font-weight: 800;
  line-height: 36rpx;
  text-align: center;
}

.cps-proof-uploader__upload:disabled {
  opacity: 0.68;
}

.cps-proof-uploader__plus {
  font-size: 58rpx;
  font-weight: 400;
  line-height: 58rpx;
}

.cps-inline-action-panel {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18rpx;
  width: 100%;
  min-width: 0;
  padding-top: 4rpx;
}

.cps-inline-action-panel__button {
  position: relative;
  min-width: 0;
  min-height: 104rpx;
  border: 2rpx solid #0f766e;
  border-radius: 14rpx;
  padding: 16rpx 18rpx;
  background: #0f766e;
  color: #ffffff;
  box-shadow: 0 10rpx 22rpx rgba(15, 118, 110, 0.2);
  font-size: 30rpx;
  font-weight: 900;
  line-height: 42rpx;
  text-align: center;
  transition: transform 120ms ease, box-shadow 120ms ease, background-color 120ms ease;
}

.cps-inline-action-panel__button::before {
  position: absolute;
  top: 14rpx;
  bottom: 14rpx;
  left: 0;
  width: 6rpx;
  border-radius: 0 6rpx 6rpx 0;
  background: #99f6e4;
  content: "";
}

.cps-inline-action-panel__button:active {
  transform: translateY(2rpx);
  box-shadow: 0 4rpx 10rpx rgba(15, 118, 110, 0.18);
}

.cps-inline-action-panel__button--danger {
  border-color: #fecaca;
  background: #fff1f2;
  color: #be123c;
  box-shadow: none;
}

.cps-inline-action-panel__button--danger::before {
  background: #e11d48;
}

.cps-inline-action-panel__button--success {
  border-color: #15803d;
  background: #15803d;
  box-shadow: 0 10rpx 22rpx rgba(21, 128, 61, 0.2);
}

.cps-inline-action-panel__button--success::before {
  background: #bbf7d0;
}

.cps-flow-empty {
  padding: 30rpx 24rpx;
  color: #64748b;
  font-size: 30rpx;
  font-weight: 800;
  line-height: 44rpx;
  text-align: center;
}

.cps-flow-timeline {
  display: grid;
  gap: 0;
  min-width: 0;
  margin: 0;
  padding: 0;
  list-style: none;
}

.cps-flow-timeline__item {
  position: relative;
  display: grid;
  grid-template-columns: 38rpx minmax(0, 1fr);
  gap: 16rpx;
  min-width: 0;
  padding-bottom: 18rpx;
}

.cps-flow-timeline__item::before {
  position: absolute;
  top: 34rpx;
  bottom: 0;
  left: 17rpx;
  width: 4rpx;
  border-radius: 999rpx;
  background: #ccfbf1;
  content: "";
}

.cps-flow-timeline__item:last-child {
  padding-bottom: 0;
}

.cps-flow-timeline__item:last-child::before {
  display: none;
}

.cps-flow-timeline__dot {
  position: relative;
  z-index: 1;
  width: 38rpx;
  height: 38rpx;
  border: 8rpx solid #ccfbf1;
  border-radius: 999rpx;
  background: #14b8a6;
}

.cps-flow-timeline__dot.is-last {
  border-color: #dbeafe;
  background: #2563eb;
}

.cps-flow-timeline__content {
  display: grid;
  gap: 10rpx;
  min-width: 0;
  border-radius: 16rpx;
  padding: 20rpx;
  background: linear-gradient(135deg, #f0fdfa 0%, #eff6ff 100%);
}

.cps-flow-timeline__head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18rpx;
  min-width: 0;
}

.cps-flow-timeline__head strong {
  min-width: 0;
  color: #0f172a;
  font-size: 30rpx;
  font-weight: 950;
  line-height: 42rpx;
  overflow-wrap: anywhere;
}

.cps-flow-timeline__head time,
.cps-flow-timeline__content p {
  margin: 0;
  color: #64748b;
  font-size: 26rpx;
  font-weight: 700;
  line-height: 38rpx;
  overflow-wrap: anywhere;
}

.cps-flow-timeline__comment {
  border-radius: 14rpx;
  padding: 14rpx 16rpx;
  background: #ffffff;
  color: #0f172a;
}

.cps-page-loading {
  display: flex;
  justify-content: center;
  margin-top: 64rpx;
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
</style>
