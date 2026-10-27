<template>
  <input
    v-if="field.type === 'input'"
    class="u-input"
    :value="stringValue"
    :placeholder="field.placeholder"
    :maxlength="field.maxLength"
    @input="onTextInput"
  />
  <view v-else-if="field.type === 'number'" class="u-step">
    <view class="u-step__btn" @click="stepNumber(-1)">−</view>
    <input
      class="u-input u-step__input"
      type="number"
      :value="numberValue === null ? '' : numberValue"
      :placeholder="field.placeholder"
      @input="onNumberInput"
    />
    <view class="u-step__btn" @click="stepNumber(1)">＋</view>
  </view>
  <textarea
    v-else-if="field.type === 'textarea'"
    class="u-textarea"
    :value="stringValue"
    :placeholder="field.placeholder"
    :maxlength="field.maxLength"
    auto-height
    @input="onTextInput"
  />
  <picker
    v-else-if="field.type === 'select'"
    mode="selector"
    :range="optionLabels"
    :value="selectedIndex"
    @change="onSelectChange"
  >
    <view class="u-input u-picker" :class="{ 'u-picker--placeholder': !currentLabel }">
      <text class="u-picker__text">{{ currentLabel || field.placeholder || '请选择' }}</text>
      <text class="u-picker__arrow">▾</text>
    </view>
  </picker>
  <picker v-else-if="field.type === 'date'" mode="date" :value="stringValue" @change="onDateChange">
    <view class="u-input u-picker" :class="{ 'u-picker--placeholder': !stringValue }">
      <text class="u-picker__text">{{ stringValue || field.placeholder || '请选择日期' }}</text>
      <text class="u-picker__arrow">▾</text>
    </view>
  </picker>
  <switch
    v-else-if="field.type === 'switch'"
    :checked="switchChecked"
    color="#14b8a6"
    @change="onSwitchChange"
  />
  <text v-else class="u-field__hint">暂不支持的字段类型：{{ field.type }}</text>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { CpsInspectionFormField } from '@/types/cps'

const props = defineProps<{
  field: CpsInspectionFormField
  value: unknown
}>()

const emit = defineEmits<{
  (e: 'update:value', v: unknown): void
}>()

const stringValue = computed(() => (typeof props.value === 'string' ? props.value : props.value == null ? '' : String(props.value)))
const numberValue = computed(() => (typeof props.value === 'number' ? props.value : null))
const switchChecked = computed(() => props.value === true || props.value === 'true')

const selectOptions = computed(() =>
  (props.field.options ?? []).map((o) => ({
    label: o.label,
    value: String(o.value),
  })),
)

const optionLabels = computed(() => selectOptions.value.map((o) => o.label))

const selectedIndex = computed(() => {
  const index = selectOptions.value.findIndex((o) => o.value === stringValue.value)
  return index < 0 ? 0 : index
})

const currentLabel = computed(() => {
  const hit = selectOptions.value.find((o) => o.value === stringValue.value)
  return hit ? hit.label : ''
})

interface UniChangeEvent<T> {
  detail?: { value?: T }
  target?: { value?: unknown } | null
}

const readText = (event: Event): string => {
  const uniEvent = event as unknown as UniChangeEvent<string>
  const detailValue = uniEvent.detail?.value
  if (typeof detailValue === 'string') return detailValue
  const targetValue = uniEvent.target?.value
  return typeof targetValue === 'string' ? targetValue : ''
}

const readBoolean = (event: Event): boolean => {
  const uniEvent = event as unknown as UniChangeEvent<boolean>
  const detailValue = uniEvent.detail?.value
  if (typeof detailValue === 'boolean') return detailValue
  return Boolean(uniEvent.target?.value)
}

const onTextInput = (event: Event) => {
  emit('update:value', readText(event))
}

const onNumberInput = (event: Event) => {
  const raw = readText(event)
  if (raw === '') {
    emit('update:value', null)
    return
  }
  const parsed = Number(raw)
  emit('update:value', Number.isNaN(parsed) ? null : parsed)
}

const stepNumber = (delta: number) => {
  const base = numberValue.value ?? 0
  emit('update:value', base + delta)
}

const onSelectChange = (event: Event) => {
  const uniEvent = event as unknown as UniChangeEvent<number | string>
  const index = Number(uniEvent.detail?.value)
  const hit = selectOptions.value[index]
  emit('update:value', hit ? hit.value : '')
}

const onDateChange = (event: Event) => {
  emit('update:value', readText(event))
}

const onSwitchChange = (event: Event) => {
  emit('update:value', readBoolean(event))
}
</script>

<style scoped>
/* @inline-uni-controls: 由脚本按页面用到的类内联生成，勿手改 */

/* 该页自包含的 uni 内置控件样式（原全局样式已按页面内联到这里） */
*,
*::before,
*::after {
  box-sizing: border-box;
}

view,
uni-view,
text,
uni-text,
image,
uni-image,
button,
uni-button,
input,
uni-input,
textarea,
uni-textarea,
scroll-view,
uni-scroll-view,
picker,
uni-picker {
  box-sizing: border-box;
}

