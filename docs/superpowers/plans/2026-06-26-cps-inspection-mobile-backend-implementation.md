# CPS Inspection Mobile Backend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the CPS inspection mobile workflow and Spring Boot + MyBatis backend logic from the approved design.

**Architecture:** The backend uses a lightweight workflow state machine centered on `cps_issue.status`, `current_handler_emp_no`, and flow logs. The Vue3 mobile app renders issue creation, issue lists, issue detail, and node action forms based on backend-returned `availableActions`.

**Tech Stack:** Spring Boot, MyBatis, MySQL-compatible SQL, JUnit 5, Vue3, TypeScript, Pinia, Vue Router.

---

## File Structure

Use these paths as the target structure when merging into the existing project.

Backend:

```text
backend/src/main/resources/db/migration/V20260626__cps_inspection.sql
backend/src/main/java/com/company/cps/domain/CpsIssueStatus.java
backend/src/main/java/com/company/cps/domain/CpsIssueAction.java
backend/src/main/java/com/company/cps/domain/CpsAttachmentStage.java
backend/src/main/java/com/company/cps/domain/CpsIssue.java
backend/src/main/java/com/company/cps/domain/CpsIssueAttachment.java
backend/src/main/java/com/company/cps/domain/CpsIssueAiSuggestion.java
backend/src/main/java/com/company/cps/domain/CpsIssueFlowLog.java
backend/src/main/java/com/company/cps/dto/CpsIssueCreateRequest.java
backend/src/main/java/com/company/cps/dto/CpsIssueActionRequest.java
backend/src/main/java/com/company/cps/dto/CpsIssueDetailResponse.java
backend/src/main/java/com/company/cps/dto/CpsIssueListItemResponse.java
backend/src/main/java/com/company/cps/mapper/CpsIssueMapper.java
backend/src/main/java/com/company/cps/mapper/CpsIssueAttachmentMapper.java
backend/src/main/java/com/company/cps/mapper/CpsIssueAiSuggestionMapper.java
backend/src/main/java/com/company/cps/mapper/CpsIssueFlowLogMapper.java
backend/src/main/java/com/company/cps/service/CpsWorkflowStateMachine.java
backend/src/main/java/com/company/cps/service/CpsIssueService.java
backend/src/main/java/com/company/cps/service/CpsAssignmentService.java
backend/src/main/java/com/company/cps/controller/CpsIssueController.java
backend/src/test/java/com/company/cps/service/CpsWorkflowStateMachineTest.java
backend/src/test/java/com/company/cps/service/CpsIssueServiceTest.java
```

Mobile:

```text
mobile/src/api/cps/issue.ts
mobile/src/api/cps/master.ts
mobile/src/types/cps.ts
mobile/src/views/cps/IssueCreateView.vue
mobile/src/views/cps/IssueListView.vue
mobile/src/views/cps/IssueDetailView.vue
mobile/src/components/cps/ImageUploader.vue
mobile/src/components/cps/LocationSelector.vue
mobile/src/components/cps/CategorySelector.vue
mobile/src/components/cps/ActionPanel.vue
mobile/src/components/cps/FlowTimeline.vue
mobile/src/router/cpsRoutes.ts
mobile/src/views/cps/__tests__/IssueCreateView.spec.ts
mobile/src/components/cps/__tests__/ActionPanel.spec.ts
```

If the existing project uses a different package name or folder layout, keep the same class/component responsibilities and move the files into the local conventions.

---

## Task 1: Create Backend Schema

**Files:**
- Create: `backend/src/main/resources/db/migration/V20260626__cps_inspection.sql`

- [ ] **Step 1: Add migration SQL**

```sql
CREATE TABLE cps_issue (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  issue_no VARCHAR(40) NOT NULL UNIQUE,
  status VARCHAR(40) NOT NULL,
  factory_id BIGINT NOT NULL,
  area_id BIGINT NOT NULL,
  line_id BIGINT NOT NULL,
  process_id BIGINT NOT NULL,
  ai_category_l1_id BIGINT NULL,
  ai_category_l2_id BIGINT NULL,
  category_l1_id BIGINT NOT NULL,
  category_l2_id BIGINT NOT NULL,
  category_modified_flag TINYINT NOT NULL DEFAULT 0,
  description VARCHAR(1000) NOT NULL,
  creator_emp_no VARCHAR(40) NOT NULL,
  feedback_emp_no VARCHAR(40) NOT NULL,
  responsible_emp_no VARCHAR(40) NULL,
  proof_emp_no VARCHAR(40) NULL,
  reviewer_emp_no VARCHAR(40) NULL,
  current_handler_emp_no VARCHAR(40) NULL,
  reason_analysis VARCHAR(1000) NULL,
  corrective_measure VARCHAR(1000) NULL,
  rectify_remark VARCHAR(1000) NULL,
  review_opinion VARCHAR(1000) NULL,
  submit_time DATETIME NOT NULL,
  close_time DATETIME NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  INDEX idx_cps_issue_handler_status (current_handler_emp_no, status),
  INDEX idx_cps_issue_creator (creator_emp_no),
  INDEX idx_cps_issue_status_submit_time (status, submit_time)
);

CREATE TABLE cps_issue_attachment (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  issue_id BIGINT NOT NULL,
  stage VARCHAR(20) NOT NULL,
  file_url VARCHAR(500) NOT NULL,
  file_name VARCHAR(200) NOT NULL,
  file_type VARCHAR(80) NOT NULL,
  sort_no INT NOT NULL,
  created_by VARCHAR(40) NOT NULL,
  created_at DATETIME NOT NULL,
  UNIQUE KEY uk_cps_attachment_sort (issue_id, stage, sort_no),
  INDEX idx_cps_attachment_issue_stage (issue_id, stage)
);

CREATE TABLE cps_issue_ai_suggestion (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  issue_id BIGINT NOT NULL,
  source_attachment_id BIGINT NULL,
  ai_category_l1_id BIGINT NULL,
  ai_category_l1_name VARCHAR(100) NULL,
  ai_category_l2_id BIGINT NULL,
  ai_category_l2_name VARCHAR(100) NULL,
  reason_suggestion VARCHAR(1000) NULL,
  measure_suggestion VARCHAR(1000) NULL,
  model_name VARCHAR(100) NULL,
  model_version VARCHAR(100) NULL,
  raw_request JSON NULL,
  raw_response JSON NULL,
  confidence DECIMAL(6, 4) NULL,
  created_at DATETIME NOT NULL,
  INDEX idx_cps_ai_issue (issue_id)
);

CREATE TABLE cps_issue_flow_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  issue_id BIGINT NOT NULL,
  from_status VARCHAR(40) NULL,
  to_status VARCHAR(40) NOT NULL,
  action VARCHAR(40) NOT NULL,
  operator_emp_no VARCHAR(40) NOT NULL,
  from_handler_emp_no VARCHAR(40) NULL,
  to_handler_emp_no VARCHAR(40) NULL,
  comment VARCHAR(1000) NULL,
  snapshot_json JSON NULL,
  created_at DATETIME NOT NULL,
  INDEX idx_cps_flow_issue_time (issue_id, created_at),
  INDEX idx_cps_flow_operator (operator_emp_no)
);

CREATE TABLE cps_reminder_rule (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  node_status VARCHAR(40) NOT NULL,
  duration_days INT NOT NULL,
  need_escalation TINYINT NOT NULL DEFAULT 0,
  escalation_level INT NULL,
  enabled TINYINT NOT NULL DEFAULT 1,
  UNIQUE KEY uk_cps_reminder_node (node_status)
);
```

- [ ] **Step 2: Run migration in local database**

Run the existing project migration command. For Flyway Maven projects:

```bash
mvn -q flyway:migrate
```

Expected: command exits successfully and creates the five `cps_*` tables.

- [ ] **Step 3: Verify indexes exist**

Run:

```sql
SHOW INDEX FROM cps_issue;
SHOW INDEX FROM cps_issue_attachment;
SHOW INDEX FROM cps_issue_flow_log;
```

Expected: handler/status, attachment stage, and flow issue/time indexes are present.

---

## Task 2: Add Backend Domain Types

**Files:**
- Create: `backend/src/main/java/com/company/cps/domain/CpsIssueStatus.java`
- Create: `backend/src/main/java/com/company/cps/domain/CpsIssueAction.java`
- Create: `backend/src/main/java/com/company/cps/domain/CpsAttachmentStage.java`

- [ ] **Step 1: Create status enum**

```java
package com.company.cps.domain;

public enum CpsIssueStatus {
    PENDING_FEEDBACK,
    PENDING_RECTIFY,
    PENDING_UPLOAD_PROOF,
    PENDING_REVIEW,
    CLOSED
}
```

- [ ] **Step 2: Create action enum**

```java
package com.company.cps.domain;

public enum CpsIssueAction {
    SUBMIT,
    REPLY_ASSIGN,
    RECTIFY,
    UPLOAD_PROOF,
    REVIEW_CLOSE,
    REVIEW_REJECT,
    TRANSFER
}
```

- [ ] **Step 3: Create attachment stage enum**

```java
package com.company.cps.domain;

public enum CpsAttachmentStage {
    ISSUE,
    PROOF
}
```

- [ ] **Step 4: Compile**

Run:

```bash
mvn -q -DskipTests compile
```

Expected: compile succeeds.

---

## Task 3: Implement Workflow State Machine

**Files:**
- Create: `backend/src/main/java/com/company/cps/service/CpsWorkflowStateMachine.java`
- Create: `backend/src/test/java/com/company/cps/service/CpsWorkflowStateMachineTest.java`

- [ ] **Step 1: Write failing state machine tests**

```java
package com.company.cps.service;

import com.company.cps.domain.CpsIssueAction;
import com.company.cps.domain.CpsIssueStatus;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CpsWorkflowStateMachineTest {

    private final CpsWorkflowStateMachine stateMachine = new CpsWorkflowStateMachine();

    @Test
    void pendingFeedbackAllowsReplyAssignAndTransfer() {
        Set<CpsIssueAction> actions = stateMachine.availableActions(CpsIssueStatus.PENDING_FEEDBACK);

        assertEquals(Set.of(CpsIssueAction.REPLY_ASSIGN, CpsIssueAction.TRANSFER), actions);
    }

    @Test
    void replyAssignMovesToPendingRectify() {
        CpsIssueStatus next = stateMachine.nextStatus(
                CpsIssueStatus.PENDING_FEEDBACK,
                CpsIssueAction.REPLY_ASSIGN
        );

        assertEquals(CpsIssueStatus.PENDING_RECTIFY, next);
    }

    @Test
    void reviewRejectMovesBackToUploadProof() {
        CpsIssueStatus next = stateMachine.nextStatus(
                CpsIssueStatus.PENDING_REVIEW,
                CpsIssueAction.REVIEW_REJECT
        );

        assertEquals(CpsIssueStatus.PENDING_UPLOAD_PROOF, next);
    }

    @Test
    void closedStatusAllowsNoActions() {
        assertTrue(stateMachine.availableActions(CpsIssueStatus.CLOSED).isEmpty());
    }

    @Test
    void invalidActionThrows() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> stateMachine.nextStatus(CpsIssueStatus.PENDING_FEEDBACK, CpsIssueAction.REVIEW_CLOSE)
        );

        assertTrue(error.getMessage().contains("Action REVIEW_CLOSE is not allowed"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -q -Dtest=CpsWorkflowStateMachineTest test
```

