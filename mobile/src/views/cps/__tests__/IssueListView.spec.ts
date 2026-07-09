import { flushPromises, mount } from '@vue/test-utils'

import IssueListView from '../IssueListView.vue'

const mocks = vi.hoisted(() => ({
  listCpsIssues: vi.fn(),
  navigateTo: vi.fn(),
}))

vi.mock('@/api/cps/issue', () => ({
  listCpsIssues: mocks.listCpsIssues,
}))

describe('IssueListView', () => {
  beforeEach(() => {
    mocks.listCpsIssues.mockReset()
    mocks.navigateTo.mockReset()
    ;(globalThis as unknown as { uni: Pick<UniApp.Uni, 'navigateTo'> }).uni = {
      navigateTo: mocks.navigateTo,
    }
  })

  it('renders redesigned issue cards with Chinese status labels', async () => {
    mocks.listCpsIssues.mockResolvedValue([
      {
        id: 1,
        issueNo: 'CPS20260627001',
        status: 'PENDING_FEEDBACK',
        factoryName: '一厂',
        areaName: '注塑区',
        lineName: 'A1 拉线',
        processName: '外观检查',
        categoryL1Name: '现场 5S',
        categoryL2Name: '标识缺失',
        description: '周转箱缺少状态标识',
        currentHandlerEmpNo: 'E10001',
        submitTime: '2026-06-27 09:10',
        overdue: false,
      },
      {
        id: 2,
        issueNo: 'CPS20260626018',
        status: 'PENDING_UPLOAD_PROOF',
        factoryName: '二厂',
        areaName: '包装区',
        lineName: 'D1 拉线',
        processName: '装箱',
        categoryL1Name: '质量异常',
        categoryL2Name: '外观不良',
        description: '产品外壳轻微划伤',
        currentHandlerEmpNo: 'E20009',
        submitTime: '2026-06-26 15:32',
        overdue: true,
      },
    ])

    const wrapper = mount(IssueListView)
    await flushPromises()

    expect(wrapper.findAll('.cps-issue-card')).toHaveLength(2)
    expect(wrapper.text()).toContain('待反馈')
    expect(wrapper.text()).toContain('待传图')
    expect(wrapper.text()).toContain('超时')
    expect(wrapper.text()).toContain('周转箱缺少状态标识')
    expect(wrapper.text()).not.toContain('PENDING_FEEDBACK')
    expect(wrapper.text()).not.toContain('PENDING_UPLOAD_PROOF')
  })

  it('navigates with uni.navigateTo from list actions', async () => {
    mocks.listCpsIssues.mockResolvedValue([
      {
        id: 8,
        issueNo: 'CPS20260701001',
        status: 'PENDING_FEEDBACK',
        factory: '一厂',
        area: '注塑区',
        line: 'A1 拉线',
        process: '外观检查',
        categoryL1Name: '现场 5S',
        categoryL2Name: '标识缺失',
        description: '周转箱状态标识缺失',
        currentHandlerEmpNo: 'E10001',
        submitTime: '2026-07-01 09:10',
        overdue: false,
      },
    ])

    const wrapper = mount(IssueListView)
    await flushPromises()

    await wrapper.get('.cps-list-hero__action').trigger('click')
    expect(mocks.navigateTo).toHaveBeenCalledWith({ url: '/views/cps/IssueCreateView' })

    await wrapper.get('.cps-issue-card').trigger('click')
    expect(mocks.navigateTo).toHaveBeenCalledWith({ url: '/views/cps/IssueDetailView?id=8' })
  })
})
