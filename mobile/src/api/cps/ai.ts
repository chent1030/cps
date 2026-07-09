import { request } from '@/api/request'
import type { CpsAiSuggestionPayload } from '@/types/cps'

export const inspectCpsImage = (sourceAttachmentId: number): Promise<CpsAiSuggestionPayload> => {
  return request.post<CpsAiSuggestionPayload>('/api/cps/ai/match-knowledge', { attachmentId: sourceAttachmentId })
}

export const transcribeIssueVoice = async (): Promise<string> => {
  return ''
}
