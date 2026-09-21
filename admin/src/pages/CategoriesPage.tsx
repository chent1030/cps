import { Button, Card, Empty, Form, Input, Message, Modal, Pagination, Select, Space, Spin, Table } from '@arco-design/web-react'
import type { TableColumnProps } from '@arco-design/web-react'
import { IconDownload, IconEdit, IconFilter, IconPlus } from '@arco-design/web-react/icon'
import { useState } from 'react'
import { api } from '../api'
import { Enabled, PageHead, StatePanel, Toggle } from '../components/PageParts'
import { useRemote } from '../hooks/useRemote'
import { errorMessage } from '../lib/presentation'
import type { Category } from '../types'

const initialFilters = { keyword: '', enabled: '' }
const options = [{ label: '已启用', value: 'true' }, { label: '已停用', value: 'false' }]
const isPrimary = (item: Category) => !item.parentId || item.parentId === 0 || item.categoryLevel === 1

export function CategoriesPage() {
  const [filters, setFilters] = useState(initialFilters)
  const [query, setQuery] = useState({ ...initialFilters, page: 1, pageSize: 20 })
  const [visible, setVisible] = useState(false)
  const [saving, setSaving] = useState(false)
  const [exporting, setExporting] = useState(false)
  const [expandedKeys, setExpandedKeys] = useState<number[]>([])
  const [childrenByParent, setChildrenByParent] = useState<Record<number, Category[] | undefined>>({})
  const [loadingChildren, setLoadingChildren] = useState<number[]>([])
  const [form, setForm] = useState({ categoryName: '', categoryLevel: 1, parentId: 0 })
  const [editing, setEditing] = useState<Category | null>(null)
  const parents = useRemote(() => api.categories(), [])
  const remote = useRemote(() => api.categoryPage({ keyword: query.keyword, enabled: query.enabled || undefined, page: query.page, pageSize: query.pageSize }), [query])

  async function loadChildren(parentId: number, force = false) {
    if (!force && childrenByParent[parentId] !== undefined) return
    setLoadingChildren((current) => current.includes(parentId) ? current : [...current, parentId])
    try {
      const result = await api.categoryPage({ parentId, keyword: query.keyword, enabled: query.enabled || undefined, page: 1, pageSize: 100 })
      setChildrenByParent((current) => ({ ...current, [parentId]: result.records }))
    } catch (error) {
      Message.error(errorMessage(error))
    } finally {
      setLoadingChildren((current) => current.filter((id) => id !== parentId))
    }
  }

  async function save() {
    if (!form.categoryName.trim() || (form.categoryLevel === 2 && !form.parentId)) {
      return Message.warning('请完整填写分类名称和上级分类')
    }
    setSaving(true)
    try {
      await api.saveCategory({ ...form, id: editing?.id, enabled: editing?.enabled ?? true })
      Message.success(editing ? '分类已更新' : '分类已创建')
      closeModal()
      setChildrenByParent({})
      await remote.refresh()
    } catch (error) {
      Message.error(errorMessage(error))
    } finally {
      setSaving(false)
    }
  }

  const closeModal = () => {
    setVisible(false)
    setEditing(null)
    setForm({ categoryName: '', categoryLevel: 1, parentId: 0 })
  }

  const openCreate = () => {
    setEditing(null)
    setForm({ categoryName: '', categoryLevel: 1, parentId: 0 })
    setVisible(true)
  }

  const openEdit = (item: Category) => {
    setEditing(item)
    setForm({ categoryName: item.categoryName, categoryLevel: item.categoryLevel ?? (isPrimary(item) ? 1 : 2), parentId: item.parentId ?? 0 })
    setVisible(true)
  }

  async function toggle(item: Category) {
    try {
      await api.setCategoryEnabled(item.id, !item.enabled)
      Message.success(item.enabled ? '分类已停用' : '分类已启用')
      await remote.refresh()
      if (!isPrimary(item) && item.parentId) await loadChildren(item.parentId, true)
    } catch (error) {
      Message.error(errorMessage(error))
    }
  }

  async function exportRows() {
    setExporting(true)
    try {
      await api.exportCategories({ keyword: filters.keyword, enabled: filters.enabled || undefined })
      Message.success('导出文件已生成')
    } catch (error) {
      Message.error(errorMessage(error))
    } finally {
      setExporting(false)
    }
  }

  const columns: TableColumnProps<Category>[] = [
    {
      title: '分类名称', width: 240,
      render: (_, item) => <div title={item.categoryName} style={{ maxWidth: 210, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{item.categoryName}</div>,
    },
    { title: '层级', width: 130, render: (_, item) => isPrimary(item) ? '一级分类' : '二级分类' },
    { title: '状态', width: 110, render: (_, item) => <Enabled value={item.enabled} /> },
    { title: '操作', width: 156, align: 'right', render: (_, item) => <Space size={4}><Button type="text" size="small" icon={<IconEdit />} onClick={(event) => { event.stopPropagation(); openEdit(item) }}>编辑</Button><Toggle value={item.enabled} onClick={() => void toggle(item)} /></Space> },
  ]

  const secondaryColumns: TableColumnProps<Category>[] = columns.map((column) => ({ ...column, title: column.title === '分类名称' ? '二级分类名称' : column.title }))
  const onExpand = (item: Category, expanded: boolean) => {
    if (!isPrimary(item)) return
    setExpandedKeys((current) => expanded ? [...new Set([...current, item.id])] : current.filter((id) => id !== item.id))
    if (expanded) void loadChildren(item.id)
  }

  return <>
    <PageHead title="问题分类" detail="一级分类可点击展开，查看并维护其下属二级分类。">
      <div style={{ display: 'flex', gap: 10 }}>
        <Button icon={<IconDownload />} loading={exporting} onClick={() => void exportRows()}>导出 Excel</Button>
        <Button type="primary" icon={<IconPlus />} onClick={openCreate}>新建分类</Button>
      </div>
    </PageHead>
    <Card className="filter-card">
      <Form layout="inline" onSubmit={() => { setExpandedKeys([]); setChildrenByParent({}); setQuery({ ...filters, page: 1, pageSize: query.pageSize }) }} style={{ display: 'grid', gridTemplateColumns: 'minmax(240px, 1fr) 180px auto', gap: 12 }}>
        <Input.Search allowClear value={filters.keyword} onChange={(keyword) => setFilters({ ...filters, keyword })} placeholder="一级分类名称" />
        <Select allowClear value={filters.enabled || undefined} onChange={(enabled) => setFilters({ ...filters, enabled: enabled ?? '' })} options={options} placeholder="全部状态" />
        <Button type="primary" htmlType="submit" icon={<IconFilter />}>应用筛选</Button>
      </Form>
    </Card>
    <div style={{ marginTop: 20 }}>
      <StatePanel {...remote}>{remote.data && <Card className="table-card">
        <Table<Category>
          rowKey="id"
          columns={columns}
          data={remote.data.records}
          pagination={false}
          scroll={{ x: 650 }}
          expandedRowKeys={expandedKeys}
          onExpand={onExpand}
          expandProps={{ rowExpandable: isPrimary, expandRowByClick: true, width: 42 }}
          expandedRowRender={(item) => {
            if (loadingChildren.includes(item.id) || childrenByParent[item.id] === undefined) return <div style={{ padding: 16 }}><Spin size={16} /> 加载二级分类…</div>
            const children = childrenByParent[item.id] ?? []
            if (!children.length) return <div style={{ padding: 16 }}><Empty description="该一级分类下暂无二级分类" /></div>
            return <div style={{ padding: '8px 18px 14px 42px' }}><Table<Category> rowKey="id" columns={secondaryColumns} data={children} pagination={false} size="small" /></div>
          }}
        />
        <div className="pagination-wrap"><Pagination current={remote.data.page} pageSize={remote.data.pageSize} total={remote.data.total} sizeCanChange sizeOptions={[20, 50, 100]} showTotal onChange={(page, pageSize) => { setExpandedKeys([]); setChildrenByParent({}); setQuery({ ...query, page, pageSize }) }} /></div>
      </Card>}</StatePanel>
    </div>
    <Modal title={editing ? '编辑问题分类' : '新建问题分类'} visible={visible} onCancel={closeModal} onOk={() => void save()} confirmLoading={saving} okText="保存" cancelText="取消">
      <Form layout="vertical">
        <Form.Item label="分类层级" required><Select value={String(form.categoryLevel)} onChange={(value) => setForm({ ...form, categoryLevel: Number(value), parentId: 0 })}><Select.Option value="1">一级分类</Select.Option><Select.Option value="2">二级分类</Select.Option></Select></Form.Item>
        {form.categoryLevel === 2 && <Form.Item label="上级分类" required><Select loading={parents.loading} value={form.parentId ? String(form.parentId) : undefined} onChange={(value) => setForm({ ...form, parentId: Number(value) })} placeholder="请选择一级分类">{parents.data?.map((item) => <Select.Option key={item.id} value={String(item.id)}>{item.categoryName}</Select.Option>)}</Select></Form.Item>}
        <Form.Item label="分类名称" required><Input value={form.categoryName} onChange={(categoryName) => setForm({ ...form, categoryName })} placeholder="例如：品质异常" /></Form.Item>
      </Form>
    </Modal>
  </>
}
