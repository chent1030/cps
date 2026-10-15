<template>
  <main class="cps-page cps-photo-page">
    <header class="cps-photo-hero">
      <div class="cps-photo-hero__main">
        <p class="cps-photo-hero__eyebrow">点检照片</p>
        <h1>上传点检照片</h1>
        <p class="cps-photo-hero__sub">
          关联点检单：<strong>{{ recordId ?? '—' }}</strong>
          <span v-if="itemId">· 项目 #{{ itemId }}</span>
        </p>
      </div>
    </header>

    <a-alert v-if="error" class="cps-photo-notice" type="error" show-icon :message="error" closable @close="error = ''" />

    <section class="cps-photo-card">
      <h2 class="cps-photo-card__title">照片</h2>
      <div class="cps-photo-grid">
        <div v-for="(entry, index) in fileList" :key="entry.key" class="cps-photo-cell">
          <img class="cps-photo-cell__img" :src="entry.previewUrl" alt="待上传照片">
          <button
            type="button"
            class="cps-photo-cell__remove"
            aria-label="移除照片"
            @click="removeAt(index)"
          >
            ×
          </button>
        </div>
        <button
          v-if="fileList.length < maxCount"
          type="button"
          class="cps-photo-add"
          :disabled="picking"
          @click="pickImages"
        >
          <plus-outlined class="cps-photo-add__icon" />
          <span>添加</span>
        </button>
      </div>
      <p class="cps-photo-card__hint">最多 {{ maxCount }} 张；提交后逐张上传至点检单。</p>
    </section>

    <section class="cps-photo-actions">
      <a-button
        type="primary"
        block
        class="cps-photo-actions__primary"
        :loading="uploading"
        :disabled="!fileList.length || uploading"
        @click="submit"
      >
        提交照片
      </a-button>
      <a-button block class="cps-photo-actions__secondary" @click="goBack">
        返回
      </a-button>
    </section>
  </main>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'

import { uploadRoomCheckPhoto } from '@/api/cps/roomCheck'
import type { CpsRoomCheckRecord } from '@/types/cps'

interface Props {
  recordId?: number
  itemId?: number
  /** 提交完成回调。 */
  onSubmitted?: (record: CpsRoomCheckRecord) => void
}

const props = withDefaults(defineProps<Props>(), {
  recordId: undefined,
  itemId: undefined,
  onSubmitted: undefined,
})

interface PhotoEntry {
  key: number
  previewUrl: string
  source: File | string
}

const maxCount = 6
let entrySeq = 0

const fileList = ref<PhotoEntry[]>([])
const uploading = ref(false)
const uploadedCount = ref(0)
const picking = ref(false)
const error = ref('')

const fileInputRef = ref<HTMLInputElement | null>(null)
const pendingPickHandler = ref<((files: File[]) => void) | null>(null)

/** H5 隐藏 file input 动态挂载（模板 input 会被 uni 编译成 uni-input 内置组件）。 */
const ensureFileInput = (): HTMLInputElement | null => {
  if (typeof document === 'undefined') return null
  if (!fileInputRef.value) {
    const input = document.createElement('input')
    input.type = 'file'
    input.accept = 'image/*'
    input.setAttribute('capture', 'environment')
    input.setAttribute('multiple', 'multiple')
    input.setAttribute('aria-label', '点检照片选择')
    input.style.display = 'none'
    input.addEventListener('change', (event) => {
      const target = event.target as HTMLInputElement
      const files = Array.from(target.files ?? [])
      target.value = ''
      pendingPickHandler.value?.(files)
      pendingPickHandler.value = null
    })
    document.body.appendChild(input)
    fileInputRef.value = input
  }
  return fileInputRef.value
}

const pushEntry = (source: File | string, previewUrl: string) => {
  if (fileList.value.length >= maxCount) return
  fileList.value.push({ key: ++entrySeq, previewUrl, source })
}

