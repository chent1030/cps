export type IssueStatus = 'PENDING_FEEDBACK' | 'PENDING_RECTIFY' | 'PENDING_UPLOAD_PROOF' | 'PENDING_REVIEW' | 'CLOSED'

export interface Issue {
  id: number
  status: IssueStatus
  factory: string
  area: string
  line: string
  process: string
  resourceNo?: string
  inspectionTypeCode?: string
  extraData?: string
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
export interface AdminOperator {
  empNo: string
  empName: string
  /** true 表示超管（pagePermissions=["*"]），可访问账号管理与审计日志 */
  superAdmin?: boolean
  /** 强制修改密码：true=登录后必须先改密码 */
  mustChangePassword?: boolean
  /** 允许访问的页面 key 数组（* 表示所有） */
  pagePermissions?: string[]
}

/** Phase A1：管理端账号 DTO（cps_admin_user）。 */
export interface AdminUser {
  id: number
  empNo: string
  empName: string
  status: 'ACTIVE' | 'LOCKED'
  mustChangePassword?: boolean
  superAdmin?: boolean
  pagePermissions?: string[]
  remark?: string
  lastLoginAt?: string
  createdAt?: string
  updatedAt?: string
}
export interface AdminUserCreate {
  empNo: string
  empName: string
  pagePermissions: string[]
  remark?: string
}
export interface AdminUserUpdate {
  empName?: string
  pagePermissions?: string[]
  remark?: string
}
export interface AdminUserResetPasswordResult {
  newPassword: string
  message: string
}

/** Phase A1：审计日志 DTO（cps_admin_audit_log）。 */
export interface AdminAuditLog {
  id: number
  empNo: string
  empName?: string
  action: string
  targetType?: string
  targetId?: string
  detail?: string
  ip?: string
  userAgent?: string
  status: 'SUCCESS' | 'FAILED'
  errorMessage?: string
  createdAt: string
}
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

// ---- 巡检计划（CpsInspectionPlanController /api/cps/inspection-plans）----
export type InspectionPlanStatus = 'PENDING_REVIEW' | 'APPROVED' | 'REJECTED'
export type InspectionPlanTaskType = 'INSPECT_RECTIFY' | 'INSPECT_PATROL' | 'INSPECT_CHECK'
export interface InspectionPlanTask {
  id: number
  taskType: InspectionPlanTaskType | string
  title?: string
  targetEmpNo?: string
  targetEmpName?: string
  scheduledAt?: string
  frequency?: string
  acceptanceCriteria?: string
  evidenceRequirement?: string
  /** 分区域类型任务的覆盖区域编码（逗号分隔，Phase D）；空=全部启用区域 */
  coverageAreaCodes?: string | null
  taskStatus?: string
  referenceIssueId?: number
  referenceObjectKey?: string
  referenceObjectType?: string
  createdAt?: string
}
export interface InspectionPlan {
  id: number
  sourceRunId?: string
  reportId?: string
  planType?: string
  title?: string
  targetFactory?: string
  targetArea?: string
  riskBasis?: string
  draftContentJson?: string
  status: InspectionPlanStatus | string
  configVersion?: number
  lockVersion?: number
  draftBy?: string
  approver?: string
  approverName?: string
  approvedAt?: string
  rejectReason?: string
  createdAt?: string
  updatedAt?: string
  tasks: InspectionPlanTask[]
}
/** POST /{id}/approve 返回体 */
export interface InspectionPlanApproveResult {
  plan: InspectionPlan
  createdTasks: InspectionPlanTask[]
  operator: string
}
/** GET /{id}/record-status 返回体（CpsPlanRecordStatusResponse） */
export interface InspectionPlanRecordStatus {
  planId: number
  planTitle?: string
  planStatus?: string
  approvedAt?: string
  perType: {
    taskType: string
    /** 选择性建单：该类型是否在草稿勾选的建单范围内（false=不建单、不计缺失） */
    requested?: boolean
    taskExists: boolean
    taskId?: number
    taskStatus?: string
    lastBuildResult?: string | null
    lastBuildErrorMsg?: string | null
    lastBuildAt?: string
    lastBuildSource?: string
  }[]
  summary?: { typesCreated: number; typesFailed: number; typesMissing: number; allBuilt?: boolean }
}
/** POST /{id}/rebuild-tasks 返回体 */
export interface InspectionPlanRebuildResult {
  createdTasks: InspectionPlanTask[]
  recordStatus: InspectionPlanRecordStatus
  operator: string
}

// ---- 辅房 / 点检项配置（CpsRoomController / CpsCheckItemController /api/cps/admin，B1/B2）----
export interface Room {
  id: number
  roomCode: string
  building?: string
  doorNo?: string
  roomName?: string
  roomType?: string
  riskLevel?: string
  /** 房间独立配置的点检项编号（逗号分隔 itemCode）；空=按房型匹配 */
  checkItemIds?: string
  deptName?: string
  baseCode?: string
  factory?: string
  keeperEmpNo?: string
  keeperEmpName?: string
  keeperManagerEmpNo?: string
  keeperManagerEmpName?: string
  enabled: boolean
  createdBy?: string
  updatedBy?: string
  createdAt?: string
  updatedAt?: string
}
export interface CheckItem {
  id: number
  itemCode: string
  content?: string
  /** 点检类型多选（逗号分隔）：PASS_FAIL是否合格/TEXT文本框/PHOTO需拍照（Phase C1） */
  checkTypes?: string
  /** 需拍照时最低张数（Phase C1） */
  photoMinCount?: number | null
  /** 拍照判定条件（自由文本）（Phase C1） */
  photoCriteria?: string | null
  /** 文本框提示语（Phase C1） */
  textHint?: string | null
  photoCategory?: string
  deductScore?: number
  status?: string
  /** 逗号分隔房型；空=适用全部房型 */
  applicableRoomTypes?: string
  configVersion?: number
  enabled: boolean
  createdBy?: string
  updatedBy?: string
  createdAt?: string
  updatedAt?: string
}

// ---- 通用字典（双表：cps_dict_type + cps_dict，/api/cps/admin/dict-types 与 /dicts）----
/** 字典类型（cps_dict_type）：一组选项的容器，如 房型/风险等级/照片分类 */
export interface DictType {
  id: number
  /** 类型编码（大写蛇形，如 ROOM_TYPE），内置类型不可改码/不可停/不可删 */
  typeCode: string
  /** 类型中文名（如 房型） */
  typeName: string
  description?: string
  builtin?: boolean
  enabled: boolean
  sortNo?: number
  createdBy?: string
  updatedBy?: string
  createdAt?: string
  updatedAt?: string
}
/** 字典条目（cps_dict，经 join 带出 typeCode/typeName 展示字段） */
export interface DictEntry {
  id: number
  /** 所属类型 id（cps_dict_type.id，条目不可换类型） */
  typeId: number
  /** 所属类型编码（join 展示字段，如 ROOM_TYPE） */
  typeCode: string
  /** 所属类型中文名（join 展示字段，如 房型） */
  typeName: string
  /** 存库值（如 配电房 / HIGH / DAILY） */
  itemValue: string
  /** 显示名（如 高风险 / 每日） */
  itemLabel: string
  sortNo?: number
  enabled: boolean
  /** 内置保护：不可删/不可停/不可改类型与值（如 RISK_LEVEL 三项） */
  builtin?: boolean
  remark?: string
  createdBy?: string
  updatedBy?: string
  createdAt?: string
  updatedAt?: string
}

/** B：管理台辅房点检记录列表行（PRD §22 追溯）。 */
export interface RoomCheckRecordRow {
  id: number
  roomCode: string
  roomName: string
  checkEmpNo: string
  checkEmpName: string | null
  recordStatus: 'PENDING' | 'IN_PROGRESS' | 'AWAIT_CONFIRM' | 'JUDGED'
  judgeStatus: string | null
  judgeAttempt: number | null
  score: number | null
  submittedAt: string | null
  itemCount: number
  failCount: number
  modifiedCount: number
}

/** 点检明细（含 AI 判定、最终结果与修正留痕，PRD 24.2.5）。 */
export interface RoomCheckRecordItemDetail {
  id: number
  itemCode: string
  content: string
  photoCategory: string | null
  deductScore: number | null
  photoUrl: string | null
  photoFileName: string | null
  judgeResult: string | null
  judgeReason: string | null
  finalResult: string | null
  finalModifiedBy: string | null
  finalModifiedAt: string | null
  finalModifyReason: string | null
}

export interface RoomCheckRecordDetail {
  id: number
  planTaskId: number | null
  roomCode: string
  roomName: string
  checkEmpNo: string
  recordStatus: string
  judgeStatus: string | null
  score: number | null
  submittedAt: string | null
  items: RoomCheckRecordItemDetail[]
}

export interface RoomCheckRejudgeResult {
  recordId: number
  attempt: number
  judgeStatus: string
  score: number | null
  retakeRequired: string[]
  results: { itemCode: string; outcome: string; reason: string | null }[]
}

/** 后端 CpsPageResponse（total + rows）。 */
export interface TotalRows<T> {
  total: number
  rows: T[]
}

// ============================================================
// Phase A2 通用审批中心
// ============================================================
export type ApprovalInstanceStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'WITHDRAWN'
export type ApprovalAction = 'SUBMIT' | 'APPROVE' | 'REJECT' | 'TRANSFER' | 'WITHDRAW'

export const APPROVAL_INSTANCE_STATUS_LABELS: Record<ApprovalInstanceStatus, string> = {
  PENDING: '待审批',
  APPROVED: '已批准',
  REJECTED: '已驳回',
  WITHDRAWN: '已撤回',
}

export const APPROVAL_INSTANCE_STATUS_COLORS: Record<ApprovalInstanceStatus, string> = {
  PENDING: 'arcoblue',
  APPROVED: 'green',
  REJECTED: 'red',
  WITHDRAWN: 'gray',
}

export const APPROVAL_BIZ_TYPE_LABELS: Record<string, string> = {
  INSPECTION_PLAN: '巡检计划',
  ISSUE_RECTIFY: '问题整改',
  ROOM_CHECK_RECTIFY: '点检整改',
}

export const APPROVAL_ACTION_LABELS: Record<ApprovalAction, string> = {
  SUBMIT: '提交',
  APPROVE: '批准',
  REJECT: '驳回',
  TRANSFER: '转交',
  WITHDRAW: '撤回',
}

export const APPROVAL_ACTION_COLORS: Record<ApprovalAction, string> = {
  SUBMIT: 'gray',
  APPROVE: 'green',
  REJECT: 'red',
  TRANSFER: 'orange',
  WITHDRAW: 'gray',
}

export interface ApprovalInstance {
  id: number
  bizType: string
  bizId: number
  bizTitle: string | null
  bizCreatorEmpNo: string | null
  bizCreatorEmpName: string | null
  bizFactory: string | null
  bizArea: string | null
  status: ApprovalInstanceStatus
  currentApproverEmpNo: string | null
  currentApproverEmpName: string | null
  lastActionAt: string | null
  approvedAt: string | null
  rejectedAt: string | null
  rejectReason: string | null
  bizSnapshot: string | null
  stepCount?: number
  createdAt: string | null
  updatedAt: string | null
}

export interface ApprovalStep {
  id: number
  instanceId: number
  seq: number
  actorEmpNo: string
  actorEmpName: string | null
  action: ApprovalAction
  comment: string | null
  fromStatus: ApprovalInstanceStatus | null
  toStatus: ApprovalInstanceStatus | null
  createdAt: string | null
}

export interface ApprovalDetail {
  instance: ApprovalInstance
  steps: ApprovalStep[]
}

/** 【已废弃 Phase C1】旧动态表单字段定义（历史兼容保留）。 */
export interface InspectionFormField {
  key: string
  label: string
  type: 'input' | 'number' | 'textarea' | 'select' | 'date' | 'switch'
  required?: boolean
  maxLength?: number
  placeholder?: string
  defaultValue?: string | number | boolean
  helpText?: string
  options?: { label: string; value: string | number }[]
}

export interface InspectionFormSchema {
  fields: InspectionFormField[]
}

/** 巡检类型（Phase C1 统一模型）：编码/名称/巡检内容/是否分区域。 */
export interface InspectionType {
  id: number
  code: string
  name: string
  /** 巡检内容（做什么、怎么做） */
  content?: string | null
  /** 是否分区域：1=按区域执行，0=点检项直挂类型 */
  zoneFlag?: number | null
  /** 内置类型：1=系统固定（CPS巡检），不可删除/停用/改编码（Phase D2） */
  builtin?: number | null
  /** 不分区域时直挂点检项编码（逗号分隔） */
  checkItemIds?: string | null
  description?: string
  icon?: string
  color?: string
  enabled: number | boolean
  sortOrder: number
  /** 【已废弃】旧动态表单 schema */
  formSchemaJson?: string | null
  defaultAssigneeEmpNo?: string
  remark?: string
  createdAt?: string
  updatedAt?: string
}

export interface InspectionTypeRequest {
  code: string
  name: string
  content?: string
  zoneFlag?: number
  checkItemIds?: string | null
  description?: string
  icon?: string
  color?: string
  enabled?: number
  sortOrder?: number
  formSchemaJson?: string
  defaultAssigneeEmpNo?: string
  remark?: string
}

/** 巡检区域（Phase C1，泛化辅房）。 */
export interface InspectionArea {
  id: number
  areaCode: string
  typeCode: string
  areaName: string
  factory?: string | null
  area?: string | null
  /** 拉线|房型 */
  lineOrRoomType?: string | null
  process?: string | null
  building?: string | null
  doorNo?: string | null
  riskLevel: string
  keeperEmpNo: string
  keeperEmpName?: string | null
  /** 点检项编码（逗号分隔）；空=按适用范围自动匹配 */
  checkItemIds?: string | null
  remark?: string | null
  enabled: boolean
  sourceRoomId?: number | null
  createdBy?: string
  updatedBy?: string
  createdAt?: string
  updatedAt?: string
}

export interface InspectionAreaRequest {
  areaCode: string
  typeCode: string
  areaName: string
  factory?: string
  area?: string
  lineOrRoomType?: string
  process?: string
  building?: string
  doorNo?: string
  riskLevel?: string
  keeperEmpNo: string
  keeperEmpName?: string
  checkItemIds?: string | null
  remark?: string
  enabled?: boolean
  operatorEmpNo?: string
}

/** 统一巡检记录（Phase C1）。 */
export interface InspectionRecord {
  id: number
  typeCode: string
  areaId?: number | null
  areaCode?: string | null
  areaName?: string | null
  planTaskId?: number | null
  planId?: number | null
  checkEmpNo: string
  checkEmpName?: string | null
  recordStatus: 'PENDING' | 'IN_PROGRESS' | 'AWAIT_CONFIRM' | 'PENDING_REVIEW' | 'PENDING_RECTIFY' | 'JUDGED' | string
  judgeStatus?: string | null
  score?: number | null
  startedAt?: string | null
  submittedAt?: string | null
  sourceRecordId?: number | null
  createdAt?: string
  updatedAt?: string
  // ---- 非 CPS 巡检：AI 初审 / 人工复核 / 整改闭环（V20261028）----
  aiOverall?: 'PASS' | 'PARTIAL' | 'PROBLEM' | 'NONE' | string | null
  aiScore?: number | null
  aiReason?: string | null
  aiJudgedAt?: string | null
  reviewRound?: number | null
  reviewEmpNo?: string | null
  reviewEmpName?: string | null
  reviewOpinion?: string | null
  reviewedAt?: string | null
  rectifyEmpNo?: string | null
  rectifyEmpName?: string | null
  rectifyReason?: string | null
  rectifyShortTerm?: string | null
  rectifyLongTerm?: string | null
  rectifyAttachmentIds?: string | null
  rectifySubmittedAt?: string | null
  rectifyAiOverall?: string | null
  rectifyAiReason?: string | null
  currentHandlerEmpNo?: string | null
  currentHandlerEmpName?: string | null
}

export interface InspectionRecordItem {
  id: number
  recordId: number
  checkItemId: number
  itemCode: string
  content: string
  checkTypes: string
  photoMinCount?: number | null
  photoCriteria?: string | null
  deductScore: number
  passResult?: 'PASS' | 'FAIL' | string | null
  textValue?: string | null
  photoObjectKey?: string | null
  photoFileName?: string | null
  photoCount?: number
  judgeResult?: string | null
  judgeReason?: string | null
  finalResult?: string | null
  finalModifiedBy?: string | null
  finalModifiedAt?: string | null
  finalModifyReason?: string | null
  judgedAt?: string | null
}

export interface InspectionRecordDetail {
  record: InspectionRecord
  items: InspectionRecordItem[]
}

/** 记录流转日志（CpsInspectionRecordLog），用于 web 复核页时间线。 */
export interface InspectionRecordLog {
  id: number
  recordId: number
  action: 'START' | 'SUBMIT' | 'REVIEW_PASS' | 'REVIEW_RETURN' | 'RECTIFY_SUBMIT' | string
  fromStatus?: string | null
  toStatus?: string | null
  operatorEmpNo?: string | null
  operatorEmpName?: string | null
  comment?: string | null
  createdAt?: string | null
}

/** 整改照片（cps_issue_attachment），图片地址 /api/cps/attachments/{id}/content。 */
export interface InspectionRectifyAttachment {
  id: number
  fileUrl?: string | null
  fileName?: string | null
  fileType?: string | null
}

/** GET /api/cps/inspection-tasks/{id} 返回体。 */
export interface InspectionReviewDetail {
  record: InspectionRecord
  items: InspectionRecordItem[]
  logs: InspectionRecordLog[]
  rectifyAttachments: InspectionRectifyAttachment[]
}
