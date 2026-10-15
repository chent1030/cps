/**
 * E4 物品台账列表页（波次 14 收尾）。
 * - Arco Table + 分页 + 关键字/基地/库房/低库存过滤
 * - 新建/编辑 Modal，调 POST /api/cps/admin/inventory-items
 */
import { Badge, Button, Card, Form, Input, InputNumber, Message, Modal, Pagination, Select, Space, Table, Typography } from '@arco-design/web-react'
import type { TableColumnProps } from '@arco-design/web-react'
import { IconFilter, IconPlus } from '@arco-design/web-react/icon'
import { useState } from 'react'
import { saveItem, setItemEnabled } from '../../api/inventory'
import { api, getAdminOperator } from '../../api'
import { useRemote } from '../../hooks/useRemote'
import { Enabled, PageHead, StatePanel, Toggle } from '../../components/PageParts'
import { errorMessage } from '../../lib/presentation'
import type { InventoryItem } from '../../types'

interface ItemForm {
  itemCode: string
  itemName: string
  unit: string
  stockQty: number
  alertThreshold: number
  baseCode: string
  factory: string
  storageRoom: string
  roomKeeperEmpNo: string
  roomKeeperEmpName: string
  remark: string
}

const filtersInit = { keyword: '', factory: '', storageRoom: '', lowStock: '' }
const formInit: ItemForm = {
  itemCode: '', itemName: '', unit: '', stockQty: 0, alertThreshold: 0,
  baseCode: '', factory: '', storageRoom: '',
  roomKeeperEmpNo: '', roomKeeperEmpName: '', remark: '',
}

