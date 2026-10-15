/**
 * E4 库存预警处理页（波次 14 收尾）。
 * - Arco Table + 分页 + 状态过滤
 * - 「处理」按钮 → 弹窗 Modal 调 POST /api/cps/admin/inventory-alerts/{id}/handle
 */
import { Badge, Button, Card, Form, Input, Message, Modal, Pagination, Select, Table, Typography } from '@arco-design/web-react'
import type { TableColumnProps } from '@arco-design/web-react'
import { IconFilter } from '@arco-design/web-react/icon'
import { useState } from 'react'
import { handleAlert, listAlerts } from '../../api/inventory'
import { useRemote } from '../../hooks/useRemote'
import { PageHead, StatePanel } from '../../components/PageParts'
import { errorMessage } from '../../lib/presentation'
import type { InventoryAlertEvent, InventoryAlertStatus } from '../../types'

const statusLabel: Record<InventoryAlertStatus, string> = {
  OPEN: '预警中', RESOLVED_AUTO: '已自动解除', RESOLVED_MANUAL: '已人工关闭', IGNORED: '已忽略',
}
const statusColor: Record<InventoryAlertStatus, string> = {
  OPEN: 'orangered', RESOLVED_AUTO: 'green', RESOLVED_MANUAL: 'blue', IGNORED: 'gray',
}

export function InventoryAlertsPage() {
  const [filters, setFilters] = useState<{ status: string }>({ status: 'OPEN' })
  const [query, setQuery] = useState({ ...filters, page: 1, size: 20 })
  const [handleTarget, setHandleTarget] = useState<{ event: InventoryAlertEvent; action: 'IGNORE' | 'CLOSE' } | null>(null)
  const [handleReason, setHandleReason] = useState('')
  const [handling, setHandling] = useState(false)

  const remote = useRemote(() => listAlerts({
    status: (query.status as InventoryAlertStatus) || undefined,
    page: query.page, size: query.size,
  }), [query.status, query.page, query.size])

  const rows = remote.data?.rows ?? []
  const total = remote.data?.total ?? 0

  async function submit() {
    if (!handleTarget) return
    if (handleTarget.action === 'CLOSE' && !handleReason.trim()) {
      return Message.warning('关闭预警必须填写原因')
    }
    setHandling(true)
    try {
      await handleAlert(handleTarget.event.id, handleTarget.action, handleReason.trim() || undefined)
      Message.success(handleTarget.action === 'CLOSE' ? '预警已关闭' : '预警已忽略')
      setHandleTarget(null); setHandleReason('')
      await remote.refresh()
    } catch (error) {
      Message.error(errorMessage(error))
    } finally { setHandling(false) }
  }

  const columns: TableColumnProps<InventoryAlertEvent>[] = [
    { title: '物品', width: 200, render: (_, row) => <>{row.itemName || '-'} <Typography.Text type="secondary">({row.itemCode || row.itemId})</Typography.Text></> },
    { title: '状态', width: 130, render: (_, row) => <Badge color={statusColor[row.status] ?? 'gray'} text={statusLabel[row.status] ?? row.status} /> },
    { title: '首触时间', width: 170, dataIndex: 'firstTriggeredAt' },
    { title: '最近评估', width: 170, dataIndex: 'lastEvalAt' },
    { title: '评估库存/阈值', width: 160, render: (_, row) => `${row.lastEvalQty} / ${row.thresholdSnapshot}` },
    { title: '关闭信息', render: (_, row) => row.closedAt ? `${row.closedBy || '-'}：${row.closeReason || '-'}` : '-' },
    {
      title: '操作', width: 180, align: 'right',
      render: (_, row) => row.status === 'OPEN' ? <div style={{ display: 'inline-flex', gap: 4 }}>
        <Button type="text" size="small" onClick={() => { setHandleTarget({ event: row, action: 'CLOSE' }); setHandleReason('') }}>关闭</Button>
        <Button type="text" size="small" status="warning" onClick={() => { setHandleTarget({ event: row, action: 'IGNORE' }); setHandleReason('') }}>忽略</Button>
      </div> : '-',
    },
  ]

  return <>
    <PageHead title="库存预警处理" detail={<>当库存 ≤ 阈值时触发预警事件；持续不足合并为同一事件。OPEN 状态可处理：CLOSE 需写原因，IGNORE 可写说明。</>} />
    <Card className="filter-card" style={{ marginTop: 12 }}>
      <Form layout="inline" onSubmit={() => setQuery({ ...filters, page: 1, size: query.size })} style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
        <Select allowClear value={filters.status || undefined} placeholder="全部状态" style={{ width: 180 }} onChange={(value) => setFilters({ ...filters, status: value ?? '' })} options={Object.entries(statusLabel).map(([value, label]) => ({ value, label }))} />
        <Button type="primary" htmlType="submit" icon={<IconFilter />}>筛选</Button>
      </Form>
    </Card>
    <div style={{ marginTop: 20 }}>
      <StatePanel {...remote}>{remote.data && <Card className="table-card">
        <Table rowKey="id" columns={columns} data={rows} pagination={false} scroll={{ x: 1100 }} />
        <div className="pagination-wrap"><Pagination current={query.page} pageSize={query.size} total={total} showTotal onChange={(page, size) => setQuery({ ...query, page, size })} /></div>
      </Card>}</StatePanel>
    </div>

    <Modal title={handleTarget?.action === 'CLOSE' ? '关闭预警' : '忽略预警'} visible={!!handleTarget} onCancel={() => setHandleTarget(null)} onOk={() => void submit()} confirmLoading={handling} okText="确认" cancelText="取消">
      <Form layout="vertical">
        <Typography.Text>物品：{handleTarget?.event.itemName || '-'}（评估库存 {handleTarget?.event.lastEvalQty} / 阈值 {handleTarget?.event.thresholdSnapshot}）</Typography.Text>
        <Form.Item label={handleTarget?.action === 'CLOSE' ? '关闭原因（必填）' : '忽略说明（可选）'} required={handleTarget?.action === 'CLOSE'} style={{ marginTop: 12 }}>
          <Input.TextArea value={handleReason} onChange={setHandleReason} />
        </Form.Item>
      </Form>
    </Modal>
  </>
}