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

    <a-spin :spinning="loading">
      <section class="cps-list">
        <a-empty v-if="!tasks.length && !loading" description="暂无点检任务" class="cps-list-empty" />
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
    </a-spin>
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
.cps-page {
  width: 100%;
  max-width: 720px;
  margin: 0 auto;
  padding: 16px 16px 40px;
}

.cps-list-page {
  display: grid;
  align-content: start;
  gap: 14px;
}

.cps-list-hero__main {
  display: grid;
  gap: 4px;
}

.cps-list-hero__eyebrow {
  margin: 0;
  font-size: 12px;
  letter-spacing: 0.24em;
  color: #0f766e;
  font-weight: 700;
}

.cps-list-hero h1 {
  margin: 0;
  font-size: 22px;
  color: #0f172a;
}

.cps-list-hero__stats {
  display: flex;
  gap: 10px;
  font-size: 13px;
  color: #64748b;
}

.cps-list-empty {
  padding: 48px 0;
}

.cps-list {
  display: grid;
  gap: 12px;
}

.cps-list-card {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  padding: 14px 16px;
  display: grid;
  gap: 10px;
  box-shadow: 0 4px 16px rgba(15, 23, 42, 0.05);
}

.cps-roomcheck-card__head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 10px;
}

.cps-roomcheck-card__eyebrow {
  font-size: 12px;
  letter-spacing: 0.18em;
  color: #0f766e;
  font-weight: 700;
}

.cps-roomcheck-card__no {
  display: block;
  font-size: 16px;
  color: #0f172a;
}

.cps-roomcheck-card__meta {
  display: grid;
  gap: 4px;
  font-size: 13px;
  color: #475569;
}

.cps-roomcheck-card__meta b {
  color: #0f172a;
  margin-right: 8px;
}

.cps-roomcheck-card__rooms {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.cps-roomcheck-room {
  border: 1px solid #99f6e4;
  background: #f0fdfa;
  color: #0f766e;
  border-radius: 10px;
  padding: 6px 14px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
}

.cps-roomcheck-room--starting,
.cps-roomcheck-room:disabled {
  opacity: 0.55;
}

.cps-roomcheck-card__empty {
  margin: 0;
  font-size: 13px;
  color: #94a3b8;
}

.cps-roomcheck-card__error {
  margin: 0;
  font-size: 13px;
  color: #dc2626;
}

.cps-status-pill {
  display: inline-flex;
  align-items: center;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

.cps-status-pill--blue { background: #dbeafe; color: #1d4ed8; }
.cps-status-pill--orange { background: #ffedd5; color: #c2410c; }
.cps-status-pill--green { background: #dcfce7; color: #15803d; }
.cps-status-pill--teal { background: #ccfbf1; color: #0f766e; }
.cps-status-pill--gray { background: #e2e8f0; color: #475569; }
</style>
