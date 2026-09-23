import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

import type { CpsIssueDetail } from '@/types/cps'
import IssueDetailView from '../IssueDetailView.vue'

const mocks = vi.hoisted(() => ({
  executeCpsIssueAction: vi.fn(),
  getCpsIssueDetail: vi.fn(),
  transcribeSpeech: vi.fn(),
}))

vi.mock('@/api/cps/issue', () => ({
  executeCpsIssueAction: mocks.executeCpsIssueAction,
  getCpsIssueDetail: mocks.getCpsIssueDetail,
}))

vi.mock('@/api/cps/speech', () => ({
  transcribeSpeech: mocks.transcribeSpeech,
}))

vi.mock('@dcloudio/uni-app', () => ({
  onLoad: (callback: (query: Record<string, string>) => void) => callback({ id: '7' }),
}))

vi.mock('vant', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vant')>()
  return {
    ...actual,
    showImagePreview: vi.fn(),
  }
})

const rectifyDetail: CpsIssueDetail = {
  id: 7,
  status: 'PENDING_RECTIFY',
  factoryName: '一厂',
  areaName: '注塑区',
  lineName: 'A1 拉线',
  processName: '外观检查',
  categoryL1Name: '现场 5S',
  categoryL2Name: '标识缺失',
  description: '注塑区周转箱缺少状态标识。',
  currentHandlerEmpNo: 'E10023',
  submitTime: '2026-09-30 09:10',
  overdue: false,
  creatorEmpNo: 'E09999',
  feedbackEmpNo: 'E10001',
  responsibleEmpNo: 'E10023',
  proofEmpNo: null,
  reviewerEmpNo: null,
  reasonAnalysis: '班组交接确认不足。',
  correctiveMeasure: '补充状态标识并复核。',
  rectifyRemark: null,
  reviewOpinion: null,
  closeTime: null,
  issueAttachments: [
    { id: 501, fileUrl: '/issue-1.jpg', fileName: '现场问题-1.jpg' },
  ],
  proofAttachments: [
    { id: 601, fileUrl: '/proof-1.jpg', fileName: '整改凭证-1.jpg' },
  ],
  aiSuggestion: {
    sourceAttachmentId: 501,
    aiCategoryL1Id: 100,
    aiCategoryL1Name: '现场 5S',
    aiCategoryL2Id: 101,
    aiCategoryL2Name: '标识缺失',
    reasonSuggestion: '状态标识未及时补充。',
    measureSuggestion: '补齐标识并纳入点检。',
    modelName: 'mock-vision',
    modelVersion: '1.0',
    rawRequest: null,
    rawResponse: null,
    confidence: '0.8800',
  },
  availableActions: ['SUBMIT_RECTIFICATION', 'SAVE_DRAFT', 'TRANSFER'],
  flowLogs: [
    {
      action: 'SUBMIT',
      operatorEmpNo: 'E09999',
      fromStatus: null,
      toStatus: 'PENDING_FEEDBACK',
      comment: '现场稽查提交',
      createdAt: '2026-09-30 09:10',
    },
  ],
} as CpsIssueDetail

describe('IssueDetailView V2 整改流程与 §31 精简', () => {
  beforeEach(() => {
    mocks.executeCpsIssueAction.mockReset()
    mocks.getCpsIssueDetail.mockReset()
    mocks.transcribeSpeech.mockReset()
    mocks.getCpsIssueDetail.mockResolvedValue(rectifyDetail)
    mocks.executeCpsIssueAction.mockResolvedValue({
      issueId: 7,
      status: 'PENDING_AI_REVIEW',
      availableActions: [],
    })
  })

  it('renders §31 simplified detail with merged AI suggestion and hidden legacy cards', async () => {
    const wrapper = mount(IssueDetailView, {
      global: { stubs: ['ActionPanel', 'ImageUploader'] },
    })
    await flushPromises()

    // §31：AI 原因+AI 措施合并为「AI 整改建议」
    expect(wrapper.text()).toContain('AI 整改建议')
    expect(wrapper.text()).toContain('状态标识未及时补充。；补齐标识并纳入点检。')
    // §31：复核照片卡片 / 底部节点卡片不显示（隐藏不删除）
    expect(wrapper.text()).not.toContain('复核照片')
    expect(wrapper.text()).not.toContain('处理轨迹')
    // §31：基础信息仅留稽查人姓名+稽查时间
    expect(wrapper.text()).toContain('稽查人')
    expect(wrapper.text()).toContain('稽查时间')
    expect(wrapper.text()).not.toContain('当前处理人')
    expect(wrapper.text()).not.toContain('上传人')
    // 问题现场照片保留
    expect(wrapper.text()).toContain('问题现场')
  })

  it('shows V2 three-field rectification form with voice inputs and submits payload', async () => {
    const wrapper = mount(IssueDetailView, {
      global: { stubs: ['ActionPanel', 'ImageUploader'] },
    })
    await flushPromises()

    // §28 V2：三字段 + 语音按钮（按住说话）+ 责任员工 + 整改照片
    expect(wrapper.text()).toContain('提交整改（AI 初审）')
    expect(wrapper.text()).toContain('暂存')
    expect(wrapper.text()).toContain('触发 AI 初审')
    const holdButtons = wrapper.findAll('.cps-voice__hold')
    expect(holdButtons).toHaveLength(3)
    expect(wrapper.text()).toContain('原因分析')
    expect(wrapper.text()).toContain('短期措施')
    expect(wrapper.text()).toContain('长期措施')
    expect(wrapper.find('[data-testid="rectification-responsible-field"]').exists()).toBe(true)

    const textareas = wrapper.findAll('.cps-voice__input textarea')
    await textareas[0].setValue('周转箱缺少状态标识卡')
    await textareas[1].setValue('两周内补齐所有周转箱状态标识卡')
    await textareas[2].setValue('修订点检表并纳入月度审核')

    await wrapper
      .findAll('[data-testid="workflow-action"]')
      .find((button) => button.text().includes('提交整改'))!
      .trigger('click')
    await flushPromises()

    expect(mocks.executeCpsIssueAction).toHaveBeenCalledTimes(1)
    const payload = mocks.executeCpsIssueAction.mock.calls[0][1] as Record<string, unknown>
    expect(payload.action).toBe('SUBMIT_RECTIFICATION')
    expect(payload.reasonAnalysis).toBe('周转箱缺少状态标识卡')
    expect(payload.shortTermMeasure).toBe('两周内补齐所有周转箱状态标识卡')
    expect(payload.longTermMeasure).toBe('修订点检表并纳入月度审核')
    expect(payload).toHaveProperty('proofAttachmentIds')
  })
})
