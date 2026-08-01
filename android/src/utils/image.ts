import type { CpsAttachment } from '@/types/cps'
import { getCpsAttachmentBase64 } from '@/api/cps/attachment'
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

// 把后端返回的 base64 字符串规范化为可直接用于 <img src> 的 data URL
export const toImageDataUrl = (value: string) => {
  const data = value.trim()
  if (data.startsWith('data:')) return data
  if (data.includes(';base64,')) return `data:${data}`
  return `data:image/jpeg;base64,${data.replace(/\s/g, '')}`
}

// 批量加载附件的 base64 预览源，失败的附件会被忽略
export const loadAttachmentImageSources = async (attachments: CpsAttachment[]) => {
  const resolved = await Promise.all(
    attachments.map(async (attachment) => {
      try {
        const base64 = await getCpsAttachmentBase64(attachment.fileUrl)
        return [attachment.id, toImageDataUrl(base64)] as const
      } catch {
        return null
      }
    }),
  )
  return Object.fromEntries(resolved.filter((source): source is readonly [number, string] => source !== null))
}
