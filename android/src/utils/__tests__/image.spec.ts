import { describe, expect, it } from 'vitest'
import {
  attachmentContentUrl,
  mergePhotoLocalPaths,
  photoDisplayUrl,
  photoPreviewUrls,
  resolveAssetUrl,
  resolveAttachmentUrl,
  resolveUploadedPhotoStorageUrl,
  resolveUploadedPhotoUrl,
} from '@/utils/image'
import type { CpsPhotoEntry } from '@/types/cps'

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
    // App 端 chooseImage 临时路径
    expect(resolveAssetUrl('_doc/uniapp_temp/compressed/1.jpg')).toBe('_doc/uniapp_temp/compressed/1.jpg')
  })

  it('历史 /api 代理路径在 base 为空时保持原样', () => {
    expect(resolveAssetUrl('/api/cps/attachments/7/content')).toBe('/api/cps/attachments/7/content')
  })

  it('非 URL 相对路径与空值不做拼装', () => {
    expect(resolveAssetUrl('cps/xxx.jpg')).toBe('cps/xxx.jpg')
    expect(resolveAssetUrl('')).toBe('')
    expect(resolveAssetUrl('   ')).toBe('')
  })

  it('可重复调用（幂等）', () => {
    const absolute = 'http://127.0.0.1:9000/cps-attachments/cps/xxx.jpg'
    expect(resolveAssetUrl(resolveAssetUrl(absolute))).toBe(absolute)
  })
})

describe('attachmentContentUrl（附件内容代理，仅历史附件兜底）', () => {
  it('按附件 id 生成后端代理地址（base 为空时保持 /api 相对路径）', () => {
    expect(attachmentContentUrl(12)).toBe('/api/cps/attachments/12/content')
    expect(attachmentContentUrl(' 31 ')).toBe('/api/cps/attachments/31/content')
  })
})

describe('resolveAttachmentUrl（历史附件展示地址）', () => {
  it('有 id 时用代理地址，浏览器/App 都能加载', () => {
    expect(
      resolveAttachmentUrl({ id: 7, fileUrl: 'http://127.0.0.1:9000/cps-attachments/cps/xxx.jpg' }),
    ).toBe('/api/cps/attachments/7/content')
  })

  it('没有 id 的历史数据回退 url/fileUrl', () => {
    expect(resolveAttachmentUrl({ fileUrl: 'http://127.0.0.1:9000/a.jpg' })).toBe('http://127.0.0.1:9000/a.jpg')
    expect(resolveAttachmentUrl({ url: '/api/cps/attachments/7/content' })).toBe('/api/cps/attachments/7/content')
    expect(resolveAttachmentUrl({})).toBe('')
  })
})

describe('resolveUploadedPhotoUrl（上传后展示地址）', () => {
  it('保留服务端返回的绝对地址（与新建问题页一致）', () => {
    expect(resolveUploadedPhotoUrl({ id: 12, url: 'http://127.0.0.1:9000/cps-attachments/cps/xxx.jpg' }))
      .toBe('http://127.0.0.1:9000/cps-attachments/cps/xxx.jpg')
  })

  it('没有绝对地址时才回退附件内容代理', () => {
    expect(resolveUploadedPhotoUrl({ id: 12 })).toBe('/api/cps/attachments/12/content')
    expect(resolveUploadedPhotoUrl({ url: '/api/cps/attachments/12/content' })).toBe('/api/cps/attachments/12/content')
    expect(resolveUploadedPhotoUrl({ url: 'cps/xxx.jpg' })).toBe('cps/xxx.jpg')
  })
})

describe('resolveUploadedPhotoStorageUrl（上传后落库地址）', () => {
  it('落库保留 MinIO 绝对地址（服务端 AI 初审要能直接下载）', () => {
    expect(resolveUploadedPhotoStorageUrl({ id: 12, url: 'http://127.0.0.1:9000/cps-attachments/cps/xxx.jpg' }))
      .toBe('http://127.0.0.1:9000/cps-attachments/cps/xxx.jpg')
  })

  it('没有绝对地址时用代理路径兜底，保证不为空', () => {
    expect(resolveUploadedPhotoStorageUrl({ id: 12, url: 'cps/xxx.jpg' })).toBe('/api/cps/attachments/12/content')
    expect(resolveUploadedPhotoStorageUrl({ id: 12 })).toBe('/api/cps/attachments/12/content')
  })
})

describe('photoDisplayUrl（本地临时路径优先）', () => {
  it('有本地路径时一律用本地路径，上传成功后也不覆盖', () => {
    expect(photoDisplayUrl({ localPath: 'blob:http://localhost/1', url: 'http://127.0.0.1:9000/a.jpg' }))
      .toBe('blob:http://localhost/1')
    expect(photoDisplayUrl({ localPath: '_doc/tmp/1.jpg', url: '/api/cps/attachments/1/content' }))
      .toBe('_doc/tmp/1.jpg')
  })

  it('本地路径缺失（刷新/历史附件）时回退服务端地址', () => {
    expect(photoDisplayUrl({ localPath: '', url: 'http://127.0.0.1:9000/a.jpg' })).toBe('http://127.0.0.1:9000/a.jpg')
    expect(photoDisplayUrl({ localPath: '  ', url: '/api/cps/attachments/5/content' })).toBe('/api/cps/attachments/5/content')
    expect(photoDisplayUrl({})).toBe('')
  })
})

describe('photoPreviewUrls（预览地址与缩略图一致）', () => {
  it('逐张走本地优先并过滤空地址', () => {
    expect(
      photoPreviewUrls([
        { localPath: 'blob:http://localhost/1', url: 'http://127.0.0.1:9000/a.jpg' },
        { localPath: '', url: 'http://127.0.0.1:9000/b.jpg' },
        { localPath: '', url: '' },
      ]),
    ).toEqual(['blob:http://localhost/1', 'http://127.0.0.1:9000/b.jpg'])
  })
})

describe('mergePhotoLocalPaths（刷新后保留本地临时路径）', () => {
  const entry = (patch: Partial<CpsPhotoEntry>): CpsPhotoEntry => ({
    id: null,
    localPath: '',
    url: '',
    name: '照片',
    ...patch,
  })

  it('按附件 id 合并本地路径，避免刷新后缩略图退化', () => {
    const remote = [
      entry({ id: 1, url: '/api/cps/attachments/1/content', name: 'a.jpg' }),
      entry({ id: 2, url: '/api/cps/attachments/2/content', name: 'b.jpg' }),
    ]
    const local = [
      entry({ id: 1, localPath: 'blob:http://localhost/1', url: 'http://127.0.0.1:9000/a.jpg' }),
    ]
    const merged = mergePhotoLocalPaths(remote, local)
    expect(merged[0].localPath).toBe('blob:http://localhost/1')
    expect(merged[1].localPath).toBe('')
  })

  it('未上传完成的本地条目继续保留（可重试/删除）', () => {
    const merged = mergePhotoLocalPaths([], [entry({ localPath: 'blob:http://localhost/1', failed: true })])
    expect(merged).toHaveLength(1)
    expect(merged[0].failed).toBe(true)
  })
})
