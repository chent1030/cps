import { mount } from '@vue/test-utils'

import ActionPanel from '../ActionPanel.vue'

describe('ActionPanel', () => {
  it('renders only backend available actions', () => {
    const wrapper = mount(ActionPanel, {
      props: {
        status: 'PENDING_FEEDBACK',
        availableActions: ['REPLY_ASSIGN', 'TRANSFER'],
      },
    })

    expect(wrapper.text()).toContain('回复并指派')
    expect(wrapper.text()).toContain('转办')
    expect(wrapper.text()).not.toContain('审核关闭')
  })

  it('emits selected action', async () => {
    const wrapper = mount(ActionPanel, {
      props: {
        status: 'PENDING_REVIEW',
        availableActions: ['REVIEW_CLOSE'],
      },
    })

    await wrapper.get('button').trigger('click')

    expect(wrapper.emitted('action')).toEqual([[ 'REVIEW_CLOSE' ]])
  })
})
