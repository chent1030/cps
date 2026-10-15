import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'

import { getCurrentUser, setCurrentRole } from '@/api/cps/userStore'
import IssueCreateView from '../IssueCreateView.vue'
import IssueListView from '../IssueListView.vue'
import InitialReviewView from '../InitialReviewView.vue'

// 屏蔽 uni-app 生命周期，避免 onLoad 在 jsdom 下报 injectHook 错误
vi.mock('@dcloudio/uni-app', () => ({
  onLoad: (_callback: (query?: Record<string, string>) => void) => {
    /* stub */
  },
}))

const mocks = vi.hoisted(() => ({
  listCpsIssues: vi.fn().mockResolvedValue([]),
  getInitialReviewView: vi.fn(),
  takeOverInitialReview: vi.fn(),
  adjudicateReview: vi.fn(),
  createCpsIssue: vi.fn(),
  inspectCpsImage: vi.fn(),
  transcribeIssueVoice: vi.fn().mockResolvedValue(''),
  getFeedbackHandler: vi.fn(),
  getCategories: vi.fn().mockResolvedValue([]),
  getFactories: vi.fn().mockResolvedValue([]),
  navigateTo: vi.fn(),
  uploadCpsAttachment: vi.fn(),
  getInitialReviewList: vi.fn().mockResolvedValue([]),
}))

vi.mock('@/api/cps/issue', () => ({
  listCpsIssues: mocks.listCpsIssues,
  createCpsIssue: mocks.createCpsIssue,
}))

vi.mock('@/api/cps/initialReview', () => ({
  getInitialReviewView: mocks.getInitialReviewView,
  takeOverInitialReview: mocks.takeOverInitialReview,
  adjudicateReview: mocks.adjudicateReview,
  getInitialReviewList: mocks.getInitialReviewList,
}))

vi.mock('@/api/cps/ai', () => ({
  inspectCpsImage: mocks.inspectCpsImage,
  transcribeIssueVoice: mocks.transcribeIssueVoice,
}))

vi.mock('@/api/cps/master', () => ({
  getFeedbackHandler: mocks.getFeedbackHandler,
  getCategories: mocks.getCategories,
  getFactories: mocks.getFactories,
}))

vi.mock('@/api/cps/attachment', () => ({
  uploadCpsAttachment: mocks.uploadCpsAttachment,
}))

describe('B5 role-gated views', () => {
  it('inspector 看不到 IssueListView 的「新建」/「初审裁决」/「评分排名」入口，但可见点检任务', () => {
    setCurrentRole('inspector')
    ;(globalThis as unknown as { uni: Pick<UniApp.Uni, 'navigateTo'> }).uni = {
      navigateTo: mocks.navigateTo,
    }

    const wrapper = mount(IssueListView, {
      global: {
        stubs: {
          'cps-issue-card': true,
        },
      },
    })

    const entryButtons = wrapper.findAll('.cps-list-hero__entry')
    const labels = entryButtons.map((b) => b.text().trim())
    expect(labels).toContain('点检任务')
    expect(labels).not.toContain('新建')
    expect(labels).not.toContain('初审裁决')
    expect(labels).not.toContain('评分排名')
  })

  it('admin 在 IssueListView 看到全部四入口', () => {
    setCurrentRole('admin')
    ;(globalThis as unknown as { uni: Pick<UniApp.Uni, 'navigateTo'> }).uni = {
      navigateTo: mocks.navigateTo,
    }

    const wrapper = mount(IssueListView, {
      global: {
        stubs: {
          'cps-issue-card': true,
        },
      },
    })

    const labels = wrapper.findAll('.cps-list-hero__entry').map((b) => b.text().trim())
    expect(labels).toEqual(expect.arrayContaining(['新建', '点检任务', '初审裁决', '评分排名']))
  })

  it('inspector 进入 IssueCreateView 仅看到越权提示，不渲染问题表单', () => {
    setCurrentRole('inspector')
    expect(getCurrentUser().role).toBe('inspector')

    const wrapper = mount(IssueCreateView, {
      global: {
        stubs: {
          'cps-uploader': true,
          'cps-location-selector': true,
          'cps-category-selector': true,
        },
      },
    })

    expect(wrapper.text()).toContain('仅审核员可创建')
    expect(wrapper.find('form').exists()).toBe(false)
  })

  it('inspector 进入 InitialReviewView 仅看到越权提示，不渲染三态卡片', () => {
    setCurrentRole('inspector')
    expect(getCurrentUser().role).toBe('inspector')

    const wrapper = mount(InitialReviewView, {
      global: {
        stubs: {
          'cps-review-card': true,
        },
      },
    })

    expect(wrapper.text()).toContain('仅审核员可访问')
    expect(wrapper.find('.cps-review-state').exists()).toBe(false)
  })
})