Expected: FAIL because `CpsWorkflowStateMachine` does not exist.

- [ ] **Step 3: Implement state machine**

```java
package com.company.cps.service;

import com.company.cps.domain.CpsIssueAction;
import com.company.cps.domain.CpsIssueStatus;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

@Component
public class CpsWorkflowStateMachine {

    private static final Map<CpsIssueStatus, Map<CpsIssueAction, CpsIssueStatus>> TRANSITIONS =
            new EnumMap<>(CpsIssueStatus.class);

    static {
        put(CpsIssueStatus.PENDING_FEEDBACK, CpsIssueAction.REPLY_ASSIGN, CpsIssueStatus.PENDING_RECTIFY);
        put(CpsIssueStatus.PENDING_FEEDBACK, CpsIssueAction.TRANSFER, CpsIssueStatus.PENDING_FEEDBACK);

        put(CpsIssueStatus.PENDING_RECTIFY, CpsIssueAction.RECTIFY, CpsIssueStatus.PENDING_UPLOAD_PROOF);
        put(CpsIssueStatus.PENDING_RECTIFY, CpsIssueAction.TRANSFER, CpsIssueStatus.PENDING_RECTIFY);

        put(CpsIssueStatus.PENDING_UPLOAD_PROOF, CpsIssueAction.UPLOAD_PROOF, CpsIssueStatus.PENDING_REVIEW);
        put(CpsIssueStatus.PENDING_UPLOAD_PROOF, CpsIssueAction.TRANSFER, CpsIssueStatus.PENDING_UPLOAD_PROOF);

        put(CpsIssueStatus.PENDING_REVIEW, CpsIssueAction.REVIEW_CLOSE, CpsIssueStatus.CLOSED);
        put(CpsIssueStatus.PENDING_REVIEW, CpsIssueAction.REVIEW_REJECT, CpsIssueStatus.PENDING_UPLOAD_PROOF);
        put(CpsIssueStatus.PENDING_REVIEW, CpsIssueAction.TRANSFER, CpsIssueStatus.PENDING_REVIEW);
    }

    private static void put(CpsIssueStatus from, CpsIssueAction action, CpsIssueStatus to) {
        TRANSITIONS.computeIfAbsent(from, ignored -> new EnumMap<>(CpsIssueAction.class)).put(action, to);
    }

    public Set<CpsIssueAction> availableActions(CpsIssueStatus status) {
        return TRANSITIONS.getOrDefault(status, Map.of()).keySet();
    }

    public CpsIssueStatus nextStatus(CpsIssueStatus status, CpsIssueAction action) {
        CpsIssueStatus next = TRANSITIONS.getOrDefault(status, Map.of()).get(action);
        if (next == null) {
            throw new IllegalArgumentException("Action " + action + " is not allowed from status " + status);
        }
        return next;
    }
}
```

- [ ] **Step 4: Run state machine tests**

Run:

```bash
mvn -q -Dtest=CpsWorkflowStateMachineTest test
```

Expected: PASS.

---

## Task 4: Add Backend Entities and DTOs

**Files:**
- Create: `backend/src/main/java/com/company/cps/domain/CpsIssue.java`
- Create: `backend/src/main/java/com/company/cps/domain/CpsIssueAttachment.java`
- Create: `backend/src/main/java/com/company/cps/domain/CpsIssueAiSuggestion.java`
- Create: `backend/src/main/java/com/company/cps/domain/CpsIssueFlowLog.java`
- Create: `backend/src/main/java/com/company/cps/dto/CpsIssueCreateRequest.java`
- Create: `backend/src/main/java/com/company/cps/dto/CpsIssueActionRequest.java`
- Create: `backend/src/main/java/com/company/cps/dto/CpsIssueDetailResponse.java`
- Create: `backend/src/main/java/com/company/cps/dto/CpsIssueListItemResponse.java`

- [ ] **Step 1: Create issue entity**

```java
package com.company.cps.domain;

import java.time.LocalDateTime;

public class CpsIssue {
    private Long id;
    private String issueNo;
    private CpsIssueStatus status;
    private Long factoryId;
    private Long areaId;
    private Long lineId;
    private Long processId;
    private Long aiCategoryL1Id;
    private Long aiCategoryL2Id;
    private Long categoryL1Id;
    private Long categoryL2Id;
    private Boolean categoryModifiedFlag;
    private String description;
    private String creatorEmpNo;
    private String feedbackEmpNo;
    private String responsibleEmpNo;
    private String proofEmpNo;
    private String reviewerEmpNo;
    private String currentHandlerEmpNo;
    private String reasonAnalysis;
    private String correctiveMeasure;
    private String rectifyRemark;
    private String reviewOpinion;
    private LocalDateTime submitTime;
    private LocalDateTime closeTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getIssueNo() { return issueNo; }
    public void setIssueNo(String issueNo) { this.issueNo = issueNo; }
    public CpsIssueStatus getStatus() { return status; }
    public void setStatus(CpsIssueStatus status) { this.status = status; }
    public Long getFactoryId() { return factoryId; }
    public void setFactoryId(Long factoryId) { this.factoryId = factoryId; }
    public Long getAreaId() { return areaId; }
    public void setAreaId(Long areaId) { this.areaId = areaId; }
    public Long getLineId() { return lineId; }
    public void setLineId(Long lineId) { this.lineId = lineId; }
    public Long getProcessId() { return processId; }
    public void setProcessId(Long processId) { this.processId = processId; }
    public Long getAiCategoryL1Id() { return aiCategoryL1Id; }
    public void setAiCategoryL1Id(Long aiCategoryL1Id) { this.aiCategoryL1Id = aiCategoryL1Id; }
    public Long getAiCategoryL2Id() { return aiCategoryL2Id; }
    public void setAiCategoryL2Id(Long aiCategoryL2Id) { this.aiCategoryL2Id = aiCategoryL2Id; }
    public Long getCategoryL1Id() { return categoryL1Id; }
    public void setCategoryL1Id(Long categoryL1Id) { this.categoryL1Id = categoryL1Id; }
    public Long getCategoryL2Id() { return categoryL2Id; }
    public void setCategoryL2Id(Long categoryL2Id) { this.categoryL2Id = categoryL2Id; }
    public Boolean getCategoryModifiedFlag() { return categoryModifiedFlag; }
    public void setCategoryModifiedFlag(Boolean categoryModifiedFlag) { this.categoryModifiedFlag = categoryModifiedFlag; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCreatorEmpNo() { return creatorEmpNo; }
    public void setCreatorEmpNo(String creatorEmpNo) { this.creatorEmpNo = creatorEmpNo; }
    public String getFeedbackEmpNo() { return feedbackEmpNo; }
    public void setFeedbackEmpNo(String feedbackEmpNo) { this.feedbackEmpNo = feedbackEmpNo; }
    public String getResponsibleEmpNo() { return responsibleEmpNo; }
    public void setResponsibleEmpNo(String responsibleEmpNo) { this.responsibleEmpNo = responsibleEmpNo; }
    public String getProofEmpNo() { return proofEmpNo; }
    public void setProofEmpNo(String proofEmpNo) { this.proofEmpNo = proofEmpNo; }
    public String getReviewerEmpNo() { return reviewerEmpNo; }
    public void setReviewerEmpNo(String reviewerEmpNo) { this.reviewerEmpNo = reviewerEmpNo; }
    public String getCurrentHandlerEmpNo() { return currentHandlerEmpNo; }
    public void setCurrentHandlerEmpNo(String currentHandlerEmpNo) { this.currentHandlerEmpNo = currentHandlerEmpNo; }
    public String getReasonAnalysis() { return reasonAnalysis; }
    public void setReasonAnalysis(String reasonAnalysis) { this.reasonAnalysis = reasonAnalysis; }
    public String getCorrectiveMeasure() { return correctiveMeasure; }
    public void setCorrectiveMeasure(String correctiveMeasure) { this.correctiveMeasure = correctiveMeasure; }
    public String getRectifyRemark() { return rectifyRemark; }
    public void setRectifyRemark(String rectifyRemark) { this.rectifyRemark = rectifyRemark; }
    public String getReviewOpinion() { return reviewOpinion; }
    public void setReviewOpinion(String reviewOpinion) { this.reviewOpinion = reviewOpinion; }
    public LocalDateTime getSubmitTime() { return submitTime; }
    public void setSubmitTime(LocalDateTime submitTime) { this.submitTime = submitTime; }
    public LocalDateTime getCloseTime() { return closeTime; }
    public void setCloseTime(LocalDateTime closeTime) { this.closeTime = closeTime; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
```

- [ ] **Step 2: Create attachment entity**

```java
package com.company.cps.domain;

import java.time.LocalDateTime;

public class CpsIssueAttachment {
    private Long id;
    private Long issueId;
    private CpsAttachmentStage stage;
    private String fileUrl;
    private String fileName;
    private String fileType;
    private Integer sortNo;
    private String createdBy;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getIssueId() { return issueId; }
    public void setIssueId(Long issueId) { this.issueId = issueId; }
    public CpsAttachmentStage getStage() { return stage; }
    public void setStage(CpsAttachmentStage stage) { this.stage = stage; }
    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }
    public Integer getSortNo() { return sortNo; }
    public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
```

- [ ] **Step 3: Create AI suggestion entity**

