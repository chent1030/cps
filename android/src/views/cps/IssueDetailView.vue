<template>
  <view class="cps-detail-root">
    <view v-if="detail" class="cps-page cps-detail-page">
      <view class="cps-hero">
        <view class="cps-hero__top">
          <view class="cps-hero__identity">
            <text class="cps-hero__eyebrow">CPS 巡检问题</text>
            <text class="cps-hero__title">{{ detail.description }}</text>
          </view>
          <view class="cps-hero__badges">
            <text class="cps-pill" :class="statusMeta[detail.status].tone">{{ detailStatus.label }}</text>
            <text v-if="detail.overdue" class="cps-pill cps-pill--red">超时</text>
          </view>
        </view>
        <view class="cps-hero__meta">
          <text class="cps-hero__meta-item">{{ issueLocation }}</text>
          <text class="cps-hero__meta-item">{{ issueCategory }}</text>
          <text class="cps-hero__meta-item">提交 {{ formatDateTime(detail.submitTime) }}</text>
        </view>
        <view class="cps-steps" aria-label="流程进度">
          <view
            v-for="(node, index) in statusSteps"
            :key="node.value"
            class="cps-steps__item"
            :class="{ 'is-done': index < currentStepIndex, 'is-active': index === currentStepIndex }"
          >
            <text class="cps-steps__num">{{ index + 1 }}</text>
            <text class="cps-steps__label">{{ node.label }}</text>
          </view>
        </view>
      </view>

      <view class="cps-detail-layout">
        <!-- 左列：基础信息 + 照片 + AI -->
        <view class="cps-col-main">
          <view class="u-card u-card--pad-none cps-card">
            <view class="cps-card__head">
              <view class="cps-card-title">
                <text class="cps-card-title__main">处理责任</text>
              </view>
            </view>
            <view class="cps-card__body">
              <view class="u-desc">
                <view class="u-desc__row">
                  <text class="u-desc__label">提交人</text>
                  <text class="u-desc__value">{{ detail.creatorEmpNo || '-' }}</text>
                </view>
                <view class="u-desc__row">
                  <text class="u-desc__label">当前处理人</text>
                  <text class="u-desc__value">{{ detail.currentHandlerEmpName || detail.currentHandlerEmpNo || '-' }}</text>
                </view>
                <view class="u-desc__row">
                  <text class="u-desc__label">反馈人</text>
                  <text class="u-desc__value">{{ detail.feedbackEmpNo || '-' }}</text>
                </view>
                <view class="u-desc__row">
                  <text class="u-desc__label">责任人</text>
                  <text class="u-desc__value">{{ detail.responsibleEmpNo || '-' }}</text>
                </view>
                <view class="u-desc__row">
                  <text class="u-desc__label">上传人</text>
                  <text class="u-desc__value">{{ detail.proofEmpNo || '-' }}</text>
                </view>
                <view class="u-desc__row">
                  <text class="u-desc__label">审核人</text>
                  <text class="u-desc__value">{{ detail.reviewerEmpNo || '-' }}</text>
                </view>
                <view class="u-desc__row">
                  <text class="u-desc__label">资源号</text>
                  <text class="u-desc__value">{{ detail.resourceNo || '-' }}</text>
                </view>
                <view v-if="detail.inspectionTypeCode" class="u-desc__row">
                  <text class="u-desc__label">巡检类型</text>
                  <view class="u-desc__value">
                    <text class="u-tag u-tag--blue">{{ detail.inspectionTypeCode }}</text>
                  </view>
                </view>
                <view class="u-desc__row">
                  <text class="u-desc__label">提交时间</text>
                  <text class="u-desc__value">{{ detail.submitTime || '-' }}</text>
                </view>
                <view v-if="detail.closeTime" class="u-desc__row">
                  <text class="u-desc__label">关闭时间</text>
                  <text class="u-desc__value">{{ detail.closeTime }}</text>
                </view>
              </view>
            </view>
          </view>

          <view class="u-card u-card--pad-none cps-card">
            <view class="cps-card__head">
              <view class="cps-card-title">
                <text class="cps-card-title__main">问题现场</text>
                <text class="u-tag u-tag--blue">{{ detail.issueAttachments.length }} 张</text>
              </view>
            </view>
            <view class="cps-card__body">
              <view v-if="detail.issueAttachments.length" class="cps-photo-grid">
                <view
                  v-for="(image, index) in detail.issueAttachments"
                  :key="image.id"
                  class="cps-photo-tile"
                  :class="{ 'cps-photo-tile--lead': detail.issueAttachments.length > 1 && index === 0 }"
                  hover-class="u-tap"
                  @click="onPhotoTap"
                  :data-list="'issue'"
                  :data-index="index"
                >
                  <image class="cps-photo-tile__img" mode="aspectFill" :src="attachmentImageSources[image.id] || ''" />
                  <text class="cps-photo-tile__index">{{ index + 1 }}/{{ detail.issueAttachments.length }}</text>
                </view>
              </view>
              <view v-else class="u-empty">
                <text class="u-empty__icon">📭</text>
                <text class="u-empty__text">当前问题还没有现场照片</text>
              </view>
            </view>
          </view>

          <view class="u-card u-card--pad-none cps-card">
            <view class="cps-card__head">
              <view class="cps-card-title">
                <text class="cps-card-title__main">复核照片</text>
                <text class="u-tag u-tag--green">{{ detail.proofAttachments.length }} 张</text>
              </view>
            </view>
            <view class="cps-card__body">
              <view v-if="detail.proofAttachments.length" class="cps-photo-grid">
                <view
                  v-for="(image, index) in detail.proofAttachments"
                  :key="image.id"
                  class="cps-photo-tile"
                  hover-class="u-tap"
                  @click="onPhotoTap"
                  :data-list="'proof'"
                  :data-index="index"
                >
                  <image class="cps-photo-tile__img" mode="aspectFill" :src="attachmentImageSources[image.id] || ''" />
                  <text class="cps-photo-tile__index">{{ index + 1 }}/{{ detail.proofAttachments.length }}</text>
                </view>
              </view>
              <view v-else class="u-empty">
                <text class="u-empty__icon">📭</text>
                <text class="u-empty__text">{{ proofEmptyText }}</text>
              </view>
            </view>
          </view>

          <view class="u-card u-card--pad-none cps-card">
            <view class="cps-card__head">
              <view class="cps-card-title">
                <text class="cps-card-title__main">原因措施</text>
              </view>
            </view>
            <view class="cps-card__body">
              <view class="cps-ai-grid">
                <view class="cps-ai-item">
                  <text class="cps-ai-item__label">AI 分类</text>
                  <text class="cps-ai-item__value">{{ aiCategory }}</text>
                </view>
                <view class="cps-ai-item">
                  <text class="cps-ai-item__label">人工分类</text>
                  <text class="cps-ai-item__value">{{ issueCategory }}</text>
                </view>
                <view class="cps-ai-item">
                  <text class="cps-ai-item__label">AI 原因</text>
                  <text class="cps-ai-item__text">{{ detail.aiSuggestion?.reasonSuggestion || '-' }}</text>
                </view>
                <view class="cps-ai-item">
                  <text class="cps-ai-item__label">AI 措施</text>
                  <text class="cps-ai-item__text">{{ detail.aiSuggestion?.measureSuggestion || '-' }}</text>
                </view>
                <view class="cps-ai-item">
                  <text class="cps-ai-item__label">人工原因</text>
                  <text class="cps-ai-item__text">{{ detail.reasonAnalysis || '-' }}</text>
                </view>
                <view class="cps-ai-item">
                  <text class="cps-ai-item__label">人工措施</text>
                  <text class="cps-ai-item__text">{{ manualMeasureText }}</text>
                </view>
              </view>
            </view>
          </view>
        </view>

        <!-- 右列：节点处理 + 流转记录（横屏并排，竖屏置顶） -->
        <view class="cps-col-action">
          <view class="u-card u-card--pad-none cps-card">
            <view class="cps-card__head">
              <view class="cps-card-title">
                <text class="cps-card-title__main">当前操作</text>
                <view v-if="transferAvailable" class="cps-mode-switch">
                  <text class="cps-mode-switch__label" :class="{ 'is-on': transferMode }">转办</text>
                  <switch :checked="transferMode" color="#14b8a6" @change="(e: any) => (transferMode = Boolean(e.detail.value))" />
                </view>
              </view>
            </view>

            <view class="cps-card__body">
              <!-- 转办模式：只填转办目标，常规处理输入全部隐藏 -->
              <template v-if="transferMode">
                <view class="cps-mode-hint">
                  <text class="cps-mode-hint__title">转交给他人处理</text>
                  <text class="cps-mode-hint__text">当前节点将直接转交，无需填写常规处理内容</text>
                </view>
                <view class="cps-form">
                  <view class="u-field">
                    <text class="u-field__label">转办目标<text class="u-field__req">*</text></text>
                    <view class="u-field__control">
                      <view class="u-input cps-input-trigger" hover-class="u-tap" @click="openPersonPicker('targetEmpNo', '选择转办目标')">
                        <text class="cps-input-trigger__text" :class="{ 'cps-input-trigger__text--placeholder': !actionForm.targetEmpNo }">{{ personLabel(actionForm.targetEmpNo) || '选择转办目标' }}</text>
                        <text class="cps-input-trigger__icon">👤</text>
                      </view>
                    </view>
                  </view>
                  <view class="u-field">
                    <text class="u-field__label">转办说明</text>
                    <view class="u-field__control">
                      <textarea v-model="actionForm.comment" class="u-textarea" :maxlength="-1" auto-height placeholder="说明转办原因，便于接收人跟进" />
                    </view>
                  </view>
                </view>
                <view class="cps-actions">
                  <button class="u-btn u-btn--primary u-btn--block cps-actions__btn" :disabled="actionLoading === 'TRANSFER'" @click="runAction('TRANSFER')">确认转办</button>
                  <button class="u-btn u-btn--block cps-actions__btn" :disabled="actionLoading === 'TRANSFER'" @click="cancelTransfer">取消</button>
                </view>
              </template>

              <!-- 常规模式：按节点填写 -->
              <template v-else>
                <view v-if="showAnyActionFields && normalActions.length" class="cps-form">
                  <template v-if="showFeedbackFields">
                    <!-- 第二流程：反馈人员确认/修正问题分类（默认填入 AI 分类） -->
                    <view class="u-field">
                      <text class="u-field__label">问题分类（一级）</text>
                      <view class="u-field__control">
                        <view class="u-seg cps-selector-group">
                          <view
                            v-for="item in level1Categories"
                            :key="item.value"
                            class="u-seg__item"
                            :class="{ 'u-seg__item--active': actionForm.categoryL1Id === Number(item.value) }"
                            @click="onSelectCategoryL1(Number(item.value))"
                          >
                            <text>{{ item.label }}</text>
                          </view>
                        </view>
                        <text v-if="!level1Categories.length" class="cps-muted">暂无一级分类</text>
                        <text class="cps-default-hint">默认采用 AI 识别分类，可确认或修正</text>
                      </view>
                    </view>
                    <view class="u-field">
                      <text class="u-field__label">问题分类（二级）</text>
                      <view class="u-field__control">
                        <view class="u-seg cps-selector-group">
                          <view
                            v-for="item in level2Categories"
                            :key="item.value"
                            class="u-seg__item"
                            :class="{ 'u-seg__item--active': actionForm.categoryL2Id === Number(item.value) }"
                            @click="onSelectCategoryL2(Number(item.value))"
                          >
                            <text>{{ item.label }}</text>
                          </view>
                        </view>
                        <text v-if="!level2Categories.length" class="cps-muted">先选择一级分类</text>
                      </view>
                    </view>
                    <!-- v2 的原因/措施在「提交整改」时填写，反馈节点只做指派 -->
                    <template v-if="!isV2Flow">
                      <view class="u-field">
                        <text class="u-field__label">原因分析</text>
                        <view class="u-field__control">
                          <textarea v-model="actionForm.reasonAnalysis" class="u-textarea" :maxlength="-1" auto-height placeholder="填写原因分析" />
                        </view>
                      </view>
                      <view class="u-field">
                        <text class="u-field__label">整改措施</text>
                        <view class="u-field__control">
                          <textarea v-model="actionForm.correctiveMeasure" class="u-textarea" :maxlength="-1" auto-height placeholder="填写整改措施" />
                        </view>
                      </view>
                    </template>
                    <view class="u-field">
                      <text class="u-field__label">责任员工</text>
                      <view class="u-field__control">
                        <view class="u-input cps-input-trigger" hover-class="u-tap" @click="openPersonPicker('responsibleEmpNo', '选择责任员工')">
                          <text class="cps-input-trigger__text" :class="{ 'cps-input-trigger__text--placeholder': !actionForm.responsibleEmpNo }">{{ personLabel(actionForm.responsibleEmpNo) || '选择责任员工' }}</text>
                          <text class="cps-input-trigger__icon">👤</text>
                        </view>
                        <text v-if="defaultPersonHint('responsibleEmpNo')" class="cps-default-hint">已默认填入上一节点操作者，可修改</text>
                      </view>
                    </view>
                  </template>

                  <!-- v2：待整改节点直接提交整改（原因 + 短期/长期措施 + 责任员工 + 复核照片） -->
                  <template v-if="showRectifyV2Fields">
                    <view class="u-field">
                      <text class="u-field__label">原因分析<text class="u-field__req">*</text></text>
                      <view class="u-field__control">
                        <textarea v-model="actionForm.reasonAnalysis" class="u-textarea" :maxlength="-1" auto-height placeholder="填写问题原因" />
                      </view>
                    </view>
                    <view class="u-field">
                      <text class="u-field__label">短期措施<text class="u-field__req">*</text></text>
                      <view class="u-field__control">
                        <textarea v-model="actionForm.shortTermMeasure" class="u-textarea" :maxlength="-1" auto-height placeholder="立即执行的整改措施" />
                      </view>
                    </view>
                    <view class="u-field">
                      <text class="u-field__label">长期措施<text class="u-field__req">*</text></text>
                      <view class="u-field__control">
                        <textarea v-model="actionForm.longTermMeasure" class="u-textarea" :maxlength="-1" auto-height placeholder="防止再发的长期措施" />
                      </view>
                    </view>
                    <view class="u-field">
                      <text class="u-field__label">责任员工<text class="u-field__req">*</text></text>
                      <view class="u-field__control">
                        <view class="u-input cps-input-trigger" hover-class="u-tap" @click="openPersonPicker('responsibleEmpNo', '选择责任员工')">
                          <text class="cps-input-trigger__text" :class="{ 'cps-input-trigger__text--placeholder': !actionForm.responsibleEmpNo }">{{ personLabel(actionForm.responsibleEmpNo) || '选择责任员工' }}</text>
                          <text class="cps-input-trigger__icon">👤</text>
                        </view>
                        <text v-if="defaultPersonHint('responsibleEmpNo')" class="cps-default-hint">已默认填入上一节点操作者，可修改</text>
                      </view>
                    </view>
                    <view class="u-field">
                      <text class="u-field__label">复核照片<text class="u-field__req">*</text></text>
                      <view class="u-field__control">
                        <view class="cps-uploader">
                          <button
                              v-if="proofImages.length < 5"
                              class="cps-uploader__trigger"
                              hover-class="u-tap"
                              :disabled="proofUploading"
                              @click="chooseProofImages"
                          >
                            <text class="cps-uploader__plus">+</text>
                            <text class="cps-uploader__text">{{ proofUploading ? '上传中' : '上传整改照片' }}</text>
                          </button>
                          <view class="cps-uploader__grid">
                            <view
                              v-for="(image, index) in proofImages"
                              :key="image.id"
                              class="cps-uploader__item"
                              hover-class="u-tap"
                              @click="onUploadPhotoTap" :data-index="index"
                            >
                              <image class="cps-uploader__img" mode="aspectFill" :src="attachmentImageSources[image.id] || ''" />
                              <text v-if="!proofUploading" class="cps-uploader__delete" @click.stop="removeProofImage(index)">×</text>
                              <text class="cps-uploader__index">{{ index + 1 }}</text>
                            </view>
                          </view>
                        </view>
                        <text class="cps-uploader__hint">至少 1 张、最多 5 张；提交整改后版本锁定</text>
                      </view>
                    </view>
                  </template>

                  <template v-if="showRectifyFields">
                    <view class="u-field">
                      <text class="u-field__label">整改说明</text>
                      <view class="u-field__control">
                        <textarea v-model="actionForm.rectifyRemark" class="u-textarea" :maxlength="-1" auto-height placeholder="填写整改说明" />
                      </view>
                    </view>
                    <view class="u-field">
                      <text class="u-field__label">上传人</text>
                      <view class="u-field__control">
                        <view class="u-input cps-input-trigger" hover-class="u-tap" @click="openPersonPicker('proofEmpNo', '选择上传人')">
                          <text class="cps-input-trigger__text" :class="{ 'cps-input-trigger__text--placeholder': !actionForm.proofEmpNo }">{{ personLabel(actionForm.proofEmpNo) || '选择上传人' }}</text>
                          <text class="cps-input-trigger__icon">👤</text>
                        </view>
                        <text v-if="defaultPersonHint('proofEmpNo')" class="cps-default-hint">已默认填入上一节点操作者，可修改</text>
                      </view>
                    </view>
                  </template>

                  <template v-if="showProofFields">
                    <view class="u-field">
                      <text class="u-field__label">审核人</text>
                      <view class="u-field__control">
                        <view class="u-input cps-input-trigger" hover-class="u-tap" @click="openPersonPicker('reviewerEmpNo', '选择审核人')">
                          <text class="cps-input-trigger__text" :class="{ 'cps-input-trigger__text--placeholder': !actionForm.reviewerEmpNo }">{{ personLabel(actionForm.reviewerEmpNo) || '选择审核人' }}</text>
                          <text class="cps-input-trigger__icon">👤</text>
                        </view>
                      </view>
                    </view>
                    <view class="u-field">
                      <text class="u-field__label">复核照片</text>
                      <view class="u-field__control">
                        <!-- 复核照片（整改凭证）上传：仅「待传图」节点可用，最多 5 张 -->
                        <view class="cps-uploader">
                          <button
                              v-if="proofImages.length < 5"
                              class="cps-uploader__trigger"
                              hover-class="u-tap"
                              :disabled="proofUploading"
                              @click="chooseProofImages"
                          >
                            <text class="cps-uploader__plus">+</text>
                            <text class="cps-uploader__text">{{ proofUploading ? '上传中' : '上传整改图片' }}</text>
                          </button>
                          <view class="cps-uploader__grid">
                            <view
                              v-for="(image, index) in proofImages"
                              :key="image.id"
                              class="cps-uploader__item"
                              hover-class="u-tap"
                              @click="onUploadPhotoTap" :data-index="index"
                            >
                              <image class="cps-uploader__img" mode="aspectFill" :src="attachmentImageSources[image.id] || ''" />
                              <text v-if="!proofUploading" class="cps-uploader__delete" @click.stop="removeProofImage(index)">×</text>
                              <text class="cps-uploader__index">{{ index + 1 }}</text>
                            </view>
                          </view>
                        </view>
                        <text class="cps-uploader__hint">最多上传 5 张</text>
                      </view>
                    </view>
                  </template>

                  <template v-if="showReviewFields">
                    <view class="u-field">
                      <text class="u-field__label">审核意见</text>
                      <view class="u-field__control">
                        <textarea v-model="actionForm.reviewOpinion" class="u-textarea" :maxlength="-1" auto-height placeholder="填写审核意见" />
                      </view>
                    </view>
                  </template>

                  <view class="u-field">
                    <text class="u-field__label">流转备注</text>
                    <view class="u-field__control">
                      <textarea v-model="actionForm.comment" class="u-textarea" :maxlength="-1" auto-height placeholder="填写备注" />
                    </view>
                  </view>
                </view>

                <view v-if="!normalActions.length" class="u-empty">
                  <text class="u-empty__icon">📭</text>
                  <text class="u-empty__text">{{ noActionText }}</text>
                </view>
                <view v-else class="cps-actions">
                  <button
                    v-for="action in normalActions"
                    :key="action"
                    class="u-btn u-btn--block cps-actions__btn"
                    :class="{
                      'u-btn--primary': action === 'REVIEW_CLOSE' || action === 'SUBMIT_RECTIFICATION' || normalActions.length === 1,
                      'cps-actions__btn--success': action === 'REVIEW_CLOSE',
                      'cps-actions__btn--danger': action === 'REVIEW_REJECT',
                    }"
                    :disabled="actionLoading === action"
                    @click="runAction(action)"
                  >
                    {{ actionLabels[action] }}
                  </button>
                </view>
                <text v-if="transferAvailable" class="cps-action-tip">需要转交他人处理？打开右上角「转办」开关</text>
              </template>
            </view>
          </view>

          <view class="u-card u-card--pad-none cps-card">
            <view class="cps-card__head">
              <view class="cps-card-title">
                <text class="cps-card-title__main">处理轨迹</text>
              </view>
            </view>
            <view class="cps-card__body">
              <!-- 流程时间线（内联） -->
              <view v-if="!detail.flowLogs.length" class="u-empty">
                <text class="u-empty__icon">📭</text>
                <text class="u-empty__text">暂无流转记录</text>
              </view>
              <view v-else class="u-timeline cps-flow">
                <view
                  v-for="(log, index) in detail.flowLogs"
                  :key="`${log.action}-${log.createdAt}-${index}`"
                  class="u-timeline__item cps-flow__item"
                  :class="{ 'u-timeline__item--last': index === detail.flowLogs.length - 1 }"
                >
                  <view
                    class="u-timeline__dot cps-flow__dot"
                    :class="{ 'is-last': index === detail.flowLogs.length - 1 }"
                  />
                  <view class="u-timeline__body">
                    <view class="u-timeline__head cps-flow__head">
                      <text class="u-timeline__title cps-flow__title">{{ flowActionLabel(log.action) }}</text>
                      <text class="u-timeline__time cps-flow__time">{{ log.createdAt }}</text>
                    </view>
                    <text class="u-timeline__desc cps-flow__line">{{ flowStatusLabel(log.fromStatus) }} 至 {{ flowStatusLabel(log.toStatus) }}</text>
                    <text class="u-timeline__desc cps-flow__line">操作人：{{ log.operatorEmpNo }}</text>
                    <text v-if="log.comment" class="u-timeline__desc cps-flow__comment">{{ log.comment }}</text>
                  </view>
                </view>
              </view>
            </view>
          </view>
        </view>
      </view>
    </view>

    <view v-else class="cps-page">
      <view class="u-loading cps-page-loading">
        <view class="u-loading__dot" />
        <text class="u-loading__text">加载中…</text>
      </view>
    </view>

    <!-- 人员选择弹窗（内联） -->
    <view v-if="personPickerOpen" class="u-modal-mask" @click.self="personPickerOpen = false">
      <view class="u-modal cps-person-picker" :class="{ 'cps-person-picker--wide': isLandscape }">
        <view class="u-modal__head cps-person-picker__head">
          <text class="cps-person-picker__title">{{ personPickerTitle }}</text>
          <text class="cps-person-picker__close" hover-class="u-tap" @click="personPickerOpen = false">×</text>
        </view>
        <view class="u-modal__body">
          <view class="u-search cps-person-picker__search">
            <input
              v-model="personKeyword"
              class="u-search__input"
              :maxlength="-1"
              placeholder="输入姓名或工号搜索"
              confirm-type="search"
              @confirm="onSearchPeople"
            />
            <button class="u-search__btn" :disabled="personSearching" @click="onSearchPeople">搜索</button>
          </view>
          <view v-if="personSearching" class="u-loading">
            <view class="u-loading__dot" />
            <text class="u-loading__text">搜索中…</text>
          </view>
          <view class="cps-person-picker__body">
            <view v-if="!pickerPeople.length" class="u-empty">
              <text class="u-empty__icon">📭</text>
              <text class="u-empty__text">未找到人员，请调整关键词后搜索</text>
            </view>
            <view class="cps-person-picker__group">
              <view
                v-for="person in pickerPeople"
                :key="person.empNo"
                class="cps-person-picker__option"
                :class="{ 'cps-person-picker__option--active': actionForm[personPickerField] === person.empNo }"
                hover-class="u-tap"
                @click="onSelectPerson(person.empNo)"
              >
                <text class="cps-person-picker__radio">{{ actionForm[personPickerField] === person.empNo ? '●' : '○' }}</text>
                <view class="cps-person-picker__info">
                  <text class="cps-person-picker__name">{{ person.empName || person.empNo }}</text>
                  <text class="cps-person-picker__no">{{ person.empNo }}</text>
                </view>
              </view>
            </view>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { cpsApi } from '@/api/index'
