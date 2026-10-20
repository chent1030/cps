/**
 * 巡检计划管理页（D2 审核工作台 + D4 建单状态 + D1 手动新建）。
 * - 状态筛选（默认待审核）+ 计划列表（展开行看任务）
 * - 手动新建计划：勾选任务类型（如只建辅房点检）+ 每类明细 + 辅房覆盖房间多选；草稿 JSON 直传不走 AI
 * - 详情抽屉：计划字段 + 草稿 JSON + 任务明细
 * - 批准 / 拒绝（PENDING_REVIEW）；建单状态（含建单范围列）+ 补建失败任务（APPROVED）
 */
import { Badge, Button, Card, Checkbox, Descriptions, Drawer, Form, Input, Message, Modal, Select, Table, Tag, Tooltip, Typography } from '@arco-design/web-react'
import type { TableColumnProps } from '@arco-design/web-react'
import { IconFilter, IconPlus, IconRefresh } from '@arco-design/web-react/icon'
import { useState } from 'react'
import { api, getAdminOperator } from '../api'
import { useRemote } from '../hooks/useRemote'
import { PageHead, StatePanel } from '../components/PageParts'
import { errorMessage } from '../lib/presentation'
import type { InspectionPlan, InspectionPlanRecordStatus, InspectionPlanStatus, InspectionPlanTask } from '../types'

const statusLabel: Record<string, string> = { PENDING_REVIEW: '待审核', APPROVED: '已批准', REJECTED: '已拒绝' }
const statusColor: Record<string, string> = { PENDING_REVIEW: 'orange', APPROVED: 'green', REJECTED: 'red' }
/** 历史枚举任务类型的展示名（Phase D 后新任务 taskType=巡检类型编码，从类型表取名） */
const taskTypeLabel: Record<string, string> = { INSPECT_RECTIFY: '整改复查', INSPECT_PATROL: '区域巡检', INSPECT_CHECK: '辅房点检' }
const buildResultLabel: Record<string, string> = { CREATED: '已建单', SKIPPED_EXISTING: '已存在跳过', CREATE_FAILED: '建单失败' }
interface TaskSpec { title: string; targetEmpNo: string; targetEmpName: string; frequency: string; acceptanceCriteria: string; evidenceRequirement: string; areaCodes: string[] }
const emptySpec = (): TaskSpec => ({ title: '', targetEmpNo: '', targetEmpName: '', frequency: '', acceptanceCriteria: '', evidenceRequirement: '', areaCodes: [] })

function prettyJson(raw?: string): string {
  if (!raw) return '（无）'
  try { return JSON.stringify(JSON.parse(raw), null, 2) } catch { return raw }
}

