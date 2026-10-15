import { request } from '@/api/request'

/** B4 周评分条目（与后端 CpsWeeklyScoreResponse 对齐）。 */
export interface CpsWeeklyScoreItem {
  id: number
  weekStartDate: string
  empNo: string
  empName?: string
  regionSupervisorId?: number
  regionSupervisorName?: string
  totalScore: number
  rank?: number
  roomCheckCount: number
  photoCount: number
  naturalWeekFlag: boolean
  lines?: CpsWeeklyScoreLineItem[]
}

export interface CpsWeeklyScoreLineItem {
  id: number
  itemId: string
  scoreDelta: number
  reason?: string
}

export interface CpsWeeklyScorePage<T> {
  records: T[]
  total: number
  page: number
  pageSize: number
}

/** B3 本周排名查询（PRD §32 / §24）。 */
export const listWeeklyScores = (
  weekStartDate: string,
  regionSupervisorId?: number,
  page = 1,
  pageSize = 50,
) => {
  return request.get<CpsWeeklyScorePage<CpsWeeklyScoreItem>>(
    '/api/cps/admin/scores/weekly',
    {
      params: { weekStartDate, regionSupervisorId, page, pageSize },
    },
  )
}
