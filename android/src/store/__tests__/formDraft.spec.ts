import { describe, expect, it, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

import { useFormDraftStore } from '@/store/formDraft'

describe('useFormDraftStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('初始为空', () => {
    const store = useFormDraftStore()
    expect(store.images).toEqual([])
    expect(store.description).toBe('')
    expect(store.location).toEqual({ factory: '', area: '', line: '', process: '' })
  })

  it('修改后持久（组件保活场景）', () => {
    const store = useFormDraftStore()
    store.description = '现场问题描述'
    store.location = { factory: 'Factory A', area: 'Injection', line: 'A1', process: 'Check' }
    store.feedbackEmpNo = 'E10001'

    // 模拟「再次访问」（同一 Pinia 实例）
    const store2 = useFormDraftStore()
    expect(store2.description).toBe('现场问题描述')
    expect(store2.location.factory).toBe('Factory A')
    expect(store2.feedbackEmpNo).toBe('E10001')
  })

  it('reset 清空所有字段', () => {
    const store = useFormDraftStore()
    store.description = 'temp'
    store.images = [{ id: 1, url: 'u', name: 'n' }]
    store.feedbackEmpNo = 'E10001'

    store.reset()

    expect(store.description).toBe('')
    expect(store.images).toEqual([])
    expect(store.feedbackEmpNo).toBe('')
    expect(store.location).toEqual({ factory: '', area: '', line: '', process: '' })
  })
})
