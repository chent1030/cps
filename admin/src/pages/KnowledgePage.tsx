import { Button, Card, Form, Image, Input, Message, Modal, Pagination, Select, Space, Table, Typography, Upload } from '@arco-design/web-react'
import type { TableColumnProps } from '@arco-design/web-react'
import { IconDownload, IconFilter, IconPlus, IconSync, IconUpload } from '@arco-design/web-react/icon'
import { useEffect, useState } from 'react'
import { api } from '../api'
import { Enabled, PageHead, StatePanel, Toggle } from '../components/PageParts'
import { useRemote } from '../hooks/useRemote'
import { errorMessage } from '../lib/presentation'
import type { KnowledgeCase, KnowledgeMaterial } from '../types'

const initialFilters = { category: '', enabled: '' }
export function KnowledgePage() {
  const [filters, setFilters] = useState(initialFilters); const [query, setQuery] = useState({ ...initialFilters, page: 1, pageSize: 20 }); const [exporting, setExporting] = useState(false); const [materialCase, setMaterialCase] = useState<KnowledgeCase | null>(null); const [createMaterialVisible, setCreateMaterialVisible] = useState(false)
  const remote = useRemote(() => api.casePage({ ...query, enabled: query.enabled || undefined }), [query])
  const [materials, setMaterials] = useState<Record<number, KnowledgeMaterial[]>>({})
  useEffect(() => { if (!remote.data) return; void Promise.all(remote.data.records.map(async (item) => [item.id, await api.caseMaterials(item.id)] as const)).then((entries) => setMaterials(Object.fromEntries(entries))) }, [remote.data])
  async function sync(item: KnowledgeCase) { try { const result = await api.syncCaseVectors(item.id); Message.success(`已提交 ${result.synced} 条素材同步`); await remote.refresh() } catch (error) { Message.error(errorMessage(error)) } }
  async function toggle(item: KnowledgeCase) { try { await api.setCaseEnabled(item.id, !item.enabled); setMaterials({}); await remote.refresh(); Message.success(item.enabled ? '知识集合已停用' : '知识集合已启用') } catch (error) { Message.error(errorMessage(error)) } }
  async function exportRows() { setExporting(true); try { await api.exportCases({ category: filters.category, enabled: filters.enabled || undefined }); Message.success('导出文件已生成') } catch (error) { Message.error(errorMessage(error)) } finally { setExporting(false) } }
  const columns: TableColumnProps<KnowledgeCase>[] = [
    { title: '问题分类', width: 220, render: (_, item) => <><Typography.Text bold>{item.categoryL1Name || '-'}</Typography.Text><div><Typography.Text type="secondary">{item.categoryL2Name || '-'}</Typography.Text></div></> },
    { title: '状态', width: 90, render: (_, item) => <Enabled value={item.enabled} /> },
    { title: '素材', width: 220, render: (_, item) => <Image.PreviewGroup><Space wrap>{(materials[item.id] || []).map((material) => <Image key={material.id} src={material.fileUrl} width={58} height={44} style={{ objectFit: 'cover' }} preview />)}</Space></Image.PreviewGroup> },
    { title: '原因', width: 260, render: (_, item) => <div>{(materials[item.id] || []).map((material) => <Typography.Paragraph key={material.id} ellipsis={{ rows: 2 }} style={{ marginBottom: 4 }}>{material.reason || '-'}</Typography.Paragraph>)}</div> },
    { title: '措施', width: 260, render: (_, item) => <div>{(materials[item.id] || []).map((material) => <Typography.Paragraph key={material.id} ellipsis={{ rows: 2 }} style={{ marginBottom: 4 }}>{material.measure || '-'}</Typography.Paragraph>)}</div> },
    { title: '向量 ID', width: 220, render: (_, item) => <MaterialValues values={(materials[item.id] || []).map((material) => material.milvusVectorId || '-')} /> },
    { title: '失败重试次数', width: 130, render: (_, item) => <MaterialValues values={(materials[item.id] || []).map((material) => String(material.vectorRetryCount || 0))} /> },
    { title: '同步时间', width: 180, render: (_, item) => <MaterialValues values={(materials[item.id] || []).map((material) => material.vectorUpdatedAt || '-')} /> },
    { title: '同步状态', width: 140, render: (_, item) => <MaterialValues values={(materials[item.id] || []).map((material) => material.vectorStatus || 'PENDING')} /> },
    { title: '错误提示', width: 240, render: (_, item) => <MaterialValues error values={(materials[item.id] || []).map((material) => material.vectorErrorMsg || '-')} /> },
    { title: '操作', width: 280, fixed: 'right', render: (_, item) => <Space><Button type="text" icon={<IconPlus />} onClick={() => setMaterialCase(item)}>添加素材</Button><Button type="text" icon={<IconSync />} onClick={() => void sync(item)}>同步</Button><Toggle value={item.enabled} onClick={() => void toggle(item)} /></Space> },
  ]
  return <><PageHead title="案例知识库" detail="按二级问题分类归集多条素材、原因和措施，并将素材同步为向量。"><Space><Button icon={<IconDownload />} loading={exporting} onClick={() => void exportRows()}>导出 Excel</Button><Button type="primary" icon={<IconPlus />} onClick={() => setCreateMaterialVisible(true)}>添加素材</Button></Space></PageHead><Card className="filter-card"><Form layout="inline" onSubmit={() => setQuery({ ...filters, page: 1, pageSize: query.pageSize })} style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: 12 }}><Input allowClear value={filters.category} onChange={(category) => setFilters({ ...filters, category })} placeholder="问题分类" /><Select allowClear value={filters.enabled || undefined} onChange={(enabled) => setFilters({ ...filters, enabled: enabled ?? '' })} options={[{ label: '已启用', value: 'true' }, { label: '已停用', value: 'false' }]} placeholder="全部状态" /><Button type="primary" htmlType="submit" icon={<IconFilter />} style={{ justifySelf: 'start' }}>应用筛选</Button></Form></Card><div style={{ marginTop: 20 }}><StatePanel {...remote}>{remote.data && <Card className="table-card"><Table rowKey="id" columns={columns} data={remote.data.records} pagination={false} scroll={{ x: 1960 }} /><div className="pagination-wrap"><Typography.Text type="secondary">共 {remote.data.total} 个二级分类</Typography.Text><Pagination current={query.page} pageSize={query.pageSize} total={remote.data.total} showTotal showJumper size="small" onChange={(page, pageSize) => setQuery({ ...query, page, pageSize: pageSize || query.pageSize })} /></div></Card>}</StatePanel></div>{createMaterialVisible && <CreateMaterialModal onClose={() => setCreateMaterialVisible(false)} onSaved={async () => { setCreateMaterialVisible(false); setMaterials({}); await remote.refresh() }} />}{materialCase && <MaterialModal item={materialCase} onClose={() => setMaterialCase(null)} onSaved={async () => { setMaterialCase(null); setMaterials({}); await remote.refresh() }} />}</>
}

