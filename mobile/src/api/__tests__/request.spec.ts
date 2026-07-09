import { request } from '@/api/request'

describe('request mock fallback', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('uses API response first when the API is available', async () => {
    const apiFactories = [{ value: 9, label: 'API工厂' }]
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(new Response(JSON.stringify(apiFactories), { status: 200 })),
    )

    await expect(request.get('/api/cps/master/factories')).resolves.toEqual(apiFactories)
  })

  it('falls back to local mock data when the API is unavailable', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('network down')))

    await expect(request.get('/api/cps/master/factories')).resolves.toEqual(
      expect.arrayContaining([expect.objectContaining({ value: 1, label: '一厂' })]),
    )
  })

  it('falls back when the dev server returns a non-json 200 response', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(new Response('<!doctype html><div id="app"></div>', { status: 200 })),
    )

    await expect(request.get('/api/cps/issues', { params: { tab: 'todo', page: 1, pageSize: 20 } })).resolves.toEqual(
      expect.arrayContaining([expect.objectContaining({ issueNo: 'CPS20260627001' })]),
    )
  })

  it('passes POST payloads to mock handlers when falling back', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('network down')))

    await expect(request.post('/api/cps/ai/inspect-image', { sourceAttachmentId: 42 })).resolves.toMatchObject({
      sourceAttachmentId: 42,
    })
  })
})
