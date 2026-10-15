<template>
  <main class="cps-page cps-list-page">
    <header class="cps-list-hero">
      <div class="cps-list-hero__main">
        <p class="cps-list-hero__eyebrow">辅房点检</p>
        <h1>点检任务</h1>
        <div class="cps-list-hero__stats">
          <span>任务 {{ tasks.length }}</span>
          <span>已覆盖房间 {{ totalJudgedRooms }}</span>
        </div>
      </div>
    </header>

    <van-loading v-if="loading" class="cps-page-loading" color="#14B8A6">加载中...</van-loading>
    <section v-else class="cps-list">
      <van-empty v-if="!tasks.length" description="暂无点检任务" />
      <article v-for="task in tasks" :key="task.taskId" class="cps-list-card cps-roomcheck-card">
        <header class="cps-roomcheck-card__head">
          <div class="cps-roomcheck-card__identity">
            <span class="cps-roomcheck-card__eyebrow">点检任务</span>
            <strong class="cps-roomcheck-card__no">{{ task.title || `任务 #${task.taskId}` }}</strong>
          </div>
          <span class="cps-status-pill" :class="statusTone(task.taskStatus)">{{ statusLabel(task.taskStatus) }}</span>
        </header>

        <div class="cps-roomcheck-card__meta">
          <span v-if="task.scheduledAt"><b>计划</b>{{ task.scheduledAt }}</span>
          <span v-if="task.frequency"><b>频次</b>{{ task.frequency }}</span>
          <span><b>进度</b>{{ task.judgedRoomCount }}/{{ task.roomCodes.length || '-' }}</span>
        </div>

        <section class="cps-roomcheck-card__rooms" aria-label="覆盖房间">
          <button
            v-for="code in task.roomCodes"
            :key="code"
            type="button"
            class="cps-roomcheck-room"
            :class="{ 'cps-roomcheck-room--starting': startingRoom === code }"
            :disabled="starting || startingRoom === code"
            @click="openRoom(task, code)"
          >
            {{ code }}
          </button>
          <p v-if="!task.roomCodes.length" class="cps-roomcheck-card__empty">未配置覆盖房间</p>
        </section>

        <p v-if="error && errorTaskId === task.taskId" class="cps-roomcheck-card__error" role="alert">{{ error }}</p>
      </article>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import { listRoomCheckTasks, listRooms, startRoomCheck } from '@/api/cps/roomCheck'
import type { CpsRoomCheckTask } from '@/types/cps'

const tasks = ref<CpsRoomCheckTask[]>([])
const loading = ref<boolean>(false)
const starting = ref<boolean>(false)
const startingRoom = ref<string>('')
const error = ref<string>('')
const errorTaskId = ref<number | null>(null)

const totalJudgedRooms = computed<number>(() =>
  tasks.value.reduce((sum, task) => sum + (task.judgedRoomCount ?? 0), 0),
)

const STATUS_META: Record<string, { label: string; tone: string }> = {
  PENDING: { label: '待执行', tone: 'cps-status-pill--blue' },
  IN_PROGRESS: { label: '进行中', tone: 'cps-status-pill--orange' },
  DONE: { label: '已完成', tone: 'cps-status-pill--green' },
  COMPLETED: { label: '已完成', tone: 'cps-status-pill--green' },
  CANCELLED: { label: '已取消', tone: 'cps-status-pill--gray' },
}

const statusMeta = (status: string) => STATUS_META[status] ?? { label: status, tone: 'cps-status-pill--gray' }
const statusLabel = (status: string) => statusMeta(status).label
const statusTone = (status: string) => statusMeta(status).tone