```java
package com.company.cps.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class CpsIssueAiSuggestion {
    private Long id;
    private Long issueId;
    private Long sourceAttachmentId;
    private Long aiCategoryL1Id;
    private String aiCategoryL1Name;
    private Long aiCategoryL2Id;
    private String aiCategoryL2Name;
    private String reasonSuggestion;
    private String measureSuggestion;
    private String modelName;
    private String modelVersion;
    private String rawRequest;
    private String rawResponse;
    private BigDecimal confidence;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getIssueId() { return issueId; }
    public void setIssueId(Long issueId) { this.issueId = issueId; }
    public Long getSourceAttachmentId() { return sourceAttachmentId; }
    public void setSourceAttachmentId(Long sourceAttachmentId) { this.sourceAttachmentId = sourceAttachmentId; }
    public Long getAiCategoryL1Id() { return aiCategoryL1Id; }
    public void setAiCategoryL1Id(Long aiCategoryL1Id) { this.aiCategoryL1Id = aiCategoryL1Id; }
    public String getAiCategoryL1Name() { return aiCategoryL1Name; }
    public void setAiCategoryL1Name(String aiCategoryL1Name) { this.aiCategoryL1Name = aiCategoryL1Name; }
    public Long getAiCategoryL2Id() { return aiCategoryL2Id; }
    public void setAiCategoryL2Id(Long aiCategoryL2Id) { this.aiCategoryL2Id = aiCategoryL2Id; }
    public String getAiCategoryL2Name() { return aiCategoryL2Name; }
    public void setAiCategoryL2Name(String aiCategoryL2Name) { this.aiCategoryL2Name = aiCategoryL2Name; }
    public String getReasonSuggestion() { return reasonSuggestion; }
    public void setReasonSuggestion(String reasonSuggestion) { this.reasonSuggestion = reasonSuggestion; }
    public String getMeasureSuggestion() { return measureSuggestion; }
    public void setMeasureSuggestion(String measureSuggestion) { this.measureSuggestion = measureSuggestion; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }
    public String getRawRequest() { return rawRequest; }
    public void setRawRequest(String rawRequest) { this.rawRequest = rawRequest; }
    public String getRawResponse() { return rawResponse; }
    public void setRawResponse(String rawResponse) { this.rawResponse = rawResponse; }
    public BigDecimal getConfidence() { return confidence; }
    public void setConfidence(BigDecimal confidence) { this.confidence = confidence; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
```

- [ ] **Step 4: Create flow log entity**

```java
package com.company.cps.domain;

import java.time.LocalDateTime;

public class CpsIssueFlowLog {
    private Long id;
    private Long issueId;
    private CpsIssueStatus fromStatus;
    private CpsIssueStatus toStatus;
    private CpsIssueAction action;
    private String operatorEmpNo;
    private String fromHandlerEmpNo;
    private String toHandlerEmpNo;
    private String comment;
    private String snapshotJson;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getIssueId() { return issueId; }
    public void setIssueId(Long issueId) { this.issueId = issueId; }
    public CpsIssueStatus getFromStatus() { return fromStatus; }
    public void setFromStatus(CpsIssueStatus fromStatus) { this.fromStatus = fromStatus; }
    public CpsIssueStatus getToStatus() { return toStatus; }
    public void setToStatus(CpsIssueStatus toStatus) { this.toStatus = toStatus; }
    public CpsIssueAction getAction() { return action; }
    public void setAction(CpsIssueAction action) { this.action = action; }
    public String getOperatorEmpNo() { return operatorEmpNo; }
    public void setOperatorEmpNo(String operatorEmpNo) { this.operatorEmpNo = operatorEmpNo; }
    public String getFromHandlerEmpNo() { return fromHandlerEmpNo; }
    public void setFromHandlerEmpNo(String fromHandlerEmpNo) { this.fromHandlerEmpNo = fromHandlerEmpNo; }
    public String getToHandlerEmpNo() { return toHandlerEmpNo; }
    public void setToHandlerEmpNo(String toHandlerEmpNo) { this.toHandlerEmpNo = toHandlerEmpNo; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public String getSnapshotJson() { return snapshotJson; }
    public void setSnapshotJson(String snapshotJson) { this.snapshotJson = snapshotJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
```

- [ ] **Step 5: Create request DTOs**

```java
package com.company.cps.dto;

import java.util.List;

public class CpsIssueCreateRequest {
    private Long factoryId;
    private Long areaId;
    private Long lineId;
    private Long processId;
    private Long aiCategoryL1Id;
    private Long aiCategoryL2Id;
    private Long categoryL1Id;
    private Long categoryL2Id;
    private String description;
    private String feedbackEmpNo;
    private List<Long> issueAttachmentIds;
    private CpsAiSuggestionPayload aiSuggestion;

    public static class CpsAiSuggestionPayload {
        private Long sourceAttachmentId;
        private Long aiCategoryL1Id;
        private String aiCategoryL1Name;
        private Long aiCategoryL2Id;
        private String aiCategoryL2Name;
        private String reasonSuggestion;
        private String measureSuggestion;
        private String modelName;
        private String modelVersion;
        private String rawRequest;
        private String rawResponse;
        private String confidence;
        public Long getSourceAttachmentId() { return sourceAttachmentId; }
        public void setSourceAttachmentId(Long sourceAttachmentId) { this.sourceAttachmentId = sourceAttachmentId; }
        public Long getAiCategoryL1Id() { return aiCategoryL1Id; }
        public void setAiCategoryL1Id(Long aiCategoryL1Id) { this.aiCategoryL1Id = aiCategoryL1Id; }
        public String getAiCategoryL1Name() { return aiCategoryL1Name; }
        public void setAiCategoryL1Name(String aiCategoryL1Name) { this.aiCategoryL1Name = aiCategoryL1Name; }
        public Long getAiCategoryL2Id() { return aiCategoryL2Id; }
        public void setAiCategoryL2Id(Long aiCategoryL2Id) { this.aiCategoryL2Id = aiCategoryL2Id; }
        public String getAiCategoryL2Name() { return aiCategoryL2Name; }
        public void setAiCategoryL2Name(String aiCategoryL2Name) { this.aiCategoryL2Name = aiCategoryL2Name; }
        public String getReasonSuggestion() { return reasonSuggestion; }
        public void setReasonSuggestion(String reasonSuggestion) { this.reasonSuggestion = reasonSuggestion; }
        public String getMeasureSuggestion() { return measureSuggestion; }
        public void setMeasureSuggestion(String measureSuggestion) { this.measureSuggestion = measureSuggestion; }
        public String getModelName() { return modelName; }
        public void setModelName(String modelName) { this.modelName = modelName; }
        public String getModelVersion() { return modelVersion; }
        public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }
        public String getRawRequest() { return rawRequest; }
        public void setRawRequest(String rawRequest) { this.rawRequest = rawRequest; }
        public String getRawResponse() { return rawResponse; }
        public void setRawResponse(String rawResponse) { this.rawResponse = rawResponse; }
        public String getConfidence() { return confidence; }
        public void setConfidence(String confidence) { this.confidence = confidence; }
    }

    public Long getFactoryId() { return factoryId; }
    public void setFactoryId(Long factoryId) { this.factoryId = factoryId; }
    public Long getAreaId() { return areaId; }
    public void setAreaId(Long areaId) { this.areaId = areaId; }
    public Long getLineId() { return lineId; }
    public void setLineId(Long lineId) { this.lineId = lineId; }
    public Long getProcessId() { return processId; }
    public void setProcessId(Long processId) { this.processId = processId; }
    public Long getAiCategoryL1Id() { return aiCategoryL1Id; }
    public void setAiCategoryL1Id(Long aiCategoryL1Id) { this.aiCategoryL1Id = aiCategoryL1Id; }
    public Long getAiCategoryL2Id() { return aiCategoryL2Id; }
    public void setAiCategoryL2Id(Long aiCategoryL2Id) { this.aiCategoryL2Id = aiCategoryL2Id; }
    public Long getCategoryL1Id() { return categoryL1Id; }
    public void setCategoryL1Id(Long categoryL1Id) { this.categoryL1Id = categoryL1Id; }
    public Long getCategoryL2Id() { return categoryL2Id; }
    public void setCategoryL2Id(Long categoryL2Id) { this.categoryL2Id = categoryL2Id; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getFeedbackEmpNo() { return feedbackEmpNo; }
    public void setFeedbackEmpNo(String feedbackEmpNo) { this.feedbackEmpNo = feedbackEmpNo; }
    public List<Long> getIssueAttachmentIds() { return issueAttachmentIds; }
    public void setIssueAttachmentIds(List<Long> issueAttachmentIds) { this.issueAttachmentIds = issueAttachmentIds; }
    public CpsAiSuggestionPayload getAiSuggestion() { return aiSuggestion; }
    public void setAiSuggestion(CpsAiSuggestionPayload aiSuggestion) { this.aiSuggestion = aiSuggestion; }
}
```

```java
package com.company.cps.dto;

import com.company.cps.domain.CpsIssueAction;
import java.util.List;

public class CpsIssueActionRequest {
    private CpsIssueAction action;
    private String reasonAnalysis;
    private String correctiveMeasure;
    private String responsibleEmpNo;
    private String rectifyRemark;
    private String proofEmpNo;
    private String reviewerEmpNo;
    private List<Long> proofAttachmentIds;
    private String reviewOpinion;
    private String targetEmpNo;
    private String comment;

    public CpsIssueAction getAction() { return action; }
    public void setAction(CpsIssueAction action) { this.action = action; }
    public String getReasonAnalysis() { return reasonAnalysis; }
    public void setReasonAnalysis(String reasonAnalysis) { this.reasonAnalysis = reasonAnalysis; }
    public String getCorrectiveMeasure() { return correctiveMeasure; }
    public void setCorrectiveMeasure(String correctiveMeasure) { this.correctiveMeasure = correctiveMeasure; }
    public String getResponsibleEmpNo() { return responsibleEmpNo; }
    public void setResponsibleEmpNo(String responsibleEmpNo) { this.responsibleEmpNo = responsibleEmpNo; }
    public String getRectifyRemark() { return rectifyRemark; }
    public void setRectifyRemark(String rectifyRemark) { this.rectifyRemark = rectifyRemark; }
    public String getProofEmpNo() { return proofEmpNo; }
    public void setProofEmpNo(String proofEmpNo) { this.proofEmpNo = proofEmpNo; }
    public String getReviewerEmpNo() { return reviewerEmpNo; }
    public void setReviewerEmpNo(String reviewerEmpNo) { this.reviewerEmpNo = reviewerEmpNo; }
    public List<Long> getProofAttachmentIds() { return proofAttachmentIds; }
    public void setProofAttachmentIds(List<Long> proofAttachmentIds) { this.proofAttachmentIds = proofAttachmentIds; }
    public String getReviewOpinion() { return reviewOpinion; }
    public void setReviewOpinion(String reviewOpinion) { this.reviewOpinion = reviewOpinion; }
    public String getTargetEmpNo() { return targetEmpNo; }
    public void setTargetEmpNo(String targetEmpNo) { this.targetEmpNo = targetEmpNo; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}
```

- [ ] **Step 6: Create response DTOs**

