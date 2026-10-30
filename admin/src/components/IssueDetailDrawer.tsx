import { Descriptions, Divider, Drawer, Tag, Typography } from '@arco-design/web-react'
import { AttachmentThumbs } from './AttachmentImages'
import { StructuredValue } from './StructuredValue'
import { splitAttachmentUrls } from '../lib/attachmentUrl'
import { formatDate, statusLabel } from '../lib/presentation'
import type { Issue, IssueStatus } from '../types'

const statusColor: Record<IssueStatus, string> = { PENDING_FEEDBACK: 'orange', PENDING_RECTIFY: 'red', PENDING_UPLOAD_PROOF: 'arcoblue', PENDING_REVIEW: 'purple', CLOSED: 'green' }
const person = (empNo?: string, empName?: string) => empNo ? `${empNo}（${empName || '-'}）` : '-'

/** 问题图片 / 整改图片：字段是逗号分隔的附件 ID，也可能已经是绝对 URL（绝对 URL 优先）。 */
function ImageGroup({ ids, label }: { ids?: string; label: string }) {
  const urls = splitAttachmentUrls(ids)
  if (!urls.length) return <Typography.Text type="secondary">暂无{label}</Typography.Text>
  return <AttachmentThumbs values={urls} width={132} height={96} alt={label} />
}

export function IssueDetailDrawer({ issue, onClose }: { issue: Issue | null; onClose: () => void }) {
  const details = issue ? [
    { label: '当前节点', value: <>{<Tag color={statusColor[issue.status]}>{statusLabel[issue.status]}</Tag>}{issue.overdue && <Tag color="red" style={{ marginLeft: 8 }}>超期</Tag>}</> },
    { label: '工厂', value: issue.factory || '-' }, { label: '区域', value: issue.area || '-' },
    { label: '拉线', value: issue.line || '-' }, { label: '工序', value: issue.process || '-' },
    { label: '资源号', value: issue.resourceNo || '-' },
    { label: '巡检类型', value: issue.inspectionTypeCode ? <Tag>{issue.inspectionTypeCode}</Tag> : '-' },
    ...(issue.extraData ? [{ label: '动态字段', value: <StructuredValue value={issue.extraData} textRows={8} /> }] : []),
    { label: '提交时间', value: formatDate(issue.submitTime) }, { label: '更新时间', value: formatDate(issue.updatedAt) },
    { label: 'AI 一级分类', value: issue.aiCategoryL1Name || '-' }, { label: 'AI 二级分类', value: issue.aiCategoryL2Name || '-' },
    { label: '最终一级分类', value: issue.categoryL1Name || '-' }, { label: '最终二级分类', value: issue.categoryL2Name || '-' },
    { label: '创建人', value: person(issue.creatorEmpNo, issue.creatorEmpName) }, { label: '反馈人', value: person(issue.feedbackEmpNo, issue.feedbackEmpName) },
    { label: '所有流程节点人', value: <StructuredValue value={issue.allFlowHandlers} />, span: 2 },
    { label: '问题描述', value: <StructuredValue value={issue.description} textRows={5} />, span: 2 },
    { label: '原因分析', value: <StructuredValue value={issue.reasonAnalysis} textRows={5} />, span: 2 },
    { label: '整改措施', value: <StructuredValue value={issue.correctiveMeasure} textRows={5} />, span: 2 },
    { label: '整改说明', value: <StructuredValue value={issue.rectifyRemark} textRows={5} />, span: 2 },
    { label: '审核意见', value: <StructuredValue value={issue.reviewOpinion} textRows={5} />, span: 2 },
  ] : []
  return <Drawer visible={Boolean(issue)} onCancel={onClose} footer={null} title="问题详情" width={720} bodyStyle={{ padding: '20px 24px 32px' }}>
    {issue && <>
      <Descriptions border column={{ xs: 1, sm: 2 }} size="medium" layout="vertical" data={details} />
      <Divider orientation="left">问题图片</Divider>
      <ImageGroup ids={issue.issueImageIds} label="问题图片" />
      <Divider orientation="left">整改图片</Divider>
      <ImageGroup ids={issue.proofImageIds} label="整改图片" />
    </>}
  </Drawer>
}
