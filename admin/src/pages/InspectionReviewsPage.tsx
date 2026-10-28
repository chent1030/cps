/**
 * 巡检复核页（/api/cps/inspection-tasks，非 CPS 巡检）。
 * - 列表：待复核记录（提交时间 / 类型 / 区域 / 巡检人 / AI 初审 / 得分 / 轮次）
 * - 详情：记录信息 + 点检项（AI 判定 / 巡检员判定 / 照片）+ 流转日志 + 复核操作
 * - 操作：通过 → PENDING_RECTIFY（区域责任人整改）；退回 → IN_PROGRESS（提交人修改重提）
 */
import {
  Alert, Button, Card, Descriptions, Divider, Drawer, Empty, Input, Message, Result, Space, Spin, Table, Tag, Timeline, Typography,
} from '@arco-design/web-react'
import type { TableColumnProps } from '@arco-design/web-react'
import { IconCheckCircle, IconRefresh, IconReply } from '@arco-design/web-react/icon'
import { useState } from 'react'
import { api, getAdminOperator } from '../api'
import { AttachmentThumb, InspectionItemPhotos } from '../components/AttachmentImages'
import { PageHead, StatePanel } from '../components/PageParts'
import { useRemote } from '../hooks/useRemote'
import { attachmentDisplayUrl } from '../lib/attachmentUrl'
import { errorMessage } from '../lib/presentation'
import type { InspectionRecord, InspectionRecordItem, InspectionRecordLog, InspectionRectifyAttachment } from '../types'

const recordStatusColor: Record<string, string> = {
  PENDING: 'gray', IN_PROGRESS: 'orange', PENDING_REVIEW: 'arcoblue', PENDING_RECTIFY: 'gold', JUDGED: 'green',
}
const recordStatusLabel: Record<string, string> = {
  PENDING: '待执行', IN_PROGRESS: '执行中', PENDING_REVIEW: '待复核', PENDING_RECTIFY: '待整改', JUDGED: '已结束',
}
const resultColor: Record<string, string> = { PASS: 'green', FAIL: 'red', TYPE_MISMATCH: 'orange', UNJUDGEABLE: 'orange', PENDING: 'gray' }
const resultLabel: Record<string, string> = { PASS: '合格', FAIL: '不合格', TYPE_MISMATCH: '类型不符', UNJUDGEABLE: '无法判定', PENDING: '待判定' }
const checkTypeLabel: Record<string, string> = { PASS_FAIL: '合格判定', TEXT: '文本', PHOTO: '拍照' }
const aiOverallColor: Record<string, string> = { PASS: 'green', PARTIAL: 'orange', PROBLEM: 'red', NONE: 'gray' }
const aiOverallLabel: Record<string, string> = { PASS: '合格', PARTIAL: '部分问题', PROBLEM: '存在问题', NONE: '无结论' }
const actionLabel: Record<string, string> = {
  START: '开始执行', SUBMIT: '提交复核', REVIEW_PASS: '复核通过', REVIEW_RETURN: '复核退回', RECTIFY_SUBMIT: '整改提交',
}
const actionColor: Record<string, string> = { START: 'gray', SUBMIT: 'arcoblue', REVIEW_PASS: 'green', REVIEW_RETURN: 'red', RECTIFY_SUBMIT: 'green' }

const formatTime = (value?: string | null) => (value ? value.replace('T', ' ').slice(0, 19) : '-')
const person = (empNo?: string | null, empName?: string | null) => (empNo ? `${empName || '-'}（${empNo}）` : '-')
const statusTag = (status?: string | null) => <Tag color={recordStatusColor[status ?? ''] ?? 'gray'}>{recordStatusLabel[status ?? ''] ?? status ?? '-'}</Tag>

/**
 * 整改照片墙：优先绝对 URL（MinIO 直链），历史数据按附件 ID 回退 /content 代理路径；
 * 加载失败的图片由 AttachmentThumb 丢弃，保留文件名说明。
 */