export function InspectionPlansPage() {
  const [status, setStatus] = useState<InspectionPlanStatus | ''>('PENDING_REVIEW')
  const [applied, setApplied] = useState<InspectionPlanStatus | ''>('PENDING_REVIEW')
  const remote = useRemote(() => api.inspectionPlans(applied || undefined), [applied])
  // 计划类型 / 频次下拉来自字典（PLAN_TYPE / FREQUENCY 启用项；后端不校验这两组，AI 路径兼容）
  const dictRemote = useRemote(() => api.dicts({ enabled: true }), [])
  const dictOptions = (type: string) => (dictRemote.data ?? []).filter((d) => d.typeCode === type).map((d) => ({ value: d.itemValue, label: d.itemLabel }))

  const [detail, setDetail] = useState<InspectionPlan | null>(null)
  const [approveTarget, setApproveTarget] = useState<InspectionPlan | null>(null)
  const [approveComment, setApproveComment] = useState('')
  const [rejectTarget, setRejectTarget] = useState<InspectionPlan | null>(null)
  const [rejectReason, setRejectReason] = useState('')
  const [recordTarget, setRecordTarget] = useState<{ plan: InspectionPlan; status: InspectionPlanRecordStatus | null } | null>(null)
  const [acting, setActing] = useState(false)

  // ---- D1 手动新建计划（Phase D）：勾选巡检类型 → 每类型配置 分配人/覆盖区域 → 组装 tasks 草稿 ----
  const [createVisible, setCreateVisible] = useState(false)
  const [creating, setCreating] = useState(false)
  const [createPlanType, setCreatePlanType] = useState('MANUAL')
  const [createTitle, setCreateTitle] = useState('')
  const [createFactory, setCreateFactory] = useState('')
  const [createArea, setCreateArea] = useState('')
  const [createRiskBasis, setCreateRiskBasis] = useState('')
  // 巡检类型全集（同一套类型：/cps/inspection-types）
  const typesRemote = useRemote(() => api.inspectionTypes(), [])
  const [selectedTypes, setSelectedTypes] = useState<string[]>([])
  const [specs, setSpecs] = useState<Record<string, TaskSpec>>({})
  // 分区域类型的覆盖区域候选：typeCode → 区域列表（勾选类型时懒加载）
  const [areasByType, setAreasByType] = useState<Record<string, { value: string; label: string }[]>>({})
  const [areasLoading, setAreasLoading] = useState<string | null>(null)

  const allTypes = typesRemote.data ?? []
  const typeByCode = (code: string) => allTypes.find((t) => t.code === code)
  const isZoned = (code: string) => typeByCode(code)?.zoneFlag === 1

  async function loadAreasForType(typeCode: string) {
    if (areasByType[typeCode]) return
    setAreasLoading(typeCode)
    try {
      const result = await api.inspectionAreaPage({ typeCode, enabled: true, page: 1, pageSize: 100 })
      setAreasByType((prev) => ({
        ...prev,
        [typeCode]: result.records.map((a) => ({
          value: a.areaCode,
          label: `${a.areaCode} · ${a.areaName}${a.lineOrRoomType ? `（${a.lineOrRoomType}）` : ''}`,
        })),
      }))
    } catch {
      setAreasByType((prev) => ({ ...prev, [typeCode]: [] }))
    } finally {
      setAreasLoading(null)
    }
  }

  function openCreate() {
    setCreatePlanType('MANUAL'); setCreateTitle(''); setCreateFactory(''); setCreateArea(''); setCreateRiskBasis('')
    setSelectedTypes([]); setSpecs({})
    setCreateVisible(true)
    void typesRemote.refresh()
  }

  function toggleType(code: string, checked: boolean) {
    setSelectedTypes((previous) => {
      const next = checked ? [...previous, code] : previous.filter((t) => t !== code)
      // 按类型列表顺序保持稳定
      return allTypes.map((t) => t.code).filter((c) => next.includes(c))
    })
    setSpecs((previous) => ({ ...previous, [code]: previous[code] ?? emptySpec() }))
    if (checked && isZoned(code)) void loadAreasForType(code)
  }

  function updateSpec(code: string, patch: Partial<TaskSpec>) {
    setSpecs((previous) => ({ ...previous, [code]: { ...previous[code], ...patch } }))
  }

  async function submitCreate() {
    if (!createTitle.trim()) return Message.warning('计划标题必填')
    if (!createPlanType.trim()) return Message.warning('计划类型必填')
    if (selectedTypes.length === 0) return Message.warning('请至少勾选一个巡检类型')
    for (const code of selectedTypes) {
      const spec = specs[code] ?? emptySpec()
      if (!spec.targetEmpNo.trim()) {
        return Message.warning(`请为「${typeByCode(code)?.name ?? code}」指定分配人（工号）`)
      }
    }
    // Phase D 新草稿格式：tasks 列表 + inspectionTypeCode（后端建单按类型编码落任务）
    const tasks = selectedTypes.map((code) => {
      const spec = specs[code] ?? emptySpec()
      return {
        inspectionTypeCode: code,
        title: spec.title.trim() || `${createTitle.trim()} - ${typeByCode(code)?.name ?? code}`,
        targetEmpNo: spec.targetEmpNo.trim(),
        ...(spec.targetEmpName.trim() ? { targetEmpName: spec.targetEmpName.trim() } : {}),
        ...(spec.frequency ? { frequency: spec.frequency } : {}),
        ...(spec.acceptanceCriteria.trim() ? { acceptanceCriteria: spec.acceptanceCriteria.trim() } : {}),
        ...(spec.evidenceRequirement.trim() ? { evidenceRequirement: spec.evidenceRequirement.trim() } : {}),
        ...(isZoned(code) && spec.areaCodes.length ? { coverageAreaCodes: spec.areaCodes.join(',') } : {}),
      }
    })
    setCreating(true)
    try {
      const plan = await api.createInspectionPlan({
        planType: createPlanType.trim(),
        title: createTitle.trim(),
        targetFactory: createFactory.trim() || undefined,
        targetArea: createArea.trim() || undefined,
        riskBasis: createRiskBasis.trim() || undefined,
        draftContentJson: JSON.stringify({ tasks }),
        draftBy: getAdminOperator()?.empNo,
      })
      Message.success(`已创建待审核计划 #${plan.id}，批准后按勾选类型建单并分配`)
      setCreateVisible(false)
      setStatus('PENDING_REVIEW'); setApplied('PENDING_REVIEW')
      await remote.refresh()
    } catch (error) { Message.error(errorMessage(error)) } finally { setCreating(false) }
  }

  async function submitApprove() {
    if (!approveTarget) return
    setActing(true)
    try {
      const result = await api.approveInspectionPlan(approveTarget.id, approveTarget.lockVersion, approveComment.trim() || undefined)
      Message.success(`已批准并建单：新增任务 ${result.createdTasks.length} 条`)
      setApproveTarget(null); setApproveComment('')
      await remote.refresh()
    } catch (error) { Message.error(errorMessage(error)) } finally { setActing(false) }
  }

  async function submitReject() {
    if (!rejectTarget) return
    if (!rejectReason.trim()) return Message.warning('拒绝原因必填')
    setActing(true)
    try {
      await api.rejectInspectionPlan(rejectTarget.id, rejectReason.trim(), rejectTarget.lockVersion)
      Message.success('已拒绝该计划')
      setRejectTarget(null); setRejectReason('')
      await remote.refresh()
    } catch (error) { Message.error(errorMessage(error)) } finally { setActing(false) }
  }

  async function openRecord(plan: InspectionPlan) {
    setRecordTarget({ plan, status: null })
    try {
      const status = await api.inspectionPlanRecordStatus(plan.id)
      setRecordTarget({ plan, status })
    } catch (error) { Message.error(errorMessage(error)) }
  }

  async function rebuild(plan: InspectionPlan) {
    setActing(true)
    try {
      const result = await api.rebuildInspectionPlanTasks(plan.id)
      Message.success(`补建完成：本次新增 ${result.createdTasks.length} 条任务`)
      setRecordTarget({ plan, status: result.recordStatus })
      await remote.refresh()
    } catch (error) { Message.error(errorMessage(error)) } finally { setActing(false) }
  }

  const taskColumns: TableColumnProps<InspectionPlanTask>[] = [
    { title: 'ID', dataIndex: 'id', width: 70 },
    { title: '类型', width: 120, render: (_, t) => <Tag color="arcoblue">{typeByCode(t.taskType)?.name ?? taskTypeLabel[t.taskType] ?? t.taskType}</Tag> },
    { title: '任务', width: 320, render: (_, t) => (
      <>
        <Tooltip
          content={t.title || '-'}
          disabled={!t.title}
        >
          <Typography.Text
            ellipsis
            style={{ display: 'inline-block', maxWidth: 240, verticalAlign: 'bottom' }}
          >
            {t.title || '-'}
          </Typography.Text>
        </Tooltip>
        {t.referenceIssueId ? <Typography.Text type="secondary">（问题 #{t.referenceIssueId}）</Typography.Text> : null}
      </>
    ) },
    { title: '分配给', width: 140, render: (_, t) => t.targetEmpName ? `${t.targetEmpName}（${t.targetEmpNo}）` : t.targetEmpNo || <Typography.Text type="secondary">未分配</Typography.Text> },
    {
      title: '覆盖区域', width: 180, dataIndex: 'coverageAreaCodes',
      render: (v: string | null, t) => {
        if (!isZoned(t.taskType) && !v) return <Typography.Text type="secondary">整体执行</Typography.Text>
        const codes = (v ?? '').split(',').filter(Boolean)
        return codes.length
          ? <Tooltip content={codes.join('、')}><Tag color="cyan">{codes.length} 个区域</Tag></Tooltip>
          : <Typography.Text type="secondary">全部区域</Typography.Text>
      },
    },
    { title: '计划时间', width: 170, dataIndex: 'scheduledAt', render: (v: string) => v || '-' },
    { title: '频次', width: 90, dataIndex: 'frequency', render: (v: string) => v || '-' },
    { title: '任务状态', width: 110, dataIndex: 'taskStatus', render: (v: string) => v || '-' },
  ]

  const columns: TableColumnProps<InspectionPlan>[] = [
    { title: 'ID', dataIndex: 'id', width: 70 },
    { title: '计划', render: (_, p) => <>{p.title || '-'} <Typography.Text type="secondary">（{p.planType || '-'}）</Typography.Text></> },
    { title: '目标范围', width: 180, render: (_, p) => [p.targetFactory, p.targetArea].filter(Boolean).join(' / ') || '-' },
    { title: '状态', width: 110, render: (_, p) => <Badge color={statusColor[p.status] ?? 'gray'} text={statusLabel[p.status] ?? p.status} /> },
    { title: '任务数', width: 80, render: (_, p) => p.tasks?.length ?? 0 },
    { title: '拟定人', width: 100, dataIndex: 'draftBy', render: (v: string) => v || '-' },
    { title: '创建时间', width: 170, dataIndex: 'createdAt' },
    {
      title: '操作', width: 250, align: 'right',
      render: (_, p) => <div style={{ display: 'inline-flex', gap: 4, flexWrap: 'wrap', justifyContent: 'flex-end' }}>
        <Button type="text" size="small" onClick={() => setDetail(p)}>详情</Button>
        {p.status === 'PENDING_REVIEW' ? <>
          <Button type="text" size="small" status="success" onClick={() => { setApproveTarget(p); setApproveComment('') }}>批准</Button>
          <Button type="text" size="small" status="danger" onClick={() => { setRejectTarget(p); setRejectReason('') }}>拒绝</Button>
        </> : null}
        {p.status === 'APPROVED' ? <Button type="text" size="small" onClick={() => void openRecord(p)}>建单状态</Button> : null}
      </div>,
    },
  ]

  const rows = remote.data ?? []

  return <>
    <PageHead title="巡检计划管理" detail="审核 AI 生成或手动新建计划；批准后按勾选类型建单" />
    <div className="page-body">
      <Card className="filter-card">
        <Form layout="inline" className="filter-bar" onSubmit={() => setApplied(status)}>
          <Button type="primary" htmlType="button" icon={<IconPlus />} onClick={openCreate}>新建计划</Button>
          <Select allowClear value={status || undefined} placeholder="全部状态" style={{ width: 180 }} onChange={(value) => setStatus((value ?? '') as InspectionPlanStatus | '')} options={Object.entries(statusLabel).map(([value, label]) => ({ value, label }))} />
          <Button type="primary" htmlType="submit" icon={<IconFilter />}>筛选</Button>
          <Button icon={<IconRefresh />} onClick={() => void remote.refresh()}>刷新</Button>
        </Form>
      </Card>
      <StatePanel {...remote}>{remote.data && <Card className="table-card">
        <Table rowKey="id" columns={columns} data={rows} pagination={false} scroll={{ x: 1200 }}
          expandedRowRender={(p) => <Table rowKey="id" size="small" columns={taskColumns} data={p.tasks ?? []} pagination={false} />} />
      </Card>}</StatePanel>
    </div>

    <Drawer title={detail ? `计划 #${detail.id} · ${detail.title || ''}` : ''} visible={!!detail} width={720} footer={null} onCancel={() => setDetail(null)} bodyStyle={{ padding: '20px 24px 32px' }}>
      {detail && <>
        <Descriptions column={2} size="small" style={{ marginBottom: 16 }}
          data={[
            { label: '状态', value: <Badge color={statusColor[detail.status] ?? 'gray'} text={statusLabel[detail.status] ?? detail.status} /> },
            { label: '计划类型', value: detail.planType || '-' },
            { label: '来源周报', value: detail.reportId || '-' },
            { label: '来源运行', value: detail.sourceRunId || '-' },
            { label: '目标工厂', value: detail.targetFactory || '-' },
            { label: '目标区域', value: detail.targetArea || '-' },
            { label: '拟定人', value: detail.draftBy || '-' },
            { label: '配置版本', value: `v${detail.configVersion ?? '-'} (lock ${detail.lockVersion ?? '-'})` },
            { label: '审批人', value: detail.approverName ? `${detail.approverName}（${detail.approver || '-'}）` : '-' },
            { label: '审批时间', value: detail.approvedAt || '-' },
            { label: '风险依据', value: detail.riskBasis || '-' },
            ...(detail.rejectReason ? [{ label: '拒绝原因', value: detail.rejectReason }] : []),
          ] as never} />
        <Typography.Title heading={6} style={{ marginTop: 8 }}>任务明细（{detail.tasks?.length ?? 0}）</Typography.Title>
        <Table rowKey="id" size="small" columns={taskColumns} data={detail.tasks ?? []} pagination={false} style={{ marginBottom: 16 }} />
        <Typography.Title heading={6}>草稿内容</Typography.Title>
        <pre style={{ background: '#f6f8f7', padding: 12, borderRadius: 6, maxHeight: 320, overflow: 'auto', fontSize: 12 }}>{prettyJson(detail.draftContentJson)}</pre>
      </>}
    </Drawer>

    <Modal title={approveTarget ? `批准计划 #${approveTarget.id}` : ''} visible={!!approveTarget} onCancel={() => setApproveTarget(null)} onOk={() => void submitApprove()} confirmLoading={acting} okText="批准并建单" cancelText="取消" style={{ width: 640 }}>
      <Typography.Paragraph style={{ marginBottom: 4 }}>{approveTarget?.title}：批准后将按勾选的任务类型自动建单。</Typography.Paragraph>
      <Form layout="vertical"><Form.Item label="审批意见"><Input.TextArea value={approveComment} onChange={setApproveComment} /></Form.Item></Form>
    </Modal>

    <Modal title={rejectTarget ? `拒绝计划 #${rejectTarget.id}` : ''} visible={!!rejectTarget} onCancel={() => setRejectTarget(null)} onOk={() => void submitReject()} confirmLoading={acting} okText="确认拒绝" cancelText="取消" style={{ width: 640 }}>
      <Typography.Paragraph style={{ marginBottom: 4 }}>{rejectTarget?.title}</Typography.Paragraph>
      <Form layout="vertical"><Form.Item label="拒绝原因" required><Input.TextArea value={rejectReason} onChange={setRejectReason} /></Form.Item></Form>
    </Modal>

    <Modal title={recordTarget ? `建单状态 · 计划 #${recordTarget.plan.id}` : ''} visible={!!recordTarget} footer={null} onCancel={() => setRecordTarget(null)} style={{ width: 760 }}>
      {recordTarget?.status ? <>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
          <Typography.Text>汇总：成功 {recordTarget.status.summary?.typesCreated ?? 0} / 失败 {recordTarget.status.summary?.typesFailed ?? 0} / 缺失 {recordTarget.status.summary?.typesMissing ?? 0}</Typography.Text>
          <Button size="small" type="primary" loading={acting} onClick={() => void rebuild(recordTarget.plan)}>补建失败任务</Button>
        </div>
        <Table rowKey="taskType" size="small" pagination={false} data={recordTarget.status.perType} columns={[
          { title: '任务类型', dataIndex: 'taskType', width: 110, render: (v: string) => taskTypeLabel[v] ?? v },
          { title: '建单范围', width: 88, render: (_, r) => r.requested === false ? <Tag color="gray">未勾选</Tag> : <Tag color="green">勾选</Tag> },
          { title: '任务', width: 100, render: (_, r) => r.taskExists ? `#${r.taskId}` : '未创建' },
          { title: '任务状态', width: 96, dataIndex: 'taskStatus', render: (v: string) => v || '-' },
          { title: '建单结果', width: 120, render: (_, r) => r.lastBuildResult ? <Tag color={r.lastBuildResult === 'CREATE_FAILED' ? 'red' : 'green'}>{buildResultLabel[r.lastBuildResult] ?? r.lastBuildResult}</Tag> : '从未尝试' },
          { title: '失败原因', render: (_, r) => r.lastBuildErrorMsg || '-' },
        ]} />
      </> : <Typography.Text type="secondary">正在加载建单状态…</Typography.Text>}
    </Modal>

    <Modal title="新建巡检计划（创建后进入待审核）" visible={createVisible} onCancel={() => setCreateVisible(false)} onOk={() => void submitCreate()} confirmLoading={creating} okText="创建计划" cancelText="取消" style={{ width: 760 }}>
      <Form layout="vertical" className="form-grid">
        <Form.Item label="计划标题" required><Input value={createTitle} onChange={setCreateTitle} placeholder="如 11 月配电房专项点检" /></Form.Item>
        <Form.Item label="计划类型" required><Select allowCreate value={createPlanType} onChange={setCreatePlanType} options={dictOptions('PLAN_TYPE')} placeholder="选择或输入类型编码" /></Form.Item>
        <Form.Item label="目标工厂"><Input value={createFactory} onChange={setCreateFactory} /></Form.Item>
        <Form.Item label="目标区域"><Input value={createArea} onChange={setCreateArea} /></Form.Item>
        <Form.Item label="风险依据" className="span-2"><Input value={createRiskBasis} onChange={setCreateRiskBasis} placeholder="如 近期周报高风险项/消防检查要求" /></Form.Item>
      </Form>
      <Typography.Title heading={6} style={{ marginTop: 8 }}>勾选巡检类型（与 Android 巡检任务同一套，可在「巡检类型」页维护）</Typography.Title>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 6, marginBottom: 4 }}>
        {allTypes.filter((t) => t.enabled).map((t) => (
          <div key={t.code} style={{ border: '1px solid var(--color-border-2)', borderRadius: 6, padding: '6px 12px', background: selectedTypes.includes(t.code) ? 'var(--color-primary-light-1)' : undefined }}>
            <Checkbox checked={selectedTypes.includes(t.code)} onChange={(c) => toggleType(t.code, c)}>
              <Typography.Text bold>{t.name}</Typography.Text>
              <Tag size="small" style={{ marginLeft: 8 }} color={t.zoneFlag === 1 ? 'green' : 'orange'}>{t.zoneFlag === 1 ? '分区域' : '不分区域'}</Tag>
              <Tag size="small" style={{ marginLeft: 4 }}>{t.code}</Tag>
            </Checkbox>
            <br />
            <Typography.Text type="secondary" style={{ fontSize: 12, marginLeft: 24 }}>{t.content || t.description || '（无说明）'}</Typography.Text>
          </div>
        ))}
        {!allTypes.some((t) => t.enabled) && <Typography.Text type="secondary">暂无启用的巡检类型，请先到「巡检类型」页创建</Typography.Text>}
      </div>
      {selectedTypes.map((code) => {
        const spec = specs[code] ?? emptySpec()
        const zoned = isZoned(code)
        return <div key={code} style={{ border: '1px solid var(--color-border-2)', borderRadius: 6, padding: '4px 12px 12px', marginTop: 12 }}>
          <Typography.Text bold>
            {typeByCode(code)?.name ?? code} · 任务明细
            {zoned ? <Tag size="small" color="green" style={{ marginLeft: 8 }}>按区域执行</Tag> : <Tag size="small" color="orange" style={{ marginLeft: 8 }}>整体执行</Tag>}
          </Typography.Text>
          <Form layout="vertical" className="form-grid" style={{ marginTop: 8 }}>
            <Form.Item label="任务标题"><Input value={spec.title} onChange={(v) => updateSpec(code, { title: v })} placeholder={`留空 = ${createTitle || '计划标题'} - ${typeByCode(code)?.name ?? code}`} /></Form.Item>
            <Form.Item label="频次"><Select allowClear value={spec.frequency || undefined} onChange={(v) => updateSpec(code, { frequency: v ?? '' })} options={dictOptions('FREQUENCY')} placeholder="不填" /></Form.Item>
            <Form.Item label="分配人工号" required><Input value={spec.targetEmpNo} onChange={(v) => updateSpec(code, { targetEmpNo: v })} placeholder="任务分配给谁（工号）" /></Form.Item>
            <Form.Item label="分配人姓名"><Input value={spec.targetEmpName} onChange={(v) => updateSpec(code, { targetEmpName: v })} placeholder="选填，默认回显工号" /></Form.Item>
            <Form.Item label="验收标准" className="span-2"><Input.TextArea value={spec.acceptanceCriteria} onChange={(v) => updateSpec(code, { acceptanceCriteria: v })} placeholder="如 全部点检项合格、异常拍照留证" /></Form.Item>
            <Form.Item label="证据要求" className="span-2"><Input.TextArea value={spec.evidenceRequirement} onChange={(v) => updateSpec(code, { evidenceRequirement: v })} placeholder="如 每区域至少 1 张全景照" /></Form.Item>
            {zoned ? <Form.Item label="覆盖区域" className="span-2">
              <Select mode="multiple" allowClear showSearch value={spec.areaCodes} onChange={(v) => updateSpec(code, { areaCodes: v })}
                placeholder="不选 = 该类型全部启用区域"
                options={areasByType[code] ?? []}
                notFoundContent={areasLoading === code ? '正在加载区域…' : '该类型暂无启用区域，请先到「巡检区域与点检项」维护'} />
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>区域隶属本巡检类型；执行人在 Android 端按覆盖区域逐个执行。</Typography.Text>
            </Form.Item> : null}
          </Form>
        </div>
      })}
    </Modal>
  </>
}
