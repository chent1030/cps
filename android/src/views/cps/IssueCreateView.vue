<template>
  <main class="cps-page cps-create-page">
    <header class="cps-hero">
      <div class="cps-hero__main">
        <p class="cps-hero__eyebrow">CPS 现场巡检</p>
        <h1>{{ step === 'select' ? '选择巡检类型' : '新建问题' }}</h1>
        <ol class="cps-steps">
          <li
            v-for="(label, index) in stepLabels"
            :key="label"
            class="cps-steps__item"
            :class="{ 'is-active': stepIndex === index, 'is-done': stepIndex > index }"
          >
            <span class="cps-steps__num">{{ index + 1 }}</span>
            <span class="cps-steps__label">{{ label }}</span>
          </li>
        </ol>
      </div>
      <a-tag color="blue" class="cps-hero__badge">AI 辅助</a-tag>
    </header>

    <!-- ============================================================ -->
    <!-- 第一步：选择巡检类型（选完才渲染对应表单） -->
    <!-- ============================================================ -->
    <div v-if="step === 'select'" class="cps-type-select">
      <a-card class="cps-card" :body-style="{ padding: '20px' }">
        <template #title>
          <div class="cps-card-title">
            <h2 class="cps-card-title__main">本次巡检属于哪类问题？</h2>
            <a-tag color="blue">{{ typeCountLabel }}</a-tag>
          </div>
        </template>
        <p class="cps-create-card__hint">「CPS巡检」进入标准问题上报表单；其他类型按各自配置逐项点检填写（合格判定/文本/拍照），互不混用；分区域类型（如辅房点检）先选区域再开始</p>
        <a-input-search
          v-model:value="typeKeyword"
          class="cps-type-search"
          placeholder="搜索巡检类型名称或编码"
          allow-clear
          :disabled="!draft.inspectionTypes.length"
        />
        <a-spin :spinning="inspectionTypesLoading">
          <a-empty v-if="!draft.inspectionTypes.length && !inspectionTypesLoading" description="暂无可用巡检类型，请联系管理员配置" />
          <div v-else-if="!filteredTypes.length" class="cps-type-empty">没有匹配「{{ typeKeyword }}」的类型</div>
          <div v-else class="cps-type-select__grid">
            <button
              v-for="t in filteredTypes"
              :key="t.code"
              type="button"
              class="cps-type-select__item"
              :data-color="t.color || 'blue'"
              :disabled="startingCustom !== null"
              @click="onSelectInspectionType(t.code)"
            >
              <span class="cps-type-select__head">
                <strong class="cps-type-select__name">{{ t.name }}</strong>
                <a-tag v-if="t.builtin === 1" color="purple" class="cps-type-select__tag">内置</a-tag>
              </span>
              <span class="cps-type-select__code">{{ t.code }}</span>
              <span v-if="t.content || t.description" class="cps-type-select__desc">{{ t.content || t.description }}</span>
              <span class="cps-type-select__go">
                <span>{{ typeActionLabel(t) }}</span>
                <span class="cps-type-select__arrow" aria-hidden="true">→</span>
              </span>
            </button>
          </div>
        </a-spin>
      </a-card>
    </div>

    <!-- ============================================================ -->
    <!-- 第二步：按所选类型渲染的表单 -->
    <!-- ============================================================ -->
    <a-row v-else :gutter="[16, 16]">
      <!-- 左列：照片 + 描述 -->
      <a-col :xs="{ span: 24 }" :lg="{ span: 12 }">
        <a-card class="cps-card" :body-style="{ padding: '16px' }">
          <template #title>
            <div class="cps-card-title">
              <h2 class="cps-card-title__main">问题照片</h2>
              <a-tag color="blue">{{ draft.images.length }}/5</a-tag>
            </div>
          </template>
          <p class="cps-create-card__hint">最多上传 5 张，仅第 1 张参与 AI 识别</p>

          <a-upload
            list-type="picture-card"
            :file-list="uploadFileList"
            :max-count="5"
            :custom-request="customRequest"
            :before-upload="beforeUpload"
            :show-upload-list="false"
            multiple
            accept="image/*"
          >
            <div v-if="draft.images.length < 5" class="cps-uploader__trigger">
              <span class="cps-uploader__plus">+</span>
              <span class="cps-uploader__text">{{ uploading ? '上传中' : '上传图片' }}</span>
            </div>
          </a-upload>

          <div class="cps-uploader__grid">
            <div
              v-for="(image, index) in draft.images"
              :key="image.id"
              class="cps-uploader__item"
              @click="onPreviewImage(index)"
            >
              <img :src="draft.imagePreviewSources[image.id] || ''" :alt="image.name" />
              <span v-if="!uploading" class="cps-uploader__delete" @click.stop="onRemoveImage(index)">×</span>
              <span class="cps-uploader__index">{{ index + 1 }}</span>
            </div>
          </div>
        </a-card>

        <a-card class="cps-card" :body-style="{ padding: '16px' }" style="margin-top: 16px">
          <template #title>
            <div class="cps-card-title">
              <h2 class="cps-card-title__main">现场情况</h2>
            </div>
          </template>
          <a-textarea
            v-model:value="draft.description"
            :rows="5"
            :maxlength="500"
            show-count
            placeholder="请输入现场问题描述"
          />
          <div class="cps-desc-toolbar">
            <a-button
              :loading="transcribing"
              :disabled="transcribing"
              :danger="recording"
              @click="onVoiceClick"
            >
              <template #icon>
                <AudioOutlined v-if="!recording && !transcribing" />
              </template>
              <span v-if="transcribing">识别中</span>
              <span v-else-if="recording">停止录音</span>
              <span v-else>语音输入</span>
            </a-button>
            <span v-if="recording" class="cps-voice-hint">正在录音，再次点击「停止录音」结束并识别…</span>
          </div>
        </a-card>
      </a-col>

      <!-- 右列：类型指示 + 动态字段 + 位置 + 派发 -->
      <a-col :xs="{ span: 24 }" :lg="{ span: 12 }">
        <!-- 当前巡检类型（第一步已选，可切换） -->
        <a-card class="cps-card" :body-style="{ padding: '14px 16px' }">
          <div class="cps-type-active">
            <div class="cps-type-active__info">
              <span class="cps-type-active__label">当前巡检类型</span>
              <strong class="cps-type-active__name">
                {{ activeTypeName || draft.inspectionTypeCode }}
                <span class="cps-type-active__code">{{ draft.inspectionTypeCode }}</span>
              </strong>
            </div>
            <a-button size="small" @click="switchType">切换类型</a-button>
          </div>
        </a-card>

        <!-- Phase D2：类型配置联动 —— 巡检内容说明 -->
        <a-card v-if="activeTypeContent" class="cps-card" :body-style="{ padding: '12px 16px' }" style="margin-top: 16px">
          <p class="cps-type-content-banner"><strong>巡检内容：</strong>{{ activeTypeContent }}</p>
        </a-card>

        <!-- Phase D2：类型配置联动 —— 点检项填写（管理端「直挂点检项」配置驱动） -->
        <a-card
          v-if="typeCheckItems.length || checkItemsLoading"
          class="cps-card"
          :body-style="{ padding: '16px' }"
          style="margin-top: 16px"
        >
          <template #title>
            <div class="cps-card-title">
              <h2 class="cps-card-title__main">现场点检项</h2>
              <a-tag color="cyan">{{ checkDoneCount }}/{{ typeCheckItems.length }}</a-tag>
            </div>
          </template>
          <a-spin :spinning="checkItemsLoading">
            <div v-for="(ci, idx) in typeCheckItems" :key="ci.itemCode" class="cps-check-entry" :class="{ 'cps-check-entry--done': isCheckEntryDone(ci) }">
              <div class="cps-check-entry__head">
                <strong>{{ idx + 1 }}. {{ ci.content }}</strong>
                <span class="cps-check-entry__code">{{ ci.itemCode }}</span>
              </div>
              <p v-if="hasCheckType(ci, 'PHOTO') && ci.photoCriteria" class="cps-check-entry__criteria">
                📷 {{ ci.photoCriteria }}（至少 {{ ci.photoMinCount ?? 1 }} 张）
              </p>
              <div v-if="hasCheckType(ci, 'PASS_FAIL')" class="cps-check-entry__row">
                <a-radio-group
                  :value="checkEntries[ci.itemCode]?.passResult ?? ''"
                  button-style="solid"
                  size="small"
                  @change="(e: any) => setCheckField(ci.itemCode, 'passResult', e.target.value)"
                >
                  <a-radio-button value="PASS">✓ 合格</a-radio-button>
                  <a-radio-button value="FAIL">✗ 不合格</a-radio-button>
                </a-radio-group>
              </div>
              <div v-if="hasCheckType(ci, 'TEXT')" class="cps-check-entry__row">
                <a-textarea
                  :value="checkEntries[ci.itemCode]?.textValue ?? ''"
                  :rows="2"
                  :maxlength="500"
                  :placeholder="ci.textHint || '请填写现场情况说明'"
                  @change="(e: any) => setCheckField(ci.itemCode, 'textValue', e.target.value)"
                />
              </div>
              <div v-if="hasCheckType(ci, 'PHOTO')" class="cps-check-entry__row">
                <div class="cps-check-photos">
                  <div v-for="(p, pi) in checkEntries[ci.itemCode]?.photos ?? []" :key="pi" class="cps-check-photo">
                    <img :src="p.url" :alt="p.name" />
                    <span class="cps-check-photo__del" @click.stop="removeCheckPhoto(ci.itemCode, pi)">×</span>
                  </div>
                  <label class="cps-check-photo__add">
                    <input type="file" accept="image/*" multiple hidden @change="(e: any) => onCheckPhotoPick(ci.itemCode, e)" />
                    <span>＋ {{ (checkEntries[ci.itemCode]?.photos?.length ?? 0) }}/{{ ci.photoMinCount ?? 1 }}</span>
                  </label>
                </div>
              </div>
            </div>
          </a-spin>
        </a-card>

        <a-card class="cps-card" :body-style="{ padding: '16px' }" style="margin-top: 16px">
          <template #title>
            <div class="cps-card-title">
              <h2 class="cps-card-title__main">工厂 / 区域 / 拉线 / 工序</h2>
            </div>
          </template>

          <div class="cps-selector">
            <div class="cps-selector-row">
              <span class="cps-selector-label">工厂</span>
              <a-radio-group :value="draft.location.factory" button-style="solid" class="cps-selector-group" @change="(e: any) => onSelectFactory(e.target.value)">
                <a-radio-button v-for="item in draft.factories" :key="item.value" :value="String(item.value)">{{ item.label }}</a-radio-button>
              </a-radio-group>
              <span v-if="!draft.factories.length" class="cps-muted">暂无工厂数据</span>
            </div>

            <div class="cps-selector-row">
              <span class="cps-selector-label">区域</span>
              <a-radio-group :value="draft.location.area" button-style="solid" class="cps-selector-group" @change="(e: any) => onSelectArea(e.target.value)">
                <a-radio-button v-for="item in draft.areas" :key="item.value" :value="String(item.value)">{{ item.label }}</a-radio-button>
              </a-radio-group>
              <span v-if="!draft.areas.length" class="cps-muted">先选择工厂</span>
            </div>

            <div v-if="draft.location.area" class="cps-selector-row">
              <span class="cps-selector-label">拉线</span>
              <a-radio-group :value="draft.location.line" button-style="solid" class="cps-selector-group" @change="(e: any) => onSelectLine(e.target.value)">
                <a-radio-button v-for="item in draft.lines" :key="item.value" :value="String(item.value)">{{ item.label }}</a-radio-button>
              </a-radio-group>
              <span v-if="!draft.lines.length" class="cps-muted">暂无拉线数据</span>
            </div>

            <div v-if="draft.location.area" class="cps-selector-row">
              <span class="cps-selector-label">工序</span>
              <a-radio-group :value="draft.location.process" button-style="solid" class="cps-selector-group" @change="(e: any) => onSelectProcess(e.target.value)">
                <a-radio-button v-for="item in draft.processes" :key="item.value" :value="String(item.value)">{{ item.label }}</a-radio-button>
              </a-radio-group>
              <span v-if="!draft.processes.length" class="cps-muted">暂无工序数据</span>
            </div>
          </div>
        </a-card>

        <a-alert v-if="inspecting" type="info" show-icon message="AI 正在识别首张图片，请稍候" style="margin-top: 16px" />

        <a-card v-if="aiSuggestionSummary" class="cps-card cps-create-ai" :body-style="{ padding: '16px' }" style="margin-top: 16px">
          <template #title>
            <div class="cps-card-title">
              <h2 class="cps-card-title__main">原因措施</h2>
              <a-tag color="blue">可修改</a-tag>
            </div>
          </template>
          <pre class="cps-create-ai__pre">{{ aiSuggestionSummary }}</pre>
        </a-card>

        <a-card class="cps-card" :body-style="{ padding: '16px' }" style="margin-top: 16px">
          <template #title>
            <div class="cps-card-title">
              <h2 class="cps-card-title__main">反馈人</h2>
            </div>
          </template>
          <a-form layout="vertical">
            <a-form-item label="资源号" required>
              <a-input
                v-model:value="draft.resourceNo"
                placeholder="请输入现场资源号（设备/工位编号等）"
                allow-clear
                :maxlength="64"
              />
            </a-form-item>
            <a-form-item label="反馈人">
              <a-input :value="feedbackPersonLabel" placeholder="选择反馈人" read-only @click="feedbackPickerOpen = true">
                <template #suffix><UserOutlined /></template>
              </a-input>
            </a-form-item>
          </a-form>
        </a-card>
      </a-col>
    </a-row>

    <div v-if="step === 'form'" class="cps-submit-bar">
      <div class="cps-submit-bar__progress">
        <span v-for="item in progressItems" :key="item.label" class="cps-progress-chip" :class="{ 'is-done': item.done }">
          {{ item.done ? '✓' : '·' }} {{ item.label }}
        </span>
      </div>
      <div class="cps-submit-bar__actions">
        <a-button
          size="large"
          :loading="savingDraft"
          :disabled="!canSaveDraft"
          class="cps-draft-btn"
          @click="saveDraft"
        >
          {{ draft.draftId ? '更新暂存' : '暂存' }}
        </a-button>
        <a-button type="primary" size="large" :loading="submitting" :disabled="!canSubmit" class="cps-submit-btn" @click="submit">
          提交并派发
        </a-button>
      </div>
      <p v-if="draft.draftId" class="cps-draft-hint">已关联服务端草稿 #{{ draft.draftId }}，提交后将自动归档</p>
    </div>

    <!-- 反馈人选择弹窗（内联） -->
    <a-modal
      :open="feedbackPickerOpen"
      title="选择反馈人"
      :width="isLandscape ? 520 : '100%'"
      :centered="isLandscape"
      :footer="null"
      :destroy-on-close="false"
      @update:open="(v: boolean) => (feedbackPickerOpen = v)"
    >
      <a-input-search
        v-model:value="feedbackKeyword"
        placeholder="输入姓名或工号搜索"
        allow-clear
        enter-button="搜索"
        :loading="feedbackSearching"
        class="cps-person-picker__search"
        @search="onSearchFeedback"
      />
      <a-spin :spinning="feedbackSearching">
        <div class="cps-person-picker__body">
          <a-empty v-if="!feedbackPeople.length" description="未找到人员，请调整关键词后搜索" />
          <a-radio-group :value="draft.feedbackEmpNo" class="cps-person-picker__group" @change="(e: any) => onSelectFeedback(e.target.value)">
            <a-radio v-for="person in feedbackPeople" :key="person.empNo" :value="person.empNo" class="cps-person-picker__option">
              <strong>{{ person.empName || person.empNo }}</strong>
              <span>{{ person.empNo }}</span>
            </a-radio>
          </a-radio-group>
        </div>
      </a-spin>
    </a-modal>

    <!-- 分区域类型（如辅房点检）：先选区域，再开始巡检 -->
    <a-modal
      :open="areaPickerOpen"
      :title="`选择巡检区域${areaPickerType ? ` · ${areaPickerType.name}` : ''}`"
      :width="isLandscape ? 560 : '100%'"
      :centered="isLandscape"
      :footer="null"
      @update:open="(v: boolean) => (areaPickerOpen = v)"
    >
      <p class="cps-create-card__hint">该类型按区域执行，选定区域后将在该区域下生成点检项快照</p>
      <a-input-search
        v-model:value="areaKeyword"
        class="cps-area-search"
        placeholder="搜索区域名称、编码或房型"
        allow-clear
        :disabled="!areaOptions.length"
      />
      <a-spin :spinning="areaPickerLoading">
        <a-empty v-if="!areaOptions.length && !areaPickerLoading" description="该类型暂无启用区域，请联系管理员配置" />
        <div v-else-if="!filteredAreas.length" class="cps-type-empty">没有匹配「{{ areaKeyword }}」的区域</div>
        <div v-else class="cps-area-pick">
          <button
            v-for="a in filteredAreas"
            :key="a.id"
            type="button"
            class="cps-area-pick__item"
            :disabled="areaPickerStarting !== null"
            @click="startZonedInspection(a)"
          >
            <span class="cps-area-pick__main">
              <strong>{{ a.areaName }}</strong>
              <span class="cps-area-pick__meta">
                {{ a.areaCode }}
                <template v-if="a.lineOrRoomType"> · {{ a.lineOrRoomType }}</template>
                <template v-if="a.process"> · {{ a.process }}</template>
              </span>
            </span>
            <span class="cps-area-pick__risk" :data-risk="a.riskLevel">{{ riskLabel(a.riskLevel) }}</span>
            <span class="cps-area-pick__go">{{ areaPickerStarting === a.id ? '正在开启…' : '开始 →' }}</span>
          </button>
        </div>
      </a-spin>
    </a-modal>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { AudioOutlined, UserOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import type { UploadProps } from 'ant-design-vue'

import { inspectCpsImage, startVoiceRecording, stopVoiceRecording } from '@/api/cps/ai'
import { createCpsIssue } from '@/api/cps/issue'
import { getInspectionTypeCheckItems, listCpsInspectionTypes } from '@/api/cps/inspectionType'
import { listInspectionAreas, startInspection } from '@/api/cps/inspectionTask'
import { getCpsIssueDraft, saveCpsIssueDraft } from '@/api/cps/issueDraft'
import {
  getAreas,
  getFactories,
  getFeedbackHandler,
  getLines,
  getProcesses,
  searchCpsEmployees,
  type CpsEmployeeOption,
} from '@/api/cps/master'
import { uploadCpsAttachment } from '@/api/cps/attachment'
import type { CpsCheckItemInfo, CpsInspectionArea, CpsInspectionType, CpsIssueCheckResult, CpsIssueDraftData, CpsUploadedImage } from '@/types/cps'
import { useFormDraftStore } from '@/store/formDraft'
import { toImageUrl } from '@/utils/image'

const draft = useFormDraftStore()

const submitting = ref(false)
const inspecting = ref(false)
const uploading = ref(false)

// Phase B1/D2：巡检类型与配置联动
const inspectionTypesLoading = ref(false)
const activeTypeName = ref('')

// 分步流程（用户反馈 #3）：select=第一步选类型，form=第二步按类型渲染表单
const step = ref<'select' | 'form'>('select')
// 本次进入是否由「我的草稿」带 draftId 恢复；有值时步骤由 restoreDraft 决定
const pendingDraftId = ref<number | null>(null)

// ===== Phase D2：类型点检项配置联动 =====
interface CheckEntryState {
  passResult?: 'PASS' | 'FAIL' | null
  textValue?: string
  photos?: { id: number; url: string; name: string }[]
}
const typeCheckItems = ref<CpsCheckItemInfo[]>([])
const checkEntries = ref<Record<string, CheckEntryState>>({})
const checkItemsLoading = ref(false)
// Phase D3：自定义类型直接开启巡检执行的加载态
const startingCustom = ref<string | null>(null)

// ===== 分区域类型（如辅房点检）：先选区域，再开始巡检 =====
const areaPickerOpen = ref(false)
const areaPickerType = ref<CpsInspectionType | null>(null)
const areaPickerLoading = ref(false)
const areaPickerStarting = ref<number | null>(null)
const areaOptions = ref<CpsInspectionArea[]>([])

// 区域搜索：名称 / 编码 / 拉线房型 / 工序 / 楼栋门牌
const areaKeyword = ref('')
const filteredAreas = computed(() => {
  const keyword = areaKeyword.value.trim().toLowerCase()
  if (!keyword) return areaOptions.value
  return areaOptions.value.filter((area) =>
    [area.areaName, area.areaCode, area.lineOrRoomType, area.process, area.building, area.doorNo]
      .filter(Boolean)
      .join(' ')
      .toLowerCase()
      .includes(keyword),
  )
})

const riskLabel = (level?: string) =>
  ({ HIGH: '高风险', MEDIUM: '中风险', LOW: '低风险' }[level ?? 'LOW'] ?? level ?? '')

const openAreaPicker = async (t: CpsInspectionType) => {
  areaPickerType.value = t
  areaOptions.value = []
  areaKeyword.value = ''
  areaPickerOpen.value = true
  areaPickerLoading.value = true
  try {
    areaOptions.value = await listInspectionAreas(t.code)
  } catch (error) {
    message.error(error instanceof Error ? error.message : '加载巡检区域失败')
  } finally {
    areaPickerLoading.value = false
  }
}

const startZonedInspection = async (area: CpsInspectionArea) => {
  const t = areaPickerType.value
  if (!t || areaPickerStarting.value !== null) return
  areaPickerStarting.value = area.id
  try {
    const record = await startInspection({ typeCode: t.code, areaId: area.id })
    areaPickerOpen.value = false
    uni.redirectTo({ url: `/views/cps/InspectionExecuteView?recordId=${record.id}` })
  } catch (error) {
    message.error(error instanceof Error ? error.message : '开始巡检失败（请确认该区域已配置点检项）')
  } finally {
    areaPickerStarting.value = null
  }
}

/** 当前选中类型完整对象 */
const activeType = computed(() =>
  draft.inspectionTypes.find((x) => x.code === draft.inspectionTypeCode) ?? null,
)
/** 类型「巡检内容」横幅 */
const activeTypeContent = computed(() => activeType.value?.content || '')

const hasCheckType = (ci: CpsCheckItemInfo, t: string) => (ci.checkTypes || '').split(',').includes(t)

const setCheckField = (itemCode: string, key: keyof CheckEntryState, value: unknown) => {
  checkEntries.value = {
    ...checkEntries.value,
    [itemCode]: { ...checkEntries.value[itemCode], [key]: value },
  }
}

const isCheckEntryDone = (ci: CpsCheckItemInfo): boolean => {
  const entry = checkEntries.value[ci.itemCode] ?? {}
  if (hasCheckType(ci, 'PASS_FAIL') && !entry.passResult) return false
  if (hasCheckType(ci, 'TEXT') && !(entry.textValue ?? '').trim()) return false
  if (hasCheckType(ci, 'PHOTO') && (entry.photos?.length ?? 0) < (ci.photoMinCount ?? 1)) return false
  return true
}

const checkDoneCount = computed(() => typeCheckItems.value.filter(isCheckEntryDone).length)
const checkItemsDone = computed(
  () => typeCheckItems.value.length === 0 || typeCheckItems.value.every(isCheckEntryDone),
)

/** 拉取所选类型的点检项配置（管理端「直挂点检项」） */
const loadTypeCheckItems = async (code: string) => {
  checkItemsLoading.value = true
  try {
    typeCheckItems.value = await getInspectionTypeCheckItems(code)
  } catch {
    typeCheckItems.value = []
  } finally {
    checkItemsLoading.value = false
  }
}

const onCheckPhotoPick = async (itemCode: string, e: Event) => {
  const input = e.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  input.value = ''
  if (!files.length) return
  try {
    const current = [...(checkEntries.value[itemCode]?.photos ?? [])]
    for (const file of files) {
      const uploaded = await uploadCpsAttachment(file)
      current.push({ id: uploaded.id, url: uploaded.url, name: uploaded.name })
    }
    setCheckField(itemCode, 'photos', current)
  } catch (error) {
    message.error(error instanceof Error ? error.message : '照片上传失败')
  }
}

const removeCheckPhoto = (itemCode: string, index: number) => {
  const current = (checkEntries.value[itemCode]?.photos ?? []).filter((_, i) => i !== index)
  setCheckField(itemCode, 'photos', current)
}

/** 收集点检结果（提交时并入 extraData.checkResults） */
const collectCheckResults = (): CpsIssueCheckResult[] =>
  typeCheckItems.value.map((ci) => {
    const entry = checkEntries.value[ci.itemCode] ?? {}
    return {
      itemCode: ci.itemCode,
      content: ci.content,
      passResult: entry.passResult ?? null,
      textValue: entry.textValue ?? null,
      photoUrls: (entry.photos ?? []).map((p) => p.url),
      photoNames: (entry.photos ?? []).map((p) => p.name),
      photoCount: entry.photos?.length ?? 0,
    }
  })

/** 从 extraData.checkResults 恢复填写状态（草稿/重进） */
const hydrateCheckResults = (results: CpsIssueCheckResult[]) => {
  const next: Record<string, CheckEntryState> = {}
  for (const r of results ?? []) {
    next[r.itemCode] = {
      passResult: r.passResult ?? null,
      textValue: r.textValue ?? '',
      photos: (r.photoUrls ?? []).map((url, i) => ({ id: -(i + 1), url, name: r.photoNames?.[i] ?? `照片${i + 1}` })),
    }
  }
  checkEntries.value = next
}

const onSelectInspectionType = async (code: string) => {
  if (!code) {
    draft.inspectionTypeCode = null
    activeTypeName.value = ''
    typeCheckItems.value = []
    checkEntries.value = {}
    return
  }
  const t = draft.inspectionTypes.find((x) => x.code === code)
  // ===== Phase D3：按类型分流 =====
  // 分区域类型（如辅房点检）：先弹区域选择，选定区域后开启巡检执行。
  if (t && t.zoneFlag === 1) {
    void openAreaPicker(t)
    return
  }
  // 非内置 CPS巡检 的自定义类型：不复用问题上报表单，直接开启一次巡检执行，
  // 跳转执行页按该类型的点检项配置逐项填写（合格/文本/拍照）并计分提交。
  if (t && t.builtin !== 1) {
    if (startingCustom.value) return
    startingCustom.value = code
    try {
      const record = await startInspection({ typeCode: code })
      uni.redirectTo({ url: `/views/cps/InspectionExecuteView?recordId=${record.id}` })
    } catch (error) {
      message.error(error instanceof Error ? error.message : '开始巡检失败（请确认该类型已配置点检项）')
    } finally {
      startingCustom.value = null
    }
    return
  }
  // 内置 CPS巡检：走标准问题上报流程（原表单）
  if (draft.inspectionTypeCode === code) {
    step.value = 'form'
    return
  }
  draft.inspectionTypeCode = code
  activeTypeName.value = t?.name || code
  draft.extraData = {}
  checkEntries.value = {}
  typeCheckItems.value = []
  step.value = 'form'
}

/** 切换类型：回到第一步；仅清空类型与点检填写，通用内容（照片/描述/位置）保留 */
const switchType = () => {
  draft.inspectionTypeCode = null
  draft.extraData = {}
  activeTypeName.value = ''
  typeCheckItems.value = []
  checkEntries.value = {}
  step.value = 'select'
}

const loadInspectionTypes = async () => {
  if (draft.inspectionTypes.length || inspectionTypesLoading.value) return
  inspectionTypesLoading.value = true
  try {
    const list: CpsInspectionType[] = await listCpsInspectionTypes()
    // Phase C/D：全量列出后端返回的可用类型（含分区域类型，如辅房点检）
    // 内置 CPS巡检（builtin=1）排在最前
    draft.inspectionTypes = list.sort(
      (a, b) => (b.builtin ?? 0) - (a.builtin ?? 0) || (a.sortOrder ?? 0) - (b.sortOrder ?? 0),
    )
  } catch (error) {
    message.error(error instanceof Error ? error.message : '加载巡检类型失败')
  } finally {
    inspectionTypesLoading.value = false
  }
}

// 反馈人选择器状态（内联）
const feedbackPickerOpen = ref(false)
const feedbackKeyword = ref('')
const feedbackSearching = ref(false)
const feedbackSearched = ref(false)
const feedbackSearchResults = ref<CpsEmployeeOption[]>([])
const feedbackCandidates = ref<CpsEmployeeOption[]>([])

// 横竖屏断点：lg 以上视为横屏（平板宽屏）
const isLandscape = ref(false)
const updateOrientation = () => {
  isLandscape.value = window.innerWidth >= 992
}
if (typeof window !== 'undefined') {
  updateOrientation()
  window.addEventListener('resize', updateOrientation)
}

const aiSuggestionSummary = computed(() => {
  if (!draft.aiSuggestion) return ''
  return [
    draft.aiSuggestion.reasonSuggestion ? `原因建议：${draft.aiSuggestion.reasonSuggestion}` : '',
    draft.aiSuggestion.measureSuggestion ? `措施建议：${draft.aiSuggestion.measureSuggestion}` : '',
  ]
    .filter(Boolean)
    .join('\n')
})

const feedbackPersonLabel = computed(() => {
  if (!draft.feedbackEmpNo) return ''
  const name = draft.feedbackPerson?.empName
  return name && name !== draft.feedbackEmpNo ? `${name} (${draft.feedbackEmpNo})` : draft.feedbackEmpNo
})

// 未搜索时展示候选；搜索后展示搜索结果（合并去重）
const feedbackPeople = computed<CpsEmployeeOption[]>(() => {
  if (feedbackSearched.value) {
    return [
      ...feedbackSearchResults.value,
      ...feedbackCandidates.value.filter((c) => !feedbackSearchResults.value.some((s) => s.empNo === c.empNo)),
    ]
  }
  return feedbackCandidates.value
})

const uploadFileList = computed(() =>
  draft.images.map((image) => ({
    uid: String(image.id),
    name: image.name,
    status: 'done' as const,
    url: draft.imagePreviewSources[image.id],
  })),
)

interface ProgressItem {
  label: string
  done: boolean
}
// 顶部三步进度：选类型 → 填表单 → 提交（提交即底部动作条）
const stepLabels = ['选类型', '填表单', '提交']
const stepIndex = computed(() => (step.value === 'select' ? 0 : 1))

const typeActionLabel = (type: CpsInspectionType) => {
  if (startingCustom.value === type.code) return '正在开启巡检…'
  if (type.builtin === 1) return '上报问题'
  if (type.zoneFlag === 1) return '选择区域'
  return '开始巡检'
}

// 类型搜索：名称 / 编码 / 巡检内容 / 描述
const typeKeyword = ref('')
const filteredTypes = computed(() => {
  const keyword = typeKeyword.value.trim().toLowerCase()
  if (!keyword) return draft.inspectionTypes
  return draft.inspectionTypes.filter((type) =>
    [type.name, type.code, type.content, type.description]
      .filter(Boolean)
      .join(' ')
      .toLowerCase()
      .includes(keyword),
  )
})
const typeCountLabel = computed(() => {
  const total = draft.inspectionTypes.length
  return typeKeyword.value.trim() ? `${filteredTypes.value.length}/${total} 种` : `${total} 种可选`
})

const progressItems = computed<ProgressItem[]>(() => [
  { label: '照片', done: draft.images.length >= 1 && draft.images.length <= 5 },
  { label: '位置', done: Boolean(draft.location.factory && draft.location.area && draft.location.line && draft.location.process && draft.resourceNo.trim()) },
  { label: 'AI分类', done: Boolean(draft.aiSuggestion?.aiCategoryL1Id && draft.aiSuggestion?.aiCategoryL2Id) },
  { label: '描述', done: Boolean(draft.description.trim()) },
])

const canSubmit = computed(
  () =>
    draft.images.length >= 1 &&
    draft.images.length <= 5 &&
    Boolean(draft.location.factory) &&
    Boolean(draft.location.area) &&
    Boolean(draft.location.line) &&
    Boolean(draft.location.process) &&
    Boolean(draft.resourceNo.trim()) &&
    Boolean(draft.inspectionTypeCode) &&
    Boolean(draft.aiSuggestion?.aiCategoryL1Id) &&
    Boolean(draft.aiSuggestion?.aiCategoryL2Id) &&
    Boolean(draft.description.trim()) &&
    Boolean(draft.feedbackEmpNo.trim()) &&
    checkItemsDone.value &&
    !submitting.value,
)

// 位置完整后预填反馈人候选（第一次：不自动选中，仅预填列表，沿用源码行为）
watch(
  () => ({ ...draft.location }),
  async (value) => {
    if (!value.factory || !value.area || !value.line || !value.process) {
      draft.feedbackEmpNo = ''
      draft.feedbackPerson = null
      feedbackCandidates.value = []
      return
    }
    const handler = await getFeedbackHandler({
      factory: value.factory,
      area: value.area,
      line: value.line,
      process: value.process,
    })
    draft.feedbackEmpNo = ''
    draft.feedbackPerson = null
    feedbackCandidates.value = handler ? [handler] : []
  },
)

const onSelectFactory = async (factory: string) => {
  draft.location = { factory, area: '', line: '', process: '' }
  draft.areas = await getAreas(factory)
  draft.lines = []
  draft.processes = []
}
const onSelectArea = async (area: string) => {
  draft.location = { ...draft.location, area, line: '', process: '' }
  draft.lines = await getLines(draft.location.factory, area)
  draft.processes = await getProcesses(draft.location.factory, area)
}
const onSelectLine = async (line: string) => {
  draft.location = { ...draft.location, line, process: '' }
  draft.processes = await getProcesses(draft.location.factory, draft.location.area, line || undefined)
}
const onSelectProcess = (process: string) => {
  draft.location = { ...draft.location, process }
}

const onSearchFeedback = async () => {
  const kw = feedbackKeyword.value.trim()
  if (!kw || feedbackSearching.value) return
  feedbackSearching.value = true
  try {
    feedbackSearchResults.value = await searchCpsEmployees(kw)
    feedbackSearched.value = true
  } finally {
    feedbackSearching.value = false
  }
}

const onSelectFeedback = (empNo: string) => {
  const person = feedbackPeople.value.find((p) => p.empNo === empNo)
  if (person) {
    draft.feedbackEmpNo = empNo
    draft.feedbackPerson = person
    feedbackPickerOpen.value = false
  }
}

watch(feedbackPickerOpen, (v) => {
  if (v) {
    feedbackKeyword.value = ''
    feedbackSearched.value = false
    feedbackSearchResults.value = []
  }
})

// 图片上传
const beforeUpload: UploadProps['beforeUpload'] = (_file, fileList) => {
  const remaining = Math.max(5 - draft.images.length, 0)
  if (fileList.length > remaining) return false
  return true
}

const customRequest: UploadProps['customRequest'] = async (options) => {
  const { file, onSuccess, onError } = options
  const shouldInspectFirst = draft.images.length === 0
  uploading.value = true
  try {
    const uploaded = await uploadCpsAttachment(file as File)
    draft.images = [...draft.images, uploaded]
    // 直接使用后端返回的 /api/cps/attachments/{id}/content 路径作为预览地址，
    // 免掉 base64 转换与 /api/cps/attachments/base64 接口（已下线）。
    draft.imagePreviewSources = { ...draft.imagePreviewSources, [uploaded.id]: uploaded.url }
    onSuccess?.({}, file as any)
    if (shouldInspectFirst) {
      await onFirstImageReady(uploaded.id)
    }
  } catch (error) {
    onError?.(error as any)
  } finally {
    uploading.value = false
  }
}

const onRemoveImage = (index: number) => {
  draft.images = draft.images.filter((_, i) => i !== index)
}

const onPreviewImage = (index: number) => {
  const target = draft.images[index]
  if (!target) return
  const urls = draft.images
    .map((i) => draft.imagePreviewSources[i.id] || i.url)
    .filter(Boolean)
  if (typeof uni !== 'undefined' && typeof uni.previewImage === 'function') {
    uni.previewImage({ urls, current: draft.imagePreviewSources[target.id] || target.url })
  }
}

const onFirstImageReady = async (fileId: number) => {
  inspecting.value = true
  try {
    // 创建阶段仅保存 AI 识别结果，分类由反馈回复节点确认/修正
    draft.aiSuggestion = await inspectCpsImage(fileId)
  } finally {
    inspecting.value = false
  }
}

// 语音输入：点击开始录音、再点击结束并转写
const recording = ref(false)
const transcribing = ref(false)

const onVoiceClick = async () => {
  // 正在录音 → 结束并转写
  if (recording.value) {
    recording.value = false
    transcribing.value = true
    try {
      const text = await stopVoiceRecording()
      if (text.trim()) {
        draft.description = [draft.description.trim(), text.trim()].filter(Boolean).join('\n')
      } else {
        message.info('未识别到语音内容')
      }
    } catch (error) {
      message.error(error instanceof Error ? error.message : '语音识别失败')
    } finally {
      transcribing.value = false
    }
    return
  }
  // 未录音 → 开始
  if (transcribing.value) return
  const started = await startVoiceRecording()
  if (!started) {
    message.error('当前环境不支持语音录入')
    return
  }
  recording.value = true
}

// ============================================================
// Phase B2：服务端草稿（暂存 / 恢复）
// ============================================================
const savingDraft = ref(false)

/** 暂存门槛：比提交宽松——只要填了任意内容即可暂存。 */
const canSaveDraft = computed(
  () =>
    (draft.images.length > 0 ||
      Boolean(draft.description.trim()) ||
      Boolean(draft.resourceNo.trim()) ||
      Boolean(draft.inspectionTypeCode) ||
      Boolean(draft.location.factory)) &&
    !savingDraft.value,
)

/** 把 store 序列化为服务端草稿快照（含点检项填写结果）。 */
const serializeDraft = (): CpsIssueDraftData => ({
  location: { ...draft.location },
  description: draft.description,
  resourceNo: draft.resourceNo,
  inspectionTypeCode: draft.inspectionTypeCode,
  extraData: {
    ...draft.extraData,
    ...(typeCheckItems.value.length ? { checkResults: collectCheckResults() } : {}),
  },
  feedbackEmpNo: draft.feedbackEmpNo,
  feedbackPerson: draft.feedbackPerson
    ? { empNo: draft.feedbackPerson.empNo, empName: draft.feedbackPerson.empName ?? null }
    : null,
  images: [...draft.images],
  imagePreviewSources: { ...draft.imagePreviewSources },
  aiSuggestion: draft.aiSuggestion,
})

/** 暂存（或覆盖更新）到服务端。 */
const saveDraft = async () => {
  if (!canSaveDraft.value) return
  savingDraft.value = true
  try {
    const snapshot = serializeDraft()
    const saved = await saveCpsIssueDraft({
      id: draft.draftId,
      inspectionTypeCode: snapshot.inspectionTypeCode,
      factory: snapshot.location.factory || undefined,
      area: snapshot.location.area || undefined,
      draftData: JSON.stringify(snapshot),
    })
    draft.draftId = saved.id
    message.success(`已暂存（草稿 #${saved.id}），可稍后从「我的草稿」继续填写`)
  } catch (error) {
    message.error(error instanceof Error ? error.message : '暂存失败')
  } finally {
    savingDraft.value = false
  }
}

/** 从服务端草稿恢复整份表单。 */
const restoreDraft = async (id: number) => {
  try {
    const remote = await getCpsIssueDraft(id)
    let data: CpsIssueDraftData
    try {
      data = JSON.parse(remote.draftData) as CpsIssueDraftData
    } catch {
      message.error('草稿数据损坏，无法恢复')
      return
    }
    draft.location = data.location ?? { factory: '', area: '', line: '', process: '' }
    draft.description = data.description ?? ''
    draft.resourceNo = data.resourceNo ?? ''
    draft.inspectionTypeCode = data.inspectionTypeCode ?? null
    draft.extraData = data.extraData ?? {}
    draft.feedbackEmpNo = data.feedbackEmpNo ?? ''
    draft.feedbackPerson = (data.feedbackPerson as never) ?? null
    draft.images = data.images ?? []
    draft.imagePreviewSources = data.imagePreviewSources ?? {}
    draft.aiSuggestion = data.aiSuggestion ?? null
    draft.draftId = remote.id
    // 恢复的类型非空 → 直接进入第二步表单
    step.value = draft.inspectionTypeCode ? 'form' : 'select'
    // 恢复类型名与点检项配置（Phase D2），并回填点检填写结果
    if (draft.inspectionTypeCode) {
      const t = draft.inspectionTypes.find((x) => x.code === draft.inspectionTypeCode)
      activeTypeName.value = t?.name || draft.inspectionTypeCode
      await loadTypeCheckItems(draft.inspectionTypeCode)
      const results = (draft.extraData as Record<string, unknown>).checkResults as CpsIssueCheckResult[] | undefined
      if (results) hydrateCheckResults(results)
    }
    if (draft.location.factory) {
      draft.areas = await getAreas(draft.location.factory)
      if (draft.location.area) {
        draft.lines = await getLines(draft.location.factory, draft.location.area)
        draft.processes = await getProcesses(draft.location.factory, draft.location.area)
      }
    }
    message.success(`已恢复草稿 #${remote.id}，继续填写`)
  } catch (error) {
    message.error(error instanceof Error ? error.message : '恢复草稿失败')
  }
}

const submit = async () => {
  if (!canSubmit.value) return
  submitting.value = true
  try {
    // Phase D2：点检项填写结果并入 extraData（与管理端类型配置联动）
    const extraPayload: Record<string, unknown> = { ...draft.extraData }
    if (typeCheckItems.value.length) extraPayload.checkResults = collectCheckResults()
    const result = await createCpsIssue({
      factory: draft.location.factory,
      area: draft.location.area,
      line: draft.location.line,
      process: draft.location.process,
      resourceNo: draft.resourceNo.trim(),
      inspectionTypeCode: draft.inspectionTypeCode ?? undefined,
      extraData: Object.keys(extraPayload).length ? JSON.stringify(extraPayload) : undefined,
      draftId: draft.draftId ?? undefined,
      aiCategoryL1Id: draft.aiSuggestion?.aiCategoryL1Id ?? null,
      aiCategoryL2Id: draft.aiSuggestion?.aiCategoryL2Id ?? null,
      // 创建时直接采用 AI 分类，后续在反馈回复节点由反馈人员确认/修正
      categoryL1Id: draft.aiSuggestion?.aiCategoryL1Id!,
      categoryL2Id: draft.aiSuggestion?.aiCategoryL2Id!,
      description: draft.description.trim(),
      feedbackEmpNo: draft.feedbackEmpNo.trim(),
      feedbackEmpName: draft.feedbackPerson?.empName,
      issueAttachmentIds: draft.images.map((image) => image.id),
      aiSuggestion: draft.aiSuggestion ?? undefined,
    })
    draft.reset()
    uni.redirectTo({ url: `/views/cps/IssueDetailView?id=${result.issueId}` })
  } catch (error) {
    message.error(error instanceof Error ? error.message : '提交失败')
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  if (!draft.factories.length) {
    draft.factories = await getFactories()
  }
  await loadInspectionTypes()
  // 从列表再次进入「新建问题」时，始终回到第一步重新选类型；
  // 上次选择的类型仍留在 store（避免反复拉接口），但不再自动跳进旧类型的表单。
  // 仅当本次是从「我的草稿」恢复（?draftId=）时，步骤交给 restoreDraft 决定。
  if (pendingDraftId.value) return
  step.value = 'select'
})

// 从「我的草稿」入口跳入：?draftId=x 时整包恢复（优先级高于本地内存 store）
onLoad((query?: Record<string, string | string[] | undefined>) => {
  const raw = Array.isArray(query?.draftId) ? query?.draftId[0] : query?.draftId
  const draftIdParam = Number(raw)
  if (raw !== undefined && Number.isFinite(draftIdParam) && draftIdParam > 0) {
    pendingDraftId.value = draftIdParam
    void restoreDraft(draftIdParam)
  }
})
</script>

<style scoped>
.cps-page {
  width: 100%;
  max-width: 1280px;
  margin: 0 auto;
  padding: 16px 20px 120px;
}

.cps-hero {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  border-radius: var(--cps-radius-xl);
  padding: 20px 24px;
  background: var(--cps-hero-bg);
  box-shadow: var(--cps-shadow-hero);
  color: #fff;
  margin-bottom: 16px;
}

.cps-hero__main {
  display: grid;
  gap: 6px;
}

.cps-hero__eyebrow {
  margin: 0;
  color: rgba(255, 255, 255, 0.82);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.18em;
}

.cps-hero h1 {
  margin: 0;
  font-size: 24px;
  font-weight: 800;
}

/* 顶部三步进度 */
.cps-steps {
  display: flex;
  gap: 6px;
  margin: 12px 0 0;
  padding: 0;
  list-style: none;
  overflow-x: auto;
}

.cps-steps__item {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 6px;
  border-radius: var(--cps-radius-pill);
  padding: 5px 10px 5px 6px;
  background: rgba(255, 255, 255, 0.12);
  color: rgba(255, 255, 255, 0.72);
  font-size: 12px;
  font-weight: 700;
}

.cps-steps__num {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.2);
  font-size: 11px;
  font-weight: 800;
}

