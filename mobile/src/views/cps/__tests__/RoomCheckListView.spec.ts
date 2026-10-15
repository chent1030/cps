import { flushPromises, mount } from '@vue/test-utils'

import RoomCheckListView from '../RoomCheckListView.vue'

const mocks = vi.hoisted(() => ({
  listRoomCheckTasks: vi.fn(),
  listRooms: vi.fn(),
  startRoomCheck: vi.fn(),
  navigateTo: vi.fn(),
}))

vi.mock('@/api/cps/roomCheck', () => ({
  listRoomCheckTasks: mocks.listRoomCheckTasks,
  listRooms: mocks.listRooms,
  startRoomCheck: mocks.startRoomCheck,
  uploadRoomCheckPhoto: vi.fn(),
  getRoomCheckRecord: vi.fn(),
  submitRoomCheckRecord: vi.fn(),
}))

describe('RoomCheckListView', () => {
  beforeEach(() => {
    mocks.listRoomCheckTasks.mockReset()
    mocks.listRooms.mockReset()
    mocks.startRoomCheck.mockReset()
    mocks.navigateTo.mockReset()
    ;(globalThis as unknown as { uni: Pick<UniApp.Uni, 'navigateTo'> }).uni = {
      navigateTo: mocks.navigateTo,
    }
  })

  it('renders tasks with room codes and progress', async () => {
    mocks.listRoomCheckTasks.mockResolvedValue([
      {
        taskId: 11,
        planId: 3,
        title: '配电房周点检',
        taskStatus: 'PENDING',
        targetEmpNo: 'DEMO_EMP',
        scheduledAt: '2026-09-27 08:00',
        frequency: '每周',
        roomCodes: ['RM-101', 'RM-102'],
        judgedRoomCount: 1,
      },
    ])

    const wrapper = mount(RoomCheckListView)
    await flushPromises()

    expect(wrapper.text()).toContain('配电房周点检')
    expect(wrapper.text()).toContain('待执行')
    expect(wrapper.text()).toContain('1/2')
    expect(wrapper.findAll('.cps-roomcheck-room')).toHaveLength(2)
  })

  it('starts room check by resolving roomCode to roomId then navigates', async () => {
    mocks.listRoomCheckTasks.mockResolvedValue([
      {
        taskId: 12,
        planId: 3,
        title: '泵房点检',
        taskStatus: 'PENDING',
        targetEmpNo: 'DEMO_EMP',
        scheduledAt: null,
        frequency: null,
        roomCodes: ['PUMP-1'],
        judgedRoomCount: 0,
      },
    ])
    mocks.listRooms.mockResolvedValue([
      { id: 77, roomCode: 'PUMP-1', roomName: '泵房' },
      { id: 78, roomCode: 'OTHER', roomName: '其它' },
    ])
    mocks.startRoomCheck.mockResolvedValue({ id: 901 })

    const wrapper = mount(RoomCheckListView)
    await flushPromises()

    await wrapper.get('.cps-roomcheck-room').trigger('click')
    await flushPromises()

    expect(mocks.startRoomCheck).toHaveBeenCalledWith({ planTaskId: 12, roomId: 77 })
    expect(mocks.navigateTo).toHaveBeenCalledWith({ url: '/views/cps/RoomCheckExecuteView?recordId=901' })
  })

  it('surfaces start failure without navigating', async () => {
    mocks.listRoomCheckTasks.mockResolvedValue([
      {
        taskId: 13,
        planId: 4,
        title: '空压机房点检',
        taskStatus: 'PENDING',
        targetEmpNo: 'DEMO_EMP',
        scheduledAt: null,
        frequency: null,
        roomCodes: ['AIR-1'],
        judgedRoomCount: 0,
      },
    ])
    mocks.listRooms.mockResolvedValue([])

    const wrapper = mount(RoomCheckListView)
    await flushPromises()

    await wrapper.get('.cps-roomcheck-room').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('未找到房间配置：AIR-1')
    expect(mocks.navigateTo).not.toHaveBeenCalled()
  })
})
