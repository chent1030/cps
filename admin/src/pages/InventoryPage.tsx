import { Badge, Button, Card, Form, Input, InputNumber, Message, Modal, Pagination, Radio, Select, Space, Table, Tabs, Typography } from '@arco-design/web-react'
import type { TableColumnProps } from '@arco-design/web-react'
import { IconFilter, IconPlus } from '@arco-design/web-react/icon'
import { useState } from 'react'
import { api, getAdminOperator } from '../api'
import { Enabled, PageHead, StatePanel, Toggle } from '../components/PageParts'
import { useRemote } from '../hooks/useRemote'
import { errorMessage } from '../lib/presentation'
import type { InventoryAlertEvent, InventoryAlertStatus, InventoryItem, InventoryTxn } from '../types'

const itemFiltersInit = { keyword: '', factory: '', storageRoom: '', lowStock: '' }
const itemFormInit = { itemCode: '', itemName: '', unit: '', stockQty: 0, alertThreshold: 0, baseCode: '', factory: '', storageRoom: '', roomKeeperEmpNo: '', roomKeeperEmpName: '', remark: '' }
const txnFormInit = { itemId: undefined as number | undefined, txnType: 'IN', qty: 1, remark: '' }
const txnFiltersInit = { itemId: '', txnType: '' }
const alertFiltersInit = { status: 'OPEN' as string }

const txnTypeLabel: Record<string, string> = { IN: '入库', OUT: '出库', ADJUST: '盘点调整' }
const alertStatusLabel: Record<InventoryAlertStatus, string> = { OPEN: '预警中', RESOLVED_AUTO: '已自动解除', RESOLVED_MANUAL: '已人工关闭', IGNORED: '已忽略' }
const alertStatusColor: Record<InventoryAlertStatus, string> = { OPEN: 'orangered', RESOLVED_AUTO: 'green', RESOLVED_MANUAL: 'blue', IGNORED: 'gray' }

