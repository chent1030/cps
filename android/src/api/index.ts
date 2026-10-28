/**
 * CPS 接口统一出口。
 *
 * 页面里只需要引入一次：
 *   import { cpsApi } from '@/api/index'
 *
 *   const detail = await cpsApi.getCpsIssueDetail(id)
 *
 * cpsApi 上的方法名与 ./cps/* 各模块的导出一致，只是集中到同一个对象上，
 * 便于统一管理；原来的 `import { xxx } from '@/api/cps/xxx'` 写法仍然可用。
 */
import * as ai from './cps/ai'
import * as attachment from './cps/attachment'
import * as inspectionTask from './cps/inspectionTask'
import * as inspectionType from './cps/inspectionType'
import * as issue from './cps/issue'
import * as issueDraft from './cps/issueDraft'
import * as master from './cps/master'
import * as speech from './cps/speech'

export const cpsApi = {
  // ---------- 问题 ----------
  createCpsIssue: issue.createCpsIssue,
  listCpsIssues: issue.listCpsIssues,
  getCpsIssueDetail: issue.getCpsIssueDetail,
  getCpsIssueCounts: issue.getCpsIssueCounts,
  executeCpsIssueAction: issue.executeCpsIssueAction,

  // ---------- 暂存草稿 ----------
  saveCpsIssueDraft: issueDraft.saveCpsIssueDraft,
  listMyCpsIssueDrafts: issueDraft.listMyCpsIssueDrafts,
  getCpsIssueDraft: issueDraft.getCpsIssueDraft,
  deleteCpsIssueDraft: issueDraft.deleteCpsIssueDraft,

  // ---------- 巡检类型 / 巡检任务 ----------
  listCpsInspectionTypes: inspectionType.listCpsInspectionTypes,
  getCpsInspectionType: inspectionType.getCpsInspectionType,
  getInspectionTypeCheckItems: inspectionType.getInspectionTypeCheckItems,
  listInspectionAreas: inspectionTask.listInspectionAreas,
  startInspection: inspectionTask.startInspection,
  getInspectionRecord: inspectionTask.getInspectionRecord,
  saveInspectionItemResult: inspectionTask.saveInspectionItemResult,
  submitInspection: inspectionTask.submitInspection,
  listMyInProgressInspections: inspectionTask.listMyInProgressInspections,

  // ---------- 基础字典 ----------
  getFactories: master.getFactories,
  getAreas: master.getAreas,
  getLines: master.getLines,
  getProcesses: master.getProcesses,
  getCategories: master.getCategories,
  getFeedbackHandler: master.getFeedbackHandler,
  getReviewer: master.getReviewer,
  searchCpsEmployees: master.searchCpsEmployees,

  // ---------- 附件 / AI / 语音 ----------
  uploadCpsAttachment: attachment.uploadCpsAttachment,
  inspectCpsImage: ai.inspectCpsImage,
  startVoiceRecording: ai.startVoiceRecording,
  stopVoiceRecording: ai.stopVoiceRecording,
  uploadVoiceForText: ai.uploadVoiceForText,
  isVoiceRecording: ai.isVoiceRecording,
  transcribeSpeech: speech.transcribeSpeech,
}

/** cpsApi 的类型，需要给参数/返回值标注时可以引用 */
export type CpsApi = typeof cpsApi

// 常用类型一并从这里导出，页面不必再分别引入
export type { CpsAttachmentUploadSource } from './cps/attachment'
export type { CpsEmployeeOption, CpsOption } from './cps/master'

export default cpsApi
