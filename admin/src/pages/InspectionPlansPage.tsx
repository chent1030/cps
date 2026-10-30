/**
 * 巡检计划管理页（D2 审核工作台 + D4 建单状态 + D1 手动新建 + 批量导入）。
 * - 状态筛选（默认待审核）+ 计划列表（展开行看任务）
 * - 手动新建计划：勾选巡检任务类型 + 每类明细 + 覆盖区域多选；草稿 JSON 直传不走 AI
 * - 批量导入：下载 xlsx 模板 → 解析预览（校验必填）→ 按「计划标题」合并成多个计划 → 逐条提交结果
 * - 详情抽屉：计划字段 + 任务明细（计划类型已按产品要求移除，草稿 JSON 不再展示）
 * - 批准 / 拒绝（PENDING_REVIEW）；建单状态（含建单范围列）+ 补建失败任务（APPROVED）
 */
import { Badge, Button, Card, Checkbox, Descriptions, Drawer, Form, Input, Message, Modal, Select, Space, Table, Tag, Tooltip, Typography, Upload } from '@arco-design/web-react'
import type { TableColumnProps } from '@arco-design/web-react'
import { IconDownload, IconFilter, IconPlus, IconRefresh, IconUpload } from '@arco-design/web-react/icon'
import { useState } from 'react'
import * as XLSX from 'xlsx'
import { api, getAdminOperator } from '../api'
import { useRemote } from '../hooks/useRemote'
import { PageHead, StatePanel } from '../components/PageParts'
import { errorMessage } from '../lib/presentation'
import { StructuredValue } from '../components/StructuredValue'
import type { InspectionPlan, InspectionPlanRecordStatus, InspectionPlanStatus, InspectionPlanTask } from '../types'

const statusLabel: Record<string, string> = { PENDING_REVIEW: '待审核', APPROVED: '已批准', REJECTED: '已拒绝' }
const statusColor: Record<string, string> = { PENDING_REVIEW: 'orange', APPROVED: 'green', REJECTED: 'red' }
/** 历史枚举任务类型的展示名（Phase D 后新任务 taskType=巡检类型编码，从类型表取名） */
const taskTypeLabel: Record<string, string> = { INSPECT_RECTIFY: '整改复查', INSPECT_PATROL: '区域巡检', INSPECT_CHECK: '辅房点检' }
const buildResultLabel: Record<string, string> = { CREATED: '已建单', SKIPPED_EXISTING: '已存在跳过', CREATE_FAILED: '建单失败' }
interface TaskSpec { title: string; targetEmpNo: string; targetEmpName: string; frequency: string; acceptanceCriteria: string; evidenceRequirement: string; areaCodes: string[] }
const emptySpec = (): TaskSpec => ({ title: '', targetEmpNo: '', targetEmpName: '', frequency: '', acceptanceCriteria: '', evidenceRequirement: '', areaCodes: [] })

// ============================================================
// 批量导入（xlsx）
// 模板一行 = 一个巡检任务；同一「计划标题」的多行合并成一个计划的多个 tasks。
// ============================================================
type ImportField =
  | 'planTitle' | 'targetFactory' | 'targetArea' | 'riskBasis'
  | 'inspectionTypeCode' | 'taskTitle' | 'targetEmpNo' | 'targetEmpName'
  | 'frequency' | 'acceptanceCriteria' | 'evidenceRequirement' | 'coverageAreaCodes'

interface ImportRow {
  rowNo: number
  planTitle: string
  targetFactory: string
  targetArea: string
  riskBasis: string
  inspectionTypeCode: string
  taskTitle: string
  targetEmpNo: string
  targetEmpName: string
  frequency: string
  acceptanceCriteria: string
  evidenceRequirement: string
  coverageAreaCodes: string
  errors: string[]
}

interface ImportResult {
  planTitle: string
  ok: boolean
  /** true = 校验未通过而被跳过的数据行（不是提交失败的计划）。 */
  skipped?: boolean
  message: string
}

