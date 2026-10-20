import type { AdminOperator, AdminAuditLog, AdminUser, AdminUserCreate, AdminUserResetPasswordResult, AdminUserUpdate, AgentRuntime, ApprovalAction, ApprovalDetail, ApprovalInstance, AreaPersonConfig, Category, CheckItem, DictEntry, DictType, InspectionArea, InspectionAreaRequest, InspectionPlan, InspectionPlanApproveResult, InspectionPlanRecordStatus, InspectionPlanRebuildResult, InspectionRecord, InspectionRecordDetail, InspectionType, InspectionTypeRequest, InventoryAlertEvent, InventoryItem, InventoryTxn, Issue, KnowledgeCase, KnowledgeMaterial, Overview, PageResult, Room, RoomCheckRecordDetail, RoomCheckRecordRow, RoomCheckRejudgeResult, RowsPage, TotalRows } from './types'

const baseUrl = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '')

export class ApiError extends Error {
  public readonly status: number
  constructor(status: number, message: string) { super(message); this.status = status }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const isFormData = init?.body instanceof FormData
  const response = await fetch(`${baseUrl}${path}`, {
    ...init,
    credentials: 'include',
    headers: { Accept: 'application/json', ...(init?.body && !isFormData ? { 'Content-Type': 'application/json' } : {}), ...init?.headers },
  })
  if (response.status === 401) {
    const ssoLoginUrl = import.meta.env.VITE_SSO_LOGIN_URL
    if (ssoLoginUrl) window.location.assign(ssoLoginUrl)
    throw new ApiError(401, '登录已失效，请重新登录')
  }
  if (!response.ok) {
    const text = await response.text()
    throw new ApiError(response.status, text || `请求失败 (${response.status})`)
  }
  // Spring 的 void Controller 可能返回空的 200，而不仅仅是 204。
  // 空响应不应再按 JSON 解析，否则调用方会误判操作失败并跳过后续刷新。
  const body = await response.text()
  if (!body.trim()) return undefined as T
  return JSON.parse(body) as T
}

const query = (params: Record<string, string | number | boolean | undefined>) => {
  const values = Object.entries(params).filter(([, value]) => value !== undefined && value !== '')
  return values.length ? `?${new URLSearchParams(values.map(([key, value]) => [key, String(value)]))}` : ''
}

const operatorStorageKey = 'cps-admin-operator'
export function getAdminOperator(): AdminOperator | null {
  try { return JSON.parse(localStorage.getItem(operatorStorageKey) || 'null') as AdminOperator | null } catch { return null }
}
export function setAdminOperator(operator: AdminOperator | null) {
  if (operator) localStorage.setItem(operatorStorageKey, JSON.stringify(operator))
  else localStorage.removeItem(operatorStorageKey)
}

