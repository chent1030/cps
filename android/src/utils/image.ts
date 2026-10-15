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

// 批量加载附件的图片预览源（直接用后端 url，失败的附件会被忽略）
export const loadAttachmentImageSources = async (attachments: CpsAttachment[]) => {
  const resolved = attachments
    .map((attachment) => {
      const url = toImageUrl(attachment)
      if (!url) return null
      return [attachment.id, url] as const
    })
    .filter((entry): entry is readonly [number, string] => entry !== null)
  return Object.fromEntries(resolved)
}