```java
package com.company.cps.dto;

import com.company.cps.domain.CpsIssueAction;
import com.company.cps.domain.CpsIssueStatus;
import java.time.LocalDateTime;
import java.util.List;

public class CpsIssueDetailResponse {
    private Long id;
    private String issueNo;
    private CpsIssueStatus status;
    private String currentHandlerEmpNo;
    private List<CpsIssueAction> availableActions;
    private String description;
    private Long factoryId;
    private Long areaId;
    private Long lineId;
    private Long processId;
    private Long aiCategoryL1Id;
    private Long aiCategoryL2Id;
    private Long categoryL1Id;
    private Long categoryL2Id;
    private String reasonAnalysis;
    private String correctiveMeasure;
    private String rectifyRemark;
    private String reviewOpinion;
    private LocalDateTime submitTime;
    private List<AttachmentView> issueAttachments;
    private List<AttachmentView> proofAttachments;
    private List<FlowLogView> flowLogs;

    public static class AttachmentView {
        private Long id;
        private String fileUrl;
        private String fileName;
        private Integer sortNo;
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getFileUrl() { return fileUrl; }
        public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        public Integer getSortNo() { return sortNo; }
        public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
    }

    public static class FlowLogView {
        private String action;
        private String operatorEmpNo;
        private String fromStatus;
        private String toStatus;
        private String comment;
        private LocalDateTime createdAt;
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
        public String getOperatorEmpNo() { return operatorEmpNo; }
        public void setOperatorEmpNo(String operatorEmpNo) { this.operatorEmpNo = operatorEmpNo; }
        public String getFromStatus() { return fromStatus; }
        public void setFromStatus(String fromStatus) { this.fromStatus = fromStatus; }
        public String getToStatus() { return toStatus; }
        public void setToStatus(String toStatus) { this.toStatus = toStatus; }
        public String getComment() { return comment; }
        public void setComment(String comment) { this.comment = comment; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getIssueNo() { return issueNo; }
    public void setIssueNo(String issueNo) { this.issueNo = issueNo; }
    public CpsIssueStatus getStatus() { return status; }
    public void setStatus(CpsIssueStatus status) { this.status = status; }
    public String getCurrentHandlerEmpNo() { return currentHandlerEmpNo; }
    public void setCurrentHandlerEmpNo(String currentHandlerEmpNo) { this.currentHandlerEmpNo = currentHandlerEmpNo; }
    public List<CpsIssueAction> getAvailableActions() { return availableActions; }
    public void setAvailableActions(List<CpsIssueAction> availableActions) { this.availableActions = availableActions; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getFactoryId() { return factoryId; }
    public void setFactoryId(Long factoryId) { this.factoryId = factoryId; }
    public Long getAreaId() { return areaId; }
    public void setAreaId(Long areaId) { this.areaId = areaId; }
    public Long getLineId() { return lineId; }
    public void setLineId(Long lineId) { this.lineId = lineId; }
    public Long getProcessId() { return processId; }
    public void setProcessId(Long processId) { this.processId = processId; }
    public Long getAiCategoryL1Id() { return aiCategoryL1Id; }
    public void setAiCategoryL1Id(Long aiCategoryL1Id) { this.aiCategoryL1Id = aiCategoryL1Id; }
    public Long getAiCategoryL2Id() { return aiCategoryL2Id; }
    public void setAiCategoryL2Id(Long aiCategoryL2Id) { this.aiCategoryL2Id = aiCategoryL2Id; }
    public Long getCategoryL1Id() { return categoryL1Id; }
    public void setCategoryL1Id(Long categoryL1Id) { this.categoryL1Id = categoryL1Id; }
    public Long getCategoryL2Id() { return categoryL2Id; }
    public void setCategoryL2Id(Long categoryL2Id) { this.categoryL2Id = categoryL2Id; }
    public String getReasonAnalysis() { return reasonAnalysis; }
    public void setReasonAnalysis(String reasonAnalysis) { this.reasonAnalysis = reasonAnalysis; }
    public String getCorrectiveMeasure() { return correctiveMeasure; }
    public void setCorrectiveMeasure(String correctiveMeasure) { this.correctiveMeasure = correctiveMeasure; }
    public String getRectifyRemark() { return rectifyRemark; }
    public void setRectifyRemark(String rectifyRemark) { this.rectifyRemark = rectifyRemark; }
    public String getReviewOpinion() { return reviewOpinion; }
    public void setReviewOpinion(String reviewOpinion) { this.reviewOpinion = reviewOpinion; }
    public LocalDateTime getSubmitTime() { return submitTime; }
    public void setSubmitTime(LocalDateTime submitTime) { this.submitTime = submitTime; }
    public List<AttachmentView> getIssueAttachments() { return issueAttachments; }
    public void setIssueAttachments(List<AttachmentView> issueAttachments) { this.issueAttachments = issueAttachments; }
    public List<AttachmentView> getProofAttachments() { return proofAttachments; }
    public void setProofAttachments(List<AttachmentView> proofAttachments) { this.proofAttachments = proofAttachments; }
    public List<FlowLogView> getFlowLogs() { return flowLogs; }
    public void setFlowLogs(List<FlowLogView> flowLogs) { this.flowLogs = flowLogs; }
}
```

```java
package com.company.cps.dto;

import com.company.cps.domain.CpsIssueStatus;
import java.time.LocalDateTime;

public class CpsIssueListItemResponse {
    private Long id;
    private String issueNo;
    private CpsIssueStatus status;
    private Long factoryId;
    private Long areaId;
    private Long lineId;
    private Long processId;
    private Long categoryL1Id;
    private Long categoryL2Id;
    private String description;
    private String currentHandlerEmpNo;
    private LocalDateTime submitTime;
    private Boolean overdue;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getIssueNo() { return issueNo; }
    public void setIssueNo(String issueNo) { this.issueNo = issueNo; }
    public CpsIssueStatus getStatus() { return status; }
    public void setStatus(CpsIssueStatus status) { this.status = status; }
    public Long getFactoryId() { return factoryId; }
    public void setFactoryId(Long factoryId) { this.factoryId = factoryId; }
    public Long getAreaId() { return areaId; }
    public void setAreaId(Long areaId) { this.areaId = areaId; }
    public Long getLineId() { return lineId; }
    public void setLineId(Long lineId) { this.lineId = lineId; }
    public Long getProcessId() { return processId; }
    public void setProcessId(Long processId) { this.processId = processId; }
    public Long getCategoryL1Id() { return categoryL1Id; }
    public void setCategoryL1Id(Long categoryL1Id) { this.categoryL1Id = categoryL1Id; }
    public Long getCategoryL2Id() { return categoryL2Id; }
    public void setCategoryL2Id(Long categoryL2Id) { this.categoryL2Id = categoryL2Id; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCurrentHandlerEmpNo() { return currentHandlerEmpNo; }
    public void setCurrentHandlerEmpNo(String currentHandlerEmpNo) { this.currentHandlerEmpNo = currentHandlerEmpNo; }
    public LocalDateTime getSubmitTime() { return submitTime; }
    public void setSubmitTime(LocalDateTime submitTime) { this.submitTime = submitTime; }
    public Boolean getOverdue() { return overdue; }
    public void setOverdue(Boolean overdue) { this.overdue = overdue; }
}
```

- [ ] **Step 7: Compile**

Run:

```bash
mvn -q -DskipTests compile
```

Expected: compile succeeds.

---

## Task 5: Add MyBatis Mappers

**Files:**
- Create: `backend/src/main/java/com/company/cps/mapper/CpsIssueMapper.java`
- Create: `backend/src/main/java/com/company/cps/mapper/CpsIssueAttachmentMapper.java`
- Create: `backend/src/main/java/com/company/cps/mapper/CpsIssueAiSuggestionMapper.java`
- Create: `backend/src/main/java/com/company/cps/mapper/CpsIssueFlowLogMapper.java`

- [ ] **Step 1: Create issue mapper**

```java
package com.company.cps.mapper;

import com.company.cps.domain.CpsIssue;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface CpsIssueMapper {

    @Insert("""
        INSERT INTO cps_issue (
          issue_no, status, factory_id, area_id, line_id, process_id,
          ai_category_l1_id, ai_category_l2_id, category_l1_id, category_l2_id,
          category_modified_flag, description, creator_emp_no, feedback_emp_no,
          current_handler_emp_no, submit_time, created_at, updated_at
        ) VALUES (
          #{issueNo}, #{status}, #{factoryId}, #{areaId}, #{lineId}, #{processId},
          #{aiCategoryL1Id}, #{aiCategoryL2Id}, #{categoryL1Id}, #{categoryL2Id},
          #{categoryModifiedFlag}, #{description}, #{creatorEmpNo}, #{feedbackEmpNo},
          #{currentHandlerEmpNo}, #{submitTime}, #{createdAt}, #{updatedAt}
        )
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(CpsIssue issue);

    @Select("SELECT * FROM cps_issue WHERE id = #{id}")
    CpsIssue findById(Long id);

    @Update("""
        UPDATE cps_issue
        SET status = #{status},
            responsible_emp_no = #{responsibleEmpNo},
            proof_emp_no = #{proofEmpNo},
            reviewer_emp_no = #{reviewerEmpNo},
            current_handler_emp_no = #{currentHandlerEmpNo},
            reason_analysis = #{reasonAnalysis},
            corrective_measure = #{correctiveMeasure},
            rectify_remark = #{rectifyRemark},
            review_opinion = #{reviewOpinion},
            close_time = #{closeTime},
            updated_at = #{updatedAt}
        WHERE id = #{id}
        """)
    int updateWorkflowFields(CpsIssue issue);

    @Select("""
        SELECT * FROM cps_issue
        WHERE current_handler_emp_no = #{empNo}
          AND status <> 'CLOSED'
        ORDER BY submit_time DESC
        LIMIT #{limit} OFFSET #{offset}
        """)
    List<CpsIssue> findTodo(String empNo, int limit, int offset);

    @Select("""
        SELECT * FROM cps_issue
        WHERE creator_emp_no = #{empNo}
        ORDER BY submit_time DESC
        LIMIT #{limit} OFFSET #{offset}
        """)
    List<CpsIssue> findCreatedBy(String empNo, int limit, int offset);

    @Select("""
        SELECT DISTINCT i.*
        FROM cps_issue i
        LEFT JOIN cps_issue_flow_log l ON l.issue_id = i.id
        WHERE i.creator_emp_no = #{empNo}
           OR i.current_handler_emp_no = #{empNo}
           OR i.feedback_emp_no = #{empNo}
           OR i.responsible_emp_no = #{empNo}
           OR i.proof_emp_no = #{empNo}
           OR i.reviewer_emp_no = #{empNo}
           OR l.operator_emp_no = #{empNo}
        ORDER BY i.submit_time DESC
        LIMIT #{limit} OFFSET #{offset}
        """)
    List<CpsIssue> findRelated(String empNo, int limit, int offset);
}
```

- [ ] **Step 2: Create attachment mapper**

