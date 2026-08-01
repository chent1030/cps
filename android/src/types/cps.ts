export type CpsIssueStatus =
  | 'PENDING_FEEDBACK'
  | 'PENDING_RECTIFY'
  | 'PENDING_UPLOAD_PROOF'
  | 'PENDING_REVIEW'
  | 'CLOSED'

export type CpsIssueAction =
  | 'REPLY_ASSIGN'
  | 'RECTIFY'
  | 'UPLOAD_PROOF'
  | 'REVIEW_CLOSE'
  | 'REVIEW_REJECT'
  | 'TRANSFER'

export type CpsIssueTab = 'todo' | 'created' | 'related' | 'closed'

export interface CpsOption {
  value: string | number
  label: string
}

export interface CpsAttachment {
  id: number
  fileUrl: string
  fileName: string
  fileType?: string
  sortNo?: number
}

export interface CpsUploadedImage {
  id: number
  url: string
  name: string
}

export interface CpsAiSuggestionPayload {
  sourceAttachmentId: number
  aiCategoryL1Id: number | null
  aiCategoryL1Name: string | null
  aiCategoryL2Id: number | null
  aiCategoryL2Name: string | null
  reasonSuggestion: string | null
  measureSuggestion: string | null
  modelName: string | null
  modelVersion: string | null
  rawRequest?: string | null
  rawResponse?: string | null
  confidence: string | number | null
}

export interface CpsIssueCreateRequest {
  factory: string
  area: string
  line: string
  process: string
  aiCategoryL1Id: number | null
  aiCategoryL2Id: number | null
  categoryL1Id: number
  categoryL2Id: number
  description: string
  feedbackEmpNo: string
  feedbackEmpName?: string
  issueAttachmentIds: number[]
  aiSuggestion?: CpsAiSuggestionPayload
}

export interface CpsIssueActionRequest {
  action: CpsIssueAction
  reasonAnalysis?: string
  correctiveMeasure?: string
  responsibleEmpNo?: string
  responsibleEmpName?: string
  proofEmpNo?: string
  proofEmpName?: string
  reviewerEmpNo?: string
  reviewerEmpName?: string
  rectifyRemark?: string
  reviewOpinion?: string
  proofAttachmentIds?: number[]
  targetEmpNo?: string
  targetEmpName?: string
  comment?: string
  categoryL1Id?: number
  categoryL2Id?: number
}

export interface CpsIssueListItem {
  id: number
  issueNo: string
  status: CpsIssueStatus
  factory?: string
  area?: string
  line?: string
  process?: string
  factoryName?: string
  areaName?: string
  lineName?: string
  processName?: string
  categoryL1Name?: string
  categoryL2Name?: string
  description: string
  currentHandlerEmpNo: string | null
  currentHandlerEmpName?: string | null
  submitTime: string
  overdue?: boolean
}

export interface CpsFlowLog {
  action: string
  operatorEmpNo: string
  fromStatus: CpsIssueStatus | null
  toStatus: CpsIssueStatus
  comment: string | null
  createdAt: string
}

export interface CpsIssueDetail extends CpsIssueListItem {
  creatorEmpNo: string
  feedbackEmpNo: string
  responsibleEmpNo: string | null
  responsibleEmpName?: string | null
  proofEmpNo: string | null
  proofEmpName?: string | null
  reviewerEmpNo: string | null
  reviewerEmpName?: string | null
  reasonAnalysis: string | null
  correctiveMeasure: string | null
  rectifyRemark: string | null
  reviewOpinion: string | null
  closeTime: string | null
  issueAttachments: CpsAttachment[]
  proofAttachments: CpsAttachment[]
  aiSuggestion: CpsAiSuggestionPayload | null
  availableActions: CpsIssueAction[]
  flowLogs: CpsFlowLog[]
}
