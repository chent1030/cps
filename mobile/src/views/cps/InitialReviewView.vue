<template>
  <main v-if="!isAdmin" class="cps-page cps-review-page">
    <header class="cps-review-hero">
      <p class="cps-review-hero__eyebrow">无权访问</p>
      <h1 class="cps-review-hero__title">仅审核员可访问</h1>
      <p class="cps-review-hero__hint">AI 初审裁决需要审核员角色；巡检员请返回问题列表。</p>
    </header>
  </main>
  <main v-else class="cps-page cps-review-page">
    <header class="cps-review-hero">
      <p class="cps-review-hero__eyebrow">AI 初审 · 人工裁决</p>
      <h1 class="cps-review-hero__title">{{ issueId ? `问题 #${issueId} 初审` : 'AI 初审裁决' }}</h1>
      <p class="cps-review-hero__state" :data-state="stateView">
        <b>{{ STATE_LABEL[stateView] ?? stateView }}</b>
      </p>
      <p v-if="stateHint" class="cps-review-hero__hint">{{ stateHint }}</p>
      <p v-if="loadError" class="cps-review-hero__error">{{ loadError }}</p>
    </header>

    <template v-if="view">
      <!-- 三态呈现：运行中 / 失败 / 超时可接管（PRD §28.4 界面必须区分） -->
      <section
        v-if="stateView === 'running' || stateView === 'timeout_open' || stateView === 'failed'"
        class="cps-card cps-review-state"
        :data-state="stateView"
      >
        <div class="cps-review-state__row">
          <span class="cps-review-state__title">{{ STATE_LABEL[stateView] }}</span>
          <span v-if="countdownText" class="cps-review-state__countdown">{{ countdownText }}</span>
        </div>
        <p v-if="view.task?.errorCode" class="cps-review-state__detail">
          失败原因：{{ view.task.errorCode }}（AI 明确失败可立即接管）
        </p>
        <p v-else-if="stateView === 'timeout_open'" class="cps-review-state__detail">
          超过 10 分钟接管阈值（600s），AI 未返回结论，可人工接管。
        </p>
        <p v-else-if="stateView === 'running'" class="cps-review-state__detail">
          AI 初审运行中，未到接管阈值；结果返回后此页展示逐项意见。
        </p>

        <div v-if="view.can_take_over" class="cps-review-takeover">
          <p class="cps-review-takeover__title">人工接管（PRD §28.4：必须注明原因）</p>
          <!-- van-field 而非原生 textarea：模板 textarea 会被 uni 编译成 uni-textarea（依赖 uni 运行时，单测缺失） -->
          <van-field
            v-model="takeoverReason"
            class="cps-review-takeover__reason"
            type="textarea"
            rows="2"
            placeholder="接管原因（必填，留痕可追溯）"
            aria-label="接管原因"
          />
          <button
            type="button"
            class="cps-review-takeover__submit"
            :disabled="takeoverBusy || !takeoverReason.trim()"
            @click="submitTakeover"
          >
            {{ takeoverBusy ? '接管中...' : '接管此初审' }}
          </button>
        </div>
        <button type="button" class="cps-review-refresh" @click="load">刷新状态</button>
      </section>

      <!-- AI 意见（迟到结果仅留痕不覆盖） -->
      <section v-if="view.result" class="cps-card">
        <h2 class="cps-card-title">
          AI 初审意见
          <span v-if="view.result.isLate" class="cps-tag cps-tag--late">迟到留痕</span>
        </h2>
        <p class="cps-review-overall">
          总体结论：
          <b :data-overall="view.result.overall">{{ OVERALL_LABEL[view.result.overall] ?? view.result.overall }}</b>
          <span v-if="view.result.modelStatus" class="cps-review-model">（{{ view.result.modelStatus }}）</span>
        </p>
        <ul v-if="view.items.length" class="cps-review-items">
          <li v-for="item in view.items" :key="item.id" class="cps-review-item" :data-verdict="item.verdict">
            <p class="cps-review-item__head">
              <b>{{ item.fieldName ?? item.checkType }}</b>
              <span class="cps-review-item__verdict">{{ VERDICT_LABEL[item.verdict ?? ''] ?? item.verdict }}</span>
            </p>
            <p v-if="item.reason" class="cps-review-item__reason">{{ item.reason }}</p>
            <p v-if="item.problemFragment" class="cps-review-item__fragment">问题片段：{{ item.problemFragment }}</p>
            <p class="cps-review-item__meta">
              长度 {{ item.textLength ?? '-' }} · 标点 {{ item.punctuationCount ?? '-' }}
              <template v-if="item.ratioOk !== null && item.ratioOk !== undefined">
                · 标点占比 {{ item.ratioOk ? '合规' : '超限' }}
              </template>
            </p>
          </li>
        </ul>
        <p v-else class="cps-muted-text">无逐项意见。</p>
      </section>

      <!-- 整改提交快照（§28 整改人员提交内容） -->
      <section v-if="view.submission" class="cps-card">
        <h2 class="cps-card-title">整改提交内容</h2>
        <dl class="cps-review-submission">
          <div><dt>原因分析</dt><dd>{{ view.submission.reason ?? '-' }}</dd></div>
          <div><dt>短期措施</dt><dd>{{ view.submission.shortTermMeasure ?? '-' }}</dd></div>
          <div><dt>长期措施</dt><dd>{{ view.submission.longTermMeasure ?? '-' }}</dd></div>
          <div><dt>责任员工</dt><dd>{{ view.submission.responsibleEmpNo ?? '-' }}</dd></div>
          <div><dt>提交人/时间</dt><dd>{{ view.submission.submittedBy ?? '-' }} · {{ view.submission.submittedAt ?? '-' }}</dd></div>
        </dl>
      </section>

      <!-- 既有裁决留痕 -->
      <section v-if="view.adjudication" class="cps-card">
        <h2 class="cps-card-title">裁决留痕</h2>
        <p class="cps-review-adjudication">
          <b :data-decision="view.adjudication.decision">
            {{ view.adjudication.decision === 'APPROVE' ? '通过并关闭' : '不通过退回整改' }}
          </b>
          <span class="cps-tag" :data-relation="view.adjudication.aiRelation">
            {{ RELATION_LABEL[view.adjudication.aiRelation ?? ''] ?? '' }}
          </span>
        </p>
        <p v-if="view.adjudication.reason" class="cps-review-adjudication__reason">
          裁决理由：{{ view.adjudication.reason }}
        </p>
        <p class="cps-review-adjudication__meta">
          裁决人 {{ view.adjudication.reviewerEmpNo ?? '-' }} · {{ view.adjudication.createdAt ?? '-' }}
        </p>
      </section>

      <!-- 裁决操作：结果已出或已接管且尚未裁决 -->
      <section
        v-if="canAdjudicate"
        class="cps-card cps-review-decide"
      >
        <h2 class="cps-card-title">人工裁决</h2>
        <p class="cps-review-decide__relation">
          <template v-if="relationPreview">{{ relationPreview }}</template>
        </p>
        <van-field
          v-model="decideReason"
          class="cps-review-decide__reason"
          type="textarea"
          rows="2"
          placeholder="裁决理由（必填，留痕可追溯）"
          aria-label="裁决理由"
        />
        <div class="cps-review-decide__actions">
          <button
            type="button"
            class="cps-review-decide__approve"
            :disabled="decideBusy || !decideReason.trim()"
            @click="submitDecision('APPROVE')"
          >
            通过并关单
          </button>
          <button
            type="button"
            class="cps-review-decide__reject"
            :disabled="decideBusy || !decideReason.trim()"
            @click="submitDecision('REJECT')"
          >
            不通过退回整改
          </button>
        </div>
      </section>

      <!-- 事件流水 -->
      <section v-if="view.events.length" class="cps-card">
        <h2 class="cps-card-title">事件留痕</h2>
        <ol class="cps-review-events">
          <li v-for="(event, index) in view.events" :key="event.id ?? index" class="cps-review-event">
            <p class="cps-review-event__head">
              <b>{{ event.eventType }}</b>
              <span>{{ event.createdAt ?? '' }}</span>
            </p>
            <p v-if="event.detail" class="cps-review-event__detail">{{ event.detail }}</p>
          </li>
        </ol>
      </section>
    </template>

    <p v-else-if="loadError" class="cps-review-loading">{{ loadError }}</p>
    <p v-else-if="!issueId" class="cps-review-loading">未指定问题：请从「我的问题」列表选择待初审/待裁决的问题进入。</p>
    <p v-else class="cps-review-loading">加载中...</p>
  </main>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getCurrentUser } from '@/api/cps/userStore'
