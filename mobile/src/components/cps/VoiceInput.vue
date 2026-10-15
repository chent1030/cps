<template>
  <div class="cps-voice" :data-field="field">
    <div class="cps-voice__field">
      <!-- van-field 而非原生 textarea：uni 会把模板 textarea 编译成 uni-textarea（依赖 uni 运行时） -->
      <van-field
        :model-value="modelValue"
        class="cps-voice__input"
        type="textarea"
        rows="3"
        autosize
        :label="label"
        :placeholder="placeholder"
        :aria-label="label"
        @update:model-value="(value: string) => emit('update:modelValue', value)"
      />
      <!-- 按住说话（PRD §20.2）：松开结束录音并上传转写；转写文本回填上方输入框（用户编辑确认后才随表单提交） -->
      <button
        type="button"
        class="cps-voice__hold"
        :class="{ 'cps-voice__hold--active': state === 'recording' }"
        :disabled="state === 'uploading' || unsupported"
        :aria-label="`按住录入${label}`"
        @pointerdown.prevent="startRecording"
        @pointerup.prevent="stopRecording"
        @pointerleave="stopRecording"
        @pointercancel="stopRecording"
      >
        {{ holdLabel }}
      </button>
    </div>
    <p v-if="statusText" class="cps-voice__status" :data-state="state">{{ statusText }}</p>
    <button
      v-if="state === 'error' && lastRecording"
      type="button"
      class="cps-voice__retry"
      :aria-label="`重试${label}转写`"
      @click="retryLast"
    >
      重试转写
    </button>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { transcribeSpeech } from '@/api/cps/speech'
import type { CpsSpeechField } from '@/types/cps'

/** D-03：语音覆盖三字段 reason / short_term / long_term（对应原因分析/短期措施/长期措施）。 */
const props = defineProps<{
  field: CpsSpeechField
  label: string
  modelValue: string
  submissionId?: string
  placeholder?: string
}>()

const emit = defineEmits<{ (e: 'update:modelValue', value: string): void }>()

const FIELD_PLACEHOLDER: Record<CpsSpeechField, string> = {
  reason: '填写原因分析，或按住麦克风语音录入',
  short_term: '填写短期措施，或按住麦克风语音录入',
  long_term: '填写长期措施，或按住麦克风语音录入',
}

const placeholder = computed(() => props.placeholder ?? FIELD_PLACEHOLDER[props.field])

type VoiceState = 'idle' | 'recording' | 'uploading' | 'error' | 'unsupported'
const state = ref<VoiceState>('idle')
const attempt = ref(0)
const statusText = ref('')
const lastRecording = ref<File | null>(null)

let recorder: MediaRecorder | null = null
let stream: MediaStream | null = null
let chunks: Blob[] = []

const unsupported = computed(() => state.value === 'unsupported')

const holdLabel = computed(() => {
  if (unsupported.value) return '不支持录音'
  if (state.value === 'recording') return '松开结束'
  if (state.value === 'uploading') return '转写中...'
  return '按住说话'
})

const recordingSupported = () =>
  typeof navigator !== 'undefined'
  && !!navigator.mediaDevices
  && typeof navigator.mediaDevices.getUserMedia === 'function'
  && typeof MediaRecorder !== 'undefined'

const teardownRecorder = () => {
  recorder?.stop()
  recorder = null
  stream?.getTracks().forEach((track) => track.stop())
  stream = null
  chunks = []
}

const startRecording = async () => {
  if (state.value === 'recording' || state.value === 'uploading') return
  if (!recordingSupported()) {
    state.value = 'unsupported'
    statusText.value = '当前环境不支持录音，请手工输入'
    return
  }
  try {
    stream = await navigator.mediaDevices.getUserMedia({ audio: true })
    recorder = new MediaRecorder(stream)
    chunks = []
    recorder.ondataavailable = (event) => {
      if (event.data.size > 0) chunks.push(event.data)
    }
    recorder.onstop = () => {
      void finishRecording()
    }
    recorder.start()
    state.value = 'recording'
    statusText.value = ''
  } catch {
    state.value = 'error'
    statusText.value = '无法访问麦克风，请检查权限或手工输入'
    teardownRecorder()
  }
}

const stopRecording = () => {
  if (state.value !== 'recording' || !recorder) return
  recorder.stop()
}

/** 松开后组装音频文件并上传转写（F3 链路：Java multipart → RustFS → C-06 同步转写）。 */
const finishRecording = async () => {
  const blob = new Blob(chunks, { type: recorder?.mimeType || 'audio/webm' })
  teardownRecorder()
  if (blob.size === 0) {
    state.value = 'idle'
    statusText.value = '录音太短，请按住重录'
    return
  }
  attempt.value += 1
  const file = new File([blob], `${props.field}-${attempt.value}.webm`, { type: blob.type })
  lastRecording.value = file
  state.value = 'uploading'
  statusText.value = '录音上传转写中，请稍候...'
  try {
    const result = await transcribeSpeech(file, props.field, props.submissionId, attempt.value)
    if (result.status === 'TRANSCRIBED' && result.text) {
      emit('update:modelValue', result.text)
      state.value = 'idle'
      statusText.value = `已回填${props.label}，请编辑确认后提交（语音不作点检证据）`
    } else {
      state.value = 'error'
      statusText.value = result.fallbackMessage || '未识别到有效语音内容，可重试或手工输入'
    }
  } catch {
    state.value = 'error'
    statusText.value = '转写服务暂不可用，可重试或手工输入'
  }
}

const retryLast = () => {
  if (!lastRecording.value) return
  attempt.value += 1
  state.value = 'uploading'
  statusText.value = '重新转写中...'
  transcribeSpeech(lastRecording.value, props.field, props.submissionId, attempt.value)
    .then((result) => {
      if (result.status === 'TRANSCRIBED' && result.text) {
        emit('update:modelValue', result.text)
        state.value = 'idle'
        statusText.value = `已回填${props.label}，请编辑确认后提交（语音不作点检证据）`
      } else {
        state.value = 'error'
        statusText.value = result.fallbackMessage || '未识别到有效语音内容，可重试或手工输入'
      }
    })
    .catch(() => {
      state.value = 'error'
      statusText.value = '转写服务暂不可用，可重试或手工输入'
    })
}

onBeforeUnmount(teardownRecorder)
</script>

<style scoped>
.cps-voice__field {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}
.cps-voice__input {
  flex: 1;
}
.cps-voice__hold {
  flex: 0 0 auto;
  margin-top: 4px;
  padding: 8px 10px;
  border-radius: 999px;
  border: 1px solid #c7d2fe;
  background: #eef2ff;
  color: #3730a3;
  font-size: 13px;
}
.cps-voice__hold--active {
  background: #3730a3;
  color: #fff;
}
.cps-voice__hold:disabled {
  opacity: 0.6;
}
.cps-voice__status {
  margin: 4px 0 0;
  font-size: 12px;
  color: #6b7280;
}
.cps-voice__status[data-state='error'] {
  color: #b91c1c;
}
.cps-voice__retry {
  margin-top: 4px;
  padding: 4px 10px;
  border-radius: 6px;
  border: 1px solid #d1d5db;
  background: #fff;
  color: #374151;
  font-size: 12px;
}
</style>
