<template>
  <main class="cps-page cps-execute-page">
    <a-spin :spinning="loading">
      <template v-if="record">
        <header class="cps-hero">
          <div class="cps-hero__main">
            <p class="cps-hero__eyebrow">巡检执行 · #{{ record.id }}</p>
            <h1>{{ record.areaName || '整体巡检' }}</h1>
            <div class="cps-hero__stats">
              <span>{{ record.typeCode }}</span>
              <span>{{ statusLabel(record.recordStatus) }}</span>
              <span v-if="record.areaCode">{{ record.areaCode }}</span>
              <span>进度 {{ doneCount }}/{{ items.length }}</span>
              <span v-if="record.score != null">得分 {{ record.score }}</span>
            </div>
          </div>
          <div class="cps-hero__side">
            <a-tag :color="readonly ? 'green' : 'blue'" class="cps-hero__badge">
              {{ readonly ? '已锁定' : '执行中' }}
            </a-tag>
            <a-button v-if="!readonly" size="small" class="cps-hero__switch" @click="switchType">
              切换巡检类型
            </a-button>
            <a-button v-else size="small" class="cps-hero__switch" @click="backToList">
              返回任务列表
            </a-button>
          </div>
        </header>

        <!-- 点检项逐项执行 -->
        <a-card
          v-for="(item, index) in items"
          :key="item.id"
          class="cps-card cps-item-card"
          :class="{ 'cps-item-card--done': isItemDone(item), 'cps-item-card--fail': item.passResult === 'FAIL' }"
          :body-style="{ padding: '16px' }"
        >
          <template #title>
            <div class="cps-card-title">
              <div>
                <p class="cps-card-title__sub">第 {{ index + 1 }} 项 · {{ item.itemCode }}</p>
                <h2 class="cps-card-title__main">{{ item.content }}</h2>
              </div>
              <div class="cps-item-badges">
                <a-tag v-for="t in splitTypes(item.checkTypes)" :key="t" :color="typeColor(t)">{{ typeLabel(t) }}</a-tag>
                <a-tag v-if="item.deductScore" color="red">扣 {{ item.deductScore }}</a-tag>
              </div>
            </div>
          </template>

          <!-- 拍照判定条件提示 -->
          <p v-if="hasType(item, 'PHOTO') && item.photoCriteria" class="cps-item-criteria">
            📷 判定条件：{{ item.photoCriteria }}（至少 {{ item.photoMinCount ?? 1 }} 张）
          </p>

          <!-- 是否合格 -->
          <div v-if="hasType(item, 'PASS_FAIL')" class="cps-item-row">
            <span class="cps-item-label">是否合格</span>
            <a-radio-group
              :value="local[item.id]?.passResult ?? item.passResult ?? ''"
              button-style="solid"
              :disabled="readonly || savingItem === item.id"
              @change="(e: any) => onPassChange(item, e.target.value)"
            >
              <a-radio-button value="PASS">✓ 合格</a-radio-button>
              <a-radio-button value="FAIL">✗ 不合格</a-radio-button>
            </a-radio-group>
          </div>

          <!-- 文本说明 -->
          <div v-if="hasType(item, 'TEXT')" class="cps-item-row cps-item-row--stack">
            <span class="cps-item-label">文本说明</span>
            <a-textarea
              :value="local[item.id]?.textValue ?? item.textValue ?? ''"
              :rows="2"
              :maxlength="500"
              :disabled="readonly"
              :placeholder="textHint(item)"
              @change="(e: any) => onTextChange(item, e.target.value)"
              @blur="() => void persistItem(item)"
            />
          </div>

          <!-- 拍照 -->
          <div v-if="hasType(item, 'PHOTO')" class="cps-item-row cps-item-row--stack">
            <span class="cps-item-label">
              现场照片
              <em :class="photoCount(item) >= (item.photoMinCount ?? 1) ? 'cps-count cps-count--ok' : 'cps-count cps-count--lack'">
                {{ photoCount(item) }}/{{ item.photoMinCount ?? 1 }}
              </em>
            </span>
            <div class="cps-photo-grid">
              <div v-for="(p, pi) in photoList(item)" :key="pi" class="cps-photo-tile">
                <img :src="p.url" :alt="p.name" @click="previewPhoto(item, pi)" />
                <span v-if="!readonly" class="cps-photo-del" @click.stop="removePhoto(item, pi)">×</span>
              </div>
              <label v-if="!readonly" class="cps-photo-add">
                <input type="file" accept="image/*" multiple hidden @change="(e: any) => onPhotoPick(item, e)" />
                <span class="cps-photo-add__plus">＋</span>
                <span class="cps-photo-add__text">{{ uploadingItem === item.id ? '上传中' : '拍照/选择' }}</span>
              </label>
            </div>
          </div>

          <div v-if="!readonly && (hasType(item, 'TEXT') || hasType(item, 'PHOTO'))" class="cps-item-save">
            <a-button size="small" :loading="savingItem === item.id" @click="persistItem(item)">保存本项</a-button>
            <span v-if="savedTip[item.id]" class="cps-saved-tip">已保存</span>
          </div>
        </a-card>

        <!-- 提交栏 -->
        <div v-if="!readonly" class="cps-submit-bar">
          <a-button
            type="primary"
            size="large"
            block
            :loading="submitting"
            class="cps-submit-btn"
            @click="submit"
          >
            提交巡检（{{ doneCount }}/{{ items.length }} 项完成）
          </a-button>
        </div>

        <!-- 已判定：得分横幅 -->
        <a-result
          v-else
          status="success"
          :title="`巡检已完成，得分 ${record.score ?? '-'}`"
          :subtitle="`提交时间：${formatTime(record.submittedAt)}`"
          class="cps-done-result"
        />
      </template>
      <a-empty v-else-if="!loading" description="记录不存在或已删除" />
    </a-spin>
  </main>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { message } from 'ant-design-vue'