import { computed, onUnmounted, ref, watch } from 'vue'
import { onLoad } from '@dcloudio/uni-app'


import type { CpsAttachmentUploadSource, CpsEmployeeOption, CpsOption } from '@/api/index'
import { loadAttachmentImageSources, resolveAssetUrl, resolveUploadSources, toImageUrl } from '@/utils/image'
import { statusMeta, issueLocation as formatLocation, issueCategory as formatCategory, personLabel as formatPersonLabel } from '@/utils/format'
import { resolveDefaultPersons, type ActionPersonField } from '@/utils/personDefault'
import type {
  CpsAttachment,
  CpsIssueAction,
  CpsIssueDetail,
  CpsIssueStatus,
  CpsUploadedImage,
} from '@/types/cps'

interface DetailStatusMeta {
  label: string
  color: string
}

type CpsIssueDetailCore = Omit<CpsIssueDetail, 'issueAttachments' | 'proofAttachments' | 'aiSuggestion' | 'availableActions' | 'flowLogs'>

interface CpsIssueDetailApiResponse {
  issue?: CpsIssueDetailCore
  issueAttachments?: CpsAttachment[]
  proofAttachments?: CpsAttachment[]
  aiSuggestion?: CpsIssueDetail['aiSuggestion']
  availableActions?: CpsIssueAction[]
  flowLogs?: CpsIssueDetail['flowLogs']
}

