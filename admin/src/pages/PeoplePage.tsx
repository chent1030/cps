import { Button, Card, Form, Input, Message, Modal, Pagination, Select, Space, Table, Typography, Upload } from '@arco-design/web-react'
import type { TableColumnProps } from '@arco-design/web-react'
import { IconDownload, IconEdit, IconFilter, IconPlus, IconUpload } from '@arco-design/web-react/icon'
import * as XLSX from 'xlsx'
import { useState } from 'react'
import { api } from '../api'
import { Enabled, PageHead, StatePanel, Toggle } from '../components/PageParts'
import { useRemote } from '../hooks/useRemote'
import { errorMessage } from '../lib/presentation'
import type { AreaPersonConfig } from '../types'

const initialFilters = { keyword: '', factory: '', area: '', line: '', process: '', enabled: '' }
const emptyForm = { factory: '', area: '', line: '', process: '', empNo: '', empName: '' }
type Location = { value: string; label: string }

export function PeoplePage() {
  const [filters, setFilters] = useState(initialFilters)
  const [query, setQuery] = useState({ ...initialFilters, page: 1, pageSize: 20 })
  const [visible, setVisible] = useState(false)
  const [saving, setSaving] = useState(false)
  const [exporting, setExporting] = useState(false)
  const [form, setForm] = useState(emptyForm)
  const [editing, setEditing] = useState<AreaPersonConfig | null>(null)
  const remote = useRemote(() => api.peoplePage({ ...query, enabled: query.enabled || undefined }), [query])
  const filterFactories = useRemote(() => api.locationOptions('factory'), [])
  const filterAreas = useRemote<Location[]>(() => filters.factory ? api.locationOptions('area', { factory: filters.factory }) : Promise.resolve([]), [filters.factory])
  const filterLines = useRemote<Location[]>(() => filters.factory && filters.area ? api.locationOptions('line', { factory: filters.factory, area: filters.area }) : Promise.resolve([]), [filters.factory, filters.area])
  const filterProcesses = useRemote<Location[]>(() => filters.factory && filters.area && filters.line ? api.locationOptions('process', { factory: filters.factory, area: filters.area, line: filters.line }) : Promise.resolve([]), [filters.factory, filters.area, filters.line])

  const setFilterLocation = (key: 'factory' | 'area' | 'line' | 'process', value: string) => setFilters((current) => ({
    ...current,
    [key]: value,
    ...(key === 'factory' ? { area: '', line: '', process: '' } : {}),
    ...(key === 'area' ? { line: '', process: '' } : {}),
    ...(key === 'line' ? { process: '' } : {}),
  }))

  const closeModal = () => {
    setVisible(false)
    setEditing(null)
    setForm(emptyForm)
  }

  const openCreate = () => {
    setEditing(null)
    setForm(emptyForm)
    setVisible(true)
  }

  const openEdit = (item: AreaPersonConfig) => {
    setEditing(item)
    setForm({
      factory: item.factory ?? '',
      area: item.area ?? '',
      line: item.line ?? '',
      process: item.process ?? '',
      empNo: item.empNo ?? '',
      empName: item.empName ?? '',
    })
    setVisible(true)
  }

  async function save() {
    if (Object.values(form).some((value) => !value.trim())) {
      return Message.warning('工厂、区域、拉线、工序、员工工号和员工姓名均为必填项')
    }
    setSaving(true)
    try {
      await api.savePerson({ ...form, id: editing?.id, enabled: editing?.enabled ?? true })
      Message.success(editing ? '人员配置已更新' : '人员配置已创建')
      closeModal()
      await remote.refresh()
    } catch (error) {
      Message.error(errorMessage(error))
    } finally {
      setSaving(false)
    }
  }

  async function toggle(item: AreaPersonConfig) {
    try {
      await api.setPersonEnabled(item.id, !item.enabled)
      await remote.refresh()
    } catch (error) {
      Message.error(errorMessage(error))
    }
  }

  async function exportRows() {
    setExporting(true)
    try {
      await api.exportPeople(query)
      Message.success('导出文件已生成')
    } catch (error) {
      Message.error(errorMessage(error))
    } finally {
      setExporting(false)
    }
  }

  async function downloadImportTemplate() {
    try {
      await api.downloadPeopleImportTemplate()
      Message.success('导入模板已下载')
    } catch (error) {
      Message.error(errorMessage(error))
    }
  }

  async function importExcel(file: File) {
    try {
      const workbook = XLSX.read(await file.arrayBuffer(), { type: 'array' })
      const rows = XLSX.utils.sheet_to_json<Record<string, unknown>>(workbook.Sheets[workbook.SheetNames[0]])
      const requiredColumns = ['工厂', '区域', '拉线', '工序', '员工工号', '员工姓名']
      if (!rows.length) return Message.warning('Excel 中没有可导入的数据')
      const invalidRow = rows.findIndex((row) => requiredColumns.some((key) => !String(row[key] ?? '').trim()))
      if (invalidRow >= 0) return Message.warning(`第 ${invalidRow + 2} 行存在必填字段为空，未执行导入`)
      for (const row of rows) {
        const value = (key: string) => String(row[key] ?? '').trim()
        await api.savePerson({
          factory: value('工厂'), area: value('区域'), line: value('拉线'), process: value('工序'),
          empNo: value('员工工号'), empName: value('员工姓名'), enabled: true,
        })
      }
      Message.success(`已导入 ${rows.length} 条配置`)
      await remote.refresh()
    } catch (error) {
      Message.error(errorMessage(error))
    }
    return false
  }

  const columns: TableColumnProps<AreaPersonConfig>[] = [
    {
      title: '适用范围', width: 190,
      render: (_, item) => {
        const firstLine = `${item.factory} / ${item.area}`
        const secondLine = `${item.line} / ${item.process}`
        return <div title={`${firstLine} / ${secondLine}`} style={{ width: 170, minWidth: 0 }}><Typography.Text bold ellipsis={{ showTooltip: true }} style={{ display: 'block', maxWidth: 170 }}>{firstLine}</Typography.Text><Typography.Text type="secondary" ellipsis={{ showTooltip: true }} style={{ display: 'block', maxWidth: 170, fontSize: 12 }}>{secondLine}</Typography.Text></div>
      },
    },
    { title: '匹配员工', width: 220, render: (_, item) => <>{item.empName || '-'} <Typography.Text type="secondary">({item.empNo})</Typography.Text></> },
    { title: '创建人', width: 180, render: (_, item) => item.createdBy ? `${item.createdBy}（${item.createdName || '-'}）` : '-' },
    { title: '状态', width: 100, render: (_, item) => <Enabled value={item.enabled} /> },
    {
      title: '操作', width: 150, align: 'right',
      render: (_, item) => <Space size={4}><Button type="text" size="small" icon={<IconEdit />} onClick={() => openEdit(item)}>编辑</Button><Toggle value={item.enabled} onClick={() => void toggle(item)} /></Space>,
    },
  ]

  return <>
    <PageHead title="区域人员" detail="被配置员工信息必填，当前操作人由管理端登录会话自动记录。">
      <Space size={10}>
        <Upload accept=".xlsx,.xls" showUploadList={false} beforeUpload={importExcel}><Button icon={<IconUpload />}>导入 Excel</Button></Upload>
        <Button onClick={() => void downloadImportTemplate()}>下载导入模板</Button>
        <Button icon={<IconDownload />} loading={exporting} onClick={() => void exportRows()}>导出 Excel</Button>
        <Button type="primary" icon={<IconPlus />} onClick={openCreate}>新增配置</Button>
      </Space>
    </PageHead>
    <Card className="filter-card">
      <Form layout="inline" onSubmit={() => setQuery({ ...filters, page: 1, pageSize: query.pageSize })} style={{ display: 'grid', gridTemplateColumns: 'repeat(5, minmax(130px, 1fr)) auto', gap: 12 }}>
        <Input.Search allowClear value={filters.keyword} onChange={(keyword) => setFilters({ ...filters, keyword })} placeholder="员工或工号" />
        <Select allowClear value={filters.factory || undefined} options={filterFactories.data ?? undefined} onChange={(factory) => setFilterLocation('factory', factory ?? '')} placeholder="全部工厂" />
        <Select allowClear value={filters.area || undefined} options={filterAreas.data ?? undefined} onChange={(area) => setFilterLocation('area', area ?? '')} disabled={!filters.factory} placeholder="全部区域" />
        <Select allowClear value={filters.line || undefined} options={filterLines.data ?? undefined} onChange={(line) => setFilterLocation('line', line ?? '')} disabled={!filters.area} placeholder="全部拉线" />
        <Select allowClear value={filters.process || undefined} options={filterProcesses.data ?? undefined} onChange={(process) => setFilterLocation('process', process ?? '')} disabled={!filters.line} placeholder="全部工序" />
        <Button type="primary" htmlType="submit" icon={<IconFilter />}>筛选</Button>
      </Form>
    </Card>
    <div style={{ marginTop: 20 }}>
      <StatePanel {...remote}>{remote.data && <Card className="table-card">
        <Table rowKey="id" columns={columns} data={remote.data.records} pagination={false} scroll={{ x: 800 }} />
        <div className="pagination-wrap"><Pagination current={remote.data.page} pageSize={remote.data.pageSize} total={remote.data.total} sizeCanChange showTotal onChange={(page, pageSize) => setQuery({ ...query, page, pageSize })} /></div>
      </Card>}</StatePanel>
    </div>
    <Modal title={editing ? '编辑区域人员配置' : '新增区域人员配置'} visible={visible} onCancel={closeModal} onOk={() => void save()} confirmLoading={saving} okText="保存" cancelText="取消">
      <Form layout="vertical">
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
          <Form.Item label="工厂" required><Input value={form.factory} onChange={(factory) => setForm({ ...form, factory })} /></Form.Item>
          <Form.Item label="区域" required><Input value={form.area} onChange={(area) => setForm({ ...form, area })} /></Form.Item>
          <Form.Item label="拉线" required><Input value={form.line} onChange={(line) => setForm({ ...form, line })} /></Form.Item>
          <Form.Item label="工序" required><Input value={form.process} onChange={(process) => setForm({ ...form, process })} /></Form.Item>
          <Form.Item label="员工工号" required><Input value={form.empNo} onChange={(empNo) => setForm({ ...form, empNo })} /></Form.Item>
          <Form.Item label="员工姓名" required><Input value={form.empName} onChange={(empName) => setForm({ ...form, empName })} /></Form.Item>
        </div>
        <Typography.Text type="secondary">当前操作人由登录会话自动带入，创建时写入创建人，编辑时写入更新人。</Typography.Text>
      </Form>
    </Modal>
  </>
}
