import { describe, expect, it } from 'vitest'
import { cpsApi } from '@/api/index'
import * as ai from '@/api/cps/ai'
import * as attachment from '@/api/cps/attachment'
import * as inspectionTask from '@/api/cps/inspectionTask'
import * as inspectionType from '@/api/cps/inspectionType'
import * as issue from '@/api/cps/issue'
import * as issueDraft from '@/api/cps/issueDraft'
import * as master from '@/api/cps/master'
import * as speech from '@/api/cps/speech'

const modules = { ai, attachment, inspectionTask, inspectionType, issue, issueDraft, master, speech }

describe('cpsApi 聚合出口', () => {
  it('每个 cps 模块导出的方法都挂在 cpsApi 上', () => {
    const missing: string[] = []
    for (const [name, mod] of Object.entries(modules)) {
      for (const key of Object.keys(mod)) {
        if (!(key in cpsApi)) missing.push(`${name}.${key}`)
      }
    }
    expect(missing).toEqual([])
  })

  it('cpsApi 上的每一项都是函数', () => {
    for (const [key, value] of Object.entries(cpsApi)) {
      expect(typeof value, key).toBe('function')
    }
  })
})
