import { request } from '@/api/request'
import type { CpsAiSuggestionPayload } from '@/types/cps'

export const inspectCpsImage = (sourceAttachmentId: number) => {
  return request.post<CpsAiSuggestionPayload>('/api/cps/ai/match-knowledge', { attachmentId: sourceAttachmentId })
}

interface TranscribeResponse {
  text: string
}

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? ''
const TRANSCRIBE_PATH = '/api/cps/ai/transcribe'

/**
 * 语音转文字录音器：点击开始、再点击结束的两段式控制。
 *
 * - start()：开始录音，返回一个可调用的 stop 函数。
 * - stop()：结束录音并上传后端转写，返回识别文本。
 *
 * 后端契约：POST /api/cps/ai/transcribe，multipart 字段名 audio，返回 { text: string }。
 */

// ---- app-plus 录音器（uni.getRecorderManager）----
interface UniRecorderController {
  stop: () => Promise<string>
}

const startUniRecorder = (): UniRecorderController => {
  const recorder = uni.getRecorderManager()
  let resolveStop: ((text: string) => void) | null = null
  let rejectStop: ((error: Error) => void) | null = null
  let finished = false

  recorder.onStop((res) => {
    if (finished) return
    finished = true
    uploadAudio(res.tempFilePath).then(
      (text) => resolveStop?.(text),
      (error) => rejectStop?.(error),
    )
  })

  recorder.onError((error) => {
    if (finished) return
    finished = true
    rejectStop?.(new Error(error.errMsg || '录音失败'))
  })

  recorder.start({
    duration: 60000,
    format: 'aac',
    sampleRate: 16000,
    numberOfChannels: 1,
  })

  return {
    stop: () =>
      new Promise<string>((resolve, reject) => {
        resolveStop = resolve
        rejectStop = reject
        try {
          recorder.stop()
        } catch (error) {
          reject(error instanceof Error ? error : new Error('停止录音失败'))
        }
      }),
  }
}

// ---- H5 / 浏览器录音器（MediaRecorder）----
interface MediaRecorderController {
  stop: () => Promise<string>
}

const startMediaRecorder = async (): Promise<MediaRecorderController> => {
  const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
  const mime = pickMime()
  const recorder = new MediaRecorder(stream, mime ? { mimeType: mime } : undefined)
  const chunks: BlobPart[] = []

  recorder.ondataavailable = (event) => {
    if (event.data.size > 0) chunks.push(event.data)
  }

  recorder.start()

  return {
    stop: () =>
      new Promise<string>((resolve, reject) => {
        recorder.onstop = async () => {
          stream.getTracks().forEach((track) => track.stop())
          const blob = new Blob(chunks, { type: mime || 'audio/webm' })
          try {
            resolve(await uploadAudioBlob(blob))
          } catch (error) {
            reject(error instanceof Error ? error : new Error('语音转写失败'))
          }
        }
        recorder.onerror = () => reject(new Error('录音失败'))
        if (recorder.state !== 'inactive') recorder.stop()
      }),
  }
}

const pickMime = () => {
  const candidates = ['audio/webm;codecs=opus', 'audio/webm', 'audio/ogg;codecs=opus', 'audio/mp4']
  if (typeof MediaRecorder === 'undefined') return ''
  return candidates.find((type) => MediaRecorder.isTypeSupported(type)) ?? ''
}

type RecorderController = UniRecorderController | MediaRecorderController

let activeController: RecorderController | null = null

/** 开始录音。返回 true 表示成功开始；当前环境不支持或已在录音中时返回 false。 */
export const startVoiceRecording = async (): Promise<boolean> => {
  if (activeController) return false
  try {
    if (typeof uni !== 'undefined' && typeof uni.getRecorderManager === 'function') {
      activeController = startUniRecorder()
    } else if (typeof navigator !== 'undefined' && typeof navigator.mediaDevices?.getUserMedia === 'function') {
      activeController = await startMediaRecorder()
    } else {
      return false
    }
    return true
  } catch {
    activeController = null
    return false
  }
}

/** 结束录音并转写。返回识别文本。 */
export const stopVoiceRecording = async (): Promise<string> => {
  const controller = activeController
  activeController = null
  if (!controller) {
    throw new Error('未在录音中')
  }
  return controller.stop()
}

/** 是否正在录音。 */
export const isVoiceRecording = () => activeController !== null

const uploadAudio = (filePath: string): Promise<string> => {
  return new Promise((resolve, reject) => {
    if (typeof uni === 'undefined' || typeof uni.uploadFile !== 'function') {
      reject(new Error('当前环境不支持文件上传'))
      return
    }
    uni.uploadFile({
      url: buildUrl(TRANSCRIBE_PATH),
      filePath,
      name: 'audio',
      success(response) {
        const statusCode = response.statusCode ?? 0
        if (statusCode < 200 || statusCode >= 300) {
          reject(new Error(`语音转写失败：${statusCode}`))
          return
        }
        try {
          const data = typeof response.data === 'string' ? JSON.parse(response.data) : response.data
          resolve((data as TranscribeResponse).text ?? '')
        } catch {
          reject(new Error('语音转写响应解析失败'))
        }
      },
      fail(error) {
        reject(new Error(error.errMsg || '语音转写上传失败'))
      },
    })
  })
}

const uploadAudioBlob = async (blob: Blob): Promise<string> => {
  const formData = new FormData()
  formData.append('audio', blob, `voice.${(blob.type.split('/')[1] || 'webm').split(';')[0]}`)
  const response = await request.post<TranscribeResponse>(TRANSCRIBE_PATH, formData)
  return response.text ?? ''
}

const buildUrl = (path: string) => {
  const origin = typeof window !== 'undefined' && window.location ? window.location.origin : ''
  const base = API_BASE || origin
  return new URL(`${base}${path}`, origin || 'http://localhost').toString()
}
