import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import InitialReviewView from '../InitialReviewView.vue'

const mocks = vi.hoisted(() => ({
  getInitialReviewView: vi.fn(),
  takeOverInitialReview: vi.fn(),
  adjudicateReview: vi.fn(),
}))

vi.mock('@/api/cps/initialReview', () => mocks)

vi.mock('@dcloudio/uni-app', () => ({
  onLoad: (callback: (query?: Record<string, string>) => void) => {
    callback({ issueId: '301' })
  },
}))

const baseTask = {
  id: 11,
  issueId: 301,
  submissionId: 21,
  versionNo: 1,
  status: 'RUNNING',
  submittedAt: '2026-09-30 10:00:00',
  timeoutAt: '2026-09-30 10:10:00',
  completedAt: null,
  takenOverBy: null,
  takenOverName: null,
  takenOverAt: null,
  takeoverReason: null,
  errorCode: null,
  retryCount: 0,
}

const baseResult = {
  id: 31,
  taskId: 11,
  overall: 'PASS',
  modelStatus: 'qwen3:8b',
  isLate: false,
  createdAt: '2026-09-30 10:02:00',
}

const baseItems = [
  {
    id: 41,
    checkType: 'TEXT_LENGTH',
    fieldName: '原因分析',
    verdict: 'PASS',
    textLength: 22,
    punctuationCount: 2,
    ratioOk: true,
    reason: null,
    problemFragment: null,
  },
  {
    id: 42,
    checkType: 'TEXT_LENGTH',
    fieldName: '短期措施',
    verdict: 'FAIL',
    textLength: 5,
    punctuationCount: 6,
    ratioOk: false,
    reason: '字段长度不足（L≥15）',
    problemFragment: '加强点检',
  },
]

const baseSubmission = {
  id: 21,
  issueId: 301,
  versionNo: 1,
  reason: '周转箱缺少状态标识卡',
  shortTermMeasure: '两周内补齐所有周转箱状态标识卡',
  longTermMeasure: '修订点检表并纳入月度审核',
  responsibleEmpNo: 'EMP1024',
  responsibleEmpName: '王强',
  submittedBy: 'EMP2048',
  submittedAt: '2026-09-30 09:58:00',
}

const baseEvents = [
  {
    id: 51,
    eventType: 'SUBMITTED',
    detail: '整改提交 v1',
    operatorEmpNo: 'EMP2048',
    createdAt: '2026-09-30 09:58:00',
  },
  {
    id: 52,
    eventType: 'TAKE_OVER',
    detail: '超时接管：AI 未返回',
    operatorEmpNo: 'EMP3072',
    createdAt: '2026-09-30 10:11:00',
  },
]

const view = (overrides: Record<string, unknown>) => ({
  issue_id: 301,
  state_view: 'running',
  can_take_over: false,
  seconds_until_takeover: 540,
  task: baseTask,
  result: null,
  items: [],
  submission: baseSubmission,
  adjudication: null,
  events: baseEvents,
  ...overrides,
})

const mountView = async (first: Record<string, unknown>, second?: Record<string, unknown>) => {
  mocks.getInitialReviewView.mockResolvedValueOnce(view(first))
  if (second) mocks.getInitialReviewView.mockResolvedValueOnce(view(second))
  const wrapper = mount(InitialReviewView)
  await flushPromises()
  return wrapper
}

