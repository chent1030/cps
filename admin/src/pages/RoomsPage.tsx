/**
 * 巡检区域与点检项管理页（Phase C1 统一模型，替代原「辅房与点检项」）。
 * - 巡检区域：编码唯一、必须挂在「分区域」巡检类型下；点检项从点检项库勾选（checkItemIds，空=按房型自动匹配兼容旧逻辑）
 * - 点检项：编码唯一；点检类型多选（是否合格/文本框/需拍照）；需拍照时最低张数+判定条件（自由文本）；扣分进统一巡检计分
 * - 风险等级/照片分类下拉走字典（cps_dict 唯一事实源）
 */
import { Button, Card, Checkbox, Form, Input, InputNumber, Message, Modal, Pagination, Select, Table, Tabs, Tag, Typography } from '@arco-design/web-react'
import type { TableColumnProps } from '@arco-design/web-react'
import { IconDelete, IconFilter, IconPlus, IconRefresh } from '@arco-design/web-react/icon'
import { useState } from 'react'
import { api } from '../api'
import { useRemote } from '../hooks/useRemote'
import { StructuredValue } from '../components/StructuredValue'
import { Enabled, PageHead, StatePanel, Toggle } from '../components/PageParts'
import { errorMessage } from '../lib/presentation'
import type { CheckItem, DictEntry, InspectionArea, InspectionType } from '../types'

const riskColor: Record<string, string> = { HIGH: 'red', MEDIUM: 'orange', LOW: 'green' }
const itemStatusOptions = [
  { value: 'APPLICABLE', label: '适用' },
  { value: 'NOT_APPLICABLE', label: '不适用' },
]
const itemStatusLabel: Record<string, string> = { APPLICABLE: '适用', NOT_APPLICABLE: '不适用' }

/** 点检类型选项（Phase C1 多选） */
const CHECK_TYPE_OPTIONS = [
  { value: 'PASS_FAIL', label: '是否合格' },
  { value: 'TEXT', label: '文本框' },
  { value: 'PHOTO', label: '需要拍照' },
]
const checkTypeLabel: Record<string, string> = { PASS_FAIL: '合格判定', TEXT: '文本', PHOTO: '拍照' }

/** 字典组 → 下拉选项（启用项） */
const dictOptions = (dicts: DictEntry[], type: string) =>
  dicts.filter((d) => d.typeCode === type).map((d) => ({ value: d.itemValue, label: d.itemLabel }))

function required(value: string | null | undefined, name: string): string | null {
  return value && value.trim() ? null : `${name}必填`
}

