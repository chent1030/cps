import { request } from '@/api/request'
import type { CpsRoomCheckRecord, CpsRoomCheckTask, CpsRoomInfo } from '@/types/cps'

const EMP_NO = import.meta.env.VITE_CPS_EMP_NO ?? 'DEMO_EMP'

/** B5① 点检任务列表（本人 INSPECT_CHECK 任务，含覆盖房间与完成进度）。 */
export const listRoomCheckTasks = (status?: string) => {
  return request.get<CpsRoomCheckTask[]>('/api/cps/room-checks/tasks', {
    params: { empNo: EMP_NO, status },
  })
}

/** 覆盖房间解析：roomCode → roomId（start 需要 roomId）。 */
export const listRooms = () => {
  return request.get<CpsRoomInfo[]>('/api/cps/admin/rooms', { params: { enabled: true } })
}

/** 开启点检（幂等，重复开启返回原单）。 */
export const startRoomCheck = (payload: { planTaskId: number; roomId: number }) => {
  return request.post<CpsRoomCheckRecord>('/api/cps/room-checks/start', {
    ...payload,
    empNo: EMP_NO,
  })
}

export const getRoomCheckRecord = (id: number) => {
  return request.get<CpsRoomCheckRecord>(`/api/cps/room-checks/records/${id}`, {
    params: { empNo: EMP_NO },
  })
}

/** 照片提交（multipart；重拍覆盖）。File 走 FormData，设备临时路径走 uni.uploadFile。 */
export const uploadRoomCheckPhoto = (
  recordId: number,
  itemId: number,
  source: File | string,
) => {
  if (typeof source === 'string') {
    return uploadPhotoByTempPath(recordId, itemId, source)
  }
  const formData = new FormData()
  formData.append('file', source)
  formData.append('empNo', EMP_NO)
  return request.post<CpsRoomCheckRecord>(
    `/api/cps/room-checks/records/${recordId}/items/${itemId}/photo`,
    formData,
  )
}

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? ''

const uploadPhotoByTempPath = (recordId: number, itemId: number, filePath: string) => {
  if (typeof uni === 'undefined' || typeof uni.uploadFile !== 'function') {
    return Promise.reject(new Error('CPS room-check photo upload unavailable'))
  }
  const path = `/api/cps/room-checks/records/${recordId}/items/${itemId}/photo`
  return new Promise((resolve: (value: CpsRoomCheckRecord) => void, reject) => {
    uni.uploadFile({
      url: new URL(`${API_BASE}${path}`, window.location.origin).toString(),
      filePath,
      name: 'file',
      formData: { empNo: EMP_NO },
      success(response) {
        if ((response.statusCode ?? 0) < 200 || (response.statusCode ?? 0) >= 300) {
          reject(new Error(`CPS room-check photo upload failed: ${response.statusCode}`))
          return
        }
        try {
          resolve(parseResponse(response.data))
        } catch (error) {
          reject(error)
        }
      },
      fail(error) {
        reject(new Error(error.errMsg || 'CPS room-check photo upload failed'))
      },
    })
  })
}

const parseResponse = (data: string | object | undefined) => {
  return typeof data === 'string' ? (JSON.parse(data) as CpsRoomCheckRecord) : (data as CpsRoomCheckRecord)
}

/** 提交并同步判定：TYPE_MISMATCH/UNJUDGEABLE 阻断须补拍；判定服务降级返回 PENDING 不阻塞。 */
export const submitRoomCheckRecord = (recordId: number) => {
  return request.post<CpsRoomCheckRecord>(`/api/cps/room-checks/records/${recordId}/submit`, {
    empNo: EMP_NO,
  })
}
