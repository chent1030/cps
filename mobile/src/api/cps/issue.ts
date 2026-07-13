import { request } from '@/api/request'
import type {
  CpsAiSuggestionPayload,
  CpsAttachment,
  CpsIssueActionRequest,
  CpsIssueCreateRequest,
  CpsIssueDetail,
  CpsFlowLog,
  CpsIssueListItem,
  CpsIssueStatus,
  CpsIssueTab,
} from '@/types/cps'

type CpsIssueDetailCore = Omit<CpsIssueDetail, 'issueAttachments' | 'proofAttachments' | 'aiSuggestion' | 'availableActions' | 'flowLogs'>

interface CpsIssueDetailApiResponse {
  issue?: CpsIssueDetailCore
  issueAttachments?: CpsAttachment[]
  proofAttachments?: CpsAttachment[]
  aiSuggestion?: CpsAiSuggestionPayload | null
  availableActions?: CpsIssueDetail['availableActions']
  flowLogs?: CpsFlowLog[]
}

export const createCpsIssue = (payload: CpsIssueCreateRequest) => {
  return request.post<{ issueId: number }>('/api/cps/issues', payload)
}

export const listCpsIssues = (params: {
  tab: CpsIssueTab
  status?: CpsIssueStatus
  keyword?: string
  page: number
  pageSize: number
}) => {
  return request.get<CpsIssueListItem[]>('/api/cps/issues', { params })
}

export const getCpsIssueDetail = async (id: number) => {
  const response = await request.get<CpsIssueDetailApiResponse & Partial<CpsIssueDetail>>(`/api/cps/issues/${id}`)
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

export const executeCpsIssueAction = (
  id: number,
  payload: CpsIssueActionRequest,
) => {
  return request.post<{
    issueId: number
    status: CpsIssueStatus
    currentHandlerEmpNo: string | null
  }>(`/api/cps/issues/${id}/actions`, payload)
}