export function InventoryPage() {
  // ---- 台账列表 ----
  const [itemFilters, setItemFilters] = useState(itemFiltersInit)
  const [itemQuery, setItemQuery] = useState(itemFiltersInit)
  const [itemModalVisible, setItemModalVisible] = useState(false)
  const [itemSaving, setItemSaving] = useState(false)
  const [editing, setEditing] = useState<InventoryItem | null>(null)
  const [itemForm, setItemForm] = useState(itemFormInit)
  const items = useRemote(() => api.inventoryItems({
    ...itemQuery,
    lowStock: itemQuery.lowStock === 'true' ? true : undefined,
    factory: itemQuery.factory || undefined,
    storageRoom: itemQuery.storageRoom || undefined,
    keyword: itemQuery.keyword || undefined,
  }), [itemQuery])

  // ---- 出入库 ----
  const [txnModalVisible, setTxnModalVisible] = useState(false)
  const [txnSaving, setTxnSaving] = useState(false)
  const [txnForm, setTxnForm] = useState(txnFormInit)
  const [txnFilters, setTxnFilters] = useState(txnFiltersInit)
  const [txnQuery, setTxnQuery] = useState({ ...txnFiltersInit, page: 1, size: 20 })
  const txns = useRemote(() => api.inventoryTxns({
    itemId: txnQuery.itemId ? Number(txnQuery.itemId) : undefined,
    txnType: txnQuery.txnType || undefined,
    page: txnQuery.page, size: txnQuery.size,
  }), [txnQuery])

  // ---- 预警处理 ----
  const [alertFilters, setAlertFilters] = useState(alertFiltersInit)
  const [alertQuery, setAlertQuery] = useState({ ...alertFiltersInit, page: 1, size: 20 })
  const [handleTarget, setHandleTarget] = useState<{ event: InventoryAlertEvent; action: 'IGNORE' | 'CLOSE' } | null>(null)
  const [handleReason, setHandleReason] = useState('')
  const [handling, setHandling] = useState(false)
  const alerts = useRemote(() => api.inventoryAlerts({
    status: alertQuery.status || undefined,
    page: alertQuery.page, size: alertQuery.size,
  }), [alertQuery])

  const lowStockCount = (items.data ?? []).filter((item) => item.stockQty <= item.alertThreshold).length

  function openCreateItem() {
    setEditing(null)
    setItemForm(itemFormInit)
    setItemModalVisible(true)
  }

  function openEditItem(item: InventoryItem) {
    setEditing(item)
    setItemForm({
      itemCode: item.itemCode ?? '', itemName: item.itemName ?? '', unit: item.unit ?? '',
      stockQty: item.stockQty ?? 0, alertThreshold: item.alertThreshold ?? 0, baseCode: item.baseCode ?? '',
      factory: item.factory ?? '', storageRoom: item.storageRoom ?? '',
      roomKeeperEmpNo: item.roomKeeperEmpNo ?? '', roomKeeperEmpName: item.roomKeeperEmpName ?? '',
      remark: item.remark ?? '',
    })
    setItemModalVisible(true)
  }

  async function saveItem() {
    if (!itemForm.itemCode.trim() || !itemForm.itemName.trim()) {
      return Message.warning('物品编码和物品名称为必填项')
    }
    setItemSaving(true)
    try {
      await api.saveInventoryItem({ ...itemForm, id: editing?.id, enabled: editing?.enabled ?? true })
      Message.success(editing ? '物品已更新' : '物品已创建')
      setItemModalVisible(false)
      await items.refresh()
    } catch (error) {
      Message.error(errorMessage(error))
    } finally {
      setItemSaving(false)
    }
  }

  async function toggleItem(item: InventoryItem) {
    try {
      await api.setInventoryItemEnabled(item.id, !item.enabled)
      await items.refresh()
    } catch (error) {
      Message.error(errorMessage(error))
    }
  }

  async function submitTxn() {
    if (!txnForm.itemId) return Message.warning('请选择物品')
    if (!txnForm.qty) return Message.warning('数量必须为非零整数')
    setTxnSaving(true)
    try {
      const result = await api.createInventoryTxn({
        itemId: txnForm.itemId,
        txnType: txnForm.txnType,
        qty: txnForm.qty,
        remark: txnForm.remark || undefined,
      })
      const alertTip = result.alertAction ? `；预警动作：${result.alertAction}` : ''
      Message.success(`已登记，库存 ${result.item.stockQty}${alertTip}`)
      setTxnModalVisible(false)
      setTxnForm(txnFormInit)
      await Promise.all([txns.refresh(), items.refresh(), alerts.refresh()])
    } catch (error) {
      Message.error(errorMessage(error))
    } finally {
      setTxnSaving(false)
    }
  }

  async function submitHandle() {
    if (!handleTarget) return
    if (handleTarget.action === 'CLOSE' && !handleReason.trim()) {
      return Message.warning('关闭预警必须填写原因')
    }
    setHandling(true)
    try {
      await api.handleInventoryAlert(handleTarget.event.id, { action: handleTarget.action, reason: handleReason || undefined })
      Message.success(handleTarget.action === 'CLOSE' ? '预警已关闭' : '预警已忽略')
      setHandleTarget(null)
      setHandleReason('')
      await alerts.refresh()
    } catch (error) {
      Message.error(errorMessage(error))
    } finally {
      setHandling(false)
    }
  }

  const itemColumns: TableColumnProps<InventoryItem>[] = [
    { title: '物品', width: 200, render: (_, item) => <div><Typography.Text bold>{item.itemName}</Typography.Text><Typography.Text type="secondary" style={{ display: 'block', fontSize: 12 }}>{item.itemCode}{item.unit ? `（${item.unit}）` : ''}</Typography.Text></div> },
    { title: '基地/库房', width: 180, render: (_, item) => <>{item.factory || '-'} / {item.storageRoom || '-'}</> },
    {
      title: '库存 / 阈值', width: 140,
      render: (_, item) => item.stockQty <= item.alertThreshold
        ? <Badge color="orangered" text={`${item.stockQty} / ${item.alertThreshold}`} />
        : <span>{item.stockQty} / {item.alertThreshold}</span>,
    },
    { title: '库管员', width: 160, render: (_, item) => item.roomKeeperEmpNo ? `${item.roomKeeperEmpName || '-'}（${item.roomKeeperEmpNo}）` : '-' },
    { title: '状态', width: 90, render: (_, item) => <Enabled value={item.enabled} /> },
    {
      title: '操作', width: 150, align: 'right',
      render: (_, item) => <Space size={4}><Button type="text" size="small" onClick={() => openEditItem(item)}>编辑</Button><Toggle value={item.enabled} onClick={() => void toggleItem(item)} /></Space>,
    },
  ]

  const txnColumns: TableColumnProps<InventoryTxn>[] = [
    { title: '时间', width: 170, dataIndex: 'createdAt' },
    { title: '物品', width: 180, render: (_, row) => <>{row.itemName || '-'} <Typography.Text type="secondary">({row.itemCode || row.itemId})</Typography.Text></> },
    { title: '类型', width: 100, render: (_, row) => txnTypeLabel[row.txnType] ?? row.txnType },
    {
      title: '数量', width: 110,
      render: (_, row) => <Typography.Text style={{ color: row.qty > 0 ? '#00b42a' : '#f53f3f' }} bold>{row.qty > 0 ? `+${row.qty}` : row.qty}</Typography.Text>,
    },
    { title: '变更前 → 后', width: 130, render: (_, row) => `${row.beforeQty} → ${row.afterQty}` },
    { title: '操作人', width: 150, render: (_, row) => row.operatorEmpNo ? `${row.operatorName || '-'}（${row.operatorEmpNo}）` : '-' },
    { title: '备注', render: (_, row) => row.remark || '-' },
  ]

  const alertColumns: TableColumnProps<InventoryAlertEvent>[] = [
    { title: '物品', width: 180, render: (_, row) => <>{row.itemName || '-'} <Typography.Text type="secondary">({row.itemCode || row.itemId})</Typography.Text></> },
    { title: '状态', width: 120, render: (_, row) => <Badge color={alertStatusColor[row.status] ?? 'gray'} text={alertStatusLabel[row.status] ?? row.status} /> },
    { title: '首触时间', width: 170, dataIndex: 'firstTriggeredAt' },
    { title: '最近评估', width: 170, dataIndex: 'lastEvalAt' },
    { title: '评估库存 / 阈值', width: 140, render: (_, row) => `${row.lastEvalQty} / ${row.thresholdSnapshot}` },
    { title: '关闭信息', render: (_, row) => row.closedAt ? `${row.closedBy || '-'}：${row.closeReason || '-'}` : '-' },
    {
      title: '操作', width: 160, align: 'right',
      render: (_, row) => row.status === 'OPEN' ? (
        <Space size={4}>
          <Button type="text" size="small" onClick={() => { setHandleTarget({ event: row, action: 'CLOSE' }); setHandleReason('') }}>关闭</Button>
          <Button type="text" size="small" status="warning" onClick={() => { setHandleTarget({ event: row, action: 'IGNORE' }); setHandleReason('') }}>忽略</Button>
        </Space>
      ) : '-',
    },
  ]

  const operator = getAdminOperator()

  return <>
    <PageHead title="物品台账" detail={<>台账、出入库流水与低库存预警（库存 ≤ 阈值即触发，含等于；持续不足合并为同一预警事件）。{lowStockCount > 0 && <Badge color="orangered" text={`当前 ${lowStockCount} 项低库存`} />}</>}>
      <Button type="primary" icon={<IconPlus />} onClick={openCreateItem}>新增物品</Button>
    </PageHead>
    <Tabs defaultActiveTab="items" style={{ marginTop: 12 }}>
      <Tabs.TabPane key="items" title="台账列表">
        <Card className="filter-card">
          <Form layout="inline" onSubmit={() => setItemQuery({ ...itemFilters })} style={{ display: 'grid', gridTemplateColumns: 'repeat(4, minmax(140px, 1fr)) auto', gap: 12 }}>
            <Input.Search allowClear value={itemFilters.keyword} onChange={(keyword) => setItemFilters({ ...itemFilters, keyword })} placeholder="编码或名称" />
            <Input allowClear value={itemFilters.factory} onChange={(factory) => setItemFilters({ ...itemFilters, factory })} placeholder="基地" />
            <Input allowClear value={itemFilters.storageRoom} onChange={(storageRoom) => setItemFilters({ ...itemFilters, storageRoom })} placeholder="库房" />
            <Select allowClear value={itemFilters.lowStock || undefined} placeholder="全部库存" onChange={(value) => setItemFilters({ ...itemFilters, lowStock: value ?? '' })} options={[{ value: 'true', label: '仅看低库存' }]} />
            <Button type="primary" htmlType="submit" icon={<IconFilter />}>筛选</Button>
          </Form>
        </Card>
        <div style={{ marginTop: 20 }}>
          <StatePanel {...items}>{items.data && <Card className="table-card">
            <Table rowKey="id" columns={itemColumns} data={items.data} pagination={false} scroll={{ x: 900 }} />
          </Card>}</StatePanel>
        </div>
      </Tabs.TabPane>
      <Tabs.TabPane key="txns" title="出入库流水">
        <Card className="filter-card">
          <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, flexWrap: 'wrap' }}>
            <Form layout="inline" onSubmit={() => setTxnQuery({ ...txnFilters, page: 1, size: txnQuery.size })} style={{ display: 'flex', gap: 12 }}>
              <Input allowClear value={txnFilters.itemId} onChange={(itemId) => setTxnFilters({ ...txnFilters, itemId })} placeholder="物品 ID" style={{ width: 120 }} />
              <Select allowClear value={txnFilters.txnType || undefined} placeholder="全部类型" style={{ width: 140 }} onChange={(value) => setTxnFilters({ ...txnFilters, txnType: value ?? '' })} options={Object.entries(txnTypeLabel).map(([value, label]) => ({ value, label }))} />
              <Button type="primary" htmlType="submit" icon={<IconFilter />}>筛选</Button>
            </Form>
            <Button type="primary" icon={<IconPlus />} onClick={() => setTxnModalVisible(true)}>登记出入库</Button>
          </div>
        </Card>
        <div style={{ marginTop: 20 }}>
          <StatePanel {...txns}>{txns.data && <Card className="table-card">
            <Table rowKey="id" columns={txnColumns} data={txns.data.rows} pagination={false} scroll={{ x: 1000 }} />
            <div className="pagination-wrap"><Pagination current={txnQuery.page} pageSize={txnQuery.size} total={txns.data.total} showTotal onChange={(page, size) => setTxnQuery({ ...txnQuery, page, size })} /></div>
          </Card>}</StatePanel>
        </div>
      </Tabs.TabPane>
      <Tabs.TabPane key="alerts" title="预警处理">
        <Card className="filter-card">
          <Form layout="inline" onSubmit={() => setAlertQuery({ ...alertFilters, page: 1, size: alertQuery.size })} style={{ display: 'flex', gap: 12 }}>
            <Select allowClear value={alertFilters.status || undefined} placeholder="全部状态" style={{ width: 160 }} onChange={(value) => setAlertFilters({ ...alertFilters, status: value ?? '' })} options={Object.entries(alertStatusLabel).map(([value, label]) => ({ value, label }))} />
            <Button type="primary" htmlType="submit" icon={<IconFilter />}>筛选</Button>
          </Form>
        </Card>
        <div style={{ marginTop: 20 }}>
          <StatePanel {...alerts}>{alerts.data && <Card className="table-card">
            <Table rowKey="id" columns={alertColumns} data={alerts.data.rows} pagination={false} scroll={{ x: 1100 }} />
            <div className="pagination-wrap"><Pagination current={alertQuery.page} pageSize={alertQuery.size} total={alerts.data.total} showTotal onChange={(page, size) => setAlertQuery({ ...alertQuery, page, size })} /></div>
          </Card>}</StatePanel>
        </div>
      </Tabs.TabPane>
    </Tabs>

    <Modal title={editing ? '编辑物品' : '新增物品'} visible={itemModalVisible} onCancel={() => setItemModalVisible(false)} onOk={() => void saveItem()} confirmLoading={itemSaving} okText="保存" cancelText="取消">
      <Form layout="vertical">
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
          <Form.Item label="物品编码" required><Input value={itemForm.itemCode} onChange={(itemCode) => setItemForm({ ...itemForm, itemCode })} /></Form.Item>
          <Form.Item label="物品名称" required><Input value={itemForm.itemName} onChange={(itemName) => setItemForm({ ...itemForm, itemName })} /></Form.Item>
          <Form.Item label="单位"><Input value={itemForm.unit} onChange={(unit) => setItemForm({ ...itemForm, unit })} /></Form.Item>
          <Form.Item label="预警阈值（库存 ≤ 阈值触发，含等于）" required><InputNumber min={0} value={itemForm.alertThreshold} onChange={(alertThreshold) => setItemForm({ ...itemForm, alertThreshold: alertThreshold ?? 0 })} /></Form.Item>
          <Form.Item label="基地"><Input value={itemForm.factory} onChange={(factory) => setItemForm({ ...itemForm, factory })} /></Form.Item>
          <Form.Item label="库房"><Input value={itemForm.storageRoom} onChange={(storageRoom) => setItemForm({ ...itemForm, storageRoom })} /></Form.Item>
          <Form.Item label="库管员工号"><Input value={itemForm.roomKeeperEmpNo} onChange={(roomKeeperEmpNo) => setItemForm({ ...itemForm, roomKeeperEmpNo })} /></Form.Item>
          <Form.Item label="库管员姓名"><Input value={itemForm.roomKeeperEmpName} onChange={(roomKeeperEmpName) => setItemForm({ ...itemForm, roomKeeperEmpName })} /></Form.Item>
          <Form.Item label="所属基地编码"><Input value={itemForm.baseCode} onChange={(baseCode) => setItemForm({ ...itemForm, baseCode })} /></Form.Item>
          <Form.Item label="初始库存"><InputNumber min={0} value={itemForm.stockQty} onChange={(stockQty) => setItemForm({ ...itemForm, stockQty: stockQty ?? 0 })} disabled={!!editing} /></Form.Item>
        </div>
        <Form.Item label="备注"><Input.TextArea value={itemForm.remark} onChange={(remark) => setItemForm({ ...itemForm, remark })} /></Form.Item>
        {editing && <Typography.Text type="secondary">库存数量请通过「出入库流水 → 登记出入库」变更（盘点调整走 ADJUST），保证流水留痕。</Typography.Text>}
      </Form>
    </Modal>

    <Modal title="登记出入库" visible={txnModalVisible} onCancel={() => setTxnModalVisible(false)} onOk={() => void submitTxn()} confirmLoading={txnSaving} okText="提交" cancelText="取消">
      <Form layout="vertical">
        <Form.Item label="物品" required>
          <Select placeholder="选择物品" value={txnForm.itemId} onChange={(itemId) => setTxnForm({ ...txnForm, itemId })} showSearch
            options={(items.data ?? []).map((item) => ({ value: item.id, label: `${item.itemName}（${item.itemCode}，现存 ${item.stockQty}）` }))} />
        </Form.Item>
        <Form.Item label="类型" required>
          <Radio.Group type="button" value={txnForm.txnType} onChange={(txnType) => setTxnForm({ ...txnForm, txnType })} options={Object.entries(txnTypeLabel).map(([value, label]) => ({ value, label }))} />
        </Form.Item>
        <Form.Item label="数量（出库/盘点减少请输入正数，系统按类型换算符号）" required>
          <InputNumber value={Math.abs(txnForm.qty)} min={1} precision={0} onChange={(value) => setTxnForm({ ...txnForm, qty: (txnForm.txnType === 'OUT' ? -1 : 1) * (value ?? 0) })} />
        </Form.Item>
        <Form.Item label="备注"><Input.TextArea value={txnForm.remark} onChange={(remark) => setTxnForm({ ...txnForm, remark })} /></Form.Item>
        <Typography.Text type="secondary">操作人：{operator ? `${operator.empName}（${operator.empNo}）` : '未登录'}。库存不足时出库会被拒绝。</Typography.Text>
      </Form>
    </Modal>

    <Modal title={handleTarget?.action === 'CLOSE' ? '关闭预警' : '忽略预警'} visible={!!handleTarget} onCancel={() => setHandleTarget(null)} onOk={() => void submitHandle()} confirmLoading={handling} okText="确认" cancelText="取消">
      <Form layout="vertical">
        <Typography.Text>物品：{handleTarget?.event.itemName || '-'}（评估库存 {handleTarget?.event.lastEvalQty} / 阈值 {handleTarget?.event.thresholdSnapshot}）</Typography.Text>
        <Form.Item label={handleTarget?.action === 'CLOSE' ? '关闭原因（必填）' : '忽略说明（可选）'} required={handleTarget?.action === 'CLOSE'} style={{ marginTop: 12 }}>
          <Input.TextArea value={handleReason} onChange={setHandleReason} />
        </Form.Item>
      </Form>
    </Modal>
  </>
}
