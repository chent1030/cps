import { request } from '@/api/request'
import type {
  CpsIssueActionRequest,
  CpsIssueCreateRequest,
  CpsIssueDetail,
  CpsIssueListItem,
  CpsIssueStatus,
  CpsIssueTab,
} from '@/types/cps'

const EMP_NO = import.meta.env.VITE_CPS_EMP_NO ?? '61016968'
const EMP_NAME = import.meta.env.VITE_CPS_EMP_NAME ?? '陈涛'

export const createCpsIssue = (payload: CpsIssueCreateRequest) => {
  return request.post<{ issueId: number }>('/api/cps/issues', {
    ...payload,
    empNo: payload.empNo ?? EMP_NO,
    empName: payload.empName ?? EMP_NAME,
  })
}

export const listCpsIssues = (params: {
  tab: CpsIssueTab
  status?: CpsIssueStatus
  keyword?: string
  page: number
  pageSize: number
}) => {
  return request.get<CpsIssueListItem[]>('/api/cps/issues', {
    params: { ...params, empNo: EMP_NO },
  })
}

export const getCpsIssueDetail = (id: number) => {
  return request.get<CpsIssueDetail>(`/api/cps/issues/${id}`, { params: { empNo: EMP_NO } })
}

/** 页签角标：当前用户 4 个问题页签的条数 */
export const getCpsIssueCounts = () => {
  return request.get<Record<CpsIssueTab, number>>('/api/cps/issues/counts', {
    params: { empNo: EMP_NO },
  })
}

export const executeCpsIssueAction = (id: number, payload: CpsIssueActionRequest) => {
  return request.post<{
    issueId: number
    status: CpsIssueStatus
    currentHandlerEmpNo: string | null
  }>(`/api/cps/issues/${id}/actions`, {
    ...payload,
    empNo: payload.empNo ?? EMP_NO,
    empName: payload.empName ?? EMP_NAME,
  })
}
