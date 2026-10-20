import { request } from '@/api/request'
import type { CpsUploadedImage } from '@/types/cps'

const EMP_NO = import.meta.env.VITE_CPS_EMP_NO ?? '61016968'
const API_BASE = import.meta.env.VITE_API_BASE_URL ?? ''

export type CpsAttachmentUploadSource = File | string

export const uploadCpsAttachment = (source: CpsAttachmentUploadSource) => {
  if (typeof source === 'string') {
    return uploadByTempPath(source)
  }
  return uploadByFile(source)
}

const uploadByFile = (file: File) => {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('empNo', EMP_NO)
  return request.post<CpsUploadedImage>('/api/cps/attachments', formData)
}

const uploadByTempPath = (filePath: string) => {
  if (typeof uni === 'undefined' || typeof uni.uploadFile !== 'function') {
    return uploadFallback()
  }

  return new Promise<CpsUploadedImage>((resolve, reject) => {
    uni.uploadFile({
      url: buildUploadUrl('/api/cps/attachments'),
      filePath,
      name: 'file',
      formData: { empNo: EMP_NO },
      success: (res) => {
        try {
          const parsed = typeof res.data === 'string' ? JSON.parse(res.data) : (res.data as unknown as CpsUploadedImage)
          resolve(parsed)
        } catch (error) {
          reject(error)
        }
      },
      fail: (err) => reject(err),
    })
  })
}

const uploadFallback = async (): Promise<CpsUploadedImage> => {
  throw new Error('当前环境不支持临时路径上传，请使用 File 对象')
}

const buildUploadUrl = (path: string) => {
  if (/^https?:\/\//.test(path)) return path
  if (path.startsWith('/')) return `${API_BASE}${path}`
  return `${API_BASE}/${path}`
}