// ============================================================
// 巡检区域 Tab
// ============================================================
function AreasSection({ riskOptions, zonedTypes }: { riskOptions: { value: string; label: string }[]; zonedTypes: InspectionType[] }) {
  const riskLabel = new Map(riskOptions.map((o) => [o.value, o.label]))
  const [typeFilter, setTypeFilter] = useState('')
  const [keyword, setKeyword] = useState('')
  const [applied, setApplied] = useState({ typeCode: '', keyword: '', page: 1, pageSize: 20 })
  const remote = useRemote(
    () => api.inspectionAreaPage({
      typeCode: applied.typeCode || undefined,
      keyword: applied.keyword || undefined,
      page: applied.page,
      pageSize: applied.pageSize,
    }),
    [applied],
  )
  const checkItemsRemote = useRemote(() => api.checkItems({ enabled: true }), [])
  const [editing, setEditing] = useState<Partial<InspectionArea> | null>(null)
  const [saving, setSaving] = useState(false)

  const typeOptions = zonedTypes.map((t) => ({ value: t.code, label: `${t.name}（${t.code}）` }))
  const typeName = (code?: string) => zonedTypes.find((t) => t.code === code)?.name ?? code ?? '-'

  function openForm(area: Partial<InspectionArea>) {
    void checkItemsRemote.refresh()
    setEditing(area)
  }

  async function toggle(area: InspectionArea) {
    try {
      await api.setInspectionAreaEnabled(area.id, !area.enabled)
      Message.success(area.enabled ? `已停用 ${area.areaCode}` : `已启用 ${area.areaCode}`)
      await remote.refresh()
    } catch (error) { Message.error(errorMessage(error)) }
  }

  async function remove(area: InspectionArea) {
    Modal.confirm({
      title: `确认删除区域「${area.areaName}」？`,
      content: '已有巡检记录引用的区域建议停用而非删除。',
      okButtonProps: { status: 'danger' },
      onOk: async () => {
        try {
          await api.deleteInspectionArea(area.id)
          Message.success('已删除')
          await remote.refresh()
        } catch (e) { Message.error(errorMessage(e)) }
      },
    })
  }

  async function save() {
    if (!editing) return
    const body = { ...editing }
    for (const [value, name] of [
      [body.areaCode, '区域编码'], [body.typeCode, '所属巡检类型'], [body.areaName, '区域名称'],
      [body.keeperEmpNo, '责任人工号'], [body.keeperEmpName, '责任人姓名'],
    ] as const) {
      const problem = required(value, name)
      if (problem) return Message.warning(problem)
    }
    setSaving(true)
    try {
      await api.saveInspectionArea(body.id, {
        areaCode: body.areaCode!.trim(),
        typeCode: body.typeCode!.trim(),
        areaName: body.areaName!.trim(),
        factory: body.factory?.trim() || undefined,
        area: body.area?.trim() || undefined,
        lineOrRoomType: body.lineOrRoomType?.trim() || undefined,
        process: body.process?.trim() || undefined,
        building: body.building?.trim() || undefined,
        doorNo: body.doorNo?.trim() || undefined,
        riskLevel: body.riskLevel || 'LOW',
        keeperEmpNo: body.keeperEmpNo!.trim(),
        keeperEmpName: body.keeperEmpName!.trim(),
        checkItemIds: body.checkItemIds || null,
        remark: body.remark?.trim() || undefined,
        enabled: body.enabled ?? true,
      })
      Message.success(body.id ? `已更新区域 ${body.areaCode}` : `已创建区域 ${body.areaCode}`)
      setEditing(null)
      await remote.refresh()
    } catch (error) { Message.error(errorMessage(error)) } finally { setSaving(false) }
  }

  const columns: TableColumnProps<InspectionArea>[] = [
    { title: '编码', dataIndex: 'areaCode', width: 110 },
    { title: '巡检类型', dataIndex: 'typeCode', width: 120, render: (v: string) => <Tag color="arcoblue">{typeName(v)}</Tag> },
    { title: '区域名称', dataIndex: 'areaName', width: 140, render: (v, r) => <>{v || '-'} {r.building || r.doorNo ? <Typography.Text type="secondary">{[r.building, r.doorNo].filter(Boolean).join(' ')}</Typography.Text> : null}</> },
    { title: '工厂', dataIndex: 'factory', width: 90, render: (v: string) => v || '-' },
    { title: '区域', dataIndex: 'area', width: 90, render: (v: string) => v || '-' },
    { title: '拉线|房型', dataIndex: 'lineOrRoomType', width: 100, render: (v: string) => v || '-' },
    { title: '工序', dataIndex: 'process', width: 90, render: (v: string) => v || '-' },
    { title: '风险', dataIndex: 'riskLevel', width: 84, render: (v: string) => <Tag color={riskColor[v] ?? 'gray'}>{riskLabel.get(v) ?? v ?? '-'}</Tag> },
    {
      title: '点检项', width: 96,
      render: (_, r) => {
        const codes = (r.checkItemIds ?? '').split(',').filter(Boolean)
        return codes.length ? <Tag color="green">{codes.length} 项</Tag> : <Typography.Text type="secondary">自动匹配</Typography.Text>
      },
    },
    { title: '责任人', width: 110, render: (_, r) => r.keeperEmpName || r.keeperEmpNo || '-' },
    { title: '状态', width: 88, dataIndex: 'enabled', render: (v: boolean) => <Enabled value={v} /> },
    {
      title: '操作', width: 170, align: 'right', fixed: 'right',
      render: (_, r) => <>
        <Button type="text" size="small" onClick={() => openForm({ ...r })}>编辑</Button>
        <Toggle value={r.enabled} onClick={() => void toggle(r)} />
        <Button type="text" size="small" status="danger" icon={<IconDelete />} onClick={() => remove(r)} />
      </>,
    },
  ]

  return <div className="page-body">
    <Card className="filter-card">
      <Form layout="inline" className="filter-bar" onSubmit={() => setApplied({ typeCode: typeFilter, keyword, page: 1, pageSize: applied.pageSize })}>
        <Select allowClear value={typeFilter || undefined} placeholder="按巡检类型筛选" style={{ width: 200 }} onChange={(v) => setTypeFilter(v ?? '')} options={typeOptions} />
        <Input.Search allowClear value={keyword} placeholder="编码/名称/责任人" style={{ width: 220 }} onChange={(v) => setKeyword(v)} onSearch={() => setApplied({ typeCode: typeFilter, keyword, page: 1, pageSize: applied.pageSize })} />
        <Button type="primary" htmlType="submit" icon={<IconFilter />}>筛选</Button>
        <Button icon={<IconRefresh />} onClick={() => void remote.refresh()}>刷新</Button>
        <Button status="success" htmlType="button" icon={<IconPlus />} onClick={() => openForm({ riskLevel: 'LOW', typeCode: zonedTypes[0]?.code })}>新建区域</Button>
      </Form>
    </Card>
    <StatePanel {...remote}>{remote.data && <Card className="table-card">
      <Table rowKey="id" columns={columns} data={remote.data.records} pagination={false} scroll={{ x: 1400 }} />
      {remote.data.total > 0 && (
        <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 12 }}>
          <Pagination
            current={applied.page}
            pageSize={applied.pageSize}
            total={remote.data.total}
            sizeCanChange
            sizeOptions={[20, 50, 100]}
            onChange={(p, ps) => setApplied((c) => ({ ...c, page: p, pageSize: ps }))}
            showTotal
          />
        </div>
      )}
    </Card>}</StatePanel>

    <Modal title={editing?.id ? `编辑区域 ${editing.areaCode ?? ''}` : '新建巡检区域'} visible={!!editing} onCancel={() => setEditing(null)} onOk={() => void save()} confirmLoading={saving} okText="保存" cancelText="取消" style={{ width: 680 }}>
      {editing && <Form layout="vertical" className="form-grid">
        <Form.Item label="区域编码（唯一）" required><Input value={editing.areaCode ?? ''} onChange={v => setEditing({ ...editing, areaCode: v })} placeholder="如 JX-01 / LINE-A01" disabled={!!editing.id} /></Form.Item>
        <Form.Item label="所属巡检类型" required extra="仅「分区域」类型可挂区域">
          <Select value={editing.typeCode || undefined} onChange={v => setEditing({ ...editing, typeCode: v })} options={typeOptions} placeholder="选择分区域的巡检类型" notFoundContent="请先在「巡检类型」页创建分区域类型" />
        </Form.Item>
        <Form.Item label="区域名称" required><Input value={editing.areaName ?? ''} onChange={v => setEditing({ ...editing, areaName: v })} placeholder="如 配电间J1 / A线巡检段" /></Form.Item>
        <Form.Item label="风险等级"><Select value={editing.riskLevel ?? 'LOW'} onChange={v => setEditing({ ...editing, riskLevel: v })} options={riskOptions.length ? riskOptions : [{ value: 'HIGH', label: '高' }, { value: 'MEDIUM', label: '中' }, { value: 'LOW', label: '低' }]} /></Form.Item>
        <Form.Item label="工厂"><Input value={editing.factory ?? ''} onChange={v => setEditing({ ...editing, factory: v })} /></Form.Item>
        <Form.Item label="区域（厂区分区）"><Input value={editing.area ?? ''} onChange={v => setEditing({ ...editing, area: v })} /></Form.Item>
        <Form.Item label="拉线 | 房型"><Input value={editing.lineOrRoomType ?? ''} onChange={v => setEditing({ ...editing, lineOrRoomType: v })} placeholder="产线巡检填拉线；辅房类填房型" /></Form.Item>
        <Form.Item label="工序"><Input value={editing.process ?? ''} onChange={v => setEditing({ ...editing, process: v })} placeholder="拉线场景填写；辅房可空" /></Form.Item>
        <Form.Item label="楼栋"><Input value={editing.building ?? ''} onChange={v => setEditing({ ...editing, building: v })} /></Form.Item>
        <Form.Item label="门牌号"><Input value={editing.doorNo ?? ''} onChange={v => setEditing({ ...editing, doorNo: v })} /></Form.Item>
        <Form.Item label="责任人工号" required><Input value={editing.keeperEmpNo ?? ''} onChange={v => setEditing({ ...editing, keeperEmpNo: v })} /></Form.Item>
        <Form.Item label="责任人姓名" required><Input value={editing.keeperEmpName ?? ''} onChange={v => setEditing({ ...editing, keeperEmpName: v })} /></Form.Item>
        <Form.Item label="点检项" className="span-2" extra="勾选后巡检单只按此清单生成；不选=按点检项适用范围自动匹配">
          <Select
            mode="multiple"
            allowClear
            showSearch
            value={(editing.checkItemIds ?? '').split(',').filter(Boolean)}
            onChange={(codes) => setEditing({ ...editing, checkItemIds: (codes as string[]).join(',') })}
            options={(checkItemsRemote.data ?? []).map((i) => ({ value: i.itemCode, label: `${i.content ?? ''}（${i.itemCode}）` }))}
            placeholder="选择本区域要点检的项目"
            notFoundContent="请先在「点检项配置」页签中创建"
          />
        </Form.Item>
        <Form.Item label="备注" className="span-2"><Input value={editing.remark ?? ''} onChange={v => setEditing({ ...editing, remark: v })} /></Form.Item>
      </Form>}
    </Modal>
  </div>
}

