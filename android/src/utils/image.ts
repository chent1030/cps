import type { CpsAttachment, CpsPhotoEntry } from '@/types/cps'
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
 * 绝对 URL（MinIO 直链、本地临时文件路径、blob:/data:）一律原样返回。
 * 历史数据里的 /api/cps/attachments/{id}/content 代理路径才补 base；其它站内路径
 * （/storage/...、/uploads/...）原样返回，由调用方处理。可重复调用。
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

const hasId = (id: number | string | null | undefined): id is number | string =>
  id !== undefined && id !== null && String(id).trim() !== ''

/**
 * 附件内容代理地址：`${base}/api/cps/attachments/{id}/content`。
 * 仅在需要回显历史附件（只有附件 id、没有本地临时文件）时作为展示兜底。
 */
export const attachmentContentUrl = (id: number | string): string =>
  resolveAssetUrl(`/api/cps/attachments/${String(id).trim()}/content`)

/**
 * 历史附件（CpsAttachment）在界面上的展示地址：优先用附件 id 的代理地址，
 * 没有 id 的历史数据才回退 url/fileUrl。
 */
export const resolveAttachmentUrl = (attachment: {
  id?: number | string | null
  url?: string | null
  fileUrl?: string | null
}): string => {
  if (hasId(attachment.id)) return attachmentContentUrl(attachment.id)
  return resolveAssetUrl(attachment.url || attachment.fileUrl || '')
}

/**
 * 上传接口返回值的展示地址：优先服务端返回的绝对 URL（与新建问题页一致），
 * 没有绝对地址（旧后端/仅返回 id）时才回退附件内容代理。
 * 页面内展示请优先用 photoDisplayUrl（本地临时文件优先）。
 */
export const resolveUploadedPhotoUrl = (uploaded: { id?: number | string | null; url?: string | null }): string => {
  const absolute = resolveAssetUrl(uploaded.url ?? '')
  if (/^(https?:)?\/\//i.test(absolute)) return absolute
  if (hasId(uploaded.id)) return attachmentContentUrl(uploaded.id)
  return absolute
}

/**
 * 上传接口返回值的**落库**地址：服务端 AI 初审会直接拿 photo_url 去下载图片，
 * 因此必须保存后端可访问的绝对地址（MinIO 直链）；没有绝对地址时才用代理路径兜底。
 */
export const resolveUploadedPhotoStorageUrl = (uploaded: {
  id?: number | string | null
  url?: string | null
}): string => {
  const absolute = resolveAssetUrl(uploaded.url ?? '')
  if (/^(https?:)?\/\//i.test(absolute)) return absolute
  if (hasId(uploaded.id)) return attachmentContentUrl(uploaded.id)
  return absolute
}

/**
 * 页面内照片的展示地址：本地临时路径优先（选图后立即可见，不依赖上传结果），
 * 本地路径缺失（刷新/历史附件）时才回退服务端地址。
 */
export const photoDisplayUrl = (photo: { localPath?: string | null; url?: string | null }): string => {
  const local = (photo.localPath ?? '').trim()
  if (local) return local
  const remote = (photo.url ?? '').trim()
  if (!remote) return ''
  return resolveAssetUrl(remote)
}

/** 预览用的地址列表：与缩略图同一套「本地优先」规则，保证点开能看到。 */
export const photoPreviewUrls = (
  photos: Array<{ localPath?: string | null; url?: string | null }>
): string[] => photos.map((photo) => photoDisplayUrl(photo)).filter(Boolean)

/**
 * 详情刷新后用服务端附件重建列表时，按附件 id 把本地临时路径合并回来，
 * 避免刷新瞬间缩略图退化成网络图片；上传失败/未完成的本地条目继续保留。
 */
export const mergePhotoLocalPaths = (remote: CpsPhotoEntry[], local: CpsPhotoEntry[]): CpsPhotoEntry[] => {
  const localById = new Map<number, CpsPhotoEntry>()
  for (const photo of local) {
    if (photo.id != null) localById.set(photo.id, photo)
  }
  const remoteIds = new Set<number>()
  const merged = remote.map((photo) => {
    if (photo.id != null) remoteIds.add(photo.id)
    const hit = photo.id != null ? localById.get(photo.id) : undefined
    if (!hit) return photo
    return { ...photo, localPath: hit.localPath, source: hit.source }
  })
  const pending = local.filter((photo) => photo.id == null || !remoteIds.has(photo.id))
  return [...merged, ...pending]
}

// 批量加载附件的图片预览源（统一走附件内容代理，没有 id 的历史数据回退原地址）
export const loadAttachmentImageSources = async (attachments: CpsAttachment[]) => {
  const resolved = attachments
    .map((attachment) => {
      const url = resolveAttachmentUrl(attachment)
      if (!url) return null
      return [attachment.id, url] as const
    })
    .filter((entry): entry is readonly [number, string] => entry !== null)
  return Object.fromEntries(resolved)
}
