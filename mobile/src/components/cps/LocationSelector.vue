<template>
  <view class="cps-detail-card cps-selector-card">
    <view class="cps-card-title">
      <view>
        <text class="cps-card-title__eyebrow">位置选择</text>
        <text class="cps-card-title__heading">工厂 / 区域 / 拉线 / 工序</text>
      </view>
    </view>

    <view class="cps-selector-list">
      <view class="cps-selector-row">
        <text class="cps-selector-label">工厂</text>
        <view class="cps-choice-grid cps-choice-grid--three" role="radiogroup" aria-label="工厂">
          <button
            v-for="item in factories"
            :key="item.value"
            type="button"
            role="radio"
            :aria-checked="modelValue.factory === String(item.value)"
            class="cps-choice-button"
            :class="{ 'is-selected': modelValue.factory === String(item.value) }"
            @click="selectFactory(item.value)"
          >
            {{ item.label }}
          </button>
          <text v-if="!factories.length" class="cps-muted-text">暂无工厂数据</text>
        </view>
      </view>

      <view class="cps-selector-row">
        <text class="cps-selector-label">区域</text>
        <view class="cps-choice-grid cps-choice-grid--two" role="radiogroup" aria-label="区域">
          <button
            v-for="item in areas"
            :key="item.value"
            type="button"
            role="radio"
            :aria-checked="modelValue.area === String(item.value)"
            class="cps-choice-button"
            :class="{ 'is-selected': modelValue.area === String(item.value) }"
            @click="selectArea(item.value)"
          >
            {{ item.label }}
          </button>
          <text v-if="!areas.length" class="cps-muted-text">先选择工厂</text>
        </view>
      </view>

      <view v-if="showLineProcess" class="cps-selector-row">
        <text class="cps-selector-label">拉线</text>
        <view class="cps-choice-grid cps-choice-grid--two" role="radiogroup" aria-label="拉线">
          <button
            v-for="item in lines"
            :key="item.value"
            type="button"
            role="radio"
            :aria-checked="modelValue.line === String(item.value)"
            class="cps-choice-button"
            :class="{ 'is-selected': modelValue.line === String(item.value) }"
            @click="selectLine(item.value)"
          >
            {{ item.label }}
          </button>
          <text v-if="!lines.length" class="cps-muted-text">暂无拉线数据</text>
        </view>
      </view>

      <view v-if="showLineProcess" class="cps-selector-row">
        <text class="cps-selector-label">工序</text>
        <view class="cps-choice-grid cps-choice-grid--two" role="radiogroup" aria-label="工序">
          <button
            v-for="item in processes"
            :key="item.value"
            type="button"
            role="radio"
            :aria-checked="modelValue.process === String(item.value)"
            class="cps-choice-button"
            :class="{ 'is-selected': modelValue.process === String(item.value) }"
            @click="selectProcess(item.value)"
          >
            {{ item.label }}
          </button>
          <text v-if="!processes.length" class="cps-muted-text">暂无工序数据</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import { getAreas, getFactories, getLines, getProcesses, type CpsOption } from '@/api/cps/master'

interface LocationModel {
  factory: string
  area: string
  line: string
  process: string
}

const props = defineProps<{
  modelValue: LocationModel
}>()

const emit = defineEmits<{
  'update:modelValue': [value: LocationModel]
}>()

const factories = ref<CpsOption[]>([])
const areas = ref<CpsOption[]>([])
const lines = ref<CpsOption[]>([])
const processes = ref<CpsOption[]>([])

const modelValue = computed<LocationModel>(() => props.modelValue)
const showLineProcess = computed<boolean>(() => Boolean(props.modelValue.area))

const resetArea = (): void => {
  areas.value = []
  lines.value = []
  processes.value = []
}

const resetLineAndProcess = (): void => {
  lines.value = []
  processes.value = []
}

const updateModel = (partial: Partial<LocationModel>): void => {
  emit('update:modelValue', {
    ...props.modelValue,
    ...partial,
  })
}

