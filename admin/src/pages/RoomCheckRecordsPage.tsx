/**
 * 统一巡检记录追溯页（Phase C1，/api/cps/admin/inspection-records）。
 * - 列表：类型 × 区域 × 状态 × 巡检人筛选，得分/提交时间
 * - 详情：逐项 点检类型 / 合格判定 / 文本说明 / 照片张数 / 最终结果 + 修正留痕
 * - 历史辅房点检记录已由 V20261024 迁移进本表（sourceRecordId 追溯）
 */
import { Button, Card, Descriptions, Divider, Drawer, Form, Input, Select, Space, Spin, Table, Tag, Typography } from '@arco-design/web-react'
import type { TableColumnProps } from '@arco-design/web-react'
import { IconFilter, IconRefresh } from '@arco-design/web-react/icon'
import { useState } from 'react'
import { api } from '../api'
import { InspectionItemPhotos } from '../components/AttachmentImages'
import { useRemote } from '../hooks/useRemote'
import { PageHead, StatePanel } from '../components/PageParts'
import type { InspectionRecord, InspectionRecordItem } from '../types'

const recordStatusColor: Record<string, string> = { PENDING: 'arcoblue', IN_PROGRESS: 'orange', AWAIT_CONFIRM: 'cyan', JUDGED: 'green' }
const recordStatusLabel: Record<string, string> = { PENDING: '待执行', IN_PROGRESS: '进行中', AWAIT_CONFIRM: '待确认', JUDGED: '已判定' }
const resultColor: Record<string, string> = { PASS: 'green', FAIL: 'red', TYPE_MISMATCH: 'orange', UNJUDGEABLE: 'orange', PENDING: 'gray' }
const resultLabel: Record<string, string> = { PASS: '合格', FAIL: '不合格', TYPE_MISMATCH: '类型不符', UNJUDGEABLE: '无法判定', PENDING: '待判定' }
const checkTypeLabel: Record<string, string> = { PASS_FAIL: '合格判定', TEXT: '文本', PHOTO: '拍照' }

const statusOptions = Object.entries(recordStatusLabel).map(([value, label]) => ({ value, label }))