const detail = ref<CpsIssueDetail | null>(null)
const issueId = ref<number>(0)
const proofImages = ref<CpsUploadedImage[]>([])
const proofUploading = ref(false)
const attachmentImageSources = ref<Record<number, string>>({})
const selectedPeople = ref<Record<string, CpsEmployeeOption>>({})
const defaultAppliedFields = ref<Set<ActionPersonField>>(new Set())
// 转办模式开关 + 正在提交的动作
const transferMode = ref(false)
const actionLoading = ref<CpsIssueAction | null>(null)

// 分类选项（第二流程：反馈人员确认/修正分类）
const level1Categories = ref<CpsOption[]>([])
const level2Categories = ref<CpsOption[]>([])

// 人员选择器状态（内联）
const personPickerOpen = ref(false)
const personPickerTitle = ref('')
const personPickerField = ref<ActionPersonField>('responsibleEmpNo')
const personKeyword = ref('')
const personSearching = ref(false)
const personSearched = ref(false)
const personSearchResults = ref<CpsEmployeeOption[]>([])

const actionForm = ref({
  reasonAnalysis: '',
  correctiveMeasure: '',
  responsibleEmpNo: '',
  proofEmpNo: '',
  reviewerEmpNo: '',
  rectifyRemark: '',
  reviewOpinion: '',
  targetEmpNo: '',
  comment: '',
  categoryL1Id: null as number | null,
  categoryL2Id: null as number | null,
  shortTermMeasure: '',
  longTermMeasure: '',
})