image,
uni-image {
  display: block;
}

button,
uni-button {
  margin: 0;
  padding: 0;
  background: transparent;
  border: 0;
  border-radius: 0;
  line-height: normal;
  font: inherit;
  color: inherit;
  text-align: center;
}

button::after,
uni-button::after {
  border: none;
}

input,
uni-input,
textarea,
uni-textarea {
  margin: 0;
  padding: 0;
  background: transparent;
  border: 0;
  font: inherit;
  color: inherit;
  outline: none;
}

@keyframes u-pulse {
  0%, 100% { opacity: 0.3; transform: scale(0.8); }
  50% { opacity: 1; transform: scale(1); }
}

switch {
  transform: scale(0.9);
  transform-origin: right center;
}

radio .wx-radio-input,
checkbox .wx-checkbox-input {
  border-radius: 4px;
}

.cps-page {
  --cps-teal: #14b8a6;
  --cps-teal-deep: #0f766e;
  --cps-blue: #2563eb;
  --cps-orange: #f97316;
  --cps-green: #22c55e;
  --cps-red: #ef4444;
  --cps-ink: #0f172a;
  --cps-muted: #64748b;
  --cps-mist: #f0fdfa;
  --cps-cloud: #f8fafc;
  --cps-line: #ccfbf1;
  --cps-ink-2: #334155;
  --cps-faint: #94a3b8;
  --cps-border: #e2e8f0;
  --cps-border-strong: #cbd5e1;
  --cps-info-deep: #1d4ed8;
  --cps-warn-deep: #b45309;
  --cps-success-deep: #15803d;
  --cps-danger-deep: #b91c1c;
  --cps-radius-sm: 8px;
  --cps-radius-md: 10px;
  --cps-radius-lg: 12px;
  --cps-radius-xl: 16px;
  --cps-radius-pill: 999px;
  --cps-shadow-card: 0 1px 2px rgba(15, 23, 42, 0.04), 0 4px 12px rgba(15, 23, 42, 0.04);
  --cps-shadow-hero: 0 12px 32px rgba(15, 23, 42, 0.12);
  --cps-hero-bg: linear-gradient(160deg, #0f766e 0%, #115e59 100%);
  margin: 0;
  background:
    radial-gradient(circle at top left, rgba(20, 184, 166, 0.18), transparent 512px),
    linear-gradient(180deg, #f0fdfa 0%, #f8fafc 42%, #eef2ff 100%);
  color: var(--cps-ink);
  font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif;
  font-size: 16px;
  touch-action: pan-y;
  -webkit-tap-highlight-color: transparent;
}

.u-input,
.u-textarea {
  width: 100%;
  min-height: 44px;
  padding: 10px 14px;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  background: #ffffff;
  font-size: 15px;
  line-height: 22px;
  color: var(--cps-ink);
}

.u-textarea {
  min-height: 96px;
  line-height: 1.6;
}

.u-input--error,
.u-textarea--error {
  border-color: var(--cps-red);
}

.u-input__placeholder {
  color: #94a3b8;
}

.u-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.u-field__label {
  font-size: 14px;
  font-weight: 600;
  color: #334155;
}

.u-field__req {
  color: var(--cps-red);
  margin-left: 2px;
}

.u-field__control {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.u-field__err {
  font-size: 12px;
  color: var(--cps-red);
}

.u-field__hint {
  font-size: 12px;
  color: var(--cps-muted);
}

.u-step {
  display: flex;
  align-items: stretch;
  gap: 6px;
}

.u-step__btn {
  width: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--cps-radius-md, 10px);
  border: 1px solid var(--cps-border-strong, #cbd5e1);
  background: #fff;
  color: var(--cps-ink-2, #334155);
  font-size: 18px;
  font-weight: 700;
  line-height: 1;
}

.u-step__btn:active {
  background: var(--cps-cloud, #f8fafc);
}

.u-step__btn--disabled {
  opacity: 0.45;
}

.u-step__input {
  flex: 1;
  text-align: center;
}

.u-picker {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.u-picker--placeholder {
  color: var(--cps-faint, #94a3b8);
}

.u-picker__arrow {
  font-size: 12px;
  color: var(--cps-faint, #94a3b8);
}

.cps-status-pill {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 10px;
  border-radius: var(--cps-radius-pill);
  font-size: 12px;
  font-weight: 600;
  line-height: 18px;
  white-space: nowrap;
}

.cps-status-pill--blue {
  background: #dbeafe;
  color: #1d4ed8;
}

.cps-status-pill--orange {
  background: #ffedd5;
  color: #c2410c;
}

.cps-status-pill--green {
  background: #dcfce7;
  color: #15803d;
}

.cps-status-pill--teal {
  background: #ccfbf1;
  color: #0f766e;
}

.cps-status-pill--gray {
  background: #e2e8f0;
  color: #475569;
}

.cps-status-pill--red {
  background: #fee2e2;
  color: #b91c1c;
}
/* @end-inline-uni-controls */
</style>
