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

/**
 * 语音转写：把录音文件上传到后端，返回后端识别出的文本。
 *
 * TODO 待实现：接口地址、字段名、返回结构按后端约定填。
 * filePath 是设备录好的临时文件路径（如 file:///storage/.../xxx.aac）。
 */
export const transcribeVoice = (filePath: string): Promise<string> => {
  void filePath
  return Promise.reject(new Error('语音识别接口未实现'))
}