import {
  adjudicateReview,
  getInitialReviewView,
  takeOverInitialReview,
} from '@/api/cps/initialReview'
import type { CpsInitialReviewView } from '@/types/cps'

defineOptions({ name: 'InitialReviewView' })

const isAdmin = computed<boolean>(() => getCurrentUser().role === 'admin')

const issueId = ref(0)
const view = ref<CpsInitialReviewView | null>(null)
const loadError = ref('')
const takeoverReason = ref('')
const takeoverBusy = ref(false)
const decideReason = ref('')
const decideBusy = ref(false)

const STATE_LABEL: Record<string, string> = {
  none: '暂无初审任务',
  pending_dispatch: '待派发',
  running: 'AI 初审运行中',
  timeout_open: '超时可接管',
  failed: 'AI 执行失败',
  taken_over: '已人工接管',
  late_result: '迟到结果留痕',
  completed: 'AI 初审完成',
}
const OVERALL_LABEL: Record<string, string> = {
  PASS: '通过',
  PARTIAL: '部分通过',
  PROBLEM: '不通过',
}
const VERDICT_LABEL: Record<string, string> = {
  PASS: '通过',
  FAIL: '不通过',
}
const RELATION_LABEL: Record<string, string> = {
  WITH_AI: '与 AI 同向',
  AGAINST_AI: '与 AI 反向',
  NO_AI_RESULT: '无 AI 结果',
}

