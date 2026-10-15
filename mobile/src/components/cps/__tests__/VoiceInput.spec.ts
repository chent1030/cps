import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import VoiceInput from '../VoiceInput.vue'

const mocks = vi.hoisted(() => ({
  transcribeSpeech: vi.fn(),
}))

vi.mock('@/api/cps/speech', () => mocks)

/** 最小 MediaRecorder 桩：start 记状态，stop 同步触发 onstop（jsdom 无真实录音）。 */
class FakeRecorder {
  static last: FakeRecorder | null = null
  mimeType = 'audio/webm'
  ondataavailable: ((event: { data: Blob }) => void) | null = null
  onstop: (() => void) | null = null
  stopped = false
  constructor(public stream: MediaStream) {
    FakeRecorder.last = this
  }
  start() { /* noop */ }
  stop() {
    if (this.stopped) return
    this.stopped = true
    this.ondataavailable?.({ data: new Blob(['audio-bytes'], { type: 'audio/webm' }) })
    this.onstop?.()
  }
}

const fakeStream = {
  getTracks: () => [{ stop: () => undefined }],
} as unknown as MediaStream

const originalMediaDevices = Object.getOwnPropertyDescriptor(navigator, 'mediaDevices')

describe('VoiceInput', () => {
  beforeEach(() => {
    mocks.transcribeSpeech.mockReset()
    FakeRecorder.last = null
    Object.defineProperty(navigator, 'mediaDevices', {
      configurable: true,
      value: { getUserMedia: vi.fn().mockResolvedValue(fakeStream) },
    })
    vi.stubGlobal('MediaRecorder', FakeRecorder)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    if (originalMediaDevices) {
      Object.defineProperty(navigator, 'mediaDevices', originalMediaDevices)
    }
  })

  it('degrades to manual input when recording is unsupported', async () => {
    Object.defineProperty(navigator, 'mediaDevices', { configurable: true, value: undefined })
    vi.stubGlobal('MediaRecorder', undefined)

    const wrapper = mount(VoiceInput, { props: { field: 'reason', label: '原因分析', modelValue: '' } })
    await wrapper.get('.cps-voice__hold').trigger('pointerdown')
    await flushPromises()

    expect(wrapper.text()).toContain('当前环境不支持录音，请手工输入')
    expect(wrapper.get('.cps-voice__hold').text()).toContain('不支持录音')
    expect(mocks.transcribeSpeech).not.toHaveBeenCalled()
  })

  it('fills field text after hold-to-record transcription (PRD §20.2)', async () => {
    mocks.transcribeSpeech.mockResolvedValueOnce({
      status: 'TRANSCRIBED',
      text: '周转箱缺少状态标识卡，影响状态识别',
      fallbackMessage: null,
      field: 'reason',
      attempt: 1,
    })
    const wrapper = mount(VoiceInput, {
      props: { field: 'reason', label: '原因分析', modelValue: '' },
    })

    await wrapper.get('.cps-voice__hold').trigger('pointerdown')
    await flushPromises()
    // 录音中：松开结束
    expect(wrapper.get('.cps-voice__hold').text()).toContain('松开结束')

    await wrapper.get('.cps-voice__hold').trigger('pointerup')
    await flushPromises()

    expect(mocks.transcribeSpeech).toHaveBeenCalledTimes(1)
    const [file, field, submissionId, attempt] = mocks.transcribeSpeech.mock.calls[0]
    expect(file).toBeInstanceOf(File)
    expect((file as File).name).toBe('reason-1.webm')
    expect(field).toBe('reason')
    expect(submissionId).toBeUndefined()
    expect(attempt).toBe(1)
    // 回填文本 + 编辑确认提示（语音不作点检证据）
    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual(['周转箱缺少状态标识卡，影响状态识别'])
    expect(wrapper.text()).toContain('已回填原因分析，请编辑确认后提交（语音不作点检证据）')
  })

  it('shows fallback message on SKIPPED and recovers via retry', async () => {
    mocks.transcribeSpeech
      .mockResolvedValueOnce({ status: 'SKIPPED', text: '', fallbackMessage: '未识别到有效语音内容，可重试或手工输入', field: 'short_term', attempt: 1 })
      .mockResolvedValueOnce({ status: 'TRANSCRIBED', text: '两周内补齐周转箱状态标识', fallbackMessage: null, field: 'short_term', attempt: 2 })
    const wrapper = mount(VoiceInput, {
      props: { field: 'short_term', label: '短期措施', modelValue: '' },
    })

    await wrapper.get('.cps-voice__hold').trigger('pointerdown')
    await wrapper.get('.cps-voice__hold').trigger('pointerup')
    await flushPromises()

    expect(wrapper.text()).toContain('未识别到有效语音内容，可重试或手工输入')
    expect(wrapper.find('.cps-voice__retry').exists()).toBe(true)

    await wrapper.get('.cps-voice__retry').trigger('click')
    await flushPromises()

    // 重试 attempt 递增（幂等键 speech-{submissionId}-{field}-{attempt}）
    expect(mocks.transcribeSpeech).toHaveBeenCalledTimes(2)
    expect(mocks.transcribeSpeech.mock.calls[1][3]).toBe(2)
    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual(['两周内补齐周转箱状态标识'])
  })

  it('falls back to manual input when transcription request fails', async () => {
    mocks.transcribeSpeech.mockRejectedValueOnce(new Error('network down'))
    const wrapper = mount(VoiceInput, {
      props: { field: 'long_term', label: '长期措施', modelValue: '既有内容保留' },
    })

    await wrapper.get('.cps-voice__hold').trigger('pointerdown')
    await wrapper.get('.cps-voice__hold').trigger('pointerup')
    await flushPromises()

    expect(wrapper.text()).toContain('转写服务暂不可用，可重试或手工输入')
    // 失败不清空既有手工内容（失败允许重试或手工输入）
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })
})
