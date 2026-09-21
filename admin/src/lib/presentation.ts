import type { IssueStatus } from '../types'
import { ApiError } from '../api'

export const statusLabel: Record<IssueStatus, string> = {
  PENDING_FEEDBACK: '待反馈',
  PENDING_RECTIFY: '待整改',
  PENDING_UPLOAD_PROOF: '待上传凭证',
  PENDING_REVIEW: '待审核',
  CLOSED: '已关闭',
}

export const statusStyle: Record<IssueStatus, string> = {
  PENDING_FEEDBACK: 'bg-[#eee7d5] text-[#796339]',
  PENDING_RECTIFY: 'bg-[#eadfd8] text-[#855d49]',
  PENDING_UPLOAD_PROOF: 'bg-[#dde6e1] text-[#527064]',
  PENDING_REVIEW: 'bg-[#dce5e5] text-[#506a70]',
  CLOSED: 'bg-[#e3e7e2] text-[#667264]',
}

export function errorMessage(error: unknown) {
  return error instanceof ApiError ? error.message : '网络连接失败，请检查服务状态后重试'
}

export function formatDate(value?: string) {
  return value
    ? new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
    : '-'
}
