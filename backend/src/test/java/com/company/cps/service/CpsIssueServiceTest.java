package com.company.cps.service;

import com.company.cps.domain.CpsIssue;
import com.company.cps.domain.CpsIssueAction;
import com.company.cps.domain.CpsIssueFlowLog;
import com.company.cps.domain.CpsIssueStatus;
import com.company.cps.dto.CpsIssueActionRequest;
import com.company.cps.dto.CpsIssueAiSuggestionRequest;
import com.company.cps.dto.CpsIssueCreateRequest;
import com.company.cps.dto.CpsIssueDetailResponse;
import com.company.cps.mapper.CpsIssueAiSuggestionMapper;
import com.company.cps.mapper.CpsIssueAttachmentMapper;
import com.company.cps.mapper.CpsIssueFlowLogMapper;
import com.company.cps.mapper.CpsIssueMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CpsIssueServiceTest {

    @Mock
    private CpsIssueMapper issueMapper;
    @Mock
    private CpsIssueAttachmentMapper attachmentMapper;
    @Mock
    private CpsIssueAiSuggestionMapper aiSuggestionMapper;
    @Mock
    private CpsIssueFlowLogMapper flowLogMapper;
    @Mock
    private CpsAssignmentService assignmentService;

    private final CpsWorkflowStateMachine stateMachine = new CpsWorkflowStateMachine();

    private CpsIssueService service;

    @BeforeEach
    void setUp() {
        service = new CpsIssueService(
                issueMapper,
                attachmentMapper,
                aiSuggestionMapper,
                flowLogMapper,
                assignmentService,
                stateMachine
        );
    }

    @Test
    void createIssueInitializesPendingFeedbackAndCurrentHandler() {
        mockIssueInsertGeneratedId(900L);
        CpsIssueCreateRequest request = new CpsIssueCreateRequest();
        request.setFactory("F1");
        request.setArea("A1");
        request.setLine("L1");
        request.setProcess("P1");
        request.setCategoryL1Id(10L);
        request.setCategoryL2Id(11L);
        request.setDescription("label missing");
        request.setFeedbackEmpNo("E10001");
        request.setIssueAttachmentIds(Collections.singletonList(101L));
        when(attachmentMapper.attachToIssue(101L, 900L, "ISSUE", 1, "E00001", "E00001")).thenReturn(1);

        service.createIssue(request, "E00001");

        ArgumentCaptor<CpsIssue> issueCaptor = ArgumentCaptor.forClass(CpsIssue.class);
        verify(issueMapper).insert(issueCaptor.capture());
        CpsIssue saved = issueCaptor.getValue();
        assertEquals(CpsIssueStatus.PENDING_FEEDBACK, saved.getStatus());
        assertEquals("E10001", saved.getCurrentHandlerEmpNo());
        assertEquals("E00001", saved.getCreatorEmpNo());
        assertEquals("F1", saved.getFactory());
        assertEquals("E10001", saved.getCurrentHandlerEmpName());
        assertFalse(saved.getIssueNo().trim().isEmpty());
        assertEquals(900L, saved.getId());
        verify(attachmentMapper).attachToIssue(101L, 900L, "ISSUE", 1, "E00001", "E00001");
        verify(flowLogMapper).insert(any());
    }

    @Test
    void createIssueUsesAssignmentWhenFeedbackEmployeeIsBlank() {
        mockIssueInsertGeneratedId(900L);
        CpsIssueCreateRequest request = createRequest();
        request.setFeedbackEmpNo(" ");
        when(assignmentService.findFeedbackHandler("F1", "A1", "L1", "P1")).thenReturn("E10001");
        when(attachmentMapper.attachToIssue(101L, 900L, "ISSUE", 1, "E00001", "E00001")).thenReturn(1);

        service.createIssue(request, "E00001");

        ArgumentCaptor<CpsIssue> issueCaptor = ArgumentCaptor.forClass(CpsIssue.class);
        verify(issueMapper).insert(issueCaptor.capture());
        assertEquals("E10001", issueCaptor.getValue().getFeedbackEmpNo());
        assertEquals("E10001", issueCaptor.getValue().getCurrentHandlerEmpNo());
    }

    @Test
    void createIssueFailsClearlyWhenFeedbackEmployeeCannotBeResolved() {
        CpsIssueCreateRequest request = createRequest();
        request.setFeedbackEmpNo(null);
        when(assignmentService.findFeedbackHandler("F1", "A1", "L1", "P1")).thenReturn(null);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.createIssue(request, "E00001")
        );

        assertTrue(error.getMessage().contains("feedbackEmpNo is required when no assignment rule matches"));
        verify(issueMapper, never()).insert(any());
    }

    @Test
    void createIssueRequiresAiSuggestionSourceToBeFirstIssueAttachment() {
        CpsIssueCreateRequest request = createRequest();
        request.setIssueAttachmentIds(Arrays.asList(101L, 102L));
        CpsIssueAiSuggestionRequest suggestion = new CpsIssueAiSuggestionRequest();
        suggestion.setSourceAttachmentId(102L);
        request.setAiSuggestion(suggestion);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.createIssue(request, "E00001")
        );

        assertTrue(error.getMessage().contains("sourceAttachmentId must match first issue attachment id"));
        verify(issueMapper, never()).insert(any());
    }

    @Test
    void createIssueBindsMultipleIssueImagesInSelectedOrder() {
        mockIssueInsertGeneratedId(900L);
        CpsIssueCreateRequest request = createRequest();
        request.setIssueAttachmentIds(Arrays.asList(101L, 102L, 103L));
        when(attachmentMapper.attachToIssue(101L, 900L, "ISSUE", 1, "E00001", "E00001")).thenReturn(1);
        when(attachmentMapper.attachToIssue(102L, 900L, "ISSUE", 2, "E00001", "E00001")).thenReturn(1);
        when(attachmentMapper.attachToIssue(103L, 900L, "ISSUE", 3, "E00001", "E00001")).thenReturn(1);

        service.createIssue(request, "E00001");

        verify(attachmentMapper).attachToIssue(101L, 900L, "ISSUE", 1, "E00001", "E00001");
        verify(attachmentMapper).attachToIssue(102L, 900L, "ISSUE", 2, "E00001", "E00001");
        verify(attachmentMapper).attachToIssue(103L, 900L, "ISSUE", 3, "E00001", "E00001");
    }

    @Test
    void createIssueFailsWhenAttachmentCannotBeBound() {
        mockIssueInsertGeneratedId(900L);
        CpsIssueCreateRequest request = createRequest();
        when(attachmentMapper.attachToIssue(101L, 900L, "ISSUE", 1, "E00001", "E00001")).thenReturn(0);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.createIssue(request, "E00001")
        );

        assertTrue(error.getMessage().contains("Attachment 101 cannot be bound to issue 900"));
    }

    @Test
    void createIssueFailsClearlyWhenGeneratedIssueIdIsMissing() {
        CpsIssueCreateRequest request = createRequest();

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.createIssue(request, "E00001")
        );

        assertTrue(error.getMessage().contains("Issue id was not generated"));
        verify(attachmentMapper, never()).attachToIssue(any(), any(), any(), any(Integer.class), any(), any());
    }

    @Test
    void createIssueWritesSnapshotJsonWithActionCommentAndPayload() {
        mockIssueInsertGeneratedId(900L);
        CpsIssueCreateRequest request = createRequest();
        when(attachmentMapper.attachToIssue(101L, 900L, "ISSUE", 1, "E00001", "E00001")).thenReturn(1);

        service.createIssue(request, "E00001");

        ArgumentCaptor<CpsIssueFlowLog> logCaptor = ArgumentCaptor.forClass(CpsIssueFlowLog.class);
        verify(flowLogMapper).insert(logCaptor.capture());
        String snapshotJson = logCaptor.getValue().getSnapshotJson();
        assertTrue(snapshotJson.contains("\"action\":\"SUBMIT\""));
        assertTrue(snapshotJson.contains("\"comment\":\"submit issue\""));
        assertTrue(snapshotJson.contains("\"toHandlerEmpNo\":\"E10001\""));
    }

    @Test
    void replyAssignMovesIssueToResponsibleEmployee() {
        CpsIssue issue = issue(1L, CpsIssueStatus.PENDING_FEEDBACK, "E10001");
        when(issueMapper.findById(1L)).thenReturn(Optional.of(issue));

        CpsIssueActionRequest request = new CpsIssueActionRequest();
        request.setAction(CpsIssueAction.REPLY_ASSIGN);
        request.setReasonAnalysis("not updated");
        request.setCorrectiveMeasure("refresh labels");
        request.setResponsibleEmpNo("E10023");
        request.setComment("today");

        service.executeAction(1L, request, "E10001");

        assertEquals(CpsIssueStatus.PENDING_RECTIFY, issue.getStatus());
        assertEquals("E10023", issue.getCurrentHandlerEmpNo());
        assertEquals("E10023", issue.getResponsibleEmpNo());
        verify(issueMapper).updateWorkflowFields(issue);
        verify(flowLogMapper).insert(any());
    }

    @Test
    void actionRequiresCurrentHandler() {
        CpsIssue issue = issue(1L, CpsIssueStatus.PENDING_FEEDBACK, "E10001");
        when(issueMapper.findById(1L)).thenReturn(Optional.of(issue));

        CpsIssueActionRequest request = new CpsIssueActionRequest();
        request.setAction(CpsIssueAction.TRANSFER);
        request.setTargetEmpNo("E20002");

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.executeAction(1L, request, "E99999")
        );

        assertTrue(error.getMessage().contains("Current employee is not issue handler"));
    }

    @Test
    void detailReturnsActionsOnlyForCurrentHandler() {
        CpsIssue issue = issue(1L, CpsIssueStatus.PENDING_FEEDBACK, "E10001");
        when(issueMapper.findById(1L)).thenReturn(Optional.of(issue));

        CpsIssueDetailResponse handlerResponse = service.getDetail(1L, "E10001");
        CpsIssueDetailResponse otherResponse = service.getDetail(1L, "E99999");

        assertTrue(handlerResponse.getAvailableActions().contains(CpsIssueAction.REPLY_ASSIGN));
        assertTrue(otherResponse.getAvailableActions().isEmpty());
    }

    private static CpsIssueCreateRequest createRequest() {
        CpsIssueCreateRequest request = new CpsIssueCreateRequest();
        request.setFactory("F1");
        request.setArea("A1");
        request.setLine("L1");
        request.setProcess("P1");
        request.setCategoryL1Id(10L);
        request.setCategoryL2Id(11L);
        request.setDescription("label missing");
        request.setFeedbackEmpNo("E10001");
        request.setIssueAttachmentIds(Collections.singletonList(101L));
        return request;
    }

    private static CpsIssue issue(Long id, CpsIssueStatus status, String handler) {
        CpsIssue issue = new CpsIssue();
        issue.setId(id);
        issue.setStatus(status);
        issue.setCurrentHandlerEmpNo(handler);
        return issue;
    }

    private void mockIssueInsertGeneratedId(Long id) {
        doAnswer(invocation -> {
            CpsIssue issue = invocation.getArgument(0);
            issue.setId(id);
            return 1;
        }).when(issueMapper).insert(any(CpsIssue.class));
    }
}
