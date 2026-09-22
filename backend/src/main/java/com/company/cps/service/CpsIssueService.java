package com.company.cps.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.company.cps.domain.CpsIssue;
import com.company.cps.domain.CpsIssueAction;
import com.company.cps.domain.CpsIssueAiSuggestion;
import com.company.cps.domain.CpsIssueFlowLog;
import com.company.cps.domain.CpsIssueStatus;
import com.company.cps.domain.CpsInitialReviewTask;
import com.company.cps.domain.CpsRectificationSubmission;
import com.company.cps.domain.CpsRectificationSubmissionStatus;
import com.company.cps.domain.CpsRectificationTransfer;
import com.company.cps.dto.CpsIssueActionRequest;
import com.company.cps.dto.CpsIssueActionResponse;
import com.company.cps.dto.CpsIssueAiSuggestionRequest;
import com.company.cps.dto.CpsIssueCreateRequest;
import com.company.cps.dto.CpsIssueDetailResponse;
import com.company.cps.dto.CpsIssueListItemResponse;
import com.company.cps.mapper.CpsIssueAiSuggestionMapper;
import com.company.cps.mapper.CpsIssueAttachmentMapper;
import com.company.cps.mapper.CpsIssueFlowLogMapper;
import com.company.cps.mapper.CpsIssueMapper;
import com.company.cps.mapper.CpsRectificationSubmissionMapper;
import com.company.cps.mapper.CpsRectificationTransferMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class CpsIssueService {

    private static final int MAX_ATTACHMENTS = 5;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    /** flow_version 取值：legacy（旧状态机，冻结）/ v2（整改域新流程）。 */
    private static final String FLOW_VERSION_V2 = "v2";

    private final CpsIssueMapper issueMapper;
    private final CpsIssueAttachmentMapper attachmentMapper;
    private final CpsIssueAiSuggestionMapper aiSuggestionMapper;
    private final CpsIssueFlowLogMapper flowLogMapper;
    private final CpsAssignmentService assignmentService;
    private final CpsWorkflowStateMachine stateMachine;
    private final CpsWorkflowStateMachineV2 stateMachineV2;
    private final CpsAgentFrameworkClient agentFrameworkClient;
    private final CpsRectificationSubmissionMapper submissionMapper;
    private final CpsRectificationTransferMapper transferMapper;
    private final CpsInitialReviewService initialReviewService;

    public CpsIssueService(
            CpsIssueMapper issueMapper,
            CpsIssueAttachmentMapper attachmentMapper,
            CpsIssueAiSuggestionMapper aiSuggestionMapper,
            CpsIssueFlowLogMapper flowLogMapper,
            CpsAssignmentService assignmentService,
            CpsWorkflowStateMachine stateMachine
    ) {
        this(issueMapper, attachmentMapper, aiSuggestionMapper, flowLogMapper,
                assignmentService, stateMachine,
                new CpsAgentFrameworkClient(new com.company.cps.config.CpsAgentFrameworkProperties(),
                        CpsAttachmentContentResolver.unsupported()),
                null, null, null, null);
    }

    @Autowired
    public CpsIssueService(
            CpsIssueMapper issueMapper,
            CpsIssueAttachmentMapper attachmentMapper,
            CpsIssueAiSuggestionMapper aiSuggestionMapper,
            CpsIssueFlowLogMapper flowLogMapper,
            CpsAssignmentService assignmentService,
            CpsWorkflowStateMachine stateMachine,
            CpsAgentFrameworkClient agentFrameworkClient,
            CpsWorkflowStateMachineV2 stateMachineV2,
            CpsRectificationSubmissionMapper submissionMapper,
            CpsRectificationTransferMapper transferMapper,
            CpsInitialReviewService initialReviewService
    ) {
        this.issueMapper = issueMapper;
        this.attachmentMapper = attachmentMapper;
        this.aiSuggestionMapper = aiSuggestionMapper;
        this.flowLogMapper = flowLogMapper;
        this.assignmentService = assignmentService;
        this.stateMachine = stateMachine;
        this.stateMachineV2 = stateMachineV2;
        this.agentFrameworkClient = agentFrameworkClient;
        this.submissionMapper = submissionMapper;
        this.transferMapper = transferMapper;
        this.initialReviewService = initialReviewService;
    }

    @Transactional
    public Long createIssue(CpsIssueCreateRequest request, String currentEmpNo) {
        validateCreateRequest(request);
        LocalDateTime now = LocalDateTime.now();

        String feedbackEmpNo = firstNonBlank(
                request.getFeedbackEmpNo(),
                assignmentService.findFeedbackHandler(
                        request.getFactory(),
                        request.getArea(),
                        request.getLine(),
                        request.getProcess()
                )
        );
        if (feedbackEmpNo == null) {
            throw new IllegalArgumentException("feedbackEmpNo is required when no assignment rule matches");
        }
        validateAiSuggestionSource(request.getIssueAttachmentIds(), request.getAiSuggestion());

        CpsIssue issue = new CpsIssue();
        issue.setStatus(CpsIssueStatus.PENDING_FEEDBACK);
        // 新单统一走 V2 整改域流程（flow_version 路由；存量行 DB 默认 'legacy' 不受影响）
        issue.setFlowVersion(FLOW_VERSION_V2);
        issue.setFactory(request.getFactory().trim());
        issue.setArea(request.getArea().trim());
        issue.setLine(request.getLine().trim());
        issue.setProcess(request.getProcess().trim());
        issue.setAiCategoryL1Id(request.getAiCategoryL1Id());
        issue.setAiCategoryL2Id(request.getAiCategoryL2Id());
        issue.setCategoryL1Id(request.getCategoryL1Id());
        issue.setCategoryL2Id(request.getCategoryL2Id());
        issue.setCategoryModifiedFlag(isCategoryModified(request));
        issue.setDescription(request.getDescription().trim());
        issue.setCreatorEmpNo(currentEmpNo);
        issue.setCreatorEmpName(empName(currentEmpNo));
        issue.setFeedbackEmpNo(feedbackEmpNo);
        issue.setFeedbackEmpName(empName(feedbackEmpNo));
        issue.setCurrentHandlerEmpNo(feedbackEmpNo);
        issue.setCurrentHandlerEmpName(empName(feedbackEmpNo));
        issue.setSubmitTime(now);
        issue.setCreatedAt(now);
        issue.setUpdatedAt(now);
        issueMapper.insert(issue);
        Long issueId = requireGeneratedId(issue.getId(), "Issue id was not generated");

        attachFiles(issueId, request.getIssueAttachmentIds(), "ISSUE", currentEmpNo);
        insertAiSuggestionIfPresent(issueId, request.getAiSuggestion(), now);
        insertFlowLog(issueId, null, CpsIssueStatus.PENDING_FEEDBACK, CpsIssueAction.SUBMIT, currentEmpNo, null, feedbackEmpNo, "submit issue");
        String agentInspectionId = agentFrameworkClient.createAndStart(
                issueId, currentEmpNo, request,
                attachmentMapper.findByIssueAndStage(issueId, "ISSUE")
        );
        if (agentInspectionId != null) {
            issue.setAgentInspectionId(agentInspectionId);
            issueMapper.updateAgentInspectionId(issueId, agentInspectionId);
        }
        return issueId;
    }

    @Transactional
    public CpsIssueActionResponse executeAction(Long issueId, CpsIssueActionRequest request, String currentEmpNo) {
        CpsIssue issue = issueMapper.findById(issueId)
                .orElseThrow(() -> new IllegalArgumentException("Issue not found: " + issueId));
        if (!Objects.equals(issue.getCurrentHandlerEmpNo(), currentEmpNo)) {
            throw new IllegalStateException("Current employee is not issue handler");
        }
        if (isV2(issue)) {
            return executeActionV2(issue, request, currentEmpNo);
        }

        CpsIssueStatus fromStatus = issue.getStatus();
        String fromHandler = issue.getCurrentHandlerEmpNo();
        validateActionRequest(issue, request);
        CpsIssueStatus nextStatus = stateMachine.nextStatus(fromStatus, request.getAction());
        applyAction(issue, request, nextStatus, currentEmpNo);
        issue.setUpdatedAt(LocalDateTime.now());
        issueMapper.updateWorkflowFields(issue);

        insertFlowLog(
                issue.getId(),
                fromStatus,
                nextStatus,
                request.getAction(),
                currentEmpNo,
                fromHandler,
                issue.getCurrentHandlerEmpNo(),
                request.getComment()
        );
        return new CpsIssueActionResponse(
                issue.getId(),
                issue.getStatus(),
                issue.getCurrentHandlerEmpNo(),
                stateMachine.availableActions(issue.getStatus())
        );
    }

    public CpsIssueDetailResponse getDetail(Long issueId, String currentEmpNo) {
        CpsIssue issue = issueMapper.findById(issueId)
                .orElseThrow(() -> new IllegalArgumentException("Issue not found: " + issueId));
        CpsIssueDetailResponse response = new CpsIssueDetailResponse();
        response.setIssue(issue);
        response.setIssueAttachments(attachmentMapper.findByIssueAndStage(issueId, "ISSUE"));
        response.setProofAttachments(attachmentMapper.findByIssueAndStage(issueId, "PROOF"));
        response.setAiSuggestion(aiSuggestionMapper.findLatestByIssueId(issueId));
        response.setFlowLogs(flowLogMapper.findByIssueId(issueId));
        response.setAvailableActions(
                Objects.equals(issue.getCurrentHandlerEmpNo(), currentEmpNo)
                        ? availableActionsFor(issue)
                        : Collections.<CpsIssueAction>emptySet()
        );
        return response;
    }

    public List<CpsIssueListItemResponse> list(String tab, int page, int pageSize, String currentEmpNo) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        return issueMapper.listByTab(tab, currentEmpNo, safePageSize, (safePage - 1) * safePageSize);
    }

    private void validateCreateRequest(CpsIssueCreateRequest request) {
        requireText(request.getFactory(), "factory is required");
        requireText(request.getArea(), "area is required");
        requireText(request.getLine(), "line is required");
        requireText(request.getProcess(), "process is required");
        requireNonNull(request.getCategoryL1Id(), "categoryL1Id is required");
        requireNonNull(request.getCategoryL2Id(), "categoryL2Id is required");
        requireText(request.getDescription(), "description is required");
        validateAttachmentCount(request.getIssueAttachmentIds(), "issue attachments must contain 1 to 5 files");
    }

    private void validateActionRequest(CpsIssue issue, CpsIssueActionRequest request) {
        requireNonNull(request.getAction(), "action is required");
        if (request.getAction() == CpsIssueAction.REPLY_ASSIGN) {
            requireText(request.getReasonAnalysis(), "reasonAnalysis is required");
            requireText(request.getCorrectiveMeasure(), "correctiveMeasure is required");
            requireText(request.getResponsibleEmpNo(), "responsibleEmpNo is required");
        } else if (request.getAction() == CpsIssueAction.RECTIFY) {
            requireText(request.getProofEmpNo(), "proofEmpNo is required");
        } else if (request.getAction() == CpsIssueAction.UPLOAD_PROOF) {
            validateAttachmentCount(request.getProofAttachmentIds(), "proof attachments must contain 1 to 5 files");
        } else if (request.getAction() == CpsIssueAction.REVIEW_CLOSE || request.getAction() == CpsIssueAction.REVIEW_REJECT) {
            requireText(request.getReviewOpinion(), "reviewOpinion is required");
        } else if (request.getAction() == CpsIssueAction.TRANSFER) {
            requireText(request.getTargetEmpNo(), "targetEmpNo is required");
        }
        stateMachine.nextStatus(issue.getStatus(), request.getAction());
    }

    private void applyAction(CpsIssue issue, CpsIssueActionRequest request, CpsIssueStatus nextStatus, String currentEmpNo) {
        issue.setStatus(nextStatus);
        if (request.getAction() == CpsIssueAction.REPLY_ASSIGN) {
            issue.setReasonAnalysis(request.getReasonAnalysis().trim());
            issue.setCorrectiveMeasure(request.getCorrectiveMeasure().trim());
            issue.setResponsibleEmpNo(request.getResponsibleEmpNo().trim());
            issue.setResponsibleEmpName(empName(issue.getResponsibleEmpNo()));
            issue.setCurrentHandlerEmpNo(issue.getResponsibleEmpNo());
            issue.setCurrentHandlerEmpName(issue.getResponsibleEmpName());
            // 第二流程：反馈人员确认/修正问题分类（未传则保留创建时的分类）
            if (request.getCategoryL1Id() != null && request.getCategoryL2Id() != null) {
                issue.setCategoryL1Id(request.getCategoryL1Id());
                issue.setCategoryL2Id(request.getCategoryL2Id());
                issue.setCategoryModifiedFlag(
                        !Objects.equals(issue.getAiCategoryL1Id(), request.getCategoryL1Id())
                                || !Objects.equals(issue.getAiCategoryL2Id(), request.getCategoryL2Id())
                );
            }
        } else if (request.getAction() == CpsIssueAction.RECTIFY) {
            issue.setRectifyRemark(trimToNull(request.getRectifyRemark()));
            issue.setProofEmpNo(request.getProofEmpNo().trim());
            issue.setProofEmpName(empName(issue.getProofEmpNo()));
            issue.setCurrentHandlerEmpNo(issue.getProofEmpNo());
            issue.setCurrentHandlerEmpName(issue.getProofEmpName());
        } else if (request.getAction() == CpsIssueAction.UPLOAD_PROOF) {
            attachFiles(issue.getId(), request.getProofAttachmentIds(), "PROOF", currentEmpNo);
            String reviewer = firstNonBlank(
                    request.getReviewerEmpNo(),
                    issue.getReviewerEmpNo(),
                    assignmentService.findReviewer(issue.getFactory(), issue.getArea())
            );
            if (reviewer == null) {
                throw new IllegalArgumentException("reviewerEmpNo is required when no assignment rule matches");
            }
            issue.setReviewerEmpNo(reviewer);
            issue.setReviewerEmpName(empName(reviewer));
            issue.setCurrentHandlerEmpNo(reviewer);
            issue.setCurrentHandlerEmpName(issue.getReviewerEmpName());
        } else if (request.getAction() == CpsIssueAction.REVIEW_CLOSE) {
            issue.setReviewOpinion(request.getReviewOpinion().trim());
            issue.setCurrentHandlerEmpNo(null);
            issue.setCurrentHandlerEmpName(null);
            issue.setCloseTime(LocalDateTime.now());
        } else if (request.getAction() == CpsIssueAction.REVIEW_REJECT) {
            issue.setReviewOpinion(request.getReviewOpinion().trim());
            issue.setCurrentHandlerEmpNo(issue.getProofEmpNo());
            issue.setCurrentHandlerEmpName(issue.getProofEmpName());
        } else if (request.getAction() == CpsIssueAction.TRANSFER) {
            issue.setCurrentHandlerEmpNo(request.getTargetEmpNo().trim());
            issue.setCurrentHandlerEmpName(empName(issue.getCurrentHandlerEmpNo()));
        }
    }

    private void attachFiles(Long issueId, List<Long> attachmentIds, String stage, String currentEmpNo) {
        for (int i = 0; i < attachmentIds.size(); i++) {
            Long attachmentId = attachmentIds.get(i);
            int updatedRows = attachmentMapper.attachToIssue(attachmentId, issueId, stage, i + 1, currentEmpNo, empName(currentEmpNo));
            if (updatedRows != 1) {
                throw new IllegalStateException("Attachment " + attachmentId + " cannot be bound to issue " + issueId);
            }
        }
    }

    // ==================== V2 整改域（flow_version='v2' 路由） ====================

    /** flow_version 路由判定：仅 'v2' 走新状态机；null/'legacy' 保持旧机（语义冻结）。 */
    private static boolean isV2(CpsIssue issue) {
        return FLOW_VERSION_V2.equalsIgnoreCase(issue.getFlowVersion());
    }

    private Set<CpsIssueAction> availableActionsFor(CpsIssue issue) {
        return isV2(issue) ? stateMachineV2.availableActions(issue.getStatus())
                : stateMachine.availableActions(issue.getStatus());
    }

    private CpsIssueActionResponse executeActionV2(CpsIssue issue, CpsIssueActionRequest request, String currentEmpNo) {
        requireV2Dependencies();
        CpsIssueStatus fromStatus = issue.getStatus();
        String fromHandler = issue.getCurrentHandlerEmpNo();
        validateActionRequestV2(issue, request);
        LocalDateTime now = LocalDateTime.now();
        CpsIssueStatus nextStatus;
        Long initialReviewTaskId = null;
        if (request.getAction() == CpsIssueAction.SUBMIT_RECTIFICATION) {
            String reviewer = initialReviewService.resolveReviewer(issue, request.getReviewerEmpNo());
            // 提交落点带上下文：审核员可解析→PENDING_AI_REVIEW；无人可审→PENDING_REVIEWER_CONFIG（AC-25 提交保留）
            nextStatus = stateMachineV2.resolveSubmissionTarget(reviewer != null);
            initialReviewTaskId = applySubmitRectification(issue, request, nextStatus, reviewer, currentEmpNo, now);
        } else {
            nextStatus = stateMachineV2.nextStatus(fromStatus, request.getAction());
            applyActionV2(issue, request, nextStatus, currentEmpNo, now);
        }
        issue.setUpdatedAt(now);
        issueMapper.updateWorkflowFields(issue);
        insertFlowLog(issue.getId(), fromStatus, nextStatus, request.getAction(), currentEmpNo,
                fromHandler, issue.getCurrentHandlerEmpNo(), request.getComment());
        if (initialReviewTaskId != null) {
            // 事务提交后投递 C-01（事务外；投递失败由任务 FAILED 承接，不回滚提交快照）
            initialReviewService.dispatchAfterCommit(initialReviewTaskId);
        }
        return new CpsIssueActionResponse(
                issue.getId(),
                issue.getStatus(),
                issue.getCurrentHandlerEmpNo(),
                stateMachineV2.availableActions(issue.getStatus())
        );
    }

    private void requireV2Dependencies() {
        if (stateMachineV2 == null || initialReviewService == null || submissionMapper == null || transferMapper == null) {
            throw new IllegalStateException("V2 rectification flow dependencies are not wired for this service instance");
        }
    }

    private void validateActionRequestV2(CpsIssue issue, CpsIssueActionRequest request) {
        requireNonNull(request.getAction(), "action is required");
        if (request.getAction() == CpsIssueAction.REPLY_ASSIGN) {
            // V2：原因/措施在 SUBMIT_RECTIFICATION 时填写；REPLY_ASSIGN 仅指派整改办理人
            requireText(request.getResponsibleEmpNo(), "responsibleEmpNo is required");
        } else if (request.getAction() == CpsIssueAction.SUBMIT_RECTIFICATION) {
            requireText(request.getReasonAnalysis(), "reasonAnalysis is required");
            requireText(request.getShortTermMeasure(), "shortTermMeasure is required");
            requireText(request.getLongTermMeasure(), "longTermMeasure is required");
            requireText(request.getResponsibleEmpNo(), "responsibleEmpNo is required");
            validateAttachmentCount(request.getProofAttachmentIds(), "proof attachments must contain 1 to 5 files");
        } else if (request.getAction() == CpsIssueAction.SAVE_DRAFT) {
            // 暂存允许不完整内容（PRD §28.3），不触发初审
        } else if (request.getAction() == CpsIssueAction.REVIEW_CLOSE || request.getAction() == CpsIssueAction.REVIEW_REJECT) {
            requireText(request.getReviewOpinion(), "reviewOpinion is required");
        } else if (request.getAction() == CpsIssueAction.TRANSFER) {
            requireText(request.getTargetEmpNo(), "targetEmpNo is required");
        }
        // 确定性流转校验（SUBMIT_RECTIFICATION 的上下文落点由 resolveSubmissionTarget 承接）
        stateMachineV2.nextStatus(issue.getStatus(), request.getAction());
    }

    /**
     * 提交整改（PRD §28.1/28.3）：版本快照+锁定、旧版本 SUPERSEDED、创建初审任务（RUNNING，+600s）。
     * 版本锁定期间（PENDING_AI_REVIEW/PENDING_REVIEWER_CONFIG/PENDING_REVIEW）不可编辑或转办。
     */
    private Long applySubmitRectification(CpsIssue issue, CpsIssueActionRequest request,
                                          CpsIssueStatus target, String reviewer,
                                          String currentEmpNo, LocalDateTime now) {
        int versionNo = issue.getCurrentSubmissionVersion() == null ? 1 : issue.getCurrentSubmissionVersion() + 1;
        submissionMapper.markSuperseded(issue.getId());
        attachFiles(issue.getId(), request.getProofAttachmentIds(), "PROOF", currentEmpNo);

        CpsRectificationSubmission submission = new CpsRectificationSubmission();
        submission.setIssueId(issue.getId());
        submission.setVersionNo(versionNo);
        submission.setReason(request.getReasonAnalysis().trim());
        submission.setShortTermMeasure(request.getShortTermMeasure().trim());
        submission.setLongTermMeasure(request.getLongTermMeasure().trim());
        submission.setResponsibleEmpNo(request.getResponsibleEmpNo().trim());
        submission.setResponsibleEmpName(empName(request.getResponsibleEmpNo().trim()));
        submission.setAttachmentIds(submissionAttachmentJson(issue.getId(), request.getProofAttachmentIds()));
        submission.setSubmittedBy(currentEmpNo);
        submission.setSubmittedName(empName(currentEmpNo));
        submission.setSubmittedAt(now);
        submission.setSource(versionNo > 1 ? "RESUBMIT" : "SUBMIT");
        submission.setStatus(CpsRectificationSubmissionStatus.LOCKED.name());
        submission.setCreatedAt(now);
        submission.setUpdatedAt(now);
        submissionMapper.insert(submission);

        issue.setReasonAnalysis(request.getReasonAnalysis().trim());
        issue.setShortTermMeasure(request.getShortTermMeasure().trim());
        issue.setLongTermMeasure(request.getLongTermMeasure().trim());
        issue.setResponsibleEmpNo(request.getResponsibleEmpNo().trim());
        issue.setResponsibleEmpName(empName(request.getResponsibleEmpNo().trim()));
        issue.setCurrentSubmissionVersion(versionNo);
        issue.setStatus(target);
        if (reviewer != null) {
            issue.setReviewerEmpNo(reviewer);
            issue.setReviewerEmpName(empName(reviewer));
        }
        // 初审中/待配置期间无人可办理：清空当前处理人（后续系统流转将路由到审核员）
        issue.setCurrentHandlerEmpNo(null);
        issue.setCurrentHandlerEmpName(null);

        CpsInitialReviewTask task = initialReviewService.createTask(issue.getId(), submission.getId(), versionNo, now);
        return task.getId();
    }

    private void applyActionV2(CpsIssue issue, CpsIssueActionRequest request,
                               CpsIssueStatus nextStatus, String currentEmpNo, LocalDateTime now) {
        issue.setStatus(nextStatus);
        if (request.getAction() == CpsIssueAction.REPLY_ASSIGN) {
            issue.setResponsibleEmpNo(request.getResponsibleEmpNo().trim());
            issue.setResponsibleEmpName(empName(issue.getResponsibleEmpNo()));
            issue.setCurrentHandlerEmpNo(issue.getResponsibleEmpNo());
            issue.setCurrentHandlerEmpName(issue.getResponsibleEmpName());
            if (request.getCategoryL1Id() != null && request.getCategoryL2Id() != null) {
                issue.setCategoryL1Id(request.getCategoryL1Id());
                issue.setCategoryL2Id(request.getCategoryL2Id());
                issue.setCategoryModifiedFlag(
                        !Objects.equals(issue.getAiCategoryL1Id(), request.getCategoryL1Id())
                                || !Objects.equals(issue.getAiCategoryL2Id(), request.getCategoryL2Id())
                );
            }
        } else if (request.getAction() == CpsIssueAction.SAVE_DRAFT) {
            issue.setReasonAnalysis(trimToNull(request.getReasonAnalysis()));
            issue.setShortTermMeasure(trimToNull(request.getShortTermMeasure()));
            issue.setLongTermMeasure(trimToNull(request.getLongTermMeasure()));
            issue.setRectifyRemark(trimToNull(request.getRectifyRemark()));
            if (!isBlank(request.getResponsibleEmpNo())) {
                issue.setResponsibleEmpNo(request.getResponsibleEmpNo().trim());
                issue.setResponsibleEmpName(empName(issue.getResponsibleEmpNo()));
            }
        } else if (request.getAction() == CpsIssueAction.TRANSFER) {
            // 转办：仅当前承办人办理并接收退回，原办理人失权；责任员工不变（PRD §28.3）
            issue.setCurrentHandlerEmpNo(request.getTargetEmpNo().trim());
            issue.setCurrentHandlerEmpName(empName(issue.getCurrentHandlerEmpNo()));
            CpsRectificationTransfer transfer = new CpsRectificationTransfer();
            transfer.setIssueId(issue.getId());
            transfer.setVersionNo(issue.getCurrentSubmissionVersion() == null || issue.getCurrentSubmissionVersion() <= 0
                    ? null : issue.getCurrentSubmissionVersion());
            transfer.setFromEmpNo(currentEmpNo);
            transfer.setFromEmpName(empName(currentEmpNo));
            transfer.setToEmpNo(issue.getCurrentHandlerEmpNo());
            transfer.setToEmpName(issue.getCurrentHandlerEmpName());
            transfer.setTransferredAt(now);
            transfer.setRemark(trimToNull(request.getComment()));
            transfer.setCreatedAt(now);
            transferMapper.insert(transfer);
        } else if (request.getAction() == CpsIssueAction.REVIEW_CLOSE) {
            issue.setReviewOpinion(request.getReviewOpinion().trim());
            issue.setCurrentHandlerEmpNo(null);
            issue.setCurrentHandlerEmpName(null);
            issue.setCloseTime(now);
            markLatestSubmissionReviewed(issue);
        } else if (request.getAction() == CpsIssueAction.REVIEW_REJECT) {
            // 退回整改人员：重提必须再次触发 AI 初审（新版本），不沿用旧版本意见（PRD §28.3）
            issue.setReviewOpinion(request.getReviewOpinion().trim());
            CpsRectificationSubmission latest = submissionMapper.findLatestByIssueId(issue.getId());
            String rectifier = latest != null ? latest.getSubmittedBy() : issue.getResponsibleEmpNo();
            issue.setCurrentHandlerEmpNo(rectifier);
            issue.setCurrentHandlerEmpName(empName(rectifier));
            markLatestSubmissionReviewed(issue);
        }
    }

    private void markLatestSubmissionReviewed(CpsIssue issue) {
        if (issue.getCurrentSubmissionVersion() != null && issue.getCurrentSubmissionVersion() > 0) {
            submissionMapper.markReviewed(issue.getId(), issue.getCurrentSubmissionVersion());
        }
    }

    /** 版本证据附件快照：{"before":[ISSUE 阶段 ID],"after":[PROOF 阶段 ID]}。 */
    private String submissionAttachmentJson(Long issueId, List<Long> proofAttachmentIds) {
        List<Long> before = new java.util.ArrayList<>();
        for (com.company.cps.domain.CpsIssueAttachment attachment : attachmentMapper.findByIssueAndStage(issueId, "ISSUE")) {
            before.add(attachment.getId());
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("before", before);
        snapshot.put("after", proofAttachmentIds);
        try {
            return OBJECT_MAPPER.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize submission attachment ids", exception);
        }
    }


    private void insertAiSuggestionIfPresent(Long issueId, CpsIssueAiSuggestionRequest request, LocalDateTime now) {
        if (request == null) {
            return;
        }
        CpsIssueAiSuggestion suggestion = new CpsIssueAiSuggestion();
        suggestion.setIssueId(issueId);
        suggestion.setSourceAttachmentId(request.getSourceAttachmentId());
        suggestion.setAiCategoryL1Id(request.getAiCategoryL1Id());
        suggestion.setAiCategoryL1Name(request.getAiCategoryL1Name());
        suggestion.setAiCategoryL2Id(request.getAiCategoryL2Id());
        suggestion.setAiCategoryL2Name(request.getAiCategoryL2Name());
        suggestion.setReasonSuggestion(request.getReasonSuggestion());
        suggestion.setMeasureSuggestion(request.getMeasureSuggestion());
        suggestion.setRawRequest(request.getRawRequest());
        suggestion.setRawResponse(request.getRawResponse());
        suggestion.setConfidence(request.getConfidence());
        suggestion.setCreatedAt(now);
        aiSuggestionMapper.insert(suggestion);
    }

    private static void validateAiSuggestionSource(List<Long> issueAttachmentIds, CpsIssueAiSuggestionRequest request) {
        if (request == null) {
            return;
        }
        Long firstAttachmentId = issueAttachmentIds.get(0);
        if (!Objects.equals(request.getSourceAttachmentId(), firstAttachmentId)) {
            throw new IllegalArgumentException("sourceAttachmentId must match first issue attachment id");
        }
    }

    private void insertFlowLog(
            Long issueId,
            CpsIssueStatus fromStatus,
            CpsIssueStatus toStatus,
            CpsIssueAction action,
            String operatorEmpNo,
            String fromHandlerEmpNo,
            String toHandlerEmpNo,
            String comment
    ) {
        CpsIssueFlowLog log = new CpsIssueFlowLog();
        log.setIssueId(issueId);
        log.setFromStatus(fromStatus);
        log.setToStatus(toStatus);
        log.setAction(action);
        log.setOperatorEmpNo(operatorEmpNo);
        log.setFromHandlerEmpNo(fromHandlerEmpNo);
        log.setToHandlerEmpNo(toHandlerEmpNo);
        log.setOperatorEmpName(empName(operatorEmpNo));
        log.setFromHandlerEmpName(empName(fromHandlerEmpNo));
        log.setToHandlerEmpName(empName(toHandlerEmpNo));
        log.setComment(trimToNull(comment));
        log.setSnapshotJson(snapshotJson(action, comment, fromStatus, toStatus, operatorEmpNo, fromHandlerEmpNo, toHandlerEmpNo));
        log.setCreatedAt(LocalDateTime.now());
        flowLogMapper.insert(log);
    }

    private static String snapshotJson(
            CpsIssueAction action,
            String comment,
            CpsIssueStatus fromStatus,
            CpsIssueStatus toStatus,
            String operatorEmpNo,
            String fromHandlerEmpNo,
            String toHandlerEmpNo
    ) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("action", action);
        snapshot.put("comment", trimToNull(comment));
        snapshot.put("fromStatus", fromStatus);
        snapshot.put("toStatus", toStatus);
        snapshot.put("operatorEmpNo", operatorEmpNo);
        snapshot.put("fromHandlerEmpNo", fromHandlerEmpNo);
        snapshot.put("toHandlerEmpNo", toHandlerEmpNo);
        try {
            return OBJECT_MAPPER.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize flow log snapshot", e);
        }
    }

    private static void validateAttachmentCount(List<Long> attachmentIds, String message) {
        if (attachmentIds == null || attachmentIds.isEmpty() || attachmentIds.size() > MAX_ATTACHMENTS) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void requireNonNull(Object value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
    }

    private static Long requireGeneratedId(Long value, String message) {
        if (value == null) {
            throw new IllegalStateException(message);
        }
        return value;
    }

    private static void requireText(String value, String message) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(message);
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static String trimToNull(String value) {
        if (isBlank(value)) {
            return null;
        }
        return value.trim();
    }

    private static String empName(String empNo) {
        return empNo;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static Boolean isCategoryModified(CpsIssueCreateRequest request) {
        return !Objects.equals(request.getAiCategoryL1Id(), request.getCategoryL1Id())
                || !Objects.equals(request.getAiCategoryL2Id(), request.getCategoryL2Id());
    }

}
