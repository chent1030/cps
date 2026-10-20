<template>
  <main v-if="detail" class="cps-page cps-detail-page">
    <header class="cps-hero">
      <div class="cps-hero__top">
        <div class="cps-hero__identity">
          <p class="cps-hero__eyebrow">CPS 巡检问题</p>
          <h1 class="cps-hero__title">{{ detail.description }}</h1>
        </div>
        <div class="cps-hero__badges">
          <a-tag :color="detailStatus.color" class="cps-pill">{{ detailStatus.label }}</a-tag>
          <a-tag v-if="detail.overdue" color="red" class="cps-pill">超时</a-tag>
        </div>
      </div>
      <div class="cps-hero__meta">
        <span>{{ issueLocation }}</span>
        <span>{{ issueCategory }}</span>
        <span>提交 {{ formatDateTime(detail.submitTime) }}</span>
      </div>
      <ol class="cps-steps" aria-label="流程进度">
        <li
          v-for="(node, index) in statusSteps"
          :key="node.value"
          class="cps-steps__item"
          :class="{ 'is-done': index < currentStepIndex, 'is-active': index === currentStepIndex }"
        >
          <span class="cps-steps__num">{{ index + 1 }}</span>
          <span class="cps-steps__label">{{ node.label }}</span>
        </li>
      </ol>
    </header>

    <a-row :gutter="[16, 16]">
      <!-- 左列：基础信息 + 照片 + AI -->
      <a-col :xs="{ span: 24 }" :xl="{ span: 14 }">
        <a-card class="cps-card" :body-style="{ padding: '16px' }">
          <template #title>
            <div class="cps-card-title">
              <h2 class="cps-card-title__main">处理责任</h2>
            </div>
          </template>
          <a-descriptions :column="{ xs: 2, sm: 3 }" size="small" bordered>
            <a-descriptions-item label="提交人">{{ detail.creatorEmpNo || '-' }}</a-descriptions-item>
            <a-descriptions-item label="当前处理人">{{ detail.currentHandlerEmpName || detail.currentHandlerEmpNo || '-' }}</a-descriptions-item>
            <a-descriptions-item label="反馈人">{{ detail.feedbackEmpNo || '-' }}</a-descriptions-item>
            <a-descriptions-item label="责任人">{{ detail.responsibleEmpNo || '-' }}</a-descriptions-item>
            <a-descriptions-item label="上传人">{{ detail.proofEmpNo || '-' }}</a-descriptions-item>
            <a-descriptions-item label="审核人">{{ detail.reviewerEmpNo || '-' }}</a-descriptions-item>
            <a-descriptions-item label="资源号">{{ detail.resourceNo || '-' }}</a-descriptions-item>
            <a-descriptions-item v-if="detail.inspectionTypeCode" label="巡检类型">
              <a-tag color="arcoblue">{{ detail.inspectionTypeCode }}</a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="提交时间">{{ detail.submitTime || '-' }}</a-descriptions-item>
            <a-descriptions-item v-if="detail.closeTime" label="关闭时间">{{ detail.closeTime }}</a-descriptions-item>
          </a-descriptions>
        </a-card>

        <a-card class="cps-card" :body-style="{ padding: '16px' }" style="margin-top: 16px">
          <template #title>
            <div class="cps-card-title">
              <h2 class="cps-card-title__main">问题现场</h2>
              <a-tag color="blue">{{ detail.issueAttachments.length }} 张</a-tag>
            </div>
          </template>
          <div v-if="detail.issueAttachments.length" class="cps-photo-grid">
            <div
              v-for="(image, index) in detail.issueAttachments"
              :key="image.id"
              class="cps-photo-tile"
              :class="{ 'cps-photo-tile--lead': detail.issueAttachments.length > 1 && index === 0 }"
              @click="previewAttachments(detail.issueAttachments, index)"
            >
              <img :src="attachmentImageSources[image.id] || ''" :alt="image.fileName" />
              <span>{{ index + 1 }}/{{ detail.issueAttachments.length }}</span>
            </div>
          </div>
          <a-empty v-else description="当前问题还没有现场照片" />
        </a-card>

        <a-card class="cps-card" :body-style="{ padding: '16px' }" style="margin-top: 16px">
          <template #title>
            <div class="cps-card-title">
              <h2 class="cps-card-title__main">复核照片</h2>
              <a-tag color="green">{{ detail.proofAttachments.length }} 张</a-tag>
            </div>
          </template>
          <div v-if="detail.proofAttachments.length" class="cps-photo-grid">
            <div
              v-for="(image, index) in detail.proofAttachments"
              :key="image.id"
              class="cps-photo-tile"
              @click="previewAttachments(detail.proofAttachments, index)"
            >
              <img :src="attachmentImageSources[image.id] || ''" :alt="image.fileName" />
              <span>{{ index + 1 }}/{{ detail.proofAttachments.length }}</span>
            </div>
          </div>
          <a-empty v-else description="当前节点还没有上传整改照片" />
        </a-card>

        <a-card class="cps-card" :body-style="{ padding: '16px' }" style="margin-top: 16px">
          <template #title>
            <div class="cps-card-title">
              <h2 class="cps-card-title__main">原因措施</h2>
            </div>
          </template>
          <div class="cps-ai-grid">
            <div class="cps-ai-item">
              <span class="cps-ai-item__label">AI 分类</span>
              <strong class="cps-ai-item__value">{{ aiCategory }}</strong>
            </div>
            <div class="cps-ai-item">
              <span class="cps-ai-item__label">人工分类</span>
              <strong class="cps-ai-item__value">{{ issueCategory }}</strong>
            </div>
            <div class="cps-ai-item">
              <span class="cps-ai-item__label">AI 原因</span>
              <p class="cps-ai-item__text">{{ detail.aiSuggestion?.reasonSuggestion || '-' }}</p>
            </div>
            <div class="cps-ai-item">
              <span class="cps-ai-item__label">AI 措施</span>
              <p class="cps-ai-item__text">{{ detail.aiSuggestion?.measureSuggestion || '-' }}</p>
            </div>
            <div class="cps-ai-item">
              <span class="cps-ai-item__label">人工原因</span>
              <p class="cps-ai-item__text">{{ detail.reasonAnalysis || '-' }}</p>
            </div>
            <div class="cps-ai-item">
              <span class="cps-ai-item__label">人工措施</span>
              <p class="cps-ai-item__text">{{ detail.correctiveMeasure || '-' }}</p>
            </div>
          </div>
        </a-card>
      </a-col>

      <!-- 右列：节点处理 + 流转记录（横屏并排，竖屏置顶） -->
      <a-col :xs="{ span: 24 }" :xl="{ span: 10 }" class="cps-col-action">
        <a-card class="cps-card" :body-style="{ padding: '16px' }">
          <template #title>
            <div class="cps-card-title">
              <h2 class="cps-card-title__main">当前操作</h2>
              <div v-if="transferAvailable" class="cps-mode-switch">
                <span class="cps-mode-switch__label" :class="{ 'is-on': transferMode }">转办</span>
                <a-switch v-model:checked="transferMode" size="small" />
              </div>
            </div>
          </template>

          <!-- 转办模式：只填转办目标，常规处理输入全部隐藏 -->
          <template v-if="transferMode">
            <div class="cps-mode-hint">
              <strong>转交给他人处理</strong>
              <span>当前节点将直接转交，无需填写常规处理内容</span>
            </div>
            <a-form layout="vertical">
              <a-form-item label="转办目标" required>
                <a-input :value="personLabel(actionForm.targetEmpNo)" placeholder="选择转办目标" read-only @click="openPersonPicker('targetEmpNo', '选择转办目标')">
                  <template #suffix><UserOutlined /></template>
                </a-input>
              </a-form-item>
              <a-form-item label="转办说明">
                <a-textarea v-model:value="actionForm.comment" :rows="3" placeholder="说明转办原因，便于接收人跟进" />
              </a-form-item>
            </a-form>
            <div class="cps-actions">
              <a-button block size="large" type="primary" class="cps-actions__btn" :loading="actionLoading === 'TRANSFER'" @click="runAction('TRANSFER')">确认转办</a-button>
              <a-button block size="large" class="cps-actions__btn" :disabled="actionLoading === 'TRANSFER'" @click="cancelTransfer">取消</a-button>
            </div>
          </template>

          <!-- 常规模式：按节点填写 -->
          <template v-else>
            <a-form v-if="showAnyActionFields" layout="vertical">
              <template v-if="showFeedbackFields">
                <!-- 第二流程：反馈人员确认/修正问题分类（默认填入 AI 分类） -->
                <a-form-item label="问题分类（一级）">
                  <a-radio-group :value="actionForm.categoryL1Id ?? undefined" button-style="solid" class="cps-selector-group" @change="(e: any) => onSelectCategoryL1(Number(e.target.value))">
                    <a-radio-button v-for="item in level1Categories" :key="item.value" :value="Number(item.value)">{{ item.label }}</a-radio-button>
                  </a-radio-group>
                  <span v-if="!level1Categories.length" class="cps-muted">暂无一级分类</span>
                  <div class="cps-default-hint">默认采用 AI 识别分类，可确认或修正</div>
                </a-form-item>
                <a-form-item label="问题分类（二级）">
                  <a-radio-group :value="actionForm.categoryL2Id ?? undefined" button-style="solid" class="cps-selector-group" @change="(e: any) => onSelectCategoryL2(Number(e.target.value))">
                    <a-radio-button v-for="item in level2Categories" :key="item.value" :value="Number(item.value)">{{ item.label }}</a-radio-button>
                  </a-radio-group>
                  <span v-if="!level2Categories.length" class="cps-muted">先选择一级分类</span>
                </a-form-item>
                <a-form-item label="原因分析">
                  <a-textarea v-model:value="actionForm.reasonAnalysis" :rows="3" placeholder="填写原因分析" />
                </a-form-item>
                <a-form-item label="整改措施">
                  <a-textarea v-model:value="actionForm.correctiveMeasure" :rows="3" placeholder="填写整改措施" />
                </a-form-item>
                <a-form-item label="责任员工">
                  <a-input :value="personLabel(actionForm.responsibleEmpNo)" placeholder="选择责任员工" read-only @click="openPersonPicker('responsibleEmpNo', '选择责任员工')">
                    <template #suffix><UserOutlined /></template>
                  </a-input>
                  <div v-if="defaultPersonHint('responsibleEmpNo')" class="cps-default-hint">已默认填入上一节点操作者，可修改</div>
                </a-form-item>
              </template>

              <template v-if="showRectifyFields">
                <a-form-item label="整改说明">
                  <a-textarea v-model:value="actionForm.rectifyRemark" :rows="3" placeholder="填写整改说明" />
                </a-form-item>
                <a-form-item label="上传人">
                  <a-input :value="personLabel(actionForm.proofEmpNo)" placeholder="选择上传人" read-only @click="openPersonPicker('proofEmpNo', '选择上传人')">
                    <template #suffix><UserOutlined /></template>
                  </a-input>
                  <div v-if="defaultPersonHint('proofEmpNo')" class="cps-default-hint">已默认填入上一节点操作者，可修改</div>
                </a-form-item>
              </template>

              <template v-if="showProofFields">
                <a-form-item label="审核人">
                  <a-input :value="personLabel(actionForm.reviewerEmpNo)" placeholder="选择审核人" read-only @click="openPersonPicker('reviewerEmpNo', '选择审核人')">
                    <template #suffix><UserOutlined /></template>
                  </a-input>
                </a-form-item>
                <a-form-item label="整改照片">
                  <!-- 整改照片上传（内联） -->
                  <a-upload list-type="picture-card" :file-list="proofFileList" :max-count="5" :custom-request="customProofRequest" :before-upload="beforeProofUpload" :show-upload-list="false" multiple accept="image/*">
                    <div v-if="proofImages.length < 5" class="cps-uploader__trigger">
                      <span class="cps-uploader__plus">+</span>
                      <span class="cps-uploader__text">{{ proofUploading ? '上传中' : '上传整改图片' }}</span>
                    </div>
                  </a-upload>
                  <div class="cps-uploader__grid">
                    <div v-for="(image, index) in proofImages" :key="image.id" class="cps-uploader__item" @click="previewProofImage(index)">
                      <img :src="attachmentImageSources[image.id] || ''" :alt="image.name" />
                      <span v-if="!proofUploading" class="cps-uploader__delete" @click.stop="removeProofImage(index)">×</span>
                      <span class="cps-uploader__index">{{ index + 1 }}</span>
                    </div>
                  </div>
                  <p class="cps-uploader__hint">最多上传 5 张</p>
                </a-form-item>
              </template>

              <template v-if="showReviewFields">
                <a-form-item label="审核意见">
                  <a-textarea v-model:value="actionForm.reviewOpinion" :rows="3" placeholder="填写审核意见" />
                </a-form-item>
              </template>

              <a-form-item label="流转备注">
                <a-textarea v-model:value="actionForm.comment" :rows="2" placeholder="填写备注" />
              </a-form-item>
            </a-form>

            <a-empty v-if="!normalActions.length" description="当前节点没有可执行的操作" />
            <div v-else class="cps-actions">
              <a-button
                v-for="action in normalActions"
                :key="action"
                block
                size="large"
                :type="action === 'REVIEW_CLOSE' || normalActions.length === 1 ? 'primary' : 'default'"
                :danger="action === 'REVIEW_REJECT'"
                class="cps-actions__btn"
                :class="{ 'cps-actions__btn--success': action === 'REVIEW_CLOSE', 'cps-actions__btn--danger': action === 'REVIEW_REJECT' }"
                :loading="actionLoading === action"
                @click="runAction(action)"
              >
                {{ actionLabels[action] }}
              </a-button>
            </div>
            <p v-if="transferAvailable" class="cps-action-tip">需要转交他人处理？打开右上角「转办」开关</p>
          </template>
        </a-card>

        <a-card class="cps-card" :body-style="{ padding: '16px' }" style="margin-top: 16px">
          <template #title>
            <div class="cps-card-title">
              <h2 class="cps-card-title__main">处理轨迹</h2>
            </div>
          </template>
          <!-- 流程时间线（内联） -->
          <a-empty v-if="!detail.flowLogs.length" description="暂无流转记录" />
          <a-timeline v-else>
            <a-timeline-item v-for="(log, index) in detail.flowLogs" :key="`${log.action}-${log.createdAt}-${index}`" :color="index === detail.flowLogs.length - 1 ? 'blue' : 'cyan'">
              <template #dot>
                <span class="cps-flow__dot" :class="{ 'is-last': index === detail.flowLogs.length - 1 }" />
              </template>
              <div class="cps-flow__head">
                <strong>{{ flowActionLabel(log.action) }}</strong>
                <time>{{ log.createdAt }}</time>
              </div>
              <p class="cps-flow__line">{{ flowStatusLabel(log.fromStatus) }} 至 {{ flowStatusLabel(log.toStatus) }}</p>
              <p class="cps-flow__line">操作人：{{ log.operatorEmpNo }}</p>
              <p v-if="log.comment" class="cps-flow__comment">{{ log.comment }}</p>
            </a-timeline-item>
          </a-timeline>
        </a-card>
      </a-col>
    </a-row>
  </main>

  <main v-else class="cps-page">
    <a-spin class="cps-page-loading" />
  </main>

  <!-- 人员选择弹窗（内联） -->
  <a-modal
    :open="personPickerOpen"
    :title="personPickerTitle"
    :width="isLandscape ? 520 : '100%'"
    :centered="isLandscape"
    :footer="null"
    :destroy-on-close="false"
    @update:open="(v: boolean) => (personPickerOpen = v)"
  >
    <a-input-search
      v-model:value="personKeyword"
      placeholder="输入姓名或工号搜索"
      allow-clear
      enter-button="搜索"
      :loading="personSearching"
      class="cps-person-picker__search"
      @search="onSearchPeople"
    />
    <a-spin :spinning="personSearching">
      <div class="cps-person-picker__body">
        <a-empty v-if="!pickerPeople.length" description="未找到人员，请调整关键词后搜索" />
        <a-radio-group :value="actionForm[personPickerField]" class="cps-person-picker__group" @change="(e: any) => onSelectPerson(e.target.value)">
          <a-radio v-for="person in pickerPeople" :key="person.empNo" :value="person.empNo" class="cps-person-picker__option">
            <strong>{{ person.empName || person.empNo }}</strong>
            <span>{{ person.empNo }}</span>
          </a-radio>
        </a-radio-group>
      </div>
    </a-spin>
  </a-modal>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { UserOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import type { UploadProps } from 'ant-design-vue'

import { executeCpsIssueAction, getCpsIssueDetail } from '@/api/cps/issue'
import { getCategories, searchCpsEmployees, type CpsEmployeeOption, type CpsOption } from '@/api/cps/master'
import { uploadCpsAttachment } from '@/api/cps/attachment'
import { loadAttachmentImageSources } from '@/utils/image'
import { statusMeta, issueLocation as formatLocation, issueCategory as formatCategory, personLabel as formatPersonLabel } from '@/utils/format'
import { resolveDefaultPersons, type ActionPersonField } from '@/utils/personDefault'
import type {
  CpsAttachment,
  CpsIssueAction,
  CpsIssueDetail,
  CpsIssueStatus,
  CpsUploadedImage,
} from '@/types/cps'

interface DetailStatusMeta {
  label: string
  color: string
}

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
const defaultAppliedFields = ref<Set<ActionPersonField>>(new Set())
// 转办模式开关 + 正在提交的动作
const transferMode = ref(false)
const actionLoading = ref<CpsIssueAction | null>(null)

// 分类选项（第二流程：反馈人员确认/修正分类）
const level1Categories = ref<CpsOption[]>([])
const level2Categories = ref<CpsOption[]>([])

// 人员选择器状态（内联）
const personPickerOpen = ref(false)
const personPickerTitle = ref('')
const personPickerField = ref<ActionPersonField>('responsibleEmpNo')
const personKeyword = ref('')
const personSearching = ref(false)
const personSearched = ref(false)
const personSearchResults = ref<CpsEmployeeOption[]>([])

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
  categoryL1Id: null as number | null,
  categoryL2Id: null as number | null,
})

