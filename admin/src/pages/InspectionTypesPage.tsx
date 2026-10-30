import { Button, Card, Form, Input, InputNumber, Message, Modal, Select, Space, Switch, Table, Tag, Typography } from '@arco-design/web-react'
import type { TableColumnProps } from '@arco-design/web-react'
import { IconDelete, IconEdit, IconPlus } from '@arco-design/web-react/icon'
import { useState } from 'react'
import { api } from '../api'
import { useRemote } from '../hooks/useRemote'
import { StructuredValue } from '../components/StructuredValue'
import { errorMessage } from '../lib/presentation'
import type { InspectionType } from '../types'

/**
 * 巡检类型管理（Phase C1 统一模型）。
 * 固定字段：编码 / 巡检名称 / 巡检内容 / 是否分区域；
 * 不分区域时点检项直接挂类型（checkItemIds 多选）；分区域时点检项在「巡检区域」页挂到区域上。
 */

const COLORS = [
  { label: '蓝', value: 'blue' },
  { label: '绿', value: 'green' },
  { label: '红', value: 'red' },
  { label: '橙', value: 'orange' },
  { label: '紫', value: 'purple' },
  { label: '灰', value: 'gray' },
]

interface FormState {
  id?: number
  code: string
  name: string
  content: string
  zoneFlag: number
  checkItemCodes: string[]
  color: string
  enabled: number
  sortOrder: number
  defaultAssigneeEmpNo: string
  remark: string
}

const EMPTY_FORM: FormState = {
  code: '', name: '', content: '', zoneFlag: 1, checkItemCodes: [],
  color: 'blue', enabled: 1, sortOrder: 0, defaultAssigneeEmpNo: '', remark: '',
}