```java
package com.company.cps.mapper;

import com.company.cps.domain.CpsAttachmentStage;
import com.company.cps.domain.CpsIssueAttachment;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface CpsIssueAttachmentMapper {

    @Insert("""
        INSERT INTO cps_issue_attachment (
          issue_id, stage, file_url, file_name, file_type, sort_no, created_by, created_at
        ) VALUES (
          #{issueId}, #{stage}, #{fileUrl}, #{fileName}, #{fileType}, #{sortNo}, #{createdBy}, #{createdAt}
        )
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(CpsIssueAttachment attachment);

    @Select("""
        SELECT * FROM cps_issue_attachment
        WHERE issue_id = #{issueId}
          AND stage = #{stage}
        ORDER BY sort_no ASC
        """)
    List<CpsIssueAttachment> findByIssueAndStage(Long issueId, CpsAttachmentStage stage);

    @Select("""
        SELECT COUNT(*)
        FROM cps_issue_attachment
        WHERE issue_id = #{issueId}
          AND stage = #{stage}
        """)
    int countByIssueAndStage(Long issueId, CpsAttachmentStage stage);

    @Update("""
        UPDATE cps_issue_attachment
        SET issue_id = #{issueId},
            stage = #{stage},
            sort_no = #{sortNo}
        WHERE id = #{id}
        """)
    int bindToIssue(Long id, Long issueId, CpsAttachmentStage stage, Integer sortNo);
}
```

- [ ] **Step 3: Create AI suggestion mapper**

```java
package com.company.cps.mapper;

import com.company.cps.domain.CpsIssueAiSuggestion;
import org.apache.ibatis.annotations.*;

@Mapper
public interface CpsIssueAiSuggestionMapper {

    @Insert("""
        INSERT INTO cps_issue_ai_suggestion (
          issue_id, source_attachment_id, ai_category_l1_id, ai_category_l1_name,
          ai_category_l2_id, ai_category_l2_name, reason_suggestion, measure_suggestion,
          model_name, model_version, raw_request, raw_response, confidence, created_at
        ) VALUES (
          #{issueId}, #{sourceAttachmentId}, #{aiCategoryL1Id}, #{aiCategoryL1Name},
          #{aiCategoryL2Id}, #{aiCategoryL2Name}, #{reasonSuggestion}, #{measureSuggestion},
          #{modelName}, #{modelVersion}, #{rawRequest}, #{rawResponse}, #{confidence}, #{createdAt}
        )
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(CpsIssueAiSuggestion suggestion);

    @Select("""
        SELECT * FROM cps_issue_ai_suggestion
        WHERE issue_id = #{issueId}
        ORDER BY created_at DESC
        LIMIT 1
        """)
    CpsIssueAiSuggestion findLatestByIssueId(Long issueId);
}
```

- [ ] **Step 4: Create flow log mapper**

```java
package com.company.cps.mapper;

import com.company.cps.domain.CpsIssueFlowLog;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface CpsIssueFlowLogMapper {

    @Insert("""
        INSERT INTO cps_issue_flow_log (
          issue_id, from_status, to_status, action, operator_emp_no,
          from_handler_emp_no, to_handler_emp_no, comment, snapshot_json, created_at
        ) VALUES (
          #{issueId}, #{fromStatus}, #{toStatus}, #{action}, #{operatorEmpNo},
          #{fromHandlerEmpNo}, #{toHandlerEmpNo}, #{comment}, #{snapshotJson}, #{createdAt}
        )
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(CpsIssueFlowLog flowLog);

    @Select("""
        SELECT * FROM cps_issue_flow_log
        WHERE issue_id = #{issueId}
        ORDER BY created_at ASC
        """)
    List<CpsIssueFlowLog> findByIssueId(Long issueId);
}
```

- [ ] **Step 5: Compile**

Run:

```bash
mvn -q -DskipTests compile
```

Expected: compile succeeds. If the existing project does not enable MyBatis enum handling, add the project-standard enum type handler and rerun compile.

---

## Task 6: Implement Backend Issue Service

**Files:**
- Create: `backend/src/main/java/com/company/cps/service/CpsIssueService.java`
- Create: `backend/src/main/java/com/company/cps/service/CpsAssignmentService.java`
- Create: `backend/src/test/java/com/company/cps/service/CpsIssueServiceTest.java`

- [ ] **Step 1: Write service tests for validation and workflow**

```java
package com.company.cps.service;

import com.company.cps.domain.CpsIssueAction;
import com.company.cps.dto.CpsIssueActionRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CpsIssueServiceTest {

    @Test
    void validatesIssueImageLimit() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> CpsIssueService.validateAttachmentCount("ISSUE", List.of(1L, 2L, 3L, 4L, 5L, 6L))
        );

        assertEquals("ISSUE attachments must contain 1 to 5 files", error.getMessage());
    }

    @Test
    void validatesReplyAssignRequiredFields() {
        CpsIssueActionRequest request = new CpsIssueActionRequest();
        request.setAction(CpsIssueAction.REPLY_ASSIGN);
        request.setReasonAnalysis("原因");
        request.setCorrectiveMeasure("");
        request.setResponsibleEmpNo("E10023");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> CpsIssueService.validateActionPayload(request)
        );

        assertEquals("correctiveMeasure is required for REPLY_ASSIGN", error.getMessage());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
mvn -q -Dtest=CpsIssueServiceTest test
```

Expected: FAIL because `CpsIssueService` does not exist.

- [ ] **Step 3: Implement assignment service skeleton**

```java
package com.company.cps.service;

import org.springframework.stereotype.Service;

@Service
public class CpsAssignmentService {

    public String findFeedbackHandler(Long factoryId, Long areaId, Long lineId, Long processId) {
        // Replace this query with the existing project's personnel assignment mapper.
        // Required matching order: factory+area+line+process, then factory+area+line, then factory+area, then factory.
        return "";
    }

    public String findReviewer(Long factoryId, Long areaId) {
        // Replace this query with the existing project's reviewer assignment mapper.
        return "";
    }
}
```

- [ ] **Step 4: Implement issue service validation and workflow core**