import { uploadCpsAttachment } from '@/api/cps/attachment'
import {
  getInspectionRecord,
  saveInspectionItemResult,
  submitInspection,
} from '@/api/cps/inspectionTask'
import type { CpsInspectionRecord, CpsInspectionRecordItem } from '@/types/cps'

interface PhotoEntry {
  url: string
  name: string
}

const recordId = ref<number>(0)
const loading = ref(false)
const submitting = ref(false)
const savingItem = ref<number | null>(null)
const uploadingItem = ref<number | null>(null)

const record = ref<CpsInspectionRecord | null>(null)
const items = ref<CpsInspectionRecordItem[]>([])

/** 本地编辑缓冲：itemId → 未保存的修改 */
const local = reactive<Record<number, { passResult?: string | null; textValue?: string | null; photos?: PhotoEntry[] }>>({})
const savedTip = reactive<Record<number, boolean>>({})

const readonly = computed(() => record.value?.recordStatus === 'JUDGED' || record.value?.recordStatus === 'AWAIT_CONFIRM')

const splitTypes = (types?: string) => (types ?? '').split(',').filter(Boolean)
const hasType = (item: CpsInspectionRecordItem, t: string) => splitTypes(item.checkTypes).includes(t)
const typeLabel = (t: string) => ({ PASS_FAIL: '合格判定', TEXT: '文本', PHOTO: '拍照' }[t] ?? t)
const typeColor = (t: string) => (t === 'PHOTO' ? 'orange' : t === 'TEXT' ? 'blue' : 'green')
const textHint = (item: CpsInspectionRecordItem) => `请描述现场情况（${item.itemCode}）`

const statusLabel = (s: string) =>
  ({ PENDING: '待执行', IN_PROGRESS: '执行中', AWAIT_CONFIRM: '待确认', JUDGED: '已判定' }[s] ?? s)

const formatTime = (t?: string | null) => (t ? t.replace('T', ' ').slice(0, 16) : '-')

/**
 * Phase D3：切换巡检类型。
 * 当前记录保持 IN_PROGRESS 落库（不丢已填内容），跳回类型选择页；
 * 之后可在「我的问题 → 我的草稿」或再次新建时恢复本记录。
 */
