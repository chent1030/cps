<template>
  <view class="cps-page cps-create-page">
    <view class="cps-hero">
      <view class="cps-hero__main">
        <text class="cps-hero__eyebrow">CPS 现场巡检</text>
        <text class="cps-hero__title">{{ step === 'select' ? '选择巡检类型' : '新建问题' }}</text>
        <view class="cps-steps">
          <view
            v-for="(label, index) in stepLabels"
            :key="label"
            class="cps-steps__item"
            :class="{ 'is-active': stepIndex === index, 'is-done': stepIndex > index }"
          >
            <text class="cps-steps__num">{{ index + 1 }}</text>
            <text class="cps-steps__label">{{ label }}</text>
          </view>
        </view>
      </view>
      <text class="cps-pill cps-pill--blue cps-hero__badge">AI 辅助</text>
    </view>

    <!-- ============================================================ -->
    <!-- 第一步：选择巡检类型（选完才渲染对应表单） -->
    <!-- ============================================================ -->
    <view v-if="step === 'select'" class="cps-type-select">
      <view class="u-card cps-card cps-card--pad-lg">
        <view class="cps-card-title">
          <text class="cps-card-title__main">本次巡检属于哪类问题？</text>
          <text class="cps-pill cps-pill--blue">{{ typeCountLabel }}</text>
        </view>
        <text class="cps-create-card__hint">「CPS巡检」进入标准问题上报表单；其他类型按各自配置逐项点检填写（合格判定/文本/拍照），互不混用；分区域类型（如辅房点检）先选区域再开始</text>
        <view class="u-search cps-type-search">
          <input
            v-model="typeKeyword"
            class="u-input u-search__input"
            type="text"
            placeholder="搜索巡检类型名称或编码"
            :disabled="!draft.inspectionTypes.length"
          />
          <text v-if="typeKeyword" class="u-search__clear" @click="typeKeyword = ''">×</text>
        </view>
        <view v-if="inspectionTypesLoading" class="u-loading">
          <view class="u-loading__dot" />
          <text class="u-loading__text">加载中…</text>
        </view>
        <view v-if="!draft.inspectionTypes.length && !inspectionTypesLoading" class="u-empty">
          <text class="u-empty__icon">📭</text>
          <text class="u-empty__text">暂无可用巡检类型，请联系管理员配置</text>
        </view>
        <text v-else-if="!filteredTypes.length" class="cps-type-empty">没有匹配「{{ typeKeyword }}」的类型</text>
        <view v-else class="cps-type-select__grid">
          <button
            v-for="t in filteredTypes"
            :key="t.code"
            class="cps-type-select__item"
            :data-color="t.color || 'blue'"
            :disabled="startingCustom !== null"
            @click="onSelectInspectionType(t.code)"
          >
            <view class="cps-type-select__head">
              <text class="cps-type-select__name">{{ t.name }}</text>
              <text v-if="t.builtin === 1" class="cps-pill cps-pill--purple cps-type-select__tag">内置</text>
            </view>
            <text class="cps-type-select__code">{{ t.code }}</text>
            <text v-if="t.content || t.description" class="cps-type-select__desc">{{ t.content || t.description }}</text>
            <view class="cps-type-select__go">
              <text class="cps-type-select__go-label">{{ typeActionLabel(t) }}</text>
              <text class="cps-type-select__arrow">→</text>
            </view>
          </button>
        </view>
      </view>
    </view>

    <!-- ============================================================ -->
    <!-- 第二步：按所选类型渲染的表单 -->
    <!-- ============================================================ -->
    <view v-else class="cps-create-layout">
      <!-- 左列：照片 + 描述 -->
      <view class="cps-create-layout__col">
        <view class="u-card cps-card">
          <view class="cps-card-title">
            <text class="cps-card-title__main">问题照片</text>
            <text class="cps-pill cps-pill--blue">{{ draft.images.length }}/5</text>
          </view>
          <text class="cps-create-card__hint">最多上传 5 张，仅第 1 张参与 AI 识别</text>

          <view class="cps-uploader__grid">
            <view
              v-for="(image, index) in draft.images"
              :key="image.id"
              class="cps-uploader__item"
              @click="onPreviewImage" :data-index="index"
            >
              <image class="cps-uploader__img" :src="draft.imagePreviewSources[image.id] || ''" mode="aspectFill" />
              <text v-if="!uploading" class="cps-uploader__delete" @click.stop="onRemoveImage(index)">×</text>
              <text class="cps-uploader__index">{{ index + 1 }}</text>
            </view>
            <button
              v-if="draft.images.length < 5"
              class="cps-uploader__add"
              :disabled="uploading"
              @click="pickImages"
            >
              <text class="cps-uploader__plus">＋</text>
              <text class="cps-uploader__text">{{ uploading ? '上传中' : '上传图片' }}</text>
            </button>
          </view>
        </view>

        <view class="u-card cps-card">
          <view class="cps-card-title">
            <text class="cps-card-title__main">现场情况</text>
          </view>
          <textarea
            v-model="draft.description"
            class="u-textarea"
            auto-height
            :maxlength="500"
            placeholder="请输入现场问题描述"
          />
          <text class="cps-desc-count">{{ draft.description.length }}/500</text>
          <view class="cps-desc-toolbar">
            <button
              class="u-btn u-btn--ghost u-btn--sm"
              :class="{ 'u-btn--danger': recording }"
              :disabled="transcribing"
              @click="onVoiceClick"
            >
              <text v-if="transcribing">🎤 识别中</text>
              <text v-else-if="recording">🎤 停止录音</text>
              <text v-else>🎤 语音输入</text>
            </button>
            <text v-if="recording" class="cps-voice-hint">正在录音，再次点击「停止录音」结束并识别…</text>
          </view>
        </view>
      </view>

      <!-- 右列：类型指示 + 动态字段 + 位置 + 派发 -->
      <view class="cps-create-layout__col">
        <!-- 当前巡检类型（第一步已选，可切换） -->
        <view class="u-card cps-card cps-card--pad-x">
          <view class="cps-type-active">
            <view class="cps-type-active__info">
              <text class="cps-type-active__label">当前巡检类型</text>
              <view class="cps-type-active__name">
                <text class="cps-type-active__value">{{ activeTypeName || draft.inspectionTypeCode }}</text>
                <text class="cps-type-active__code">{{ draft.inspectionTypeCode }}</text>
              </view>
            </view>
            <button class="u-btn u-btn--ghost u-btn--sm" @click="switchType">切换类型</button>
          </view>
        </view>

        <!-- Phase D2：类型配置联动 —— 巡检内容说明 -->
        <view v-if="activeTypeContent" class="u-card cps-card cps-card--pad-sm">
          <text class="cps-type-content-banner"><text class="cps-type-content-banner__label">巡检内容：</text>{{ activeTypeContent }}</text>
        </view>

        <!-- Phase D2：类型配置联动 —— 点检项填写（管理端「直挂点检项」配置驱动） -->
        <view v-if="typeCheckItems.length || checkItemsLoading" class="u-card cps-card">
          <view class="cps-card-title">
            <text class="cps-card-title__main">现场点检项</text>
            <text class="cps-pill cps-pill--teal">{{ checkDoneCount }}/{{ typeCheckItems.length }}</text>
          </view>
          <view v-if="checkItemsLoading" class="u-loading">
            <view class="u-loading__dot" />
            <text class="u-loading__text">加载中…</text>
          </view>
          <view
            v-for="(ci, idx) in typeCheckItems"
            :key="ci.itemCode"
            class="cps-check-entry"
            :class="{ 'cps-check-entry--done': isCheckEntryDone(ci) }"
          >
            <view class="cps-check-entry__head">
              <text class="cps-check-entry__title">{{ idx + 1 }}. {{ ci.content }}</text>
              <text class="cps-check-entry__code">{{ ci.itemCode }}</text>
            </view>
            <text v-if="hasCheckType(ci, 'PHOTO') && ci.photoCriteria" class="cps-check-entry__criteria">
              📷 {{ ci.photoCriteria }}（至少 {{ ci.photoMinCount ?? 1 }} 张）
            </text>
            <view v-if="hasCheckType(ci, 'PASS_FAIL')" class="cps-check-entry__row">
              <view class="u-seg">
                <text
                  class="u-seg__item"
                  :class="{ 'u-seg__item--active': checkEntries[ci.itemCode]?.passResult === 'PASS' }"
                  @click="setCheckField(ci.itemCode, 'passResult', 'PASS')"
                >✓ 合格</text>
                <text
                  class="u-seg__item"
                  :class="{ 'u-seg__item--active': checkEntries[ci.itemCode]?.passResult === 'FAIL' }"
                  @click="setCheckField(ci.itemCode, 'passResult', 'FAIL')"
                >✗ 不合格</text>
              </view>
            </view>
            <view v-if="hasCheckType(ci, 'TEXT')" class="cps-check-entry__row">
              <textarea
                class="u-textarea"
                auto-height
                :value="checkEntries[ci.itemCode]?.textValue ?? ''"
                :maxlength="500"
                :placeholder="ci.textHint || '请填写现场情况说明'"
                @input="(event: any) => setCheckField(ci.itemCode, 'textValue', event.detail.value)"
              />
            </view>
            <view v-if="hasCheckType(ci, 'PHOTO')" class="cps-check-entry__row">
              <view class="cps-check-photos">
                <view v-for="(p, pi) in checkEntries[ci.itemCode]?.photos ?? []" :key="pi" class="cps-check-photo">
                  <image class="cps-check-photo__img" :src="checkPhotoUrl(p)" mode="aspectFill" />
                  <text class="cps-check-photo__del" @click.stop="removeCheckPhoto(ci.itemCode, pi)">×</text>
                </view>
                <button class="cps-check-photo__add" @click="onCheckPhotoPick" :data-code="ci.itemCode">
                  <text>＋ {{ (checkEntries[ci.itemCode]?.photos?.length ?? 0) }}/{{ ci.photoMinCount ?? 1 }}</text>
                </button>
              </view>
            </view>
          </view>
        </view>

        <view class="u-card cps-card">
          <view class="cps-card-title">
            <text class="cps-card-title__main">工厂 / 区域 / 拉线 / 工序</text>
          </view>

          <view class="cps-selector">
            <view class="cps-selector-row">
              <text class="cps-selector-label">工厂</text>
              <view class="u-seg cps-selector-group">
                <text
                  v-for="item in draft.factories"
                  :key="item.value"
                  class="u-seg__item"
                  :class="{ 'u-seg__item--active': draft.location.factory === String(item.value) }"
                  @click="onSelectFactory(String(item.value))"
                >{{ item.label }}</text>
              </view>
              <text v-if="!draft.factories.length" class="cps-muted">暂无工厂数据</text>
            </view>

            <view class="cps-selector-row">
              <text class="cps-selector-label">区域</text>
              <view class="u-seg cps-selector-group">
                <text
                  v-for="item in draft.areas"
                  :key="item.value"
                  class="u-seg__item"
                  :class="{ 'u-seg__item--active': draft.location.area === String(item.value) }"
                  @click="onSelectArea(String(item.value))"
                >{{ item.label }}</text>
              </view>
              <text v-if="!draft.areas.length" class="cps-muted">先选择工厂</text>
            </view>

            <view v-if="draft.location.area" class="cps-selector-row">
              <text class="cps-selector-label">拉线</text>
              <view class="u-seg cps-selector-group">
                <text
                  v-for="item in draft.lines"
                  :key="item.value"
                  class="u-seg__item"
                  :class="{ 'u-seg__item--active': draft.location.line === String(item.value) }"
                  @click="onSelectLine(String(item.value))"
                >{{ item.label }}</text>
              </view>
              <text v-if="!draft.lines.length" class="cps-muted">暂无拉线数据</text>
            </view>

            <view v-if="draft.location.area" class="cps-selector-row">
              <text class="cps-selector-label">工序</text>
              <view class="u-seg cps-selector-group">
                <text
                  v-for="item in draft.processes"
                  :key="item.value"
                  class="u-seg__item"
                  :class="{ 'u-seg__item--active': draft.location.process === String(item.value) }"
                  @click="onSelectProcess(String(item.value))"
                >{{ item.label }}</text>
              </view>
              <text v-if="!draft.processes.length" class="cps-muted">暂无工序数据</text>
            </view>
          </view>
        </view>

        <view v-if="inspecting" class="u-alert u-alert--info">
          <text class="u-alert__icon">ℹ</text>
          <text class="u-alert__text">AI 正在识别首张图片，请稍候</text>
        </view>

        <view v-if="aiSuggestionSummary" class="u-card cps-card cps-create-ai">
          <view class="cps-card-title">
            <text class="cps-card-title__main">原因措施</text>
            <text class="cps-pill cps-pill--blue">可修改</text>
          </view>
          <text class="cps-create-ai__pre">{{ aiSuggestionSummary }}</text>
        </view>

        <view class="u-card cps-card">
          <view class="cps-card-title">
            <text class="cps-card-title__main">整改安排</text>
          </view>
          <view class="cps-form">
            <view class="u-field">
              <text class="u-field__label">资源号<text class="u-field__req">*</text></text>
              <view class="u-field__control">
                <input
                  v-model="draft.resourceNo"
                  class="u-input"
                  type="text"
                  placeholder="请输入现场资源号（设备/工位编号等）"
                  :maxlength="64"
                />
              </view>
            </view>
            <view class="u-field">
              <text class="u-field__label">整改人</text>
              <view class="u-field__control">
                <view class="u-input cps-person-field" @click="feedbackPickerOpen = true">
                  <text class="cps-person-field__value" :class="{ 'cps-person-field__placeholder': !feedbackPersonLabel }">
                    {{ feedbackPersonLabel || '选择整改人' }}
                  </text>
                  <text class="cps-person-field__icon">👤</text>
                </view>
              </view>
            </view>
          </view>
        </view>
      </view>
    </view>

    <view v-if="step === 'form'" class="cps-submit-bar">
      <view class="cps-submit-bar__progress">
        <text
          v-for="item in progressItems"
          :key="item.label"
          class="cps-progress-chip"
          :class="{ 'is-done': item.done }"
        >
          {{ item.done ? '✓' : '·' }} {{ item.label }}
        </text>
      </view>
      <view class="cps-submit-bar__actions">
        <button
          class="u-btn u-btn--ghost"
          :disabled="!canSaveDraft || savingDraft"
          @click="saveDraft"
        >
          {{ savingDraft ? '保存中…' : draft.draftId ? '更新暂存' : '暂存' }}
        </button>
        <button class="u-btn u-btn--primary" :disabled="!canSubmit || submitting" @click="submit">
          {{ submitting ? '提交中…' : '提交并派发' }}
        </button>
      </view>
      <text v-if="draft.draftId" class="cps-draft-hint">已关联服务端草稿 #{{ draft.draftId }}，提交后将自动归档</text>
    </view>

    <!-- 反馈人选择弹窗（内联） -->
    <view v-if="feedbackPickerOpen" class="u-modal-mask" @click.self="feedbackPickerOpen = false">
      <view class="u-modal" :style="{ maxWidth: isLandscape ? '520px' : '100%' }">
        <view class="u-modal__head">
          <text class="u-modal__title">选择整改人</text>
          <text class="u-modal__close" @click="feedbackPickerOpen = false">×</text>
        </view>
        <view class="u-modal__body">
          <view class="u-search cps-person-picker__search">
            <input
              v-model="feedbackKeyword"
              class="u-input u-search__input"
              type="text"
              placeholder="输入姓名或工号搜索"
              @confirm="onSearchFeedback"
            />
            <button class="u-search__btn" :disabled="feedbackSearching" @click="onSearchFeedback">搜索</button>
          </view>
          <view v-if="feedbackSearching" class="u-loading">
            <view class="u-loading__dot" />
            <text class="u-loading__text">搜索中…</text>
          </view>
          <view class="cps-person-picker__body">
            <view v-if="!feedbackPeople.length" class="u-empty">
              <text class="u-empty__icon">📭</text>
              <text class="u-empty__text">未找到人员，请调整关键词后搜索</text>
            </view>
            <view v-else class="cps-person-picker__group">
              <view
                v-for="person in feedbackPeople"
                :key="person.empNo"
                class="cps-person-picker__option"
                :class="{ 'cps-person-picker__option--active': draft.feedbackEmpNo === person.empNo }"
                @click="onSelectFeedback(person.empNo)"
              >
                <text class="cps-person-picker__radio">{{ draft.feedbackEmpNo === person.empNo ? '●' : '○' }}</text>
                <view class="cps-person-picker__text">
                  <text class="cps-person-picker__name">{{ person.empName || person.empNo }}</text>
                  <text class="cps-person-picker__no">{{ person.empNo }}</text>
                </view>
              </view>
            </view>
          </view>
        </view>
      </view>
    </view>

    <!-- 分区域类型（如辅房点检）：先选区域，再开始巡检 -->
    <view v-if="areaPickerOpen" class="u-modal-mask" @click.self="areaPickerOpen = false">
      <view class="u-modal" :style="{ maxWidth: isLandscape ? '560px' : '100%' }">
        <view class="u-modal__head">
          <text class="u-modal__title">选择巡检区域{{ areaPickerType ? ` · ${areaPickerType.name}` : '' }}</text>
          <text class="u-modal__close" @click="areaPickerOpen = false">×</text>
        </view>
        <view class="u-modal__body">
          <text class="cps-create-card__hint">该类型按区域执行，选定区域后将在该区域下生成点检项快照</text>
          <view class="u-search cps-area-search">
            <input
              v-model="areaKeyword"
              class="u-input u-search__input"
              type="text"
              placeholder="搜索区域名称、编码或房型"
              :disabled="!areaOptions.length"
            />
            <text v-if="areaKeyword" class="u-search__clear" @click="areaKeyword = ''">×</text>
          </view>
          <view v-if="areaPickerLoading" class="u-loading">
            <view class="u-loading__dot" />
            <text class="u-loading__text">加载中…</text>
          </view>
          <view v-if="!areaOptions.length && !areaPickerLoading" class="u-empty">
            <text class="u-empty__icon">📭</text>
            <text class="u-empty__text">该类型暂无启用区域，请联系管理员配置</text>
          </view>
          <text v-else-if="!filteredAreas.length" class="cps-type-empty">没有匹配「{{ areaKeyword }}」的区域</text>
          <view v-else class="cps-area-pick">
            <button
              v-for="a in filteredAreas"
              :key="a.id"
              class="cps-area-pick__item"
              :disabled="areaPickerStarting !== null"
              @click="startZonedInspection(a)"
            >
              <view class="cps-area-pick__main">
                <text class="cps-area-pick__name">{{ a.areaName }}</text>
                <view class="cps-area-pick__meta">
                  <text class="cps-area-pick__meta-text">{{ a.areaCode }}</text>
                  <text v-if="a.lineOrRoomType" class="cps-area-pick__meta-text"> · {{ a.lineOrRoomType }}</text>
                  <text v-if="a.process" class="cps-area-pick__meta-text"> · {{ a.process }}</text>
                </view>
              </view>
              <text class="cps-area-pick__risk" :data-risk="a.riskLevel">{{ riskLabel(a.riskLevel) }}</text>
              <text class="cps-area-pick__go">{{ areaPickerStarting === a.id ? '正在开启…' : '开始 →' }}</text>
            </button>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { cpsApi } from '@/api/index'
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { onLoad } from '@dcloudio/uni-app'