const selectFactory = async (factoryValue: CpsOption['value']): Promise<void> => {
  resetArea()
  const factory = String(factoryValue)
  updateModel({
    factory,
    area: '',
    line: '',
    process: '',
  })
  areas.value = await getAreas(factory)
}

const selectArea = async (value: CpsOption['value']): Promise<void> => {
  resetLineAndProcess()

  const area = String(value)
  updateModel({
    area,
    line: '',
    process: '',
  })
  if (!area) return
  lines.value = await getLines(props.modelValue.factory, area)
  processes.value = await getProcesses(props.modelValue.factory, area)
}

const selectLine = async (value: CpsOption['value']): Promise<void> => {
  const line = String(value)
  updateModel({
    line,
    process: '',
  })

  if (!props.modelValue.area) return
  processes.value = await getProcesses(props.modelValue.factory, props.modelValue.area, line || undefined)
}

const selectProcess = (value: CpsOption['value']): void => {
  updateModel({
    process: String(value),
  })
}

onMounted(async (): Promise<void> => {
  factories.value = await getFactories()
})
</script>

<style scoped>
.cps-detail-card,
.cps-detail-card *,
.cps-detail-card *::before,
.cps-detail-card *::after {
  box-sizing: border-box;
}

.cps-detail-card :deep(*),
.cps-detail-card :deep(*::before),
.cps-detail-card :deep(*::after) {
  box-sizing: border-box;
}

.cps-detail-card {
  display: grid;
  width: 100%;
  min-width: 0;
  max-width: 100%;
  gap: 24rpx;
  overflow: hidden;
  border: 2rpx solid rgba(20, 184, 166, 0.16);
  border-radius: 20rpx;
  padding: 30rpx;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 18rpx 52rpx rgba(15, 23, 42, 0.08);
}

.cps-selector-card {
  padding: 30rpx 0 0;
}

.cps-card-title {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 18rpx;
  min-width: 0;
  padding: 0 30rpx;
}

.cps-card-title__eyebrow {
  display: block;
  margin: 0;
  color: #0f766e;
  font-size: 26rpx;
  font-weight: 900;
  line-height: 36rpx;
}

.cps-card-title__heading {
  display: block;
  margin: 4rpx 0 0;
  color: #0f172a;
  font-size: 36rpx;
  font-weight: 950;
  line-height: 46rpx;
}

.cps-selector-list {
  display: grid;
  width: 100%;
  min-width: 0;
  border-top: 2rpx solid #ccfbf1;
}

.cps-selector-row {
  display: grid;
  grid-template-columns: 150rpx minmax(0, 1fr);
  gap: 20rpx;
  width: 100%;
  min-width: 0;
  padding: 28rpx 30rpx;
  border-bottom: 2rpx solid #f1f5f9;
}

.cps-selector-row:last-child {
  border-bottom: 0;
}

.cps-selector-label {
  padding-top: 20rpx;
  color: #0f172a;
  font-size: 30rpx;
  font-weight: 900;
  line-height: 42rpx;
}

.cps-choice-grid {
  display: grid;
  width: 100%;
  min-width: 0;
  max-width: 100%;
  gap: 16rpx;
  overflow: hidden;
}

.cps-choice-grid--three,
.cps-choice-grid--two {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.cps-choice-button {
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 0;
  max-width: 100%;
  min-height: 96rpx;
  border: 2rpx solid #cbd5e1;
  border-radius: 18rpx;
  padding: 16rpx 18rpx;
  background: #ffffff;
  color: #334155;
  font-size: 32rpx;
  font-weight: 800;
  line-height: 42rpx;
  text-align: center;
  overflow-wrap: anywhere;
}

.cps-choice-button:active {
  transform: scale(0.98);
}

.cps-choice-button.is-selected {
  border-color: #14b8a6;
  background: linear-gradient(135deg, #ccfbf1 0%, #dbeafe 100%);
  color: #0f766e;
  box-shadow: 0 10rpx 28rpx rgba(20, 184, 166, 0.18);
}

.cps-muted-text {
  color: #64748b;
  font-size: 30rpx;
  font-weight: 800;
  line-height: 44rpx;
}
</style>