// ============================================================
// 点检项 Tab（Phase C1 扩展：点检类型多选/拍照要求/文本提示）
// ============================================================
function CheckItemsSection({ roomTypeOptions, photoCategoryOptions }: { roomTypeOptions: { value: string; label: string }[]; photoCategoryOptions: { value: string; label: string }[] }) {
  const [statusFilter, setStatusFilter] = useState('')
  const [applied, setApplied] = useState('')
  const remote = useRemote(() => api.checkItems(applied ? { status: applied } : undefined), [applied])
  const [editing, setEditing] = useState<Partial<CheckItem> | null>(null)
  const [saving, setSaving] = useState(false)

  const editingCheckTypes = (editing?.checkTypes ?? 'PASS_FAIL').split(',').filter(Boolean)
  const needPhoto = editingCheckTypes.includes('PHOTO')
  const needText = editingCheckTypes.includes('TEXT')

  async function toggle(item: CheckItem) {
    try {
      await api.setCheckItemEnabled(item.id, !item.enabled)
      Message.success(item.enabled ? `已停用 ${item.itemCode}` : `已启用 ${item.itemCode}`)
      await remote.refresh()
    } catch (error) { Message.error(errorMessage(error)) }
  }

  async function remove(item: CheckItem) {
    Modal.confirm({
      title: `确认删除点检项「${item.content || item.itemCode}」？`,
      content: '已被巡检记录引用的点检项不可删除（后端会返回具体原因），可先停用。',
      okButtonProps: { status: 'danger' },
      onOk: async () => {
        try {
          await api.deleteCheckItem(item.id)
          Message.success(`已删除点检项 ${item.itemCode}`)
          await remote.refresh()
        } catch (error) { Message.error(errorMessage(error)) }
      },
    })
  }

  async function save() {
    if (!editing) return
    const body = { ...editing }
    for (const [value, name] of [[body.itemCode, '点检项编码'], [body.content, '点检内容'], [body.photoCategory, '照片分类']] as const) {
      const problem = required(value, name)
      if (problem) return Message.warning(problem)
    }
    if ((body.deductScore ?? 0) < 0) return Message.warning('扣分不能为负数')
    if (editingCheckTypes.length === 0) return Message.warning('点检类型至少选一项')
    if (needPhoto && (!body.photoMinCount || body.photoMinCount < 1)) return Message.warning('需要拍照时必须设置最低张数（≥1）')
    setSaving(true)
    try {
      await api.saveCheckItem({
        ...body,
        itemCode: body.itemCode?.trim(),
        content: body.content?.trim(),
        status: body.status || 'APPLICABLE',
        checkTypes: editingCheckTypes.join(','),
        photoMinCount: needPhoto ? body.photoMinCount : null,
        photoCriteria: needPhoto ? body.photoCriteria : null,
        textHint: needText ? body.textHint : null,
      })
      Message.success(body.id ? `已更新点检项 ${body.itemCode}` : `已创建点检项 ${body.itemCode}`)
      setEditing(null)
      await remote.refresh()
    } catch (error) { Message.error(errorMessage(error)) } finally { setSaving(false) }
  }

  const columns: TableColumnProps<CheckItem>[] = [
    { title: '编码', dataIndex: 'itemCode', width: 110 },
    { title: '点检内容', render: (_, r) => <StructuredValue value={r.content} textRows={2} /> },
    {
      title: '点检类型', dataIndex: 'checkTypes', width: 170,
      render: (v: string) => (v ?? 'PASS_FAIL').split(',').filter(Boolean).map((t) => <Tag key={t} color={t === 'PHOTO' ? 'orange' : t === 'TEXT' ? 'arcoblue' : 'green'} style={{ marginRight: 4 }}>{checkTypeLabel[t] ?? t}</Tag>),
    },
    {
      title: '拍照要求', width: 150,
      render: (_, r) => (r.checkTypes ?? '').includes('PHOTO')
        ? <Typography.Text type="secondary">≥{r.photoMinCount ?? 1} 张{r.photoCriteria ? ` · ${r.photoCriteria}` : ''}</Typography.Text>
        : '-',
    },
    { title: '扣分', dataIndex: 'deductScore', width: 70, render: (v: number) => v ?? 0 },
    { title: '适用状态', dataIndex: 'status', width: 90, render: (v: string) => <Tag color={v === 'NOT_APPLICABLE' ? 'gray' : 'green'}>{itemStatusLabel[v ?? 'APPLICABLE'] ?? v}</Tag> },
    { title: '适用房型', dataIndex: 'applicableRoomTypes', width: 130, render: (v: string) => v ? <StructuredValue value={v} textRows={2} /> : '全部房型' },
    { title: '版本', dataIndex: 'configVersion', width: 70, render: (v: number) => `v${v ?? '-'}` },
    { title: '状态', width: 88, dataIndex: 'enabled', render: (v: boolean) => <Enabled value={v} /> },
    {
      title: '操作', width: 180, align: 'right', fixed: 'right',
      render: (_, r) => <><Button type="text" size="small" onClick={() => setEditing({ ...r })}>编辑</Button><Toggle value={r.enabled} onClick={() => void toggle(r)} /><Button type="text" size="small" status="danger" icon={<IconDelete />} onClick={() => remove(r)} /></>,
    },
  ]

  return <div className="page-body">
    <Card className="filter-card">
      <Form layout="inline" className="filter-bar" onSubmit={() => setApplied(statusFilter)}>
        <Select allowClear value={statusFilter || undefined} placeholder="按适用状态筛选" style={{ width: 180 }} onChange={(v) => setStatusFilter(v ?? '')} options={itemStatusOptions} />
        <Button type="primary" htmlType="submit" icon={<IconFilter />}>筛选</Button>
        <Button icon={<IconRefresh />} onClick={() => void remote.refresh()}>刷新</Button>
        <Button status="success" htmlType="button" icon={<IconPlus />} onClick={() => setEditing({ status: 'APPLICABLE', deductScore: 0, checkTypes: 'PASS_FAIL' })}>新建点检项</Button>
      </Form>
    </Card>
    <StatePanel {...remote}>{remote.data && <Card className="table-card">
      <Table rowKey="id" columns={columns} data={remote.data} pagination={false} scroll={{ x: 1300 }} />
    </Card>}</StatePanel>

    <Modal title={editing?.id ? `编辑点检项 ${editing.itemCode ?? ''}` : '新建点检项'} visible={!!editing} onCancel={() => setEditing(null)} onOk={() => void save()} confirmLoading={saving} okText="保存" cancelText="取消" style={{ width: 680 }}>
      {editing && <Form layout="vertical" className="form-grid">
        <Form.Item label="点检项编码（唯一）" required><Input value={editing.itemCode ?? ''} onChange={v => setEditing({ ...editing, itemCode: v })} placeholder="如 CHK-PD-01" disabled={!!editing.id} /></Form.Item>
        <Form.Item label="照片分类" required><Select value={editing.photoCategory || undefined} onChange={v => setEditing({ ...editing, photoCategory: v ?? '' })} options={photoCategoryOptions} placeholder="选择照片分类" notFoundContent="请先在字典管理中维护 PHOTO_CATEGORY" /></Form.Item>
        <Form.Item label="点检内容" required className="span-2"><Input.TextArea value={editing.content ?? ''} onChange={v => setEditing({ ...editing, content: v })} placeholder="如 灭火器压力表指针在绿区、无遮挡" /></Form.Item>
        <Form.Item label="点检类型（多选）" required className="span-2" extra="决定执行时该项怎么填：是否合格 → 合格/不合格按钮；文本框 → 填说明；需要拍照 → 拍照并校验最低张数">
          <Checkbox.Group
            value={editingCheckTypes}
            onChange={(v) => setEditing({ ...editing, checkTypes: (v as string[]).join(',') })}
            options={CHECK_TYPE_OPTIONS}
          />
        </Form.Item>
        {needPhoto && <>
          <Form.Item label="最低拍照张数" required><InputNumber value={editing.photoMinCount ?? 1} min={1} step={1} onChange={v => setEditing({ ...editing, photoMinCount: typeof v === 'number' ? v : 1 })} /></Form.Item>
          <Form.Item label="拍照判定条件" className="span-2" extra="自由文本：给 AI/人工判定的参考条件，如「全景1张+缺陷特写，灭火器压力表指针在绿区」"><Input.TextArea value={editing.photoCriteria ?? ''} onChange={v => setEditing({ ...editing, photoCriteria: v })} placeholder="如 全景1张+缺陷特写，指针在绿区" autoSize={{ minRows: 2, maxRows: 4 }} /></Form.Item>
        </>}
        {needText && (
          <Form.Item label="文本框提示语" className="span-2"><Input value={editing.textHint ?? ''} onChange={v => setEditing({ ...editing, textHint: v })} placeholder="如 请描述异常情况" /></Form.Item>
        )}
        <Form.Item label="扣分（≥0）" extra="不合格时按此扣分（统一巡检计分 100-Σ扣分）"><InputNumber value={editing.deductScore ?? 0} min={0} step={1} onChange={v => setEditing({ ...editing, deductScore: typeof v === 'number' ? v : 0 })} /></Form.Item>
        <Form.Item label="适用状态"><Select value={editing.status ?? 'APPLICABLE'} onChange={v => setEditing({ ...editing, status: v })} options={itemStatusOptions} /></Form.Item>
        <Form.Item label="适用房型（不选 = 全部房型）" className="span-2">
          <Select
            mode="multiple"
            allowClear
            value={(editing.applicableRoomTypes ?? '').split(',').map((s) => s.trim()).filter(Boolean)}
            onChange={(v) => setEditing({ ...editing, applicableRoomTypes: (v as string[]).join(',') })}
            options={roomTypeOptions}
            placeholder="选择适用房型"
            notFoundContent="请先在字典管理中维护 ROOM_TYPE"
          />
        </Form.Item>
      </Form>}
    </Modal>
  </div>
}