import type { CpsAttachmentUploadSource, CpsEmployeeOption } from '@/api/index'
import type { CpsAiSuggestionPayload, CpsCheckItemInfo, CpsInspectionArea, CpsInspectionType, CpsIssueCheckResult, CpsIssueDraftData, CpsOption, CpsUploadedImage } from '@/types/cps'
import { resolveAssetUrl, resolveUploadSources, toImageUrl } from '@/utils/image'

// 新建问题表单状态：只存在于当前页面实例，**不写任何本地缓存**（不做草稿暂存/回填）。
// 从「我的草稿」进入时，由 restoreDraft 通过接口 cpsApi.getCpsIssueDraft 拉取后回填。
interface CreateDraftState {
  location: { factory: string; area: string; line: string; process: string }
  images: CpsUploadedImage[]
  imagePreviewSources: Record<number, string>
  description: string
  resourceNo: string
  inspectionTypeCode: string | null
  extraData: Record<string, unknown>
  draftId: number | null
  feedbackEmpNo: string
  feedbackPerson: CpsEmployeeOption | null
  aiSuggestion: CpsAiSuggestionPayload | null
  factories: CpsOption[]
  areas: CpsOption[]
  lines: CpsOption[]
  processes: CpsOption[]
  inspectionTypes: CpsInspectionType[]
}