const isLandscape = ref(false)
const updateOrientation = () => {
  isLandscape.value = typeof window !== 'undefined' && window.innerWidth >= 1200
}
if (typeof window !== 'undefined') {
  updateOrientation()
  window.addEventListener('resize', updateOrientation)
}

// 标签字典（内联）
const actionLabels: Record<CpsIssueAction, string> = {
  REPLY_ASSIGN: '回复并指派',
  RECTIFY: '完成整改',
  UPLOAD_PROOF: '上传凭证',
  REVIEW_CLOSE: '审核通过',
  REVIEW_REJECT: '审核退回',
  TRANSFER: '转办',
}

const flowActionLabels: Record<string, string> = {
  SUBMIT: '提交问题',
  REPLY_ASSIGN: '回复并指派',
  RECTIFY: '完成整改',
  UPLOAD_PROOF: '上传凭证',
  REVIEW_CLOSE: '审核通过',
  REVIEW_REJECT: '审核退回',
  TRANSFER: '转办',
}

const flowStatusLabels: Record<CpsIssueStatus, string> = {
  PENDING_FEEDBACK: '待反馈',
  PENDING_RECTIFY: '待整改',
  PENDING_UPLOAD_PROOF: '待传图',
  PENDING_REVIEW: '待审核',
  CLOSED: '已关闭',
}

