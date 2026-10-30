import { Button, Card, Descriptions, Drawer, Empty, Form, Input, Message, Modal, Pagination, Select, Space, Table, Tag, Timeline, Tooltip, Typography } from '@arco-design/web-react'
import type { TableColumnProps } from '@arco-design/web-react'
import { IconCheck, IconClose, IconRefresh, IconSearch, IconUndo } from '@arco-design/web-react/icon'
import { useState } from 'react'
import { approvals, getAdminOperator } from '../api'
import { errorMessage } from '../lib/presentation'
import { DataCard, FilterBar, PageHead, StatePanel } from '../components/PageParts'
import { ApprovalSnapshot } from '../components/ApprovalSnapshot'
import { StructuredValue } from '../components/StructuredValue'
import { useRemote } from '../hooks/useRemote'
import {
  APPROVAL_ACTION_COLORS,
  APPROVAL_ACTION_LABELS,
  APPROVAL_BIZ_TYPE_LABELS,
  APPROVAL_INSTANCE_STATUS_COLORS,
  APPROVAL_INSTANCE_STATUS_LABELS,
} from '../types'
import type { ApprovalAction, ApprovalDetail, ApprovalInstance, ApprovalInstanceStatus } from '../types'

const STATUS_FILTER_OPTIONS: { label: string; value: '' | ApprovalInstanceStatus }[] = [
  { label: '全部状态', value: '' },
  { label: APPROVAL_INSTANCE_STATUS_LABELS.PENDING, value: 'PENDING' },
  { label: APPROVAL_INSTANCE_STATUS_LABELS.APPROVED, value: 'APPROVED' },
  { label: APPROVAL_INSTANCE_STATUS_LABELS.REJECTED, value: 'REJECTED' },
  { label: APPROVAL_INSTANCE_STATUS_LABELS.WITHDRAWN, value: 'WITHDRAWN' },
]

const BIZ_TYPE_FILTER_OPTIONS = [
  { label: '全部业务', value: '' },
  ...Object.entries(APPROVAL_BIZ_TYPE_LABELS).map(([k, v]) => ({ label: v, value: k })),
]

const initialFilters = { keyword: '', status: '' as '' | ApprovalInstanceStatus, bizType: '' }

