import { request } from '@/api/request'
import type { CpsOption } from '@/types/cps'

export type { CpsOption }

export interface CpsEmployeeOption {
  empNo: string
  empName: string
}

export const getFactories = () => {
  return request.get<CpsOption[]>('/api/cps/master/factories')
}

export const getAreas = (factory: string) => {
  return request.get<CpsOption[]>('/api/cps/master/areas', { params: { factory } })
}

export const getLines = (factory: string, area: string) => {
  return request.get<CpsOption[]>('/api/cps/master/lines', { params: { factory, area } })
}

export const getProcesses = (factory: string, area: string, line?: string) => {
  return request.get<CpsOption[]>('/api/cps/master/processes', { params: { factory, area, line } })
}

export const getCategories = (parentId?: number) => {
  return request.get<CpsOption[]>('/api/cps/master/categories', { params: { parentId } })
}

export const getFeedbackHandler = async (params: {
  factory: string
  area: string
  line: string
  process: string
}): Promise<CpsEmployeeOption | null> => {
  // 后端返回单个人 {empNo, empName}，前端展示为可选列表
  const response = await request.get<CpsEmployeeOption>('/api/cps/assignment/feedback-handler', { params })
  if (!response || !response.empNo) return null
  return response
}

export const getReviewer = (params: { factory: string; area: string }) => {
  return request.get<{ empNo: string; empName: string }>('/api/cps/assignment/reviewer', { params })
}

export const searchCpsEmployees = async (keyword: string) => {
  // 后端未提供按关键字搜人接口。后端真正可用的是 /api/cps/assignment/feedback-handler
  // （按工厂/区域/拉线/工序查 1 个人），这里保持类型兼容并在无接口时返回空数组，
  // 避免在反馈人选人弹层出现 .find is not a function。
  if (!keyword || keyword.trim().length === 0) return [] as CpsEmployeeOption[]
  try {
    return await request.get<CpsEmployeeOption[]>('/api/cps/master/employees', { params: { keyword } })
  } catch (error) {
    return [] as CpsEmployeeOption[]
  }
}