const isLandscape = ref(false)
const updateOrientation = () => {
  if (typeof uni === 'undefined') return
  isLandscape.value = uni.getSystemInfoSync().windowWidth >= 1200
}
if (typeof uni !== 'undefined') {
  updateOrientation()
  uni.onWindowResize(updateOrientation)
  onUnmounted(() => {
    uni.offWindowResize(updateOrientation)
  })
}

// 标签字典（内联）
const actionLabels: Record<CpsIssueAction, string> = {
  REPLY_ASSIGN: '回复并指派',
  RECTIFY: '完成整改',
  UPLOAD_PROOF: '上传凭证',
  SUBMIT_RECTIFICATION: '提交整改',
  SAVE_DRAFT: '暂存整改',
  REVIEW_CLOSE: '审核通过',
  REVIEW_REJECT: '审核退回',
  TRANSFER: '转办',
}

const flowActionLabels: Record<string, string> = {
  SUBMIT: '提交问题',
  REPLY_ASSIGN: '回复并指派',
  RECTIFY: '完成整改',
  UPLOAD_PROOF: '上传凭证',
  SUBMIT_RECTIFICATION: '提交整改',
  SAVE_DRAFT: '暂存整改',
  REVIEWER_REASSIGN: '改配审核员',
  REVIEW_CLOSE: '审核通过',
  REVIEW_REJECT: '审核退回',
  TRANSFER: '转办',
}

const flowStatusLabels: Record<CpsIssueStatus, string> = {
  PENDING_FEEDBACK: '待反馈',
  PENDING_RECTIFY: '待整改',
  PENDING_UPLOAD_PROOF: '待传图',
  PENDING_AI_REVIEW: 'AI 初审中',
  PENDING_REVIEWER_CONFIG: '待配置审核员',
  PENDING_REVIEW: '待审核',
  CLOSED: '已关闭',
}

const flowActionLabel = (action: string) => flowActionLabels[action] ?? action
const flowStatusLabel = (status: CpsIssueStatus | null) => (status ? flowStatusLabels[status] : '开始')

