import { flushPromises, mount } from '@vue/test-utils'

import CategorySelector from '../CategorySelector.vue'

const masterMocks = vi.hoisted(() => ({
  getCategories: vi.fn(),
}))

vi.mock('@/api/cps/master', () => ({
  getCategories: masterMocks.getCategories,
}))

describe('CategorySelector', () => {
  beforeEach(() => {
    masterMocks.getCategories.mockImplementation((parentId?: number) => {
      if (parentId === 100) {
        return Promise.resolve([
          { value: 101, label: 'Missing Label' },
          { value: 102, label: 'Mixed Materials' },
        ])
      }

      return Promise.resolve([
        { value: 100, label: 'Site 5S' },
        { value: 200, label: 'Quality Issue' },
      ])
    })
  })

  it('uses button radios for two-level category selection', async () => {
    const model = {
      categoryL1Id: null as number | null,
      categoryL2Id: null as number | null,
    }
    const wrapper = mount(CategorySelector, { props: { modelValue: model } })
    await flushPromises()

    const level1 = wrapper.get('[role="radiogroup"][aria-label="一级分类"]')
    const level1Radios = level1.findAll('[role="radio"]')
    expect(level1Radios).toHaveLength(2)

    await level1Radios[0].trigger('click')
    await flushPromises()

    const level2 = wrapper.get('[role="radiogroup"][aria-label="二级分类"]')
    const level2Radios = level2.findAll('[role="radio"]')
    expect(model.categoryL1Id).toBe(100)
    expect(level2Radios).toHaveLength(2)

    await level2Radios[1].trigger('click')
    await flushPromises()

    expect(model.categoryL2Id).toBe(102)
    expect(level2Radios[1].attributes('aria-checked')).toBe('true')
  })
})
