import type { CpsIssueListItem, CpsIssueStatus } from '@/types/cps'

export interface StatusMeta {
  label: string
  /** antd Tag color */
  color: string
  /** 卡片左侧色条 CSS 类 */
  rail: string
  /** 药丸背景/前景类（自定义 cps 样式） */
  tone: string
}

export const statusMeta: Record<CpsIssueStatus, StatusMeta> = {
  PENDING_FEEDBACK: {
    label: '待反馈',
    color: 'blue',
    rail: 'cps-rail--blue',
    tone: 'cps-pill--blue',
  },
  PENDING_RECTIFY: {
    label: '待整改',
    color: 'orange',
    rail: 'cps-rail--orange',
    tone: 'cps-pill--orange',
  },
  PENDING_UPLOAD_PROOF: {
    label: '待传图',
    color: 'orange',
    rail: 'cps-rail--orange',
    tone: 'cps-pill--orange',
  },
  PENDING_REVIEW: {
    label: '待审核',
    color: 'cyan',
    rail: 'cps-rail--teal',
    tone: 'cps-pill--teal',
  },
  CLOSED: {
    label: '已关闭',
    color: 'green',
    rail: 'cps-rail--green',
    tone: 'cps-pill--green',
  },
}

export const issueLocation = (item: Pick<CpsIssueListItem, 'factory' | 'area' | 'line' | 'process' | 'factoryName' | 'areaName' | 'lineName' | 'processName'>) => {
  return [
    item.factoryName ?? item.factory,
    item.areaName ?? item.area,
    item.lineName ?? item.line,
    item.processName ?? item.process,
  ]
    .filter(Boolean)
    .join(' / ') || '未填写位置'
}

export const issueCategory = (item: Pick<CpsIssueListItem, 'categoryL1Name' | 'categoryL2Name'>) => {
  return [item.categoryL1Name, item.categoryL2Name].filter(Boolean).join(' / ') || '未分类'
}

/** 人员展示文本：姓名与工号相同时仅显示工号 */
export const personLabel = (empName?: string | null, empNo?: string | null) => {
  if (!empNo) return ''
  return empName && empName !== empNo ? `${empName} (${empNo})` : empNo
}
