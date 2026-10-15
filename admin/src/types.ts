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

// ---- 波次 8 E 线：物品台账 / 出入库流水 / 库存预警 ----
export interface InventoryItem {
  id: number
  itemCode: string
  itemName: string
  unit?: string
  stockQty: number
  alertThreshold: number
  baseCode?: string
  factory?: string
  storageRoom?: string
  roomKeeperEmpNo?: string
  roomKeeperEmpName?: string
  remark?: string
  enabled: boolean
}
export interface InventoryTxn {
  id: number
  itemId: number
  itemCode?: string
  itemName?: string
  txnType: 'IN' | 'OUT' | 'ADJUST'
  qty: number
  beforeQty: number
  afterQty: number
  unit?: string
  operatorEmpNo?: string
  operatorName?: string
  remark?: string
  createdAt: string
}
export type InventoryAlertStatus = 'OPEN' | 'RESOLVED_AUTO' | 'RESOLVED_MANUAL' | 'IGNORED'
export interface InventoryAlertEvent {
  id: number
  itemId: number
  itemCode?: string
  itemName?: string
  status: InventoryAlertStatus
  firstTriggeredAt: string
  lastEvalAt: string
  lastEvalQty: number
  thresholdSnapshot: number
  closedAt?: string
  closedBy?: string
  closeReason?: string
}
/** 后端 CpsPageResponse：total + rows（与 PageResult 的 records 形态不同） */
export interface RowsPage<T> { total: number; rows: T[] }
