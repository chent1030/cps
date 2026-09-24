/**
 * E4 出入库流水只读页（波次 14 收尾）。
 * - Arco Table + 分页 + 类型/物品 ID 过滤
 * - 写入入口保留在 Items 页（登记出入库 Modal 之前由波次 8 整合），本页只读。
 */
import { Button, Card, Form, Input, Pagination, Select, Table, Typography } from '@arco-design/web-react'
import type { TableColumnProps } from '@arco-design/web-react'
import { IconFilter } from '@arco-design/web-react/icon'
import { useState } from 'react'
import { listTxns } from '../../api/inventory'
import { useRemote } from '../../hooks/useRemote'
import { PageHead, StatePanel } from '../../components/PageParts'
import type { InventoryTxn } from '../../types'

const txnTypeLabel: Record<string, string> = { IN: '入库', OUT: '出库', ADJUST: '盘点调整' }

export function InventoryTransactionsPage() {
  const [filters, setFilters] = useState({ itemId: '', txnType: '' })
  const [query, setQuery] = useState({ ...filters, page: 1, size: 20 })

  const remote = useRemote(() => listTxns({
    itemId: query.itemId ? Number(query.itemId) : undefined,
    type: (query.txnType as 'IN' | 'OUT' | 'ADJUST' | '') || undefined,
    page: query.page, size: query.size,
  }), [query.itemId, query.txnType, query.page, query.size])

  const rows = remote.data?.rows ?? []
  const total = remote.data?.total ?? 0

  const columns: TableColumnProps<InventoryTxn>[] = [
    { title: '时间', width: 170, dataIndex: 'createdAt' },
    { title: '物品', width: 200, render: (_, row) => <>{row.itemName || '-'} <Typography.Text type="secondary">({row.itemCode || row.itemId})</Typography.Text></> },
    { title: '类型', width: 110, render: (_, row) => txnTypeLabel[row.txnType] ?? row.txnType },
    { title: '数量', width: 120, render: (_, row) => <Typography.Text bold style={{ color: row.qty > 0 ? '#00b42a' : '#f53f3f' }}>{row.qty > 0 ? `+${row.qty}` : row.qty}</Typography.Text> },
    { title: '变更前 → 后', width: 160, render: (_, row) => `${row.beforeQty} → ${row.afterQty}` },
    { title: '操作人', width: 160, render: (_, row) => row.operatorEmpNo ? `${row.operatorName || '-'}（${row.operatorEmpNo}）` : '-' },
    { title: '备注', render: (_, row) => row.remark || '-' },
  ]

  return <>
    <PageHead title="出入库流水" detail={<>物品的入库、出库、盘点调整流水。所有库存变更需通过此流水留痕，禁止直接修改台账库存字段。</>} />
    <Card className="filter-card" style={{ marginTop: 12 }}>
      <Form layout="inline" onSubmit={() => setQuery({ ...filters, page: 1, size: query.size })} style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
        <Input allowClear value={filters.itemId} onChange={(itemId) => setFilters({ ...filters, itemId })} placeholder="物品 ID" style={{ width: 140 }} />
        <Select allowClear value={filters.txnType || undefined} placeholder="全部类型" style={{ width: 160 }} onChange={(value) => setFilters({ ...filters, txnType: value ?? '' })} options={Object.entries(txnTypeLabel).map(([value, label]) => ({ value, label }))} />
        <Button type="primary" htmlType="submit" icon={<IconFilter />}>筛选</Button>
      </Form>
    </Card>
    <div style={{ marginTop: 20 }}>
      <StatePanel {...remote}>{remote.data && <Card className="table-card">
        <Table rowKey="id" columns={columns} data={rows} pagination={false} scroll={{ x: 1000 }} />
        <div className="pagination-wrap"><Pagination current={query.page} pageSize={query.size} total={total} showTotal onChange={(page, size) => setQuery({ ...query, page, size })} /></div>
      </Card>}</StatePanel>
    </div>
  </>
}