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
  empNo?: string
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
  empNo?: string
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

export type CpsRoomCheckJudgeResult = 'PASS' | 'FAIL' | 'TYPE_MISMATCH' | 'UNJUDGEABLE' | 'PENDING'
export type CpsRoomCheckRecordStatus = 'PENDING' | 'IN_PROGRESS' | 'JUDGED'

export interface CpsRoomCheckTask {
  taskId: number
  planId: number | null
  title: string
  taskStatus: string
  targetEmpNo: string | null
  scheduledAt: string | null
  frequency: string | null
  acceptanceCriteria?: string | null
  evidenceRequirement?: string | null
  roomCodes: string[]
  judgedRoomCount: number
}

export interface CpsRoomCheckItem {
  id: number
  checkItemId: number
  itemCode: string
  content: string
  photoCategory: string | null
  deductScore: number | null
  configVersion: number | null
  photoObjectKey: string | null
  photoUrl: string | null
  photoFileName: string | null
  judgeResult: CpsRoomCheckJudgeResult
  judgeReason: string | null
  finalResult: string | null
}

export interface CpsRoomCheckRecord {
  id: number
  planTaskId: number | null
  roomId: number
  roomCode: string
  roomName: string
  checkEmpNo: string
  recordStatus: CpsRoomCheckRecordStatus
  judgeStatus: string | null
  score: number | null
  startedAt: string | null
  submittedAt: string | null
  items: CpsRoomCheckItem[]
}

export interface CpsRoomInfo {
  id: number
  roomCode: string
  roomName: string | null
  building?: string | null
  doorNo?: string | null
  roomType?: string | null
  riskLevel?: string | null
  enabled?: boolean
}

// ===== B5② AI 初审三态/接管/裁决（PRD §28-§29）=====

export type CpsInitialReviewStateView =
  | 'none'
  | 'pending_dispatch'
  | 'running'
  | 'timeout_open'
  | 'failed'
  | 'taken_over'
  | 'late_result'
  | 'completed'

export interface CpsInitialReviewTaskView {
  id: number
  issueId: number
  submissionId: number | null
  versionNo: number | null
  status: string
  submittedAt: string | null
  timeoutAt: string | null
  completedAt: string | null
  takenOverBy: string | null
  takenOverName: string | null
  takenOverAt: string | null
  takeoverReason: string | null
  errorCode: string | null
  retryCount: number | null
}

export interface CpsInitialReviewResultView {
  id: number
  taskId: number
  overall: 'PASS' | 'PARTIAL' | 'PROBLEM'
  modelStatus: string | null
  isLate: boolean | null
  createdAt: string | null
}

export interface CpsInitialReviewItemView {
  id: number
  checkType: string
  fieldName: string | null
  verdict: string | null
  textLength: number | null
  punctuationCount: number | null
  ratioOk: boolean | null
  reason: string | null
  problemFragment: string | null
}

export interface CpsRectificationSubmissionView {
  id: number
  issueId: number
  versionNo: number | null
  reason: string | null
  shortTermMeasure: string | null
  longTermMeasure: string | null
  responsibleEmpNo: string | null
  responsibleEmpName: string | null
  submittedBy: string | null
  submittedAt: string | null
}

export interface CpsReviewAdjudicationView {
  id: number
  issueId: number
  versionNo: number | null
  reviewerEmpNo: string | null
  decision: 'APPROVE' | 'REJECT' | string
  aiOverall: string | null
  /** 与 AI 意见关系（CpsIssueService.aiRelation）：WITH_AI=同向，AGAINST_AI=反向，NO_AI_RESULT=无 AI 结果。 */
  aiRelation: string | null
  reason: string | null
  createdAt: string | null
}

export interface CpsInitialReviewEventView {
  id: number
  eventType: string
  detail: string | null
  operatorEmpNo: string | null
  createdAt: string | null
}

export interface CpsInitialReviewView {
  issue_id: number
  state_view: CpsInitialReviewStateView
  can_take_over: boolean
  seconds_until_takeover: number | null
  task: CpsInitialReviewTaskView | null
  result: CpsInitialReviewResultView | null
  items: CpsInitialReviewItemView[]
  submission: CpsRectificationSubmissionView | null
  adjudication: CpsReviewAdjudicationView | null
  events: CpsInitialReviewEventView[]
}

// ===== F3/F4 语音转写（D-03 三字段）=====

export type CpsSpeechField = 'reason' | 'short_term' | 'long_term'
export type CpsSpeechStatus = 'TRANSCRIBED' | 'SKIPPED' | 'UNAVAILABLE'

export interface CpsSpeechTranscriptionResult {
  status: CpsSpeechStatus
  text: string | null
  fallbackMessage: string | null
  field: CpsSpeechField
  attempt: number
  submissionId: string
  audioObjectKey: string | null
  durationSeconds: number | null
  model: string | null
}
