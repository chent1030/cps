/**
 * StructuredValue —— 任意值的结构化渲染，管理端所有“动态值”统一走这里。
 *
 * 背景：需求要求所有页面都不允许出现 JSON 字符串（`{...}`、`[{"a":1}]`、转义串、<pre> 里的 JSON），
 * 因此任何来自接口的值（字符串 / 对象 / 数组 / JSON 字符串）都必须解析成可读组件：
 * - 字符串：先尝试 JSON.parse（含二次转义），成功则递归结构化；失败按纯文本（超长省略 + 展开）
 * - 对象：键值块，英文 key 自动转中文（未知 key 原样），值递归
 * - 数组：图片墙 / 标量 Tag 列表 / 对象数组表格（列 = 字段并集中文化）
 * - 图片地址：http(s)（带图片后缀）、`/api/cps/attachments/{id}/content`、图片类字段下的纯附件 id
 *   → 缩略图（可点开预览）
 * - 空值 / null / '-' 统一显示 “—”
 */
import { Space, Table, Tag, Typography } from '@arco-design/web-react'
import type { TableColumnProps } from '@arco-design/web-react'
import type { CSSProperties, ReactNode } from 'react'
import { AttachmentThumbs } from './AttachmentImages'
import { attachmentContentUrl, resolveAttachmentUrl, splitAttachmentUrls } from '../lib/attachmentUrl'

export interface StructuredValueProps {
  /** 任意接口值：字符串 / 数字 / 布尔 / 数组 / 对象 / JSON 字符串 */
  value: unknown
  /** 该值在父对象里的 key（英文或中文），用于中文标签与图片/状态/频次语义 */
  fieldKey?: string
  /** 空值占位，默认 “—” */
  emptyText?: ReactNode
  /** 纯文本最多展示行数，超出省略 + 展开，默认 4 */
  textRows?: number
  /** 图片缩略图尺寸 */
  imageWidth?: number
  imageHeight?: number
}

const FIELD_LABELS: Record<string, string> = {
  // 通用
  id: 'ID',
  title: '标题',
  status: '状态',
  message: '信息',
  detail: '详情',
  metrics: '运行指标',
  errorMessage: '错误信息',
  action: '动作',
  targetType: '对象',
  targetId: '对象ID',
  comment: '意见',
  result: '结果',
  reason: '原因',
  outcome: '结果',
  extraData: '动态字段',
  createdAt: '创建时间',
  updatedAt: '更新时间',
  submittedAt: '提交时间',
  // 巡检计划 / 任务（兼容旧快照的下划线 key）
  planId: '计划ID',
  draft_id: '草稿ID',
  plan_type: '计划类型',
  source_run_id: '来源运行ID',
  draft_content_json: '草稿内容',
  generated_at: '生成时间',
  model_version: '模型版本',
  schema_version: '格式版本',
  task_type: '任务类型',
  priority: '优先级',
  rationale: '推荐理由',
  factory: '工厂',
  area: '区域',
  risk_basis: '风险依据',
  planType: '计划类型',
  draftContentJson: '巡检任务',
  tasks: '巡检任务',
  targetFactory: '目标工厂',
  targetArea: '目标区域',
  riskBasis: '风险依据',
  draftBy: '拟稿人',
  inspectionTypeCode: '巡检类型',
  inspectionTypeName: '巡检类型',
  taskTitle: '任务标题',
  targetEmpNo: '责任人工号',
  targetEmpName: '责任人',
  frequency: '频次',
  acceptanceCriteria: '验收标准',
  evidenceRequirement: '证据要求',
  coverageAreaCodes: '覆盖区域',
  lastBuildResult: '建单结果',
  lastBuildErrorMsg: '建单错误',
  lastBuildAt: '建单时间',
  taskType: '任务类型',
  taskStatus: '任务状态',
  // 问题 / 整改
  issueId: '问题ID',
  description: '问题描述',
  location: '地点',
  reporterName: '上报人',
  rectifyVersion: '整改版本',
  reasonAnalysis: '原因分析',
  shortTermMeasure: '短期措施',
  longTermMeasure: '长期措施',
  responsibleEmpName: '责任员工',
  photoCount: '整改照片',
  rectifyImages: '整改图片',
  images: '图片',
  aiOpinion: 'AI 初审意见',
  submitterName: '整改提交人',
  reviewOpinion: '审核意见',
  judgeReason: '判定原因',
  judgeResult: '判定结果',
  textValue: '文字结果',
  // 点检 / 记录
  itemCode: '点检项编码',
  itemContent: '点检内容',
  checkTypes: '点检类型',
  deductScore: '扣分',
  applicableRoomTypes: '适用房型',
  photoCategory: '照片分类',
  // 运行时
  state: '接入状态',
  tenantId: '租户标识',
  baseUrl: '运行时地址',
}

