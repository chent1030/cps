<template>
  <main class="cps-page cps-exec-page">
    <header class="cps-exec-hero">
      <div class="cps-exec-hero__main">
        <p class="cps-exec-hero__eyebrow">辅房点检执行</p>
        <h1>{{ record?.roomName || record?.roomCode || '点检明细' }}</h1>
        <div class="cps-exec-hero__stats">
          <span v-if="record">{{ recordStatusMeta.label }}</span>
          <span v-if="record?.roomCode">房间 {{ record.roomCode }}</span>
          <span v-if="record && record.recordStatus === 'JUDGED'">
            {{ record.score === null || record.score === undefined ? '分数待判定（降级）' : `得分 ${record.score}` }}
          </span>
        </div>
      </div>
      <span v-if="record" class="cps-status-pill" :class="recordStatusMeta.tone">{{ recordStatusMeta.label }}</span>
    </header>

    <p v-if="degraded" class="cps-exec-notice cps-exec-notice--warn" role="status">
      判定服务暂不可用，本次提交按降级处理（结果待 J 线补判定），不阻塞点检流程。
    </p>
    <p v-if="retakeHint" class="cps-exec-notice cps-exec-notice--danger" role="alert">{{ retakeHint }}</p>

    <a-spin :spinning="loading">
      <section v-if="record" class="cps-exec-items" aria-label="点检项明细">
        <article
          v-for="item in record.items"
          :key="item.id"
          class="cps-exec-card"
          :class="{ 'cps-exec-card--locked': locked }"
        >
          <header class="cps-exec-card__head">
            <div class="cps-exec-card__identity">
              <span class="cps-exec-card__code">{{ item.itemCode }}</span>
              <p class="cps-exec-card__content">{{ item.content }}</p>
            </div>
            <span class="cps-status-pill" :class="judgeMeta(item.judgeResult).tone">
              {{ judgeMeta(item.judgeResult).label }}
            </span>
          </header>

          <div class="cps-exec-card__meta">
            <span v-if="item.photoCategory"><b>照片类型</b>{{ item.photoCategory }}</span>
            <span v-if="item.deductScore !== null && item.deductScore !== undefined">
              <b>不合格扣分</b>{{ item.deductScore }}
            </span>
          </div>

          <div class="cps-exec-card__photo">
            <img
              v-if="item.photoUrl"
              class="cps-exec-card__image"
              :src="item.photoUrl"
              alt="点检照片"
            >
            <div v-else class="cps-exec-card__empty">未拍照（判定依据，必须补齐）</div>
            <button
              v-if="!locked"
              type="button"
              class="cps-exec-card__capture"
              :disabled="uploadingItemId !== null"
              @click="captureFor(item)"
            >
              {{ item.photoUrl ? '重拍' : '拍照' }}
            </button>
          </div>

          <p v-if="item.judgeReason" class="cps-exec-card__reason">{{ judgeHint(item) }}</p>
        </article>
      </section>
    </a-spin>

    <footer v-if="record && !locked" class="cps-exec-footer">
      <p class="cps-exec-footer__hint">{{ submitHint }}</p>
      <button
        type="button"
        class="cps-exec-footer__submit"
        :disabled="submitting"
        @click="submitRecord"
      >
        {{ submitting ? '判定中...' : '提交并判定' }}
      </button>
    </footer>

  </main>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'

import {
  getRoomCheckRecord,
  submitRoomCheckRecord,
  uploadRoomCheckPhoto,
} from '@/api/cps/roomCheck'
import type { CpsRoomCheckItem, CpsRoomCheckRecord } from '@/types/cps'

const record = ref<CpsRoomCheckRecord | null>(null)
const loading = ref<boolean>(false)
const submitting = ref<boolean>(false)
const uploadingItemId = ref<number | null>(null)
const retakeHint = ref<string>('')
const fileInputRef = ref<HTMLInputElement | null>(null)
const pendingCaptureItemId = ref<number | null>(null)

/**
 * H5 隐藏 file input 不能写在模板里：uni 编译器会把模板 <input> 改写成 uni-input
 * 内置组件。改为按需 createElement 挂到 body，
 * 行为与模板隐藏 input 完全一致（capture=environment 唤起后置摄像头）。
 */
const ensureFileInput = (): HTMLInputElement | null => {
  if (typeof document === 'undefined') return null
  if (!fileInputRef.value) {
    const input = document.createElement('input')
    input.type = 'file'
    input.accept = 'image/*'
    input.setAttribute('capture', 'environment')
    input.className = 'cps-exec-file-input'
    input.setAttribute('aria-label', '点检照片选择')
    input.style.display = 'none'
    input.addEventListener('change', (event) => handleFileChange(event))
    document.body.appendChild(input)
    fileInputRef.value = input
  }
  return fileInputRef.value
}

