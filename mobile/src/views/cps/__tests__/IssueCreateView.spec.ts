import { flushPromises, mount } from '@vue/test-utils'

import type { CpsUploadedImage } from '@/types/cps'
import IssueCreateView from '../IssueCreateView.vue'

const mocks = vi.hoisted(() => ({
  inspectImage: vi.fn(),
  transcribeVoice: vi.fn(),
}))

vi.mock('@/api/cps/issue', () => ({
  createCpsIssue: vi.fn(),
}))

vi.mock('@/api/cps/ai', () => ({
  inspectCpsImage: mocks.inspectImage,
  transcribeIssueVoice: mocks.transcribeVoice,
}))

vi.mock('@/api/cps/master', () => ({
  getFeedbackHandler: vi.fn(),
}))

describe('IssueCreateView', () => {
  beforeEach(() => {
    mocks.inspectImage.mockReset()
    mocks.transcribeVoice.mockReset()
    mocks.transcribeVoice.mockResolvedValue('')
  })

  it('shows submit disabled when no issue image exists', () => {
    const wrapper = mount(IssueCreateView, {
      global: {
        stubs: ['LocationSelector', 'CategorySelector', 'ImageUploader'],
      },
    })

    expect(wrapper.get('[data-test="submit"]').attributes('disabled')).toBeDefined()
  })

  it('keeps submit disabled when more than five issue images exist', () => {
    const wrapper = mount(IssueCreateView, {
      global: {
        stubs: ['LocationSelector', 'CategorySelector', 'ImageUploader'],
      },
    })

    const view = wrapper.vm as unknown as { images: CpsUploadedImage[] }
    view.images = Array.from({ length: 6 }, (_, index) => ({
      id: index + 1,
      url: `/image-${index + 1}.jpg`,
      name: `image-${index + 1}.jpg`,
    }))

    expect(wrapper.get('[data-test="submit"]').attributes('disabled')).toBeDefined()
  })

  it('prefills category and AI suggestions after the first image is ready', async () => {
    mocks.inspectImage.mockResolvedValue({
      sourceAttachmentId: 9,
      aiCategoryL1Id: 11,
      aiCategoryL1Name: 'Equipment',
      aiCategoryL2Id: 12,
      aiCategoryL2Name: 'Oil Leak',
      reasonSuggestion: 'Seal is aging',
      measureSuggestion: 'Replace the seal',
      modelName: 'cps-ai',
      modelVersion: '1',
      rawRequest: null,
      rawResponse: null,
      confidence: '0.91',
    })

    const wrapper = mount(IssueCreateView, {
      global: {
        stubs: ['LocationSelector', 'CategorySelector', 'ImageUploader'],
      },
    })

    await (wrapper.vm as unknown as { onFirstImageReady: (fileId: number) => unknown }).onFirstImageReady(9)

    const view = wrapper.vm as unknown as {
      category: { categoryL1Id: number | null; categoryL2Id: number | null }
      aiSuggestionSummary: string
    }
    expect(mocks.inspectImage).toHaveBeenCalledWith(9)
    expect(view.category).toMatchObject({ categoryL1Id: 11, categoryL2Id: 12 })
    expect(view.aiSuggestionSummary).toContain('Seal is aging')
    expect(view.aiSuggestionSummary).toContain('Replace the seal')
  })

  it('uses a compact voice icon button to append transcribed text', async () => {
    mocks.transcribeVoice.mockResolvedValue('Guard is loose')
    const wrapper = mount(IssueCreateView, {
      global: {
        stubs: ['LocationSelector', 'CategorySelector', 'ImageUploader'],
      },
    })

    const button = wrapper.get('[data-test="voice-transcribe"]')
    expect(button.attributes('aria-label')).toBe('语音转文字')
    expect(button.text()).toBe('')

    await button.trigger('click')
    await flushPromises()

    expect((wrapper.vm as unknown as { description: string }).description).toBe('Guard is loose')
  })
})
