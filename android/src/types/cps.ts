export type CpsIssueStatus =
  | 'PENDING_FEEDBACK'
  | 'PENDING_RECTIFY'
  | 'PENDING_UPLOAD_PROOF'
  | 'PENDING_AI_REVIEW'
  | 'PENDING_REVIEWER_CONFIG'
  | 'PENDING_REVIEW'
  | 'CLOSED'

export type CpsIssueAction =
  | 'REPLY_ASSIGN'
  | 'RECTIFY'
  | 'UPLOAD_PROOF'
  | 'SUBMIT_RECTIFICATION'
  | 'SAVE_DRAFT'
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
  /** 绝对 URL（MinIO 直链）；历史数据可能是 /api/cps/attachments/{id}/content 代理路径。 */
  fileUrl: string
  fileName: string
  fileType?: string
  sortNo?: number
}

export interface CpsUploadedImage {
  id: number
  /** 上传接口返回的图片地址：绝对 URL 优先（MinIO）；旧后端为 /api 代理路径，前端按需补 base。 */
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
  empName?: string
  factory: string
  area: string
  line: string
  process: string
  /** 资源号：现场手动录入的物理资源标识（PRD 24.2.6），必填。 */
  resourceNo: string
  /** Phase B1：巡检类型编码（来自 /api/cps/inspection-types）。选填，留空走老路径。 */
  inspectionTypeCode?: string
  /** Phase B1：动态字段值 JSON 字符串（与所选 inspectionType.formSchemaJson 对应）。 */
  extraData?: string
  /** Phase B2：服务端草稿 ID，提交成功后由后端归档。可空。 */
  draftId?: number
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

/** Phase B1：巡检类型 form_schema 中单个字段定义。 */
export interface CpsInspectionFormField {
  key: string
  label: string
  type: 'input' | 'number' | 'textarea' | 'select' | 'date' | 'switch'
  required?: boolean
  maxLength?: number
  placeholder?: string
  defaultValue?: string | number | boolean
  helpText?: string
  options?: CpsOption[]
}

export interface CpsInspectionFormSchema {
  fields: CpsInspectionFormField[]
}

export interface CpsInspectionType {
  id: number
  code: string
  name: string
  /** 巡检内容（Phase C1） */
  content?: string | null
  /** 是否分区域：1=按区域执行，0=点检项直挂类型（Phase C1） */
  zoneFlag?: number | null
  /** 内置类型：1=系统固定（CPS巡检），不可删除/停用（Phase D2） */
  builtin?: number | null
  /** 不分区域时直挂点检项编码（逗号分隔）（Phase C1） */
  checkItemIds?: string | null
  description?: string
  icon?: string
  color?: string
  enabled?: number
  sortOrder?: number
  /** 【已废弃 Phase C1】旧动态表单 schema */
  formSchemaJson?: string
  defaultAssigneeEmpNo?: string
  remark?: string
  // 解析后的 schema 缓存（v1: formSchemaJson → CpsInspectionFormSchema）
  _parsedSchema?: CpsInspectionFormSchema
}

// ===== Phase C1：统一巡检任务（区域 / 记录 / 明细）=====

/** 点检项定义（Phase D2：新建问题页按类型配置联动渲染）。 */
export interface CpsCheckItemInfo {
  id: number
  itemCode: string
  content: string
  /** 点检类型多选（逗号分隔）：PASS_FAIL/TEXT/PHOTO */
  checkTypes: string
  photoMinCount?: number | null
  /** 拍照判定条件（自由文本） */
  photoCriteria?: string | null
  textHint?: string | null
  deductScore?: number
}

/** 新建问题时按点检项填写的结果（存入 extraData.checkResults）。 */
export interface CpsIssueCheckResult {
  itemCode: string
  content: string
  passResult?: 'PASS' | 'FAIL' | null
  textValue?: string | null
  photoUrls?: string[]
  photoNames?: string[]
  photoCount?: number
}

export interface CpsInspectionArea {
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
  checkItemIds?: string | null
  enabled?: boolean
}

/**
 * 巡检记录状态。
 * 非 CPS 闭环：IN_PROGRESS（执行中）→ PENDING_REVIEW（待复核）
 *   → PENDING_RECTIFY（待整改）→ JUDGED（已结束）；复核退回则回到 IN_PROGRESS。
 * PENDING/AWAIT_CONFIRM 为历史数据兼容值。
 */
export type CpsInspectionRecordStatus =
  | 'PENDING'
  | 'IN_PROGRESS'
  | 'PENDING_REVIEW'
  | 'PENDING_RECTIFY'
  | 'AWAIT_CONFIRM'
  | 'JUDGED'

export interface CpsInspectionRecord {
  id: number
  typeCode: string
  areaId?: number | null
  areaCode?: string | null
  areaName?: string | null
  planTaskId?: number | null
  planId?: number | null
  checkEmpNo: string
  checkEmpName?: string | null
  recordStatus: CpsInspectionRecordStatus | string
  judgeStatus?: string | null
  score?: number | null
  /** AI 初审结论：PASS/PARTIAL/PROBLEM/NONE（提交时写入） */
  aiOverall?: string | null
  aiScore?: number | null
  aiReason?: string | null
  aiJudgedAt?: string | null
  /** 提交-复核轮数（复核退回后 +1） */
  reviewRound?: number | null
  reviewEmpNo?: string | null
  reviewEmpName?: string | null
  /** web 端人工复核意见 */
  reviewOpinion?: string | null
  reviewedAt?: string | null
  /** 整改责任人（分区域取区域责任人） */
  rectifyEmpNo?: string | null
  rectifyEmpName?: string | null
  rectifyReason?: string | null
  rectifyShortTerm?: string | null
  rectifyLongTerm?: string | null
  /** 整改照片附件 id，逗号分隔 */
  rectifyAttachmentIds?: string | null
  rectifySubmittedAt?: string | null
  rectifyAiOverall?: string | null
  rectifyAiReason?: string | null
  currentHandlerEmpNo?: string | null
  currentHandlerEmpName?: string | null
  sourceRecordId?: number | null
  startedAt?: string | null
  submittedAt?: string | null
  createdAt?: string | null
  updatedAt?: string | null
}

/** 巡检记录流转日志（START/SUBMIT/REVIEW_PASS/REVIEW_RETURN/RECTIFY_SUBMIT）。 */
export interface CpsInspectionRecordLog {
  id: number
  recordId: number
  action: string
  fromStatus?: string | null
  toStatus?: string | null
  operatorEmpNo?: string | null
  operatorEmpName?: string | null
  comment?: string | null
  createdAt?: string | null
}

export interface CpsInspectionRecordItem {
  id: number
  recordId: number
  checkItemId: number
  itemCode: string
  content: string
  /** 点检类型快照（逗号分隔）：PASS_FAIL/TEXT/PHOTO */
  checkTypes: string
  photoMinCount?: number | null
  photoCriteria?: string | null
  deductScore: number
  passResult?: 'PASS' | 'FAIL' | string | null
  textValue?: string | null
  /** 点检照片：逗号分隔的绝对 URL（MinIO）；历史数据可能是 /api 代理路径或纯数字附件 ID。 */
  photoUrl?: string | null
  photoFileName?: string | null
  photoCount?: number
  judgeResult?: string | null
  judgeReason?: string | null
  finalResult?: string | null
}

export interface CpsInspectionRecordDetail {
  record: CpsInspectionRecord
  items: CpsInspectionRecordItem[]
  logs?: CpsInspectionRecordLog[]
  /** 整改照片附件（后端按 rectifyAttachmentIds 回查） */
  rectifyAttachments?: CpsAttachment[]
}

/**
 * 我的分配巡检任务（GET /api/cps/inspection-tasks/my-tasks）。
 * 来源 cps_inspection_plan_task 中 target_emp_no=当前用户且 task_status 为 PENDING/IN_PROGRESS 的任务，
 * 供 Android「我的任务」列表渲染与一键开始执行。
 */
export interface CpsMyInspectionTask {
  taskId: number
  planId?: number | null
  planTitle?: string | null
  typeCode: string
  /** 巡检类型名称；历史枚举类型（INSPECT_*）不在类型表时后端回退为编码 */
  typeName?: string | null
  /** 1=分区域（需先选区域开始），0=点检项直挂类型 */
  zoneFlag?: number | null
  title?: string | null
  targetEmpNo?: string | null
  targetEmpName?: string | null
  scheduledAt?: string | null
  frequency?: string | null
  acceptanceCriteria?: string | null
  evidenceRequirement?: string | null
  /** 覆盖区域编码，逗号分隔（仅分区域任务） */
  coverageAreaCodes?: string | null
  /** PENDING / IN_PROGRESS */
  taskStatus?: string | null
}

/**
 * 页面内照片条目：选图后立刻用本地临时路径渲染，上传成功只回填 id/url，不覆盖 localPath。
 * - localPath：uni.chooseImage 返回的 tempFilePaths（或 H5 blob URL），只在当前页面生命周期有效
 * - url：上传成功后的服务端地址（MinIO 绝对地址）或历史附件的代理地址，作为刷新/回显兜底
 * - failed：上传失败，保留本地图并允许重试/删除
 */
export interface CpsPhotoEntry {
  /** 页面内唯一键（本地图片在上传完成前没有附件 id） */
  uid?: string
  id: number | null
  localPath: string
  url: string
  name: string
  uploading?: boolean
  failed?: boolean
  /** 重试上传用的原始源（H5 File 或 App 临时路径），不参与渲染 */
  source?: File | string
}

/** 非 CPS 巡检整改提交（原因分析 / 短期措施 / 长期措施 + 整改照片附件 ID）。 */
export interface CpsInspectionRectifyPayload {
  reason: string
  shortTermMeasure: string
  longTermMeasure: string
  attachmentIds: number[]
  empNo?: string
  empName?: string
}

export interface CpsIssueActionRequest {
  empNo?: string
  empName?: string
  action: CpsIssueAction
  reasonAnalysis?: string
  correctiveMeasure?: string
  /** v2 整改：短期措施 / 长期措施 */
  shortTermMeasure?: string
  longTermMeasure?: string
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

/** 单个巡检类型的条数（GET /api/cps/issues/type-summary）。 */
export interface CpsIssueTypeCount {
  code: string
  name?: string | null
  count: number
}

/** 当前页签下的巡检类型聚合结果。 */
export interface CpsIssueTypeSummary {
  total: number
  types: CpsIssueTypeCount[]
}

export interface CpsIssueListItem {
  id: number
  status: CpsIssueStatus
  factory?: string
  area?: string
  line?: string
  process?: string
  /** 资源号：现场手动录入的物理资源标识（PRD 24.2.6）。 */
  resourceNo?: string | null
  /** Phase B1：巡检类型编码。 */
  inspectionTypeCode?: string | null
  /** Phase E：巡检类型中文名（后端 LEFT JOIN cps_inspection_type 带出）。 */
  inspectionTypeName?: string | null
  /** Phase B1：动态字段 JSON 字符串。 */
  extraData?: string | null
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
  /** 流程版本：v2 = 提交整改→AI初审→人工审核（无独立待传图节点） */
  flowVersion?: string | null
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
  shortTermMeasure?: string | null
  longTermMeasure?: string | null
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
export type CpsRoomCheckRecordStatus = 'PENDING' | 'IN_PROGRESS' | 'AWAIT_CONFIRM' | 'JUDGED'

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
  /** 点检照片：绝对 URL（MinIO 直链）；历史数据可能是 /api 代理路径。 */
  photoUrl: string | null
  photoFileName: string | null
  judgeResult: CpsRoomCheckJudgeResult
  judgeReason: string | null
  finalResult: string | null
  finalModifiedBy?: string | null
  finalModifiedAt?: string | null
  finalModifyReason?: string | null
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

// ===== Phase B2：服务端草稿（防止一次填不完）=====

/** 草稿表单快照：保存到服务端草稿接口（saveCpsIssueDraft / getCpsIssueDraft）的序列化格式。 */
export interface CpsIssueDraftData {
  location: { factory: string; area: string; line: string; process: string }
  description: string
  resourceNo: string
  inspectionTypeCode: string | null
  extraData: Record<string, unknown>
  feedbackEmpNo: string
  feedbackPerson: { empNo: string; empName?: string | null } | null
  images: CpsUploadedImage[]
  imagePreviewSources: Record<number, string>
  aiSuggestion: CpsAiSuggestionPayload | null
}

export interface CpsIssueDraft {
  id: number
  creatorEmpNo: string
  creatorEmpName?: string | null
  title?: string | null
  inspectionTypeCode?: string | null
  factory?: string | null
  area?: string | null
  /** 完整表单快照 JSON 字符串（CpsIssueDraftData 序列化） */
  draftData: string
  status: 'ACTIVE' | 'SUBMITTED' | 'DISCARDED' | string
  submittedIssueId?: number | null
  createdAt?: string
  updatedAt?: string
}

export interface CpsIssueDraftSaveRequest {
  /** null=新建，非空=覆盖更新 */
  id?: number | null
  empNo?: string
  empName?: string
  title?: string
  inspectionTypeCode?: string | null
  factory?: string | null
  area?: string | null
  draftData: string
}