// v2 流程：提交整改（含整改照片）→ AI 初审 → 人工审核，没有独立的「待传图」节点
const isV2Flow = computed(() => detail.value?.flowVersion === 'v2')
const showFeedbackFields = computed(() => detail.value?.status === 'PENDING_FEEDBACK')
// v1 的「完成整改」与 v2 的「提交整改」是两套表单
const showRectifyFields = computed(() => detail.value?.status === 'PENDING_RECTIFY' && !isV2Flow.value)
const showRectifyV2Fields = computed(() => detail.value?.status === 'PENDING_RECTIFY' && isV2Flow.value)
const showProofFields = computed(() => detail.value?.status === 'PENDING_UPLOAD_PROOF')
// 复核照片卡片的空态说明：告诉用户照片该在哪个节点、哪个位置上传
const proofEmptyText = computed(() => {
  if (!detail.value) return '暂无复核照片'
  if (detail.value.status === 'PENDING_UPLOAD_PROOF') return '还没有复核照片，在上方「当前操作」里上传'
  if (isV2Flow.value && detail.value.status === 'PENDING_RECTIFY') return '还没有复核照片，在「当前操作」提交整改时上传'
  if (detail.value.status === 'CLOSED') return '本单没有复核照片'
  return isV2Flow.value ? '复核照片在「提交整改」时上传' : '复核照片在「待传图」节点由上传人提交'
})
const showReviewFields = computed(() => detail.value?.status === 'PENDING_REVIEW')
// 后端按「当前办理人 + 状态」给出可执行动作；空数组就是不可操作，不再本地兜底，
// 否则非办理人也会看到按钮，点下去会被后端拒绝（"Current employee is not issue handler"）。
const workflowActions = computed<CpsIssueAction[]>(() => detail.value?.availableActions ?? [])
const currentEmpNo = import.meta.env.VITE_CPS_EMP_NO ?? '61016968'
const noActionText = computed(() => {
  if (!detail.value) return '当前节点没有可执行的操作'
  if (detail.value.status === 'CLOSED') return '本单已关闭'
  if (detail.value.status === 'PENDING_AI_REVIEW') return 'AI 初审中，完成后流转到人工审核'
  if (detail.value.status === 'PENDING_REVIEWER_CONFIG') return '结果已出但缺少审核员，等待管理员配置'
  if (detail.value.currentHandlerEmpNo && detail.value.currentHandlerEmpNo !== currentEmpNo) {
    return `当前处理人是 ${detail.value.currentHandlerEmpNo}，你无法执行操作`
  }
  return '当前节点没有可执行的操作'
})
// 转办独立成模式：开启后只填转办目标，常规操作项不再展示
const transferAvailable = computed(() => workflowActions.value.includes('TRANSFER'))
const normalActions = computed<CpsIssueAction[]>(() => workflowActions.value.filter((action) => action !== 'TRANSFER'))
const showAnyActionFields = computed(
  () =>
    showFeedbackFields.value ||
    showRectifyFields.value ||
    showRectifyV2Fields.value ||
    showProofFields.value ||
    showReviewFields.value,
)

const v1Steps: { value: CpsIssueStatus; label: string }[] = [
  { value: 'PENDING_FEEDBACK', label: '待反馈' },
  { value: 'PENDING_RECTIFY', label: '待整改' },
  { value: 'PENDING_UPLOAD_PROOF', label: '待传图' },
  { value: 'PENDING_REVIEW', label: '待审核' },
  { value: 'CLOSED', label: '已关闭' },
]
// v2 新流程没有「待反馈」：新建即进入整改，提交整改后才进 AI 初审
const v2Steps: { value: CpsIssueStatus; label: string }[] = [
  { value: 'PENDING_RECTIFY', label: '待整改' },
  { value: 'PENDING_AI_REVIEW', label: 'AI 初审' },
  { value: 'PENDING_REVIEW', label: '待审核' },
  { value: 'CLOSED', label: '已关闭' },
]
const statusSteps = computed(() => (isV2Flow.value ? v2Steps : v1Steps))
const currentStepIndex = computed(() => {
  if (!detail.value) return 0
  const status = detail.value.status
  if (isV2Flow.value) {
    // 迁移遗留的待反馈单按「待整改」展示；「待配置审核员」并入 AI 初审
    if (status === 'PENDING_FEEDBACK') return 0
    if (status === 'PENDING_REVIEWER_CONFIG') return 1
  }
  const index = statusSteps.value.findIndex((node) => node.value === status)
  return index < 0 ? 0 : index
})
const formatDateTime = (value?: string | null) => (value ? value.replace('T', ' ').slice(0, 16) : '-')


const detailStatus = computed<DetailStatusMeta>(() =>
  detail.value ? { label: statusMeta[detail.value.status].label, color: statusMeta[detail.value.status].color } : { label: '待反馈', color: 'blue' },
)

const issueLocation = computed(() => (detail.value ? formatLocation(detail.value) : '-'))
const issueCategory = computed(() => (detail.value ? formatCategory(detail.value) : '-'))
const aiCategory = computed(() =>
  detail.value?.aiSuggestion
    ? [detail.value.aiSuggestion.aiCategoryL1Name, detail.value.aiSuggestion.aiCategoryL2Name].filter(Boolean).join(' / ') || '-'
    : '-',
)

const knownPeople = computed<CpsEmployeeOption[]>(() => {
  if (!detail.value) return []
  const people = [
    { empNo: detail.value.creatorEmpNo, empName: detail.value.creatorEmpNo },
    { empNo: detail.value.feedbackEmpNo, empName: detail.value.feedbackEmpNo },
    { empNo: detail.value.responsibleEmpNo, empName: detail.value.responsibleEmpName || detail.value.responsibleEmpNo },
    { empNo: detail.value.proofEmpNo, empName: detail.value.proofEmpName || detail.value.proofEmpNo },
    { empNo: detail.value.reviewerEmpNo, empName: detail.value.reviewerEmpName || detail.value.reviewerEmpNo },
    { empNo: detail.value.currentHandlerEmpNo, empName: detail.value.currentHandlerEmpName || detail.value.currentHandlerEmpNo },
  ].filter((person): person is CpsEmployeeOption => Boolean(person.empNo))
  return [...new Map(people.map((person) => [person.empNo, person])).values()]
})

// 未搜索时展示已知人员；搜索后展示搜索结果（合并去重）
const pickerPeople = computed<CpsEmployeeOption[]>(() => {
  if (personSearched.value) {
    return [
      ...personSearchResults.value,
      ...knownPeople.value.filter((c) => !personSearchResults.value.some((s) => s.empNo === c.empNo)),
    ]
  }
  return knownPeople.value
})

const personLabel = (empNo: string) => {
  if (!empNo) return ''
  const person = selectedPeople.value[empNo] ?? knownPeople.value.find((item) => item.empNo === empNo)
  return formatPersonLabel(person?.empName, empNo)
}

const defaultPersonHint = (field: ActionPersonField) => defaultAppliedFields.value.has(field)

const normalizeIssueDetail = (response: CpsIssueDetailApiResponse & Partial<CpsIssueDetail>): CpsIssueDetail => {
  if (!response.issue) {
    return {
      ...response,
      issueAttachments: response.issueAttachments ?? [],
      proofAttachments: response.proofAttachments ?? [],
      aiSuggestion: response.aiSuggestion ?? null,
      availableActions: response.availableActions ?? [],
      flowLogs: response.flowLogs ?? [],
    } as CpsIssueDetail
  }
  return {
    ...response.issue,
    issueAttachments: response.issueAttachments ?? [],
    proofAttachments: response.proofAttachments ?? [],
    aiSuggestion: response.aiSuggestion ?? null,
    availableActions: response.availableActions ?? [],
    flowLogs: response.flowLogs ?? [],
  }
}

/**
 * 加载详情后：重置表单，并按「后续节点默认选择对应人员」预填。
 * 状态每次变化（动作执行后 reload）都会重新计算默认值。
 */
const resetActionForm = () => {
  actionForm.value = {
    reasonAnalysis: '',
    correctiveMeasure: '',
    responsibleEmpNo: '',
    proofEmpNo: '',
    reviewerEmpNo: '',
    rectifyRemark: '',
    reviewOpinion: '',
    targetEmpNo: '',
    comment: '',
    categoryL1Id: null,
    categoryL2Id: null,
    shortTermMeasure: '',
    longTermMeasure: '',
  }
  defaultAppliedFields.value = new Set()
}

