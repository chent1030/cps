<template>
  <section class="cps-action-panel" :aria-label="`当前状态 ${statusLabels[status]}`">
    <VanButton
      v-for="action in availableActions"
      :key="action"
      block
      round
      :type="action === 'REVIEW_REJECT' ? 'danger' : action === 'REVIEW_CLOSE' ? 'success' : 'primary'"
      class="cps-action-panel__button"
      @click="emit('action', action)"
    >
      {{ labels[action] }}
    </VanButton>
  </section>
</template>

<script setup lang="ts">
import { Button as VanButton } from 'vant'
import type { CpsIssueAction, CpsIssueStatus } from '@/types/cps'

defineProps<{
  status: CpsIssueStatus
  availableActions: CpsIssueAction[]
}>()

const emit = defineEmits<{
  action: [action: CpsIssueAction]
}>()

const labels: Record<CpsIssueAction, string> = {
  SUBMIT: '提交问题',
  REPLY_ASSIGN: '回复并指派',
  RECTIFY: '完成整改',
  UPLOAD_PROOF: '上传整改照片',
  REVIEW_CLOSE: '审核关闭',
  REVIEW_REJECT: '审核退回',
  TRANSFER: '转办',
  SUBMIT_RECTIFICATION: '提交整改（AI 初审）',
  SAVE_DRAFT: '暂存',
  AI_REVIEW_ADVANCE: 'AI 初审推进',
  REVIEWER_CONFIGURED: '配置审核人',
  REVIEWER_REASSIGN: '改派审核人',
  AI_REVIEW_RETRIGGER: '重触发初审',
}

const statusLabels: Record<CpsIssueStatus, string> = {
  PENDING_FEEDBACK: '待反馈',
  PENDING_RECTIFY: '待整改',
  PENDING_UPLOAD_PROOF: '待传图',
  PENDING_REVIEW: '待审核',
  PENDING_AI_REVIEW: 'AI 初审中',
  PENDING_REVIEWER_CONFIG: '待审核裁决',
  CLOSED: '已关闭',
}
</script>

<style scoped>
.cps-action-panel,
.cps-action-panel *,
.cps-action-panel *::before,
.cps-action-panel *::after {
  box-sizing: border-box;
}

.cps-action-panel :deep(*),
.cps-action-panel :deep(*::before),
.cps-action-panel :deep(*::after) {
  box-sizing: border-box;
}

.cps-action-panel {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16rpx;
  width: 100%;
  min-width: 0;
}

.cps-action-panel__button {
  min-width: 0;
  min-height: 96rpx;
  font-size: 32rpx;
  font-weight: 900;
  line-height: 42rpx;
}
</style>