```java
package com.company.cps.service;

import com.company.cps.domain.*;
import com.company.cps.dto.CpsIssueActionRequest;
import com.company.cps.dto.CpsIssueCreateRequest;
import com.company.cps.mapper.CpsIssueAiSuggestionMapper;
import com.company.cps.mapper.CpsIssueAttachmentMapper;
import com.company.cps.mapper.CpsIssueFlowLogMapper;
import com.company.cps.mapper.CpsIssueMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
public class CpsIssueService {

    private final CpsIssueMapper issueMapper;
    private final CpsIssueAttachmentMapper attachmentMapper;
    private final CpsIssueAiSuggestionMapper aiSuggestionMapper;
    private final CpsIssueFlowLogMapper flowLogMapper;
    private final CpsWorkflowStateMachine stateMachine;
    private final CpsAssignmentService assignmentService;

    public CpsIssueService(
            CpsIssueMapper issueMapper,
            CpsIssueAttachmentMapper attachmentMapper,
            CpsIssueAiSuggestionMapper aiSuggestionMapper,
            CpsIssueFlowLogMapper flowLogMapper,
            CpsWorkflowStateMachine stateMachine,
            CpsAssignmentService assignmentService
    ) {
        this.issueMapper = issueMapper;
        this.attachmentMapper = attachmentMapper;
        this.aiSuggestionMapper = aiSuggestionMapper;
        this.flowLogMapper = flowLogMapper;
        this.stateMachine = stateMachine;
        this.assignmentService = assignmentService;
    }

    public static void validateAttachmentCount(String label, List<Long> attachmentIds) {
        if (attachmentIds == null || attachmentIds.isEmpty() || attachmentIds.size() > 5) {
            throw new IllegalArgumentException(label + " attachments must contain 1 to 5 files");
        }
    }

    public static void validateActionPayload(CpsIssueActionRequest request) {
        if (request.getAction() == CpsIssueAction.REPLY_ASSIGN) {
            requireText(request.getReasonAnalysis(), "reasonAnalysis is required for REPLY_ASSIGN");
            requireText(request.getCorrectiveMeasure(), "correctiveMeasure is required for REPLY_ASSIGN");
            requireText(request.getResponsibleEmpNo(), "responsibleEmpNo is required for REPLY_ASSIGN");
        }
        if (request.getAction() == CpsIssueAction.RECTIFY) {
            requireText(request.getProofEmpNo(), "proofEmpNo is required for RECTIFY");
        }
        if (request.getAction() == CpsIssueAction.UPLOAD_PROOF) {
            validateAttachmentCount("PROOF", request.getProofAttachmentIds());
        }
        if (request.getAction() == CpsIssueAction.TRANSFER) {
            requireText(request.getTargetEmpNo(), "targetEmpNo is required for TRANSFER");
        }
    }

    private static void requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
    }

    @Transactional
    public Long createIssue(CpsIssueCreateRequest request, String currentEmpNo) {
        validateCreateRequest(request);
        LocalDateTime now = LocalDateTime.now();

        CpsIssue issue = new CpsIssue();
        issue.setIssueNo(generateIssueNo(now));
        issue.setStatus(CpsIssueStatus.PENDING_FEEDBACK);
        issue.setFactoryId(request.getFactoryId());
        issue.setAreaId(request.getAreaId());
        issue.setLineId(request.getLineId());
        issue.setProcessId(request.getProcessId());
        issue.setAiCategoryL1Id(request.getAiCategoryL1Id());
        issue.setAiCategoryL2Id(request.getAiCategoryL2Id());
        issue.setCategoryL1Id(request.getCategoryL1Id());
        issue.setCategoryL2Id(request.getCategoryL2Id());
        issue.setCategoryModifiedFlag(!same(request.getAiCategoryL1Id(), request.getCategoryL1Id()) || !same(request.getAiCategoryL2Id(), request.getCategoryL2Id()));
        issue.setDescription(request.getDescription());
        issue.setCreatorEmpNo(currentEmpNo);
        issue.setFeedbackEmpNo(request.getFeedbackEmpNo());
        issue.setCurrentHandlerEmpNo(request.getFeedbackEmpNo());
        issue.setSubmitTime(now);
        issue.setCreatedAt(now);
        issue.setUpdatedAt(now);
        issueMapper.insert(issue);

        if (request.getAiSuggestion() != null) {
            saveAiSuggestion(issue.getId(), request.getAiSuggestion(), now);
        }

        writeFlowLog(issue.getId(), null, CpsIssueStatus.PENDING_FEEDBACK, CpsIssueAction.SUBMIT, currentEmpNo, null, request.getFeedbackEmpNo(), "提交问题", "{}");
        return issue.getId();
    }

    @Transactional
    public void executeAction(Long issueId, CpsIssueActionRequest request, String currentEmpNo) {
        validateActionPayload(request);
        CpsIssue issue = issueMapper.findById(issueId);
        if (issue == null) {
            throw new IllegalArgumentException("Issue not found: " + issueId);
        }
        if (issue.getStatus() == CpsIssueStatus.CLOSED) {
            throw new IllegalStateException("Closed issue cannot be changed");
        }
        if (!currentEmpNo.equals(issue.getCurrentHandlerEmpNo())) {
            throw new IllegalStateException("Current user is not the issue handler");
        }

        CpsIssueStatus fromStatus = issue.getStatus();
        CpsIssueStatus toStatus = stateMachine.nextStatus(fromStatus, request.getAction());
        String fromHandler = issue.getCurrentHandlerEmpNo();
        String toHandler = resolveNextHandler(issue, request, toStatus);

        applyActionFields(issue, request, toStatus, toHandler);
        issueMapper.updateWorkflowFields(issue);

        writeFlowLog(issueId, fromStatus, toStatus, request.getAction(), currentEmpNo, fromHandler, toHandler, request.getComment(), "{}");
    }

    private void validateCreateRequest(CpsIssueCreateRequest request) {
        if (request.getFactoryId() == null) throw new IllegalArgumentException("factoryId is required");
        if (request.getAreaId() == null) throw new IllegalArgumentException("areaId is required");
        if (request.getLineId() == null) throw new IllegalArgumentException("lineId is required");
        if (request.getProcessId() == null) throw new IllegalArgumentException("processId is required");
        if (request.getCategoryL1Id() == null) throw new IllegalArgumentException("categoryL1Id is required");
        if (request.getCategoryL2Id() == null) throw new IllegalArgumentException("categoryL2Id is required");
        requireText(request.getDescription(), "description is required");
        requireText(request.getFeedbackEmpNo(), "feedbackEmpNo is required");
        validateAttachmentCount("ISSUE", request.getIssueAttachmentIds());
    }

    private String resolveNextHandler(CpsIssue issue, CpsIssueActionRequest request, CpsIssueStatus toStatus) {
        if (request.getAction() == CpsIssueAction.TRANSFER) return request.getTargetEmpNo();
        if (toStatus == CpsIssueStatus.PENDING_RECTIFY) return request.getResponsibleEmpNo();
        if (toStatus == CpsIssueStatus.PENDING_UPLOAD_PROOF) return request.getProofEmpNo() == null ? issue.getProofEmpNo() : request.getProofEmpNo();
        if (toStatus == CpsIssueStatus.PENDING_REVIEW) return request.getReviewerEmpNo() == null
                ? assignmentService.findReviewer(issue.getFactoryId(), issue.getAreaId())
                : request.getReviewerEmpNo();
        if (toStatus == CpsIssueStatus.CLOSED) return null;
        return issue.getCurrentHandlerEmpNo();
    }

    private void applyActionFields(CpsIssue issue, CpsIssueActionRequest request, CpsIssueStatus toStatus, String toHandler) {
        issue.setStatus(toStatus);
        issue.setCurrentHandlerEmpNo(toHandler);
        issue.setUpdatedAt(LocalDateTime.now());
        if (request.getAction() == CpsIssueAction.REPLY_ASSIGN) {
            issue.setReasonAnalysis(request.getReasonAnalysis());
            issue.setCorrectiveMeasure(request.getCorrectiveMeasure());
            issue.setResponsibleEmpNo(request.getResponsibleEmpNo());
        }
        if (request.getAction() == CpsIssueAction.RECTIFY) {
            issue.setRectifyRemark(request.getRectifyRemark());
            issue.setProofEmpNo(request.getProofEmpNo());
        }
        if (request.getAction() == CpsIssueAction.UPLOAD_PROOF) {
            issue.setReviewerEmpNo(toHandler);
        }
        if (request.getAction() == CpsIssueAction.REVIEW_CLOSE) {
            issue.setReviewOpinion(request.getReviewOpinion());
            issue.setCloseTime(LocalDateTime.now());
        }
        if (request.getAction() == CpsIssueAction.REVIEW_REJECT) {
            issue.setReviewOpinion(request.getReviewOpinion());
        }
    }

    private void saveAiSuggestion(Long issueId, CpsIssueCreateRequest.CpsAiSuggestionPayload payload, LocalDateTime now) {
        CpsIssueAiSuggestion suggestion = new CpsIssueAiSuggestion();
        suggestion.setIssueId(issueId);
        suggestion.setSourceAttachmentId(payload.getSourceAttachmentId());
        suggestion.setAiCategoryL1Id(payload.getAiCategoryL1Id());
        suggestion.setAiCategoryL1Name(payload.getAiCategoryL1Name());
        suggestion.setAiCategoryL2Id(payload.getAiCategoryL2Id());
        suggestion.setAiCategoryL2Name(payload.getAiCategoryL2Name());
        suggestion.setReasonSuggestion(payload.getReasonSuggestion());
        suggestion.setMeasureSuggestion(payload.getMeasureSuggestion());
        suggestion.setModelName(payload.getModelName());
        suggestion.setModelVersion(payload.getModelVersion());
        suggestion.setRawRequest(payload.getRawRequest());
        suggestion.setRawResponse(payload.getRawResponse());
        suggestion.setConfidence(payload.getConfidence() == null ? null : new BigDecimal(payload.getConfidence()));
        suggestion.setCreatedAt(now);
        aiSuggestionMapper.insert(suggestion);
    }

    private void writeFlowLog(Long issueId, CpsIssueStatus fromStatus, CpsIssueStatus toStatus, CpsIssueAction action, String operator, String fromHandler, String toHandler, String comment, String snapshotJson) {
        CpsIssueFlowLog log = new CpsIssueFlowLog();
        log.setIssueId(issueId);
        log.setFromStatus(fromStatus);
        log.setToStatus(toStatus);
        log.setAction(action);
        log.setOperatorEmpNo(operator);
        log.setFromHandlerEmpNo(fromHandler);
        log.setToHandlerEmpNo(toHandler);
        log.setComment(comment);
        log.setSnapshotJson(snapshotJson);
        log.setCreatedAt(LocalDateTime.now());
        flowLogMapper.insert(log);
    }

    private static String generateIssueNo(LocalDateTime now) {
        return "CPS" + now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    private static boolean same(Long left, Long right) {
        return left == null ? right == null : left.equals(right);
    }
}
```

- [ ] **Step 5: Run service tests**

Run:

```bash
mvn -q -Dtest=CpsIssueServiceTest test
```

Expected: PASS.

---

## Task 7: Add Backend Controller

**Files:**
- Create: `backend/src/main/java/com/company/cps/controller/CpsIssueController.java`

- [ ] **Step 1: Add controller**

```java
package com.company.cps.controller;

import com.company.cps.dto.CpsIssueActionRequest;
import com.company.cps.dto.CpsIssueCreateRequest;
import com.company.cps.service.CpsIssueService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/cps/issues")
public class CpsIssueController {

    private final CpsIssueService issueService;

    public CpsIssueController(CpsIssueService issueService) {
        this.issueService = issueService;
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody CpsIssueCreateRequest request) {
        String empNo = currentEmpNo();
        Long issueId = issueService.createIssue(request, empNo);
        return Map.of("issueId", issueId);
    }

    @PostMapping("/{id}/actions")
    public Map<String, Object> action(@PathVariable Long id, @RequestBody CpsIssueActionRequest request) {
        String empNo = currentEmpNo();
        issueService.executeAction(id, request, empNo);
        return Map.of("success", true);
    }

    private String currentEmpNo() {
        return CurrentUserHolder.empNo();
    }

    static class CurrentUserHolder {
        static String empNo() {
            throw new UnsupportedOperationException("Replace with existing project current-user empNo lookup");
        }
    }
}
```

- [ ] **Step 2: Replace current-user lookup**

Replace the body of `CurrentUserHolder.empNo()` with the existing project's current-user access. The method must return the authenticated employee number as a non-empty string.

Example shape:

```java
static String empNo() {
    String empNo = SecurityContext.getCurrentUser().getEmpNo();
    if (empNo == null || empNo.isBlank()) {
        throw new IllegalStateException("Current empNo is missing");
    }
    return empNo;
}
```

- [ ] **Step 3: Compile**

Run:

```bash
mvn -q -DskipTests compile
```

Expected: compile succeeds after integrating the project-specific current-user lookup.

---

## Task 8: Add Mobile Types and API Client

**Files:**
- Create: `mobile/src/types/cps.ts`
- Create: `mobile/src/api/cps/issue.ts`
- Create: `mobile/src/api/cps/master.ts`

- [ ] **Step 1: Add CPS TypeScript types**

```ts
export type CpsIssueStatus =
  | 'PENDING_FEEDBACK'
  | 'PENDING_RECTIFY'
  | 'PENDING_UPLOAD_PROOF'
  | 'PENDING_REVIEW'
  | 'CLOSED'

export type CpsIssueAction =
  | 'REPLY_ASSIGN'
  | 'RECTIFY'
  | 'UPLOAD_PROOF'
  | 'REVIEW_CLOSE'
  | 'REVIEW_REJECT'
  | 'TRANSFER'

export interface CpsAttachment {
  id: number
  fileUrl: string
  fileName: string
  sortNo: number
}

export interface CpsIssueListItem {
  id: number
  issueNo: string
  status: CpsIssueStatus
  factoryId: number
  areaId: number
  lineId: number
  processId: number
  categoryL1Id: number
  categoryL2Id: number
  description: string
  currentHandlerEmpNo: string | null
  submitTime: string
  overdue: boolean
}

export interface CpsIssueDetail extends CpsIssueListItem {
  availableActions: CpsIssueAction[]
  aiCategoryL1Id: number | null
  aiCategoryL2Id: number | null
  reasonAnalysis: string | null
  correctiveMeasure: string | null
  rectifyRemark: string | null
  reviewOpinion: string | null
  issueAttachments: CpsAttachment[]
  proofAttachments: CpsAttachment[]
  flowLogs: Array<{
    action: string
    operatorEmpNo: string
    fromStatus: string | null
    toStatus: string
    comment: string | null
    createdAt: string
  }>
}

export interface CpsIssueCreatePayload {
  factoryId: number
  areaId: number
  lineId: number
  processId: number
  aiCategoryL1Id: number | null
  aiCategoryL2Id: number | null
  categoryL1Id: number
  categoryL2Id: number
  description: string
  feedbackEmpNo: string
  issueAttachmentIds: number[]
  aiSuggestion?: {
    sourceAttachmentId: number
    aiCategoryL1Id: number | null
    aiCategoryL1Name: string | null
    aiCategoryL2Id: number | null
    aiCategoryL2Name: string | null
    reasonSuggestion: string | null
    measureSuggestion: string | null
    modelName: string | null
    modelVersion: string | null
    rawRequest: string | null
    rawResponse: string | null
    confidence: string | null
  }
}
```

