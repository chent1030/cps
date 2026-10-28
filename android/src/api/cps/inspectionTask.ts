import { request } from '@/api/request'
import type {
  CpsInspectionArea,
  CpsInspectionRecord,
  CpsInspectionRecordDetail,
  CpsInspectionRecordItem,
  CpsInspectionRectifyPayload,
} from '@/types/cps'

/**
 * Phase C1 统一巡检任务 API（移动端巡检执行）。
 * 流程：areas（分区域类型选区域）→ start → 逐项 saveItemResult → submit。
 * 类型列表走 /api/cps/inspection-types（inspectionType.ts）。
 */

const EMP_NO = import.meta.env.VITE_CPS_EMP_NO ?? '61016968'
const EMP_NAME = import.meta.env.VITE_CPS_EMP_NAME ?? '陈涛'

/** 分区域类型的启用区域列表 */
export const listInspectionAreas = (typeCode: string) => {
  return request.get<CpsInspectionArea[]>('/api/cps/inspection-tasks/areas', {
    params: { typeCode },
  })
}

/** 开始执行：生成记录 + 点检项明细快照（分区域类型必带 areaId） */
export const startInspection = (payload: { typeCode: string; areaId?: number | null; planTaskId?: number | null }) => {
  return request.post<CpsInspectionRecord>('/api/cps/inspection-tasks/start', {
    ...payload,
    empNo: EMP_NO,
    empName: EMP_NAME,
  })
}

/** 记录详情（record + items，执行页渲染用） */
export const getInspectionRecord = (recordId: number) => {
  return request.get<CpsInspectionRecordDetail>(`/api/cps/inspection-tasks/${recordId}`)
}

/** 保存单项执行结果（合格判定 / 文本 / 照片） */
export const saveInspectionItemResult = (
  itemId: number,
  payload: {
    passResult?: 'PASS' | 'FAIL' | null
    textValue?: string | null
    photoObjectKey?: string | null
    photoFileName?: string | null
    photoCount?: number | null
  },
) => {
  return request.post<CpsInspectionRecordItem>(`/api/cps/inspection-tasks/items/${itemId}/result`, payload)
}

/** 提交计分（校验照片最低张数 → JUDGED 锁定） */
export const submitInspection = (recordId: number) => {
  return request.post<CpsInspectionRecord>(`/api/cps/inspection-tasks/${recordId}/submit`, {})
}

/** 我的执行中记录（IN_PROGRESS，用于「执行中」分组恢复现场） */
export const listMyInProgressInspections = () => {
  return request.get<CpsInspectionRecord[]>('/api/cps/inspection-tasks/my-in-progress', {
    params: { empNo: EMP_NO },
  })
}

/**
 * 我的待整改记录（PENDING_REVIEW 复核通过后转入，整改责任人为当前用户）。
 * web 端复核通过后，记录状态变 PENDING_RECTIFY 并出现在这里。
 */
export const myRectify = (empNo: string = EMP_NO) => {
  return request.get<CpsInspectionRecord[]>('/api/cps/inspection-tasks/my-rectify', {
    params: { empNo },
  })
}

/**
 * 提交整改：原因分析 / 短期措施 / 长期措施各 ≥15 字 + 1~5 张照片。
 * 成功后记录变 JUDGED，流程结束。
 */
export const submitRectify = (recordId: number, payload: CpsInspectionRectifyPayload) => {
  return request.post<CpsInspectionRecord>(`/api/cps/inspection-tasks/${recordId}/rectify`, {
    ...payload,
    empNo: payload.empNo ?? EMP_NO,
    empName: payload.empName ?? EMP_NAME,
  })
}