const createInitialDraft = (): CreateDraftState => ({
  location: { factory: '', area: '', line: '', process: '' },
  images: [],
  imagePreviewSources: {},
  description: '',
  resourceNo: '',
  inspectionTypeCode: null,
  extraData: {},
  draftId: null,
  feedbackEmpNo: '',
  feedbackPerson: null,
  aiSuggestion: null,
  factories: [],
  areas: [],
  lines: [],
  processes: [],
  inspectionTypes: [],
})

const draft = reactive<CreateDraftState & { reset: () => void }>({
  ...createInitialDraft(),
  reset: () => {
    Object.assign(draft, createInitialDraft())
  },
})

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
    areaOptions.value = await cpsApi.listInspectionAreas(t.code)
  } catch (error) {
    uni.showToast({ title: error instanceof Error ? error.message : '加载巡检区域失败', icon: 'none' })
  } finally {
    areaPickerLoading.value = false
  }
}

const startZonedInspection = async (area: CpsInspectionArea) => {
  const t = areaPickerType.value
  if (!t || areaPickerStarting.value !== null) return
  areaPickerStarting.value = area.id
  try {
    const record = await cpsApi.startInspection({ typeCode: t.code, areaId: area.id })
    areaPickerOpen.value = false
    uni.redirectTo({ url: `/views/cps/InspectionExecuteView?recordId=${record.id}` })
  } catch (error) {
    uni.showToast({ title: error instanceof Error ? error.message : '开始巡检失败（请确认该区域已配置点检项）', icon: 'none' })
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
    typeCheckItems.value = await cpsApi.getInspectionTypeCheckItems(code)
  } catch {
    typeCheckItems.value = []
  } finally {
    checkItemsLoading.value = false
  }
}

