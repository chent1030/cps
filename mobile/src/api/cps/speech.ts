import { request } from '@/api/request'
import type { CpsSpeechField, CpsSpeechTranscriptionResult } from '@/types/cps'

/**
 * F3 语音转写：录音上传 → Java 落 RustFS → 同步调 Python C-06。
 * 转写文本仅回填表单（PRD §20.2，不作证据）；UNAVAILABLE 时展示降级文案允许重试/手输。
 */
const API_BASE = import.meta.env.VITE_API_BASE_URL ?? ''
const EMP_NO = import.meta.env.VITE_CPS_EMP_NO ?? 'DEMO_EMP'

export const transcribeSpeech = (
  source: File | string,
  field: CpsSpeechField,
  submissionId?: string,
  attempt?: number,
) => {
  if (typeof source === 'string') {
    return transcribeByTempPath(source, field, submissionId, attempt)
  }
  const formData = new FormData()
  formData.append('file', source)
  formData.append('field', field)
  formData.append('empNo', EMP_NO)
  if (submissionId) {
    formData.append('submissionId', submissionId)
  }
  if (attempt !== undefined) {
    formData.append('attempt', String(attempt))
  }
  return request.post<CpsSpeechTranscriptionResult>('/api/cps/speech/transcriptions', formData)
}

const transcribeByTempPath = (
  filePath: string,
  field: CpsSpeechField,
  submissionId?: string,
  attempt?: number,
) => {
  if (typeof uni === 'undefined' || typeof uni.uploadFile !== 'function') {
    return Promise.reject(new Error('CPS speech upload unavailable'))
  }
  const formData: Record<string, string> = { field, empNo: EMP_NO }
  if (submissionId) {
    formData.submissionId = submissionId
  }
  if (attempt !== undefined) {
    formData.attempt = String(attempt)
  }
  return new Promise((resolve: (value: CpsSpeechTranscriptionResult) => void, reject) => {
    uni.uploadFile({
      url: new URL(`${API_BASE}/api/cps/speech/transcriptions`, window.location.origin).toString(),
      filePath,
      name: 'file',
      formData,
      success(response) {
        if ((response.statusCode ?? 0) < 200 || (response.statusCode ?? 0) >= 300) {
          reject(new Error(`CPS speech transcription failed: ${response.statusCode}`))
          return
        }
        try {
          const parsed = typeof response.data === 'string'
            ? JSON.parse(response.data)
            : response.data
          resolve(parsed as CpsSpeechTranscriptionResult)
        } catch (error) {
          reject(error)
        }
      },
      fail(error) {
        reject(new Error(error.errMsg || 'CPS speech transcription failed'))
      },
    })
  })
}