const switchType = () => {
  if (typeof uni !== 'undefined' && typeof uni.showModal === 'function') {
    uni.showModal({
      title: '切换巡检类型',
      content: '当前记录将保存为「执行中」，已填内容不会丢失，可稍后从巡检执行页继续。确认切换？',
      confirmText: '切换',
      cancelText: '留下继续',
      success: (res) => {
        if (res.confirm) {
          uni.redirectTo({ url: '/views/cps/IssueCreateView' })
        }
      },
    })
    return
  }
  uni.redirectTo({ url: '/views/cps/IssueCreateView' })
}

const backToList = () => {
  const pages = typeof getCurrentPages === 'function' ? getCurrentPages() : []
  if (pages.length > 1) {
    uni.navigateBack()
  } else {
    uni.redirectTo({ url: '/views/cps/IssueListView' })
  }
}

/** 照片列表：本地缓冲优先，其次远端快照（photoObjectKey 逗号分隔） */
const photoList = (item: CpsInspectionRecordItem): PhotoEntry[] => {
  const buffered = local[item.id]?.photos
  if (buffered) return buffered
  if (!item.photoObjectKey) return []
  const keys = item.photoObjectKey.split(',').filter(Boolean)
  const names = (item.photoFileName ?? '').split(',').filter(Boolean)
  return keys.map((k, i) => ({ url: k, name: names[i] ?? `照片${i + 1}` }))
}

const photoCount = (item: CpsInspectionRecordItem) => photoList(item).length

const isItemDone = (item: CpsInspectionRecordItem) => {
  const needPass = hasType(item, 'PASS_FAIL')
  const needText = hasType(item, 'TEXT')
  const needPhoto = hasType(item, 'PHOTO')
  const pass = local[item.id]?.passResult ?? item.passResult
  const text = local[item.id]?.textValue ?? item.textValue
  if (needPass && !pass) return false
  if (needText && !(text ?? '').trim()) return false
  if (needPhoto && photoCount(item) < (item.photoMinCount ?? 1)) return false
  return true
}

const doneCount = computed(() => items.value.filter(isItemDone).length)

const load = async () => {
  if (!recordId.value) return
  loading.value = true
  try {
    const detail = await getInspectionRecord(recordId.value)
    record.value = detail.record
    items.value = detail.items
  } catch (error) {
    message.error(error instanceof Error ? error.message : '加载巡检记录失败')
  } finally {
    loading.value = false
  }
}

// ===== 单项编辑 =====
const onPassChange = (item: CpsInspectionRecordItem, value: string) => {
  local[item.id] = { ...local[item.id], passResult: value }
  void persistItem(item)
}

const onTextChange = (item: CpsInspectionRecordItem, value: string) => {
  local[item.id] = { ...local[item.id], textValue: value }
}

const onPhotoPick = async (item: CpsInspectionRecordItem, e: Event) => {
  const input = e.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  input.value = ''
  if (!files.length) return
  uploadingItem.value = item.id
  try {
    const current = [...photoList(item)]
    for (const file of files) {
      const uploaded = await uploadCpsAttachment(file)
      current.push({ url: uploaded.url, name: uploaded.name })
    }
    local[item.id] = { ...local[item.id], photos: current }
    await persistItem(item)
  } catch (error) {
    message.error(error instanceof Error ? error.message : '照片上传失败')
  } finally {
    uploadingItem.value = null
  }
}

const removePhoto = async (item: CpsInspectionRecordItem, index: number) => {
  const current = photoList(item).filter((_, i) => i !== index)
  local[item.id] = { ...local[item.id], photos: current }
  await persistItem(item)
}

const previewPhoto = (item: CpsInspectionRecordItem, index: number) => {
  const urls = photoList(item).map((p) => p.url)
  if (typeof uni !== 'undefined' && typeof uni.previewImage === 'function') {
    uni.previewImage({ urls, current: urls[index] })
  }
}