const stateView = computed(() => view.value?.state_view ?? 'none')

const countdownText = computed(() => {
  const seconds = view.value?.seconds_until_takeover
  if (stateView.value !== 'running' || seconds === null || seconds === undefined) return ''
  return `约 ${seconds}s 后达到接管阈值`
})

const stateHint = computed(() => {
  if (stateView.value === 'taken_over' && view.value?.task) {
    const task = view.value.task
    return `接管人：${task.takenOverName ?? task.takenOverBy ?? '-'}${task.takeoverReason ? ` · 原因：${task.takeoverReason}` : ''}`
  }
  if (stateView.value === 'late_result') {
    return 'AI 结果迟到，仅留痕展示，不覆盖已作出的裁决。'
  }
  if (stateView.value === 'pending_dispatch') {
    return 'AI 初审任务已登记，等待派发执行。'
  }
  return ''
})

const canAdjudicate = computed(() => {
  if (!view.value || view.value.adjudication) return false
  return ['completed', 'late_result', 'taken_over'].includes(view.value.state_view)
})

/** 裁决与 AI 意见的同向/反向预览（与后端 CpsIssueService.aiRelation 矩阵一致）。 */
const relationPreview = computed(() => {
  const overall = view.value?.result?.overall
  if (!overall) return ''
  const aiPass = overall === 'PASS'
  const aiProblem = overall === 'PROBLEM'
  return `通过=与 AI ${aiPass ? '同向' : '反向'}；退回=与 AI ${aiProblem ? '同向' : '反向'}`
})

const load = async () => {
  if (!issueId.value) return
  loadError.value = ''
  try {
    view.value = await getInitialReviewView(issueId.value)
  } catch (error) {
    view.value = null
    loadError.value = error instanceof Error ? error.message : '初审视图加载失败'
  }
}

const submitTakeover = async () => {
  if (!issueId.value || !takeoverReason.value.trim()) return
  takeoverBusy.value = true
  try {
    await takeOverInitialReview(issueId.value, takeoverReason.value.trim())
    takeoverReason.value = ''
    await load()
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '接管失败，请重试'
  } finally {
    takeoverBusy.value = false
  }
}

const submitDecision = async (decision: 'APPROVE' | 'REJECT') => {
  if (!issueId.value || !decideReason.value.trim()) return
  decideBusy.value = true
  try {
    await adjudicateReview(issueId.value, decision, decideReason.value.trim())
    decideReason.value = ''
    await load()
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '裁决失败，请重试'
  } finally {
    decideBusy.value = false
  }
}

onLoad((query) => {
  const raw = query?.issueId ?? ''
  const parsed = Number.parseInt(String(raw), 10)
  issueId.value = Number.isFinite(parsed) ? parsed : 0
  void load()
})
</script>

