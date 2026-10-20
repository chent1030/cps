import { request } from '@/api/request'
import type { CpsCheckItemInfo, CpsInspectionType } from '@/types/cps'

/** Phase B1：拉取客户端可用的巡检类型（仅 enabled=1）。Android 端启动时一次性缓存。 */
export const listCpsInspectionTypes = () => {
  return request.get<CpsInspectionType[]>('/api/cps/inspection-types')
}

export const getCpsInspectionType = (code: string) => {
  return request.get<CpsInspectionType>(`/api/cps/inspection-types/${code}`)
}

/** Phase D2：类型的点检项明细（新建问题填写页按配置渲染；仅不分区域类型）。 */
export const getInspectionTypeCheckItems = (code: string) => {
  return request.get<CpsCheckItemInfo[]>(`/api/cps/inspection-types/${code}/check-items`)
}