const STATUS_LABELS: Record<string, string> = {
  PENDING_REVIEW: '待审核',
  PENDING: '待审批',
  APPROVED: '已批准',
  REJECTED: '已拒绝',
  WITHDRAWN: '已撤回',
  DRAFT: '草稿',
  CLOSED: '已关闭',
  待审批: '待审批',
  待审核: '待审核',
  已通过: '已通过',
  已批准: '已批准',
  已驳回: '已驳回',
  已拒绝: '已拒绝',
  已撤回: '已撤回',
}

const STATUS_COLORS: Record<string, string> = {
  PENDING_REVIEW: 'orange',
  PENDING: 'orange',
  APPROVED: 'green',
  REJECTED: 'red',
  WITHDRAWN: 'gray',
  DRAFT: 'gray',
  CLOSED: 'green',
  待审批: 'orange',
  待审核: 'orange',
  已通过: 'green',
  已批准: 'green',
  已驳回: 'red',
  已拒绝: 'red',
  已撤回: 'gray',
}

/** 枚举值本地化：按字段语义翻译，避免 task_type=INSPECT_PATROL 这类裸枚举。 */
const VALUE_LABELS: Record<string, Record<string, string>> = {
  task_type: { INSPECT_PATROL: '巡检', INSPECT_CHECK: '点检', INSPECT_RECTIFY: '整改' },
  priority: { high: '高', normal: '普通', low: '低' },
  riskLevel: { HIGH: '高', MEDIUM: '中', LOW: '低' },
}

const FREQUENCY_LABELS: Record<string, string> = {
  DAILY: '每日',
  WEEKLY: '每周',
  MONTHLY: '每月',
  QUARTERLY: '每季度',
  YEARLY: '每年',
  ONCE: '单次',
}

interface RenderOptions {
  emptyText: ReactNode
  textRows: number
  imageWidth: number
  imageHeight: number
}

export const fieldLabel = (key: string): string => FIELD_LABELS[key] ?? key

const isStatusKey = (key: string) => /(status|状态)$/i.test(key)
const isFrequencyKey = (key: string) => /(frequency|频次)$/i.test(key)
/** key 本身暗示值是图片/附件（决定纯数字能否按附件 ID 解析）。 */
const isImageKey = (key: string) => /(图片|照片|相片|前后照|附件|凭证|image|photo|attachment)/i.test(key)

const textStyle: CSSProperties = { whiteSpace: 'pre-wrap', wordBreak: 'break-word' }