// 拍照/选图：直接用 uni 原生 uni.chooseImage（App 端会调起系统相机/相册）
const PHOTO_SOURCE_TYPES: Array<'camera' | 'album'> = ['camera', 'album']

const onChooseImageFail = (err: unknown, fallback: string) => {
  const message = String((err as { errMsg?: string } | undefined)?.errMsg ?? '')
  // 用户主动取消不算失败
  if (/cancel/i.test(message)) return
  uni.showToast({ title: message || fallback, icon: 'none' })
}

/** 点检项照片的显示地址：后端存的是站内相对路径，显示前补成绝对地址 */
const checkPhotoUrl = (entry: { url: string }) => resolveAssetUrl(entry.url)

const uploadCheckPhotos = async (itemCode: string, sources: CpsAttachmentUploadSource[]) => {
  if (!sources.length) return
  try {
    const current = [...(checkEntries.value[itemCode]?.photos ?? [])]
    for (const source of sources) {
      const uploaded = await cpsApi.uploadCpsAttachment(source)
      current.push({ id: uploaded.id, url: uploaded.url, name: uploaded.name })
    }
    setCheckField(itemCode, 'photos', current)
  } catch (error) {
    uni.showToast({ title: error instanceof Error ? error.message : '照片上传失败', icon: 'none' })
  }
}