const flowActionLabel = (action: string) => flowActionLabels[action] ?? action
const flowStatusLabel = (status: CpsIssueStatus | null) => (status ? flowStatusLabels[status] : '开始')

const showFeedbackFields = computed(() => detail.value?.status === 'PENDING_FEEDBACK')
const showRectifyFields = computed(() => detail.value?.status === 'PENDING_RECTIFY')
const showProofFields = computed(() => detail.value?.status === 'PENDING_UPLOAD_PROOF')
const showReviewFields = computed(() => detail.value?.status === 'PENDING_REVIEW')
const workflowActions = computed<CpsIssueAction[]>(() => {
  if (!detail.value) return []
  return detail.value.availableActions.length ? detail.value.availableActions : statusDefaultActions[detail.value.status]
})
// 转办独立成模式：开启后只填转办目标，常规操作项不再展示
const transferAvailable = computed(() => workflowActions.value.includes('TRANSFER'))
const normalActions = computed<CpsIssueAction[]>(() => workflowActions.value.filter((action) => action !== 'TRANSFER'))
const showAnyActionFields = computed(
  () => showFeedbackFields.value || showRectifyFields.value || showProofFields.value || showReviewFields.value,
)

const statusSteps: { value: CpsIssueStatus; label: string }[] = [
  { value: 'PENDING_FEEDBACK', label: '待反馈' },
  { value: 'PENDING_RECTIFY', label: '待整改' },
  { value: 'PENDING_UPLOAD_PROOF', label: '待传图' },
  { value: 'PENDING_REVIEW', label: '待审核' },
  { value: 'CLOSED', label: '已关闭' },
]
const currentStepIndex = computed(() => {
  if (!detail.value) return 0
  const index = statusSteps.findIndex((node) => node.value === detail.value?.status)
  return index < 0 ? 0 : index
})
const formatDateTime = (value?: string | null) => (value ? value.replace('T', ' ').slice(0, 16) : '-')

