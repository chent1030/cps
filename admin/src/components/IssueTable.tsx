import { Button, Empty, Table, Tag, Typography } from '@arco-design/web-react'
import type { TableColumnProps } from '@arco-design/web-react'
import { formatDate, statusLabel } from '../lib/presentation'
import type { Issue, IssueStatus } from '../types'

const statusColor: Record<IssueStatus, string> = { PENDING_FEEDBACK: 'orange', PENDING_RECTIFY: 'red', PENDING_UPLOAD_PROOF: 'arcoblue', PENDING_REVIEW: 'purple', CLOSED: 'green' }
const columns: TableColumnProps<Issue>[] = [
  { title: '工厂', dataIndex: 'factory', width: 120 }, { title: '区域', dataIndex: 'area', width: 120 }, { title: '拉线', dataIndex: 'line', width: 90 }, { title: '工序', dataIndex: 'process', width: 90 },
  { title: '当前节点', dataIndex: 'status', width: 118, render: (value: IssueStatus) => <Tag color={statusColor[value]}>{statusLabel[value]}</Tag> },
  { title: '提交时间', dataIndex: 'submitTime', width: 170, render: (value, record) => <>{formatDate(value)}{record.overdue && <Tag color="red" style={{ marginLeft: 8 }}>超期</Tag>}</> },
  { title: 'AI分类一', dataIndex: 'aiCategoryL1Name', width: 120, render: (v) => v || '-' }, { title: 'AI分类二', dataIndex: 'aiCategoryL2Name', width: 130, render: (v) => v || '-' },
  { title: '分类一', dataIndex: 'categoryL1Name', width: 120, render: (v) => v || '-' }, { title: '分类二', dataIndex: 'categoryL2Name', width: 130, render: (v) => v || '-' },
  { title: '描述', dataIndex: 'description', width: 240, ellipsis: true, render: (value) => <Typography.Text ellipsis={{ showTooltip: true }}>{value || '-'}</Typography.Text> },
  { title: '创建人', width: 130, render: (_, record) => record.creatorEmpNo ? `${record.creatorEmpNo}（${record.creatorEmpName || '-'}）` : '-' },
  { title: '反馈人', width: 130, render: (_, record) => record.feedbackEmpNo ? `${record.feedbackEmpNo}（${record.feedbackEmpName || '-'}）` : '-' },
  { title: '流程节点人', dataIndex: 'allFlowHandlers', width: 230, ellipsis: true, render: (v) => v || '-' },
  { title: '原因', dataIndex: 'reasonAnalysis', width: 220, ellipsis: true, render: (v) => v || '-' }, { title: '措施', dataIndex: 'correctiveMeasure', width: 220, ellipsis: true, render: (v) => v || '-' }, { title: '整改说明', dataIndex: 'rectifyRemark', width: 220, ellipsis: true, render: (v) => v || '-' }, { title: '审核意见', dataIndex: 'reviewOpinion', width: 220, ellipsis: true, render: (v) => v || '-' },
  { title: '更新时间', dataIndex: 'updatedAt', width: 170, render: (v) => formatDate(v) },
  { title: '问题图片', width: 150, render: (_, record) => <Thumbs ids={record.issueImageIds} /> }, { title: '整改图片', width: 150, render: (_, record) => <Thumbs ids={record.proofImageIds} /> },
]

function Thumbs({ ids }: { ids?: string }) { if (!ids) return <Typography.Text type="secondary">-</Typography.Text>; return <div style={{ display: 'flex', gap: 5 }}>{ids.split(',').map((id) => <img key={id} src={`/api/cps/attachments/${id}/content`} alt="问题附件" style={{ width: 42, height: 34, objectFit: 'cover', borderRadius: 4 }} />)}</div> }
export function IssueTable({ records, loading = false, onDetail }: { records: Issue[]; loading?: boolean; onDetail?: (issue: Issue) => void }) {
  const displayColumns = onDetail ? [...columns, { title: '操作', width: 92, fixed: 'right' as const, render: (_: unknown, issue: Issue) => <Button type="text" size="small" onClick={() => onDetail(issue)}>详情</Button> }] : columns
  return <Table<Issue> rowKey="id" columns={displayColumns} data={records} loading={loading} scroll={{ x: onDetail ? 3242 : 3150 }} pagination={false} noDataElement={<Empty description="暂无问题记录" />} />
}
