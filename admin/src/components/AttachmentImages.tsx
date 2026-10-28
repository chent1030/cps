/**
 * 附件/照片渲染组件（绝对 URL 优先）。
 * 地址解析规则集中在 ../lib/attachmentUrl：
 * - 绝对 http(s) 地址（MinIO 直链）直接作为 <img src>；
 * - /api/... 代理路径按 API base 补全；
 * - 裸对象键（cps/xxx.jpg）解析为空串，降级为文件名/张数文案，不渲染坏图。
 */
import { Image, Space, Typography } from '@arco-design/web-react'
import { useState } from 'react'
import { resolveAttachmentUrl, splitAttachmentUrls } from '../lib/attachmentUrl'

/** 单张附件缩略图：地址不可解析（裸对象键）或加载失败时返回 null，由外层降级。 */
export function AttachmentThumb({
  value,
  width = 64,
  height = 48,
  alt = '照片',
}: {
  value?: string | null
  width?: number
  height?: number
  alt?: string
}) {
  const [broken, setBroken] = useState(false)
  const url = resolveAttachmentUrl(value)
  if (!url || broken) return null
  return <Image
    src={url}
    width={width}
    height={height}
    alt={alt}
    style={{ objectFit: 'cover', borderRadius: 4, background: '#f5f8f7' }}
    onError={() => setBroken(true)}
  />
}

/** 一组附件缩略图（支持点击预览）：不可解析的地址与加载失败的图片会被跳过。 */
export function AttachmentThumbs({
  values,
  width = 64,
  height = 48,
  alt = '照片',
}: {
  values?: Array<string | null | undefined> | null
  width?: number
  height?: number
  alt?: string
}) {
  const urls = (values ?? []).map((value) => resolveAttachmentUrl(value)).filter((url): url is string => Boolean(url))
  if (!urls.length) return null
  return <Image.PreviewGroup>
    <Space wrap size={4}>
      {urls.map((url, index) => <AttachmentThumb key={`${url}-${index}`} value={url} width={width} height={height} alt={alt} />)}
    </Space>
  </Image.PreviewGroup>
}

/**
 * 点检项照片墙 + 张数摘要，供巡检复核页与统一巡检记录页共用。
 * photoObjectKey 支持绝对 URL、/api/... 代理路径与纯数字附件 ID；裸对象键只保留张数摘要。
 */
export function InspectionItemPhotos({
  item,
  width = 64,
  height = 48,
}: {
  item: {
    photoObjectKey?: string | null
    photoCount?: number
    photoMinCount?: number | null
    photoFileName?: string | null
    content?: string | null
  }
  width?: number
  height?: number
}) {
  const urls = splitAttachmentUrls(item.photoObjectKey)
  const summary = `照片 ${item.photoCount ?? urls.length}/${item.photoMinCount ?? 1} 张${item.photoFileName ? ` · ${item.photoFileName}` : ''}`
  return <Space direction="vertical" size={4}>
    <AttachmentThumbs values={urls} width={width} height={height} alt={item.photoFileName || item.content || '点检照片'} />
    <Typography.Text type="secondary" style={{ fontSize: 11 }}>{summary}</Typography.Text>
  </Space>
}
