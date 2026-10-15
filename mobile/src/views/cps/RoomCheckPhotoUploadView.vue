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

    <van-notice-bar
      v-if="error"
      class="cps-photo-notice"
      type="danger"
      :text="error"
      left-icon="warning-o"
    />

    <section class="cps-photo-card">
      <h2 class="cps-photo-card__title">照片</h2>
      <van-uploader
        v-model="fileList"
        :after-read="afterRead"
        :max-count="6"
        :deletable="true"
        accept="image/*"
        capture="environment"
        preview-image
      />
      <p class="cps-photo-card__hint">最多 6 张，超过会覆盖前一帧。</p>
    </section>

    <section class="cps-photo-actions">
      <van-button
        type="primary"
        block
        round
        :loading="uploading"
        :disabled="!fileList.length || uploading"
        @click="submit"
      >
        提交照片
      </van-button>
      <van-button
        type="default"
        block
        round
        plain
        class="cps-photo-actions__secondary"
        @click="goBack"
      >
        返回
      </van-button>
    </section>

    <van-toast v-if="success" class="cps-photo-toast">
      上传成功：{{ uploadedCount }} 张
    </van-toast>
  </main>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { showToast } from 'vant'
import type { UploaderAfterRead, UploaderFileListItem } from 'vant'

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

const fileList = ref<UploaderFileListItem[]>([])
const uploading = ref(false)
const uploadedCount = ref(0)
const error = ref('')
const success = ref(false)

const afterRead: UploaderAfterRead = (item) => {
  // 仅本地记录上传意图；后端逐张提交由 submit() 触发。
  const apply = (entry: UploaderFileListItem) => {
    if (entry) entry.status = 'done'
  }
  if (Array.isArray(item)) {
    item.forEach(apply)
  } else {
    apply(item)
  }
  error.value = ''
}

const submit = async () => {
  error.value = ''
  success.value = false
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
      const source: File | string | undefined = entry.file ?? entry.objectUrl ?? entry.url
      if (!source) continue
      const record = await uploadRoomCheckPhoto(
        props.recordId,
        itemId,
        source,
      )
      lastRecord = record
      uploadedCount.value++
    }
    success.value = true
    showToast({ message: `已提交 ${uploadedCount.value} 张`, type: 'success' })
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
.cps-photo-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 16px;
  background: #f8fafc;
  min-height: 100vh;
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
  border-radius: 14px;
  padding: 16px;
  box-shadow: 0 4px 16px rgba(15, 118, 110, 0.06);
}

.cps-photo-card__title {
  font-size: 16px;
  margin: 0 0 12px;
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

.cps-photo-actions__secondary {
  margin-top: 4px;
}

.cps-photo-toast {
  border-radius: 12px;
}
</style>
