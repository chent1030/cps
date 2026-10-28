import { describe, expect, it } from 'vitest'
import { resolveAssetUrl, resolveUploadedPhotoUrl } from '@/utils/image'

// 本仓库 .env 的 VITE_API_BASE_URL 为空：/api 相对路径保持原样（运行时 App 端再由 request 层补 host）。
describe('resolveAssetUrl（绝对 URL 优先）', () => {
  it('MinIO 绝对 URL 原样返回，不拼接任何 base', () => {
    expect(resolveAssetUrl('http://127.0.0.1:9000/cps-attachments/cps/xxx.jpg')).toBe('http://127.0.0.1:9000/cps-attachments/cps/xxx.jpg')
    expect(resolveAssetUrl('https://cdn.example.com/a/b.png')).toBe('https://cdn.example.com/a/b.png')
    expect(resolveAssetUrl('//cdn.example.com/a.png')).toBe('//cdn.example.com/a.png')
  })

  it('协议相对与本地临时文件、data URL 原样返回', () => {
    expect(resolveAssetUrl('data:image/png;base64,AAA')).toBe('data:image/png;base64,AAA')
    expect(resolveAssetUrl('blob:http://localhost/1')).toBe('blob:http://localhost/1')
    expect(resolveAssetUrl('/storage/tmp/a.jpg')).toBe('/storage/tmp/a.jpg')
    expect(resolveAssetUrl('/uploads/cps/a.jpg')).toBe('/uploads/cps/a.jpg')
  })

  it('历史 /api 代理路径在 base 为空时保持原样', () => {
    expect(resolveAssetUrl('/api/cps/attachments/7/content')).toBe('/api/cps/attachments/7/content')
  })

  it('裸对象键与空值不做拼装', () => {
    expect(resolveAssetUrl('cps/xxx.jpg')).toBe('cps/xxx.jpg')
    expect(resolveAssetUrl('')).toBe('')
    expect(resolveAssetUrl('   ')).toBe('')
  })

  it('可重复调用（幂等）', () => {
    const absolute = 'http://127.0.0.1:9000/cps-attachments/cps/xxx.jpg'
    expect(resolveAssetUrl(resolveAssetUrl(absolute))).toBe(absolute)
  })
})

describe('resolveUploadedPhotoUrl（上传返回值落库）', () => {
  it('新后端返回绝对 url 时直接落库为照片值', () => {
    expect(resolveUploadedPhotoUrl({ id: 12, url: 'http://127.0.0.1:9000/cps-attachments/cps/xxx.jpg' }))
      .toBe('http://127.0.0.1:9000/cps-attachments/cps/xxx.jpg')
  })

  it('旧后端返回 /api 代理路径时保持同一路径，不再重复拼接', () => {
    expect(resolveUploadedPhotoUrl({ id: 12, url: '/api/cps/attachments/12/content' })).toBe('/api/cps/attachments/12/content')
  })

  it('返回值没有可用绝对 url 时回退旧拼法 /api/cps/attachments/{id}/content', () => {
    expect(resolveUploadedPhotoUrl({ id: 12, url: 'cps/xxx.jpg' })).toBe('/api/cps/attachments/12/content')
    expect(resolveUploadedPhotoUrl({ id: 12 })).toBe('/api/cps/attachments/12/content')
    expect(resolveUploadedPhotoUrl({ id: 12, url: '' })).toBe('/api/cps/attachments/12/content')
  })

  it('连 id 都没有时保留原值，由渲染层降级', () => {
    expect(resolveUploadedPhotoUrl({ url: 'cps/xxx.jpg' })).toBe('cps/xxx.jpg')
  })
})