.cps-steps__item.is-done {
  color: rgba(255, 255, 255, 0.92);
}

.cps-steps__item.is-done .cps-steps__num {
  background: rgba(255, 255, 255, 0.42);
  color: #115e59;
}

.cps-steps__item.is-active {
  background: #fff;
  color: var(--cps-teal-deep);
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.18);
}

.cps-steps__item.is-active .cps-steps__num {
  background: var(--cps-teal-deep);
  color: #fff;
}

.cps-hero__badge {
  align-self: flex-start;
  border-radius: 999px !important;
  font-weight: 800 !important;
}

.cps-card {
  border-radius: var(--cps-radius-lg);
  border: 1px solid var(--cps-border);
  box-shadow: var(--cps-shadow-card);
}

.cps-card :deep(.ant-card-head) {
  min-height: 54px;
  padding: 0 16px;
  border-bottom: 1px solid var(--cps-border);
}

.cps-card-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.cps-card-title__main {
  margin: 0;
  color: var(--cps-ink);
  font-size: 17px;
  font-weight: 800;
  letter-spacing: -0.01em;
}

.cps-create-card__hint {
  margin: 0 0 12px;
  color: #64748b;
  font-size: 13px;
}

.cps-desc-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-top: 10px;
}

.cps-voice-hint {
  margin: 0;
  color: #0f766e;
  font-size: 12px;
  font-weight: 600;
}

