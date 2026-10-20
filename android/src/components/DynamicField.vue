<template>
  <a-input
    v-if="field.type === 'input'"
    :value="stringValue"
    :placeholder="field.placeholder"
    :maxlength="field.maxLength"
    allow-clear
    @update:value="(v: string) => emit('update:value', v)"
  />
  <a-input-number
    v-else-if="field.type === 'number'"
    :value="numberValue"
    :placeholder="field.placeholder"
    style="width: 100%"
    @update:value="(v: number | null) => emit('update:value', v)"
  />
  <a-textarea
    v-else-if="field.type === 'textarea'"
    :value="stringValue"
    :placeholder="field.placeholder"
    :maxlength="field.maxLength"
    :auto-size="{ minRows: 2, maxRows: 6 }"
    allow-clear
    @update:value="(v: string) => emit('update:value', v)"
  />
  <a-select
    v-else-if="field.type === 'select'"
    :value="stringValue"
    :placeholder="field.placeholder || '请选择'"
    :options="selectOptions"
    allow-clear
    @update:value="(v: any) => emit('update:value', v)"
  />
  <a-date-picker
    v-else-if="field.type === 'date'"
    :value="stringValue"
    :placeholder="field.placeholder || '请选择日期'"
    style="width: 100%"
    value-format="YYYY-MM-DD"
    @update:value="(v: string) => emit('update:value', v)"
  />
  <a-switch
    v-else-if="field.type === 'switch'"
    :checked="switchChecked"
    @update:checked="(v: boolean) => emit('update:value', v)"
  />
  <span v-else>暂不支持的字段类型：{{ field.type }}</span>
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
</script>