/** 房间入口：roomCode → roomId（start 需要 roomId）→ 开启点检（幂等）→ 进入执行明细。 */
const openRoom = async (task: CpsRoomCheckTask, roomCode: string) => {
  starting.value = true
  startingRoom.value = roomCode
  error.value = ''
  errorTaskId.value = null
  try {
    const rooms = await listRooms()
    const room = rooms.find((candidate) => candidate.roomCode === roomCode)
    if (!room) {
      throw new Error(`未找到房间配置：${roomCode}`)
    }
    const record = await startRoomCheck({ planTaskId: task.taskId, roomId: room.id })
    uni.navigateTo({ url: `/views/cps/RoomCheckExecuteView?recordId=${record.id}` })
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '开启点检失败，请重试'
    errorTaskId.value = task.taskId
  } finally {
    starting.value = false
    startingRoom.value = ''
  }
}

const load = async () => {
  loading.value = true
  try {
    tasks.value = await listRoomCheckTasks()
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.cps-page,
.cps-page *,
.cps-page *::before,
.cps-page *::after {
  box-sizing: border-box;
}

.cps-page {
  width: 100%;
  min-height: 100dvh;
  margin: 0;
  padding: 28rpx 24rpx 96rpx;
  overflow-x: hidden;
}

.cps-list-page {
  display: grid;
  align-content: start;
  gap: 26rpx;
}

.cps-list-hero__main {
  display: grid;
  gap: 8rpx;
}

.cps-list-hero__eyebrow {
  margin: 0;
  font-size: 22rpx;
  letter-spacing: 0.24em;
  color: #0f766e;
  font-weight: 600;
}

.cps-list-hero h1 {
  margin: 0;
  font-size: 40rpx;
  color: #0f172a;
}

.cps-list-hero__stats {
  display: flex;
  gap: 18rpx;
  font-size: 24rpx;
  color: #64748b;
}

.cps-list {
  display: grid;
  gap: 22rpx;
}

.cps-list-card {
  background: #ffffff;
  border: 1rpx solid #e2e8f0;
  border-radius: 20rpx;
  padding: 26rpx;
  display: grid;
  gap: 18rpx;
  box-shadow: 0 6rpx 18rpx rgba(15, 23, 42, 0.05);
}

.cps-roomcheck-card__head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16rpx;
}

.cps-roomcheck-card__eyebrow {
  font-size: 20rpx;
  letter-spacing: 0.18em;
  color: #0f766e;
  font-weight: 600;
}

.cps-roomcheck-card__no {
  display: block;
  font-size: 30rpx;
  color: #0f172a;
}

.cps-roomcheck-card__meta {
  display: grid;
  gap: 8rpx;
  font-size: 24rpx;
  color: #475569;
}

.cps-roomcheck-card__meta b {
  color: #0f172a;
  margin-right: 12rpx;
}

.cps-roomcheck-card__rooms {
  display: flex;
  flex-wrap: wrap;
  gap: 14rpx;
}

.cps-roomcheck-room {
  border: 1rpx solid #99f6e4;
  background: #f0fdfa;
  color: #0f766e;
  border-radius: 12rpx;
  padding: 10rpx 22rpx;
  font-size: 26rpx;
  font-weight: 600;
}

.cps-roomcheck-room--starting,
.cps-roomcheck-room:disabled {
  opacity: 0.55;
}

.cps-roomcheck-card__empty {
  margin: 0;
  font-size: 24rpx;
  color: #94a3b8;
}

.cps-roomcheck-card__error {
  margin: 0;
  font-size: 24rpx;
  color: #dc2626;
}

.cps-status-pill {
  display: inline-flex;
  align-items: center;
  padding: 4rpx 14rpx;
  border-radius: 999rpx;
  font-size: 22rpx;
  font-weight: 600;
  white-space: nowrap;
}

.cps-status-pill--blue { background: #dbeafe; color: #1d4ed8; }
.cps-status-pill--orange { background: #ffedd5; color: #c2410c; }
.cps-status-pill--green { background: #dcfce7; color: #15803d; }
.cps-status-pill--teal { background: #ccfbf1; color: #0f766e; }
.cps-status-pill--gray { background: #e2e8f0; color: #475569; }
</style>