const statusDefaultActions: Record<CpsIssueStatus, CpsIssueAction[]> = {
  PENDING_FEEDBACK: ['REPLY_ASSIGN', 'TRANSFER'],
  PENDING_RECTIFY: ['RECTIFY', 'TRANSFER'],
  PENDING_UPLOAD_PROOF: ['UPLOAD_PROOF', 'TRANSFER'],
  PENDING_REVIEW: ['REVIEW_CLOSE', 'REVIEW_REJECT', 'TRANSFER'],
  CLOSED: [],
}

const detailStatus = computed<DetailStatusMeta>(() =>
  detail.value ? { label: statusMeta[detail.value.status].label, color: statusMeta[detail.value.status].color } : { label: '待反馈', color: 'blue' },
)

const issueLocation = computed(() => (detail.value ? formatLocation(detail.value) : '-'))
const issueCategory = computed(() => (detail.value ? formatCategory(detail.value) : '-'))
const aiCategory = computed(() =>
  detail.value?.aiSuggestion
    ? [detail.value.aiSuggestion.aiCategoryL1Name, detail.value.aiSuggestion.aiCategoryL2Name].filter(Boolean).join(' / ') || '-'
    : '-',
)

const knownPeople = computed<CpsEmployeeOption[]>(() => {
  if (!detail.value) return []
  const people = [
    { empNo: detail.value.creatorEmpNo, empName: detail.value.creatorEmpNo },
    { empNo: detail.value.feedbackEmpNo, empName: detail.value.feedbackEmpNo },
    { empNo: detail.value.responsibleEmpNo, empName: detail.value.responsibleEmpName || detail.value.responsibleEmpNo },
    { empNo: detail.value.proofEmpNo, empName: detail.value.proofEmpName || detail.value.proofEmpNo },
    { empNo: detail.value.reviewerEmpNo, empName: detail.value.reviewerEmpName || detail.value.reviewerEmpNo },
    { empNo: detail.value.currentHandlerEmpNo, empName: detail.value.currentHandlerEmpName || detail.value.currentHandlerEmpNo },
  ].filter((person): person is CpsEmployeeOption => Boolean(person.empNo))
  return [...new Map(people.map((person) => [person.empNo, person])).values()]
})