export function InspectionTypesPage() {
  const remote = useRemote(() => api.inspectionTypePage({ page: 1, pageSize: 100 }), [])
  const checkItemsRemote = useRemote(() => api.checkItems({ enabled: true }), [])
  const [editing, setEditing] = useState<InspectionType | null>(null)
  const [visible, setVisible] = useState(false)
  const [saving, setSaving] = useState(false)
  const [form, setForm] = useState<FormState>(EMPTY_FORM)

  const checkItemOptions = (checkItemsRemote.data ?? []).map((i) => ({
    value: i.itemCode,
    label: `${i.itemCode} · ${i.content ?? ''}`,
  }))

  const openCreate = () => {
    setEditing(null)
    setForm({ ...EMPTY_FORM })
    void checkItemsRemote.refresh()
    setVisible(true)
  }

  const openEdit = (item: InspectionType) => {
    setEditing(item)
    setForm({
      id: item.id,
      code: item.code,
      name: item.name,
      content: item.content ?? '',
      zoneFlag: item.zoneFlag ?? 0,
      checkItemCodes: (item.checkItemIds ?? '').split(',').map((s) => s.trim()).filter(Boolean),
      color: item.color ?? 'blue',
      enabled: item.enabled ? 1 : 0,
      sortOrder: item.sortOrder ?? 0,
      defaultAssigneeEmpNo: item.defaultAssigneeEmpNo ?? '',
      remark: item.remark ?? '',
    })
    void checkItemsRemote.refresh()
    setVisible(true)
  }

  async function save() {
    if (!form.code.trim() || !form.name.trim()) {
      Message.warning('请填写「编码」和「巡检名称」后再保存')
      return
    }
    if (!form.content.trim()) {
      Message.warning('请填写「巡检内容」后再保存')
      return
    }
    setSaving(true)
    try {
      await api.saveInspectionType({
        id: form.id,
        code: form.code.trim().toUpperCase(),
        name: form.name.trim(),
        content: form.content.trim(),
        zoneFlag: form.zoneFlag,
        checkItemIds: form.zoneFlag === 0 && form.checkItemCodes.length ? form.checkItemCodes.join(',') : null,
        color: form.color || undefined,
        enabled: form.enabled,
        sortOrder: form.sortOrder,
        defaultAssigneeEmpNo: form.defaultAssigneeEmpNo.trim() || undefined,
        remark: form.remark.trim() || undefined,
      })
      Message.success(editing ? '巡检类型已更新' : '巡检类型已创建')
      setVisible(false)
      await remote.refresh()
    } catch (e) {
      Message.error(errorMessage(e))
    } finally {
      setSaving(false)
    }
  }

  async function toggleEnabled(item: InspectionType) {
    try {
      await api.setInspectionTypeEnabled(item.id, !item.enabled)
      Message.success(item.enabled ? '已停用' : '已启用')
      await remote.refresh()
    } catch (e) {
      Message.error(errorMessage(e))
    }
  }

  async function remove(item: InspectionType) {
    Modal.confirm({
      title: `确认删除「${item.name}」？`,
      content: '已有问题引用或已挂区域的类型不可删除，可先停用。',
      okButtonProps: { status: 'danger' },
      onOk: async () => {
        try {
          await api.deleteInspectionType(item.id)
          Message.success('已删除')
          await remote.refresh()
        } catch (e) {
          Message.error(errorMessage(e))
        }
      },
    })
  }

  const columns: TableColumnProps<InspectionType>[] = [
    { title: 'ID', dataIndex: 'id', width: 64 },
    {
      title: '编码', dataIndex: 'code', width: 170,
      render: (v, r) => <>{<Tag color="arcoblue">{v}</Tag>}{r.builtin === 1 ? <Tag color="purple" style={{ marginLeft: 4 }}>内置</Tag> : null}</>,
    },
    { title: '巡检名称', dataIndex: 'name', width: 140 },
    { title: '巡检内容', dataIndex: 'content', render: (v) => <StructuredValue value={v} textRows={2} /> },
    {
      title: '是否分区域', dataIndex: 'zoneFlag', width: 110,
      render: (v: number) => v === 1 ? <Tag color="green">分区域</Tag> : <Tag color="orange">不分区域</Tag>,
    },
    {
      title: '直挂点检项', dataIndex: 'checkItemIds', width: 160,
      render: (v: string | null, r) => {
        if (r.zoneFlag === 1) return <Typography.Text type="secondary">按区域配置</Typography.Text>
        const codes = (v ?? '').split(',').filter(Boolean)
        return codes.length ? <Tag color="cyan">{codes.length} 项</Tag> : <Typography.Text type="secondary">未配置</Typography.Text>
      },
    },
    { title: '排序', dataIndex: 'sortOrder', width: 70 },
    {
      title: '状态', dataIndex: 'enabled', width: 90,
      render: (v: number | boolean) => v ? <Tag color="green">启用</Tag> : <Tag color="red">停用</Tag>,
    },
    { title: '更新时间', dataIndex: 'updatedAt', width: 170 },
    {
      title: '操作', width: 220, fixed: 'right',
      render: (_, item) => (
        <Space size="mini">
          <Button size="mini" type="text" icon={<IconEdit />} onClick={() => openEdit(item)}>编辑</Button>
          {item.builtin !== 1 && (
            <>
              <Button size="mini" type="text" onClick={() => toggleEnabled(item)}>{item.enabled ? '停用' : '启用'}</Button>
              <Button size="mini" type="text" status="danger" icon={<IconDelete />} onClick={() => remove(item)}>删除</Button>
            </>
          )}
          {item.builtin === 1 && <Typography.Text type="secondary" style={{ fontSize: 12 }}>内置不可删停</Typography.Text>}
        </Space>
      ),
    },
  ]

  return (
    <div className="cps-page">
      <div className="cps-page__head">
        <Typography.Title heading={5} style={{ margin: 0 }}>巡检类型</Typography.Title>
        <Typography.Text type="secondary">
          定义"巡检什么"：编码 / 巡检名称 / 巡检内容 / 是否分区域。分区域的类型在「巡检区域」页配置区域与点检项；不分区域的类型在此直接勾选点检项。
        </Typography.Text>
        <Space>
          <Button type="primary" icon={<IconPlus />} onClick={openCreate}>新建类型</Button>
          <Button onClick={remote.refresh} loading={remote.loading}>刷新</Button>
        </Space>
      </div>

      <Card bordered={false} style={{ marginTop: 12 }}>
        <Table
          rowKey="id"
          loading={remote.loading}
          data={remote.data?.records ?? []}
          columns={columns}
          pagination={false}
          size="small"
          scroll={{ x: 1300 }}
        />
        {remote.error && <div style={{ color: 'red', marginTop: 8 }}>{errorMessage(remote.error)}</div>}
      </Card>

      <Modal
        title={editing ? `编辑：${editing.name}` : '新建巡检类型'}
        visible={visible}
        onCancel={() => setVisible(false)}
        onOk={save}
        okButtonProps={{ loading: saving }}
        style={{ width: 720 }}
        okText="保存"
      >
        <Form labelCol={{ span: 5 }} wrapperCol={{ span: 19 }}>
          <Form.Item label="编码" required extra="大写字母数字，创建后不可修改（被区域/问题引用）">
            <Input
              value={form.code}
              placeholder="如 ROOM_CHECK / LINE_PATROL"
              onChange={(v) => setForm((f) => ({ ...f, code: v }))}
              disabled={!!editing}
            />
          </Form.Item>
          <Form.Item label="巡检名称" required>
            <Input value={form.name} placeholder="如 辅房点检 / 拉线巡检" onChange={(v) => setForm((f) => ({ ...f, name: v }))} />
          </Form.Item>
          <Form.Item label="巡检内容" required extra="说明这个巡检做什么、怎么做，会展示给巡检员">
            <Input.TextArea
              value={form.content}
              placeholder="如：对厂区辅房（仓库/配电房/工具间等）按区域逐项点检，拍照留证并计分"
              autoSize={{ minRows: 2, maxRows: 5 }}
              onChange={(v) => setForm((f) => ({ ...f, content: v }))}
            />
          </Form.Item>
          <Form.Item label="是否分区域" required extra={editing?.builtin === 1 ? '内置类型固定为不分区域（标准问题上报流程）' : '分区域：任务按区域逐个执行，点检项挂在区域上；不分区域：点检项直接挂类型，整体执行一次'}>
            <Switch
              checked={form.zoneFlag === 1}
              disabled={editing?.builtin === 1}
              checkedText="分区域"
              uncheckedText="不分区域"
              onChange={(v) => setForm((f) => ({ ...f, zoneFlag: v ? 1 : 0 }))}
            />
          </Form.Item>
          {form.zoneFlag === 0 && (
            <Form.Item label="点检项" extra="选填：勾选后该类型可作为巡检任务整体执行；不选则仅用于问题上报分类（如质量/安全/设备）。点检项在「巡检区域与点检项」页维护">
              <Select
                mode="multiple"
                allowClear
                showSearch
                value={form.checkItemCodes}
                onChange={(v) => setForm((f) => ({ ...f, checkItemCodes: v as string[] }))}
                options={checkItemOptions}
                placeholder="选择本类型要点检的项目（可不选）"
                notFoundContent="请先在「巡检区域与点检项」页创建点检项"
              />
            </Form.Item>
          )}
          <Form.Item label="颜色">
            <Select value={form.color} onChange={(v) => setForm((f) => ({ ...f, color: v }))} options={COLORS} style={{ width: 140 }} />
          </Form.Item>
          <Form.Item label="排序" extra="数字越小越靠前">
            <InputNumber value={form.sortOrder} onChange={(v) => setForm((f) => ({ ...f, sortOrder: Number(v ?? 0) }))} min={0} />
          </Form.Item>
          <Form.Item label="默认反馈人工号">
            <Input value={form.defaultAssigneeEmpNo} placeholder="留空时使用派发规则" onChange={(v) => setForm((f) => ({ ...f, defaultAssigneeEmpNo: v }))} />
          </Form.Item>
          <Form.Item label="启用">
            <Switch checked={form.enabled === 1} onChange={(v) => setForm((f) => ({ ...f, enabled: v ? 1 : 0 }))} />
          </Form.Item>
          <Form.Item label="备注">
            <Input value={form.remark} onChange={(v) => setForm((f) => ({ ...f, remark: v }))} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