- [ ] **Step 2: Add issue API**

```ts
import request from '@/utils/request'
import type { CpsIssueAction, CpsIssueCreatePayload, CpsIssueDetail, CpsIssueListItem } from '@/types/cps'

export function createCpsIssue(payload: CpsIssueCreatePayload) {
  return request.post<{ issueId: number }>('/api/cps/issues', payload)
}

export function listCpsIssues(params: { tab: 'todo' | 'created' | 'related' | 'closed'; page: number; pageSize: number }) {
  return request.get<CpsIssueListItem[]>('/api/cps/issues', { params })
}

export function getCpsIssueDetail(id: number) {
  return request.get<CpsIssueDetail>(`/api/cps/issues/${id}`)
}

export function executeCpsIssueAction(id: number, payload: { action: CpsIssueAction; [key: string]: unknown }) {
  return request.post<{ success: boolean }>(`/api/cps/issues/${id}/actions`, payload)
}
```

- [ ] **Step 3: Add master data API**

```ts
import request from '@/utils/request'

export interface CpsOption {
  label: string
  value: number | string
}

export function getFactories() {
  return request.get<CpsOption[]>('/api/cps/master/factories')
}

export function getAreas(factoryId: number) {
  return request.get<CpsOption[]>('/api/cps/master/areas', { params: { factoryId } })
}

export function getLines(areaId: number) {
  return request.get<CpsOption[]>('/api/cps/master/lines', { params: { areaId } })
}

export function getProcesses(areaId: number, lineId?: number) {
  return request.get<CpsOption[]>('/api/cps/master/processes', { params: { areaId, lineId } })
}

export function getCategories(parentId?: number) {
  return request.get<CpsOption[]>('/api/cps/master/categories', { params: { parentId } })
}

export function getFeedbackHandler(params: { factoryId: number; areaId: number; lineId: number; processId: number }) {
  return request.get<{ empNo: string; empName: string }>('/api/cps/assignment/feedback-handler', { params })
}

export function getReviewer(params: { factoryId: number; areaId: number }) {
  return request.get<{ empNo: string; empName: string }>('/api/cps/assignment/reviewer', { params })
}
```

- [ ] **Step 4: Run typecheck**

Run:

```bash
pnpm typecheck
```

Expected: PASS after aligning `@/utils/request` with the existing mobile project request wrapper.

---

## Task 9: Build Mobile Shared Components

**Files:**
- Create: `mobile/src/components/cps/ImageUploader.vue`
- Create: `mobile/src/components/cps/LocationSelector.vue`
- Create: `mobile/src/components/cps/CategorySelector.vue`
- Create: `mobile/src/components/cps/ActionPanel.vue`
- Create: `mobile/src/components/cps/FlowTimeline.vue`
- Create: `mobile/src/components/cps/__tests__/ActionPanel.spec.ts`

- [ ] **Step 1: Write ActionPanel test**

```ts
import { mount } from '@vue/test-utils'
import ActionPanel from '../ActionPanel.vue'

describe('ActionPanel', () => {
  it('renders only backend available actions', () => {
    const wrapper = mount(ActionPanel, {
      props: {
        status: 'PENDING_FEEDBACK',
        availableActions: ['REPLY_ASSIGN', 'TRANSFER'],
      },
    })

    expect(wrapper.text()).toContain('回复并指派')
    expect(wrapper.text()).toContain('转办')
    expect(wrapper.text()).not.toContain('审核关闭')
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
pnpm test ActionPanel
```

Expected: FAIL because `ActionPanel.vue` does not exist.

- [ ] **Step 3: Create ImageUploader**

```vue
<script setup lang="ts">
const props = defineProps<{
  modelValue: Array<{ id: number; url: string; name: string }>
  max?: number
}>()

const emit = defineEmits<{
  'update:modelValue': [value: Array<{ id: number; url: string; name: string }>]
  firstImageReady: [fileId: number]
}>()

const maxCount = props.max ?? 5

function removeImage(id: number) {
  emit('update:modelValue', props.modelValue.filter((item) => item.id !== id))
}

function addUploadedImage(file: { id: number; url: string; name: string }) {
  if (props.modelValue.length >= maxCount) {
    throw new Error(`最多上传${maxCount}张图片`)
  }
  const next = [...props.modelValue, file]
  emit('update:modelValue', next)
  if (next.length === 1) {
    emit('firstImageReady', file.id)
  }
}

defineExpose({ addUploadedImage })
</script>

<template>
  <div class="cps-uploader">
    <div v-for="image in modelValue" :key="image.id" class="cps-uploader__item">
      <img :src="image.url" :alt="image.name" />
      <button type="button" @click="removeImage(image.id)">删除</button>
    </div>
    <button v-if="modelValue.length < maxCount" type="button">上传图片</button>
  </div>
</template>
```

- [ ] **Step 4: Create LocationSelector**

```vue
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { getAreas, getFactories, getLines, getProcesses, type CpsOption } from '@/api/cps/master'

const model = defineModel<{
  factoryId: number | null
  areaId: number | null
  lineId: number | null
  processId: number | null
}>({ required: true })

const factories = ref<CpsOption[]>([])
const areas = ref<CpsOption[]>([])
const lines = ref<CpsOption[]>([])
const processes = ref<CpsOption[]>([])
const showLineProcess = computed(() => model.value.areaId !== null)

async function loadFactories() {
  factories.value = await getFactories()
}

watch(() => model.value.factoryId, async (factoryId) => {
  model.value.areaId = null
  model.value.lineId = null
  model.value.processId = null
  areas.value = factoryId ? await getAreas(factoryId) : []
  lines.value = []
  processes.value = []
})

watch(() => model.value.areaId, async (areaId) => {
  model.value.lineId = null
  model.value.processId = null
  lines.value = areaId ? await getLines(areaId) : []
  processes.value = areaId ? await getProcesses(areaId) : []
})

watch(() => model.value.lineId, async (lineId) => {
  if (model.value.areaId) {
    model.value.processId = null
    processes.value = await getProcesses(model.value.areaId, lineId ?? undefined)
  }
})

loadFactories()
</script>

<template>
  <section class="cps-location">
    <select v-model="model.factoryId">
      <option :value="null">选择工厂</option>
      <option v-for="item in factories" :key="item.value" :value="item.value">{{ item.label }}</option>
    </select>
    <select v-model="model.areaId">
      <option :value="null">选择区域</option>
      <option v-for="item in areas" :key="item.value" :value="item.value">{{ item.label }}</option>
    </select>
    <select v-if="showLineProcess" v-model="model.lineId">
      <option :value="null">选择拉线</option>
      <option v-for="item in lines" :key="item.value" :value="item.value">{{ item.label }}</option>
    </select>
    <select v-if="showLineProcess" v-model="model.processId">
      <option :value="null">选择工序</option>
      <option v-for="item in processes" :key="item.value" :value="item.value">{{ item.label }}</option>
    </select>
  </section>
</template>
```

- [ ] **Step 5: Create CategorySelector**

```vue
<script setup lang="ts">
import { ref, watch } from 'vue'
import { getCategories, type CpsOption } from '@/api/cps/master'

const model = defineModel<{
  categoryL1Id: number | null
  categoryL2Id: number | null
}>({ required: true })

const level1 = ref<CpsOption[]>([])
const level2 = ref<CpsOption[]>([])

level1.value = await getCategories()

watch(() => model.value.categoryL1Id, async (parentId) => {
  model.value.categoryL2Id = null
  level2.value = parentId ? await getCategories(parentId) : []
})
</script>

<template>
  <section class="cps-category">
    <select v-model="model.categoryL1Id">
      <option :value="null">一级分类</option>
      <option v-for="item in level1" :key="item.value" :value="item.value">{{ item.label }}</option>
    </select>
    <select v-model="model.categoryL2Id">
      <option :value="null">二级分类</option>
      <option v-for="item in level2" :key="item.value" :value="item.value">{{ item.label }}</option>
    </select>
  </section>
</template>
```

- [ ] **Step 6: Create ActionPanel**

```vue
<script setup lang="ts">
import type { CpsIssueAction, CpsIssueStatus } from '@/types/cps'

defineProps<{
  status: CpsIssueStatus
  availableActions: CpsIssueAction[]
}>()

const labels: Record<CpsIssueAction, string> = {
  REPLY_ASSIGN: '回复并指派',
  RECTIFY: '完成整改',
  UPLOAD_PROOF: '上传整改照片',
  REVIEW_CLOSE: '审核关闭',
  REVIEW_REJECT: '审核退回',
  TRANSFER: '转办',
}

const emit = defineEmits<{
  action: [action: CpsIssueAction]
}>()
</script>

<template>
  <section class="cps-action-panel">
    <button
      v-for="action in availableActions"
      :key="action"
      type="button"
      @click="emit('action', action)"
    >
      {{ labels[action] }}
    </button>
  </section>
</template>
```

- [ ] **Step 7: Create FlowTimeline**

```vue
<script setup lang="ts">
defineProps<{
  logs: Array<{
    action: string
    operatorEmpNo: string
    fromStatus: string | null
    toStatus: string
    comment: string | null
    createdAt: string
  }>
}>()
</script>

<template>
  <ol class="cps-flow">
    <li v-for="log in logs" :key="`${log.action}-${log.createdAt}`">
      <strong>{{ log.action }}</strong>
      <span>{{ log.operatorEmpNo }}</span>
      <span>{{ log.fromStatus || '开始' }} -> {{ log.toStatus }}</span>
      <p v-if="log.comment">{{ log.comment }}</p>
      <time>{{ log.createdAt }}</time>
    </li>
  </ol>
</template>
```

- [ ] **Step 8: Run component test**

Run:

```bash
pnpm test ActionPanel
```

Expected: PASS.

---

## Task 10: Build Mobile Pages

**Files:**
- Create: `mobile/src/views/cps/IssueCreateView.vue`
- Create: `mobile/src/views/cps/IssueListView.vue`
- Create: `mobile/src/views/cps/IssueDetailView.vue`
- Create: `mobile/src/router/cpsRoutes.ts`
- Create: `mobile/src/views/cps/__tests__/IssueCreateView.spec.ts`

- [ ] **Step 1: Write create view test for 5-image rule**

```ts
import { mount } from '@vue/test-utils'
import IssueCreateView from '../IssueCreateView.vue'

describe('IssueCreateView', () => {
  it('shows submit disabled when no issue image exists', () => {
    const wrapper = mount(IssueCreateView, {
      global: {
        stubs: ['LocationSelector', 'CategorySelector', 'ImageUploader'],
      },
    })

    expect(wrapper.get('[data-test="submit"]').attributes('disabled')).toBeDefined()
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
pnpm test IssueCreateView
```

Expected: FAIL because `IssueCreateView.vue` does not exist.

