import { describe, expect, it } from 'vitest'

import { resolveDefaultPersons } from '@/utils/personDefault'
import type { CpsIssueDetail } from '@/types/cps'

const baseDetail = (overrides: Partial<CpsIssueDetail> = {}): CpsIssueDetail => {
  return {
    id: 1,
    issueNo: 'CPS001',
    status: 'PENDING_FEEDBACK',
    factory: 'Factory A',
    area: 'Injection Area',
    line: 'A1 Line',
    process: 'Appearance Check',
    description: 'desc',
    currentHandlerEmpNo: 'E10001',
    currentHandlerEmpName: 'E10001',
    submitTime: '2026-06-27 09:10',
    creatorEmpNo: 'E09999',
    feedbackEmpNo: 'E10001',
    responsibleEmpNo: null,
    proofEmpNo: null,
    reviewerEmpNo: null,
    reasonAnalysis: null,
    correctiveMeasure: null,
    rectifyRemark: null,
    reviewOpinion: null,
    closeTime: null,
    issueAttachments: [],
    proofAttachments: [],
    aiSuggestion: null,
    availableActions: ['REPLY_ASSIGN', 'TRANSFER'],
    flowLogs: [],
    ...overrides,
  }
}

describe('resolveDefaultPersons — 后续节点人员默认选择', () => {
  it('PENDING_FEEDBACK：责任员工默认 = 提交人（上一节点 SUBMIT 的操作者）', () => {
    const result = resolveDefaultPersons(baseDetail({ creatorEmpNo: 'E09999' }))
    expect(result).toHaveLength(1)
    expect(result[0].field).toBe('responsibleEmpNo')
    expect(result[0].person.empNo).toBe('E09999')
  })

  it('PENDING_RECTIFY：凭证上传人默认 = 反馈人（上一节点 REPLY_ASSIGN 的操作者）', () => {
    const result = resolveDefaultPersons(
      baseDetail({
        status: 'PENDING_RECTIFY',
        feedbackEmpNo: 'E10001',
        availableActions: ['RECTIFY', 'TRANSFER'],
      }),
    )
    expect(result).toHaveLength(1)
    expect(result[0].field).toBe('proofEmpNo')
    expect(result[0].person.empNo).toBe('E10001')
  })

  it('PENDING_UPLOAD_PROOF：审核专员不预填（按需求保持手动 / 交后端）', () => {
    const result = resolveDefaultPersons(
      baseDetail({
        status: 'PENDING_UPLOAD_PROOF',
        availableActions: ['UPLOAD_PROOF', 'TRANSFER'],
      }),
    )
    expect(result.find((r) => r.field === 'reviewerEmpNo')).toBeUndefined()
  })

  it('TRANSFER 目标不预填', () => {
    const result = resolveDefaultPersons(baseDetail())
    expect(result.find((r) => r.field === 'targetEmpNo')).toBeUndefined()
  })

  it('CLOSED：无任何默认人', () => {
    const result = resolveDefaultPersons(
      baseDetail({ status: 'CLOSED', availableActions: [] }),
    )
    expect(result).toEqual([])
  })

  it('availableActions 为空时按 status 兜底', () => {
    const result = resolveDefaultPersons(
      baseDetail({ status: 'PENDING_FEEDBACK', availableActions: [], creatorEmpNo: 'E09999' }),
    )
    expect(result).toHaveLength(1)
    expect(result[0].person.empNo).toBe('E09999')
  })

  it('上一节点操作者为空时不预填（避免填入空值）', () => {
    const result = resolveDefaultPersons(baseDetail({ creatorEmpNo: '' }))
    expect(result).toEqual([])
  })
})