function RectifyPhotoWall({ attachments }: { attachments: InspectionRectifyAttachment[] }) {
  return <Space wrap size={10}>
    {attachments.map((attachment) => <div key={attachment.id} style={{ width: 132 }}>
      <AttachmentThumb
        value={attachmentDisplayUrl(attachment)}
        width={132}
        height={96}
        alt={attachment.fileName || `整改照片 #${attachment.id}`}
      />
      <div style={{ marginTop: 2, fontSize: 11, color: 'var(--color-text-3)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
        {attachment.fileName || `附件 #${attachment.id}`}
      </div>
    </div>)}
  </Space>
}

function LogTimeline({ logs }: { logs: InspectionRecordLog[] }) {
  if (logs.length === 0) return <Empty description="暂无流转日志" />
  return <Timeline>
    {logs.map((log) => <Timeline.Item key={log.id} dotColor={actionColor[log.action] ?? 'blue'} label={formatTime(log.createdAt)}>
      <Space size={6} wrap>
        <Tag color={actionColor[log.action] ?? 'arcoblue'}>{actionLabel[log.action] ?? log.action}</Tag>
        <Typography.Text style={{ fontSize: 12 }}>
          {log.operatorEmpName || log.operatorEmpNo || '系统'}
          {log.fromStatus && log.toStatus && <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {' '}· {recordStatusLabel[log.fromStatus] ?? log.fromStatus} → {recordStatusLabel[log.toStatus] ?? log.toStatus}
          </Typography.Text>}
        </Typography.Text>
      </Space>
      {log.comment && <div style={{ marginTop: 4, fontSize: 12, color: 'var(--color-text-2)', whiteSpace: 'pre-wrap' }}>{log.comment}</div>}
    </Timeline.Item>)}
  </Timeline>
}

function ReviewDetailDrawer({ recordId, onClose, onReviewed }: { recordId: number; onClose: () => void; onReviewed: () => void }) {
  const remote = useRemote(() => api.inspectionReviewDetail(recordId), [recordId])
  const detail = remote.data
  const [opinion, setOpinion] = useState('')
  const [submitting, setSubmitting] = useState<'PASS' | 'RETURN' | null>(null)
  const operator = getAdminOperator()

  async function submit(decision: 'PASS' | 'RETURN') {
    if (!detail) return
    const text = opinion.trim()
    if (!operator?.empNo) {
      Message.error('未获取到当前登录人信息，请重新登录后再复核')
      return
    }
    if (decision === 'RETURN' && !text) {
      Message.warning('退回修改必须填写复核意见')
      return
    }
    setSubmitting(decision)
    try {
      await api.reviewInspectionRecord(detail.record.id, decision, text)
      Message.success(decision === 'PASS' ? '复核通过，已转区域责任人整改' : '已退回提交人修改')
      setSubmitting(null)
      onReviewed()
    } catch (error) {
      Message.error(errorMessage(error))
      setSubmitting(null)
    }
  }

  const record = detail?.record
  const items = detail?.items ?? []
  const logs = detail?.logs ?? []
  const attachments = detail?.rectifyAttachments ?? []

  return <Drawer
    visible
    onCancel={onClose}
    footer={null}
    width={960}
    title={record ? `巡检复核 #${record.id}${record.areaName ? `（${record.areaName}）` : ''}` : '巡检复核'}
    bodyStyle={{ padding: '20px 24px 32px' }}
  >
    {remote.error ? <Result
      status="error"
      title="复核详情加载失败"
      subTitle={remote.error}
      extra={<Button type="primary" icon={<IconRefresh />} onClick={() => void remote.refresh()}>重新加载</Button>}
    /> : !detail ? <div style={{ minHeight: 320, display: 'grid', placeItems: 'center' }}><Spin tip="正在加载记录" /></div> : record && <>
      <Descriptions
        border
        column={{ xs: 1, sm: 2, md: 3 }}
        size="small"
        data={[
          { label: '记录状态', value: statusTag(record.recordStatus) },
          { label: '巡检类型', value: <Tag color="arcoblue">{record.typeCode}</Tag> },
          { label: '区域', value: record.areaCode ? `${record.areaName ?? ''}（${record.areaCode}）` : <Typography.Text type="secondary">不分区域</Typography.Text> },
          { label: '巡检人', value: person(record.checkEmpNo, record.checkEmpName) },
          { label: '提交时间', value: formatTime(record.submittedAt) },
          { label: '复核轮次', value: `第 ${record.reviewRound ?? 0} 轮` },
          { label: '当前得分', value: record.score == null ? <Typography.Text type="secondary">未计分</Typography.Text> : <Typography.Text bold style={{ color: '#0f766e' }}>{record.score}</Typography.Text> },
          { label: '当前办理人', value: person(record.currentHandlerEmpNo, record.currentHandlerEmpName) },
          {
            label: 'AI 初审结论',
            value: <Space size={4}>
              <Tag color={aiOverallColor[record.aiOverall ?? 'NONE'] ?? 'gray'}>{aiOverallLabel[record.aiOverall ?? 'NONE'] ?? record.aiOverall ?? '无结论'}</Tag>
              {record.aiScore != null && <Typography.Text type="secondary" style={{ fontSize: 12 }}>{record.aiScore} 分</Typography.Text>}
            </Space>,
          },
          {
            label: 'AI 初审意见',
            value: record.aiReason
              ? <Typography.Text style={{ fontSize: 12, whiteSpace: 'pre-wrap' }}>{record.aiReason}</Typography.Text>
              : <Typography.Text type="secondary" style={{ fontSize: 12 }}>AI 初审无结论（人工复核）</Typography.Text>,
            span: 3,
          },
          ...(record.reviewEmpNo ? [
            { label: '最近复核人', value: person(record.reviewEmpNo, record.reviewEmpName) },
            { label: '最近复核时间', value: formatTime(record.reviewedAt) },
            { label: '最近复核意见', value: record.reviewOpinion || '-', span: 2 },
          ] : []),
          ...(record.rectifyEmpNo ? [
            { label: '整改责任人', value: person(record.rectifyEmpNo, record.rectifyEmpName) },
            { label: '整改提交时间', value: formatTime(record.rectifySubmittedAt) },
          ] : []),
          ...(record.rectifyReason ? [{ label: '整改原因', value: record.rectifyReason, span: 3 }] : []),
          ...(record.rectifyShortTerm ? [{ label: '短期措施', value: record.rectifyShortTerm, span: 3 }] : []),
          ...(record.rectifyLongTerm ? [{ label: '长期措施', value: record.rectifyLongTerm, span: 3 }] : []),
          ...(record.rectifyAiOverall ? [{
            label: '整改 AI 结论',
            value: `${aiOverallLabel[record.rectifyAiOverall] ?? record.rectifyAiOverall}${record.rectifyAiReason ? `：${record.rectifyAiReason}` : ''}`,
            span: 3,
          }] : []),
        ]}
      />

      <Divider orientation="left">点检项（{items.length} 项）</Divider>
      <Table<InspectionRecordItem>
        rowKey="id"
        size="small"
        pagination={false}
        scroll={{ x: 960 }}
        data={items}
        noDataElement={<Empty description="暂无点检项" />}
        columns={[
          {
            title: '点检项',
            width: 200,
            render: (_, item) => <Space direction="vertical" size={2}>
              <Typography.Text style={{ fontSize: 12 }}>{item.content || '-'}</Typography.Text>
              <Typography.Text type="secondary" style={{ fontSize: 11 }}>{item.itemCode}</Typography.Text>
            </Space>,
          },
          {
            title: '点检类型',
            width: 110,
            render: (_, item) => (item.checkTypes ?? '').split(',').filter(Boolean).map((type) => <Tag key={type} style={{ marginRight: 4, fontSize: 11 }}>
              {checkTypeLabel[type] ?? type}
            </Tag>),
          },
          {
            title: 'AI 判定',
            width: 180,
            render: (_, item) => <Space direction="vertical" size={2}>
              {item.judgeResult
                ? <Tag color={resultColor[item.judgeResult] ?? 'gray'}>{resultLabel[item.judgeResult] ?? item.judgeResult}</Tag>
                : <Typography.Text type="secondary" style={{ fontSize: 12 }}>无 AI 判定</Typography.Text>}
              {item.judgeReason && <Typography.Text type="secondary" style={{ fontSize: 11 }}>{item.judgeReason}</Typography.Text>}
            </Space>,
          },
          {
            title: '扣分',
            width: 64,
            render: (_, item) => item.deductScore > 0
              ? <Typography.Text style={{ color: '#b42318', fontSize: 12 }}>-{item.deductScore}</Typography.Text>
              : <Typography.Text type="secondary" style={{ fontSize: 12 }}>0</Typography.Text>,
          },
          {
            title: '巡检员判定',
            width: 130,
            render: (_, item) => <Space direction="vertical" size={2}>
              {item.passResult
                ? <Tag color={resultColor[item.passResult] ?? 'gray'}>{resultLabel[item.passResult] ?? item.passResult}</Tag>
                : <Typography.Text type="secondary" style={{ fontSize: 12 }}>-</Typography.Text>}
              {item.textValue && <Typography.Text type="secondary" style={{ fontSize: 11, whiteSpace: 'pre-wrap' }}>{item.textValue}</Typography.Text>}
            </Space>,
          },
          {
            title: '照片',
            width: 160,
            render: (_, item) => (item.checkTypes ?? '').includes('PHOTO') || item.photoObjectKey
              ? <InspectionItemPhotos item={item} />
              : <Typography.Text type="secondary" style={{ fontSize: 12 }}>-</Typography.Text>,
          },
          {
            title: '最终结果',
            width: 90,
            render: (_, item) => {
              const final = item.finalResult ?? item.judgeResult
              return final
                ? <Tag color={resultColor[final] ?? 'gray'}>{resultLabel[final] ?? final}</Tag>
                : <Typography.Text type="secondary" style={{ fontSize: 12 }}>-</Typography.Text>
            },
          },
        ]}
      />

      {attachments.length > 0 && <>
        <Divider orientation="left">整改照片（{attachments.length} 张）</Divider>
        <RectifyPhotoWall attachments={attachments} />
      </>}

      <Divider orientation="left">流转日志</Divider>
      <LogTimeline logs={logs} />

      <Divider orientation="left">复核意见</Divider>
      {record.recordStatus === 'PENDING_REVIEW' ? <>
        {!operator?.empNo && <Alert
          type="warning"
          content="未获取到当前登录人信息（empNo），无法提交复核，请重新登录后再操作。"
          style={{ marginBottom: 12 }}
        />}
        <Input.TextArea
          value={opinion}
          onChange={setOpinion}
          placeholder="通过可选填；退回必须填写需要修改的内容"
          maxLength={500}
          showWordLimit
          autoSize={{ minRows: 3, maxRows: 6 }}
        />
        <Space style={{ marginTop: 12 }} wrap>
          <Button
            type="primary"
            icon={<IconCheckCircle />}
            loading={submitting === 'PASS'}
            disabled={!operator?.empNo || submitting !== null}
            onClick={() => void submit('PASS')}
          >通过并转整改</Button>
          <Button
            status="danger"
            icon={<IconReply />}
            loading={submitting === 'RETURN'}
            disabled={!operator?.empNo || submitting !== null}
            onClick={() => void submit('RETURN')}
          >退回修改</Button>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            通过后转区域责任人整改；退回后提交人修改重提，重提会重跑 AI 初审
          </Typography.Text>
        </Space>
      </> : <Alert
        type="info"
        content={`当前状态为「${recordStatusLabel[record.recordStatus] ?? record.recordStatus}」，只有待复核记录可执行复核。`}
      />}
    </>}
  </Drawer>
}

export function InspectionReviewsPage() {
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const remote = useRemote(() => api.inspectionReviews(100), [])

  const columns: TableColumnProps<InspectionRecord>[] = [
    { title: '提交时间', width: 150, render: (_, record) => formatTime(record.submittedAt) },
    { title: '巡检类型', width: 130, render: (_, record) => <Tag color="arcoblue">{record.typeCode}</Tag> },
    {
      title: '区域',
      width: 170,
      render: (_, record) => record.areaCode
        ? <>{record.areaName || '-'} <Typography.Text type="secondary">{record.areaCode}</Typography.Text></>
        : <Typography.Text type="secondary">不分区域</Typography.Text>,
    },
    { title: '巡检人', width: 110, render: (_, record) => record.checkEmpName || record.checkEmpNo || '-' },
    {
      title: 'AI 结论',
      width: 230,
      render: (_, record) => {
        const overall = record.aiOverall ?? 'NONE'
        return <Space direction="vertical" size={2}>
          <Space size={4}>
            <Tag color={aiOverallColor[overall] ?? 'gray'}>{aiOverallLabel[overall] ?? overall}</Tag>
            {record.aiScore != null && <Typography.Text type="secondary" style={{ fontSize: 12 }}>{record.aiScore} 分</Typography.Text>}
          </Space>
          {record.aiReason && <div style={{
            fontSize: 11, color: 'var(--color-text-3)', overflow: 'hidden', display: '-webkit-box',
            WebkitLineClamp: 2, WebkitBoxOrient: 'vertical',
          }}>{record.aiReason}</div>}
        </Space>
      },
    },
    {
      title: '得分',
      width: 72,
      render: (_, record) => record.score == null
        ? <Typography.Text type="secondary">-</Typography.Text>
        : <Typography.Text bold style={{ color: '#0f766e' }}>{record.score}</Typography.Text>,
    },
    { title: '轮次', width: 80, render: (_, record) => `第 ${record.reviewRound ?? 0} 轮` },
    {
      title: '操作',
      width: 90,
      align: 'right',
      render: (_, record) => <Button type="text" size="small" onClick={(event) => {
        event.stopPropagation()
        setSelectedId(record.id)
      }}>复核</Button>,
    },
  ]

  return <>
    <PageHead title="巡检复核" detail="非 CPS 巡检提交后的人工复核：通过转区域责任人整改，退回由提交人修改重提">
      <Button icon={<IconRefresh />} onClick={() => void remote.refresh()}>刷新</Button>
    </PageHead>
    <div className="page-body">
      <StatePanel {...remote} onRetry={() => void remote.refresh()}>
        {remote.data && <Card className="table-card">
          <Table<InspectionRecord>
            rowKey="id"
            columns={columns}
            data={remote.data}
            scroll={{ x: 1030 }}
            pagination={false}
            noDataElement={<Empty description="暂无待复核记录" />}
            onRow={(record) => ({ onClick: () => setSelectedId(record.id), style: { cursor: 'pointer' } })}
          />
        </Card>}
      </StatePanel>
    </div>
    {selectedId != null && <ReviewDetailDrawer
      key={selectedId}
      recordId={selectedId}
      onClose={() => setSelectedId(null)}
      onReviewed={() => {
        setSelectedId(null)
        void remote.refresh()
      }}
    />}
  </>
}
