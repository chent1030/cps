import { request } from '@/api/request'
import type {
  CpsIssueActionRequest,
  CpsIssueCreateRequest,
  CpsIssueDetail,
  CpsIssueListItem,
  CpsIssueStatus,
  CpsIssueTab,
} from '@/types/cps'

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

export const getCpsIssueDetail = (id: number) => {
  return request.get<CpsIssueDetail>(`/api/cps/issues/${id}`)
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
