import { flushPromises, mount } from '@vue/test-utils'

import type { CpsIssueDetail } from '@/types/cps'
import IssueDetailView from '../IssueDetailView.vue'

const mocks = vi.hoisted(() => ({
  executeCpsIssueAction: vi.fn(),
  getCpsIssueDetail: vi.fn(),
  showImagePreview: vi.fn(),
}))

vi.mock('@/api/cps/issue', () => ({
  executeCpsIssueAction: mocks.executeCpsIssueAction,
  getCpsIssueDetail: mocks.getCpsIssueDetail,
}))

vi.mock('@dcloudio/uni-app', () => ({
  onLoad: (callback: (query: Record<string, string>) => void) => callback({ id: '1' }),
}))

vi.mock('vant', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vant')>()
  return {
    ...actual,
    showImagePreview: mocks.showImagePreview,
  }
})

const detail: CpsIssueDetail = {
  id: 1,
  issueNo: 'CPS20260627001',
  status: 'PENDING_REVIEW',
  factoryName: '一厂',
  areaName: '注塑区',
  lineName: 'A1 拉线',
  processName: '外观检查',
  categoryL1Name: '现场 5S',
  categoryL2Name: '标识缺失',
  description: '注塑区周转箱缺少状态标识。',
  currentHandlerEmpNo: 'E90001',
  submitTime: '2026-06-27 09:10',
  overdue: true,
  creatorEmpNo: 'E09999',
  feedbackEmpNo: 'E10001',
  responsibleEmpNo: 'E10023',
  proofEmpNo: 'E10024',
  reviewerEmpNo: 'E90001',
  reasonAnalysis: '班组交接确认不足。',
  correctiveMeasure: '补充状态标识并复核。',
  rectifyRemark: null,
  reviewOpinion: null,
  closeTime: null,
  issueAttachments: [
    {
      id: 501,
      fileUrl: '/issue-1.jpg',
      fileName: '现场问题-1.jpg',
    },
    {
      id: 502,
      fileUrl: '/issue-2.jpg',
      fileName: '现场问题-2.jpg',
    },
  ],
  proofAttachments: [
    {
      id: 601,
      fileUrl: '/proof-1.jpg',
      fileName: '整改凭证-1.jpg',
    },
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
  availableActions: ['REVIEW_CLOSE', 'REVIEW_REJECT'],
  flowLogs: [
    {
      action: 'SUBMIT',
      operatorEmpNo: 'E09999',
      fromStatus: null,
      toStatus: 'PENDING_FEEDBACK',
      comment: '现场稽查提交',
      createdAt: '2026-06-27 09:10',
    },
    {
      action: 'REVIEW_CLOSE',
      operatorEmpNo: 'E90001',
      fromStatus: 'PENDING_REVIEW',
      toStatus: 'CLOSED',
      comment: '复核通过',
      createdAt: '2026-06-27 11:20',
    },
  ],
}

describe('IssueDetailView', () => {
  beforeEach(() => {
    mocks.executeCpsIssueAction.mockReset()
    mocks.getCpsIssueDetail.mockReset()
    mocks.showImagePreview.mockReset()
    mocks.getCpsIssueDetail.mockResolvedValue(detail)
  })

  it('renders Chinese detail status and flow labels', async () => {
    const wrapper = mount(IssueDetailView, {
      global: {
        stubs: ['ActionPanel', 'ImageUploader'],
      },
    })

    await flushPromises()

    expect(wrapper.text()).toContain('待审核')
    expect(wrapper.text()).toContain('超时')
    expect(wrapper.text()).toContain('提交问题')
    expect(wrapper.text()).toContain('审核关闭')
    expect(wrapper.text()).not.toContain('PENDING_REVIEW')
    expect(wrapper.text()).not.toContain('REVIEW_CLOSE')
  })

  it('opens image preview from photo tiles', async () => {
    const wrapper = mount(IssueDetailView, {
      global: {
        stubs: ['ActionPanel', 'ImageUploader'],
      },
    })

    await flushPromises()
    await wrapper.get('.cps-photo-tile').trigger('click')

    expect(mocks.showImagePreview).toHaveBeenCalledWith({
      images: ['/issue-1.jpg', '/issue-2.jpg'],
      startPosition: 0,
      closeable: true,
    })
  })

  it('renders person selection fields in their own workflow stages', async () => {
    mocks.getCpsIssueDetail.mockResolvedValue({
      ...detail,
      status: 'PENDING_FEEDBACK',
      availableActions: ['REPLY_ASSIGN'],
    })
    const wrapper = mount(IssueDetailView)

    await flushPromises()

    expect(wrapper.find('[data-testid="responsible-employee-field"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="proof-employee-field"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="reviewer-employee-field"]').exists()).toBe(false)
  })

  it('provides both review decisions when the review action list is unavailable', async () => {
    mocks.getCpsIssueDetail.mockResolvedValue({
      ...detail,
      availableActions: [],
    })
    const wrapper = mount(IssueDetailView)

    await flushPromises()

    const actionLabels = wrapper.findAll('[data-testid="workflow-action"]').map((button) => button.text())
    expect(actionLabels).toContain('审核通过')
    expect(actionLabels).toContain('审核退回')
  })
})
