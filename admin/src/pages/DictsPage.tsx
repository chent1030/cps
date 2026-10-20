/**
 * 字典管理页（双表：cps_dict_type + cps_dict）。
 * - 类型页签来自 /api/cps/admin/dict-types（可新建/编辑/启停/删除自定义类型；内置五组不可删/停/改码）
 * - 条目表格来自 /api/cps/admin/dicts（join 带出 typeCode/typeName；条目不可换类型）
 * - 内置条目（如 RISK_LEVEL 三项）不可删/不可停/不可改类型与值，可改显示名/排序/备注
 * - 后端强校验类型：ROOM_TYPE（房型/适用房型）/ RISK_LEVEL / PHOTO_CATEGORY；
 *   PLAN_TYPE / FREQUENCY 仅下拉展示（后端不校验，兼容 AI 生成的任意值）
 */
import { Button, Card, Form, Input, InputNumber, Message, Modal, Popconfirm, Select, Table, Tabs, Tag, Typography } from '@arco-design/web-react'
import type { TableColumnProps } from '@arco-design/web-react'
import { IconPlus, IconRefresh } from '@arco-design/web-react/icon'
import { useState } from 'react'
import { api } from '../api'
import { useRemote } from '../hooks/useRemote'
import { Enabled, FilterBar, PageHead, StatePanel, Toggle } from '../components/PageParts'
import { errorMessage } from '../lib/presentation'
import type { DictEntry, DictType } from '../types'

const dictTypePattern = /^[A-Z][A-Z0-9_]{0,63}$/

