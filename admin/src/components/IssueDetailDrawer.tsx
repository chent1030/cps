import { Descriptions, Divider, Drawer, Image, Space, Tag, Typography } from '@arco-design/web-react'
import { formatDate, statusLabel } from '../lib/presentation'
import type { Issue, IssueStatus } from '../types'

const statusColor: Record<IssueStatus, string> = { PENDING_FEEDBACK: 'orange', PENDING_RECTIFY: 'red', PENDING_UPLOAD_PROOF: 'arcoblue', PENDING_REVIEW: 'purple', CLOSED: 'green' }
const attachmentUrl = (id: string) => `${(import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '')}/cps/attachments/${id}/content`
const person = (empNo?: string, empName?: string) => empNo ? `${empNo}（${empName || '-'}）` : '-'

function ImageGroup({ ids, label }: { ids?: string; label: string }) {
  const attachmentIds = ids?.split(',').map((id) => id.trim()).filter(Boolean) ?? []
  if (!attachmentIds.length) return <Typography.Text type="secondary">暂无{label}</Typography.Text>
  return <Image.PreviewGroup><Space wrap size={10}>{attachmentIds.map((id) => <Image key={id} src={attachmentUrl(id)} width={132} height={96} style={{ objectFit: 'cover' }} alt={label} />)}</Space></Image.PreviewGroup>
}

export function IssueDetailDrawer({ issue, onClose }: { issue: Issue | null; onClose: () => void }) {
  const details = issue ? [
    { label: '当前节点', value: <>{<Tag color={statusColor[issue.status]}>{statusLabel[issue.status]}</Tag>}{issue.overdue && <Tag color="red" style={{ marginLeft: 8 }}>超期</Tag>}</> },
    { label: '工厂', value: issue.factory || '-' }, { label: '区域', value: issue.area || '-' },
    { label: '拉线', value: issue.line || '-' }, { label: '工序', value: issue.process || '-' },
    { label: '提交时间', value: formatDate(issue.submitTime) }, { label: '更新时间', value: formatDate(issue.updatedAt) },
    { label: 'AI 一级分类', value: issue.aiCategoryL1Name || '-' }, { label: 'AI 二级分类', value: issue.aiCategoryL2Name || '-' },
    { label: '最终一级分类', value: issue.categoryL1Name || '-' }, { label: '最终二级分类', value: issue.categoryL2Name || '-' },
    { label: '创建人', value: person(issue.creatorEmpNo, issue.creatorEmpName) }, { label: '反馈人', value: person(issue.feedbackEmpNo, issue.feedbackEmpName) },
    { label: '所有流程节点人', value: issue.allFlowHandlers || '-', span: 2 },
    { label: '问题描述', value: issue.description || '-', span: 2 },
    { label: '原因分析', value: issue.reasonAnalysis || '-', span: 2 },
    { label: '整改措施', value: issue.correctiveMeasure || '-', span: 2 },
    { label: '整改说明', value: issue.rectifyRemark || '-', span: 2 },
    { label: '审核意见', value: issue.reviewOpinion || '-', span: 2 },
  ] : []
  return <Drawer visible={Boolean(issue)} onCancel={onClose} footer={null} title="问题详情" width={780} bodyStyle={{ padding: '20px 24px 32px' }}>
    {issue && <>
      <Descriptions border column={{ xs: 1, sm: 2 }} size="medium" layout="vertical" data={details} />
      <Divider orientation="left">问题图片</Divider>
      <ImageGroup ids={issue.issueImageIds} label="问题图片" />
      <Divider orientation="left">整改图片</Divider>
      <ImageGroup ids={issue.proofImageIds} label="整改图片" />
    </>}
  </Drawer>
}