function RecordDetailDrawer({ recordId, onClose }: { recordId: number; onClose: () => void }) {
  const remote = useRemote(() => api.inspectionRecordDetail(recordId), [recordId])
  const detail = remote.data

  return <Drawer visible onCancel={onClose} title={detail ? `巡检记录 #${detail.record.id}（${detail.record.areaName ?? detail.record.typeCode}）` : '巡检记录'} width={900} footer={null}>
    {!detail ? <div style={{ minHeight: 320, display: 'grid', placeItems: 'center' }}><Spin tip="正在加载记录" /></div> : <>
      <Descriptions
        border
        column={{ xs: 1, sm: 2, md: 3 }}
        size="small"
        data={[
          { label: '状态', value: <Tag color={recordStatusColor[detail.record.recordStatus] ?? 'gray'}>{recordStatusLabel[detail.record.recordStatus] ?? detail.record.recordStatus}</Tag> },
          { label: '巡检类型', value: <Tag color="arcoblue">{detail.record.typeCode}</Tag> },
          { label: '区域', value: detail.record.areaCode ? `${detail.record.areaName ?? ''}（${detail.record.areaCode}）` : <Typography.Text type="secondary">不分区域</Typography.Text> },
          { label: '得分', value: detail.record.score == null ? <Typography.Text type="secondary">未计分</Typography.Text> : <Typography.Text bold style={{ color: '#0f766e' }}>{detail.record.score}</Typography.Text> },
          { label: '巡检人', value: detail.record.checkEmpName || detail.record.checkEmpNo },
          { label: '提交时间', value: detail.record.submittedAt?.replace('T', ' ').slice(0, 19) ?? '-' },
          ...(detail.record.sourceRecordId ? [{ label: '历史迁移', value: <Typography.Text type="secondary">原辅房点检 #{detail.record.sourceRecordId}</Typography.Text> }] : []),
        ]}
      />
      <Divider orientation="left">点检明细（{detail.items.length} 项）</Divider>
      <Table<InspectionRecordItem>
        rowKey="id"
        size="small"
        pagination={false}
        scroll={{ x: 900 }}
        data={detail.items}
        columns={[
          { title: '点检项', width: 110, render: (_, r) => <Typography.Text copyable style={{ fontSize: 12 }}>{r.itemCode}</Typography.Text> },
          { title: '内容', width: 200, render: (_, r) => <Typography.Text style={{ fontSize: 12 }}>{r.content || '-'}</Typography.Text> },
          {
            title: '点检类型', width: 150,
            render: (_, r) => (r.checkTypes ?? '').split(',').filter(Boolean).map((t) => <Tag key={t} style={{ marginRight: 4, fontSize: 11 }}>{checkTypeLabel[t] ?? t}</Tag>),
          },
          {
            title: '执行结果', width: 220,
            render: (_, r) => <Space direction="vertical" size={2}>
              {r.passResult && <Tag color={resultColor[r.passResult] ?? 'gray'}>{resultLabel[r.passResult] ?? r.passResult}</Tag>}
              {r.textValue && <Typography.Text type="secondary" style={{ fontSize: 12, whiteSpace: 'pre-wrap' }}>文本：{r.textValue}</Typography.Text>}
              {(r.checkTypes ?? '').includes('PHOTO') && (
                <InspectionItemPhotos item={r} width={56} height={42} />
              )}
            </Space>,
          },
          {
            title: '最终结果', width: 100,
            render: (_, r) => {
              const final = r.finalResult ?? r.judgeResult
              return <Space direction="vertical" size={4}>
                {final ? <Tag color={resultColor[final] ?? 'gray'}>{resultLabel[final] ?? final}</Tag> : <Typography.Text type="secondary">-</Typography.Text>}
                {r.finalModifiedBy && <Tag color="purple" style={{ fontSize: 11 }}>人工修正</Tag>}
              </Space>
            },
          },
          {
            title: '判定/修正留痕', render: (_, r) => r.finalModifiedBy
              ? <Typography.Text style={{ fontSize: 12, whiteSpace: 'pre-wrap' }}>
                  {r.finalModifiedBy} · {r.finalModifiedAt?.replace('T', ' ').slice(0, 19) ?? '-'}
                  {r.finalModifyReason ? `\n原因：${r.finalModifyReason}` : ''}
                </Typography.Text>
              : r.judgeReason
                ? <Typography.Text type="secondary" style={{ fontSize: 12, whiteSpace: 'pre-wrap' }}>AI：{r.judgeReason}</Typography.Text>
                : <Typography.Text type="secondary" style={{ fontSize: 12 }}>-</Typography.Text>,
          },
        ]}
      />
    </>}
  </Drawer>
}

export function RoomCheckRecordsPage() {
  const [statusFilter, setStatusFilter] = useState('')
  const [typeFilter, setTypeFilter] = useState('')
  const [keyword, setKeyword] = useState('')
  const [applied, setApplied] = useState({ status: '', typeCode: '', keyword: '' })
  const [page, setPage] = useState(1)
  const [selectedId, setSelectedId] = useState<number | null>(null)

  const typesRemote = useRemote(() => api.inspectionTypes(), [])
  const typeOptions = (typesRemote.data ?? []).map((t) => ({ value: t.code, label: `${t.name}（${t.code}）` }))
  const typeName = (code: string) => typesRemote.data?.find((t) => t.code === code)?.name ?? code

  const remote = useRemote(() => api.inspectionRecordPage({
    recordStatus: applied.status || undefined,
    typeCode: applied.typeCode || undefined,
    keyword: applied.keyword || undefined,
    page,
    pageSize: 20,
  }), [applied, page])

  const rows = remote.data?.records ?? []

  const columns: TableColumnProps<InspectionRecord>[] = [
    { title: 'ID', dataIndex: 'id', width: 72 },
    { title: '巡检类型', dataIndex: 'typeCode', width: 130, render: (v: string) => <Tag color="arcoblue">{typeName(v)}</Tag> },
    { title: '区域', width: 170, render: (_, r) => r.areaCode ? <>{r.areaName || '-'} <Typography.Text type="secondary">{r.areaCode}</Typography.Text></> : <Typography.Text type="secondary">不分区域</Typography.Text> },
    { title: '巡检人', width: 96, render: (_, r) => r.checkEmpName || r.checkEmpNo || '-' },
    { title: '状态', width: 88, render: (_, r) => <Tag color={recordStatusColor[r.recordStatus] ?? 'gray'}>{recordStatusLabel[r.recordStatus] ?? r.recordStatus}</Tag> },
    { title: '得分', width: 72, render: (_, r) => r.score == null ? <Typography.Text type="secondary">-</Typography.Text> : <Typography.Text bold style={{ color: '#0f766e' }}>{r.score}</Typography.Text> },
    { title: '来源', width: 90, render: (_, r) => r.sourceRecordId ? <Tag color="gray">历史迁移</Tag> : <Typography.Text type="secondary">新流程</Typography.Text> },
    { title: '提交时间', width: 150, render: (_, r) => r.submittedAt ? r.submittedAt.replace('T', ' ').slice(0, 19) : '-' },
    { title: '操作', width: 80, align: 'right', render: (_, r) => <Button type="text" size="small" onClick={() => setSelectedId(r.id)}>详情</Button> },
  ]

  function apply() {
    setPage(1)
    setApplied({ status: statusFilter, typeCode: typeFilter, keyword: keyword.trim() })
  }

  return <>
    <PageHead title="巡检记录" detail="统一巡检记录追溯（含历史辅房点检迁移数据）；得分 = max(0, 100 − Σ不合格扣分)" />
    <div className="page-body">
      <Card className="filter-card">
        <Form layout="inline" className="filter-bar" onSubmit={() => apply()}>
          <Select allowClear value={typeFilter || undefined} placeholder="按巡检类型筛选" style={{ width: 190 }} onChange={(v) => setTypeFilter(v ?? '')} options={typeOptions} />
          <Select allowClear value={statusFilter || undefined} placeholder="按状态筛选" style={{ width: 150 }} onChange={(v) => setStatusFilter(v ?? '')} options={statusOptions} />
          <Input allowClear value={keyword} placeholder="区域/巡检人（模糊）" style={{ width: 190 }} onChange={setKeyword} />
          <Button type="primary" htmlType="submit" icon={<IconFilter />}>筛选</Button>
          <Button icon={<IconRefresh />} onClick={() => void remote.refresh()}>刷新</Button>
        </Form>
      </Card>
      <StatePanel {...remote}>{remote.data && <Card className="table-card">
        <Table
          rowKey="id"
          columns={columns}
          data={rows}
          scroll={{ x: 1080 }}
          pagination={{
            total: remote.data.total,
            current: page,
            pageSize: 20,
            showTotal: true,
            onChange: (next) => setPage(next),
          }}
        />
      </Card>}</StatePanel>
    </div>
    {selectedId != null && <RecordDetailDrawer key={selectedId} recordId={selectedId} onClose={() => setSelectedId(null)} />}
  </>
}