/** 值是否看起来是图片地址：绝对 URL 带图片后缀，或附件代理路径。 */
function isImageUrlText(value: string): boolean {
  const text = value.trim()
  if (!/^(https?:)?\/\//i.test(text) && !text.startsWith('/api/')) return false
  if (/\.(png|jpe?g|gif|webp|bmp|svg)(\?|#|$)/i.test(text)) return true
  return /attachments?\/|cps-attachments|minio|photo|image|proof/i.test(text)
}

/** 从数组/单值里提取可展示的图片地址；元素支持 URL 串、逗号分隔串、{id,url} 对象。 */
function collectImageUrls(key: string, value: unknown): string[] {
  const urls: string[] = []
  const push = (item: unknown) => {
    if (typeof item === 'string') {
      const text = item.trim()
      const explicit = /^(https?:)?\/\//i.test(text) || text.startsWith('/')
      if (explicit || (isImageKey(key) && /^\d+$/.test(text))) urls.push(...splitAttachmentUrls(text))
      return
    }
    if (typeof item === 'number') {
      if (isImageKey(key)) urls.push(attachmentContentUrl(item))
      return
    }
    if (item && typeof item === 'object') {
      const record = item as Record<string, unknown>
      const candidate = typeof record.url === 'string' ? record.url : typeof record.fileUrl === 'string' ? record.fileUrl : null
      const resolved = resolveAttachmentUrl(candidate)
      // 只有图片类字段（或地址本身像图片）才把对象当附件，避免把任务对象里的普通 url 变成图片墙
      if (resolved && (isImageKey(key) || isImageUrlText(resolved))) urls.push(resolved)
      else if (record.id != null && isImageKey(key)) urls.push(attachmentContentUrl(record.id as number | string))
    }
  }
  if (Array.isArray(value)) value.forEach(push)
  else push(value)
  return urls
}

/** 字符串化的 JSON / 二次转义 JSON → 值；解析不了返回原字符串。 */
export function parseJsonish(value: string): unknown {
  let current: unknown = value
  for (let depth = 0; depth < 3; depth += 1) {
    if (typeof current !== 'string') return current
    const trimmed = current.trim()
    if (!(trimmed.startsWith('{') || trimmed.startsWith('['))) return current
    try {
      current = JSON.parse(trimmed) as unknown
    } catch {
      return current
    }
  }
  return current
}

const isEmptyValue = (value: unknown): boolean =>
  value === null || value === undefined || value === '' ||
  (typeof value === 'string' && ['-', 'null', 'undefined'].includes(value.trim().toLowerCase()))

/** 长文本：超出指定行数时省略并提供“展开”。 */
function LongText({ text, rows }: { text: string; rows: number }) {
  const long = text.length > rows * 90 || text.split('\n').length > rows
  if (!long) return <Typography.Text style={textStyle}>{text}</Typography.Text>
  return (
    <Typography.Paragraph
      style={{ ...textStyle, marginBottom: 0 }}
      ellipsis={{ rows, expandable: true, showTooltip: false }}
    >
      {text}
    </Typography.Paragraph>
  )
}

function StatusTag({ text }: { text: string }) {
  const upper = text.toUpperCase()
  const label = STATUS_LABELS[upper] ?? text
  const color = STATUS_COLORS[upper]
  return color ? <Tag color={color}>{label}</Tag> : <Tag>{label}</Tag>
}

function FrequencyText({ text }: { text: string }) {
  return <>{FREQUENCY_LABELS[text.toUpperCase()] ?? text}</>
}

/** 对象 → 键值块（value 递归），不再出现 JSON 大括号。 */
function ObjectBlock({ value, options }: { value: Record<string, unknown>; options: RenderOptions }) {
  const entries = Object.entries(value).filter(([key, item]) => key !== '' && item !== undefined)
  if (!entries.length) return <Typography.Text type="secondary">{options.emptyText}</Typography.Text>
  return (
    <div style={{ border: '1px solid var(--color-border-2)', borderRadius: 4, padding: '6px 10px', maxWidth: '100%' }}>
      {entries.map(([key, item]) => (
        <div key={key} style={{ display: 'flex', gap: 8, lineHeight: 1.8 }}>
          <Typography.Text type="secondary" style={{ minWidth: 96, flexShrink: 0 }}>{fieldLabel(key)}</Typography.Text>
          <div style={{ minWidth: 0, flex: 1 }}>{renderValue(key, item, options)}</div>
        </div>
      ))}
    </div>
  )
}

/** 表格行的稳定 key：同一对象跨渲染复用，避免 key 里出现 JSON 串。 */
const rowKeyMap = new WeakMap<object, string>()
let rowKeySeq = 0
function rowKeyOf(record: Record<string, unknown>): string {
  if (!rowKeyMap.has(record)) {
    rowKeySeq += 1
    rowKeyMap.set(record, `row-${rowKeySeq}`)
  }
  return rowKeyMap.get(record) ?? 'row'
}

/** 对象数组 → 表格（列 = 所有元素出现过的字段并集中文化，单元格递归渲染）。 */
function ObjectTable({ rows, options }: { rows: Record<string, unknown>[]; options: RenderOptions }) {
  const keys: string[] = []
  rows.forEach((row) => {
    Object.entries(row).forEach(([key, item]) => {
      if (!keys.includes(key) && item !== undefined && item !== null && item !== '') keys.push(key)
    })
  })
  if (!keys.length) return <Typography.Text type="secondary">{options.emptyText}</Typography.Text>
  const columns: TableColumnProps<Record<string, unknown>>[] = keys.map((key) => ({
    title: fieldLabel(key),
    dataIndex: key,
    render: (item: unknown) => renderValue(key, item, { ...options, textRows: Math.min(options.textRows, 3) }),
  }))
  return (
    <Table
      rowKey={rowKeyOf}
      size="small"
      border={{ wrapper: true, cell: true }}
      pagination={false}
      data={rows}
      columns={columns}
      scroll={{ x: true }}
    />
  )
}

function ArrayValue({ fieldKey, value, options }: { fieldKey: string; value: unknown[]; options: RenderOptions }) {
  if (!value.length) return <Typography.Text type="secondary">{options.emptyText}</Typography.Text>
  const urls = collectImageUrls(fieldKey, value)
  if (urls.length) {
    return <AttachmentThumbs values={urls} width={options.imageWidth} height={options.imageHeight} alt={fieldLabel(fieldKey)} />
  }
  const objects = value.filter((item): item is Record<string, unknown> => Boolean(item) && typeof item === 'object' && !Array.isArray(item))
  if (objects.length === value.length) return <ObjectTable rows={objects} options={options} />
  if (value.every((item) => item === null || typeof item !== 'object')) {
    return (
      <Space wrap size={4}>
        {value.map((item, index) => (
          <Tag key={index}>{isEmptyValue(item) ? String(options.emptyText) : String(item)}</Tag>
        ))}
      </Space>
    )
  }
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
      {value.map((item, index) => (
        <div key={index}>{renderValue(fieldKey, item, options)}</div>
      ))}
    </div>
  )
}

function renderValue(fieldKey: string, value: unknown, options: RenderOptions): ReactNode {
  if (isEmptyValue(value)) return <Typography.Text type="secondary">{options.emptyText}</Typography.Text>
  if (typeof value === 'string') {
    const parsed = parseJsonish(value)
    if (typeof parsed !== 'string') return renderValue(fieldKey, parsed, options)
    const text = value.trim()
    const enumText = VALUE_LABELS[fieldKey]?.[text]
    if (enumText) return <Typography.Text>{enumText}</Typography.Text>
    if (isStatusKey(fieldKey)) return <StatusTag text={text} />
    if (isFrequencyKey(fieldKey)) return <FrequencyText text={text} />
    if (isImageKey(fieldKey)) {
      const urls = splitAttachmentUrls(text)
      if (urls.length) return <AttachmentThumbs values={urls} width={options.imageWidth} height={options.imageHeight} alt={fieldLabel(fieldKey)} />
    }
    if (isImageUrlText(text)) return <AttachmentThumbs values={[text]} width={options.imageWidth} height={options.imageHeight} alt={fieldLabel(fieldKey)} />
    return <LongText text={text} rows={options.textRows} />
  }
  if (typeof value === 'boolean') return <>{value ? '是' : '否'}</>
  if (typeof value === 'number' || typeof value === 'bigint') return <>{String(value)}</>
  if (Array.isArray(value)) return <ArrayValue fieldKey={fieldKey} value={value} options={options} />
  if (typeof value === 'object') return <ObjectBlock value={value as Record<string, unknown>} options={options} />
  return <LongText text={String(value)} rows={options.textRows} />
}

/**
 * 渲染任意接口值。用法：
 * ```tsx
 * <StructuredValue value={issue.extraData} />
 * <StructuredValue value={record.judgeReason} textRows={2} />
 * ```
 */
export function StructuredValue({
  value,
  fieldKey = '',
  emptyText = '—',
  textRows = 4,
  imageWidth = 96,
  imageHeight = 72,
}: StructuredValueProps) {
  return <>{renderValue(fieldKey, value, { emptyText, textRows, imageWidth, imageHeight })}</>
}