export const api = {
  adminLogin: (body: { empNo: string; password: string }) => request<AdminOperator>('/cps/admin/master/auth/login', { method: 'POST', body: JSON.stringify(body) }),
  adminLogout: (body: { empNo: string; empName: string }) => request<{ ok: boolean }>('/cps/admin/master/auth/logout', { method: 'POST', body: JSON.stringify(body) }).catch(() => ({ ok: true })),
  // ---- Phase A1：账号管理（超管）----
  usersPage: (params: Record<string, string | number | undefined>) => request<PageResult<AdminUser>>(`/cps/admin/users/page${query(params)}`),
  getUser: (id: number) => request<AdminUser>(`/cps/admin/users/${id}`),
  createUser: (body: AdminUserCreate) => request<{ user: AdminUser; message: string }>('/cps/admin/users', { method: 'POST', body: JSON.stringify(body) }),
  updateUser: (id: number, body: AdminUserUpdate) => request<AdminUser>(`/cps/admin/users/${id}`, { method: 'PATCH', body: JSON.stringify(body) }),
  setUserStatus: (id: number, enabled: boolean) => {
    const operator = getAdminOperator()
    return request<void>(`/cps/admin/users/${id}/status`, { method: 'PATCH', body: JSON.stringify({ enabled, empNo: operator?.empNo }) })
  },
  resetUserPassword: (id: number) => request<AdminUserResetPasswordResult>(`/cps/admin/users/${id}/reset-password`, { method: 'POST', body: JSON.stringify({ currentEmpNo: getAdminOperator()?.empNo }) }),
  changeMyPassword: (body: { empNo: string; currentEmpNo: string; oldPassword: string; newPassword: string }) => request<void>('/cps/admin/users/change-password', { method: 'POST', body: JSON.stringify(body) }),
  // ---- Phase A1：审计日志（超管）----
  auditLogPage: (params: Record<string, string | number | undefined>) => request<PageResult<AdminAuditLog>>(`/cps/admin/audit-logs/page${query(params)}`),
  overview: () => request<Overview>('/cps/admin/overview'),
  issues: (params: Record<string, string | number | undefined>) => request<PageResult<Issue>>(`/cps/admin/issues${query(params)}`),
  exportIssues: (params: Record<string, string | number | undefined>) => download(`/cps/admin/issues/export${query(params)}`, 'cps-issues.xlsx'),
  categories: (parentId?: number) => request<Category[]>(`/cps/admin/master/categories${query({ parentId })}`),
  categoryPage: (params: Record<string, string | number | boolean | undefined>) => request<PageResult<Category>>(`/cps/admin/master/categories/page${query(params)}`),
  exportCategories: (params: Record<string, string | number | boolean | undefined>) => download(`/cps/admin/master/categories/export${query(params)}`, 'cps-categories.xlsx'),
  saveCategory: (body: Partial<Category>) => request<Category>('/cps/admin/master/categories', { method: 'POST', body: JSON.stringify(body) }),
  setCategoryEnabled: (id: number, enabled: boolean) => request<void>(`/cps/admin/master/categories/${id}/enabled`, { method: 'PATCH', body: JSON.stringify({ enabled }) }),
  people: () => request<AreaPersonConfig[]>('/cps/admin/master/area-person-configs'),
  peoplePage: (params: Record<string, string | number | boolean | undefined>) => request<PageResult<AreaPersonConfig>>(`/cps/admin/master/area-person-configs/page${query(params)}`),
  exportPeople: (params: Record<string, string | number | boolean | undefined>) => download(`/cps/admin/master/area-person-configs/export${query(params)}`, 'cps-area-person-configs.xlsx'),
  downloadPeopleImportTemplate: () => download('/cps/admin/master/area-person-configs/import-template', '区域人员导入模板.xlsx'),
  savePerson: (body: Partial<AreaPersonConfig>) => {
    const operator = getAdminOperator()
    return request<AreaPersonConfig>('/cps/admin/master/area-person-configs', { method: 'POST', body: JSON.stringify({ ...body, operatorEmpNo: operator?.empNo, operatorEmpName: operator?.empName }) })
  },
  locationOptions: (level: 'factory' | 'area' | 'line' | 'process', params: Record<string, string | undefined> = {}) => request<{ value: string; label: string }[]>(`/cps/master/${level === 'factory' ? 'factories' : `${level}s`}${query(params)}`),
  setPersonEnabled: (id: number, enabled: boolean) => request<void>(`/cps/admin/master/area-person-configs/${id}/enabled`, { method: 'PATCH', body: JSON.stringify({ enabled }) }),
  cases: () => request<KnowledgeCase[]>('/cps/admin/knowledge/cases'),
  casePage: (params: Record<string, string | number | boolean | undefined>) => request<PageResult<KnowledgeCase>>(`/cps/admin/knowledge/cases/page${query(params)}`),
  exportCases: (params: Record<string, string | number | boolean | undefined>) => download(`/cps/admin/knowledge/cases/export${query(params)}`, 'cps-knowledge-cases.xlsx'),
  saveCase: (body: Partial<KnowledgeCase>) => request<KnowledgeCase>('/cps/admin/knowledge/cases', { method: 'POST', body: JSON.stringify(body) }),
  caseMaterials: (caseId: number) => request<KnowledgeMaterial[]>(`/cps/admin/knowledge/cases/${caseId}/images`),
  saveMaterial: (body: Record<string, unknown>) => request<KnowledgeMaterial>('/cps/admin/knowledge/materials', { method: 'POST', body: JSON.stringify(body) }),
  uploadKnowledgeMaterial: (file: File, body: Record<string, string | number | undefined>) => {
    const formData = new FormData()
    formData.append('file', file)
    const operator = getAdminOperator()
    Object.entries({ ...body, empNo: operator?.empNo }).forEach(([key, value]) => {
      if (value !== undefined) formData.append(key, String(value))
    })
    return request<KnowledgeMaterial>('/cps/admin/knowledge/materials/upload', { method: 'POST', body: formData })
  },
  syncCaseVectors: (caseId: number) => request<{ synced: number }>(`/cps/admin/knowledge/cases/${caseId}/sync-vectors`, { method: 'POST' }),
  setCaseEnabled: (id: number, enabled: boolean) => request<void>(`/cps/admin/knowledge/cases/${id}/enabled${query({ enabled })}`, { method: 'PATCH' }),
  agentRuntime: () => request<AgentRuntime>('/cps/admin/agent/runtime'),
  // ---- Phase B1：巡检问题类型 ----
  inspectionTypes: () => request<InspectionType[]>('/cps/inspection-types'),
  inspectionTypePage: (params: Record<string, string | number | undefined>) =>
    request<PageResult<InspectionType>>(`/cps/admin/inspection-types/page${query(params)}`),
  inspectionType: (id: number) => request<InspectionType>(`/cps/admin/inspection-types/${id}`),
  saveInspectionType: (body: Partial<InspectionTypeRequest> & { id?: number }) => {
    if (body.id) {
      return request<InspectionType>(`/cps/admin/inspection-types/${body.id}`, { method: 'PATCH', body: JSON.stringify(body) })
    }
    return request<InspectionType>('/cps/admin/inspection-types', { method: 'POST', body: JSON.stringify(body) })
  },
  deleteInspectionType: (id: number) => request<{ ok: boolean }>(`/cps/admin/inspection-types/${id}`, { method: 'DELETE' }),
  setInspectionTypeEnabled: (id: number, enabled: boolean) =>
    request<InspectionType>(`/cps/admin/inspection-types/${id}`, { method: 'PATCH', body: JSON.stringify({ enabled: enabled ? 1 : 0 }) }),
  // ---- Phase C1：巡检区域（统一模型，替代辅房台账）----
  inspectionAreaPage: (params: Record<string, string | number | boolean | undefined>) =>
    request<PageResult<InspectionArea>>(`/cps/admin/inspection-areas/page${query(params)}`),
  inspectionArea: (id: number) => request<InspectionArea>(`/cps/admin/inspection-areas/${id}`),
  saveInspectionArea: (id: number | undefined, body: InspectionAreaRequest) => {
    const operator = getAdminOperator()
    return request<InspectionArea>(`/cps/admin/inspection-areas${query({ id, operatorEmpNo: operator?.empNo })}`, {
      method: 'POST',
      body: JSON.stringify({ ...body, operatorEmpNo: operator?.empNo }),
    })
  },
  setInspectionAreaEnabled: (id: number, enabled: boolean) => {
    const operator = getAdminOperator()
    return request<void>(`/cps/admin/inspection-areas/${id}/enabled`, { method: 'PATCH', body: JSON.stringify({ enabled, empNo: operator?.empNo }) })
  },
  deleteInspectionArea: (id: number) => request<{ ok: boolean }>(`/cps/admin/inspection-areas/${id}`, { method: 'DELETE' }),
  // ---- Phase C1：统一巡检记录 ----
  inspectionRecordPage: (params: Record<string, string | number | undefined>) =>
    request<PageResult<InspectionRecord>>(`/cps/admin/inspection-records/page${query(params)}`),
  inspectionRecordDetail: (id: number) => request<InspectionRecordDetail>(`/cps/admin/inspection-records/${id}`),
  // ---- 波次 8 E 线：物品台账 / 出入库 / 库存预警 ----
  inventoryItems: (params: Record<string, string | number | boolean | undefined>) => request<InventoryItem[]>(`/cps/admin/inventory-items${query(params)}`),
  saveInventoryItem: (body: Partial<InventoryItem>) => {
    const operator = getAdminOperator()
    return request<InventoryItem>(`/cps/admin/inventory-items${query({ id: body.id, operatorEmpNo: operator?.empNo })}`, { method: 'POST', body: JSON.stringify(body) })
  },
  setInventoryItemEnabled: (id: number, enabled: boolean) => {
    const operator = getAdminOperator()
    return request<void>(`/cps/admin/inventory-items/${id}/enabled`, { method: 'PATCH', body: JSON.stringify({ enabled, empNo: operator?.empNo }) })
  },
  createInventoryTxn: (body: { itemId: number; txnType: string; qty: number; remark?: string }) => {
    const operator = getAdminOperator()
    return request<{ txn: InventoryTxn; item: InventoryItem; alertAction?: string }>('/cps/admin/inventory-txns', { method: 'POST', body: JSON.stringify({ ...body, operatorEmpNo: operator?.empNo, operatorName: operator?.empName }) })
  },
  inventoryTxns: (params: Record<string, string | number | undefined>) => request<RowsPage<InventoryTxn>>(`/cps/admin/inventory-txns${query(params)}`),
  inventoryAlerts: (params: Record<string, string | number | undefined>) => request<RowsPage<InventoryAlertEvent>>(`/cps/admin/inventory-alerts${query(params)}`),
  handleInventoryAlert: (id: number, body: { action: 'IGNORE' | 'CLOSE'; reason?: string }) => {
    const operator = getAdminOperator()
    return request<InventoryAlertEvent>(`/cps/admin/inventory-alerts/${id}/handle`, { method: 'POST', body: JSON.stringify({ ...body, operatorEmpNo: operator?.empNo }) })
  },
  // ---- 巡检计划（CpsInspectionPlanController）----
  inspectionPlans: (status?: string) => request<InspectionPlan[]>(`/cps/inspection-plans${query({ status })}`),
  inspectionPlanDetail: (id: number) => request<InspectionPlan>(`/cps/inspection-plans/${id}`),
  approveInspectionPlan: (id: number, lockVersion: number | undefined, comment?: string) => {
    const operator = getAdminOperator()
    return request<InspectionPlanApproveResult>(`/cps/inspection-plans/${id}/approve${query({ operatorEmpNo: operator?.empNo })}`, {
      method: 'POST',
      body: JSON.stringify({ lockVersion, approver: operator?.empNo, approverName: operator?.empName, comment }),
    })
  },
  rejectInspectionPlan: (id: number, reason: string, lockVersion?: number) => {
    const operator = getAdminOperator()
    return request<InspectionPlan>(`/cps/inspection-plans/${id}/reject${query({ operatorEmpNo: operator?.empNo })}`, {
      method: 'POST',
      body: JSON.stringify({ lockVersion, approver: operator?.empNo, approverName: operator?.empName, reason }),
    })
  },
  inspectionPlanRecordStatus: (id: number) => request<InspectionPlanRecordStatus>(`/cps/inspection-plans/${id}/record-status`),
  rebuildInspectionPlanTasks: (id: number) => {
    const operator = getAdminOperator()
    return request<InspectionPlanRebuildResult>(`/cps/inspection-plans/${id}/rebuild-tasks${query({ operatorEmpNo: operator?.empNo })}`, { method: 'POST' })
  },
  /** 手动创建巡检计划（D1 apply-draft；draftContentJson 非空则不调 AI 草稿，直接落待审核计划） */
  createInspectionPlan: (body: {
    planType: string
    title: string
    targetFactory?: string
    targetArea?: string
    riskBasis?: string
    draftContentJson: string
    draftBy?: string
  }) => request<InspectionPlan>('/cps/inspection-plans/apply-draft', { method: 'POST', body: JSON.stringify(body) }),
  // ---- 辅房 / 点检项配置（B1/B2 /api/cps/admin/rooms|check-items）----
  rooms: (params?: { factory?: string; roomType?: string; enabled?: boolean }) => request<Room[]>(`/cps/admin/rooms${query(params ?? {})}`),  saveRoom: (body: Partial<Room>) => {
    const operator = getAdminOperator()
    return request<Room>(`/cps/admin/rooms${query({ id: body.id, operatorEmpNo: operator?.empNo })}`, { method: 'POST', body: JSON.stringify(body) })
  },
  setRoomEnabled: (id: number, enabled: boolean) => {
    const operator = getAdminOperator()
    return request<void>(`/cps/admin/rooms/${id}/enabled`, { method: 'PATCH', body: JSON.stringify({ enabled, empNo: operator?.empNo }) })
  },
  // ---- 辅房点检记录追溯（PRD §22/§24 /api/cps/admin/room-check-records）----
  roomCheckRecords: (params: Record<string, string | number | undefined>) =>
    request<TotalRows<RoomCheckRecordRow>>(`/cps/admin/room-check-records${query(params)}`),
  roomCheckRecordDetail: (id: number) => request<RoomCheckRecordDetail>(`/cps/admin/room-check-records/${id}`),
  roomCheckRejudge: (id: number) => request<RoomCheckRejudgeResult>(`/cps/admin/room-check-records/${id}/rejudge`, { method: 'POST' }),
  checkItems: (params?: { photoCategory?: string; status?: string; enabled?: boolean }) => request<CheckItem[]>(`/cps/admin/check-items${query(params ?? {})}`),
  saveCheckItem: (body: Partial<CheckItem>) => {
    const operator = getAdminOperator()
    return request<CheckItem>(`/cps/admin/check-items${query({ id: body.id, operatorEmpNo: operator?.empNo })}`, { method: 'POST', body: JSON.stringify(body) })
  },
  setCheckItemEnabled: (id: number, enabled: boolean) => {
    const operator = getAdminOperator()
    return request<void>(`/cps/admin/check-items/${id}/enabled`, { method: 'PATCH', body: JSON.stringify({ enabled, empNo: operator?.empNo }) })
  },
  // ---- 通用字典（双表：类型 /api/cps/admin/dict-types + 条目 /api/cps/admin/dicts）----
  dictTypes: (params?: { enabled?: boolean }) => request<DictType[]>(`/cps/admin/dict-types${query(params ?? {})}`),
  saveDictType: (body: Partial<DictType>) => {
    const operator = getAdminOperator()
    return request<DictType>(`/cps/admin/dict-types${query({ id: body.id, operatorEmpNo: operator?.empNo })}`, { method: 'POST', body: JSON.stringify(body) })
  },
  setDictTypeEnabled: (id: number, enabled: boolean) => {
    const operator = getAdminOperator()
    return request<void>(`/cps/admin/dict-types/${id}/enabled`, { method: 'PATCH', body: JSON.stringify({ enabled, empNo: operator?.empNo }) })
  },
  deleteDictType: (id: number) => {
    const operator = getAdminOperator()
    return request<void>(`/cps/admin/dict-types/${id}${query({ operatorEmpNo: operator?.empNo })}`, { method: 'DELETE' })
  },
  dicts: (params?: { typeCode?: string; enabled?: boolean }) => request<DictEntry[]>(`/cps/admin/dicts${query(params ?? {})}`),
  saveDict: (body: Partial<DictEntry>) => {
    const operator = getAdminOperator()
    // 后端 CpsDictRequest 字段名为 dictType（=条目的 typeCode）
    const payload = { ...body, dictType: body.typeCode }
    delete payload.typeCode
    return request<DictEntry>(`/cps/admin/dicts${query({ id: body.id, operatorEmpNo: operator?.empNo })}`, { method: 'POST', body: JSON.stringify(payload) })
  },
  setDictEnabled: (id: number, enabled: boolean) => {
    const operator = getAdminOperator()
    return request<void>(`/cps/admin/dicts/${id}/enabled`, { method: 'PATCH', body: JSON.stringify({ enabled, empNo: operator?.empNo }) })
  },
  deleteDict: (id: number) => {
    const operator = getAdminOperator()
    return request<void>(`/cps/admin/dicts/${id}${query({ operatorEmpNo: operator?.empNo })}`, { method: 'DELETE' })
  },
}

