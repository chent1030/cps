import { request } from '@/api/request'
import type { CpsIssueDraft, CpsIssueDraftSaveRequest } from '@/types/cps'

const EMP_NO = import.meta.env.VITE_CPS_EMP_NO ?? '61016968'
const EMP_NAME = import.meta.env.VITE_CPS_EMP_NAME ?? '陈涛'

/** Phase B2：暂存草稿（id 空=新建，非空=覆盖更新）。返回落库后的草稿（含 id）。 */
export const saveCpsIssueDraft = (payload: CpsIssueDraftSaveRequest) => {
  return request.post<CpsIssueDraft>('/api/cps/issue-drafts', {
    ...payload,
    empNo: payload.empNo ?? EMP_NO,
    empName: payload.empName ?? EMP_NAME,
  })
}

/** 我的 ACTIVE 草稿列表（按最近暂存倒序）。 */
export const listMyCpsIssueDrafts = () => {
  return request.get<CpsIssueDraft[]>('/api/cps/issue-drafts', {
    params: { empNo: EMP_NO },
  })
}

/** 取单份草稿（恢复时用；仅限本人）。 */
export const getCpsIssueDraft = (id: number) => {
  return request.get<CpsIssueDraft>(`/api/cps/issue-drafts/${id}`, {
    params: { empNo: EMP_NO },
  })
}

/** 删除草稿。 */
export const deleteCpsIssueDraft = (id: number) => {
  return request.delete<{ ok: boolean }>(`/api/cps/issue-drafts/${id}`, {
    params: { empNo: EMP_NO },
  })
}