const onCheckPhotoPick = (event: unknown) => {
  const dataset = ((event as { currentTarget?: { dataset?: Record<string, unknown> } } | undefined)?.currentTarget
    ?.dataset ?? {}) as { code?: string }
  const itemCode = String(dataset.code ?? '')
  if (!itemCode) return
  uni.chooseImage({
    count: 9,
    sizeType: ['compressed'],
    sourceType: PHOTO_SOURCE_TYPES,
    success: (result) => {
      void uploadCheckPhotos(itemCode, resolveUploadSources(result))
    },
    fail: (err) => onChooseImageFail(err, '拍照失败'),
  })
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
      const record = await cpsApi.startInspection({ typeCode: code })
      uni.redirectTo({ url: `/views/cps/InspectionExecuteView?recordId=${record.id}` })
    } catch (error) {
      uni.showToast({ title: error instanceof Error ? error.message : '开始巡检失败（请确认该类型已配置点检项）', icon: 'none' })
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
    const list: CpsInspectionType[] = await cpsApi.listCpsInspectionTypes()
    // Phase C/D：全量列出后端返回的可用类型（含分区域类型，如辅房点检）
    // 内置 CPS巡检（builtin=1）排在最前
    draft.inspectionTypes = list.sort(
      (a, b) => (b.builtin ?? 0) - (a.builtin ?? 0) || (a.sortOrder ?? 0) - (b.sortOrder ?? 0),
    )
  } catch (error) {
    uni.showToast({ title: error instanceof Error ? error.message : '加载巡检类型失败', icon: 'none' })
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
const updateOrientation = (size?: { size?: { windowWidth?: number } }) => {
  const width = size?.size?.windowWidth ?? uni.getSystemInfoSync().windowWidth
  isLandscape.value = width >= 992
}
updateOrientation()
uni.onWindowResize(updateOrientation)
onUnmounted(() => {
  uni.offWindowResize(updateOrientation)
})

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
    const handler = await cpsApi.getFeedbackHandler({
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
  draft.areas = await cpsApi.getAreas(factory)
  draft.lines = []
  draft.processes = []
}
const onSelectArea = async (area: string) => {
  draft.location = { ...draft.location, area, line: '', process: '' }
  draft.lines = await cpsApi.getLines(draft.location.factory, area)
  draft.processes = await cpsApi.getProcesses(draft.location.factory, area)
}
const onSelectLine = async (line: string) => {
  draft.location = { ...draft.location, line, process: '' }
  draft.processes = await cpsApi.getProcesses(draft.location.factory, draft.location.area, line || undefined)
}
const onSelectProcess = (process: string) => {
  draft.location = { ...draft.location, process }
}

const onSearchFeedback = async () => {
  const kw = feedbackKeyword.value.trim()
  if (!kw || feedbackSearching.value) return
  feedbackSearching.value = true
  try {
    feedbackSearchResults.value = await cpsApi.searchCpsEmployees(kw)
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

// 图片上传：uni.chooseImage 选完立即逐张串行上传，保持选择顺序（首张仍触发 AI 识别）
const uploadDraftImages = async (sources: CpsAttachmentUploadSource[]) => {
  if (!sources.length) return
  uploading.value = true
  try {
    for (const source of sources) {
      const shouldInspectFirst = draft.images.length === 0
      const uploaded = await cpsApi.uploadCpsAttachment(source)
      draft.images = [...draft.images, uploaded]
      // 直接使用后端返回的 /api/cps/attachments/{id}/content 路径作为预览地址，
      // 免掉 base64 转换与 /api/cps/attachments/base64 接口（已下线）。
      draft.imagePreviewSources = { ...draft.imagePreviewSources, [uploaded.id]: resolveAssetUrl(uploaded.url) }
      if (shouldInspectFirst) {
        await onFirstImageReady(uploaded.id)
      }
    }
  } catch (error) {
    uni.showToast({ title: error instanceof Error ? error.message : '图片上传失败', icon: 'none' })
  } finally {
    uploading.value = false
  }
}

const pickImages = () => {
  const remaining = Math.max(5 - draft.images.length, 0)
  if (!remaining) {
    uni.showToast({ title: '最多上传 5 张照片', icon: 'none' })
    return
  }
  uni.chooseImage({
    count: remaining,
    sizeType: ['compressed'],
    sourceType: PHOTO_SOURCE_TYPES,
    success: (result) => {
      void uploadDraftImages(resolveUploadSources(result))
    },
    fail: (err) => onChooseImageFail(err, '拍照/选图失败'),
  })
}

const onRemoveImage = (index: number) => {
  draft.images = draft.images.filter((_, i) => i !== index)
}


type PhotoTapDataset = { list?: string; index?: number | string; itemId?: number | string }

const readDataset = (event: unknown): PhotoTapDataset =>
  ((event as { currentTarget?: { dataset?: Record<string, unknown> } } | undefined)?.currentTarget?.dataset ??
    {}) as PhotoTapDataset

// uni 原生图片预览：urls 必须是绝对地址，current 用下标（避免 filter 后错位）
const previewImages = (sources: string[], index: number) => {
  const urls = sources.map((item) => resolveAssetUrl(item)).filter(Boolean)
  if (!urls.length) {
    uni.showToast({ title: '图片还没加载出来', icon: 'none' })
    return
  }
  const current = Math.min(Math.max(index, 0), urls.length - 1)
  uni.previewImage({
    urls,
    current,
    indicator: 'number',
    loop: true,
    fail: () => uni.showToast({ title: '图片打不开，检查图片地址配置', icon: 'none' }),
  })
}

// 点开已选图片：uni 原生预览
const onPreviewImage = (event: unknown) => {
  const dataset = readDataset(event)
  const sources = draft.images.map((item) => draft.imagePreviewSources[item.id] || item.url)
  previewImages(sources, Number(dataset.index ?? 0))
}

const onFirstImageReady = async (fileId: number) => {
  inspecting.value = true
  try {
    // 创建阶段仅保存 AI 识别结果，分类由反馈回复节点确认/修正
    draft.aiSuggestion = await cpsApi.inspectCpsImage(fileId)
  } finally {
    inspecting.value = false
  }
}

// ---------- 语音输入：Android 系统录音 → 停止后上传转写 ----------
const recording = ref(false)
const transcribing = ref(false)

// 录音器（uni 官方用法：recorderManager）
const recorderManager = uni.getRecorderManager()

// 录音停止：拿到录音临时文件 → 上传后端 → 回填识别文本
recorderManager.onStop((result) => {
  recording.value = false
  const filePath = result.tempFilePath
  if (!filePath) {
    transcribing.value = false
    uni.showToast({ title: '没有取到录音文件', icon: 'none' })
    return
  }
  cpsApi.transcribeVoice(filePath).then(
    (text) => {
      transcribing.value = false
      if (text && text.trim()) {
        draft.description = [draft.description.trim(), text.trim()].filter(Boolean).join('\n')
      } else {
        uni.showToast({ title: '未识别到语音内容', icon: 'none' })
      }
    },
    (error: unknown) => {
      transcribing.value = false
      uni.showToast({ title: error instanceof Error ? error.message : '语音识别失败', icon: 'none' })
    },
  )
})

recorderManager.onError((error) => {
  recording.value = false
  transcribing.value = false
  uni.showToast({ title: error.errMsg || '录音失败', icon: 'none' })
})

const onVoiceClick = () => {
  // 正在录音 → 停止录音（停止后由 onStop 上传并回填）
  if (recording.value) {
    recording.value = false
    transcribing.value = true
    recorderManager.stop()
    return
  }
  // 未录音 → 开始录音
  if (transcribing.value) return
  try {
    recorderManager.start({
      duration: 120000, // 最长 2 分钟
      format: 'aac',
      sampleRate: 16000,
      numberOfChannels: 1,
      encodeBitRate: 48000,
    })
    recording.value = true
  } catch (error) {
    uni.showToast({ title: error instanceof Error ? error.message : '录音启动失败', icon: 'none' })
  }
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
    const saved = await cpsApi.saveCpsIssueDraft({
      id: draft.draftId,
      inspectionTypeCode: snapshot.inspectionTypeCode,
      factory: snapshot.location.factory || undefined,
      area: snapshot.location.area || undefined,
      draftData: JSON.stringify(snapshot),
    })
    draft.draftId = saved.id
    uni.showToast({ title: `已暂存（草稿 #${saved.id}），可稍后从「我的草稿」继续填写`, icon: 'success' })
  } catch (error) {
    uni.showToast({ title: error instanceof Error ? error.message : '暂存失败', icon: 'none' })
  } finally {
    savingDraft.value = false
  }
}

/** 从服务端草稿恢复整份表单。 */
const restoreDraft = async (id: number) => {
  try {
    const remote = await cpsApi.getCpsIssueDraft(id)
    let data: CpsIssueDraftData
    try {
      data = JSON.parse(remote.draftData) as CpsIssueDraftData
    } catch {
      uni.showToast({ title: '草稿数据损坏，无法恢复', icon: 'none' })
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
    draft.imagePreviewSources = Object.fromEntries(
      Object.entries(data.imagePreviewSources ?? {}).map(([key, url]) => [key, resolveAssetUrl(url)]),
    )
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
      draft.areas = await cpsApi.getAreas(draft.location.factory)
      if (draft.location.area) {
        draft.lines = await cpsApi.getLines(draft.location.factory, draft.location.area)
        draft.processes = await cpsApi.getProcesses(draft.location.factory, draft.location.area)
      }
    }
    uni.showToast({ title: `已恢复草稿 #${remote.id}，继续填写`, icon: 'success' })
  } catch (error) {
    uni.showToast({ title: error instanceof Error ? error.message : '恢复草稿失败', icon: 'none' })
  }
}

const submit = async () => {
  if (!canSubmit.value) return
  submitting.value = true
  try {
    // Phase D2：点检项填写结果并入 extraData（与管理端类型配置联动）
    const extraPayload: Record<string, unknown> = { ...draft.extraData }
    if (typeCheckItems.value.length) extraPayload.checkResults = collectCheckResults()
    const result = await cpsApi.createCpsIssue({
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
    uni.showToast({ title: error instanceof Error ? error.message : '提交失败', icon: 'none' })
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  if (!draft.factories.length) {
    draft.factories = await cpsApi.getFactories()
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
/* @inline-uni-controls: 由脚本按页面用到的类内联生成，勿手改 */

/* 该页自包含的 uni 内置控件样式（原全局样式已按页面内联到这里） */
*,
*::before,
*::after {
  box-sizing: border-box;
}

view,
uni-view,
text,
uni-text,
image,
uni-image,
button,
uni-button,
input,
uni-input,
textarea,
uni-textarea,
scroll-view,
uni-scroll-view,
picker,
uni-picker {
  box-sizing: border-box;
}

image,
uni-image {
  display: block;
}

button,
uni-button {
  margin: 0;
  padding: 0;
  background: transparent;
  border: 0;
  border-radius: 0;
  line-height: normal;
  font: inherit;
  color: inherit;
  text-align: center;
}

button::after,
uni-button::after {
  border: none;
}

input,
uni-input,
textarea,
uni-textarea {
  margin: 0;
  padding: 0;
  background: transparent;
  border: 0;
  font: inherit;
  color: inherit;
  outline: none;
}

@keyframes u-pulse {
  0%, 100% { opacity: 0.3; transform: scale(0.8); }
  50% { opacity: 1; transform: scale(1); }
}

switch {
  transform: scale(0.9);
  transform-origin: right center;
}

radio .wx-radio-input,
checkbox .wx-checkbox-input {
  border-radius: 4px;
}

.cps-page {
  --cps-teal: #14b8a6;
  --cps-teal-deep: #0f766e;
  --cps-blue: #2563eb;
  --cps-orange: #f97316;
  --cps-green: #22c55e;
  --cps-red: #ef4444;
  --cps-ink: #0f172a;
  --cps-muted: #64748b;
  --cps-mist: #f0fdfa;
  --cps-cloud: #f8fafc;
  --cps-line: #ccfbf1;
  --cps-ink-2: #334155;
  --cps-faint: #94a3b8;
  --cps-border: #e2e8f0;
  --cps-border-strong: #cbd5e1;
  --cps-info-deep: #1d4ed8;
  --cps-warn-deep: #b45309;
  --cps-success-deep: #15803d;
  --cps-danger-deep: #b91c1c;
  --cps-radius-sm: 8px;
  --cps-radius-md: 10px;
  --cps-radius-lg: 12px;
  --cps-radius-xl: 16px;
  --cps-radius-pill: 999px;
  --cps-shadow-card: 0 1px 2px rgba(15, 23, 42, 0.04), 0 4px 12px rgba(15, 23, 42, 0.04);
  --cps-shadow-hero: 0 12px 32px rgba(15, 23, 42, 0.12);
  --cps-hero-bg: linear-gradient(160deg, #0f766e 0%, #115e59 100%);
  margin: 0;
  background:
    radial-gradient(circle at top left, rgba(20, 184, 166, 0.18), transparent 512px),
    linear-gradient(180deg, #f0fdfa 0%, #f8fafc 42%, #eef2ff 100%);
  color: var(--cps-ink);
  font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif;
  font-size: 16px;
  touch-action: pan-y;
  -webkit-tap-highlight-color: transparent;
}

.u-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 44px;
  padding: 0 18px;
  border-radius: 12px;
  font-size: 15px;
  font-weight: 600;
  border: 1px solid #cbd5e1;
  background: #ffffff;
  color: var(--cps-ink);
  transition: opacity 0.15s ease;
}

.u-btn--primary {
  border-color: transparent;
  background: linear-gradient(135deg, #14b8a6 0%, #0e7490 100%);
  color: #ffffff;
}

.u-btn--danger {
  border-color: transparent;
  background: var(--cps-red);
  color: #ffffff;
}

.u-btn--ghost {
  border-color: rgba(255, 255, 255, 0.45);
  background: rgba(255, 255, 255, 0.15);
  color: #ffffff;
}

.u-btn--plain {
  border-color: #e2e8f0;
  background: #f8fafc;
  color: var(--cps-muted);
}

.u-btn--block {
  display: flex;
  width: 100%;
}

.u-btn--sm {
  min-height: 34px;
  padding: 0 12px;
  font-size: 13px;
  border-radius: 9px;
}

.u-btn[disabled],
.u-btn--disabled {
  opacity: 0.5;
}

.u-input,
.u-textarea {
  width: 100%;
  min-height: 44px;
  padding: 10px 14px;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  background: #ffffff;
  font-size: 15px;
  line-height: 22px;
  color: var(--cps-ink);
}

.u-textarea {
  min-height: 96px;
  line-height: 1.6;
}

.u-input--error,
.u-textarea--error {
  border-color: var(--cps-red);
}

.u-input__placeholder {
  color: #94a3b8;
}

.u-search {
  display: flex;
  align-items: center;
  gap: 8px;
}

.u-search__input {
  flex: 1;
  min-height: 44px;
  padding: 0 14px;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  background: #ffffff;
  font-size: 15px;
}

.u-search__btn {
  flex: none;
  min-height: 44px;
  padding: 0 16px;
  border-radius: 12px;
  background: var(--cps-teal);
  color: #ffffff;
  font-size: 15px;
  font-weight: 600;
}

.u-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.u-field__label {
  font-size: 14px;
  font-weight: 600;
  color: #334155;
}

.u-field__req {
  color: var(--cps-red);
  margin-left: 2px;
}

.u-field__control {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.u-field__err {
  font-size: 12px;
  color: var(--cps-red);
}

.u-field__hint {
  font-size: 12px;
  color: var(--cps-muted);
}

.u-card {
  background: #ffffff;
  border-radius: 14px;
  border: 1px solid #e2e8f0;
  padding: 16px;
  box-shadow: 0 4px 16px rgba(15, 118, 110, 0.06);
}

.u-card--flat {
  box-shadow: none;
}

.u-card--pad-none {
  padding: 0;
}

.u-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 48px 16px;
}

.u-empty__icon {
  font-size: 34px;
  line-height: 1;
}

.u-empty__text {
  font-size: 14px;
  color: var(--cps-muted);
}

.u-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 18px;
}

.u-loading__text {
  font-size: 14px;
  color: var(--cps-muted);
}

.u-loading__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--cps-teal);
  animation: u-pulse 1s ease-in-out infinite;
}

.u-alert {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 12px 14px;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
}

.u-alert__text {
  flex: 1;
  font-size: 13px;
  line-height: 1.5;
  color: #334155;
}

.u-alert--error {
  border-color: #fecaca;
  background: #fef2f2;
}

.u-alert--error .u-alert__text {
  color: #b91c1c;
}

.u-alert--success {
  border-color: #bbf7d0;
  background: #f0fdf4;
}

.u-alert--warning {
  border-color: #fed7aa;
  background: #fff7ed;
}

.u-alert--info {
  border-color: #bfdbfe;
  background: #eff6ff;
}

.u-modal-mask {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 900;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(15, 23, 42, 0.45);
}

.u-modal {
  width: 100%;
  max-width: 560px;
  max-height: 82vh;
  display: flex;
  flex-direction: column;
  background: #ffffff;
  border-radius: 16px;
  overflow: hidden;
}

.u-modal__head {
  padding: 16px 18px;
  font-size: 16px;
  font-weight: 700;
  color: var(--cps-ink);
  border-bottom: 1px solid #eef2f7;
}

.u-modal__body {
  padding: 16px 18px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.u-modal__foot {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 12px 18px 16px;
  border-top: 1px solid #eef2f7;
}

.u-seg {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 4px;
  padding: 4px;
  border-radius: 12px;
  background: #f1f5f9;
}

.u-seg__item {
  padding: 8px 14px;
  border-radius: 9px;
  font-size: 13px;
  font-weight: 600;
  color: var(--cps-muted);
}

.u-seg__item--active {
  background: #ffffff;
  color: var(--cps-teal-deep);
  box-shadow: 0 1px 4px rgba(15, 23, 42, 0.12);
}

.cps-pill {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  line-height: 18px;
}

.cps-pill--blue {
  background: #dbeafe;
  color: #1d4ed8;
}

.cps-pill--orange {
  background: #ffedd5;
  color: #c2410c;
}

.cps-pill--green {
  background: #dcfce7;
  color: #15803d;
}

.cps-pill--teal {
  background: #ccfbf1;
  color: #0f766e;
}

.cps-pill--gray {
  background: #e2e8f0;
  color: #475569;
}

.cps-pill--red {
  background: #fee2e2;
  color: #b91c1c;
}

.cps-status-pill {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 10px;
  border-radius: var(--cps-radius-pill);
  font-size: 12px;
  font-weight: 600;
  line-height: 18px;
  white-space: nowrap;
}

.cps-status-pill--blue {
  background: #dbeafe;
  color: #1d4ed8;
}

.cps-status-pill--orange {
  background: #ffedd5;
  color: #c2410c;
}

.cps-status-pill--green {
  background: #dcfce7;
  color: #15803d;
}

.cps-status-pill--teal {
  background: #ccfbf1;
  color: #0f766e;
}

.cps-status-pill--gray {
  background: #e2e8f0;
  color: #475569;
}

.cps-status-pill--red {
  background: #fee2e2;
  color: #b91c1c;
}
/* @end-inline-uni-controls */

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

.cps-hero__title {
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
}.cps-card-title {
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
}.cps-uploader__plus {
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

.cps-uploader__img {
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
}.cps-muted {
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

.cps-area-pick__name {
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

.cps-check-entry__title {
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

.cps-check-photo__img {
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
}.cps-submit-bar {
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
  .cps-hero__title {
    font-size: 26px;
  }
}
/* ===== uni-app 内置控件改造补充 ===== */
.cps-create-page .cps-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
}

.cps-create-page .cps-card--pad-lg {
  padding: 20px;
}

.cps-create-page .cps-card--pad-x {
  padding: 14px 16px;
}

.cps-create-page .cps-card--pad-sm {
  padding: 12px 16px;
}

.cps-create-layout {
  display: grid;
  gap: 16px;
}

.cps-create-layout__col {
  display: grid;
  gap: 16px;
  min-width: 0;
}

@media (min-width: 992px) {
  .cps-create-layout {
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
    align-items: start;
  }
}

.cps-form {
  display: grid;
  gap: 14px;
}

.cps-hero__title {
  color: #ffffff;
}

.cps-uploader__img,
.cps-check-photo__img {
  display: block;
  width: 100%;
  height: 100%;
}

.cps-uploader__add {
  display: grid;
  align-content: center;
  justify-items: center;
  gap: 2px;
  width: 100%;
  aspect-ratio: 1;
  border: 1.5px dashed #99f6e4;
  border-radius: 12px;
  background: var(--cps-mist);
  color: #0f766e;
  font-size: 12px;
  font-weight: 700;
}

.cps-uploader__add[disabled] {
  opacity: 0.6;
}

.cps-desc-count {
  display: block;
  margin-top: -4px;
  text-align: right;
  color: #94a3b8;
  font-size: 12px;
}

.cps-person-field {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  cursor: pointer;
}

.cps-person-field__placeholder {
  color: #94a3b8;
}

.cps-person-picker__option--active {
  outline: 1.5px solid var(--cps-teal);
  background: #f0fdfa;
}

.cps-person-picker__radio {
  color: var(--cps-teal-deep);
  font-size: 16px;
  line-height: 1;
}

.cps-person-picker__text {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.cps-person-picker__name {
  color: #0f172a;
  font-size: 15px;
  font-weight: 700;
  line-height: 22px;
}

.cps-person-picker__no {
  color: #64748b;
  font-size: 13px;
  line-height: 18px;
}

.cps-person-picker__option {
  cursor: pointer;
}

.cps-check-entry__title {
  color: #0f172a;
  font-size: 14px;
  line-height: 20px;
}

.cps-area-pick__name {
  color: #0f172a;
  font-size: 15px;
  font-weight: 700;
}

.cps-area-pick__meta-text {
  color: #64748b;
  font-size: 12px;
}

.cps-type-select__name {
  color: #0f172a;
  font-size: 15px;
  font-weight: 800;
}

.cps-type-content-banner__label {
  color: #0f172a;
  font-weight: 800;
}

.cps-selector-group {
  display: flex;
  flex: 1 1 auto;
  flex-wrap: wrap;
  gap: 8px;
  min-width: 0;
  padding: 0;
  background: transparent;
}

.cps-selector-group .u-seg__item {
  border: 1px solid #cbd5e1;
  border-radius: 999px;
  height: 36px;
  line-height: 34px;
  padding: 0 16px;
}

.cps-selector-group .u-seg__item--active {
  border-color: var(--cps-teal);
  background: var(--cps-teal);
  color: #ffffff;
  box-shadow: none;
}

.cps-progress-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.cps-draft-hint {
  display: block;
}

/* 弹层标题栏 */
.u-modal__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.u-modal__title {
  font-size: 16px;
  font-weight: 700;
  color: var(--cps-ink);
}

.u-modal__close {
  padding: 2px 8px;
  color: #94a3b8;
  font-size: 20px;
  line-height: 1;
}

/* 搜索框清除按钮 */
.u-search {
  position: relative;
}

.u-search__clear {
  position: absolute;
  right: 12px;
  top: 50%;
  transform: translateY(-50%);
  color: #94a3b8;
  font-size: 16px;
  line-height: 1;
}

.cps-type-search .u-search__input,
.cps-area-search .u-search__input {
  padding-right: 36px;
}
</style>
