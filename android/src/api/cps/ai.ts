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
// 语音输入
//   点击「语音输入」→ 调用 Android 系统录音（uni.getRecorderManager）
//   再点「停止录音」→ 停止录音，把录音文件上传后端，后端返回识别文本
// 上传那一步（uploadVoiceForText）留空，由你自己实现。
// ============================================================

/** 只声明用得到的几个方法。 */
interface RecorderManagerLike {
  start: (options: Record<string, unknown>) => void
  stop: () => void
  onStop: (handler: (result: { tempFilePath?: string }) => void) => void
  onError: (handler: (error: { errMsg?: string }) => void) => void
}

let recorder: RecorderManagerLike | null = null
let recordingFlag = false
/** 停止录音后的回调：识别成功的文本 / 失败原因 */
let onTranscribed: ((text: string) => void) | null = null
let onTranscribeFailed: ((message: string) => void) | null = null

const getRecorder = (): RecorderManagerLike => {
  if (recorder) return recorder
  const manager = uni.getRecorderManager() as unknown as RecorderManagerLike
  // 录音结束：拿到录音临时文件，交给后端识别
  manager.onStop((result) => {
    recordingFlag = false
    const filePath = result?.tempFilePath
    if (!filePath) {
      onTranscribeFailed?.('没有取到录音文件')
      return
    }
    uploadVoiceForText(filePath).then(
      (text) => onTranscribed?.(text),
      (error: unknown) => onTranscribeFailed?.(error instanceof Error ? error.message : '语音识别失败'),
    )
  })
  manager.onError((error) => {
    recordingFlag = false
    onTranscribeFailed?.(error?.errMsg || '录音失败')
  })
  recorder = manager
  return recorder
}

/** 点击开始录音：调用 Android 系统录音。 */
export const startVoiceRecording = () => {
  if (recordingFlag) return
  getRecorder().start({
    duration: 120000, // 最长 2 分钟
    format: 'aac',
    sampleRate: 16000,
    numberOfChannels: 1,
    encodeBitRate: 48000,
  })
  recordingFlag = true
}

/** 点击停止录音：停止录音 → 上传录音 → 回调后端返回的文本。 */
export const stopVoiceRecording = (onText: (text: string) => void, onError: (message: string) => void) => {
  if (!recordingFlag) {
    onError('当前没有在录音')
    return
  }
  onTranscribed = onText
  onTranscribeFailed = onError
  getRecorder().stop()
}

/** 是否正在录音。 */
export const isVoiceRecording = () => recordingFlag

/**
 * =========== 待你实现：上传录音，返回后端识别的文本 ===========
 *
 * filePath 是 Android 录好的临时文件路径（例如 file:///storage/.../xxx.aac），
 * 在这里调用后端的上传接口，把识别的文本 return 出来即可，例如：
 *
 *   const res = await uni.uploadFile({ url: `${API_BASE}/api/xxx`, filePath, name: 'audio' })
 *   return JSON.parse(res.data).text
 *
 * 现在先抛错，页面会提示「语音识别接口未实现」。
 */
export const uploadVoiceForText = async (filePath: string): Promise<string> => {
  void filePath
  throw new Error('语音识别接口未实现')
}
