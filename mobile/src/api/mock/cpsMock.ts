import type {
  CpsAiSuggestionPayload,
  CpsIssueDetail,
  CpsIssueListItem,
  CpsIssueStatus,
  CpsIssueTab,
  CpsOption,
  CpsUploadedImage,
} from '@/types/cps'

type QueryValue = string | number | boolean | null | undefined

interface MockRequest {
  method: string
  path: string
  params?: Record<string, QueryValue>
  body?: unknown
}

const factories: CpsOption[] = [
  { value: 'Factory A', label: 'Factory A' },
  { value: 'Factory B', label: 'Factory B' },
  { value: 'Pilot Factory', label: 'Pilot Factory' },
]

const areas: Record<string, CpsOption[]> = {
  'Factory A': [
    { value: 'Injection Area', label: 'Injection Area' },
    { value: 'Assembly Area', label: 'Assembly Area' },
  ],
  'Factory B': [
    { value: 'Stamping Area', label: 'Stamping Area' },
    { value: 'Packaging Area', label: 'Packaging Area' },
  ],
  'Pilot Factory': [{ value: 'Trial Area', label: 'Trial Area' }],
}

const lines: Record<string, CpsOption[]> = {
  'Factory A|Injection Area': [
    { value: 'A1 Line', label: 'A1 Line' },
    { value: 'A2 Line', label: 'A2 Line' },
  ],
  'Factory A|Assembly Area': [{ value: 'B1 Line', label: 'B1 Line' }],
  'Factory B|Stamping Area': [{ value: 'C1 Line', label: 'C1 Line' }],
  'Factory B|Packaging Area': [{ value: 'D1 Line', label: 'D1 Line' }],
  'Pilot Factory|Trial Area': [{ value: 'Trial Line', label: 'Trial Line' }],
}

const processes: Record<string, CpsOption[]> = {
  'Factory A|Injection Area|A1 Line': [
    { value: 'First Article', label: 'First Article' },
    { value: 'Appearance Check', label: 'Appearance Check' },
  ],
  'Factory A|Injection Area|A2 Line': [{ value: 'Material Feeding', label: 'Material Feeding' }],
  'Factory A|Assembly Area|B1 Line': [{ value: 'Locking', label: 'Locking' }],
  'Factory B|Stamping Area|C1 Line': [{ value: 'Forming', label: 'Forming' }],
  'Factory B|Packaging Area|D1 Line': [{ value: 'Packing', label: 'Packing' }],
  'Pilot Factory|Trial Area|Trial Line': [{ value: 'Trial Assembly', label: 'Trial Assembly' }],
}

const categories: Record<number, CpsOption[]> = {
  0: [
    { value: 100, label: 'Site 5S' },
    { value: 200, label: 'Quality Issue' },
    { value: 300, label: 'Equipment Safety' },
  ],
  100: [
    { value: 101, label: 'Missing Label' },
    { value: 102, label: 'Mixed Materials' },
  ],
  200: [
    { value: 201, label: 'Appearance Defect' },
    { value: 202, label: 'Size Deviation' },
  ],
  300: [
    { value: 301, label: 'Missing Guard' },
    { value: 302, label: 'Inspection Abnormal' },
  ],
}

const issues: CpsIssueListItem[] = [
  {
    id: 1,
    issueNo: 'CPS20260627001',
    status: 'PENDING_FEEDBACK',
    factory: 'Factory A',
    area: 'Injection Area',
    line: 'A1 Line',
    process: 'Appearance Check',
    categoryL1Name: 'Site 5S',
    categoryL2Name: 'Missing Label',
    description: 'A turnover box is missing status label.',
    currentHandlerEmpNo: 'E10001',
    currentHandlerEmpName: 'E10001',
    submitTime: '2026-06-27 09:10',
    overdue: false,
  },
  {
    id: 2,
    issueNo: 'CPS20260626018',
    status: 'PENDING_UPLOAD_PROOF',
    factory: 'Factory B',
    area: 'Packaging Area',
    line: 'D1 Line',
    process: 'Packing',
    categoryL1Name: 'Quality Issue',
    categoryL2Name: 'Appearance Defect',
    description: 'Surface scratch found before packing.',
    currentHandlerEmpNo: 'E20009',
    currentHandlerEmpName: 'E20009',
    submitTime: '2026-06-26 15:32',
    overdue: true,
  },
]

