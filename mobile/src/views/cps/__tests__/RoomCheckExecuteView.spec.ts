import { flushPromises, mount } from '@vue/test-utils'

import type { CpsRoomCheckRecord } from '@/types/cps'
import RoomCheckExecuteView from '../RoomCheckExecuteView.vue'

const mocks = vi.hoisted(() => ({
  getRoomCheckRecord: vi.fn(),
  uploadRoomCheckPhoto: vi.fn(),
  submitRoomCheckRecord: vi.fn(),
}))

vi.mock('@/api/cps/roomCheck', () => ({
  listRoomCheckTasks: vi.fn(),
  listRooms: vi.fn(),
  startRoomCheck: vi.fn(),
  getRoomCheckRecord: mocks.getRoomCheckRecord,
  uploadRoomCheckPhoto: mocks.uploadRoomCheckPhoto,
  submitRoomCheckRecord: mocks.submitRoomCheckRecord,
}))

vi.mock('@dcloudio/uni-app', () => ({
  onLoad: (callback: (query: Record<string, string>) => void) => callback({ recordId: '901' }),
}))

const baseItem = {
  id: 1,
  checkItemId: 101,
  itemCode: 'RC-001',
  content: '配电柜前绝缘垫完好',
  photoCategory: '配电柜全景',
  deductScore: 10,
  configVersion: 2,
  photoObjectKey: null,
  photoUrl: null,
  photoFileName: null,
  judgeResult: 'PENDING' as const,
  judgeReason: null,
  finalResult: null,
}

const baseRecord: CpsRoomCheckRecord = {
  id: 901,
  planTaskId: 11,
  roomId: 77,
  roomCode: 'RM-101',
  roomName: '一号配电房',
  checkEmpNo: 'DEMO_EMP',
  recordStatus: 'IN_PROGRESS',
  judgeStatus: null,
  score: null,
  startedAt: '2026-09-27 08:10',
  submittedAt: null,
  items: [baseItem],
}

describe('RoomCheckExecuteView', () => {
  beforeEach(() => {
    mocks.getRoomCheckRecord.mockReset()
    mocks.uploadRoomCheckPhoto.mockReset()
    mocks.submitRoomCheckRecord.mockReset()
    ;(globalThis as unknown as { uni: Record<string, unknown> }).uni = {
      navigateTo: vi.fn(),
    }
  })

  it('renders items with photo category, deduct score and pending hint', async () => {
    mocks.getRoomCheckRecord.mockResolvedValue(baseRecord)

    const wrapper = mount(RoomCheckExecuteView)
    await flushPromises()

    expect(wrapper.text()).toContain('一号配电房')
    expect(wrapper.text()).toContain('配电柜前绝缘垫完好')
    expect(wrapper.text()).toContain('配电柜全景')
    expect(wrapper.text()).toContain('不合格扣分')
    expect(wrapper.text()).toContain('还有 1 项未拍照')
    expect(wrapper.text()).toContain('不可跳过')
  })

  it('uploads photo via hidden file input and refreshes record', async () => {
    mocks.getRoomCheckRecord.mockResolvedValue(baseRecord)
    const uploaded = { ...baseItem, photoUrl: '/room/photo-1.jpg', photoObjectKey: 'cps/room-check/x.jpg' }
    mocks.uploadRoomCheckPhoto.mockResolvedValue({ ...baseRecord, items: [uploaded] })

    const wrapper = mount(RoomCheckExecuteView)
    await flushPromises()

    await wrapper.get('.cps-exec-card__capture').trigger('click')
    // 隐藏 file input 由组件按需 createElement 挂到 document.body（模板 <input> 会被 uni 编译成 uni-input）
    const input = document.querySelector<HTMLInputElement>('input[type="file"]')
    expect(input).not.toBeNull()
    const file = new File(['bytes'], 'photo.jpg', { type: 'image/jpeg' })
    Object.defineProperty(input, 'files', { value: [file] })
    input?.dispatchEvent(new Event('change'))
    await flushPromises()

    expect(mocks.uploadRoomCheckPhoto).toHaveBeenCalledWith(
      901,
      1,
      expect.objectContaining({ name: 'photo.jpg' }),
    )
    expect(wrapper.text()).not.toContain('还有 1 项未拍照')
  })

  it('shows degraded PENDING state after submit when judge service unavailable', async () => {
    mocks.getRoomCheckRecord.mockResolvedValue({
      ...baseRecord,
      items: [{ ...baseItem, photoUrl: '/room/photo-1.jpg', photoObjectKey: 'cps/room-check/x.jpg' }],
    })
    mocks.submitRoomCheckRecord.mockResolvedValue({
      ...baseRecord,
      recordStatus: 'JUDGED',
      judgeStatus: 'PENDING',
      score: null,
      submittedAt: '2026-09-27 08:30',
      items: [
        {
          ...baseItem,
          photoUrl: '/room/photo-1.jpg',
          photoObjectKey: 'cps/room-check/x.jpg',
          judgeResult: 'PENDING',
          judgeReason: 'judge service unavailable: degraded to PENDING (J-line will re-judge)',
          finalResult: 'PENDING',
        },
      ],
    })

    const wrapper = mount(RoomCheckExecuteView)
    await flushPromises()

    await wrapper.get('.cps-exec-footer__submit').trigger('click')
    await flushPromises()

    expect(mocks.submitRoomCheckRecord).toHaveBeenCalledWith(901)
    expect(wrapper.text()).toContain('分数待判定（降级）')
    expect(wrapper.text()).toContain('判定服务暂不可用')
    expect(wrapper.text()).toContain('J 线补判定')
    // JUDGED 后锁定：拍照按钮隐藏
    expect(wrapper.find('.cps-exec-card__capture').exists()).toBe(false)
    expect(wrapper.find('.cps-exec-footer').exists()).toBe(false)
  })

  it('keeps page actionable when submit blocked by retake requirement', async () => {
    const withPhoto = {
      ...baseRecord,
      items: [{ ...baseItem, photoUrl: '/room/photo-1.jpg', photoObjectKey: 'cps/room-check/x.jpg' }],
    }
    mocks.getRoomCheckRecord.mockResolvedValueOnce(withPhoto)
    mocks.submitRoomCheckRecord.mockRejectedValue(
      new Error('retake required (not scored as unqualified, PRD 24.2.3): RC-001:TYPE_MISMATCH'),
    )
    // 阻断后重拉：逐项 judgeResult 已回写
    mocks.getRoomCheckRecord.mockResolvedValueOnce({
      ...withPhoto,
      items: [
        {
          ...baseItem,
          photoUrl: '/room/photo-1.jpg',
          photoObjectKey: 'cps/room-check/x.jpg',
          judgeResult: 'TYPE_MISMATCH',
          judgeReason: 'photo category does not match item config',
        },
      ],
    })

    const wrapper = mount(RoomCheckExecuteView)
    await flushPromises()

    await wrapper.get('.cps-exec-footer__submit').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('retake required')
    expect(wrapper.text()).toContain('类型不符')
    expect(wrapper.text()).toContain('须重拍')
    expect(wrapper.text()).toContain('1 项须重拍/补拍后方可提交')
    // 未 JUDGED：仍可重拍
    expect(wrapper.find('.cps-exec-card__capture').exists()).toBe(true)
  })
})