onBeforeUnmount(() => {
  fileInputRef.value?.remove()
  fileInputRef.value = null
})

const locked = computed<boolean>(() => record.value?.recordStatus === 'JUDGED')

/** PENDING 降级态：JUDGED + judgeStatus=PENDING + score=null（PRD §24 降级，不阻塞）。 */
const degraded = computed<boolean>(() =>
  record.value?.recordStatus === 'JUDGED' && record.value.judgeStatus === 'PENDING',
)

const RECORD_STATUS_META: Record<string, { label: string; tone: string }> = {
  PENDING: { label: '待执行', tone: 'cps-status-pill--blue' },
  IN_PROGRESS: { label: '进行中', tone: 'cps-status-pill--orange' },
  JUDGED: { label: '已判定', tone: 'cps-status-pill--teal' },
}

const recordStatusMeta = computed(() => {
  const status = record.value?.recordStatus ?? 'PENDING'
  return RECORD_STATUS_META[status] ?? { label: status, tone: 'cps-status-pill--gray' }
})

const JUDGE_META: Record<string, { label: string; tone: string; hint: string }> = {
  PENDING: { label: '待判定', tone: 'cps-status-pill--gray', hint: '' },
  PASS: { label: '合格', tone: 'cps-status-pill--green', hint: '' },
  FAIL: { label: '不合格', tone: 'cps-status-pill--red', hint: '判定意见' },
  TYPE_MISMATCH: { label: '类型不符', tone: 'cps-status-pill--orange', hint: '照片类型不符，须重拍（不计不合格扣分）' },
  UNJUDGEABLE: { label: '无法判定', tone: 'cps-status-pill--orange', hint: 'AI 无法判定，须补拍（不默认通过）' },
}

const judgeMeta = (result: string) => JUDGE_META[result] ?? { label: result, tone: 'cps-status-pill--gray', hint: '' }

const judgeHint = (item: CpsRoomCheckItem) => {
  const meta = judgeMeta(item.judgeResult)
  const prefix = meta.hint ? `${meta.hint}｜` : ''
  return item.judgeReason ? `${prefix}${item.judgeReason}` : prefix.slice(0, -1)
}

const submitHint = computed(() => {
  const items = record.value?.items ?? []
  const missing = items.filter((item) => !item.photoUrl && !item.photoObjectKey)
  const blocked = items.filter(
    (item) => item.judgeResult === 'TYPE_MISMATCH' || item.judgeResult === 'UNJUDGEABLE',
  )
  if (missing.length) return `还有 ${missing.length} 项未拍照，拍照为判定依据，不可跳过`
  if (blocked.length) return `${blocked.length} 项须重拍/补拍后方可提交（不默认通过）`
  return '提交后将同步 AI 判定；判定服务不可用时按降级提交不阻塞'
})

const load = async (recordId: number) => {
  loading.value = true
  try {
    record.value = await getRoomCheckRecord(recordId)
    retakeHint.value = ''
  } finally {
    loading.value = false
  }
}

/** 拍照入口：设备端走 uni.chooseImage；H5 走隐藏 file input（capture=environment）。 */
const captureFor = (item: CpsRoomCheckItem) => {
  if (typeof uni !== 'undefined' && typeof uni.chooseImage === 'function') {
    uni.chooseImage({
      count: 1,
      success: (result) => {
        // uni 类型联合：tempFiles 可能是 File 或数组，统一取第一张
        const files = result.tempFiles as Array<{ path?: string }> | undefined
        const path = result.tempFilePaths?.[0] ?? files?.[0]?.path
        if (path) {
          void uploadFor(item.id, path)
        }
      },
    })
    return
  }
  pendingCaptureItemId.value = item.id
  ensureFileInput()?.click()
}

const uploadFor = async (itemId: number, source: File | string) => {
  if (!record.value) return
  uploadingItemId.value = itemId
  try {
    record.value = await uploadRoomCheckPhoto(record.value.id, itemId, source)
    retakeHint.value = ''
  } catch (caught) {
    retakeHint.value = caught instanceof Error ? caught.message : '照片上传失败，请重试'
  } finally {
    uploadingItemId.value = null
  }
}

const handleFileChange = (event: Event) => {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  const itemId = pendingCaptureItemId.value
  if (file && itemId !== null) {
    void uploadFor(itemId, file)
  }
  pendingCaptureItemId.value = null
  input.value = ''
}

