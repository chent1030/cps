import { defineStore } from 'pinia'
import { ref } from 'vue'

import type { CpsIssueListItem, CpsIssueTab } from '@/types/cps'

/**
 * 列表数据缓存：按 tab 缓存最近一次拉取结果，返回上一页时避免重复请求与闪烁。
 * 仍提供手动刷新能力（下拉/操作后失效对应 tab）。
 */
export const useListCacheStore = defineStore('cpsListCache', () => {
  const cache = ref<Record<CpsIssueTab, CpsIssueListItem[]>>({
    todo: [],
    created: [],
    related: [],
    closed: [],
  })
  const loadedAt = ref<Record<CpsIssueTab, number>>({
    todo: 0,
    created: 0,
    related: 0,
    closed: 0,
  })

  const set = (tab: CpsIssueTab, items: CpsIssueListItem[]) => {
    cache.value[tab] = items
    loadedAt.value[tab] = Date.now()
  }

  const get = (tab: CpsIssueTab) => cache.value[tab]
  const age = (tab: CpsIssueTab) => Date.now() - loadedAt.value[tab]

  const invalidate = (tab?: CpsIssueTab) => {
    if (tab) {
      loadedAt.value[tab] = 0
    } else {
      ;(Object.keys(loadedAt.value) as CpsIssueTab[]).forEach((key) => {
        loadedAt.value[key] = 0
      })
    }
  }

  return { cache, loadedAt, set, get, age, invalidate }
})
