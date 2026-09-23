import { request } from '@/api/request'
import type {
  CpsInitialReviewView,
} from '@/types/cps'

const EMP_NO = import.meta.env.VITE_CPS_EMP_NO ?? 'DEMO_EMP'

/** B5② 审核员初审视图（AI 三态 + 可接管性 + 结果/逐项意见 + 提交快照 + 裁决 + 事件）。 */
export const getInitialReviewView = (issueId: number) => {
  return request.get<CpsInitialReviewView>(`/api/cps/issues/${issueId}/initial-review`)
}

/** 超时/失败接管：必须注明原因；接管后迟到 AI 结果仅留痕（PRD §28.4）。 */
export const takeOverInitialReview = (issueId: number, reason: string) => {
  return request.post<Record<string, unknown>>(
    `/api/cps/issues/${issueId}/initial-review/take-over`,
    { empNo: EMP_NO, reason },
  )
}

/** 审核裁决：APPROVE=通过关单；REJECT=退回整改人员；理由必填。 */
export const adjudicateReview = (
  issueId: number,
  decision: 'APPROVE' | 'REJECT',
  reason: string,
) => {
  return request.post<{ issueId: number; status: string; duplicated: boolean }>(
    `/api/cps/issues/${issueId}/adjudicate`,
    { empNo: EMP_NO, decision, reason },
  )
}
