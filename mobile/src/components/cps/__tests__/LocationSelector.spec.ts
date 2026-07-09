import { flushPromises, mount } from '@vue/test-utils'

import LocationSelector from '../LocationSelector.vue'

const masterMocks = vi.hoisted(() => ({
  getFactories: vi.fn(),
  getAreas: vi.fn(),
  getLines: vi.fn(),
  getProcesses: vi.fn(),
}))

vi.mock('@/api/cps/master', () => ({
  getFactories: masterMocks.getFactories,
  getAreas: masterMocks.getAreas,
  getLines: masterMocks.getLines,
  getProcesses: masterMocks.getProcesses,
}))

describe('LocationSelector', () => {
  beforeEach(() => {
    masterMocks.getFactories.mockResolvedValue([
      { value: 'Factory A', label: 'Factory A' },
      { value: 'Factory B', label: 'Factory B' },
    ])
    masterMocks.getAreas.mockResolvedValue([])
    masterMocks.getLines.mockResolvedValue([])
    masterMocks.getProcesses.mockResolvedValue([])
  })

  it('uses text values from area person config for factory selection', async () => {
    const model = {
      factory: '',
      area: '',
      line: '',
      process: '',
    }
    const wrapper = mount(LocationSelector, { props: { modelValue: model } })
    await flushPromises()

    const radioGroup = wrapper.get('[role="radiogroup"]')
    const radios = radioGroup.findAll('[role="radio"]')
    expect(radios).toHaveLength(2)

    await radios[1].trigger('click')
    await flushPromises()

    expect(model.factory).toBe('Factory B')
    expect(masterMocks.getAreas).toHaveBeenCalledWith('Factory B')
    expect(radios[1].attributes('aria-checked')).toBe('true')
  })
})