/** 保存单项到服务端（合格/文本/照片一起提交） */
const persistItem = async (item: CpsInspectionRecordItem) => {
  if (readonly.value) return
  savingItem.value = item.id
  try {
    const photos = photoList(item)
    await saveInspectionItemResult(item.id, {
      passResult: (local[item.id]?.passResult ?? item.passResult ?? null) as 'PASS' | 'FAIL' | null,
      textValue: local[item.id]?.textValue ?? item.textValue ?? null,
      photoObjectKey: photos.length ? photos.map((p) => p.url).join(',') : null,
      photoFileName: photos.length ? photos.map((p) => p.name).join(',') : null,
      photoCount: photos.length,
    })
    // 合并回本地 item，清缓冲
    const saved = local[item.id]
    if (saved?.passResult) item.passResult = saved.passResult as 'PASS' | 'FAIL'
    if (saved?.textValue != null) item.textValue = saved.textValue
    if (saved?.photos) {
      item.photoObjectKey = saved.photos.map((p) => p.url).join(',')
      item.photoFileName = saved.photos.map((p) => p.name).join(',')
      item.photoCount = saved.photos.length
    }
    delete local[item.id]
    savedTip[item.id] = true
    setTimeout(() => { savedTip[item.id] = false }, 1500)
  } catch (error) {
    message.error(error instanceof Error ? error.message : '保存失败')
  } finally {
    savingItem.value = null
  }
}

// ===== 提交 =====
const submit = async () => {
  if (!record.value || submitting.value) return
  const missing = items.value.filter((i) => !isItemDone(i))
  if (missing.length) {
    message.warning(`还有 ${missing.length} 项未完成：${missing[0].content.slice(0, 20)}…`)
    return
  }
  submitting.value = true
  try {
    const result = await submitInspection(record.value.id)
    record.value = result
    message.success(`提交成功，得分 ${result.score ?? '-'}`)
  } catch (error) {
    message.error(error instanceof Error ? error.message : '提交失败')
  } finally {
    submitting.value = false
  }
}

onLoad((query?: Record<string, string | string[] | undefined>) => {
  const raw = Array.isArray(query?.recordId) ? query?.recordId[0] : query?.recordId
  recordId.value = Number(raw)
  void load()
})
</script>

<style scoped>
.cps-page {
  width: 100%;
  max-width: 960px;
  margin: 0 auto;
  padding: 16px 20px 120px;
}

.cps-hero {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  border-radius: var(--cps-radius-xl);
  padding: 20px 24px;
  background: var(--cps-hero-bg);
  box-shadow: var(--cps-shadow-hero);
  color: #fff;
  margin-bottom: 16px;
}
.cps-hero__main { display: grid; gap: 6px; }
.cps-hero__eyebrow { margin: 0; color: rgba(255, 255, 255, 0.82); font-size: 12px; font-weight: 700; letter-spacing: 0.18em; }
.cps-hero h1 { margin: 0; font-size: 22px; font-weight: 800; }
.cps-hero__stats { display: flex; flex-wrap: wrap; gap: 8px; }
.cps-hero__stats span {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  border-radius: var(--cps-radius-pill);
  padding: 0 10px;
  background: rgba(255, 255, 255, 0.16);
  font-size: 13px;
  font-weight: 700;
}
.cps-hero__badge { align-self: flex-start; border-radius: 999px !important; font-weight: 800 !important; }

/* Phase D3：hero 右侧（状态徽标 + 切换类型/返回按钮） */
.cps-hero__side {
  display: grid;
  justify-items: end;
  gap: 8px;
}

.cps-hero__switch {
  border-radius: var(--cps-radius-pill) !important;
  background: rgba(255, 255, 255, 0.16) !important;
  border: 1px solid rgba(255, 255, 255, 0.55) !important;
  color: #fff !important;
  font-size: 12px !important;
  font-weight: 700 !important;
}