// 未搜索时展示已知人员；搜索后展示搜索结果（合并去重）
const pickerPeople = computed<CpsEmployeeOption[]>(() => {
  if (personSearched.value) {
    return [
      ...personSearchResults.value,
      ...knownPeople.value.filter((c) => !personSearchResults.value.some((s) => s.empNo === c.empNo)),
    ]
  }
  return knownPeople.value
})

const proofFileList = computed(() =>
  proofImages.value.map((image) => ({
    uid: String(image.id),
    name: image.name,
    status: 'done' as const,
    url: attachmentImageSources.value[image.id],
  })),
)

const personLabel = (empNo: string) => {
  if (!empNo) return ''
  const person = selectedPeople.value[empNo] ?? knownPeople.value.find((item) => item.empNo === empNo)
  return formatPersonLabel(person?.empName, empNo)
}

const defaultPersonHint = (field: ActionPersonField) => defaultAppliedFields.value.has(field)

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

/**
 * 加载详情后：重置表单，并按「后续节点默认选择对应人员」预填。
 * 状态每次变化（动作执行后 reload）都会重新计算默认值。
 */
const resetActionForm = () => {
  actionForm.value = {
    reasonAnalysis: '',
    correctiveMeasure: '',
    responsibleEmpNo: '',
    proofEmpNo: '',
    reviewerEmpNo: '',
    rectifyRemark: '',
    reviewOpinion: '',
    targetEmpNo: '',
    comment: '',
    categoryL1Id: null,
    categoryL2Id: null,
  }
  defaultAppliedFields.value = new Set()
}

const applyDefaultPersons = () => {
  if (!detail.value) return
  resetActionForm()
  // 每次加载后回到常规处理模式
  transferMode.value = false
  const defaults = resolveDefaultPersons(detail.value)
  for (const { field, person } of defaults) {
    actionForm.value[field] = person.empNo
    selectedPeople.value = { ...selectedPeople.value, [person.empNo]: person }
    defaultAppliedFields.value.add(field)
  }
  // 第二流程（反馈回复）：默认填入 AI 识别的分类，供反馈人员确认/修正
  if (detail.value.status === 'PENDING_FEEDBACK' && detail.value.aiSuggestion) {
    actionForm.value.categoryL1Id = detail.value.aiSuggestion.aiCategoryL1Id
    actionForm.value.categoryL2Id = detail.value.aiSuggestion.aiCategoryL2Id
    void loadLevel2Categories(detail.value.aiSuggestion.aiCategoryL1Id)
  }
}

