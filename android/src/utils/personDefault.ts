import type { CpsEmployeeOption } from '@/api/cps/master'
import type { CpsIssueAction, CpsIssueDetail } from '@/types/cps'

/**
 * 「后续节点人员默认选择」逻辑。
 *
 * 业务规则：除第一次（新建问题选反馈人）外，后续各节点要选的人员默认填入「上一节点的操作者」，
 * 用户仍可在界面修改。本模块仅决定「该填谁」，不负责回写 UI。
 *
 * 节点 → 要选的人员字段 → 默认来源（上一节点操作者）：
 * - PENDING_FEEDBACK (REPLY_ASSIGN) → 责任员工 ← 提交人 creatorEmpNo
 * - PENDING_RECTIFY  (RECTIFY)      → 凭证上传人 ← 反馈人 feedbackEmpNo
 * - PENDING_UPLOAD_PROOF (UPLOAD_PROOF) → 审核专员 ← 不预填（依赖后端 findReviewer）
 * - TRANSFER                        → 转办目标 ← 不预填
 *
 * 默认值集中维护，便于将来调整（如改为登录人 X-Emp-No）。
 */

export type ActionPersonField = 'responsibleEmpNo' | 'proofEmpNo' | 'reviewerEmpNo' | 'targetEmpNo'

/** 各动作需要默认填入的人员字段（无则不填） */
const ACTION_TARGET_FIELD: Partial<Record<CpsIssueAction, ActionPersonField>> = {
  REPLY_ASSIGN: 'responsibleEmpNo',
  RECTIFY: 'proofEmpNo',
  // UPLOAD_PROOF 的审核员、TRANSFER 的转办目标按需求不预填
}

/** 字段 → 从 detail 的哪个属性取默认工号 */
const FIELD_SOURCE_EMP_NO: Record<ActionPersonField, keyof CpsIssueDetail> = {
  responsibleEmpNo: 'creatorEmpNo', // 上一节点=SUBMIT（提交人）
  proofEmpNo: 'feedbackEmpNo', // 上一节点=REPLY_ASSIGN（反馈人）
  reviewerEmpNo: 'reviewerEmpNo', // 占位：实际不预填
  targetEmpNo: 'currentHandlerEmpNo', // 占位：实际不预填
}

/** 字段 → 从 detail 的哪个属性取默认姓名 */
const FIELD_SOURCE_EMP_NAME: Record<ActionPersonField, keyof CpsIssueDetail> = {
  responsibleEmpNo: 'creatorEmpNo',
  proofEmpNo: 'feedbackEmpNo',
  reviewerEmpNo: 'reviewerEmpNo',
  targetEmpNo: 'currentHandlerEmpName',
}

export interface DefaultPerson {
  field: ActionPersonField
  person: CpsEmployeeOption
}

/**
 * 根据当前可用动作，计算需要默认填入的人员。
 * 通常一个状态只有一个主动作需要默认人（TRANSFER 不预填）。
 * 返回的列表可直接合并进 actionForm 与 selectedPeople。
 */
export const resolveDefaultPersons = (detail: CpsIssueDetail): DefaultPerson[] => {
  const result: DefaultPerson[] = []
  const actions = detail.availableActions.length
    ? detail.availableActions
    : (statusFallbackActions(detail.status) as CpsIssueAction[])

  for (const action of actions) {
    const field = ACTION_TARGET_FIELD[action]
    if (!field) continue

    const empNo = detail[FIELD_SOURCE_EMP_NO[field]] as string | null | undefined
    if (!empNo) continue

    const empName = (detail[FIELD_SOURCE_EMP_NAME[field]] as string | null | undefined) || empNo
    result.push({
      field,
      person: { empNo, empName },
    })
  }

  return result
}

const statusFallbackActions = (status: CpsIssueDetail['status']): readonly string[] => {
  const map: Record<CpsIssueDetail['status'], readonly string[]> = {
    PENDING_FEEDBACK: ['REPLY_ASSIGN', 'TRANSFER'],
    PENDING_RECTIFY: ['RECTIFY', 'TRANSFER'],
    PENDING_UPLOAD_PROOF: ['UPLOAD_PROOF', 'TRANSFER'],
    PENDING_REVIEW: ['REVIEW_CLOSE', 'REVIEW_REJECT', 'TRANSFER'],
    CLOSED: [],
  }
  return map[status]
}
