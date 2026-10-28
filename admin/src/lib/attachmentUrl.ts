/**
 * 附件/照片地址解析（绝对 URL 优先）。
 *
 * 后端文件存储切到 MinIO 后，上传接口返回的 url 与附件表的 fileUrl 都是可直接访问的绝对地址
 * （如 http://127.0.0.1:9000/cps-attachments/cps/xxx.jpg），前端不再需要拼 /content。
 * 历史数据仍可能落库三种形态：
 * 1. 绝对 http(s) URL（MinIO 直链）→ 原样使用；
 * 2. 站内代理路径（/api/cps/attachments/{id}/content）→ 按 VITE_API_BASE_URL 拼 base；
 * 3. 裸对象键（cps/xxx.jpg）→ 前端无从得知 bucket 域名，解析为空串，由调用方降级显示文件名/张数。
 */

/** 与 src/api.ts 保持一致的 API base（默认同源 /api，末尾不带斜杠）。 */
export const API_BASE = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/+$/, '')

/** 站内相对路径补 API base：只重写 /api/... 代理路径，其它路径（/uploads/...、/static/...）保持原样。 */
function withApiBase(path: string): string {
  if (!path.startsWith('/api/')) return path
  if (API_BASE === '/api') return path
  // 绝对 base（http://host:8080 或 http://host:8080/api）与 /api 前缀同名，拼 base 前先去掉 /api。
  return `${API_BASE}${path.slice(4)}`
}

/**
 * 把后端返回的照片/附件地址解析成可直接用于 <img src> 的地址。
 * 绝对 URL 原样返回；/api/... 代理路径拼 base；裸对象键与空值返回空串（降级）。
 */
export function resolveAttachmentUrl(value?: string | null): string {
  const raw = (value ?? '').trim()
  if (!raw) return ''
  if (/^(https?:)?\/\//i.test(raw)) return raw
  if (/^(data|blob|file|content):/i.test(raw)) return raw
  if (!raw.startsWith('/')) return ''
  return withApiBase(raw)
}

/** 只有附件 ID、没有可用地址时的历史兜底：/api/cps/attachments/{id}/content 代理接口。 */
export function attachmentContentUrl(id: number | string): string {
  return `${API_BASE}/cps/attachments/${encodeURIComponent(String(id))}/content`
}

/**
 * 逗号分隔的照片字段（photoObjectKey、问题图片 ID 列表）→ 可显示的 URL 列表。
 * 支持绝对 URL、/api/... 代理路径、纯数字附件 ID；裸对象键无法解析，直接丢弃（由调用方降级）。
 */
export function splitAttachmentUrls(value?: string | null): string[] {
  return (value ?? '')
    .split(',')
    .map((part) => part.trim())
    .filter(Boolean)
    .flatMap((part) => {
      if (/^(https?:)?\/\//i.test(part)) return [part]
      if (part.startsWith('/')) return [withApiBase(part)]
      if (/^\d+$/.test(part)) return [attachmentContentUrl(part)]
      return []
    })
}

/**
 * 附件记录（cps_issue_attachment）的展示地址：绝对 fileUrl 优先，
 * 历史裸对象键/缺失时回退 /api/cps/attachments/{id}/content。
 */
export function attachmentDisplayUrl(attachment: {
  id: number | string
  url?: string | null
  fileUrl?: string | null
}): string {
  return resolveAttachmentUrl(attachment.url) || resolveAttachmentUrl(attachment.fileUrl) || attachmentContentUrl(attachment.id)
}