function MaterialValues({ values, error = false }: { values: string[]; error?: boolean }) {
  return <div>{values.length ? values.map((value, index) => <Typography.Paragraph key={`${value}-${index}`} type={error && value !== '-' ? 'error' : undefined} ellipsis={{ rows: 2 }} style={{ marginBottom: 4 }}>{value}</Typography.Paragraph>) : '-'}</div>
}

function CreateMaterialModal({ onClose, onSaved }: { onClose: () => void; onSaved: () => Promise<void> }) {
  const categories = useRemote(() => api.categories(), [])
  const [parentId, setParentId] = useState('')
  const children = useRemote(() => parentId ? api.categories(Number(parentId)) : Promise.resolve([]), [parentId])
  const [childId, setChildId] = useState('')
  const [file, setFile] = useState<File | null>(null)
  const [form, setForm] = useState({ reason: '', measure: '' })
  const [saving, setSaving] = useState(false)
  async function save() {
    const parent = categories.data?.find((item) => item.id === Number(parentId))
    const child = children.data?.find((item) => item.id === Number(childId))
    if (!parent || !child || !file || !form.reason.trim() || !form.measure.trim()) return Message.warning('请选择二级分类，上传素材并填写原因、措施')
    setSaving(true)
    try {
      await api.uploadKnowledgeMaterial(file, { categoryL1Id: parent.id, categoryL2Id: child.id, categoryL1Name: parent.categoryName, categoryL2Name: child.categoryName, ...form })
      Message.success('素材已上传，等待向量同步')
      await onSaved()
    } catch (error) { Message.error(errorMessage(error)) } finally { setSaving(false) }
  }
  return <Modal title="添加案例素材" visible onCancel={onClose} onOk={() => void save()} confirmLoading={saving} okText="保存" cancelText="取消"><Form layout="vertical"><Form.Item label="一级分类" required><Select loading={categories.loading} value={parentId || undefined} onChange={(value) => { setParentId(value); setChildId('') }} placeholder="请选择一级分类">{categories.data?.map((item) => <Select.Option key={item.id} value={String(item.id)}>{item.categoryName}</Select.Option>)}</Select></Form.Item><Form.Item label="二级分类" required><Select disabled={!parentId} loading={children.loading} value={childId || undefined} onChange={setChildId} placeholder="请选择二级分类">{children.data?.map((item) => <Select.Option key={item.id} value={String(item.id)}>{item.categoryName}</Select.Option>)}</Select></Form.Item><MaterialFileField file={file} onSelect={setFile} /><Form.Item label="原因" required><Input.TextArea value={form.reason} onChange={(reason) => setForm({ ...form, reason })} autoSize={{ minRows: 3 }} /></Form.Item><Form.Item label="措施" required><Input.TextArea value={form.measure} onChange={(measure) => setForm({ ...form, measure })} autoSize={{ minRows: 3 }} /></Form.Item></Form></Modal>
}

