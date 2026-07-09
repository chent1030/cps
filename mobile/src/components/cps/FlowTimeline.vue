<template>
  <van-empty v-if="!logs.length" description="暂无流转记录" />
  <ol v-else class="cps-flow-timeline">
    <li v-for="(log, index) in logs" :key="`${log.action}-${log.createdAt}`" class="cps-flow-timeline__item">
      <span class="cps-flow-timeline__dot" :class="{ 'is-last': index === logs.length - 1 }" />
      <div class="cps-flow-timeline__content">
        <div class="cps-flow-timeline__head">
          <strong>{{ actionLabel(log.action) }}</strong>
          <time>{{ log.createdAt }}</time>
        </div>
        <p>{{ statusLabel(log.fromStatus) }} 至 {{ statusLabel(log.toStatus) }}</p>
        <p>操作人：{{ log.operatorEmpNo }}</p>
        <p v-if="log.comment" class="cps-flow-timeline__comment">{{ log.comment }}</p>
      </div>
    </li>
  </ol>
</template>

<script setup lang="ts">
import type { CpsFlowLog, CpsIssueStatus } from '@/types/cps'

defineProps<{
  logs: CpsFlowLog[]
}>()

const actionLabels: Record<string, string> = {
  SUBMIT: '提交问题',
  REPLY_ASSIGN: '回复并指派',
  RECTIFY: '完成整改',
  UPLOAD_PROOF: '上传凭证',
  REVIEW_CLOSE: '审核关闭',
  REVIEW_REJECT: '审核退回',
  TRANSFER: '转办',
}

const statusLabels: Record<CpsIssueStatus, string> = {
  PENDING_FEEDBACK: '待反馈',
  PENDING_RECTIFY: '待整改',
  PENDING_UPLOAD_PROOF: '待传图',
  PENDING_REVIEW: '待审核',
  CLOSED: '已关闭',
}

const actionLabel = (action: string) => {
  return actionLabels[action] ?? action
}

const statusLabel = (status: CpsIssueStatus | null) => {
  return status ? statusLabels[status] : '开始'
}
</script>

<style scoped>
.cps-flow-timeline,
.cps-flow-timeline *,
.cps-flow-timeline *::before,
.cps-flow-timeline *::after {
  box-sizing: border-box;
}

.cps-flow-timeline :deep(*),
.cps-flow-timeline :deep(*::before),
.cps-flow-timeline :deep(*::after) {
  box-sizing: border-box;
}

.cps-flow-timeline {
  display: grid;
  gap: 0;
  min-width: 0;
  margin: 0;
  padding: 0;
  list-style: none;
}

.cps-flow-timeline__item {
  position: relative;
  display: grid;
  grid-template-columns: 38rpx minmax(0, 1fr);
  gap: 16rpx;
  min-width: 0;
  padding-bottom: 18rpx;
}

.cps-flow-timeline__item::before {
  position: absolute;
  top: 34rpx;
  bottom: 0;
  left: 17rpx;
  width: 4rpx;
  border-radius: 999rpx;
  background: #ccfbf1;
  content: "";
}

.cps-flow-timeline__item:last-child {
  padding-bottom: 0;
}

.cps-flow-timeline__item:last-child::before {
  display: none;
}

.cps-flow-timeline__dot {
  position: relative;
  z-index: 1;
  width: 38rpx;
  height: 38rpx;
  border: 8rpx solid #ccfbf1;
  border-radius: 999rpx;
  background: #14b8a6;
}

.cps-flow-timeline__dot.is-last {
  border-color: #dbeafe;
  background: #2563eb;
}

.cps-flow-timeline__content {
  display: grid;
  min-width: 0;
  gap: 10rpx;
  border-radius: 16rpx;
  padding: 20rpx;
  background: linear-gradient(135deg, #f0fdfa 0%, #eff6ff 100%);
}

.cps-flow-timeline__head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18rpx;
  min-width: 0;
}

.cps-flow-timeline__head strong {
  min-width: 0;
  color: #0f172a;
  font-size: 30rpx;
  font-weight: 950;
  line-height: 42rpx;
  overflow-wrap: anywhere;
}

.cps-flow-timeline__head time,
.cps-flow-timeline__content p {
  margin: 0;
  color: #64748b;
  font-size: 26rpx;
  font-weight: 700;
  line-height: 38rpx;
  overflow-wrap: anywhere;
}

.cps-flow-timeline__comment {
  border-radius: 14rpx;
  padding: 14rpx 16rpx;
  background: #ffffff;
  color: #0f172a;
}
</style>