<style scoped>
.cps-review-page {
  padding-bottom: 32px;
}
.cps-review-hero {
  margin-bottom: 12px;
}
.cps-review-hero__eyebrow {
  font-size: 12px;
  color: #6b7280;
  letter-spacing: 2px;
}
.cps-review-hero__title {
  font-size: 20px;
  margin: 4px 0;
}
.cps-review-hero__state b {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 13px;
  background: #eef2ff;
  color: #3730a3;
}
.cps-review-hero__state[data-state='failed'] b,
.cps-review-hero__state[data-state='timeout_open'] b {
  background: #fef2f2;
  color: #b91c1c;
}
.cps-review-hero__state[data-state='taken_over'] b,
.cps-review-hero__state[data-state='late_result'] b {
  background: #fffbeb;
  color: #b45309;
}
.cps-review-hero__hint {
  margin-top: 6px;
  font-size: 13px;
  color: #6b7280;
}
.cps-review-hero__error {
  margin-top: 6px;
  color: #b91c1c;
  font-size: 13px;
}
.cps-review-state {
  border-left: 4px solid #6366f1;
}
.cps-review-state[data-state='failed'],
.cps-review-state[data-state='timeout_open'] {
  border-left-color: #ef4444;
}
.cps-review-state__row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.cps-review-state__title {
  font-weight: 600;
}
.cps-review-state__countdown {
  font-size: 13px;
  color: #6b7280;
}
.cps-review-state__detail {
  margin: 6px 0 0;
  font-size: 13px;
  color: #4b5563;
}
.cps-review-takeover {
  margin-top: 12px;
  padding: 10px;
  border: 1px dashed #d1d5db;
  border-radius: 8px;
}
.cps-review-takeover__title {
  margin: 0 0 6px;
  font-size: 13px;
  font-weight: 600;
}
.cps-review-takeover__reason :deep(textarea),
.cps-review-decide__reason :deep(textarea) {
  border: 1px solid #d1d5db;
  border-radius: 6px;
  padding: 8px;
  font-size: 14px;
  width: 100%;
  box-sizing: border-box;
}
.cps-review-takeover__submit,
.cps-review-decide__approve,
.cps-review-decide__reject,
.cps-review-refresh {
  margin-top: 8px;
  padding: 8px 14px;
  border-radius: 8px;
  border: none;
  font-size: 14px;
}
.cps-review-takeover__submit {
  background: #b45309;
  color: #fff;
}
.cps-review-refresh {
  margin-top: 10px;
  background: #f3f4f6;
  color: #374151;
}
.cps-review-overall {
  margin: 8px 0;
}
.cps-review-overall b[data-overall='PASS'] {
  color: #047857;
}
.cps-review-overall b[data-overall='PROBLEM'] {
  color: #b91c1c;
}
.cps-review-model {
  font-size: 12px;
  color: #6b7280;
}
.cps-tag {
  display: inline-block;
  margin-left: 6px;
  padding: 1px 8px;
  border-radius: 999px;
  font-size: 12px;
  background: #f3f4f6;
  color: #374151;
}
.cps-tag--late {
  background: #fffbeb;
  color: #b45309;
}
.cps-tag[data-relation='WITH_AI'] {
  background: #ecfdf5;
  color: #047857;
}
.cps-tag[data-relation='AGAINST_AI'] {
  background: #fef2f2;
  color: #b91c1c;
}
.cps-review-items {
  list-style: none;
  margin: 8px 0 0;
  padding: 0;
}
.cps-review-item {
  padding: 8px 0;
  border-top: 1px solid #f3f4f6;
}
.cps-review-item__head {
  display: flex;
  justify-content: space-between;
  margin: 0;
}
.cps-review-item[data-verdict='FAIL'] .cps-review-item__verdict {
  color: #b91c1c;
}
.cps-review-item__verdict {
  font-size: 13px;
}
.cps-review-item__reason,
.cps-review-item__fragment,
.cps-review-item__meta {
  margin: 4px 0 0;
  font-size: 13px;
  color: #4b5563;
}
.cps-review-item__fragment {
  color: #b91c1c;
}
.cps-review-submission {
  margin: 8px 0 0;
}
.cps-review-submission div {
  display: flex;
  gap: 8px;
  padding: 6px 0;
  border-top: 1px solid #f3f4f6;
  font-size: 14px;
}
.cps-review-submission dt {
  flex: 0 0 84px;
  color: #6b7280;
}
.cps-review-submission dd {
  margin: 0;
  flex: 1;
}
.cps-review-adjudication {
  margin: 8px 0 0;
}
.cps-review-adjudication b[data-decision='APPROVE'] {
  color: #047857;
}
.cps-review-adjudication b[data-decision='REJECT'] {
  color: #b91c1c;
}
.cps-review-adjudication__reason,
.cps-review-adjudication__meta {
  margin: 6px 0 0;
  font-size: 13px;
  color: #4b5563;
}
.cps-review-decide__relation {
  margin: 6px 0;
  font-size: 13px;
  color: #6b7280;
}
.cps-review-decide__actions {
  display: flex;
  gap: 10px;
}
.cps-review-decide__approve {
  flex: 1;
  background: #047857;
  color: #fff;
}
.cps-review-decide__reject {
  flex: 1;
  background: #b91c1c;
  color: #fff;
}
.cps-review-events {
  list-style: none;
  margin: 8px 0 0;
  padding: 0;
}
.cps-review-event {
  padding: 6px 0;
  border-top: 1px solid #f3f4f6;
}
.cps-review-event__head {
  display: flex;
  justify-content: space-between;
  margin: 0;
  font-size: 13px;
}
.cps-review-event__head span {
  color: #9ca3af;
}
.cps-review-event__detail {
  margin: 4px 0 0;
  font-size: 13px;
  color: #4b5563;
}
.cps-review-loading {
  text-align: center;
  color: #9ca3af;
  padding: 24px 0;
}
</style>