- [ ] **Step 3: Create IssueCreateView**

```vue
<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import ImageUploader from '@/components/cps/ImageUploader.vue'
import LocationSelector from '@/components/cps/LocationSelector.vue'
import CategorySelector from '@/components/cps/CategorySelector.vue'
import { createCpsIssue } from '@/api/cps/issue'
import { getFeedbackHandler } from '@/api/cps/master'

const location = reactive({
  factoryId: null as number | null,
  areaId: null as number | null,
  lineId: null as number | null,
  processId: null as number | null,
})

const category = reactive({
  categoryL1Id: null as number | null,
  categoryL2Id: null as number | null,
})

const images = ref<Array<{ id: number; url: string; name: string }>>([])
const description = ref('')
const feedbackEmpNo = ref('')
const aiSuggestion = ref<{
  sourceAttachmentId: number
  aiCategoryL1Id: number | null
  aiCategoryL2Id: number | null
  reasonSuggestion: string | null
  measureSuggestion: string | null
} | null>(null)

const canSubmit = computed(() =>
  images.value.length >= 1 &&
  images.value.length <= 5 &&
  location.factoryId &&
  location.areaId &&
  location.lineId &&
  location.processId &&
  category.categoryL1Id &&
  category.categoryL2Id &&
  description.value.trim() &&
  feedbackEmpNo.value
)

watch(location, async () => {
  if (location.factoryId && location.areaId && location.lineId && location.processId) {
    const handler = await getFeedbackHandler({
      factoryId: location.factoryId,
      areaId: location.areaId,
      lineId: location.lineId,
      processId: location.processId,
    })
    feedbackEmpNo.value = handler.empNo
  }
})

async function onFirstImageReady(fileId: number) {
  aiSuggestion.value = {
    sourceAttachmentId: fileId,
    aiCategoryL1Id: null,
    aiCategoryL2Id: null,
    reasonSuggestion: null,
    measureSuggestion: null,
  }
}

async function submit() {
  if (!canSubmit.value) return
  await createCpsIssue({
    factoryId: location.factoryId!,
    areaId: location.areaId!,
    lineId: location.lineId!,
    processId: location.processId!,
    aiCategoryL1Id: aiSuggestion.value?.aiCategoryL1Id ?? null,
    aiCategoryL2Id: aiSuggestion.value?.aiCategoryL2Id ?? null,
    categoryL1Id: category.categoryL1Id!,
    categoryL2Id: category.categoryL2Id!,
    description: description.value.trim(),
    feedbackEmpNo: feedbackEmpNo.value,
    issueAttachmentIds: images.value.map((image) => image.id),
    aiSuggestion: aiSuggestion.value
      ? {
          sourceAttachmentId: aiSuggestion.value.sourceAttachmentId,
          aiCategoryL1Id: aiSuggestion.value.aiCategoryL1Id,
          aiCategoryL1Name: null,
          aiCategoryL2Id: aiSuggestion.value.aiCategoryL2Id,
          aiCategoryL2Name: null,
          reasonSuggestion: aiSuggestion.value.reasonSuggestion,
          measureSuggestion: aiSuggestion.value.measureSuggestion,
          modelName: null,
          modelVersion: null,
          rawRequest: null,
          rawResponse: null,
          confidence: null,
        }
      : undefined,
  })
}
</script>

<template>
  <main class="cps-create">
    <ImageUploader v-model="images" :max="5" @first-image-ready="onFirstImageReady" />
    <LocationSelector v-model="location" />
    <CategorySelector v-model="category" />
    <textarea v-model="description" placeholder="问题描述" />
    <input v-model="feedbackEmpNo" placeholder="反馈人" />
    <button data-test="submit" type="button" :disabled="!canSubmit" @click="submit">提交并派发</button>
  </main>
</template>
```

- [ ] **Step 4: Create IssueListView**

```vue
<script setup lang="ts">
import { ref, watch } from 'vue'
import { listCpsIssues } from '@/api/cps/issue'
import type { CpsIssueListItem } from '@/types/cps'

const tab = ref<'todo' | 'created' | 'related' | 'closed'>('todo')
const items = ref<CpsIssueListItem[]>([])

async function load() {
  items.value = await listCpsIssues({ tab: tab.value, page: 1, pageSize: 20 })
}

watch(tab, load, { immediate: true })
</script>

<template>
  <main class="cps-list">
    <nav>
      <button type="button" @click="tab = 'todo'">待我处理</button>
      <button type="button" @click="tab = 'created'">我发起的</button>
      <button type="button" @click="tab = 'related'">我参与的</button>
      <button type="button" @click="tab = 'closed'">已关闭</button>
    </nav>
    <article v-for="item in items" :key="item.id" class="cps-list-card">
      <h3>{{ item.issueNo }}</h3>
      <p>{{ item.status }}</p>
      <p>{{ item.description }}</p>
      <p>{{ item.currentHandlerEmpNo }}</p>
      <time>{{ item.submitTime }}</time>
    </article>
  </main>
</template>
```

- [ ] **Step 5: Create IssueDetailView**

```vue
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { executeCpsIssueAction, getCpsIssueDetail } from '@/api/cps/issue'
import type { CpsIssueAction, CpsIssueDetail } from '@/types/cps'
import ActionPanel from '@/components/cps/ActionPanel.vue'
import FlowTimeline from '@/components/cps/FlowTimeline.vue'

const route = useRoute()
const detail = ref<CpsIssueDetail | null>(null)

async function load() {
  detail.value = await getCpsIssueDetail(Number(route.params.id))
}

async function runAction(action: CpsIssueAction) {
  await executeCpsIssueAction(Number(route.params.id), { action })
  await load()
}

onMounted(load)
</script>

<template>
  <main v-if="detail" class="cps-detail">
    <header>
      <h2>{{ detail.issueNo }}</h2>
      <p>{{ detail.status }}</p>
      <p>{{ detail.description }}</p>
    </header>
    <section>
      <h3>问题图片</h3>
      <img v-for="image in detail.issueAttachments" :key="image.id" :src="image.fileUrl" :alt="image.fileName" />
    </section>
    <section>
      <h3>整改图片</h3>
      <img v-for="image in detail.proofAttachments" :key="image.id" :src="image.fileUrl" :alt="image.fileName" />
    </section>
    <ActionPanel
      :status="detail.status"
      :available-actions="detail.availableActions"
      @action="runAction"
    />
    <FlowTimeline :logs="detail.flowLogs" />
  </main>
</template>
```

- [ ] **Step 6: Add route module**

```ts
export const cpsRoutes = [
  {
    path: '/issue/create',
    component: () => import('@/views/cps/IssueCreateView.vue'),
  },
  {
    path: '/issue/list',
    component: () => import('@/views/cps/IssueListView.vue'),
  },
  {
    path: '/issue/detail/:id',
    component: () => import('@/views/cps/IssueDetailView.vue'),
  },
]
```

- [ ] **Step 7: Run mobile tests and typecheck**

Run:

```bash
pnpm test IssueCreateView
pnpm test ActionPanel
pnpm typecheck
```

Expected: all commands pass after wiring the route module into the existing router.

---

## Task 11: Integration Verification

**Files:**
- Modify only the existing project files needed to register Spring components, MyBatis mappers, and Vue routes.

- [ ] **Step 1: Verify backend full test suite**

Run:

```bash
mvn -q test
```

Expected: PASS.

- [ ] **Step 2: Verify mobile full checks**

Run:

```bash
pnpm lint
pnpm typecheck
pnpm test
```

Expected: PASS.

- [ ] **Step 3: Verify create issue API manually**

Run with an authenticated session in the existing project API client:

```http
POST /api/cps/issues
Content-Type: application/json

{
  "factoryId": 1,
  "areaId": 2,
  "lineId": 3,
  "processId": 4,
  "aiCategoryL1Id": 10,
  "aiCategoryL2Id": 11,
  "categoryL1Id": 10,
  "categoryL2Id": 11,
  "description": "现场发现物料标识不清",
  "feedbackEmpNo": "E10001",
  "issueAttachmentIds": [101],
  "aiSuggestion": {
    "sourceAttachmentId": 101,
    "aiCategoryL1Id": 10,
    "aiCategoryL1Name": "物料问题",
    "aiCategoryL2Id": 11,
    "aiCategoryL2Name": "标识不清",
    "reasonSuggestion": "物料标识维护不及时",
    "measureSuggestion": "补充标识并复核现场物料",
    "modelName": "cps-vision",
    "modelVersion": "1.0",
    "rawRequest": "{}",
    "rawResponse": "{}",
    "confidence": "0.8600"
  }
}
```

Expected:

```json
{
  "issueId": 1
}
```

- [ ] **Step 4: Verify workflow action manually**

Run:

```http
POST /api/cps/issues/1/actions
Content-Type: application/json

{
  "action": "REPLY_ASSIGN",
  "reasonAnalysis": "现场标识未及时更新",
  "correctiveMeasure": "重新张贴标识并复核库存",
  "responsibleEmpNo": "E10023",
  "comment": "请当天完成"
}
```

Expected:

```json
{
  "success": true
}
```

Database expected:

```sql
SELECT status, current_handler_emp_no, reason_analysis, corrective_measure, responsible_emp_no
FROM cps_issue
WHERE id = 1;
```

Expected row:

```text
PENDING_RECTIFY | E10023 | 现场标识未及时更新 | 重新张贴标识并复核库存 | E10023
```

---

## Self-Review

Spec coverage:

```text
移动端 Vue3 页面：Task 8, Task 9, Task 10
Spring Boot + MyBatis 后端：Task 1 through Task 7
轻量状态机：Task 3, Task 6
每节点当前操作人 empNo：Task 1, Task 3, Task 6
我的问题和详情：Task 8, Task 10
工厂/区域/拉线/工序联动：Task 8, Task 9
反馈人按位置匹配：Task 8, Task 10; backend assignment hook in Task 6
两级问题分类：Task 8, Task 9, Task 10
图片每次最多5张：Task 1, Task 6, Task 9, Task 10
仅第一张问题图做AI识别：Task 9, Task 10, integration payload in Task 11
AI一级/二级分类建议：Task 1, Task 4, Task 8, Task 10
AI原因&措施单独存表：Task 1, Task 4, Task 5, Task 6
流程日志：Task 1, Task 4, Task 5, Task 6, Task 10
审核关闭/退回：Task 3, Task 6, Task 11
超时提醒规则表：Task 1
```

Known integration points:

```text
Current empNo lookup must use the existing project's authentication context.
AssignmentService must connect to the existing factory/area/line/process personnel data.
Attachment upload endpoint must connect to the existing file storage implementation.
AI image recognition endpoint must connect to the chosen AI service.
Speech-to-text is represented as final description text and can be implemented by the mobile shell or backend service.
```