// 分类选项加载
const loadLevel1Categories = async () => {
  if (!level1Categories.value.length) {
    level1Categories.value = await getCategories()
  }
}

const loadLevel2Categories = async (parentId: number | null) => {
  level2Categories.value = parentId ? await getCategories(parentId) : []
}

const onSelectCategoryL1 = async (categoryL1Id: number) => {
  actionForm.value.categoryL1Id = categoryL1Id
  actionForm.value.categoryL2Id = null
  await loadLevel2Categories(categoryL1Id)
}

const onSelectCategoryL2 = (categoryL2Id: number) => {
  actionForm.value.categoryL2Id = categoryL2Id
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
  const sources = await loadAttachmentImageSources([...detail.value.issueAttachments, ...detail.value.proofAttachments])
  attachmentImageSources.value = sources
  applyDefaultPersons()
}

// 人员选择器（内联）
const openPersonPicker = (field: ActionPersonField, title: string) => {
  personPickerField.value = field
  personPickerTitle.value = title
  personPickerOpen.value = true
}

watch(personPickerOpen, (v) => {
  if (v) {
    personKeyword.value = ''
    personSearched.value = false
    personSearchResults.value = []
  }
})

const onSearchPeople = async () => {
  const kw = personKeyword.value.trim()
  if (!kw || personSearching.value) return
  personSearching.value = true
  try {
    personSearchResults.value = await searchCpsEmployees(kw)
    personSearched.value = true
  } finally {
    personSearching.value = false
  }
}

const onSelectPerson = (empNo: string) => {
  const person = pickerPeople.value.find((p) => p.empNo === empNo)
  if (person) {
    actionForm.value[personPickerField.value] = empNo
    selectedPeople.value = { ...selectedPeople.value, [empNo]: person }
    defaultAppliedFields.value.delete(personPickerField.value)
    personPickerOpen.value = false
  }
}

const buildActionPayload = (action: CpsIssueAction) => {
  const personName = (empNo: string) => selectedPeople.value[empNo]?.empName ?? knownPeople.value.find((item) => item.empNo === empNo)?.empName
  return {
    action,
    reasonAnalysis: actionForm.value.reasonAnalysis || undefined,
    correctiveMeasure: actionForm.value.correctiveMeasure || undefined,
    responsibleEmpNo: actionForm.value.responsibleEmpNo || undefined,
    responsibleEmpName: personName(actionForm.value.responsibleEmpNo),
    proofEmpNo: actionForm.value.proofEmpNo || undefined,
    proofEmpName: personName(actionForm.value.proofEmpNo),
    reviewerEmpNo: actionForm.value.reviewerEmpNo || undefined,
    reviewerEmpName: personName(actionForm.value.reviewerEmpNo),
    rectifyRemark: actionForm.value.rectifyRemark || undefined,
    reviewOpinion: actionForm.value.reviewOpinion || undefined,
    proofAttachmentIds: action === 'UPLOAD_PROOF' ? proofImages.value.map((image) => image.id) : undefined,
    targetEmpNo: actionForm.value.targetEmpNo || undefined,
    targetEmpName: personName(actionForm.value.targetEmpNo),
    comment: actionForm.value.comment || undefined,
    // 反馈回复节点提交分类（确认/修正后的最终分类）
    categoryL1Id: action === 'REPLY_ASSIGN' ? actionForm.value.categoryL1Id ?? undefined : undefined,
    categoryL2Id: action === 'REPLY_ASSIGN' ? actionForm.value.categoryL2Id ?? undefined : undefined,
  }
}

const runAction = async (action: CpsIssueAction) => {
  if (actionLoading.value) return
  if (action === 'TRANSFER' && !actionForm.value.targetEmpNo) {
    message.warning('请选择转办目标')
    return
  }
  actionLoading.value = action
  try {
    await executeCpsIssueAction(issueId.value, buildActionPayload(action))
    message.success(action === 'TRANSFER' ? '已转办' : '操作成功')
    await load()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '操作失败')
  } finally {
    actionLoading.value = null
  }
}

const cancelTransfer = () => {
  transferMode.value = false
}

const previewAttachments = (attachments: CpsAttachment[], startPosition: number) => {
  const target = attachments[startPosition]
  if (!target) return
  const urls = attachments.map((image) => attachmentImageSources.value[image.id]).filter(Boolean)
  if (typeof uni !== 'undefined' && typeof uni.previewImage === 'function') {
    uni.previewImage({ urls, current: attachmentImageSources.value[target.id] })
  }
}

const previewProofImage = (index: number) => {
  const target = proofImages.value[index]
  if (!target) return
  const urls = proofImages.value.map((i) => attachmentImageSources.value[i.id]).filter(Boolean)
  if (typeof uni !== 'undefined' && typeof uni.previewImage === 'function') {
    uni.previewImage({ urls, current: attachmentImageSources.value[target.id] })
  }
}

// 整改照片上传（内联）
const beforeProofUpload: UploadProps['beforeUpload'] = (_file, fileList) => {
  const remaining = Math.max(5 - proofImages.value.length, 0)
  if (fileList.length > remaining) return false
  return true
}