const applyDefaultPersons = () => {
  if (!detail.value) return
  resetActionForm()
  // 每次加载后回到常规处理模式
  transferMode.value = false
  const defaults = resolveDefaultPersons(detail.value)
  for (const { field, person } of defaults) {
    actionForm.value[field] = person.empNo
    selectedPeople.value = { ...selectedPeople.value, [person.empNo]: person }
    defaultAppliedFields.value.add(field)
  }
  // 第二流程（反馈回复）：默认填入 AI 识别的分类，供反馈人员确认/修正
  if (detail.value.status === 'PENDING_FEEDBACK' && detail.value.aiSuggestion) {
    actionForm.value.categoryL1Id = detail.value.aiSuggestion.aiCategoryL1Id
    actionForm.value.categoryL2Id = detail.value.aiSuggestion.aiCategoryL2Id
    void loadLevel2Categories(detail.value.aiSuggestion.aiCategoryL1Id)
  }
}

/**
 * v2 暂存整改回填：SAVE_DRAFT 把原因/短期/长期措施/整改说明/责任员工写进问题主表，
 * 重新进入详情时必须按已保存内容回填，否则「暂存整改」下次进来就是空白。
 * 必须在 applyDefaultPersons 之后执行，让已暂存的责任员工覆盖默认人员。
 */
const prefillRectifyDraft = () => {
  const current = detail.value
  if (!current || current.flowVersion !== 'v2' || current.status !== 'PENDING_RECTIFY') return
  actionForm.value.reasonAnalysis = current.reasonAnalysis ?? ''
  actionForm.value.shortTermMeasure = current.shortTermMeasure ?? ''
  actionForm.value.longTermMeasure = current.longTermMeasure ?? ''
  actionForm.value.rectifyRemark = current.rectifyRemark ?? ''
  if (current.responsibleEmpNo) {
    const empNo = current.responsibleEmpNo
    actionForm.value.responsibleEmpNo = empNo
    selectedPeople.value = {
      ...selectedPeople.value,
      [empNo]: { empNo, empName: current.responsibleEmpName || empNo },
    }
    defaultAppliedFields.value.delete('responsibleEmpNo')
  }
}

// 人工原因/措施展示：v2 用短期+长期措施，v1 沿用 correctiveMeasure
const manualMeasureText = computed(() => {
  const current = detail.value
  if (!current) return '-'
  if (current.flowVersion === 'v2') {
    const parts = [
      current.shortTermMeasure ? `短期：${current.shortTermMeasure}` : '',
      current.longTermMeasure ? `长期：${current.longTermMeasure}` : '',
    ].filter(Boolean)
    return parts.length ? parts.join('\n') : '-'
  }
  return current.correctiveMeasure || '-'
})

// 分类选项加载
const loadLevel1Categories = async () => {
  if (!level1Categories.value.length) {
    level1Categories.value = await cpsApi.getCategories()
  }
}

const loadLevel2Categories = async (parentId: number | null) => {
  level2Categories.value = parentId ? await cpsApi.getCategories(parentId) : []
}

const onSelectCategoryL1 = async (categoryL1Id: number) => {
  actionForm.value.categoryL1Id = categoryL1Id
  actionForm.value.categoryL2Id = null
  await loadLevel2Categories(categoryL1Id)
}

const onSelectCategoryL2 = (categoryL2Id: number) => {
  actionForm.value.categoryL2Id = categoryL2Id
}

const load = async () => {
  if (!issueId.value) return
  const response = (await cpsApi.getCpsIssueDetail(issueId.value)) as CpsIssueDetailApiResponse & Partial<CpsIssueDetail>
  detail.value = normalizeIssueDetail(response)
  proofImages.value = (detail.value.proofAttachments ?? []).map((item) => ({
    id: item.id,
    url: item.fileUrl,
    name: item.fileName,
  }))
  attachmentImageSources.value = {}
  const sources = await loadAttachmentImageSources([...detail.value.issueAttachments, ...detail.value.proofAttachments])
  attachmentImageSources.value = sources
  applyDefaultPersons()
  prefillRectifyDraft()
}

// 人员选择器（内联）
const openPersonPicker = (field: ActionPersonField, title: string) => {
  personPickerField.value = field
  personPickerTitle.value = title
  personPickerOpen.value = true
}

watch(personPickerOpen, (v) => {
  if (v) {
    personKeyword.value = ''
    personSearched.value = false
    personSearchResults.value = []
  }
})

const onSearchPeople = async () => {
  const kw = personKeyword.value.trim()
  if (!kw || personSearching.value) return
  personSearching.value = true
  try {
    personSearchResults.value = await cpsApi.searchCpsEmployees(kw)
    personSearched.value = true
  } finally {
    personSearching.value = false
  }
}

const onSelectPerson = (empNo: string) => {
  const person = pickerPeople.value.find((p) => p.empNo === empNo)
  if (person) {
    actionForm.value[personPickerField.value] = empNo
    selectedPeople.value = { ...selectedPeople.value, [empNo]: person }
    defaultAppliedFields.value.delete(personPickerField.value)
    personPickerOpen.value = false
  }
}

const buildActionPayload = (action: CpsIssueAction) => {
  const personName = (empNo: string) => selectedPeople.value[empNo]?.empName ?? knownPeople.value.find((item) => item.empNo === empNo)?.empName
  return {
    action,
    reasonAnalysis: actionForm.value.reasonAnalysis || undefined,
    correctiveMeasure: actionForm.value.correctiveMeasure || undefined,
    responsibleEmpNo: actionForm.value.responsibleEmpNo || undefined,
    responsibleEmpName: personName(actionForm.value.responsibleEmpNo),
    proofEmpNo: actionForm.value.proofEmpNo || undefined,
    proofEmpName: personName(actionForm.value.proofEmpNo),
    reviewerEmpNo: actionForm.value.reviewerEmpNo || undefined,
    reviewerEmpName: personName(actionForm.value.reviewerEmpNo),
    rectifyRemark: actionForm.value.rectifyRemark || undefined,
    reviewOpinion: actionForm.value.reviewOpinion || undefined,
    shortTermMeasure: actionForm.value.shortTermMeasure || undefined,
    longTermMeasure: actionForm.value.longTermMeasure || undefined,
    proofAttachmentIds:
      action === 'UPLOAD_PROOF' || action === 'SUBMIT_RECTIFICATION' || action === 'SAVE_DRAFT'
        ? proofImages.value.map((image) => image.id)
        : undefined,
    targetEmpNo: actionForm.value.targetEmpNo || undefined,
    targetEmpName: personName(actionForm.value.targetEmpNo),
    comment: actionForm.value.comment || undefined,
    // 反馈回复节点提交分类（确认/修正后的最终分类）
    categoryL1Id: action === 'REPLY_ASSIGN' ? actionForm.value.categoryL1Id ?? undefined : undefined,
    categoryL2Id: action === 'REPLY_ASSIGN' ? actionForm.value.categoryL2Id ?? undefined : undefined,
  }
}