export function ApprovalCenterPage() {
  const [filters, setFilters] = useState(initialFilters)
  const [query, setQuery] = useState({ ...initialFilters, page: 1, pageSize: 20 })
  const [detail, setDetail] = useState<{ open: boolean; instanceId: number | null; loading: boolean; data: ApprovalDetail | null }>({
    open: false, instanceId: null, loading: false, data: null,
  })
  const [rejectModal, setRejectModal] = useState<{ open: boolean; instance: ApprovalInstance | null; reason: string }>({
    open: false, instance: null, reason: '',
  })
  const [approveModal, setApproveModal] = useState<{ open: boolean; instance: ApprovalInstance | null; comment: string }>({
    open: false, instance: null, comment: '',
  })
  const [acting, setActing] = useState(false)
  const operator = getAdminOperator()
  const canOperate = !!operator?.superAdmin

  const remote = useRemote(() => approvals.listAll({
    status: query.status || undefined,
    bizType: query.bizType || undefined,
    keyword: query.keyword || undefined,
    page: query.page,
    pageSize: query.pageSize,
  }), [query])

  const pendingRemote = useRemote(() => approvals.listPending({ pageSize: 5 }), [query.page])

  const openDetail = async (instanceId: number) => {
    setDetail({ open: true, instanceId, loading: true, data: null })
    try {
      const data = await approvals.getDetail(instanceId)
      setDetail({ open: true, instanceId, loading: false, data })
    } catch (e) {
      setDetail((c) => ({ ...c, loading: false }))
      Message.error(errorMessage(e))
    }
  }

  const closeDetail = () => setDetail({ open: false, instanceId: null, loading: false, data: null })

  const refreshAll = () => {
    remote.refresh()
    pendingRemote.refresh()
  }

  const doApprove = async () => {
    if (!approveModal.instance) return
    setActing(true)
    try {
      await approvals.act(approveModal.instance.id, 'APPROVE', approveModal.comment)
      Message.success('已批准')
      setApproveModal({ open: false, instance: null, comment: '' })
      refreshAll()
      if (detail.open && detail.instanceId === approveModal.instance.id) {
        const data = await approvals.getDetail(approveModal.instance.id)
        setDetail((c) => ({ ...c, data }))
      }
    } catch (e) {
      Message.error(errorMessage(e))
    } finally {
      setActing(false)
    }
  }

  const doReject = async () => {
    if (!rejectModal.instance) return
    if (!rejectModal.reason.trim()) {
      Message.warning('请填写驳回原因')
      return
    }
    setActing(true)
    try {
      await approvals.act(rejectModal.instance.id, 'REJECT', rejectModal.reason)
      Message.success('已驳回')
      setRejectModal({ open: false, instance: null, reason: '' })
      refreshAll()
      if (detail.open && detail.instanceId === rejectModal.instance.id) {
        const data = await approvals.getDetail(rejectModal.instance.id)
        setDetail((c) => ({ ...c, data }))
      }
    } catch (e) {
      Message.error(errorMessage(e))
    } finally {
      setActing(false)
    }
  }

  const doWithdraw = async (instance: ApprovalInstance) => {
    Modal.confirm({
      title: '确认撤回该审批？',
      content: `撤回后状态将变为「已撤回」，且无法再审批。业务：${APPROVAL_BIZ_TYPE_LABELS[instance.bizType] ?? instance.bizType} #${instance.bizId}`,
      okText: '撤回',
      cancelText: '取消',
      onOk: async () => {
        try {
          await approvals.act(instance.id, 'WITHDRAW', '提交人撤回')
          Message.success('已撤回')
          refreshAll()
          if (detail.open && detail.instanceId === instance.id) {
            const data = await approvals.getDetail(instance.id)
            setDetail((c) => ({ ...c, data }))
          }
        } catch (e) {
          Message.error(errorMessage(e))
        }
      },
    })
  }

  const columns: TableColumnProps<ApprovalInstance>[] = [
    { title: 'ID', dataIndex: 'id', width: 70 },
    {
      title: '业务',
      dataIndex: 'bizType',
      width: 110,
      render: (v) => <Tag>{APPROVAL_BIZ_TYPE_LABELS[v as string] ?? v}</Tag>,
    },
    {
      title: '业务 ID',
      dataIndex: 'bizId',
      width: 90,
      render: (v) => <Typography.Text copyable>{v}</Typography.Text>,
    },
    {
      title: '标题',
      dataIndex: 'bizTitle',
      width: 280,
      render: (v) => v ? <Tooltip content={v}><Typography.Text ellipsis style={{ maxWidth: 260 }}>{v}</Typography.Text></Tooltip> : <Typography.Text type="secondary">-</Typography.Text>,
    },
    { title: '提交人', dataIndex: 'bizCreatorEmpName', width: 110, render: (v, r) => v || r.bizCreatorEmpNo || '-' },
    {
      title: '工厂 / 区域',
      width: 180,
      render: (_, r) => (
        <Typography.Text type="secondary">
          {[r.bizFactory, r.bizArea].filter(Boolean).join(' / ') || '-'}
        </Typography.Text>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (v: ApprovalInstanceStatus) => (
        <Tag color={APPROVAL_INSTANCE_STATUS_COLORS[v]}>{APPROVAL_INSTANCE_STATUS_LABELS[v]}</Tag>
      ),
    },
    { title: '步骤数', dataIndex: 'stepCount', width: 80, render: (v) => v ?? '-' },
    { title: '最近活动', dataIndex: 'lastActionAt', width: 170, render: (v) => v ? new Date(v).toLocaleString('zh-CN') : '-' },
    {
      title: '操作',
      dataIndex: 'id',
      width: 230,
      fixed: 'right',
      render: (_, r) => (
        <Space size="mini">
          <Button size="mini" type="text" onClick={() => openDetail(r.id)}>查看</Button>
          {r.status === 'PENDING' && canOperate && (
            <>
              <Button
                size="mini" type="primary" status="success"
                icon={<IconCheck />}
                onClick={() => setApproveModal({ open: true, instance: r, comment: '' })}
              >批准</Button>
              <Button
                size="mini" type="primary" status="danger"
                icon={<IconClose />}
                onClick={() => setRejectModal({ open: true, instance: r, reason: '' })}
              >驳回</Button>
            </>
          )}
          {r.status === 'PENDING' && r.bizCreatorEmpNo === operator?.empNo && (
            <Button
              size="mini" type="text" icon={<IconUndo />}
              onClick={() => doWithdraw(r)}
            >撤回</Button>
          )}
        </Space>
      ),
    },
  ]

  return (
    <div className="page-stack">
      <PageHead
        title="审批中心"
        detail="跨业务的统一审批面板：当前已接入巡检计划。每条 instance 含 1 条 SUBMIT 提交 + N 条审批/驳回/撤回步骤，可点击查看完整历史。"
      >
        <Button icon={<IconRefresh />} onClick={refreshAll}>刷新</Button>
      </PageHead>

      {!canOperate && (
        <div style={{ padding: '8px 16px', background: 'var(--color-warning-light-1)', borderRadius: 6, color: 'var(--color-warning-6)' }}>
          提示：当前账号无超管权限，仅可查看、不能审批。请联系超管在「账号管理」中调整 pagePermissions。
        </div>
      )}

      <Card className="filter-card">
        <FilterBar>
        <Input
          allowClear
          prefix={<IconSearch />}
          placeholder="搜索标题 / 提交人 / 驳回原因"
          value={filters.keyword}
          onChange={(v) => setFilters((c) => ({ ...c, keyword: v }))}
          onPressEnter={() => setQuery((c) => ({ ...c, keyword: filters.keyword, page: 1 }))}
          style={{ width: 240 }}
        />
        <Select
          value={filters.status}
          onChange={(v) => {
            setFilters((c) => ({ ...c, status: v as '' | ApprovalInstanceStatus }))
            setQuery((c) => ({ ...c, status: v as '' | ApprovalInstanceStatus, page: 1 }))
          }}
          style={{ width: 140 }}
        >
          {STATUS_FILTER_OPTIONS.map((o) => (
            <Select.Option key={o.value || 'all'} value={o.value}>{o.label}</Select.Option>
          ))}
        </Select>
        <Select
          value={filters.bizType}
          onChange={(v) => {
            setFilters((c) => ({ ...c, bizType: v }))
            setQuery((c) => ({ ...c, bizType: v, page: 1 }))
          }}
          style={{ width: 140 }}
        >
          {BIZ_TYPE_FILTER_OPTIONS.map((o) => (
            <Select.Option key={o.value || 'all'} value={o.value}>{o.label}</Select.Option>
          ))}
        </Select>
        <Button type="primary" onClick={() => setQuery({ ...filters, page: 1, pageSize: 20 })}>查询</Button>
        <span style={{ marginLeft: 8, color: 'var(--color-text-3)' }}>
          待审批总数：<Typography.Text bold style={{ color: 'var(--color-primary-6)' }}>{pendingRemote.data?.total ?? '-'}</Typography.Text>
        </span>
        </FilterBar>
      </Card>

      <StatePanel loading={remote.loading} error={remote.error} onRetry={remote.refresh}>
        <DataCard>
          <Table
            rowKey="id"
            loading={remote.loading}
            data={remote.data?.rows ?? []}
            columns={columns}
            pagination={false}
            size="small"
            scroll={{ x: 1500 }}
          />
          {remote.data && remote.data.total > 0 && (
            <div className="pagination-wrap">
              <Pagination
                current={query.page}
                pageSize={query.pageSize}
                total={remote.data.total}
                onChange={(p, ps) => setQuery((c) => ({ ...c, page: p, pageSize: ps }))}
                showTotal
              />
            </div>
          )}
        </DataCard>
      </StatePanel>

      {/* 详情 Drawer */}
      <Drawer
        width={860}
        title={detail.data ? `审批详情 #${detail.data.instance.id}` : '审批详情'}
        visible={detail.open}
        onCancel={closeDetail}
        footer={null}
        maskClosable={!acting}
      >
        {detail.loading ? (
          <Empty description="加载中..." />
        ) : detail.data ? (
          <DetailBody
            data={detail.data}
            canOperate={canOperate}
            operatorEmpNo={operator?.empNo}
            onApprove={(ins) => setApproveModal({ open: true, instance: ins, comment: '' })}
            onReject={(ins) => setRejectModal({ open: true, instance: ins, reason: '' })}
            onWithdraw={(ins) => doWithdraw(ins)}
          />
        ) : (
          <Empty description="未找到审批" />
        )}
      </Drawer>

      {/* 批准 Modal */}
      <Modal
        title="批准审批"
        visible={approveModal.open}
        onCancel={() => !acting && setApproveModal({ open: false, instance: null, comment: '' })}
        onOk={doApprove}
        okText="批准"
        cancelText="取消"
        confirmLoading={acting}
        maskClosable={false}
      >
        {approveModal.instance && (
          <>
            <Descriptions
              column={1}
              size="small"
              data={[
                { label: '业务', value: APPROVAL_BIZ_TYPE_LABELS[approveModal.instance.bizType] ?? approveModal.instance.bizType },
                { label: '业务 ID', value: String(approveModal.instance.bizId) },
                { label: '标题', value: approveModal.instance.bizTitle || '-' },
                { label: '提交人', value: `${approveModal.instance.bizCreatorEmpName || ''} ${approveModal.instance.bizCreatorEmpNo ? `(${approveModal.instance.bizCreatorEmpNo})` : ''}`.trim() || '-' },
              ]}
            />
            <Form layout="vertical" style={{ marginTop: 16 }}>
              <Form.Item label="审批意见（可选）">
                <Input.TextArea
                  rows={3}
                  value={approveModal.comment}
                  onChange={(v) => setApproveModal((c) => ({ ...c, comment: v }))}
                  placeholder="同意 / 备注..."
                  maxLength={500}
                  showWordLimit
                />
              </Form.Item>
            </Form>
          </>
        )}
      </Modal>

      {/* 驳回 Modal */}
      <Modal
        title="驳回审批"
        visible={rejectModal.open}
        onCancel={() => !acting && setRejectModal({ open: false, instance: null, reason: '' })}
        onOk={doReject}
        okText="驳回"
        cancelText="取消"
        confirmLoading={acting}
        okButtonProps={{ status: 'danger' }}
        maskClosable={false}
      >
        {rejectModal.instance && (
          <>
            <Descriptions
              column={1}
              size="small"
              data={[
                { label: '业务', value: APPROVAL_BIZ_TYPE_LABELS[rejectModal.instance.bizType] ?? rejectModal.instance.bizType },
                { label: '业务 ID', value: String(rejectModal.instance.bizId) },
                { label: '标题', value: rejectModal.instance.bizTitle || '-' },
              ]}
            />
            <Form layout="vertical" style={{ marginTop: 16 }}>
              <Form.Item label="驳回原因" required>
                <Input.TextArea
                  rows={3}
                  value={rejectModal.reason}
                  onChange={(v) => setRejectModal((c) => ({ ...c, reason: v }))}
                  placeholder="请说明驳回原因，便于提交方修改后重新提交"
                  maxLength={1000}
                  showWordLimit
                />
              </Form.Item>
            </Form>
          </>
        )}
      </Modal>
    </div>
  )
}

interface DetailBodyProps {
  data: ApprovalDetail
  canOperate: boolean
  operatorEmpNo: string | null | undefined
  onApprove: (instance: ApprovalInstance) => void
  onReject: (instance: ApprovalInstance) => void
  onWithdraw: (instance: ApprovalInstance) => void
}

function DetailBody({ data, canOperate, operatorEmpNo, onApprove, onReject, onWithdraw }: DetailBodyProps) {
  const ins = data.instance
  const isCreator = ins.bizCreatorEmpNo && operatorEmpNo && ins.bizCreatorEmpNo === operatorEmpNo
  return (
    <div className="page-stack">
      <Descriptions
        title="实例信息"
        column={2}
        size="small"
        data={[
          { label: '状态', value: <Tag color={APPROVAL_INSTANCE_STATUS_COLORS[ins.status]}>{APPROVAL_INSTANCE_STATUS_LABELS[ins.status]}</Tag> },
          { label: '业务', value: APPROVAL_BIZ_TYPE_LABELS[ins.bizType] ?? ins.bizType },
          { label: '业务 ID', value: <Typography.Text copyable>{ins.bizId}</Typography.Text> },
          { label: '标题', value: ins.bizTitle || '-' },
          { label: '提交人', value: `${ins.bizCreatorEmpName || ''} ${ins.bizCreatorEmpNo ? `(${ins.bizCreatorEmpNo})` : ''}`.trim() || '-' },
          { label: '工厂 / 区域', value: [ins.bizFactory, ins.bizArea].filter(Boolean).join(' / ') || '-' },
          { label: '最近活动', value: ins.lastActionAt ? new Date(ins.lastActionAt).toLocaleString('zh-CN') : '-' },
          { label: '创建', value: ins.createdAt ? new Date(ins.createdAt).toLocaleString('zh-CN') : '-' },
          ...(ins.approvedAt ? [{ label: '批准时间', value: new Date(ins.approvedAt).toLocaleString('zh-CN') }] : []),
          ...(ins.rejectedAt ? [{ label: '驳回时间', value: new Date(ins.rejectedAt).toLocaleString('zh-CN') }] : []),
          ...(ins.rejectReason ? [{ label: '驳回原因', value: <div style={{ color: 'rgb(var(--danger-6))' }}><StructuredValue value={ins.rejectReason} textRows={4} /></div> }] : []),
        ]}
      />

      {ins.bizSnapshot && (
        <div>
          <Typography.Title heading={6} style={{ marginTop: 8 }}>业务快照（提交时）</Typography.Title>
          <ApprovalSnapshot snapshot={ins.bizSnapshot} />
        </div>
      )}

      <div>
        <Typography.Title heading={6} style={{ marginTop: 16 }}>步骤历史</Typography.Title>
        {data.steps.length === 0 ? (
          <Empty description="暂无步骤" />
        ) : (
          <Timeline>
            {data.steps.map((s) => (
              <Timeline.Item
                key={s.id}
                label={s.createdAt ? new Date(s.createdAt).toLocaleString('zh-CN') : ''}
                dotColor={
                  s.action === 'APPROVE' ? 'green'
                  : s.action === 'REJECT' ? 'red'
                  : s.action === 'WITHDRAW' ? 'gray'
                  : s.action === 'TRANSFER' ? 'orange'
                  : 'blue'
                }
              >
                <Space>
                  <Tag color={APPROVAL_ACTION_COLORS[s.action as ApprovalAction]}>{APPROVAL_ACTION_LABELS[s.action as ApprovalAction]}</Tag>
                  <Typography.Text>
                    {s.actorEmpName || s.actorEmpNo}
                    {s.fromStatus && s.toStatus ? <Typography.Text type="secondary"> · {APPROVAL_INSTANCE_STATUS_LABELS[s.fromStatus]} → {APPROVAL_INSTANCE_STATUS_LABELS[s.toStatus]}</Typography.Text> : null}
                  </Typography.Text>
                </Space>
                {s.comment ? <div style={{ marginTop: 4 }}><StructuredValue value={s.comment} textRows={4} /></div> : null}
              </Timeline.Item>
            ))}
          </Timeline>
        )}
      </div>

      {ins.status === 'PENDING' && (
        <Space>
          {canOperate && (
            <>
              <Button type="primary" status="success" icon={<IconCheck />} onClick={() => onApprove(ins)}>批准</Button>
              <Button type="primary" status="danger" icon={<IconClose />} onClick={() => onReject(ins)}>驳回</Button>
            </>
          )}
          {isCreator && <Button icon={<IconUndo />} onClick={() => onWithdraw(ins)}>撤回</Button>}
        </Space>
      )}
    </div>
  )
}