/** 选图入口：设备端走 uni.chooseImage；H5 走隐藏 file input。 */
const pickImages = () => {
  const remaining = maxCount - fileList.value.length
  if (remaining <= 0) return
  if (typeof uni !== 'undefined' && typeof uni.chooseImage === 'function') {
    picking.value = true
    uni.chooseImage({
      count: remaining,
      success: (result) => {
        const files = result.tempFiles as Array<{ path?: string; file?: File }> | undefined
        const paths: string[] = (result.tempFilePaths as string[] | undefined) ?? []
        if (Array.isArray(files)) {
          files.slice(0, remaining).forEach((file, index) => {
            const path = paths[index] ?? file.path
            if (path) pushEntry(file.file ?? path, path)
          })
        } else {
          paths.slice(0, remaining).forEach((path: string) => pushEntry(path, path))
        }
        error.value = ''
      },
      complete: () => {
        picking.value = false
      },
    })
    return
  }
  const input = ensureFileInput()
  if (!input) return
  pendingPickHandler.value = (files) => {
    files.slice(0, remaining).forEach((file) => {
      pushEntry(file, URL.createObjectURL(file))
    })
    error.value = ''
  }
  input.click()
}

const removeAt = (index: number) => {
  const entry = fileList.value[index]
  if (entry && typeof entry.source !== 'string') {
    URL.revokeObjectURL(entry.previewUrl)
  }
  fileList.value.splice(index, 1)
}

const submit = async () => {
  error.value = ''
  if (!props.recordId) {
    error.value = '缺少点检单 recordId，无法提交'
    return
  }
  const itemId = props.itemId ?? 0
  if (itemId <= 0) {
    error.value = '缺少点检项目 itemId，无法提交'
    return
  }
  uploading.value = true
  uploadedCount.value = 0
  try {
    let lastRecord: CpsRoomCheckRecord | undefined
    for (const entry of fileList.value) {
      const record = await uploadRoomCheckPhoto(props.recordId, itemId, entry.source)
      lastRecord = record
      uploadedCount.value++
    }
    fileList.value = []
    void message.success(`已提交 ${uploadedCount.value} 张`)
    if (lastRecord && props.onSubmitted) {
      props.onSubmitted(lastRecord)
    }
  } catch (caught) {
    error.value =
      caught instanceof Error ? caught.message : '上传失败，请稍后重试'
  } finally {
    uploading.value = false
  }
}

const goBack = () => {
  if (typeof uni !== 'undefined' && typeof uni.navigateBack === 'function') {
    uni.navigateBack({ delta: 1 })
  } else if (typeof history !== 'undefined') {
    history.back()
  }
}
</script>

<style scoped>
.cps-page {
  width: 100%;
  max-width: 720px;
  margin: 0 auto;
  padding: 16px 16px 40px;
}

.cps-photo-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.cps-photo-hero {
  background: linear-gradient(135deg, #14b8a6 0%, #0f766e 100%);
  color: white;
  border-radius: 16px;
  padding: 20px;
}

.cps-photo-hero__eyebrow {
  font-size: 12px;
  opacity: 0.85;
  margin: 0 0 4px;
}

.cps-photo-hero__main h1 {
  font-size: 22px;
  margin: 0 0 8px;
}

.cps-photo-hero__sub {
  margin: 0;
  font-size: 13px;
  opacity: 0.9;
}

.cps-photo-notice {
  border-radius: 12px;
}

.cps-photo-card {
  background: white;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  padding: 16px;
  box-shadow: 0 4px 16px rgba(15, 118, 110, 0.06);
}

.cps-photo-card__title {
  font-size: 16px;
  margin: 0 0 12px;
  color: #0f172a;
}

.cps-photo-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}

.cps-photo-cell {
  position: relative;
  aspect-ratio: 1;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid #e2e8f0;
}

.cps-photo-cell__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.cps-photo-cell__remove {
  position: absolute;
  top: 4px;
  right: 4px;
  width: 22px;
  height: 22px;
  border: none;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.6);
  color: #fff;
  font-size: 14px;
  line-height: 1;
  cursor: pointer;
}

.cps-photo-add {
  aspect-ratio: 1;
  border: 2px dashed #99f6e4;
  border-radius: 12px;
  background: #f0fdfa;
  color: #0f766e;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}

.cps-photo-add__icon {
  font-size: 22px;
}

.cps-photo-add:disabled {
  opacity: 0.55;
}

.cps-photo-card__hint {
  font-size: 12px;
  color: #64748b;
  margin: 8px 0 0;
}

.cps-photo-actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.cps-photo-actions__primary {
  background: #0f766e;
  border: none;
  font-weight: 700;
  height: 44px;
  border-radius: 999px;
}

.cps-photo-actions__secondary {
  border-radius: 999px;
  height: 40px;
}
</style>
