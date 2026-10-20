import { defineStore } from 'pinia'
import { ref } from 'vue'

import type { CpsInspectionType, CpsOption } from '@/types/cps'
import type { CpsAiSuggestionPayload, CpsUploadedImage } from '@/types/cps'
import type { CpsEmployeeOption } from '@/api/cps/master'

/**
 * 新建问题表单草稿。保证页面跳转、系统回收后回到新建页数据不丢失；
 * 横竖屏切换因为是纯 CSS reflow（组件不卸载），本身已不会丢数据，这里做双重保险。
 */
export const useFormDraftStore = defineStore('cpsFormDraft', () => {
  const location = ref({ factory: '', area: '', line: '', process: '' })
  const category = ref({ categoryL1Id: null as number | null, categoryL2Id: null as number | null })
  const images = ref<CpsUploadedImage[]>([])
  const imagePreviewSources = ref<Record<number, string>>({})
  const description = ref('')
  // 资源号：现场手动录入的物理资源标识（PRD 24.2.6），必填。
  const resourceNo = ref('')
  // Phase B1：已选巡检类型编码（null=走老路径不传 type）。
  const inspectionTypeCode = ref<string | null>(null)
  // Phase B1：动态字段值（key→value），与 inspectionType 的 formSchemaJson 对应。
  const extraData = ref<Record<string, unknown>>({})
  // Phase B2：当前表单对应的服务端草稿 ID（null=未暂存过；暂存后回填，提交后清空）。
  const draftId = ref<number | null>(null)
  const feedbackEmpNo = ref('')
  const feedbackPerson = ref<CpsEmployeeOption | null>(null)
  const aiSuggestion = ref<CpsAiSuggestionPayload | null>(null)
  // 缓存候选下拉，避免回填时重新请求
  const factories = ref<CpsOption[]>([])
  const areas = ref<CpsOption[]>([])
  const lines = ref<CpsOption[]>([])
  const processes = ref<CpsOption[]>([])
  const level1Categories = ref<CpsOption[]>([])
  const level2Categories = ref<CpsOption[]>([])
  // Phase B1：缓存的可用巡检类型列表。
  const inspectionTypes = ref<CpsInspectionType[]>([])

  const reset = () => {
    location.value = { factory: '', area: '', line: '', process: '' }
    category.value = { categoryL1Id: null, categoryL2Id: null }
    images.value = []
    imagePreviewSources.value = {}
    description.value = ''
    resourceNo.value = ''
    inspectionTypeCode.value = null
    extraData.value = {}
    draftId.value = null
    feedbackEmpNo.value = ''
    feedbackPerson.value = null
    aiSuggestion.value = null
    factories.value = []
    areas.value = []
    lines.value = []
    processes.value = []
    level1Categories.value = []
    level2Categories.value = []
    // inspectionTypes 不清空：避免反复拉接口
  }

  return {
    location,
    category,
    images,
    imagePreviewSources,
    description,
    resourceNo,
    inspectionTypeCode,
    extraData,
    draftId,
    feedbackEmpNo,
    feedbackPerson,
    aiSuggestion,
    factories,
    areas,
    lines,
    processes,
    level1Categories,
    level2Categories,
    inspectionTypes,
    reset,
  }
})