const customProofRequest: UploadProps['customRequest'] = async (options) => {
  const { file, onSuccess, onError } = options
  proofUploading.value = true
  try {
    const uploaded = await uploadCpsAttachment(file as File)
    proofImages.value = [...proofImages.value, uploaded]
    // 同步加载预览源
    const sources = await loadAttachmentImageSources([{ id: uploaded.id, fileUrl: uploaded.url, fileName: uploaded.name }])
    attachmentImageSources.value = { ...attachmentImageSources.value, ...sources }
    onSuccess?.({}, file as any)
  } catch (error) {
    onError?.(error as any)
  } finally {
    proofUploading.value = false
  }
}

const removeProofImage = (index: number) => {
  proofImages.value = proofImages.value.filter((_, i) => i !== index)
}

onLoad((query?: Record<string, string | string[] | undefined>) => {
  const rawId = Array.isArray(query?.id) ? query?.id[0] : query?.id
  issueId.value = Number(rawId)
  void load()
  // 预载一级分类选项（第二流程反馈节点需要）
  void loadLevel1Categories()
})
</script>

<style scoped>
.cps-page {
  width: 100%;
  max-width: 720px;
  margin: 0 auto;
  padding: 16px 16px 48px;
}

.cps-hero {
  border-radius: var(--cps-radius-xl);
  padding: 20px 24px;
  background: var(--cps-hero-bg);
  box-shadow: var(--cps-shadow-hero);
  color: #fff;
  margin-bottom: 16px;
}

.cps-hero__top {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.cps-hero__eyebrow {
  margin: 0;
  color: rgba(255, 255, 255, 0.82);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.18em;
}

.cps-hero h1 {
  margin: 6px 0 0;
  font-size: 22px;
  font-weight: 800;
  line-height: 1.45;
  letter-spacing: -0.01em;
  word-break: break-word;
}

.cps-hero__badges {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  justify-content: flex-end;
}

/* 流程进度条：5 个节点，当前节点高亮 */
.cps-steps {
  display: flex;
  gap: 6px;
  margin: 16px 0 0;
  padding: 0;
  list-style: none;
  overflow-x: auto;
}

.cps-steps__item {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 6px;
  border-radius: var(--cps-radius-pill);
  padding: 5px 10px 5px 6px;
  background: rgba(255, 255, 255, 0.12);
  color: rgba(255, 255, 255, 0.72);
  font-size: 12px;
  font-weight: 700;
}

.cps-steps__num {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.2);
  font-size: 11px;
  font-weight: 800;
}

.cps-steps__item.is-done {
  color: rgba(255, 255, 255, 0.92);
}

.cps-steps__item.is-done .cps-steps__num {
  background: rgba(255, 255, 255, 0.42);
  color: #115e59;
}

.cps-steps__item.is-active {
  background: #fff;
  color: var(--cps-teal-deep);
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.18);
}

.cps-steps__item.is-active .cps-steps__num {
  background: var(--cps-teal-deep);
  color: #fff;
}

.cps-hero__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.cps-hero__meta span {
  flex: 0 1 auto;
  max-width: 100%;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.22);
  border-radius: var(--cps-radius-md);
  padding: 8px 12px;
  background: rgba(255, 255, 255, 0.16);
  font-size: 14px;
  font-weight: 700;
  line-height: 1.4;
  text-overflow: ellipsis;
}

.cps-pill {
  border-radius: var(--cps-radius-pill) !important;
  font-weight: 700 !important;
  margin: 0 !important;
}

.cps-card {
  border-radius: var(--cps-radius-lg);
  border: 1px solid var(--cps-border);
  box-shadow: var(--cps-shadow-card);
}

.cps-card :deep(.ant-card-head) {
  min-height: 54px;
  padding: 0 16px;
  border-bottom: 1px solid var(--cps-border);
}

.cps-card-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.cps-card-title__main {
  margin: 0;
  color: #0f172a;
  font-size: 17px;
  font-weight: 800;
  letter-spacing: -0.01em;
}

.cps-photo-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  gap: 12px;
}

.cps-photo-tile {
  position: relative;
  width: 100%;
  aspect-ratio: 1;
  border-radius: 12px;
  overflow: hidden;
  background: #e2e8f0;
  cursor: pointer;
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
  right: 6px;
  bottom: 6px;
  min-width: 24px;
  height: 22px;
  border-radius: 999px;
  padding: 0 6px;
  background: rgba(15, 23, 42, 0.72);
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  line-height: 22px;
}

.cps-ai-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 10px;
}

