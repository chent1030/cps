package com.company.cps.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.company.cps.domain.CpsIssue;
import com.company.cps.domain.CpsIssueAction;
import com.company.cps.domain.CpsIssueAiSuggestion;
import com.company.cps.domain.CpsIssueFlowLog;
import com.company.cps.domain.CpsIssueStatus;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class CpsIssueService {

    private static final int MAX_ATTACHMENTS = 5;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final CpsIssueMapper issueMapper;
    private final CpsIssueAttachmentMapper attachmentMapper;
    private final CpsIssueAiSuggestionMapper aiSuggestionMapper;
    private final CpsIssueFlowLogMapper flowLogMapper;
    private final CpsAssignmentService assignmentService;
    private final CpsWorkflowStateMachine stateMachine;

    public CpsIssueService(
            CpsIssueMapper issueMapper,
            CpsIssueAttachmentMapper attachmentMapper,
            CpsIssueAiSuggestionMapper aiSuggestionMapper,
            CpsIssueFlowLogMapper flowLogMapper,
            CpsAssignmentService assignmentService,
            CpsWorkflowStateMachine stateMachine
    ) {
        this.issueMapper = issueMapper;
        this.attachmentMapper = attachmentMapper;
        this.aiSuggestionMapper = aiSuggestionMapper;
        this.flowLogMapper = flowLogMapper;
        this.assignmentService = assignmentService;
        this.stateMachine = stateMachine;
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
        issue.setIssueNo(newIssueNo(now));
        issue.setStatus(CpsIssueStatus.PENDING_FEEDBACK);
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
        return issueId;
    }

    @Transactional
    public CpsIssueActionResponse executeAction(Long issueId, CpsIssueActionRequest request, String currentEmpNo) {
        CpsIssue issue = issueMapper.findById(issueId)
                .orElseThrow(() -> new IllegalArgumentException("Issue not found: " + issueId));
        if (!Objects.equals(issue.getCurrentHandlerEmpNo(), currentEmpNo)) {
            throw new IllegalStateException("Current employee is not issue handler");
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
                        ? stateMachine.availableActions(issue.getStatus())
                        : Collections.emptySet()
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

    private static String newIssueNo(LocalDateTime now) {
        return "CPS" + now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
    }
}