export function InventoryItemsPage() {
  const [filters, setFilters] = useState(filtersInit)
  const [query, setQuery] = useState<{ keyword: string; factory: string; storageRoom: string; lowStock: string; page: number; size: number }>({
    ...filtersInit, page: 1, size: 10,
  })
  const [modalOpen, setModalOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [editing, setEditing] = useState<InventoryItem | null>(null)
  const [form, setForm] = useState<ItemForm>(formInit)

  const remote = useRemote(() => api.inventoryItems({
    keyword: query.keyword || undefined,
    factory: query.factory || undefined,
    storageRoom: query.storageRoom || undefined,
    lowStock: query.lowStock === 'true' ? true : undefined,
    page: query.page, size: query.size,
  }), [query.keyword, query.factory, query.storageRoom, query.lowStock, query.page, query.size])

  const data = remote.data ?? []
  const total = data.length
  const lowStockCount = data.filter((it) => it.stockQty <= it.alertThreshold).length

  function openCreate() {
    setEditing(null); setForm(formInit); setModalOpen(true)
  }
  function openEdit(item: InventoryItem) {
    setEditing(item)
    setForm({
      itemCode: item.itemCode ?? '', itemName: item.itemName ?? '', unit: item.unit ?? '',
      stockQty: item.stockQty ?? 0, alertThreshold: item.alertThreshold ?? 0,
      baseCode: item.baseCode ?? '', factory: item.factory ?? '', storageRoom: item.storageRoom ?? '',
      roomKeeperEmpNo: item.roomKeeperEmpNo ?? '', roomKeeperEmpName: item.roomKeeperEmpName ?? '',
      remark: item.remark ?? '',
    })
    setModalOpen(true)
  }

  async function submit() {
    if (!form.itemCode.trim() || !form.itemName.trim()) {
      return Message.warning('物品编码和物品名称为必填项')
    }
    setSaving(true)
    try {
      await saveItem({ ...form, id: editing?.id, enabled: editing?.enabled ?? true })
      Message.success(editing ? '物品已更新' : '物品已创建')
      setModalOpen(false)
      await remote.refresh()
    } catch (error) {
      Message.error(errorMessage(error))
    } finally { setSaving(false) }
  }

  async function toggle(item: InventoryItem) {
    try {
      await setItemEnabled(item.id, !item.enabled)
      await remote.refresh()
    } catch (error) {
      Message.error(errorMessage(error))
    }
  }

  const columns: TableColumnProps<InventoryItem>[] = [
    { title: '物品', width: 220, render: (_, item) => <div><Typography.Text bold>{item.itemName}</Typography.Text><Typography.Text type="secondary" style={{ display: 'block', fontSize: 12 }}>{item.itemCode}{item.unit ? `（${item.unit}）` : ''}</Typography.Text></div> },
    { title: '基地/库房', width: 180, render: (_, item) => <>{item.factory || '-'} / {item.storageRoom || '-'}</> },
    { title: '库存/阈值', width: 150, render: (_, item) => item.stockQty <= item.alertThreshold ? <Badge color="orangered" text={`${item.stockQty} / ${item.alertThreshold}`} /> : <span>{item.stockQty} / {item.alertThreshold}</span> },
    { title: '库管员', width: 160, render: (_, item) => item.roomKeeperEmpNo ? `${item.roomKeeperEmpName || '-'}（${item.roomKeeperEmpNo}）` : '-' },
    { title: '状态', width: 90, render: (_, item) => <Enabled value={item.enabled} /> },
    { title: '操作', width: 160, align: 'right', render: (_, item) => <Space size={4}><Button type="text" size="small" onClick={() => openEdit(item)}>编辑</Button><Toggle value={item.enabled} onClick={() => void toggle(item)} /></Space> },
  ]

  const operator = getAdminOperator()
  return <>
    <PageHead title="物品台账" detail={<>物品编码、名称、阈值、库管员等基础台账。{lowStockCount > 0 && <Badge color="orangered" text={`当前 ${lowStockCount} 项低库存`} />}</>}>
      <Button type="primary" icon={<IconPlus />} onClick={openCreate}>新增物品</Button>
    </PageHead>
    <Card className="filter-card" style={{ marginTop: 12 }}>
      <Form layout="inline" onSubmit={() => setQuery({ ...filters, page: 1, size: query.size })} style={{ display: 'grid', gridTemplateColumns: 'repeat(4, minmax(140px, 1fr)) auto', gap: 12 }}>
        <Input.Search allowClear value={filters.keyword} onChange={(keyword) => setFilters({ ...filters, keyword })} placeholder="编码或名称" />
        <Input allowClear value={filters.factory} onChange={(factory) => setFilters({ ...filters, factory })} placeholder="基地" />
        <Input allowClear value={filters.storageRoom} onChange={(storageRoom) => setFilters({ ...filters, storageRoom })} placeholder="库房" />
        <Select allowClear value={filters.lowStock || undefined} placeholder="全部库存" onChange={(value) => setFilters({ ...filters, lowStock: value ?? '' })} options={[{ value: 'true', label: '仅看低库存' }]} />
        <Button type="primary" htmlType="submit" icon={<IconFilter />}>筛选</Button>
      </Form>
    </Card>
    <div style={{ marginTop: 20 }}>
      <StatePanel {...remote}>{remote.data && <Card className="table-card">
        <Table rowKey="id" columns={columns} data={data} pagination={false} scroll={{ x: 900 }} />
        <div className="pagination-wrap"><Pagination current={query.page} pageSize={query.size} total={total} showTotal onChange={(page, size) => setQuery({ ...query, page, size })} /></div>
      </Card>}</StatePanel>
    </div>

    <Modal title={editing ? '编辑物品' : '新增物品'} visible={modalOpen} onCancel={() => setModalOpen(false)} onOk={() => void submit()} confirmLoading={saving} okText="保存" cancelText="取消">
      <Form layout="vertical">
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
          <Form.Item label="物品编码" required><Input value={form.itemCode} onChange={(itemCode) => setForm({ ...form, itemCode })} /></Form.Item>
          <Form.Item label="物品名称" required><Input value={form.itemName} onChange={(itemName) => setForm({ ...form, itemName })} /></Form.Item>
          <Form.Item label="单位"><Input value={form.unit} onChange={(unit) => setForm({ ...form, unit })} /></Form.Item>
          <Form.Item label="预警阈值（≤ 触发）" required><InputNumber min={0} value={form.alertThreshold} onChange={(v) => setForm({ ...form, alertThreshold: v ?? 0 })} /></Form.Item>
          <Form.Item label="基地"><Input value={form.factory} onChange={(factory) => setForm({ ...form, factory })} /></Form.Item>
          <Form.Item label="库房"><Input value={form.storageRoom} onChange={(storageRoom) => setForm({ ...form, storageRoom })} /></Form.Item>
          <Form.Item label="库管员工号"><Input value={form.roomKeeperEmpNo} onChange={(roomKeeperEmpNo) => setForm({ ...form, roomKeeperEmpNo })} /></Form.Item>
          <Form.Item label="库管员姓名"><Input value={form.roomKeeperEmpName} onChange={(roomKeeperEmpName) => setForm({ ...form, roomKeeperEmpName })} /></Form.Item>
          <Form.Item label="所属基地编码"><Input value={form.baseCode} onChange={(baseCode) => setForm({ ...form, baseCode })} /></Form.Item>
          <Form.Item label="初始库存"><InputNumber min={0} value={form.stockQty} onChange={(v) => setForm({ ...form, stockQty: v ?? 0 })} disabled={!!editing} /></Form.Item>
        </div>
        <Form.Item label="备注"><Input.TextArea value={form.remark} onChange={(remark) => setForm({ ...form, remark })} /></Form.Item>
        {editing && <Typography.Text type="secondary">库存数量请通过「出入库流水 → 登记出入库」变更，保证流水留痕。当前操作人：{operator?.empName || '未登录'}</Typography.Text>}
      </Form>
    </Modal>
  </>
}