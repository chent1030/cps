export type IssueStatus = 'PENDING_FEEDBACK' | 'PENDING_RECTIFY' | 'PENDING_UPLOAD_PROOF' | 'PENDING_REVIEW' | 'CLOSED'

export interface Issue {
  id: number
  status: IssueStatus
  factory: string
  area: string
  line: string
  process: string
  description: string
  currentHandlerEmpNo?: string
  currentHandlerEmpName?: string
  submitTime: string
  overdue: boolean
  aiCategoryL1Name?: string; aiCategoryL2Name?: string; categoryL1Name?: string; categoryL2Name?: string
  creatorEmpNo?: string; creatorEmpName?: string; feedbackEmpNo?: string; feedbackEmpName?: string; allFlowHandlers?: string
  issueImageIds?: string; proofImageIds?: string; reasonAnalysis?: string; correctiveMeasure?: string; rectifyRemark?: string; reviewOpinion?: string; updatedAt?: string
}

export interface PageResult<T> { records: T[]; total: number; page: number; pageSize: number }
export interface Overview {
  openIssueCount: number
  pendingFeedbackCount: number
  pendingRectifyCount: number
  pendingReviewCount: number
  overdueCount: number
  closedThisMonthCount: number
  recentIssues: Issue[]
}

export interface Category { id: number; categoryName: string; parentId?: number; categoryLevel?: number; enabled: boolean; sortNo?: number }
export interface AreaPersonConfig {
  id: number; factory: string; area: string; line: string; process: string
  empNo: string; empName?: string; enabled: boolean; createdBy?: string; createdName?: string; updatedBy?: string; updatedName?: string
}
export interface AdminOperator { empNo: string; empName: string }
export interface KnowledgeCase {
  id: number; categoryL1Id?: number; categoryL2Id?: number; categoryL1Name?: string; categoryL2Name?: string; enabled: boolean; imageUrl?: string; reason?: string; measure?: string; milvusVectorId?: string; vectorRetryCount?: number; vectorUpdatedAt?: string; vectorStatus?: string; vectorErrorMsg?: string
}
export interface KnowledgeMaterial { id: number; caseId: number; fileUrl: string; fileName?: string; reason: string; measure: string; milvusVectorId?: string; vectorRetryCount?: number; vectorUpdatedAt?: string; vectorStatus?: string; vectorErrorMsg?: string }

export interface AgentRuntime {
  enabled: boolean
  state: 'ONLINE' | 'UNAVAILABLE' | 'DISABLED'
  message: string
  baseUrl: string
  tenantId: string
  metrics?: Record<string, unknown>
}
