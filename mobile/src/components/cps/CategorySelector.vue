<template>
  <view class="cps-detail-card cps-selector-card">
    <view class="cps-card-title">
      <view>
        <text class="cps-card-title__eyebrow">问题分类</text>
        <text class="cps-card-title__heading">一级 / 二级</text>
      </view>
    </view>

    <van-cell-group inset>
      <van-field label="一级分类">
        <template #input>
          <view class="cps-choice-grid cps-choice-grid--two" role="radiogroup" aria-label="一级分类">
            <button
              v-for="item in level1"
              :key="item.value"
              type="button"
              role="radio"
              :aria-checked="modelValue.categoryL1Id === Number(item.value)"
              class="cps-choice-button cps-choice-button--blue"
              :class="{ 'is-selected': modelValue.categoryL1Id === Number(item.value) }"
              @click="selectCategoryL1(item.value)"
            >
              {{ item.label }}
            </button>
          </view>
        </template>
      </van-field>

      <van-field v-if="modelValue.categoryL1Id" label="二级分类">
        <template #input>
          <view class="cps-choice-grid cps-choice-grid--two" role="radiogroup" aria-label="二级分类">
            <button
              v-for="item in level2"
              :key="item.value"
              type="button"
              role="radio"
              :aria-checked="modelValue.categoryL2Id === Number(item.value)"
              class="cps-choice-button"
              :class="{ 'is-selected': modelValue.categoryL2Id === Number(item.value) }"
              @click="selectCategoryL2(item.value)"
            >
              {{ item.label }}
            </button>
          </view>
        </template>
      </van-field>

      <van-field v-else label="二级分类">
        <template #input>
          <text class="cps-muted-text">先选择一级分类</text>
        </template>
      </van-field>
    </van-cell-group>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'

import { getCategories, type CpsOption } from '@/api/cps/master'

interface CategoryModel {
  categoryL1Id: number | null
  categoryL2Id: number | null
}

const props = defineProps<{
  modelValue: CategoryModel
}>()

const emit = defineEmits<{
  'update:modelValue': [value: CategoryModel]
}>()

const level1 = ref<CpsOption[]>([])
const level2 = ref<CpsOption[]>([])
const modelValue = computed<CategoryModel>(() => props.modelValue)

const selectCategoryL1 = (categoryId: CpsOption['value']) => {
  emit('update:modelValue', {
    ...props.modelValue,
    categoryL1Id: Number(categoryId),
    categoryL2Id: null,
  })
}

const selectCategoryL2 = (categoryId: CpsOption['value']) => {
  emit('update:modelValue', {
    ...props.modelValue,
    categoryL2Id: Number(categoryId),
  })
}

onMounted(async () => {
  level1.value = await getCategories()
})

watch(
  () => props.modelValue.categoryL1Id,
  async (parentId) => {
    level2.value = parentId ? await getCategories(parentId) : []
  },
)
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

.cps-selector-card :deep(.van-cell-group--inset) {
  width: 100%;
  max-width: 100%;
  margin: 0;
  overflow: hidden;
  border: 0;
  border-top: 2rpx solid #ccfbf1;
  border-radius: 0;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: none;
}

.cps-selector-card :deep(.van-cell) {
  align-items: flex-start;
  min-width: 0;
  max-width: 100%;
  overflow: hidden;
}

.cps-selector-card :deep(.van-field__label) {
  width: 176rpx;
  padding-top: 16rpx;
  color: #0f172a;
  font-weight: 800;
}

.cps-selector-card :deep(.van-cell__value),
.cps-selector-card :deep(.van-field__body) {
  min-width: 0;
  max-width: 100%;
  overflow: hidden;
}

.cps-choice-grid {
  display: grid;
  width: 100%;
  min-width: 0;
  max-width: 100%;
  gap: 16rpx;
  overflow: hidden;
}

.cps-choice-grid--two {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.cps-choice-button {
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

.cps-choice-button--blue.is-selected {
  border-color: #2563eb;
  background: linear-gradient(135deg, #dbeafe 0%, #ccfbf1 100%);
  color: #1d4ed8;
}

.cps-muted-text {
  color: #64748b;
  font-size: 30rpx;
  font-weight: 800;
  line-height: 44rpx;
}
</style>