/* 图片上传（内联） */
.cps-uploader__trigger {
  display: grid;
  align-items: center;
  justify-items: center;
  gap: 4px;
  width: 100%;
  height: 100%;
  color: #0f766e;
}

.cps-create-page :deep(.ant-upload.ant-upload-select) {
  width: 100%;
  height: auto;
  margin-bottom: 12px;
}

.cps-create-page :deep(.ant-upload.ant-upload-select .ant-upload) {
  width: 100%;
  padding: 18px;
  border-radius: var(--cps-radius-lg);
  background: var(--cps-mist);
  border: 1px dashed #99f6e4;
}

.cps-uploader__plus {
  font-size: 28px;
  line-height: 1;
}

.cps-uploader__text {
  font-size: 13px;
  font-weight: 700;
}

.cps-uploader__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  gap: 12px;
}

.cps-uploader__item {
  position: relative;
  width: 100%;
  aspect-ratio: 1;
  border-radius: 12px;
  overflow: hidden;
  background: #e2e8f0;
  cursor: pointer;
}

.cps-uploader__item img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cps-uploader__delete {
  position: absolute;
  top: 6px;
  right: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.68);
  color: #fff;
  font-size: 16px;
  font-weight: 900;
  line-height: 1;
}

.cps-uploader__index {
  position: absolute;
  bottom: 6px;
  right: 6px;
  min-width: 22px;
  height: 22px;
  border-radius: 999px;
  padding: 0 6px;
  background: rgba(15, 23, 42, 0.72);
  color: #fff;
  font-size: 12px;
  font-weight: 900;
  line-height: 22px;
  text-align: center;
}

