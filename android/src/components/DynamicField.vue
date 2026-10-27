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