const detail: CpsIssueDetail = {
  ...issues[0],
  creatorEmpNo: 'E09999',
  feedbackEmpNo: 'E10001',
  responsibleEmpNo: null,
  proofEmpNo: null,
  reviewerEmpNo: 'E90001',
  reasonAnalysis: null,
  correctiveMeasure: null,
  rectifyRemark: null,
  reviewOpinion: null,
  closeTime: null,
  issueAttachments: [
    {
      id: 501,
      fileUrl: 'https://images.unsplash.com/photo-1581092580497-e0d23cbdf1dc?auto=format&fit=crop&w=600&q=80',
      fileName: 'issue.jpg',
      fileType: 'image/jpeg',
      sortNo: 1,
    },
  ],
  proofAttachments: [],
  aiSuggestion: {
    sourceAttachmentId: 501,
    aiCategoryL1Id: 100,
    aiCategoryL1Name: 'Site 5S',
    aiCategoryL2Id: 101,
    aiCategoryL2Name: 'Missing Label',
    reasonSuggestion: 'Status label was not updated during handover.',
    measureSuggestion: 'Add the missing label and include it in pre-shift checks.',
    modelName: 'mock-vision',
    modelVersion: '1.0',
    rawRequest: '{}',
    rawResponse: '{}',
    confidence: '0.8800',
  },
  availableActions: ['REPLY_ASSIGN', 'TRANSFER'],
  flowLogs: [
    {
      action: 'SUBMIT',
      operatorEmpNo: 'E09999',
      fromStatus: null,
      toStatus: 'PENDING_FEEDBACK',
      comment: 'Submitted by mobile mock.',
      createdAt: '2026-06-27 09:10',
    },
  ],
}

const num = (value: QueryValue) => {
  return Number(value ?? 0)
}

const text = (value: QueryValue) => {
  return String(value ?? '')
}

const issueTab = (tab: QueryValue) => {
  return tab === 'created' || tab === 'related' || tab === 'closed' ? tab : 'todo'
}

const filterIssues = (tab: CpsIssueTab) => {
  if (tab === 'closed') return issues.filter((item) => item.status === 'CLOSED')
  if (tab === 'todo') return issues.filter((item) => item.status !== 'CLOSED')
  return issues
}

const jsonBody = (body: unknown) => {
  if (typeof body !== 'string') {
    return body && typeof body === 'object' ? (body as Record<string, unknown>) : {}
  }

  try {
    return JSON.parse(body) as Record<string, unknown>
  } catch {
    return {}
  }
}

const isFormDataBody = (body: unknown) => {
  return typeof FormData !== 'undefined' && body instanceof FormData
}

export const getCpsMockResponse = <T>({ method, path, params, body }: MockRequest) => {
  if (method === 'GET' && path === '/api/cps/master/factories') return factories as T
  if (method === 'GET' && path === '/api/cps/master/areas') return (areas[text(params?.factory)] ?? []) as T
  if (method === 'GET' && path === '/api/cps/master/lines') return (lines[`${text(params?.factory)}|${text(params?.area)}`] ?? []) as T
  if (method === 'GET' && path === '/api/cps/master/processes') {
    const baseKey = `${text(params?.factory)}|${text(params?.area)}`
    const line = text(params?.line)
    if (line) return (processes[`${baseKey}|${line}`] ?? []) as T
    return (lines[baseKey] ?? []).flatMap((item) => processes[`${baseKey}|${String(item.value)}`] ?? []) as T
  }
  if (method === 'GET' && path === '/api/cps/master/categories') return (categories[num(params?.parentId)] ?? categories[0]) as T
  if (method === 'GET' && path === '/api/cps/assignment/feedback-handler') {
    return [
      { empNo: 'E10001', empName: 'E10001' },
      { empNo: 'E10002', empName: 'E10002' },
    ] as T
  }
  if (method === 'GET' && path === '/api/cps/assignment/reviewer') {
    return { empNo: 'E90001', empName: 'E90001' } as T
  }
  if (method === 'GET' && path === '/api/cps/issues') return filterIssues(issueTab(params?.tab)) as T
  if (method === 'GET' && /^\/api\/cps\/issues\/\d+$/.test(path)) return { ...detail, id: Number(path.split('/').pop()) } as T
  if (method === 'POST' && path === '/api/cps/issues') return { issueId: 999 } as T
  if (method === 'POST' && /^\/api\/cps\/issues\/\d+\/actions$/.test(path)) {
    return { issueId: Number(path.split('/')[4]), status: 'PENDING_RECTIFY' as CpsIssueStatus, currentHandlerEmpNo: 'E10023' } as T
  }
  if (method === 'POST' && path === '/api/cps/attachments') {
    const file = isFormDataBody(body) ? ((body as FormData).get('file') as File | null) : null
    const image: CpsUploadedImage = {
      id: Date.now(),
      url: file && typeof URL.createObjectURL === 'function' ? URL.createObjectURL(file) : detail.issueAttachments[0].fileUrl,
      name: file?.name ?? 'mock-image.jpg',
    }
    return image as T
  }
  if (method === 'POST' && (path === '/api/cps/ai/inspect-image' || path === '/api/cps/ai/match-knowledge')) {
    const payload = jsonBody(body)
    const suggestion: CpsAiSuggestionPayload = {
      sourceAttachmentId: Number(payload.sourceAttachmentId ?? payload.attachmentId ?? 0),
      aiCategoryL1Id: 100,
      aiCategoryL1Name: 'Site 5S',
      aiCategoryL2Id: 101,
      aiCategoryL2Name: 'Missing Label',
      reasonSuggestion: 'Status label is missing.',
      measureSuggestion: 'Add the label and verify during shift handover.',
      modelName: 'mock-vision',
      modelVersion: '1.0',
      rawRequest: '{}',
      rawResponse: '{}',
      confidence: '0.8800',
    }
    return suggestion as T
  }
  return undefined
}
