import type { CpsAttachment } from '@/types/cps'
import type { CpsAttachmentUploadSource } from '@/api/cps/attachment'

interface UniTempFileLike {
  path?: string
  file?: File
}

type TempFileCandidate = File | UniTempFileLike

export const normalizeArray = <T>(value: T | T[] | undefined) => {
  if (value === undefined) return []
  return Array.isArray(value) ? value : [value]
}

// 将 uni.chooseImage 的返回值解析为可上传的源（File 或 temp path）
export const resolveUploadSources = (result: UniApp.ChooseImageSuccessCallbackResult) => {
  const paths = normalizeArray(result.tempFilePaths)
  const tempFiles = normalizeArray(result.tempFiles) as TempFileCandidate[]
  return paths
    .map((path, index) => {
      const tempFile = tempFiles[index]
      if (typeof File !== 'undefined' && tempFile instanceof File) return tempFile
      const localFile = tempFile as UniTempFileLike | undefined
      if (typeof File !== 'undefined' && localFile?.file instanceof File) return localFile.file
      return localFile?.path || path
    })
    .filter(Boolean) as CpsAttachmentUploadSource[]
}

// 直接返回后端的 url（绝对 URL 优先；历史数据可能是 /api/cps/attachments/{id}/content 代理路径），前端无需再 base64 转换。
export const toImageUrl = (attachment: { id: number; url: string } | { id: number; fileUrl: string }): string => {
  if ('url' in attachment && attachment.url) return attachment.url
  if ('fileUrl' in attachment && attachment.fileUrl) return attachment.fileUrl
  return ''
}

// 运行期读取 API base，便于测试与运行时覆盖（VITE_API_BASE_URL 为空时与请求层一致按同源处理）。
const apiBase = () => (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/+$/, '')

/**
 * 把后端返回的站内相对路径（/api/...）补成绝对地址。
 * MinIO 之后上传接口/附件表返回的是可直接访问的绝对 URL，绝对地址原样返回，不再拼接。
 * 历史数据里的 /api/cps/attachments/{id}/content 代理路径才补 base；其它站内路径
 * （/storage/...、/uploads/...）与裸对象键保持原样，由调用方降级处理。可重复调用。
 */
export const resolveAssetUrl = (path: string): string => {
  const value = (path ?? '').trim()
  if (!value) return ''
  if (/^(https?:)?\/\//i.test(value)) return value
  if (/^(data|blob|file|content):/i.test(value)) return value
  if (!value.startsWith('/')) return value
  if (!value.startsWith('/api/')) return value
  const base = apiBase()
  if (!base) return value
  return `${base}${value}`
}

/**
 * 上传接口返回值的照片地址：新后端直接给绝对 URL（MinIO 直链），原样保存与回显；
 * 只有返回值里没有绝对 URL 时才回退历史拼法 /api/cps/attachments/{id}/content。
 * 二者都拿不到时保留原值（裸对象键），由渲染层降级。
 */
export const resolveUploadedPhotoUrl = (uploaded: { id?: number | string | null; url?: string | null }): string => {
  const resolved = resolveAssetUrl(uploaded.url ?? '')
  if (/^(https?:)?\/\//i.test(resolved)) return resolved
  const id = uploaded.id
  if (id !== undefined && id !== null && String(id).trim() !== '') {
    return resolveAssetUrl(`/api/cps/attachments/${id}/content`)
  }
  return resolved
}

// 批量加载附件的图片预览源（把后端相对地址补成绝对地址，失败的附件会被忽略）
export const loadAttachmentImageSources = async (attachments: CpsAttachment[]) => {
  const resolved = attachments
    .map((attachment) => {
      const url = resolveAssetUrl(toImageUrl(attachment))
      if (!url) return null
      return [attachment.id, url] as const
    })
    .filter((entry): entry is readonly [number, string] => entry !== null)
  return Object.fromEntries(resolved)
}
