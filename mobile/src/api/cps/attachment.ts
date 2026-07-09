import { request } from '@/api/request'
import type { CpsUploadedImage } from '@/types/cps'

export type CpsAttachmentUploadSource = File | string

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? ''

export const uploadCpsAttachment = (source: CpsAttachmentUploadSource) => {
  if (typeof source === 'string') {
    return uploadByTempPath(source)
  }

  return uploadByFile(source)
}

const uploadByFile = (file: File) => {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<CpsUploadedImage>('/api/cps/attachments', formData)
}

const uploadByTempPath = (filePath: string) => {
  if (typeof uni === 'undefined' || typeof uni.uploadFile !== 'function') {
    return uploadFallback()
  }

  return new Promise((resolve: (value: CpsUploadedImage) => void, reject) => {
    uni.uploadFile({
      url: buildUploadUrl('/api/cps/attachments'),
      filePath,
      name: 'file',
      success(response) {
        const statusCode = response.statusCode ?? 0
        if (statusCode < 200 || statusCode >= 300) {
          uploadFallback().then(resolve, () => reject(new Error(`CPS attachment upload failed: ${statusCode}`)))
          return
        }
        try {
          resolve(parseUploadResponse(response.data))
        } catch (error) {
          reject(error)
        }
      },
      fail(error) {
        uploadFallback().then(resolve, () => reject(new Error(error.errMsg || 'CPS attachment upload failed')))
      },
    })
  })
}

const uploadFallback = () => {
  return request.post<CpsUploadedImage>('/api/cps/attachments')
}

const buildUploadUrl = (path: string) => {
  return new URL(`${API_BASE}${path}`, window.location.origin).toString()
}

const parseUploadResponse = (data: string | object | undefined) => {
  if (typeof data === 'string') {
    return JSON.parse(data) as CpsUploadedImage
  }
  return data as CpsUploadedImage
}
