/**
 * E4 出入库流水页：只读流水 + 登记出入库入口（从旧整合页移植，api.createInventoryTxn）。
 * - Arco Table + 分页 + 类型/物品过滤
 * - 登记出入库 Modal：物品下拉（现存库存）、类型（入库/出库/盘点调整）、数量（按类型换算符号）
 */
import { Button, Card, Form, Input, InputNumber, Message, Modal, Pagination, Radio, Select, Table, Typography } from '@arco-design/web-react'
import type { TableColumnProps } from '@arco-design/web-react'
import { IconFilter, IconPlus } from '@arco-design/web-react/icon'
import { useState } from 'react'
import { api } from '../../api'
import { listTxns } from '../../api/inventory'
import { useRemote } from '../../hooks/useRemote'
import { PageHead, StatePanel } from '../../components/PageParts'
import { StructuredValue } from '../../components/StructuredValue'
import { errorMessage } from '../../lib/presentation'
import type { InventoryTxn } from '../../types'

const txnTypeLabel: Record<string, string> = { IN: '入库', OUT: '出库', ADJUST: '盘点调整' }

interface TxnForm {
  itemId?: number
  txnType: 'IN' | 'OUT' | 'ADJUST'
  qty: number
  remark: string
}

const emptyTxnForm: TxnForm = { txnType: 'IN', qty: 1, remark: '' }

export function InventoryTransactionsPage() {
  const [filters, setFilters] = useState({ itemId: '', txnType: '' })
  const [query, setQuery] = useState({ ...filters, page: 1, size: 20 })
  const [modalVisible, setModalVisible] = useState(false)
  const [saving, setSaving] = useState(false)
  const [txnForm, setTxnForm] = useState<TxnForm>(emptyTxnForm)

  const remote = useRemote(() => listTxns({
    itemId: query.itemId ? Number(query.itemId) : undefined,
    type: (query.txnType as 'IN' | 'OUT' | 'ADJUST' | '') || undefined,
    page: query.page, size: query.size,
  }), [query.itemId, query.txnType, query.page, query.size])

  const itemsRemote = useRemote(() => api.inventoryItems({ enabled: true }), [])

  const rows = remote.data?.rows ?? []
  const total = remote.data?.total ?? 0

  const columns: TableColumnProps<InventoryTxn>[] = [
    { title: '时间', width: 170, dataIndex: 'createdAt' },
    { title: '物品', width: 200, render: (_, row) => <>{row.itemName || '-'} <Typography.Text type="secondary">({row.itemCode || row.itemId})</Typography.Text></> },
    { title: '类型', width: 110, render: (_, row) => txnTypeLabel[row.txnType] ?? row.txnType },
    { title: '数量', width: 120, render: (_, row) => <Typography.Text bold style={{ color: row.qty > 0 ? '#00b42a' : '#f53f3f' }}>{row.qty > 0 ? `+${row.qty}` : row.qty}</Typography.Text> },
    { title: '变更前 → 后', width: 160, render: (_, row) => `${row.beforeQty} → ${row.afterQty}` },
    { title: '操作人', width: 160, render: (_, row) => row.operatorEmpNo ? `${row.operatorName || '-'}（${row.operatorEmpNo}）` : '-' },
    { title: '备注', render: (_, row) => <StructuredValue value={row.remark} textRows={2} /> },
  ]

  async function submitTxn() {
    if (!txnForm.itemId) return Message.warning('请选择物品')
    if (!txnForm.qty) return Message.warning('请输入数量')
    setSaving(true)
    try {
      const result = await api.createInventoryTxn({
        itemId: txnForm.itemId,
        txnType: txnForm.txnType,
        qty: txnForm.qty,
        remark: txnForm.remark || undefined,
      })
      Message.success(`已登记${txnTypeLabel[result.txn.txnType]} ${Math.abs(result.txn.qty)}（现存 ${result.txn.afterQty}）${result.alertAction && result.alertAction !== 'NONE' ? `；低库存预警${result.alertAction === 'CREATED' ? '已触发' : '已更新'}` : ''}`)
      setModalVisible(false)
      setTxnForm(emptyTxnForm)
      await Promise.all([remote.refresh(), itemsRemote.refresh()])
    } catch (error) {
      Message.error(errorMessage(error))
    } finally {
      setSaving(false)
    }
  }

  return <>
    <PageHead title="出入库流水" detail="入库、出库与盘点调整流水">
      <Button type="primary" icon={<IconPlus />} onClick={() => { void itemsRemote.refresh(); setModalVisible(true) }}>登记出入库</Button>
    </PageHead>
    <Card className="filter-card">
      <Form layout="inline" className="filter-bar" onSubmit={() => setQuery({ ...filters, page: 1, size: query.size })}>
        <Input allowClear value={filters.itemId} onChange={(itemId) => setFilters({ ...filters, itemId })} placeholder="物品 ID" style={{ width: 140 }} />
        <Select allowClear value={filters.txnType || undefined} placeholder="全部类型" style={{ width: 160 }} onChange={(value) => setFilters({ ...filters, txnType: value ?? '' })} options={Object.entries(txnTypeLabel).map(([value, label]) => ({ value, label }))} />
        <Button type="primary" htmlType="submit" icon={<IconFilter />}>筛选</Button>
      </Form>
    </Card>
    <div style={{ marginTop: 16 }}>
      <StatePanel {...remote}>{remote.data && <Card className="table-card">
        <Table rowKey="id" columns={columns} data={rows} pagination={false} scroll={{ x: 1000 }} />
        <div className="pagination-wrap"><Pagination current={query.page} pageSize={query.size} total={total} showTotal onChange={(page, size) => setQuery({ ...query, page, size })} /></div>
      </Card>}</StatePanel>
    </div>

    <Modal title="登记出入库" visible={modalVisible} onCancel={() => setModalVisible(false)} onOk={() => void submitTxn()} confirmLoading={saving} okText="登记" cancelText="取消" style={{ width: 640 }}>
      <Form layout="vertical">
        <Form.Item label="物品" required>
          <Select
            showSearch
            value={txnForm.itemId}
            onChange={(itemId) => setTxnForm({ ...txnForm, itemId })}
            options={(itemsRemote.data ?? []).map((item) => ({ value: item.id, label: `${item.itemName}（${item.itemCode}，现存 ${item.stockQty}）` }))}
            placeholder="选择物品"
            notFoundContent="请先在「物品台账」中创建物品"
          />
        </Form.Item>
        <Form.Item label="类型" required>
          <Radio.Group
            type="button"
            value={txnForm.txnType}
            onChange={(value) => setTxnForm({ ...txnForm, txnType: value, qty: (value === 'OUT' ? -1 : 1) * Math.abs(txnForm.qty) })}
            options={Object.entries(txnTypeLabel).map(([value, label]) => ({ value, label }))}
          />
        </Form.Item>
        <Form.Item label="数量" required extra="输入正数，按类型自动换算符号">
          <InputNumber min={1} precision={0} value={Math.abs(txnForm.qty)} onChange={(value) => setTxnForm({ ...txnForm, qty: (txnForm.txnType === 'OUT' ? -1 : 1) * (value ?? 0) })} style={{ width: 200 }} />
        </Form.Item>
        <Form.Item label="备注"><Input.TextArea value={txnForm.remark} onChange={(remark) => setTxnForm({ ...txnForm, remark })} placeholder="可选" /></Form.Item>
      </Form>
    </Modal>
  </>
}
