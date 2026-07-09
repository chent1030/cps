import { request } from '@/api/request'
import type {
  CpsIssueActionRequest,
  CpsIssueCreateRequest,
  CpsIssueDetail,
  CpsIssueListItem,
  CpsIssueStatus,
  CpsIssueTab,
} from '@/types/cps'

export const createCpsIssue = (payload: CpsIssueCreateRequest): Promise<{ issueId: number }> => {
  return request.post<{ issueId: number }>('/api/cps/issues', payload)
}

export const listCpsIssues = (params: {
  tab: CpsIssueTab
  status?: CpsIssueStatus
  keyword?: string
  page: number
  pageSize: number
}): Promise<CpsIssueListItem[]> => {
  return request.get<CpsIssueListItem[]>('/api/cps/issues', { params })
}

export const getCpsIssueDetail = (id: number): Promise<CpsIssueDetail> => {
  return request.get<CpsIssueDetail>(`/api/cps/issues/${id}`)
}

export const executeCpsIssueAction = (
  id: number,
  payload: CpsIssueActionRequest,
): Promise<{
  issueId: number
  status: CpsIssueStatus
  currentHandlerEmpNo: string | null
}> => {
  return request.post<{
    issueId: number
    status: CpsIssueStatus
    currentHandlerEmpNo: string | null
  }>(`/api/cps/issues/${id}/actions`, payload)
}
