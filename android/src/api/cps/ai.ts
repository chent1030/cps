import { request } from '@/api/request'
import type { CpsAiSuggestionPayload } from '@/types/cps'

// 后端 /api/cps/ai/match-knowledge 返回字段名是 categoryL1Id/L1Name/L2Id/L2Name（无 "ai" 前缀），
// 与前端 CpsAiSuggestionPayload 不一致。这里统一归一化为前端 ai* 字段，避免在 view 里再手工映射。
interface CpsMatchKnowledgeBackendResponse {
  sourceAttachmentId: number
  matchedCaseId: number
  matchedImageId: number
  confidence: number | string
  categoryL1Id: number | null
  categoryL2Id: number | null
  categoryL1Name: string | null
  categoryL2Name: string | null
  reasonSuggestion: string | null
  measureSuggestion: string | null
  modelName: string | null
  modelVersion: string | null
  matchedCases?: unknown[]
}

const normalizeMatchResponse = (resp: CpsMatchKnowledgeBackendResponse): CpsAiSuggestionPayload => ({
  sourceAttachmentId: resp.sourceAttachmentId,
  aiCategoryL1Id: resp.categoryL1Id,
  aiCategoryL1Name: resp.categoryL1Name,
  aiCategoryL2Id: resp.categoryL2Id,
  aiCategoryL2Name: resp.categoryL2Name,
  reasonSuggestion: resp.reasonSuggestion,
  measureSuggestion: resp.measureSuggestion,
  modelName: resp.modelName,
  modelVersion: resp.modelVersion,
  confidence: resp.confidence,
})

export const inspectCpsImage = async (sourceAttachmentId: number): Promise<CpsAiSuggestionPayload> => {
  const resp = await request.post<CpsMatchKnowledgeBackendResponse>('/api/cps/ai/match-knowledge', {
    attachmentId: sourceAttachmentId,
  })
  return normalizeMatchResponse(resp)
}

// ============================================================
// 语音输入（uni 原生录音器 uni.getRecorderManager）
// 交互：点一次 = 开始录音；再点一次 = 停止录音，停止后把录音文件送去转写，返回文字。
// 转写接口暂未接入（先留空），接入时只需要实现文件末尾的 transcribeAudio()。
// ============================================================

/** 后端契约（待接入）：POST /api/cps/ai/transcribe，multipart 字段名 audio，返回 { text: string }。 */
const TRANSCRIBE_PATH = '/api/cps/ai/transcribe'

interface RecorderStopResult {
  tempFilePath?: string
  duration?: number
  fileSize?: number
}

/** 只声明用得到的几个方法，避免依赖平台类型定义缺失。 */
interface RecorderManagerLike {
  start: (options: Record<string, unknown>) => void
  stop: () => void
  onStop: (handler: (result: RecorderStopResult) => void) => void
  onError: (handler: (error: { errMsg?: string }) => void) => void
}

let recorder: RecorderManagerLike | null = null
let recording = false
let handleStop: ((result: RecorderStopResult) => void) | null = null
let handleError: ((error: Error) => void) | null = null

/** 录音器是全局单例，onStop/onError 只注册一次。 */
const ensureRecorder = (): RecorderManagerLike => {
  if (recorder) return recorder
  const manager = uni.getRecorderManager() as unknown as RecorderManagerLike
  manager.onStop((result) => {
    recording = false
    const done = handleStop
    handleStop = null
    handleError = null
    done?.(result)
  })
  manager.onError((error) => {
    recording = false
    const fail = handleError
    handleStop = null
    handleError = null
    fail?.(new Error(error?.errMsg || '录音失败'))
  })
  recorder = manager
  return recorder
}

/** 开始录音：true=已在录音；当前环境不支持或重复点击返回 false。 */
export const startVoiceRecording = (): boolean => {
  if (recording) return false
  if (typeof uni === 'undefined' || typeof uni.getRecorderManager !== 'function') return false
  try {
    ensureRecorder().start({
      duration: 120000,
      format: 'aac',
      sampleRate: 16000,
      numberOfChannels: 1,
      encodeBitRate: 48000,
    })
    recording = true
    return true
  } catch {
    return false
  }
}

/** 是否正在录音。 */
export const isVoiceRecording = () => recording

/** 停止录音 → 上传录音文件转写 → 返回识别文字。 */
export const stopVoiceRecording = (): Promise<string> =>
  new Promise<string>((resolve, reject) => {
    if (!recorder || !recording) {
      reject(new Error('当前没有在录音'))
      return
    }
    handleStop = (result) => {
      const filePath = result?.tempFilePath
      if (!filePath) {
        reject(new Error('没有取到录音文件'))
        return
      }
      transcribeAudio(filePath).then(resolve, reject)
    }
    handleError = reject
    try {
      recorder.stop()
    } catch (error) {
      recording = false
      handleStop = null
      handleError = null
      reject(error instanceof Error ? error : new Error('停止录音失败'))
    }
  })

/**
 * 语音转写：把录音文件上传到后端，返回识别出的文字。
 *
 * TODO 待接入（按后端最终约定实现即可，页面逻辑无需改动）：
 *   const API_BASE = import.meta.env.VITE_API_BASE_URL ?? ''
 *   uni.uploadFile({
 *     url: `${API_BASE}${TRANSCRIBE_PATH}`,
 *     filePath,
 *     name: 'audio',
 *     success: (res) => resolve((JSON.parse(res.data) as { text?: string }).text ?? ''),
 *     fail: (err) => reject(new Error(err?.errMsg || '语音转写失败')),
 *   })
 *
 * 现在先返回空字符串：录音链路（开始/停止/拿到临时文件）可以正常验证，
 * 页面会提示「未识别到语音内容」。
 */
const transcribeAudio = async (filePath: string): Promise<string> => {
  void TRANSCRIBE_PATH
  void filePath
  return ''
}