@media (min-width: 640px) {
  .cps-ai-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

.cps-ai-item {
  border: 1px solid var(--cps-border);
  border-radius: var(--cps-radius-md);
  padding: 12px;
  background: #fff;
}

.cps-ai-item__label {
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.cps-ai-item__value {
  display: block;
  margin-top: 4px;
  color: #0f172a;
  font-size: 15px;
  font-weight: 800;
}

.cps-ai-item__text {
  margin: 6px 0 0;
  color: var(--cps-ink-2);
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
}

.cps-default-hint {
  margin-top: 4px;
  color: #0f766e;
  font-size: 12px;
}

/* 分类选择器（第二流程反馈节点） */
.cps-selector-group {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.cps-selector-group :deep(.ant-radio-button-wrapper) {
  border-radius: var(--cps-radius-pill);
  border: 1px solid var(--cps-border-strong);
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

/* 整改照片上传（内联） */
.cps-uploader__trigger {
  display: grid;
  align-items: center;
  justify-items: center;
  gap: 4px;
  width: 100%;
  height: 100%;
  color: #0f766e;
}

.cps-detail-page :deep(.ant-upload.ant-upload-select) {
  width: 100%;
  height: auto;
  margin-bottom: 12px;
}

.cps-detail-page :deep(.ant-upload.ant-upload-select .ant-upload) {
  width: 100%;
  padding: 18px;
  border-radius: var(--cps-radius-lg);
  background: var(--cps-mist);
  border: 1px dashed #99f6e4;
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
  margin-top: 12px;
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

.cps-uploader__hint {
  margin: 8px 0 0;
  color: #64748b;
  font-size: 13px;
}

/* 转办模式开关与提示 */
.cps-mode-switch {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.cps-mode-switch__label {
  color: #64748b;
  font-size: 13px;
  font-weight: 800;
}

.cps-mode-switch__label.is-on {
  color: var(--cps-teal-deep);
}

.cps-mode-hint {
  display: grid;
  gap: 2px;
  margin-bottom: 14px;
  border-left: 3px solid var(--cps-teal);
  border-radius: var(--cps-radius-sm);
  padding: 10px 12px;
  background: var(--cps-mist);
}

.cps-mode-hint strong {
  color: var(--cps-teal-deep);
  font-size: 14px;
  font-weight: 800;
}

.cps-mode-hint span {
  color: #64748b;
  font-size: 12px;
}

.cps-action-tip {
  margin: 12px 0 0;
  color: #94a3b8;
  font-size: 12px;
  text-align: center;
}

/* 动作按钮（内联） */
.cps-actions {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 12px;
}

.cps-actions__btn {
  height: 48px !important;
  border-radius: var(--cps-radius-lg) !important;
  font-size: 16px !important;
  font-weight: 700 !important;
  transition: transform 120ms ease, box-shadow 120ms ease;
}

.cps-actions__btn:not(:disabled):active {
  transform: translateY(1px);
}

.cps-actions__btn--success {
  background: var(--cps-success-deep) !important;
  border-color: var(--cps-success-deep) !important;
}

.cps-actions__btn--danger {
  background: #fee2e2 !important;
  border-color: #fecaca !important;
  color: var(--cps-danger-deep) !important;
}

.cps-actions__btn--danger:hover {
  background: #fecaca !important;
}

/* 流程时间线（内联） */
.cps-flow__dot {
  display: inline-block;
  width: 14px;
  height: 14px;
  border-radius: 999px;
  border: 3px solid #ccfbf1;
  background: #14b8a6;
}

.cps-flow__dot.is-last {
  border-color: #dbeafe;
  background: #2563eb;
}

.cps-flow__head {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}

.cps-flow__head strong {
  color: #0f172a;
  font-size: 15px;
  font-weight: 800;
}

.cps-flow__head time {
  color: #64748b;
  font-size: 13px;
}

.cps-flow__line {
  margin: 0;
  color: #64748b;
  font-size: 13px;
  line-height: 20px;
}

.cps-flow__comment {
  margin-top: 6px;
  padding: 8px 10px;
  border-radius: var(--cps-radius-sm);
  background: #fff;
  color: var(--cps-ink);
  font-size: 13px;
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
  border-radius: var(--cps-radius-md);
  background: var(--cps-cloud);
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

.cps-page-loading {
  display: flex;
  justify-content: center;
  margin-top: 64px;
}

@media (min-width: 1200px) {
  .cps-page {
    max-width: 1280px;
    padding: 20px 28px 56px;
  }
}

/* 竖屏/窄屏：把「当前操作」提到最前，现场处理不用滑到底 */
@media (max-width: 1199px) {
  .cps-detail-page .cps-col-action {
    order: -1;
  }
}
/* ===== antd 组件品牌色（只在本页作用域内生效） ===== */
.cps-detail-page :deep(.ant-btn-primary:not(:disabled)) {
  background: var(--cps-teal-deep);
  border-color: var(--cps-teal-deep);
  box-shadow: none;
}

.cps-detail-page :deep(.ant-btn-primary:not(:disabled):hover) {
  background: #115e59;
  border-color: #115e59;
}

.cps-detail-page :deep(.ant-switch.ant-switch-checked) {
  background: var(--cps-teal-deep);
}

.cps-detail-page :deep(.ant-radio-button-wrapper-checked:not(.ant-radio-button-wrapper-disabled)) {
  background: var(--cps-teal-deep);
  border-color: var(--cps-teal-deep);
  color: #fff;
}

.cps-detail-page :deep(.ant-radio-button-wrapper-checked:not(.ant-radio-button-wrapper-disabled)::before) {
  background-color: var(--cps-teal-deep);
}

.cps-detail-page :deep(.ant-radio-checked .ant-radio-inner) {
  border-color: var(--cps-teal-deep);
}

.cps-detail-page :deep(.ant-radio-checked .ant-radio-inner::after) {
  background-color: var(--cps-teal-deep);
}

.cps-detail-page :deep(.ant-input:focus),
.cps-detail-page :deep(.ant-input-focused),
.cps-detail-page :deep(.ant-input-affix-wrapper-focused) {
  border-color: var(--cps-teal);
  box-shadow: 0 0 0 2px rgba(20, 184, 166, 0.16);
}

.cps-detail-page :deep(.ant-spin-dot-item) {
  background-color: var(--cps-teal-deep);
}
</style>