const runAction = async (action: CpsIssueAction) => {
  if (actionLoading.value) return
  if (action === 'TRANSFER' && !actionForm.value.targetEmpNo) {
    uni.showToast({ title: '请选择转办目标', icon: 'none' })
    return
  }
  if (action === 'REPLY_ASSIGN' && !actionForm.value.responsibleEmpNo) {
    uni.showToast({ title: '请选择责任员工', icon: 'none' })
    return
  }
  if (action === 'UPLOAD_PROOF' && !proofImages.value.length) {
    uni.showToast({ title: '请先上传至少 1 张复核照片', icon: 'none' })
    return
  }
  // v2 提交整改：原因/短期措施/长期措施/责任员工/复核照片缺一不可
  if (action === 'SUBMIT_RECTIFICATION') {
    if (!actionForm.value.reasonAnalysis.trim()) return void uni.showToast({ title: '请填写原因分析', icon: 'none' })
    if (!actionForm.value.shortTermMeasure.trim()) return void uni.showToast({ title: '请填写短期措施', icon: 'none' })
    if (!actionForm.value.longTermMeasure.trim()) return void uni.showToast({ title: '请填写长期措施', icon: 'none' })
    if (!actionForm.value.responsibleEmpNo) return void uni.showToast({ title: '请选择责任员工', icon: 'none' })
    if (!proofImages.value.length) return void uni.showToast({ title: '请先上传至少 1 张复核照片', icon: 'none' })
  }
  actionLoading.value = action
  try {
    await cpsApi.executeCpsIssueAction(issueId.value, buildActionPayload(action))
    uni.showToast({ title: action === 'TRANSFER' ? '已转办' : '操作成功', icon: 'success' })
    await load()
  } catch (error) {
    uni.showToast({ title: error instanceof Error ? error.message : '操作失败', icon: 'none' })
  } finally {
    actionLoading.value = null
  }
}

