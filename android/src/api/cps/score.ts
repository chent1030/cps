import { request } from '@/api/request'

/** B4 周排名行（与后端 CpsRoomCheckRankingResponse.Row 对齐）。 */
export interface CpsRoomRankingItem {
  rank: number
  roomId: number
  roomCode: string
  roomName: string
  checkCount: number
  totalScore: number
  firstSubmittedAt: string | null
}

/** B4 周排名响应（GET /api/cps/admin/room-check-rankings）。 */
export interface CpsRoomRankingResponse {
  weekStart: string
  weekEnd: string
  rows: CpsRoomRankingItem[]
}

/**
 * B4 周排名（PRD 24.3 + D-08）：房间按周期总分排名。
 * weekStart 空默认上一完整自然周；任意日期归一所在周周一（东八区）。
 */
export const listWeeklyScores = (weekStart?: string) => {
  return request.get<CpsRoomRankingResponse>('/api/cps/admin/room-check-rankings', {
    params: { weekStart: weekStart ?? null },
  })
}
