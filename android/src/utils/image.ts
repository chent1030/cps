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

// 直接返回后端的 url（已是 /api/cps/attachments/{id}/content），前端无需再 base64 转换。
export const toImageUrl = (attachment: { id: number; url: string } | { id: number; fileUrl: string }): string => {
  if ('url' in attachment && attachment.url) return attachment.url
  if ('fileUrl' in attachment && attachment.fileUrl) return attachment.fileUrl
  return ''
}

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? ''

/**
 * 把后端返回的站内相对路径（/api/...）补成绝对地址。
 * App 端（app-plus）没有同源页面，相对路径既不能显示也不能预览，必须先补 host。
 * 本身已是绝对地址或本地临时文件（/storage/...）的原样返回，可重复调用。
 */
export const resolveAssetUrl = (path: string): string => {
  const value = (path ?? '').trim()
  if (!value) return ''
  if (/^(https?:)?\/\//i.test(value)) return value
  if (/^(data|blob|file|content):/i.test(value)) return value
  if (value.startsWith('/storage/')) return value
  if (!value.startsWith('/')) return value
  if (!API_BASE) return value
  return `${API_BASE.replace(/\/+$/, '')}${value}`
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