export function RoomsPage() {
  // 风险等级/房型/照片分类下拉走字典；分区域类型列表来自巡检类型
  const dictRemote = useRemote(() => api.dicts({ enabled: true }), [])
  const typesRemote = useRemote(() => api.inspectionTypes(), [])
  const dicts = dictRemote.data ?? []
  const roomTypeOptions = dictOptions(dicts, 'ROOM_TYPE')
  const riskOptions = dictOptions(dicts, 'RISK_LEVEL')
  const photoCategoryOptions = dictOptions(dicts, 'PHOTO_CATEGORY')
  const zonedTypes = (typesRemote.data ?? []).filter((t) => t.zoneFlag === 1 && t.enabled)
  return <>
    <PageHead title="巡检区域与点检项" detail={<>统一巡检模型：区域挂在「分区域」巡检类型下并勾选点检项；点检项定义判定方式与扣分。选项在<a href="#/dicts">字典管理</a>中维护</>} />
    <Tabs defaultActiveTab="areas">
      <Tabs.TabPane key="areas" title="巡检区域"><AreasSection riskOptions={riskOptions} zonedTypes={zonedTypes} /></Tabs.TabPane>
      <Tabs.TabPane key="items" title="点检项配置"><CheckItemsSection roomTypeOptions={roomTypeOptions} photoCategoryOptions={photoCategoryOptions} /></Tabs.TabPane>
    </Tabs>
  </>
}