// ============================================================
// Phase A2 通用审批中心
// ============================================================
export const approvals = {
  listPending: (params: { bizType?: string; keyword?: string; page?: number; pageSize?: number } = {}) => {
    return request<TotalRows<ApprovalInstance>>(
      `/cps/admin/approvals/pending${query({
        bizType: params.bizType,
        keyword: params.keyword,
        page: params.page ?? 1,
        pageSize: params.pageSize ?? 20,
      })}`
    )
  },
  listAll: (params: { status?: string; bizType?: string; keyword?: string; page?: number; pageSize?: number } = {}) => {
    return request<TotalRows<ApprovalInstance>>(
      `/cps/admin/approvals/page${query({
        status: params.status,
        bizType: params.bizType,
        keyword: params.keyword,
        page: params.page ?? 1,
        pageSize: params.pageSize ?? 20,
      })}`
    )
  },
  getDetail: (id: number) => request<ApprovalDetail>(`/cps/admin/approvals/${id}`),
  act: (id: number, action: ApprovalAction, comment: string) => {
    const operator = getAdminOperator()
    return request<ApprovalInstance>(`/cps/admin/approvals/${id}/action`, {
      method: 'POST',
      body: JSON.stringify({
        action,
        operatorEmpNo: operator?.empNo,
        operatorEmpName: operator?.empName,
        comment,
      }),
    })
  },
}

async function download(path: string, fallbackFileName: string) {
  const response = await fetch(`${baseUrl}${path}`, { credentials: 'include' })
  if (!response.ok) {
    throw new ApiError(response.status, await response.text() || `导出失败 (${response.status})`)
  }
  const file = new Blob([await response.blob()], { type: response.headers.get('Content-Type') || 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
  const url = URL.createObjectURL(file)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = fallbackFileName
  anchor.click()
  URL.revokeObjectURL(url)
}