const cancelTransfer = () => {
  transferMode.value = false
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

// 现场照片 / 复核照片：统一走 data-list + data-index，回调里再由原始数组取图
const onPhotoTap = (event: unknown) => {
  const dataset = readDataset(event)
  const current = detail.value
  if (!current) return
  const attachments = dataset.list === 'proof' ? current.proofAttachments : current.issueAttachments
  previewImages(attachments.map((item) => toImageUrl(item)), Number(dataset.index ?? 0))
}

// 整改阶段刚上传、还没刷新详情的照片
const onUploadPhotoTap = (event: unknown) => {
  const dataset = readDataset(event)
  previewImages(proofImages.value.map((item) => item.url), Number(dataset.index ?? 0))
}

// 整改照片上传（内联）
const uploadProofImages = async (sources: CpsAttachmentUploadSource[]) => {
  if (!sources.length) return
  proofUploading.value = true
  try {
    for (const source of sources) {
      const uploaded = await cpsApi.uploadCpsAttachment(source)
      proofImages.value = [...proofImages.value, uploaded]
      // 同步加载预览源
      const previewSources = await loadAttachmentImageSources([{ id: uploaded.id, fileUrl: uploaded.url, fileName: uploaded.name }])
      attachmentImageSources.value = { ...attachmentImageSources.value, ...previewSources }
    }
  } catch (error) {
    uni.showToast({ title: error instanceof Error ? error.message : '照片上传失败', icon: 'none' })
  } finally {
    proofUploading.value = false
  }
}

const chooseProofImages = () => {
  const remaining = Math.max(5 - proofImages.value.length, 0)
  if (proofUploading.value) return
  if (!remaining) {
    uni.showToast({ title: '最多上传 5 张照片', icon: 'none' })
    return
  }
  if (typeof uni === 'undefined' || typeof uni.chooseImage !== 'function') {
    uni.showToast({ title: '当前环境不支持拍照/选图', icon: 'none' })
    return
  }
  uni.chooseImage({
    count: remaining,
    sourceType: ['camera', 'album'],
    success: (result) => {
      void uploadProofImages(resolveUploadSources(result))
    },
    fail: (err) => {
      const message = String((err as { errMsg?: string } | undefined)?.errMsg ?? '')
      if (/cancel/i.test(message)) return
      uni.showToast({ title: message || '拍照/选图失败', icon: 'none' })
    },
  })
}

const removeProofImage = (index: number) => {
  proofImages.value = proofImages.value.filter((_, i) => i !== index)
}

onLoad((query?: Record<string, string | string[] | undefined>) => {
  const rawId = Array.isArray(query?.id) ? query?.id[0] : query?.id
  issueId.value = Number(rawId)
  void load()
  // 预载一级分类选项（第二流程反馈节点需要）
  void loadLevel1Categories()
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

.u-tap {
  opacity: 0.72;
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

.u-tag {
  display: inline-flex;
  align-items: center;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  line-height: 18px;
  white-space: nowrap;
}

.u-tag--blue {
  background: #dbeafe;
  color: #1d4ed8;
}

.u-tag--teal {
  background: #ccfbf1;
  color: #0f766e;
}

.u-tag--green {
  background: #dcfce7;
  color: #15803d;
}

.u-tag--orange {
  background: #ffedd5;
  color: #c2410c;
}

.u-tag--red {
  background: #fee2e2;
  color: #b91c1c;
}

.u-tag--gray {
  background: #e2e8f0;
  color: #475569;
}

.u-tag--purple {
  background: #ede9fe;
  color: #6d28d9;
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

.u-timeline {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.u-timeline__item {
  display: flex;
  gap: 12px;
  position: relative;
  padding-bottom: 18px;
}

.u-timeline__item::before {
  content: '';
  position: absolute;
  left: 5px;
  top: 16px;
  bottom: 0;
  width: 1px;
  background: #e2e8f0;
}

.u-timeline__item--last::before {
  display: none;
}

.u-timeline__dot {
  flex: none;
  width: 11px;
  height: 11px;
  margin-top: 5px;
  border-radius: 50%;
  background: var(--cps-teal);
  box-shadow: 0 0 0 3px rgba(20, 184, 166, 0.16);
}

.u-timeline__body {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.u-timeline__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.u-timeline__title {
  font-size: 14px;
  font-weight: 600;
  color: var(--cps-ink);
}

.u-timeline__time {
  font-size: 12px;
  color: var(--cps-muted);
}

.u-timeline__desc {
  font-size: 13px;
  line-height: 1.6;
  color: #475569;
  white-space: pre-wrap;
  word-break: break-word;
}

.u-desc {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.u-desc__row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  font-size: 14px;
}

.u-desc__label {
  flex: none;
  width: 92px;
  color: var(--cps-muted);
}

.u-desc__value {
  flex: 1;
  color: var(--cps-ink);
  word-break: break-word;
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
  max-width: 720px;
  margin: 0 auto;
  padding: 16px 16px 48px;
}

.cps-hero {
  border-radius: var(--cps-radius-xl);
  padding: 20px 24px;
  background: var(--cps-hero-bg);
  box-shadow: var(--cps-shadow-hero);
  color: #fff;
  margin-bottom: 16px;
}

.cps-hero__top {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.cps-hero__identity {
  min-width: 0;
}

.cps-hero__eyebrow {
  display: block;
  margin: 0;
  color: rgba(255, 255, 255, 0.82);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.18em;
}

.cps-hero__title {
  display: block;
  margin-top: 6px;
  font-size: 22px;
  font-weight: 800;
  line-height: 1.45;
  letter-spacing: -0.01em;
  word-break: break-word;
}

.cps-hero__badges {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  justify-content: flex-end;
}

/* 流程进度条：5 个节点，当前节点高亮 */
.cps-steps {
  display: flex;
  gap: 6px;
  margin: 16px 0 0;
  padding: 0;
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

.cps-hero__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.cps-hero__meta-item {
  flex: 0 1 auto;
  max-width: 100%;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.22);
  border-radius: var(--cps-radius-md);
  padding: 8px 12px;
  background: rgba(255, 255, 255, 0.16);
  font-size: 14px;
  font-weight: 700;
  line-height: 1.4;
  text-overflow: ellipsis;
}

/* 两列布局（≥1200px 并排，窄屏单列） */
.cps-detail-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  align-items: start;
  gap: 16px;
}

.cps-col-main,
.cps-col-action {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.cps-card {
  border-radius: var(--cps-radius-lg);
  border: 1px solid var(--cps-border);
  box-shadow: var(--cps-shadow-card);
  overflow: hidden;
}

.cps-card__head {
  display: flex;
  align-items: center;
  min-height: 54px;
  padding: 0 16px;
  border-bottom: 1px solid var(--cps-border);
}

.cps-card__body {
  padding: 16px;
}

.cps-card-title {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.cps-card-title__main {
  display: block;
  margin: 0;
  color: #0f172a;
  font-size: 17px;
  font-weight: 800;
  letter-spacing: -0.01em;
}

.cps-photo-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  gap: 12px;
}

.cps-photo-tile {
  position: relative;
  width: 100%;
  aspect-ratio: 1;
  border-radius: 12px;
  overflow: hidden;
  background: #e2e8f0;
  cursor: pointer;
}

.cps-photo-tile--lead {
  grid-column: span 2;
  grid-row: span 2;
}

.cps-photo-tile__img {
  width: 100%;
  height: 100%;
}

.cps-photo-tile__index {
  position: absolute;
  right: 6px;
  bottom: 6px;
  min-width: 24px;
  height: 22px;
  border-radius: 999px;
  padding: 0 6px;
  background: rgba(15, 23, 42, 0.72);
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  line-height: 22px;
}

.cps-ai-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 10px;
}

@media (min-width: 640px) {
  .cps-ai-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

.cps-ai-item {
  border: 1px solid var(--cps-border);
  border-radius: var(--cps-radius-md);
  padding: 12px;
  background: #fff;
}

.cps-ai-item__label {
  display: block;
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.cps-ai-item__value {
  display: block;
  margin-top: 4px;
  color: #0f172a;
  font-size: 15px;
  font-weight: 800;
}

.cps-ai-item__text {
  display: block;
  margin: 6px 0 0;
  color: var(--cps-ink-2);
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
}

.cps-default-hint {
  display: block;
  margin-top: 4px;
  color: #0f766e;
  font-size: 12px;
}

/* 分类选择器（第二流程反馈节点） */
.cps-selector-group {
  width: 100%;
}

.cps-muted {
  display: block;
  color: #64748b;
  font-size: 14px;
}

/* 表单容器（替代 a-form vertical） */
.cps-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

/* 只读输入触发器（人员选择） */
.cps-input-trigger {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.cps-input-trigger__text {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  color: var(--cps-ink);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cps-input-trigger__text--placeholder {
  color: #94a3b8;
}

.cps-input-trigger__icon {
  flex: none;
  font-size: 15px;
  line-height: 1;
}

/* 整改照片上传（内联） */
.cps-uploader {
  display: grid;
  gap: 12px;
}

.cps-uploader__trigger {
  display: grid;
  align-items: center;
  justify-items: center;
  gap: 4px;
  width: 100%;
  padding: 18px;
  border-radius: var(--cps-radius-lg);
  background: var(--cps-mist);
  border: 1px dashed #99f6e4;
  color: #0f766e;
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

.cps-uploader__img {
  width: 100%;
  height: 100%;
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

.cps-uploader__hint {
  display: block;
  margin: 8px 0 0;
  color: #64748b;
  font-size: 13px;
}

/* 转办模式开关与提示 */
.cps-mode-switch {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.cps-mode-switch__label {
  color: #64748b;
  font-size: 13px;
  font-weight: 800;
}

.cps-mode-switch__label.is-on {
  color: var(--cps-teal-deep);
}

.cps-mode-hint {
  display: grid;
  gap: 2px;
  margin-bottom: 14px;
  border-left: 3px solid var(--cps-teal);
  border-radius: var(--cps-radius-sm);
  padding: 10px 12px;
  background: var(--cps-mist);
}

.cps-mode-hint__title {
  display: block;
  color: var(--cps-teal-deep);
  font-size: 14px;
  font-weight: 800;
}

.cps-mode-hint__text {
  display: block;
  color: #64748b;
  font-size: 12px;
}

.cps-action-tip {
  display: block;
  margin: 12px 0 0;
  color: #94a3b8;
  font-size: 12px;
  text-align: center;
}

/* 动作按钮（内联） */
.cps-actions {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 12px;
  margin-top: 14px;
}

.cps-actions__btn {
  min-height: 48px;
  border-radius: var(--cps-radius-lg);
  font-size: 16px;
  font-weight: 700;
  transition: transform 120ms ease, box-shadow 120ms ease;
}

.cps-actions__btn:not(:disabled):active {
  transform: translateY(1px);
}

.cps-actions__btn--success {
  background: var(--cps-success-deep) !important;
  border-color: var(--cps-success-deep) !important;
  color: #fff !important;
}

.cps-actions__btn--danger {
  background: #fee2e2 !important;
  border-color: #fecaca !important;
  color: var(--cps-danger-deep) !important;
}

/* 流程时间线（内联） */
.cps-flow .cps-flow__dot {
  width: 14px;
  height: 14px;
  margin-top: 5px;
  border: 3px solid #ccfbf1;
  border-radius: 999px;
  background: #14b8a6;
  box-shadow: none;
}

.cps-flow .cps-flow__dot.is-last {
  border-color: #dbeafe;
  background: #2563eb;
}

.cps-flow .cps-flow__head {
  flex-wrap: wrap;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}

.cps-flow .cps-flow__title {
  color: #0f172a;
  font-size: 15px;
  font-weight: 800;
}

.cps-flow .cps-flow__time {
  color: #64748b;
  font-size: 13px;
}

.cps-flow .cps-flow__line {
  display: block;
  margin: 0;
  color: #64748b;
  font-size: 13px;
  line-height: 20px;
}

.cps-flow .cps-flow__comment {
  display: block;
  margin-top: 6px;
  padding: 8px 10px;
  border-radius: var(--cps-radius-sm);
  background: #fff;
  color: var(--cps-ink);
  font-size: 13px;
}

/* 人员选择弹窗（内联） */
.cps-person-picker {
  max-width: 100%;
}

.cps-person-picker--wide {
  max-width: 520px;
}

.cps-person-picker__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.cps-person-picker__title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  color: var(--cps-ink);
  font-size: 16px;
  font-weight: 800;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cps-person-picker__close {
  flex: none;
  width: 28px;
  height: 28px;
  border-radius: 999px;
  background: var(--cps-cloud);
  color: var(--cps-muted);
  font-size: 18px;
  font-weight: 800;
  line-height: 28px;
  text-align: center;
}

.cps-person-picker__search {
  flex: none;
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

.cps-person-picker__option--active {
  background: var(--cps-mist);
  box-shadow: inset 0 0 0 1px var(--cps-teal);
}

.cps-person-picker__radio {
  color: #94a3b8;
  font-size: 14px;
  line-height: 1;
  text-align: center;
}

.cps-person-picker__option--active .cps-person-picker__radio {
  color: var(--cps-teal-deep);
}

.cps-person-picker__info {
  display: grid;
  gap: 2px;
  padding-right: 8px;
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

.cps-page-loading {
  margin-top: 64px;
}

@media (min-width: 1200px) {
  .cps-page {
    max-width: 1280px;
    padding: 20px 28px 56px;
  }

  .cps-detail-layout {
    grid-template-columns: minmax(0, 14fr) minmax(0, 10fr);
  }
}

/* 竖屏/窄屏：把「当前操作」提到最前，现场处理不用滑到底 */
@media (max-width: 1199px) {
  .cps-detail-page .cps-col-action {
    order: -1;
  }
}
</style>
