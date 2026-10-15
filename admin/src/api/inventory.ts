/**
 * 库存台账前端 API 客户端（波次 14 E4 收尾）。
 *
 * 设计原则：
 * - 不新建 axios 实例：复用 `api.ts` 的 `request/ApiError/query` 工具，避免与项目既有 baseURL、
 *   鉴权 cookie、401 处理逻辑分叉。
 * - 五个高层函数直接映射后端 `cps/admin/inventory-*` 端点，便于在页面里以 useRemote 形式消费。
 */
import { api } from '../api'

export interface ItemQuery {
  keyword?: string
  factory?: string
  storageRoom?: string
  lowStock?: boolean
  page?: number
  size?: number
}

export interface SaveItemBody {
  id?: number
  itemCode: string
  itemName: string
  unit?: string
  stockQty?: number
  alertThreshold: number
  baseCode?: string
  factory?: string
  storageRoom?: string
  roomKeeperEmpNo?: string
  roomKeeperEmpName?: string
  remark?: string
  enabled?: boolean
}

export interface TxnQuery {
  itemId?: number
  type?: 'IN' | 'OUT' | 'ADJUST'
  page?: number
  size?: number
}

export interface AlertQuery {
  status?: 'OPEN' | 'RESOLVED_AUTO' | 'RESOLVED_MANUAL' | 'IGNORED'
  page?: number
  size?: number
}

export const listItems = (params: ItemQuery = {}) =>
  api.inventoryItems({
    keyword: params.keyword || undefined,
    factory: params.factory || undefined,
    storageRoom: params.storageRoom || undefined,
    lowStock: params.lowStock ? true : undefined,
    page: params.page, size: params.size,
  })

export const saveItem = (body: SaveItemBody) => api.saveInventoryItem(body)

export const setItemEnabled = (id: number, enabled: boolean) =>
  api.setInventoryItemEnabled(id, enabled)

export const listTxns = (params: TxnQuery = {}) =>
  api.inventoryTxns({
    itemId: params.itemId,
    type: params.type,
    page: params.page, size: params.size,
  })

export const listAlerts = (params: AlertQuery = {}) =>
  api.inventoryAlerts({
    status: params.status,
    page: params.page, size: params.size,
  })

export const handleAlert = (id: number, action: 'IGNORE' | 'CLOSE', reason?: string) =>
  api.handleInventoryAlert(id, { action, reason })