describe('InitialReviewView', () => {
  beforeEach(() => {
    mocks.getInitialReviewView.mockReset()
    mocks.takeOverInitialReview.mockReset()
    mocks.adjudicateReview.mockReset()
  })

  it('distinguishes running state with countdown and hides takeover', async () => {
    const wrapper = await mountView({ state_view: 'running', can_take_over: false, seconds_until_takeover: 540 })

    expect(wrapper.text()).toContain('AI 初审运行中')
    expect(wrapper.text()).toContain('约 540s 后达到接管阈值')
    // 运行中不可接管：无接管面板（PRD §28.4 界面必须区分运行中/失败/超时）
    expect(wrapper.find('.cps-review-takeover').exists()).toBe(false)
    expect(wrapper.text()).toContain('整改提交内容')
    expect(wrapper.text()).toContain('周转箱缺少状态标识卡')
    expect(mocks.getInitialReviewView).toHaveBeenCalledWith(301)
  })

  it('shows timeout takeover with mandatory reason then reloads', async () => {
    const wrapper = await mountView(
      { state_view: 'timeout_open', can_take_over: true, seconds_until_takeover: 0 },
      { state_view: 'taken_over', can_take_over: false, seconds_until_takeover: null, task: { ...baseTask, status: 'TAKEN_OVER', takenOverBy: 'EMP3072', takenOverName: '李审核', takeoverReason: 'AI 超时未返回' } },
    )
    mocks.takeOverInitialReview.mockResolvedValueOnce({ issueId: 301, status: 'PENDING_REVIEWER_CONFIG' })

    expect(wrapper.text()).toContain('超时可接管')
    expect(wrapper.text()).toContain('超过 10 分钟接管阈值（600s）')

    const submit = wrapper.get('.cps-review-takeover__submit')
    // 原因为空时禁用（接管必须注明原因）
    expect(submit.attributes('disabled')).toBeDefined()

    await wrapper.get('.cps-review-takeover__reason textarea').setValue('AI 超时未返回，人工接管')
    await submit.trigger('click')
    await flushPromises()

    expect(mocks.takeOverInitialReview).toHaveBeenCalledWith(301, 'AI 超时未返回，人工接管')
    // 接管后重载：显示接管人+原因留痕
    expect(wrapper.text()).toContain('已人工接管')
    expect(wrapper.text()).toContain('李审核')
    expect(wrapper.text()).toContain('AI 超时未返回')
  })

  it('renders failed state with error code and immediate takeover hint', async () => {
    const wrapper = await mountView({
      state_view: 'failed',
      can_take_over: true,
      seconds_until_takeover: 0,
      task: { ...baseTask, status: 'FAILED', errorCode: 'MODEL_TIMEOUT' },
    })

    expect(wrapper.text()).toContain('AI 执行失败')
    expect(wrapper.text()).toContain('MODEL_TIMEOUT')
    expect(wrapper.text()).toContain('AI 明确失败可立即接管')
    expect(wrapper.find('.cps-review-takeover').exists()).toBe(true)
  })

  it('renders AI result items and adjudicates REJECT with relation tag', async () => {
    const wrapper = await mountView(
      {
        state_view: 'completed',
        can_take_over: false,
        seconds_until_takeover: null,
        result: baseResult,
        items: baseItems,
      },
      {
        state_view: 'completed',
        can_take_over: false,
        seconds_until_takeover: null,
        result: baseResult,
        items: baseItems,
        adjudication: {
          id: 61,
          issueId: 301,
          versionNo: 1,
          reviewerEmpNo: 'EMP3072',
          decision: 'REJECT',
          aiOverall: 'PASS',
          aiRelation: 'AGAINST_AI',
          reason: '短期措施长度不足，退回补充',
          createdAt: '2026-09-30 10:15:00',
        },
      },
    )
    mocks.adjudicateReview.mockResolvedValueOnce({ issueId: 301, status: 'PENDING_RECTIFY' })

    // AI 意见：总体结论 + 逐项（§29）
    expect(wrapper.text()).toContain('总体结论')
    expect(wrapper.text()).toContain('通过')
    expect(wrapper.text()).toContain('短期措施')
    expect(wrapper.text()).toContain('字段长度不足（L≥15）')
    expect(wrapper.text()).toContain('问题片段：加强点检')
    // 同向/反向预览（APPROVE=与 PASS 同向；REJECT=反向）
    expect(wrapper.text()).toContain('通过=与 AI 同向；退回=与 AI 反向')

    await wrapper.get('.cps-review-decide__reason textarea').setValue('短期措施长度不足，退回补充')
    await wrapper.get('.cps-review-decide__reject').trigger('click')
    await flushPromises()

    expect(mocks.adjudicateReview).toHaveBeenCalledWith(301, 'REJECT', '短期措施长度不足，退回补充')
    // 裁决留痕 + 反向标记（AI 只供意见）
    expect(wrapper.text()).toContain('裁决留痕')
    expect(wrapper.text()).toContain('不通过退回整改')
    expect(wrapper.text()).toContain('与 AI 反向')
    expect(wrapper.text()).toContain('事件留痕')
  })

  it('marks late AI result as trace-only and hides decide panel after adjudication', async () => {
    const wrapper = await mountView({
      state_view: 'late_result',
      can_take_over: false,
      seconds_until_takeover: null,
      result: { ...baseResult, isLate: true },
      items: [],
      adjudication: {
        id: 62,
        issueId: 301,
        versionNo: 1,
        reviewerEmpNo: 'EMP3072',
        decision: 'APPROVE',
        aiOverall: null,
        aiRelation: 'NO_AI_RESULT',
        reason: 'AI 超时，人工审核通过',
        createdAt: '2026-09-30 10:20:00',
      },
    })

    // 迟到结果仅留痕不覆盖（PRD §28.4）
    expect(wrapper.text()).toContain('迟到留痕')
    expect(wrapper.text()).toContain('不覆盖已作出的裁决')
    expect(wrapper.text()).toContain('通过并关闭')
    expect(wrapper.text()).toContain('无 AI 结果')
    // 已裁决：不再显示裁决操作区
    expect(wrapper.find('.cps-review-decide').exists()).toBe(false)
  })
})