/* 位置/分类选择（内联） */
.cps-selector {
  display: grid;
  gap: 12px;
}

.cps-selector-row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.cps-selector-label {
  flex: 0 0 56px;
  padding-top: 6px;
  color: #0f172a;
  font-size: 14px;
  font-weight: 700;
}

.cps-selector-group {
  display: flex;
  flex: 1 1 auto;
  flex-wrap: wrap;
  gap: 8px;
  min-width: 0;
}

.cps-selector-group :deep(.ant-radio-button-wrapper) {
  border-radius: 999px;
  border: 1px solid #cbd5e1;
  height: 36px;
  line-height: 34px;
  padding: 0 16px;
}

.cps-selector-group :deep(.ant-radio-button-wrapper:not(:first-child))::before {
  display: none;
}

.cps-muted {
  flex: 1 1 auto;
  padding-top: 6px;
  color: #64748b;
  font-size: 14px;
  white-space: nowrap;
}

/* ===== 第一步：巡检类型选择 ===== */
.cps-type-search,
.cps-area-search {
  margin-bottom: 12px;
}

.cps-type-empty {
  padding: 28px 0;
  text-align: center;
  color: #94a3b8;
  font-size: 14px;
}

.cps-area-pick {
  display: grid;
  gap: 8px;
}