const IMPORT_COLUMNS: { field: ImportField; header: string; width: number }[] = [
  { field: 'planTitle', header: '计划标题*', width: 180 },
  { field: 'targetFactory', header: '目标工厂', width: 100 },
  { field: 'targetArea', header: '目标区域', width: 120 },
  { field: 'riskBasis', header: '风险依据', width: 160 },
  { field: 'inspectionTypeCode', header: '巡检类型编码*', width: 140 },
  { field: 'taskTitle', header: '任务标题', width: 180 },
  { field: 'targetEmpNo', header: '责任人工号*', width: 120 },
  { field: 'targetEmpName', header: '责任人姓名', width: 110 },
  { field: 'frequency', header: '频次(WEEKLY/MONTHLY/DAILY)', width: 160 },
  { field: 'acceptanceCriteria', header: '验收标准', width: 180 },
  { field: 'evidenceRequirement', header: '证据要求', width: 180 },
  { field: 'coverageAreaCodes', header: '覆盖区域编码(逗号分隔)', width: 180 },
]

/** 表头归一化：去掉 *、空格和括号说明，兼容用户手改过的模板。 */
function normalizeImportHeader(header: string): string {
  return header.replace(/\s+/g, '').replace(/\*/g, '').replace(/[（(].*$/, '').toLowerCase()
}

const IMPORT_HEADER_FIELD: Record<string, ImportField> = Object.fromEntries(
  IMPORT_COLUMNS.map((column) => [normalizeImportHeader(column.header), column.field]),
)

const IMPORT_FREQUENCY_ALIAS: Record<string, string> = { 每日: 'DAILY', 每天: 'DAILY', 日: 'DAILY', 每周: 'WEEKLY', 每星期: 'WEEKLY', 周: 'WEEKLY', 每月: 'MONTHLY', 月: 'MONTHLY' }
const IMPORT_FREQUENCIES = ['DAILY', 'WEEKLY', 'MONTHLY']

/** 覆盖区域编码支持中文逗号/顿号分隔，统一成后端约定的英文逗号串。 */
function normalizeAreaCodes(value: string): string {
  return value.split(/[,，、;；\s]+/).map((part) => part.trim()).filter(Boolean).join(',')
}

/** Excel 记录行 → 导入行（含必填校验；行号对应 Excel 可见行号，表头占第 1 行）。 */
function buildImportRows(records: Record<string, unknown>[]): ImportRow[] {
  const rows: ImportRow[] = []
  records.forEach((record, index) => {
    const cells: Partial<Record<ImportField, string>> = {}
    let hasValue = false
    Object.entries(record).forEach(([rawHeader, rawValue]) => {
      if (rawHeader.startsWith('__EMPTY')) return
      const field = IMPORT_HEADER_FIELD[normalizeImportHeader(rawHeader)]
      if (!field) return
      const text = rawValue == null ? '' : String(rawValue).trim()
      if (text) hasValue = true
      cells[field] = text
    })
    if (!hasValue) return
    const rawFrequency = cells.frequency ?? ''
    const frequency = IMPORT_FREQUENCY_ALIAS[rawFrequency] ?? rawFrequency.toUpperCase()
    const row: ImportRow = {
      rowNo: index + 2,
      planTitle: cells.planTitle ?? '',
      targetFactory: cells.targetFactory ?? '',
      targetArea: cells.targetArea ?? '',
      riskBasis: cells.riskBasis ?? '',
      inspectionTypeCode: cells.inspectionTypeCode ?? '',
      taskTitle: cells.taskTitle ?? '',
      targetEmpNo: cells.targetEmpNo ?? '',
      targetEmpName: cells.targetEmpName ?? '',
      frequency,
      acceptanceCriteria: cells.acceptanceCriteria ?? '',
      evidenceRequirement: cells.evidenceRequirement ?? '',
      coverageAreaCodes: cells.coverageAreaCodes ?? '',
      errors: [],
    }
    if (!row.planTitle) row.errors.push('计划标题必填')
    if (!row.inspectionTypeCode) row.errors.push('巡检类型编码必填')
    if (!row.targetEmpNo) row.errors.push('责任人工号必填')
    if (row.frequency && !IMPORT_FREQUENCIES.includes(row.frequency)) row.errors.push('频次只能是 DAILY / WEEKLY / MONTHLY（或每日/每周/每月）')
    rows.push(row)
  })
  return rows
}

export function InspectionPlansPage() {
  const [status, setStatus] = useState<InspectionPlanStatus | ''>('PENDING_REVIEW')
  const [applied, setApplied] = useState<InspectionPlanStatus | ''>('PENDING_REVIEW')
  const remote = useRemote(() => api.inspectionPlans(applied || undefined), [applied])
  // 频次下拉来自字典（FREQUENCY 启用项）；计划类型已按产品要求从界面移除（W1）
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

  // ---- 批量导入：解析后的行 + 逐计划提交结果 ----
  const [importVisible, setImportVisible] = useState(false)
  const [importing, setImporting] = useState(false)
  const [importFileName, setImportFileName] = useState('')
  const [importRows, setImportRows] = useState<ImportRow[]>([])
  const [importResults, setImportResults] = useState<ImportResult[]>([])

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
    setCreateTitle(''); setCreateFactory(''); setCreateArea(''); setCreateRiskBasis('')
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
        // 计划类型已从界面移除（产品判定为冗余字段），后端字段仍必填 → 固定 MANUAL。
        planType: 'MANUAL',
        title: createTitle.trim(),
        targetFactory: createFactory.trim() || undefined,
        targetArea: createArea.trim() || undefined,
        riskBasis: createRiskBasis.trim() || undefined,
        draftContentJson: JSON.stringify({ tasks }),
        draftBy: getAdminOperator()?.empNo,
      })
      Message.success(`已创建待审核计划 #${plan.id}，批准后按勾选类型建单并分配`)
      setCreateVisible(false)
      // 切到待审核时依赖变化会自动 refresh；已在该筛选下才手动刷新，避免用旧筛选条件重复请求。
      if (applied === 'PENDING_REVIEW') {
        await remote.refresh()
      } else {
        setStatus('PENDING_REVIEW'); setApplied('PENDING_REVIEW')
      }
    } catch (error) { Message.error(errorMessage(error)) } finally { setCreating(false) }
  }

  // ---- 批量导入：模板 → 解析预览 → 按计划标题分组提交 ----
  function downloadImportTemplate() {
    const headerSheet = XLSX.utils.aoa_to_sheet([IMPORT_COLUMNS.map((column) => column.header)])
    headerSheet['!cols'] = IMPORT_COLUMNS.map((column) => ({ wch: Math.max(12, Math.round(column.width / 8)) }))
    const typeCodes = allTypes.map((type) => type.code).slice(0, 4).join(' / ') || 'ROOM_CHECK / DEVICE_CHECK'
    const helpSheet = XLSX.utils.aoa_to_sheet([
      ['字段', '填写说明'],
      ['计划标题*', '必填。同一计划标题的多行会合并成一个计划的多个巡检任务'],
      ['目标工厂', '选填。同一计划内以第一行为准'],
      ['目标区域', '选填。同一计划内以第一行为准'],
      ['风险依据', '选填。同一计划内以第一行为准'],
      ['巡检类型编码*', `必填。与「巡检类型」页一致，如 ${typeCodes}`],
      ['任务标题', '选填。留空自动生成「计划标题 - 巡检类型名称」'],
      ['责任人工号*', '必填。任务执行人工号'],
      ['责任人姓名', '选填。留空按工号回显'],
      ['频次', '选填：DAILY / WEEKLY / MONTHLY，也接受 每日 / 每周 / 每月'],
      ['验收标准', '选填'],
      ['证据要求', '选填'],
      ['覆盖区域编码', '选填。多个用逗号分隔，如 JX-01,JX-02；仅分区域类型需要'],
      ['说明', '请在「巡检计划导入」sheet 第 2 行起填写，不要修改表头文字'],
    ])
    helpSheet['!cols'] = [{ wch: 18 }, { wch: 70 }]
    const workbook = XLSX.utils.book_new()
    XLSX.utils.book_append_sheet(workbook, headerSheet, '巡检计划导入')
    XLSX.utils.book_append_sheet(workbook, helpSheet, '填写说明')
    XLSX.writeFile(workbook, '巡检计划导入模板.xlsx')
  }

  async function handleImportFile(file: File): Promise<boolean> {
    try {
      const workbook = XLSX.read(await file.arrayBuffer(), { type: 'array' })
      const firstSheet = workbook.Sheets[workbook.SheetNames[0]]
      if (!firstSheet) { Message.error('Excel 中没有可读取的工作表'); return false }
      const records = XLSX.utils.sheet_to_json<Record<string, unknown>>(firstSheet, { defval: '' })
      const parsed = buildImportRows(records)
      if (!parsed.length) { Message.error('未解析到数据行，请从模板第 2 行开始填写'); return false }
      const knownCodes = new Set(allTypes.map((type) => type.code))
      // 类型列表加载失败（为空）时跳过编码校验，避免把整张表误判为无效
      const rows = knownCodes.size
        ? parsed.map((row) => row.inspectionTypeCode && !knownCodes.has(row.inspectionTypeCode)
          ? { ...row, errors: [...row.errors, `巡检类型编码「${row.inspectionTypeCode}」不存在`] }
          : row)
        : parsed
      setImportRows(rows)
      setImportResults([])
      setImportFileName(file.name)
      setImportVisible(true)
      if (!knownCodes.size) void typesRemote.refresh()
    } catch (error) {
      Message.error(`Excel 解析失败：${errorMessage(error)}`)
    }
    return false
  }

  async function submitImport() {
    const validRows = importRows.filter((row) => row.errors.length === 0)
    if (!validRows.length) return Message.warning('没有可导入的有效数据行')
    // Map 保留首次出现顺序：同一「计划标题」的多行 → 一个计划的多个 tasks
    const groups = new Map<string, ImportRow[]>()
    validRows.forEach((row) => {
      const list = groups.get(row.planTitle) ?? []
      list.push(row)
      groups.set(row.planTitle, list)
    })
    const results: ImportResult[] = importRows
      .filter((row) => row.errors.length > 0)
      .map((row) => ({ planTitle: `第 ${row.rowNo} 行${row.planTitle ? ` · ${row.planTitle}` : ''}`, ok: false, skipped: true, message: `未导入：${row.errors.join('；')}` }))
    setImporting(true)
    setImportResults([...results])
    try {
      for (const [planTitle, rows] of groups) {
        const first = rows[0]
        const tasks = rows.map((row) => {
          const areaCodes = normalizeAreaCodes(row.coverageAreaCodes)
          return {
            inspectionTypeCode: row.inspectionTypeCode,
            title: row.taskTitle || `${planTitle} - ${typeByCode(row.inspectionTypeCode)?.name ?? row.inspectionTypeCode}`,
            targetEmpNo: row.targetEmpNo,
            ...(row.targetEmpName ? { targetEmpName: row.targetEmpName } : {}),
            ...(row.frequency ? { frequency: row.frequency } : {}),
            ...(row.acceptanceCriteria ? { acceptanceCriteria: row.acceptanceCriteria } : {}),
            ...(row.evidenceRequirement ? { evidenceRequirement: row.evidenceRequirement } : {}),
            ...(areaCodes ? { coverageAreaCodes: areaCodes } : {}),
          }
        })
        try {
          const plan = await api.createInspectionPlan({
            planType: 'MANUAL',
            title: planTitle,
            targetFactory: first.targetFactory || undefined,
            targetArea: first.targetArea || undefined,
            riskBasis: first.riskBasis || undefined,
            draftContentJson: JSON.stringify({ tasks }),
            draftBy: 'web-import',
          })
          results.push({ planTitle, ok: true, message: `已创建待审核计划 #${plan.id}（${tasks.length} 个巡检任务）` })
        } catch (error) {
          results.push({ planTitle, ok: false, message: errorMessage(error) })
        }
        setImportResults([...results])
      }
    } finally {
      setImporting(false)
    }
    const succeeded = results.filter((result) => result.ok).length
    const failedPlans = results.filter((result) => !result.ok && !result.skipped).length
    if (succeeded > 0) {
      Message.success(`批量导入完成：成功 ${succeeded} 个计划${failedPlans ? `，失败 ${failedPlans} 个计划` : ''}${importErrorCount ? `，跳过 ${importErrorCount} 行` : ''}`)
      if (applied === 'PENDING_REVIEW') {
        await remote.refresh()
      } else {
        setStatus('PENDING_REVIEW'); setApplied('PENDING_REVIEW')
      }
    } else {
      Message.error('批量导入未成功，请查看失败原因')
    }
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
          content={<StructuredValue value={t.title} textRows={4} />}
          disabled={!t.title}
        >
          <div><StructuredValue value={t.title} textRows={2} /></div>
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
    { title: '计划', render: (_, p) => <StructuredValue value={p.title} textRows={1} /> },
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
  const importValidRows = importRows.filter((row) => row.errors.length === 0)
  const importPlanCount = new Set(importValidRows.map((row) => row.planTitle)).size
  const importErrorCount = importRows.length - importValidRows.length

  return <>
    <PageHead title="巡检计划管理" detail="审核 AI 生成或手动新建计划；批准后按勾选类型建单" />
    <div className="page-body">
      <Card className="filter-card">
        <Form layout="inline" className="filter-bar" onSubmit={() => setApplied(status)}>
          <Button type="primary" htmlType="button" icon={<IconPlus />} onClick={openCreate}>新建计划</Button>
          <Button icon={<IconDownload />} onClick={downloadImportTemplate}>下载模板</Button>
          <Upload accept=".xlsx,.xls" showUploadList={false} beforeUpload={handleImportFile}>
            <Button icon={<IconUpload />}>批量导入</Button>
          </Upload>
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
            { label: '来源周报', value: detail.reportId || '-' },
            { label: '来源运行', value: detail.sourceRunId || '-' },
            { label: '目标工厂', value: detail.targetFactory || '-' },
            { label: '目标区域', value: detail.targetArea || '-' },
            { label: '拟定人', value: detail.draftBy || '-' },
            { label: '配置版本', value: `v${detail.configVersion ?? '-'} (lock ${detail.lockVersion ?? '-'})` },
            { label: '审批人', value: detail.approverName ? `${detail.approverName}（${detail.approver || '-'}）` : '-' },
            { label: '审批时间', value: detail.approvedAt || '-' },
            { label: '风险依据', value: <StructuredValue value={detail.riskBasis} textRows={4} /> },
            ...(detail.rejectReason ? [{ label: '拒绝原因', value: <StructuredValue value={detail.rejectReason} textRows={4} /> }] : []),
          ] as never} />
        <Typography.Title heading={6} style={{ marginTop: 8 }}>任务明细（{detail.tasks?.length ?? 0}）</Typography.Title>
        <Table rowKey="id" size="small" columns={taskColumns} data={detail.tasks ?? []} pagination={false} style={{ marginBottom: 16 }} />
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
          { title: '失败原因', render: (_, r) => <StructuredValue value={r.lastBuildErrorMsg} textRows={4} /> },
        ]} />
      </> : <Typography.Text type="secondary">正在加载建单状态…</Typography.Text>}
    </Modal>

    <Modal title="新建巡检计划（创建后进入待审核）" visible={createVisible} onCancel={() => setCreateVisible(false)} onOk={() => void submitCreate()} confirmLoading={creating} okText="创建计划" cancelText="取消" style={{ width: 760 }}>
      <Form layout="vertical" className="form-grid">
        <Form.Item label="计划标题" required><Input value={createTitle} onChange={setCreateTitle} placeholder="如 11 月配电房专项点检" /></Form.Item>
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

    <Modal title="批量导入巡检计划" visible={importVisible} footer={null} onCancel={() => { if (!importing) setImportVisible(false) }} style={{ width: 1040 }}>
      <div style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 12, marginBottom: 12 }}>
        <Typography.Text bold>{importFileName || '未选择文件'}</Typography.Text>
        <Typography.Text type="secondary">
          共 {importRows.length} 行 / {importPlanCount} 个计划 / 有效 {importValidRows.length} 行 / 有误 {importErrorCount} 行
        </Typography.Text>
        {!allTypes.length && <Typography.Text type="warning">巡检类型列表未加载，巡检类型编码校验已跳过</Typography.Text>}
      </div>
      {!importResults.length ? <Table rowKey="rowNo" size="small" pagination={false} scroll={{ x: 1560 }} data={importRows} columns={[
        { title: '行号', dataIndex: 'rowNo', width: 60 },
        { title: '计划标题', width: 200, render: (_, row) => <>
          <Typography.Text bold>{row.planTitle || '-'}</Typography.Text>
          {[row.targetFactory, row.targetArea].filter(Boolean).length ? <><br /><Typography.Text type="secondary" style={{ fontSize: 12 }}>{[row.targetFactory, row.targetArea].filter(Boolean).join(' / ')}</Typography.Text></> : null}
        </> },
        { title: '巡检类型', width: 160, render: (_, row) => row.inspectionTypeCode ? <>
          {typeByCode(row.inspectionTypeCode)?.name ?? '未知类型'}
          <br /><Typography.Text type="secondary" style={{ fontSize: 12 }}>{row.inspectionTypeCode}</Typography.Text>
        </> : '-' },
        { title: '任务标题', width: 200, render: (_, row) => row.taskTitle || <Typography.Text type="secondary">自动生成</Typography.Text> },
        { title: '责任员工', width: 140, render: (_, row) => row.targetEmpName ? `${row.targetEmpName}（${row.targetEmpNo}）` : row.targetEmpNo || '-' },
        { title: '频次', width: 90, dataIndex: 'frequency', render: (v: string) => v || '-' },
        { title: '覆盖区域', width: 130, dataIndex: 'coverageAreaCodes', render: (v: string) => v || '-' },
        { title: '校验', width: 260, render: (_, row) => row.errors.length
          ? <Space wrap size={4}>{row.errors.map((message) => <Tag key={message} color="red">{message}</Tag>)}</Space>
          : <Tag color="green">可导入</Tag> },
      ]} /> : <Table rowKey={(row) => `${row.planTitle}|${row.message}`} size="small" pagination={false} scroll={{ x: 760 }} data={importResults} columns={[
        { title: '计划', width: 280, render: (_, row) => row.planTitle },
        { title: '结果', width: 100, render: (_, row) => row.ok ? <Tag color="green">成功</Tag> : row.skipped ? <Tag color="gray">跳过</Tag> : <Tag color="red">失败</Tag> },
        { title: '说明', render: (_, row) => row.message },
      ]} />}
      <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 16 }}>
        <Button disabled={importing} onClick={() => setImportVisible(false)}>关闭</Button>
        {!importResults.length && <Button type="primary" loading={importing} disabled={!importValidRows.length} onClick={() => void submitImport()}>
          确认导入（{importPlanCount} 个计划 / {importValidRows.length} 行）
        </Button>}
      </div>
    </Modal>
  </>
}
