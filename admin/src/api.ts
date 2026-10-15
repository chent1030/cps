import type { AdminOperator, AgentRuntime, AreaPersonConfig, Category, InventoryAlertEvent, InventoryItem, InventoryTxn, Issue, KnowledgeCase, KnowledgeMaterial, Overview, PageResult, RowsPage } from './types'

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
  adminLogin: (body: { empNo: string; empName: string }) => request<AdminOperator>('/cps/admin/master/auth/login', { method: 'POST', body: JSON.stringify(body) }),
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
