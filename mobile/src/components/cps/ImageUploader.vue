<template>
  <view class="cps-uploader" aria-label="问题图片">
    <view class="cps-uploader__grid">
      <view
        v-for="(image, index) in modelValue"
        :key="image.id"
        class="cps-uploader__preview"
        @click="previewImage(index)"
      >
        <image class="cps-uploader__image" :src="image.url" mode="aspectFill" />
        <button
          v-if="!uploading"
          type="button"
          class="cps-uploader__delete"
          aria-label="删除图片"
          @click.stop="removeByIndex(index)"
        >
          ×
        </button>
      </view>

      <button
        v-if="modelValue.length < max"
        type="button"
        class="cps-uploader__upload"
        :disabled="uploading"
        @click="chooseAndUpload"
      >
        <text class="cps-uploader__plus">+</text>
        <text class="cps-uploader__text">{{ uploading ? '上传中' : '上传图片' }}</text>
      </button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'

import { uploadCpsAttachment, type CpsAttachmentUploadSource } from '@/api/cps/attachment'
import type { CpsUploadedImage } from '@/types/cps'

interface UniTempFileLike {
  path?: string
  file?: File
}

type TempFileCandidate = File | UniTempFileLike

const props = withDefaults(
  defineProps<{
    modelValue: CpsUploadedImage[]
    max?: number
    upload?: (source: CpsAttachmentUploadSource) => Promise<CpsUploadedImage>
  }>(),
  { max: 5 },
)

const emit = defineEmits<{
  'update:modelValue': [value: CpsUploadedImage[]]
  firstImageReady: [fileId: number]
}>()

const uploading = ref<boolean>(false)

const removeImage = (id: number): void => {
  emit(
    'update:modelValue',
    props.modelValue.filter((item) => item.id !== id),
  )
}

const addUploadedImage = (file: CpsUploadedImage): void => {
  if (props.modelValue.length >= props.max) {
    throw new Error(`最多上传 ${props.max} 张图片`)
  }

  const next = [...props.modelValue, file]
  emit('update:modelValue', next)

  if (next.length === 1) {
    emit('firstImageReady', file.id)
  }
}

const removeByIndex = (index: number): void => {
  const next = props.modelValue.filter((_, itemIndex) => itemIndex !== index)
  emit('update:modelValue', next)
}

const chooseAndUpload = async (): Promise<void> => {
  const remaining = Math.max(props.max - props.modelValue.length, 0)
  if (remaining <= 0 || uploading.value) return

  const result = await chooseImages(remaining)
  if (!result) return
  const sources = resolveUploadSources(result).slice(0, remaining)
  if (!sources.length) return

  uploading.value = true
  try {
    const upload = props.upload ?? uploadCpsAttachment
    const next = [...props.modelValue]
    for (const source of sources) {
      const uploaded = await upload(source)
      next.push(uploaded)
    }
    emit('update:modelValue', next)

    if (props.modelValue.length === 0 && next.length > 0) {
      emit('firstImageReady', next[0].id)
    }
  } finally {
    uploading.value = false
  }
}

const chooseImages = (count: number): Promise<UniApp.ChooseImageSuccessCallbackResult | null> => {
  return new Promise((resolve, reject) => {
    uni.chooseImage({
      count,
      sizeType: ['compressed', 'original'],
      sourceType: ['album', 'camera'],
      success: resolve,
      fail(error) {
        if ((error.errMsg || '').toLowerCase().includes('cancel')) {
          resolve(null)
          return
        }
        reject(new Error(error.errMsg || 'chooseImage failed'))
      },
    })
  })
}

const resolveUploadSources = (result: UniApp.ChooseImageSuccessCallbackResult): CpsAttachmentUploadSource[] => {
  const paths = normalizeArray(result.tempFilePaths)
  const tempFiles = normalizeArray(result.tempFiles) as TempFileCandidate[]
  return paths
    .map((path: string, index: number) => {
      const tempFile = tempFiles[index]
      if (isFile(tempFile)) return tempFile
      if (isFile(tempFile?.file)) return tempFile.file
      return tempFile?.path || path
    })
    .filter((source: CpsAttachmentUploadSource | undefined): source is CpsAttachmentUploadSource => Boolean(source))
}

const normalizeArray = <T>(value: T | T[] | undefined): T[] => {
  if (value === undefined) return []
  return Array.isArray(value) ? value : [value]
}

const isFile = (value: unknown): value is File => {
  return typeof File !== 'undefined' && value instanceof File
}

const previewImage = (index: number): void => {
  const urls = props.modelValue.map((image) => image.url)
  if (!urls.length || typeof uni === 'undefined' || typeof uni.previewImage !== 'function') return
  uni.previewImage({
    urls,
    current: urls[index],
  })
}

defineExpose({ addUploadedImage, chooseAndUpload, removeImage })
</script>

<style scoped>
.cps-uploader,
.cps-uploader *,
.cps-uploader *::before,
.cps-uploader *::after {
  box-sizing: border-box;
}

.cps-uploader :deep(*),
.cps-uploader :deep(*::before),
.cps-uploader :deep(*::after) {
  box-sizing: border-box;
}

.cps-uploader {
  width: 100%;
  min-width: 0;
}

.cps-uploader__grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16rpx;
  width: 100%;
  min-width: 0;
}

.cps-uploader__preview,
.cps-uploader__upload {
  position: relative;
  width: 100%;
  aspect-ratio: 1;
  min-width: 0;
  min-height: 168rpx;
  overflow: hidden;
  border-radius: 18rpx;
}

.cps-uploader__preview {
  background: #e2e8f0;
}

.cps-uploader__image {
  display: block;
  width: 100%;
  height: 100%;
}

.cps-uploader__delete {
  position: absolute;
  top: 8rpx;
  right: 8rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44rpx;
  height: 44rpx;
  min-width: 44rpx;
  border: 0;
  border-radius: 999rpx;
  padding: 0;
  background: rgba(15, 23, 42, 0.68);
  color: #ffffff;
  font-size: 32rpx;
  font-weight: 900;
  line-height: 44rpx;
}

.cps-uploader__upload {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  border: 2rpx dashed #67e8f9;
  padding: 18rpx;
  background: #ecfeff;
  color: #0f766e;
}

.cps-uploader__upload[disabled] {
  opacity: 0.68;
}

.cps-uploader__plus {
  font-size: 58rpx;
  font-weight: 400;
  line-height: 58rpx;
}

.cps-uploader__text {
  font-size: 28rpx;
  font-weight: 800;
  line-height: 36rpx;
  text-align: center;
}
</style>