const submitRecord = async () => {
  if (!record.value || submitting.value) return
  submitting.value = true
  retakeHint.value = ''
  try {
    record.value = await submitRoomCheckRecord(record.value.id)
  } catch (caught) {
    const msg = caught instanceof Error ? caught.message : '提交失败，请重试'
    retakeHint.value = msg
    // 类型不符/缺照被阻断：重拉明细展示逐项 judgeResult（须重拍/补拍）
    if (record.value) {
      try {
        record.value = await getRoomCheckRecord(record.value.id)
      } catch {
        // 保留原始错误提示
      }
    }
  } finally {
    submitting.value = false
  }
}

onLoad((query?: Record<string, string | string[] | undefined>) => {
  const raw = Array.isArray(query?.recordId) ? query?.recordId[0] : query?.recordId
  const recordId = Number(raw)
  if (Number.isFinite(recordId) && recordId > 0) {
    void load(recordId)
  }
})
</script>

<style scoped>
.cps-page {
  width: 100%;
  max-width: 720px;
  margin: 0 auto;
  padding: 16px 16px 120px;
}

.cps-exec-page {
  display: grid;
  align-content: start;
  gap: 14px;
}

.cps-exec-hero {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.cps-exec-hero__main {
  display: grid;
  gap: 4px;
}

.cps-exec-hero__eyebrow {
  margin: 0;
  font-size: 12px;
  letter-spacing: 0.24em;
  color: #0f766e;
  font-weight: 700;
}

.cps-exec-hero h1 {
  margin: 0;
  font-size: 20px;
  color: #0f172a;
}

.cps-exec-hero__stats {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  font-size: 13px;
  color: #64748b;
}

.cps-exec-notice {
  margin: 0;
  border-radius: 12px;
  padding: 10px 14px;
  font-size: 13px;
  line-height: 1.5;
}

.cps-exec-notice--warn {
  background: #fffbeb;
  border: 1px solid #fde68a;
  color: #92400e;
}

.cps-exec-notice--danger {
  background: #fef2f2;
  border: 1px solid #fecaca;
  color: #b91c1c;
}

.cps-exec-items {
  display: grid;
  gap: 12px;
}

.cps-exec-card {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  padding: 14px 16px;
  display: grid;
  gap: 10px;
  box-shadow: 0 4px 16px rgba(15, 23, 42, 0.05);
}

.cps-exec-card--locked {
  opacity: 0.92;
}

.cps-exec-card__head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.cps-exec-card__code {
  font-size: 12px;
  letter-spacing: 0.16em;
  color: #0f766e;
  font-weight: 700;
}

.cps-exec-card__content {
  margin: 4px 0 0;
  font-size: 15px;
  color: #0f172a;
  line-height: 1.5;
}

.cps-exec-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  font-size: 13px;
  color: #475569;
}

.cps-exec-card__meta b {
  color: #0f172a;
  margin-right: 6px;
}

.cps-exec-card__photo {
  display: flex;
  align-items: center;
  gap: 12px;
}

.cps-exec-card__image {
  width: 96px;
  height: 96px;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  object-fit: cover;
}

.cps-exec-card__empty {
  flex: 1;
  border: 2px dashed #cbd5e1;
  border-radius: 12px;
  color: #94a3b8;
  font-size: 13px;
  padding: 14px;
  text-align: center;
}

.cps-exec-card__capture {
  border: none;
  border-radius: 12px;
  background: #14b8a6;
  color: #ffffff;
  font-size: 14px;
  font-weight: 600;
  padding: 10px 20px;
  cursor: pointer;
}

.cps-exec-card__capture:disabled {
  opacity: 0.55;
}

.cps-exec-card__reason {
  margin: 0;
  font-size: 13px;
  color: #64748b;
  line-height: 1.5;
}

.cps-exec-footer {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(255, 255, 255, 0.96);
  border-top: 1px solid #e2e8f0;
  padding: 12px 16px calc(12px + env(safe-area-inset-bottom));
  display: grid;
  gap: 8px;
}

.cps-exec-footer__hint {
  margin: 0;
  font-size: 12px;
  color: #64748b;
  line-height: 1.5;
}

.cps-exec-footer__submit {
  border: none;
  border-radius: 12px;
  background: #0f766e;
  color: #ffffff;
  font-size: 16px;
  font-weight: 700;
  padding: 14px 0;
  cursor: pointer;
}

.cps-exec-footer__submit:disabled {
  opacity: 0.6;
}

.cps-exec-file-input {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
}

.cps-status-pill {
  display: inline-flex;
  align-items: center;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

.cps-status-pill--blue { background: #dbeafe; color: #1d4ed8; }
.cps-status-pill--orange { background: #ffedd5; color: #c2410c; }
.cps-status-pill--green { background: #dcfce7; color: #15803d; }
.cps-status-pill--teal { background: #ccfbf1; color: #0f766e; }
.cps-status-pill--red { background: #fee2e2; color: #b91c1c; }
.cps-status-pill--gray { background: #e2e8f0; color: #475569; }
</style>