.cps-card {
  border-radius: var(--cps-radius-lg);
  border: 1px solid var(--cps-border);
  box-shadow: var(--cps-shadow-card);
}
.cps-item-card + .cps-item-card { margin-top: 12px; }
.cps-item-card--done { border-left: 4px solid #22c55e; }
.cps-item-card--fail { border-left: 4px solid #ef4444; }

.cps-card-title { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
.cps-card-title__sub { margin: 0; color: var(--cps-teal-deep); font-size: 12px; font-weight: 800; }
.cps-card-title__main { margin: 2px 0 0; color: var(--cps-ink); font-size: 16px; font-weight: 800; }
.cps-item-badges { display: flex; flex-wrap: wrap; gap: 4px; justify-content: flex-end; }

.cps-item-criteria {
  margin: 0 0 10px;
  padding: 8px 10px;
  border-radius: var(--cps-radius-md);
  background: #fff7ed;
  color: #9a3412;
  font-size: 13px;
  line-height: 19px;
}

.cps-item-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 0;
}
.cps-item-row--stack { display: grid; gap: 6px; }
.cps-item-label { color: #0f172a; font-size: 14px; font-weight: 700; flex-shrink: 0; }

.cps-count { font-style: normal; margin-left: 6px; font-size: 12px; font-weight: 800; }
.cps-count--ok { color: #16a34a; }
.cps-count--lack { color: #ef4444; }

.cps-photo-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(96px, 1fr));
  gap: 8px;
}
.cps-photo-tile {
  position: relative;
  width: 100%;
  aspect-ratio: 1;
  border-radius: 10px;
  overflow: hidden;
  background: #e2e8f0;
}
.cps-photo-tile img { width: 100%; height: 100%; object-fit: cover; }
.cps-photo-del {
  position: absolute;
  top: 4px;
  right: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.68);
  color: #fff;
  font-size: 14px;
  font-weight: 900;
}
.cps-photo-add {
  display: grid;
  align-items: center;
  justify-items: center;
  gap: 2px;
  width: 100%;
  aspect-ratio: 1;
  border-radius: 10px;
  border: 1px dashed #99f6e4;
  background: var(--cps-mist);
  color: #0f766e;
  cursor: pointer;
}
.cps-photo-add__plus { font-size: 22px; line-height: 1; }
.cps-photo-add__text { font-size: 12px; font-weight: 700; }

.cps-item-save { display: flex; align-items: center; gap: 8px; margin-top: 8px; }
.cps-saved-tip { color: #16a34a; font-size: 12px; font-weight: 700; }

.cps-submit-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 99;
  max-width: 960px;
  margin: 0 auto;
  padding: 12px 20px max(12px, env(safe-area-inset-bottom));
  background: linear-gradient(180deg, rgba(248, 250, 252, 0), #f8fafc 40%);
}
.cps-submit-btn {
  height: 48px !important;
  border-radius: var(--cps-radius-pill) !important;
  background: var(--cps-teal-deep) !important;
  border: none !important;
  font-size: 16px !important;
  font-weight: 700 !important;
}

.cps-done-result { margin-top: 16px; }

/* ===== antd 组件品牌色（只在本页作用域内生效） ===== */
.cps-execute-page :deep(.ant-btn-primary:not(:disabled)) {
  background: var(--cps-teal-deep);
  border-color: var(--cps-teal-deep);
  box-shadow: none;
}

.cps-execute-page :deep(.ant-radio-button-wrapper-checked:not(.ant-radio-button-wrapper-disabled)) {
  background: var(--cps-teal-deep);
  border-color: var(--cps-teal-deep);
  color: #fff;
}

.cps-execute-page :deep(.ant-radio-button-wrapper-checked:not(.ant-radio-button-wrapper-disabled)::before) {
  background-color: var(--cps-teal-deep);
}

.cps-execute-page :deep(.ant-radio-checked .ant-radio-inner) {
  border-color: var(--cps-teal-deep);
}

.cps-execute-page :deep(.ant-radio-checked .ant-radio-inner::after) {
  background-color: var(--cps-teal-deep);
}

.cps-execute-page :deep(.ant-spin-dot-item) {
  background-color: var(--cps-teal-deep);
}
</style>