function MaterialModal({ item, onClose, onSaved }: { item: KnowledgeCase; onClose: () => void; onSaved: () => Promise<void> }) {
  const [file, setFile] = useState<File | null>(null)
  const [form, setForm] = useState({ reason: '', measure: '' })
  const [saving, setSaving] = useState(false)
  async function save() {
    if (!file || !form.reason.trim() || !form.measure.trim()) return Message.warning('请上传素材并填写原因、措施')
    setSaving(true)
    try {
      await api.uploadKnowledgeMaterial(file, { caseId: item.id, categoryL1Id: item.categoryL1Id, categoryL2Id: item.categoryL2Id, categoryL1Name: item.categoryL1Name, categoryL2Name: item.categoryL2Name, ...form })
      Message.success('素材已上传，等待向量同步')
      await onSaved()
    } catch (error) { Message.error(errorMessage(error)) } finally { setSaving(false) }
  }
  return <Modal title={`添加素材 · ${item.categoryL2Name}`} visible onCancel={onClose} onOk={() => void save()} confirmLoading={saving} okText="保存" cancelText="取消"><Form layout="vertical"><MaterialFileField file={file} onSelect={setFile} /><Form.Item label="原因" required><Input.TextArea value={form.reason} onChange={(reason) => setForm({ ...form, reason })} autoSize={{ minRows: 3 }} /></Form.Item><Form.Item label="措施" required><Input.TextArea value={form.measure} onChange={(measure) => setForm({ ...form, measure })} autoSize={{ minRows: 3 }} /></Form.Item></Form></Modal>
}

function MaterialFileField({ file, onSelect }: { file: File | null; onSelect: (file: File) => void }) {
  return <Form.Item label="素材图片" required extra="支持 JPEG、PNG、WebP，单个文件不超过 20MB"><Upload accept=".jpg,.jpeg,.png,.webp" showUploadList={false} beforeUpload={(nextFile) => { onSelect(nextFile); return false }}><Button icon={<IconUpload />}>{file ? `已选择：${file.name}` : '选择图片'}</Button></Upload></Form.Item>
}