export function DictsPage() {
  const typesRemote = useRemote(() => api.dictTypes(), [])
  const entriesRemote = useRemote(() => api.dicts(), [])
  const [editingType, setEditingType] = useState<Partial<DictType> | null>(null)
  const [savingType, setSavingType] = useState(false)
  const [editing, setEditing] = useState<Partial<DictEntry> | null>(null)
  const [saving, setSaving] = useState(false)

  const types = [...(typesRemote.data ?? [])].sort((a, b) => (a.sortNo ?? 0) - (b.sortNo ?? 0) || a.typeCode.localeCompare(b.typeCode))
  const entries = entriesRemote.data ?? []
  const enabledTypes = types.filter((t) => t.enabled)

  async function refreshAll() {
    await Promise.all([typesRemote.refresh(), entriesRemote.refresh()])
  }

  // ---------- 字典类型 ----------

  async function toggleType(t: DictType) {
    try {
      await api.setDictTypeEnabled(t.id, !t.enabled)
      Message.success(t.enabled ? `已停用类型 ${t.typeName}` : `已启用类型 ${t.typeName}`)
      await refreshAll()
    } catch (error) { Message.error(errorMessage(error)) }
  }

  async function removeType(t: DictType) {
    try {
      await api.deleteDictType(t.id)
      Message.success(`已删除类型 ${t.typeName}`)
      await refreshAll()
    } catch (error) { Message.error(errorMessage(error)) }
  }

  async function saveType() {
    if (!editingType) return
    const typeCode = (editingType.typeCode ?? '').trim()
    const typeName = (editingType.typeName ?? '').trim()
    if (!editingType.id) {
      if (!typeCode) return Message.warning('类型编码必填')
      if (!dictTypePattern.test(typeCode)) return Message.warning('类型编码须为大写字母开头的 大写字母/数字/下划线（如 SHIFT）')
    }
    if (!typeName) return Message.warning('类型名称必填')
    setSavingType(true)
    try {
      await api.saveDictType({ ...editingType, typeCode, typeName })
      Message.success(editingType.id ? `已更新类型 ${typeName}` : `已创建类型 ${typeName}`)
      setEditingType(null)
      await refreshAll()
    } catch (error) { Message.error(errorMessage(error)) } finally { setSavingType(false) }
  }

  // ---------- 字典条目 ----------

  async function toggle(entry: DictEntry) {
    try {
      await api.setDictEnabled(entry.id, !entry.enabled)
      Message.success(entry.enabled ? `已停用 ${entry.itemLabel}` : `已启用 ${entry.itemLabel}`)
      await entriesRemote.refresh()
    } catch (error) { Message.error(errorMessage(error)) }
  }

  async function remove(entry: DictEntry) {
    try {
      await api.deleteDict(entry.id)
      Message.success(`已删除 ${entry.typeName}/${entry.itemLabel}`)
      await entriesRemote.refresh()
    } catch (error) { Message.error(errorMessage(error)) }
  }

  async function save() {
    if (!editing) return
    const typeCode = (editing.typeCode ?? '').trim()
    const itemValue = (editing.itemValue ?? '').trim()
    const itemLabel = (editing.itemLabel ?? '').trim()
    if (!typeCode) return Message.warning('字典类型必填（请先在「新建类型」中创建）')
    if (!itemValue) return Message.warning('选项值必填')
    if (!itemLabel) return Message.warning('显示名必填')
    setSaving(true)
    try {
      await api.saveDict({ ...editing, typeCode, itemValue, itemLabel })
      Message.success(editing.id ? `已更新 ${typeCode}/${itemLabel}` : `已创建 ${typeCode}/${itemLabel}`)
      setEditing(null)
      await entriesRemote.refresh()
    } catch (error) { Message.error(errorMessage(error)) } finally { setSaving(false) }
  }

  const columns: TableColumnProps<DictEntry>[] = [
    { title: '选项值（存库）', dataIndex: 'itemValue', width: 160 },
    { title: '显示名', dataIndex: 'itemLabel', width: 160 },
    { title: '排序', dataIndex: 'sortNo', width: 70, render: (v: number) => v ?? 0 },
    { title: '备注', dataIndex: 'remark', ellipsis: true, render: (v: string) => v || '-' },
    { title: '状态', width: 88, dataIndex: 'enabled', render: (v: boolean) => <Enabled value={v} /> },
    {
      title: '操作', width: 140, align: 'right',
      render: (_, r) => <>
        <Button type="text" size="small" onClick={() => setEditing({ ...r })}>编辑</Button>
        {!r.builtin && <Toggle value={r.enabled} onClick={() => void toggle(r)} />}
        {!r.builtin && <Popconfirm title={`删除 ${r.itemLabel}？停用可逆、删除不可恢复`} onOk={() => void remove(r)}>
          <Button type="text" size="small" status="danger">删除</Button>
        </Popconfirm>}
      </>,
    },
  ]

  const loading = typesRemote.loading || entriesRemote.loading
  const error = typesRemote.error || entriesRemote.error

  return <>
    <PageHead title="字典管理" detail="系统选项配置：类型与条目两级，可扩展自定义字典组" />
    <div className="page-body">
      <Card className="filter-card">
        <FilterBar>
          <Button icon={<IconRefresh />} onClick={() => void refreshAll()}>刷新</Button>
          <Button status="success" icon={<IconPlus />} onClick={() => setEditingType({ sortNo: (types.at(-1)?.sortNo ?? 0) + 1 })}>新建类型</Button>
        </FilterBar>
      </Card>
      <StatePanel loading={loading} error={error} onRetry={() => void refreshAll()}>
        {typesRemote.data && entriesRemote.data && <Card className="table-card">
          <Tabs defaultActiveTab={types[0]?.typeCode}>
            {types.map((t) => <Tabs.TabPane key={t.typeCode} title={<>{t.typeName} {!t.enabled && <Tag color="gray" style={{ marginLeft: 4 }}>已停用</Tag>} <Typography.Text type="secondary" style={{ fontSize: 12, fontWeight: 400 }}>{entries.filter((e) => e.typeCode === t.typeCode).length}</Typography.Text></>}>
              <div className="table-toolbar">
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>{t.typeCode}{t.description ? ` · ${t.description}` : ''}</Typography.Text>
                <div className="filter-bar">
                  <Button status="success" size="small" icon={<IconPlus />} disabled={!t.enabled} onClick={() => setEditing({ typeCode: t.typeCode, sortNo: entries.filter((e) => e.typeCode === t.typeCode).length + 1 })}>新建{t.typeName}项</Button>
                  <Button type="text" size="small" onClick={() => setEditingType({ ...t })}>编辑类型</Button>
                  {!t.builtin && <Toggle value={t.enabled} onClick={() => void toggleType(t)} />}
                  {!t.builtin && <Popconfirm title={`删除类型 ${t.typeName}？其下条目也需先清空`} onOk={() => void removeType(t)}>
                    <Button type="text" size="small" status="danger">删除类型</Button>
                  </Popconfirm>}
                </div>
              </div>
              <Table rowKey="id" columns={columns} data={entries.filter((e) => e.typeCode === t.typeCode)} pagination={false} scroll={{ x: 860 }} />
            </Tabs.TabPane>)}
          </Tabs>
        </Card>}
      </StatePanel>
    </div>

    <Modal title={editingType?.id ? `编辑类型 ${editingType.typeName ?? ''}` : '新建字典类型'} visible={!!editingType} onCancel={() => setEditingType(null)} onOk={() => void saveType()} confirmLoading={savingType} okText="保存" cancelText="取消" style={{ width: 640 }}>
      {editingType && <Form layout="vertical" className="form-grid">
        <Form.Item label="类型编码" required>
          <Input value={editingType.typeCode ?? ''} onChange={(v) => setEditingType({ ...editingType, typeCode: v })} disabled={!!editingType.id} placeholder="如 SHIFT / BUILDING（大写蛇形）" />
        </Form.Item>
        <Form.Item label="类型名称" required><Input value={editingType.typeName ?? ''} onChange={(v) => setEditingType({ ...editingType, typeName: v })} placeholder="如 班次 / 楼栋" /></Form.Item>
        <Form.Item label="排序号"><InputNumber value={editingType.sortNo ?? 0} min={0} step={1} onChange={(v) => setEditingType({ ...editingType, sortNo: typeof v === 'number' ? v : 0 })} /></Form.Item>
        <Form.Item label="说明"><Input value={editingType.description ?? ''} onChange={(v) => setEditingType({ ...editingType, description: v })} placeholder="用途说明（可选）" /></Form.Item>
      </Form>}
    </Modal>

    <Modal title={editing?.id ? `编辑字典项 ${editing.typeName ?? ''}/${editing.itemValue ?? ''}` : '新建字典项'} visible={!!editing} onCancel={() => setEditing(null)} onOk={() => void save()} confirmLoading={saving} okText="保存" cancelText="取消" style={{ width: 640 }}>
      {editing && <Form layout="vertical" className="form-grid">
        <Form.Item label="字典类型" required>
          <Select
            value={editing.typeCode || undefined}
            onChange={(v) => setEditing({ ...editing, typeCode: v ?? '' })}
            options={enabledTypes.map((t) => ({ value: t.typeCode, label: `${t.typeName}（${t.typeCode}）` }))}
            disabled={!!editing.builtin}
            notFoundContent="请先在上方「新建类型」创建"
            placeholder="选择类型"
          />
        </Form.Item>
        <Form.Item label="选项值（存库）" required>
          <Input value={editing.itemValue ?? ''} onChange={(v) => setEditing({ ...editing, itemValue: v })} disabled={!!editing.builtin} placeholder="如 配电房 / HIGH / DAILY" />
        </Form.Item>
        <Form.Item label="显示名" required><Input value={editing.itemLabel ?? ''} onChange={(v) => setEditing({ ...editing, itemLabel: v })} placeholder="如 高风险 / 每日" /></Form.Item>
        <Form.Item label="排序号"><InputNumber value={editing.sortNo ?? 0} min={0} step={1} onChange={(v) => setEditing({ ...editing, sortNo: typeof v === 'number' ? v : 0 })} /></Form.Item>
        <Form.Item label="备注" className="span-2"><Input value={editing.remark ?? ''} onChange={(v) => setEditing({ ...editing, remark: v })} placeholder="用途说明（可选）" /></Form.Item>
      </Form>}
    </Modal>
  </>
}
