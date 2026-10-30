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

const HTTP_STATUS_HINT: Record<number, string> = {
  400: '请求参数有误',
  401: '登录已失效，请重新登录',
  403: '没有操作权限',
  404: '数据不存在或已被删除',
  409: '数据冲突，操作未完成',
  500: '服务端异常',
}

/**
 * 后端错误 → 可读文案。
 * Spring 异常会返回 JSON（自定义 {error,message} 或默认 {status,error,message,path}），
 * 直接展示 ApiError.message 会把 JSON 大括号抛给用户，所以优先取其中的 message。
 */
export function errorMessage(error: unknown) {
  if (!(error instanceof ApiError)) return '网络连接失败，请检查服务状态后重试'
  const raw = (error.message ?? '').trim()
  if (raw.startsWith('{')) {
    try {
      const parsed = JSON.parse(raw) as { message?: unknown; error?: unknown }
      const message = typeof parsed.message === 'string' ? parsed.message.trim() : ''
      if (message) return message
    } catch {
      // 不是合法 JSON：按原文返回
    }
    return `${HTTP_STATUS_HINT[error.status] ?? '请求失败'}（${error.status}）`
  }
  return raw || `${HTTP_STATUS_HINT[error.status] ?? '请求失败'}（${error.status}）`
}

export function formatDate(value?: string) {
  return value
    ? new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
    : '-'
}
