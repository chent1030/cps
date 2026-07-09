import { request } from '@/api/request'
import type { CpsOption } from '@/types/cps'

export type { CpsOption }

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

export const getFeedbackHandler = (params: {
  factory: string
  area: string
  line: string
  process: string
}) => {
  return request.get<{ empNo: string; empName: string }>('/api/cps/assignment/feedback-handler', { params })
}

export const getReviewer = (params: { factory: string; area: string }) => {
  return request.get<{ empNo: string; empName: string }>('/api/cps/assignment/reviewer', { params })
}