.cps-area-pick__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  width: 100%;
  margin: 0;
  padding: 12px 14px;
  border: 1px solid var(--cps-border);
  border-radius: var(--cps-radius-md);
  background: var(--cps-cloud);
  text-align: left;
  cursor: pointer;
}

.cps-area-pick__item::after {
  border: none;
}

.cps-area-pick__item:active {
  transform: scale(0.99);
}

.cps-area-pick__item:disabled {
  opacity: 0.6;
}

.cps-area-pick__main {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.cps-area-pick__main strong {
  color: #0f172a;
  font-size: 14px;
  font-weight: 700;
}

.cps-area-pick__meta {
  color: #64748b;
  font-size: 12px;
}

.cps-area-pick__risk {
  flex-shrink: 0;
  border-radius: var(--cps-radius-pill);
  padding: 2px 8px;
  font-size: 12px;
  font-weight: 700;
  background: rgba(20, 184, 166, 0.12);
  color: var(--cps-teal-deep);
}

.cps-area-pick__risk[data-risk='HIGH'] {
  background: rgba(239, 68, 68, 0.12);
  color: #dc2626;
}

.cps-area-pick__risk[data-risk='MEDIUM'] {
  background: rgba(249, 115, 22, 0.12);
  color: #ea580c;
}

.cps-area-pick__go {
  flex-shrink: 0;
  color: var(--cps-teal-deep);
  font-size: 13px;
  font-weight: 700;
}

.cps-type-select__grid {
  display: grid;
  /* 固定列数均分：任何数量的卡片宽度都严格一致（auto-fit 会把不满一行的最后一张拉通整行，导致长短不一） */
  grid-template-columns: 1fr;
  gap: 12px;
}

@media (min-width: 640px) {
  .cps-type-select__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (min-width: 1024px) {
  .cps-type-select__grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

.cps-type-select__item {
  display: grid;
  align-content: start;
  gap: 6px;
  width: 100%;
  margin: 0;
  box-sizing: border-box;
  padding: 16px;
  border: 1.5px solid var(--cps-border);
  border-radius: var(--cps-radius-lg);
  background: #fff;
  text-align: left;
  cursor: pointer;
  transition: transform 140ms ease, box-shadow 140ms ease, border-color 140ms ease;
  /* grid 子项默认 min-width:auto 会被长文本撑宽轨道 → 显式归零保证各列等宽 */
  min-width: 0;
}

.cps-type-select__item::after {
  border: none;
}

.cps-type-select__item:hover {
  border-color: rgba(20, 184, 166, 0.45);
  box-shadow: 0 8px 20px rgba(15, 118, 110, 0.12);
  transform: translateY(-2px);
}

.cps-type-select__item:active {
  transform: translateY(0) scale(0.99);
}

.cps-type-select__item:disabled {
  opacity: 0.6;
  cursor: progress;
}

.cps-type-select__head {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.cps-type-select__tag {
  flex-shrink: 0;
  border-radius: var(--cps-radius-pill) !important;
  font-weight: 700 !important;
}

.cps-type-select__item[data-color='red'] {
  border-top: 4px solid #ef4444;
}

.cps-type-select__item[data-color='orange'] {
  border-top: 4px solid #f97316;
}

.cps-type-select__item[data-color='green'] {
  border-top: 4px solid #22c55e;
}

.cps-type-select__item[data-color='blue'],
.cps-type-select__item {
  border-top: 4px solid #14b8a6;
}

.cps-type-select__name {
  color: #0f172a;
  font-size: 17px;
  font-weight: 800;
  overflow-wrap: break-word;
}

/* ===== Phase D2：类型配置联动（巡检内容横幅 + 点检项填写） ===== */
.cps-type-content-banner {
  margin: 0;
  color: #475569;
  font-size: 13px;
  line-height: 20px;
}

.cps-check-entry {
  display: grid;
  gap: 8px;
  padding: 12px;
  border: 1px solid var(--cps-border);
  border-radius: var(--cps-radius-md);
  background: var(--cps-cloud);
}

.cps-check-entry + .cps-check-entry {
  margin-top: 10px;
}

.cps-check-entry--done {
  border-left: 4px solid #22c55e;
}

.cps-check-entry__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}

.cps-check-entry__head strong {
  color: #0f172a;
  font-size: 14px;
  line-height: 20px;
}

.cps-check-entry__code {
  color: #94a3b8;
  font-size: 12px;
  font-weight: 700;
  flex-shrink: 0;
}

.cps-check-entry__criteria {
  margin: 0;
  padding: 6px 10px;
  border-radius: 8px;
  background: #fff7ed;
  color: #9a3412;
  font-size: 12px;
  line-height: 18px;
}

.cps-check-entry__row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.cps-check-photos {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(76px, 1fr));
  gap: 8px;
  width: 100%;
}

.cps-check-photo {
  position: relative;
  width: 100%;
  aspect-ratio: 1;
  border-radius: 8px;
  overflow: hidden;
  background: #e2e8f0;
}

.cps-check-photo img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cps-check-photo__del {
  position: absolute;
  top: 3px;
  right: 3px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.68);
  color: #fff;
  font-size: 13px;
  font-weight: 900;
}

.cps-check-photo__add {
  display: grid;
  place-items: center;
  width: 100%;
  aspect-ratio: 1;
  border-radius: 8px;
  border: 1px dashed #99f6e4;
  background: var(--cps-mist);
  color: #0f766e;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
}

.cps-type-select__code {
  color: #94a3b8;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.06em;
  overflow-wrap: break-word;
}

.cps-type-select__desc {
  color: #64748b;
  font-size: 13px;
  line-height: 19px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  overflow-wrap: break-word;
}

.cps-type-select__go {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 4px;
  padding-top: 10px;
  border-top: 1px dashed var(--cps-border);
  color: var(--cps-teal-deep);
  font-size: 13px;
  font-weight: 800;
}

.cps-type-select__arrow {
  font-size: 15px;
  transition: transform 140ms ease;
}

.cps-type-select__item:hover .cps-type-select__arrow {
  transform: translateX(3px);
}

/* ===== 第二步：当前类型指示卡 ===== */
.cps-type-active {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.cps-type-active__info {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.cps-type-active__label {
  color: #94a3b8;
  font-size: 12px;
  font-weight: 700;
}

.cps-type-active__name {
  display: flex;
  align-items: center;
  color: #0f172a;
  font-size: 16px;
  font-weight: 800;
}

.cps-type-active__code {
  margin-left: 8px;
  border-radius: var(--cps-radius-pill);
  padding: 1px 9px;
  background: var(--cps-mist);
  color: var(--cps-teal-deep);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.02em;
}

.cps-create-ai {
  border-left: 4px solid #2563eb;
}

.cps-create-ai__pre {
  margin: 0;
  padding: 12px;
  border-radius: 10px;
  background: #f0fdfa;
  color: #0f172a;
  font-family: inherit;
  font-size: 14px;
  line-height: 22px;
  white-space: pre-wrap;
  word-break: break-word;
}

/* 人员选择弹窗（内联） */
.cps-person-picker__search {
  margin-bottom: 16px;
}

.cps-person-picker__body {
  max-height: 52vh;
  overflow-y: auto;
  padding-right: 4px;
}

.cps-person-picker__group {
  display: grid;
  gap: 8px;
  width: 100%;
}

.cps-person-picker__option {
  display: grid;
  grid-template-columns: 24px 1fr;
  align-items: center;
  width: 100%;
  min-height: 56px;
  padding: 8px 12px;
  border-radius: var(--cps-radius-md);
  background: var(--cps-cloud);
}

.cps-person-picker__option :deep(.ant-radio + span) {
  display: grid;
  gap: 2px;
  padding-right: 8px;
}

.cps-person-picker__option :deep(strong) {
  color: #0f172a;
  font-size: 15px;
  font-weight: 700;
  line-height: 22px;
}

.cps-person-picker__option :deep(span:not(.ant-radio)) {
  color: #64748b;
  font-size: 13px;
  line-height: 18px;
}

.cps-submit-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 99;
  max-width: 1280px;
  margin: 0 auto;
  padding: 12px 20px max(12px, env(safe-area-inset-bottom));
  background: rgba(248, 250, 252, 0.97);
  border-top: 1px solid var(--cps-border);
  box-shadow: 0 -8px 24px rgba(15, 23, 42, 0.06);
}

.cps-submit-bar__progress {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 6px;
  margin-bottom: 8px;
}

.cps-progress-chip {
  border-radius: var(--cps-radius-pill);
  padding: 3px 10px;
  background: #fff;
  border: 1px solid var(--cps-border);
  color: #94a3b8;
  font-size: 12px;
  font-weight: 700;
}

.cps-progress-chip.is-done {
  border-color: rgba(20, 184, 166, 0.4);
  background: var(--cps-mist);
  color: var(--cps-teal-deep);
}

.cps-submit-bar__actions {
  display: flex;
  gap: 12px;
}

.cps-draft-btn {
  flex: 0 0 34%;
  height: 48px !important;
  border-radius: var(--cps-radius-pill) !important;
  border: 1.5px solid var(--cps-teal-deep) !important;
  color: var(--cps-teal-deep) !important;
  background: #fff !important;
  font-size: 15px !important;
  font-weight: 700 !important;
}

.cps-draft-hint {
  margin: 8px 0 0;
  text-align: center;
  color: #94a3b8;
  font-size: 12px;
}

.cps-submit-btn {
  flex: 1 1 auto;
  height: 48px !important;
  border-radius: var(--cps-radius-pill) !important;
  background: var(--cps-teal-deep) !important;
  border: none !important;
  font-size: 16px !important;
  font-weight: 700 !important;
}

@media (min-width: 1024px) {
  .cps-page {
    padding: 20px 28px 120px;
  }
  .cps-hero h1 {
    font-size: 26px;
  }
}
/* ===== antd 组件品牌色（只在本页作用域内生效） ===== */
.cps-create-page :deep(.ant-btn-primary:not(:disabled)) {
  background: var(--cps-teal-deep);
  border-color: var(--cps-teal-deep);
  box-shadow: none;
}

.cps-create-page :deep(.ant-btn-primary:not(:disabled):hover) {
  background: #115e59;
  border-color: #115e59;
}

.cps-create-page :deep(.ant-radio-button-wrapper-checked:not(.ant-radio-button-wrapper-disabled)) {
  background: var(--cps-teal-deep);
  border-color: var(--cps-teal-deep);
  color: #fff;
}

.cps-create-page :deep(.ant-radio-button-wrapper-checked:not(.ant-radio-button-wrapper-disabled)::before) {
  background-color: var(--cps-teal-deep);
}

.cps-create-page :deep(.ant-radio-checked .ant-radio-inner) {
  border-color: var(--cps-teal-deep);
}

.cps-create-page :deep(.ant-radio-checked .ant-radio-inner::after) {
  background-color: var(--cps-teal-deep);
}

.cps-create-page :deep(.ant-input:focus),
.cps-create-page :deep(.ant-input-focused),
.cps-create-page :deep(.ant-input-affix-wrapper-focused) {
  border-color: var(--cps-teal);
  box-shadow: 0 0 0 2px rgba(20, 184, 166, 0.16);
}

.cps-create-page :deep(.ant-spin-dot-item) {
  background-color: var(--cps-teal-deep);
}
</style>
