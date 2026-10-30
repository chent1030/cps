/**
 * 审批业务快照（bizSnapshot）渲染。
 *
 * 快照是 JSON 字符串：解析后交给 StructuredValue 统一结构化展示（键值块 / 任务表格 /
 * 图片墙 / 中文状态 Tag），界面上不允许出现任何裸 JSON。
 *
 * 兼容性：
 * - 历史快照是英文 key（planId/title/planType/...），中文 key 由 StructuredValue 映射；
 * - 旧快照把任务字符串化在 draftContentJson 里 → 解析后统一放到「巡检任务」区块；
 * - planType 已被产品判定为冗余字段（W1），即使旧快照里存在也不展示。
 */
import { Typography } from '@arco-design/web-react'
import { StructuredValue, parseJsonish } from './StructuredValue'

const HIDDEN_KEYS = new Set(['planType'])
/** 快照里可能承载任务数组的 key（后端新快照用「巡检任务」，旧快照用 tasks / draftContentJson）。 */
const TASK_KEYS = ['tasks', '巡检任务', '任务']

function tasksOf(value: unknown, depth = 0): Record<string, unknown>[] | null {
  if (!value || typeof value !== 'object' || Array.isArray(value) || depth > 2) return null
  const record = value as Record<string, unknown>
  for (const key of TASK_KEYS) {
    const candidate = record[key]
    if (Array.isArray(candidate) && candidate.length && candidate.every((item) => item && typeof item === 'object')) {
      return candidate as Record<string, unknown>[]
    }
  }
  // 旧草稿把 tasks 嵌在 draft_content_json.tasks 里，向下再找一层
  for (const item of Object.values(record)) {
    const nested = tasksOf(item, depth + 1)
    if (nested) return nested
  }
  return null
}

/** 审批详情里的「业务快照（提交时）」。无法解析为对象时降级交给 StructuredValue（纯文本/数组）。 */
export function ApprovalSnapshot({ snapshot }: { snapshot: string }) {
  const parsed = parseJsonish(snapshot)
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
    return <StructuredValue value={parsed} />
  }
  const object = parsed as Record<string, unknown>

  // 旧快照把任务塞在 draftContentJson 字符串里：解析后与「巡检任务」数组统一成底部任务区块
  const legacyTasks = typeof object.draftContentJson === 'string' ? tasksOf(parseJsonish(object.draftContentJson)) : null
  const tasks = tasksOf(object) ?? legacyTasks
  const rest = Object.fromEntries(Object.entries(object).filter(([key, value]) => {
    if (HIDDEN_KEYS.has(key)) return false
    if (tasks && (TASK_KEYS.includes(key) || key === 'draftContentJson')) return false
    return value !== undefined
  }))

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      {Object.keys(rest).length > 0 && <StructuredValue value={rest} />}
      {tasks && (
        <div>
          <Typography.Text bold>巡检任务（{tasks.length}）</Typography.Text>
          <div style={{ marginTop: 8 }}>
            <StructuredValue value={tasks} textRows={3} />
          </div>
        </div>
      )}
    </div>
  )
}
