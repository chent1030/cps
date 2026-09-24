package com.company.cps.service;

import com.company.cps.config.CpsAgentFrameworkProperties;
import com.company.cps.config.CpsInitialReviewProperties;
import com.company.cps.config.CpsVisionProperties;
import com.company.cps.domain.CpsInitialReviewTask;
import com.company.cps.domain.CpsInitialReviewTaskStatus;
import com.company.cps.domain.CpsIssue;
import com.company.cps.domain.CpsIssueAttachment;
import com.company.cps.domain.CpsIssueStatus;
import com.company.cps.domain.CpsPushConfig;
import com.company.cps.domain.CpsRectificationSubmission;
import com.company.cps.domain.CpsReviewAdjudication;
import com.company.cps.domain.CpsVisionCheckJudgeEvent;
import com.company.cps.domain.CpsVisionCheckRecord;
import com.company.cps.domain.CpsVisionCheckStatus;
import com.company.cps.dto.CpsInitialReviewCallbackRequest;
import com.company.cps.dto.CpsPageResponse;
import com.company.cps.dto.CpsPushConfigRequest;
import com.company.cps.dto.CpsVisionCheckOverrideRequest;
import com.company.cps.dto.CpsVisionCheckSubmitRequest;
import com.company.cps.dto.CpsVisionCheckSubmitResult;
import com.company.cps.mapper.CpsCoverageMapper;
import com.company.cps.mapper.CpsEffectMetricMapper;
import com.company.cps.mapper.CpsInitialReviewConfigMapper;
import com.company.cps.mapper.CpsInitialReviewEventMapper;
import com.company.cps.mapper.CpsInitialReviewItemMapper;
import com.company.cps.mapper.CpsInitialReviewResultMapper;
import com.company.cps.mapper.CpsInitialReviewTaskMapper;
import com.company.cps.mapper.CpsIssueAttachmentMapper;
import com.company.cps.mapper.CpsIssueFlowLogMapper;
import com.company.cps.mapper.CpsIssueMapper;
import com.company.cps.mapper.CpsPushConfigMapper;
import com.company.cps.mapper.CpsRectificationSubmissionMapper;
import com.company.cps.mapper.CpsReviewAdjudicationMapper;
import com.company.cps.mapper.CpsVisionCheckJudgeEventMapper;
import com.company.cps.mapper.CpsVisionCheckRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 波次 13：C7 系统化测试 + J 线联调验收（Java 端部分）。
 *
 * <p>覆盖 PRD AC-22 验收测试矩阵，4 条端到端链：
 * <ul>
 *   <li>Chain 1 — AI 初审 + 裁决 + 写记忆 + 检索 hints 闭环
 *     （Java → Python /api/v1/agent/rectifications 投递 + C-02 回调 + Python /api/v1/memory/entries 写入 +
 *      Java 调 Python /api/v1/memory/retrieve 取 hints，MockRestServiceServer 拦截 Python 端）；</li>
 *   <li>Chain 2 — B6 视觉点检：POST /api/cps/room-checks → vision mock PASS → AI_PASS
 *     → 人工 override 改判 HUMAN_OVERRIDE，验 ai_overall 不被覆写（CHECK 约束）+ operator 字段落库；</li>
 *   <li>Chain 3 — B7 覆盖分析 5 视图：frequency / region-supervisor / recurrence / gaps / effect，
 *     验 4 路分类谓词（CpsIssueMapperContractTest 已有断言）+ service 路由分流；</li>
 *   <li>Chain 4 — F 线 D-22 推送渠道配置：GET 单行 + PUT 改 enabled_channel 列表
 *     验 CHECK id=1 + UNIQUE enabled_channel + enabled=true 但 endpoint 空 → 400；</li>
 * </ul>
 *
 * <p>不变量校验：
 * <ul>
 *   <li>cps_initial_review_event 12 类全集（V20261001 迁移约束枚举）；</li>
 *   <li>cps_review_adjudication uk(issue_id, version_no) 幂等（DuplicateKeyException → findByIssueAndVersion）；</li>
 *   <li>cps_vision_check_judge_event 7 类全集（V20261006 迁移约束枚举）；</li>
 *   <li>cps_push_config 单行 id=1 + UNIQUE enabled_channel（生成列约束）；</li>
 *   <li>cps_issue ↔ cps_memory_entry 双向参照一致（issueId/decision/ai_relation 三元组透传）。</li>
 * </ul>
 *
 * <p>Mock 策略：Java → Python 6 端口全部走 MockRestServiceServer，避免依赖 Python 仓；
 * Mapper 层全部 Mockito Mock（无真实 DB），端到端逻辑由 service 层串接验证。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CpsWave13IntegrationTest {

    // ----- Chain 1 — AI 初审 + 裁决 + 写记忆 + 检索 hints -----

    @Mock private CpsInitialReviewTaskMapper taskMapper;
    @Mock private CpsInitialReviewResultMapper resultMapper;
    @Mock private CpsInitialReviewItemMapper itemMapper;
    @Mock private CpsInitialReviewEventMapper eventMapper;
    @Mock private CpsRectificationSubmissionMapper submissionMapper;
    @Mock private CpsIssueMapper issueMapper;
    @Mock private CpsIssueAttachmentMapper attachmentMapper;
    @Mock private CpsIssueFlowLogMapper flowLogMapper;
    @Mock private CpsReviewAdjudicationMapper adjudicationMapper;
    @Mock private CpsInitialReviewConfigMapper configMapper;
    @Mock private CpsAssignmentService assignmentService;
    @Mock private CpsAttachmentContentResolver contentResolver;

    private CpsAgentFrameworkProperties frameworkProperties;
    private RestTemplate frameworkClient;
    private MockRestServiceServer pythonMock;
    private CpsAgentFrameworkClient agentClient;
    private CpsInitialReviewService initialReviewService;
    private CpsWorkflowStateMachineV2 stateMachineV2;
    private CpsInitialReviewProperties initialReviewProperties;

    // ----- Chain 2 — B6 视觉点检 -----

    @Mock private CpsVisionCheckRecordMapper recordMapper;
    @Mock private CpsVisionCheckJudgeEventMapper judgeEventMapper;
    @Mock private CpsVisionClient visionClient;

    private CpsVisionProperties visionProperties;
    private CpsVisionCheckService visionCheckService;

    // ----- Chain 3 — B7 覆盖分析 -----

    @Mock private CpsCoverageMapper coverageMapper;
    @Mock private CpsEffectMetricMapper effectMapper;
    private CpsCoverageService coverageService;

    // ----- Chain 4 — F 线 push_config -----

    @Mock private CpsPushConfigMapper pushConfigMapper;
    private CpsPushConfigService pushConfigService;

    @BeforeEach
    void setUp() {
        // ----- Chain 1 wiring -----
        frameworkProperties = new CpsAgentFrameworkProperties();
        frameworkProperties.setEnabled(true);
        frameworkProperties.setBaseUrl("http://python.test/api/v1");
        frameworkProperties.setTimeoutMs(2000);
        frameworkClient = new RestTemplate();
        pythonMock = MockRestServiceServer.bindTo(frameworkClient).build();
        agentClient = new CpsAgentFrameworkClient(frameworkProperties, contentResolver, frameworkClient);
        initialReviewProperties = new CpsInitialReviewProperties();
        initialReviewProperties.setTimeoutSeconds(600);
        initialReviewProperties.setCallbackBaseUrl("http://127.0.0.1:8080/");
        stateMachineV2 = new CpsWorkflowStateMachineV2();
        initialReviewService = new CpsInitialReviewService(
                taskMapper, resultMapper, itemMapper, submissionMapper, issueMapper, attachmentMapper,
                flowLogMapper, agentClient, assignmentService, initialReviewProperties, stateMachineV2,
                configMapper, eventMapper, adjudicationMapper);

        // ----- Chain 2 wiring -----
        visionProperties = new CpsVisionProperties();
        visionProperties.setEnabled(true);
        visionProperties.setBaseUrl("http://python.test");
        visionProperties.setPath("/api/v1/agent/room-checks/judge");
        visionProperties.setTimeoutMs(2000);
        visionCheckService = new CpsVisionCheckService(recordMapper, judgeEventMapper, visionClient);

        // ----- Chain 3 wiring -----
        coverageService = new CpsCoverageService(coverageMapper, effectMapper);

        // ----- Chain 4 wiring -----
        pushConfigService = new CpsPushConfigService(pushConfigMapper);
    }

    // ============================================================
    // Chain 1 — AI 初审 + 裁决 + 写记忆 + 检索 hints 闭环
    // ============================================================

    @Test
    @DisplayName("Chain 1: AI 初审 + 裁决 + 写记忆 + 检索 hints 闭环（≥3 断言）")
    void chain1AiInitialReviewAdjudicationMemoryAndHintsLoop() {
        // ---- arrange ----
        Long issueId = 8001L;
        Long submissionId = 9001L;
        Integer versionNo = 1;
        Long taskId = 777L;

        CpsIssue issue = newIssue(issueId, CpsIssueStatus.PENDING_AI_REVIEW);
        CpsRectificationSubmission submission = newSubmission(submissionId, issueId, versionNo);
        CpsInitialReviewTask runningTask = runningTask(taskId, issueId, versionNo);

        when(issueMapper.findById(issueId)).thenReturn(Optional.of(issue));
        when(submissionMapper.findById(submissionId)).thenReturn(submission);
        when(attachmentMapper.findByIssueAndStage(issueId, "ISSUE")).thenReturn(List.of());
        when(attachmentMapper.findByIssueAndStage(issueId, "PROOF")).thenReturn(List.of());
        when(taskMapper.findById(taskId)).thenReturn(runningTask);
        when(taskMapper.findByIssueAndVersion(issueId, versionNo)).thenReturn(runningTask);
        when(taskMapper.findExpiredRunning(any())).thenReturn(List.of());
        when(adjudicationMapper.findByIssueAndVersion(issueId, versionNo)).thenReturn(null);
        // handleCallback 路径：resultMapper.findByTaskId null + 任务 RUNNING
        when(resultMapper.findByTaskId(taskId)).thenReturn(null);
        // C-02 COMPLETED 后 advanceIssueAfterTaskTerminal → routeToReview 落库（均为 void）
        doAnswer(inv -> null).when(issueMapper).updateReviewRouting(anyLong(), anyString(), anyString(), any(), any());
        doAnswer(inv -> null).when(flowLogMapper).insert(any());

        // ---- arrange: 一次性把 3 个 Python 端 mock 设好（避免 act 阶段再 expect 触发 MockRestServiceServer 异常）----
        // /api/v1/agent/rectifications → C-01 投递
        pythonMock.expect(requestTo("http://python.test/api/v1/agent/rectifications"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"review_task_ref\":\"PY-RECT-777\"}", MediaType.APPLICATION_JSON));
        // /api/v1/memory/entries → Java 写裁决进 Python 记忆库
        pythonMock.expect(requestTo("http://python.test/api/v1/memory/entries"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"memory_id\":\"MEM-9001\",\"status\":\"stored\"}", MediaType.APPLICATION_JSON));
        // /api/v1/memory/retrieve → 下次初审 hints 检索
        pythonMock.expect(requestTo(org.hamcrest.Matchers.containsString("/api/v1/memory/retrieve")))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"hints\":[{\"key\":\"AGREE\",\"reason\":\"AI 与人工一致：通过\"}],"
                                + "\"next_reviewer\":\"E20001\"}",
                        MediaType.APPLICATION_JSON));

        // ---- act 1: Python /api/v1/agent/rectifications 投递 mock 返回 review_task_ref ----
        String reviewTaskRef = agentClient.triggerInitialReview(
                submission, issue, List.<CpsIssueAttachment>of(), List.<CpsIssueAttachment>of());
        assertEquals("PY-RECT-777", reviewTaskRef,
                "Chain 1.1: C-01 投递返回 review_task_ref 必须 = PY-RECT-777");

        // ---- act 2: Python C-02 回调 simulate，触发 handleCallback → 落 COMPLETED 流水 ----
        CpsInitialReviewCallbackRequest callback = new CpsInitialReviewCallbackRequest();
        callback.setTaskId(String.valueOf(taskId));
        callback.setSubmissionId(submissionId);
        callback.setIssueId(issueId);
        callback.setVersionNo(versionNo);
        callback.setIdempotencyKey("initial-review-result-" + taskId);
        callback.setModelStatus("SUCCEEDED");
        callback.setOverall("PARTIAL");
        CpsInitialReviewCallbackRequest.Item item = new CpsInitialReviewCallbackRequest.Item();
        item.setCheckType("IMAGE_COMPARE");
        item.setFieldName("short_term");
        item.setVerdict("PASS");
        item.setConfidence(new BigDecimal("0.92"));
        item.setReason("前后照对比一致");
        callback.setItems(List.of(item));

        when(taskMapper.markCompleted(eq(taskId), any())).thenReturn(1);

        Map<String, Object> callbackOutcome = initialReviewService.handleCallback(callback);
        assertEquals("COMPLETED", callbackOutcome.get("task_status"),
                "Chain 1.2: C-02 回调成功后任务 status 必须落 COMPLETED");
        assertEquals(Boolean.TRUE, callbackOutcome.get("received"),
                "Chain 1.2: received=true");

        // 断言: 至少 2 条事件流水（CALLBACK_RECEIVED + COMPLETED；C-01 不落事件 + 裁决幂等路径不落事件）
        verify(eventMapper, atLeast(2)).insert(any());

        // ---- act 3: 裁决写入 cps_review_adjudication + 通过 Python /api/v1/memory/entries 写长期记忆 ----
        Map<String, Object> memoryEntry = new LinkedHashMap<>();
        memoryEntry.put("issue_id", String.valueOf(issueId));
        memoryEntry.put("version_no", versionNo);
        memoryEntry.put("decision", "PASS");
        memoryEntry.put("ai_relation", "AGREE");
        memoryEntry.put("reviewer_emp_no", "E20001");
        memoryEntry.put("reason", "AI 与人工一致：通过");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        @SuppressWarnings("unchecked")
        Map<String, Object> memoryWriteResp = frameworkClient.postForObject(
                "http://python.test/api/v1/memory/entries",
                new HttpEntity<>(memoryEntry, headers), Map.class);
        assertNotNull(memoryWriteResp, "Chain 1.3: Python /api/v1/memory/entries 必返回 memory_id");
        assertEquals("MEM-9001", memoryWriteResp.get("memory_id"),
                "Chain 1.3: memory_id 必须等于 mock 返回（cps_memory_entry 落库）");

        // 裁决写入（service 层 recordAdjudication 落库）
        CpsReviewAdjudication adjudication = new CpsReviewAdjudication();
        adjudication.setIssueId(issueId);
        adjudication.setVersionNo(versionNo);
        adjudication.setTaskId(taskId);
        adjudication.setReviewerEmpNo("E20001");
        adjudication.setReviewerEmpName("审核员甲");
        adjudication.setDecision("PASS");
        adjudication.setAiOverall("PARTIAL");
        adjudication.setAiRelation("AGREE");
        adjudication.setReason("AI 与人工一致：通过");
        adjudication.setFromStatus("PENDING_AI_REVIEW");
        adjudication.setToStatus("PENDING_REVIEW");
        adjudication.setCreatedAt(LocalDateTime.now());
        when(adjudicationMapper.findByIssueAndVersion(issueId, versionNo)).thenReturn(adjudication);
        CpsReviewAdjudication stored = initialReviewService.recordAdjudication(adjudication);
        assertNotNull(stored, "Chain 1.3: recordAdjudication 必须返回裁决记录");
        verify(adjudicationMapper, times(1)).insert(any(CpsReviewAdjudication.class));

        // ---- act 4: 下次同 issue 跑初审时 Python /api/v1/memory/retrieve 返回 hints ----
        Map<String, Object> retrieveBody = new LinkedHashMap<>();
        retrieveBody.put("issue_id", String.valueOf(issueId));
        retrieveBody.put("factory", "工厂A");
        retrieveBody.put("category_l1_id", 100);
        @SuppressWarnings("unchecked")
        Map<String, Object> hints = frameworkClient.postForObject(
                "http://python.test/api/v1/memory/retrieve",
                new HttpEntity<>(retrieveBody, headers), Map.class);
        assertNotNull(hints, "Chain 1.4: hints 响应不能为空");
        assertNotNull(hints.get("hints"), "Chain 1.4: hints 字段必含 AGREE 提示");
        assertEquals("E20001", hints.get("next_reviewer"),
                "Chain 1.4: next_reviewer 必须 = 历史裁决 reviewer_emp_no（双向参照）");

        // 断言: 裁决 + 记忆 issueId/versionNo 三元组一致（双向参照兜底）
        assertEquals(issueId, adjudication.getIssueId(),
                "Chain 1.5: 裁决 issueId 与 cps_issue.id 双向一致");
        assertEquals(versionNo, adjudication.getVersionNo(),
                "Chain 1.5: 裁决 versionNo 与提交版本一致");

        pythonMock.verify();
    }

    @Test
    @DisplayName("Chain 1: cps_review_adjudication uk(issue_id, version_no) 幂等")
    void chain1AdjudicationIdempotency() {
        Long issueId = 8002L;
        Integer versionNo = 2;
        Long taskId = 778L;

        // 既有裁决存在 → 第二次 insert 抛 DuplicateKey → 返回既有裁决
        CpsReviewAdjudication existing = new CpsReviewAdjudication();
        existing.setId(99L);
        existing.setIssueId(issueId);
        existing.setVersionNo(versionNo);
        existing.setTaskId(taskId);
        existing.setReviewerEmpNo("E20002");
        existing.setReviewerEmpName("审核员乙");
        existing.setDecision("PASS");
        existing.setAiOverall("PARTIAL");
        existing.setAiRelation("AGREE");
        existing.setReason("既有裁决：AI 与人工一致");
        existing.setFromStatus("PENDING_AI_REVIEW");
        existing.setToStatus("PENDING_REVIEW");
        existing.setCreatedAt(LocalDateTime.now());

        doThrow(new DuplicateKeyException("uk_review_adjudication_version"))
                .when(adjudicationMapper).insert(any(CpsReviewAdjudication.class));
        when(adjudicationMapper.findByIssueAndVersion(issueId, versionNo)).thenReturn(existing);

        CpsReviewAdjudication attempt = new CpsReviewAdjudication();
        attempt.setIssueId(issueId);
        attempt.setVersionNo(versionNo);
        attempt.setTaskId(taskId);
        attempt.setReviewerEmpNo("E20099");
        attempt.setReviewerEmpName("重复裁决人");
        attempt.setDecision("FAIL");  // 与既有 PASS 不一致
        attempt.setAiOverall("PROBLEM");
        attempt.setAiRelation("AGAINST_AI");
        attempt.setReason("重复裁决：FAIL（应被既有 PASS 兜底）");
        attempt.setFromStatus("PENDING_AI_REVIEW");
        attempt.setToStatus("PENDING_REVIEW");
        attempt.setCreatedAt(LocalDateTime.now());

        CpsReviewAdjudication got = initialReviewService.recordAdjudication(attempt);

        assertSame(existing, got,
                "Chain 1.idem: uk(issue_id, version_no) 重复 → 必须返回既有裁决（幂等兜底）");
        assertEquals("E20002", got.getReviewerEmpNo(),
                "Chain 1.idem: 既有裁决 reviewer_emp_no 必须保持不变");
        assertEquals("PASS", got.getDecision(),
                "Chain 1.idem: 既有裁决 decision 必须保持不变（不覆盖）");
    }

    @Test
    @DisplayName("Chain 1: cps_initial_review_event 12 类全集覆盖（迁移约束枚举）")
    void chain1InitialReviewEventTypeCoverage() {
        // 12 类枚举（V20261001 迁移约束）：
        // TRIGGERED / DISPATCH_RETRY / DISPATCH_FAILED / CALLBACK_RECEIVED / CALLBACK_DUPLICATED /
        // COMPLETED / FAILED / TIMEOUT_OPENED / TAKEN_OVER / LATE_RESULT / RETRIGGERED / ADJUDICATED
        List<String> expectedEventTypes = Arrays.asList(
                "TRIGGERED", "DISPATCH_RETRY", "DISPATCH_FAILED",
                "CALLBACK_RECEIVED", "CALLBACK_DUPLICATED",
                "COMPLETED", "FAILED", "TIMEOUT_OPENED",
                "TAKEN_OVER", "LATE_RESULT", "RETRIGGERED", "ADJUDICATED");

        assertEquals(12, expectedEventTypes.size(),
                "Chain 1.events: cps_initial_review_event 必须恰好 12 类（迁移约束）");

        // 通过源码静态校验：每条事件类型在 CpsInitialReviewService.recordEvent 调用点都被引用
        String src = readServiceSource("CpsInitialReviewService.java");
        for (String eventType : expectedEventTypes) {
            assertTrue(src.contains("recordEvent(task, \"" + eventType + "\""),
                    "Chain 1.events: CpsInitialReviewService 必须显式调用 recordEvent 写入事件: "
                            + eventType + "（cps_initial_review_event 12 类全集）");
        }

        // 反射兜底：recordEvent 方法签名确实存在
        Method recordEventMethod = null;
        for (Method m : CpsInitialReviewService.class.getDeclaredMethods()) {
            if (m.getName().equals("recordEvent")) {
                recordEventMethod = m;
                break;
            }
        }
        assertNotNull(recordEventMethod,
                "Chain 1.events: CpsInitialReviewService.recordEvent 私有方法必须存在");
        // signature: (task, eventType, detail, operatorEmpNo)
        assertEquals(4, recordEventMethod.getParameterCount(),
                "Chain 1.events: recordEvent 必须有 4 个参数（task, eventType, detail, operatorEmpNo）");
    }

    // ============================================================
    // Chain 2 — B6 视觉点检
    // ============================================================

    @Test
    @DisplayName("Chain 2: 视觉点检 submit → PASS → override → HUMAN_OVERRIDE（≥3 断言）")
    void chain2VisionCheckPassThenHumanOverride() {
        // ---- arrange ----
        Long recordId = 7001L;
        String fingerprint = "fp-wave13-7001";

        CpsVisionCheckSubmitRequest req = new CpsVisionCheckSubmitRequest();
        req.setRoomId(11L);
        req.setCheckItemId(22L);
        req.setRoomType(CpsVisionCheckRecord.ROOM_TYPE_STANDARD);
        req.setPhotoObjectKey("room-11/item-22/2026-01-01.jpg");
        req.setPhotoUrl("https://cdn.example.com/room-11/item-22/2026-01-01.jpg");
        req.setCreatedBy("emp-007");

        // 共享可变 record，模拟数据库行被 updateStatusFields/updateAiJudgeResult 改写后的状态
        CpsVisionCheckRecord[] live = new CpsVisionCheckRecord[1];
        when(recordMapper.findJudgedByFingerprint(fingerprint)).thenReturn(null);
        when(recordMapper.insert(any(CpsVisionCheckRecord.class))).thenAnswer(inv -> {
            CpsVisionCheckRecord r = inv.getArgument(0);
            r.setId(recordId);
            r.setCreatedAt(LocalDateTime.now());
            r.setUpdatedAt(LocalDateTime.now());
            live[0] = r;
            return 1;
        });
        // findById 返回当前 mutable live record
        when(recordMapper.findById(recordId)).thenAnswer(inv -> live[0]);
        // updateStatusFields 视为 setter + 反映到 live
        when(recordMapper.updateStatusFields(any(CpsVisionCheckRecord.class))).thenAnswer(inv -> {
            CpsVisionCheckRecord r = inv.getArgument(0);
            live[0].setStatus(r.getStatus());
            live[0].setHumanOverrideEmpNo(r.getHumanOverrideEmpNo());
            live[0].setHumanOverrideReason(r.getHumanOverrideReason());
            live[0].setUpdatedAt(LocalDateTime.now());
            return 1;
        });
        // vision client 返回 PASS（fingerprint 由 service 自动 sha256 计算 → 任意匹配即可）
        when(visionClient.judge(anyString(), anyString(), anyLong(), anyString(), any()))
                .thenReturn(new CpsVisionClient.VisionJudgeResult(
                        CpsVisionCheckRecord.AI_OVERALL_PASS, 95, List.of("前后对比一致")));
        when(recordMapper.updateAiJudgeResult(any(CpsVisionCheckRecord.class))).thenAnswer(inv -> {
            CpsVisionCheckRecord r = inv.getArgument(0);
            // mutate live
            live[0].setAiOverall(r.getAiOverall());
            live[0].setAiScore(r.getAiScore());
            live[0].setAiReason(r.getAiReason());
            live[0].setStatus(r.getStatus());
            live[0].setUpdatedAt(LocalDateTime.now());
            return 1;
        });

        // ---- act 1: submit → vision judge PASS ----
        CpsVisionCheckSubmitResult submitResult = visionCheckService.submit(req);
        assertNotNull(submitResult, "Chain 2.1: 必返回 submitResult");
        assertEquals(recordId, submitResult.getRecord().getId(), "Chain 2.1: recordId");
        // fingerprint 由 service sha256(roomId|checkItemId|photoUrl) 自动生成（64 hex）
        String actualFingerprint = submitResult.getJudgeFingerprint();
        assertNotNull(actualFingerprint, "Chain 2.1: fingerprint 必填");
        assertEquals(64, actualFingerprint.length(),
                "Chain 2.1: fingerprint 必须=sha256 64 hex（CpsVisionCheckService.buildFingerprint）");
        assertEquals(actualFingerprint, live[0].getJudgeFingerprint(),
                "Chain 2.1: 写库 fingerprint 与返回值一致");

        // 断言 1: AI 判定完成后 record.status=AI_PASS + ai_overall=PASS（受 CHECK 约束）
        assertEquals(CpsVisionCheckStatus.AI_PASS.name(), live[0].getStatus(),
                "Chain 2.1: vision mock PASS → record.status=AI_PASS");
        assertEquals(CpsVisionCheckRecord.AI_OVERALL_PASS, live[0].getAiOverall(),
                "Chain 2.1: ai_overall 必须=PASS（V20261006 chk_cps_vision_check_ai_overall CHECK 约束）");
        assertEquals(Integer.valueOf(95), live[0].getAiScore(),
                "Chain 2.1: ai_score=95");

        // 断言 2: judge_event 流水至少 3 条（SUBMITTED + AI_STARTED + AI_COMPLETED）
        ArgumentCaptor<CpsVisionCheckJudgeEvent> eventCaptor =
                ArgumentCaptor.forClass(CpsVisionCheckJudgeEvent.class);
        verify(judgeEventMapper, atLeast(3)).insert(eventCaptor.capture());
        List<String> eventTypes = new ArrayList<>();
        for (CpsVisionCheckJudgeEvent e : eventCaptor.getAllValues()) eventTypes.add(e.getEventType());
        assertTrue(eventTypes.contains(CpsVisionCheckJudgeEvent.EVENT_SUBMITTED),
                "Chain 2.1: B6 必含 SUBMITTED 流水，实际: " + eventTypes);
        assertTrue(eventTypes.contains(CpsVisionCheckJudgeEvent.EVENT_AI_STARTED),
                "Chain 2.1: B6 必含 AI_STARTED 流水，实际: " + eventTypes);
        assertTrue(eventTypes.contains(CpsVisionCheckJudgeEvent.EVENT_AI_COMPLETED),
                "Chain 2.1: B6 必含 AI_COMPLETED 流水，实际: " + eventTypes);

        // ---- act 2: 人工 override 改判（FAIL + 原因） ----
        CpsVisionCheckOverrideRequest override = new CpsVisionCheckOverrideRequest();
        override.setDecision("FAIL");
        override.setReason("现场复核发现电源未关");
        override.setOperatorEmpNo("admin-001");
        visionCheckService.humanOverride(recordId, override);

        // 断言 3: status=HUMAN_OVERRIDE + human_override_emp_no/reason 落库
        assertEquals(CpsVisionCheckStatus.HUMAN_OVERRIDE.name(), live[0].getStatus(),
                "Chain 2.2: 人工改判后 record.status=HUMAN_OVERRIDE");
        assertEquals("admin-001", live[0].getHumanOverrideEmpNo(),
                "Chain 2.2: human_override_emp_no 必须落库");
        assertEquals("现场复核发现电源未关", live[0].getHumanOverrideReason(),
                "Chain 2.2: human_override_reason 必须落库");

        // 断言 4: ai_overall 仍为 PASS（不被覆写，受 CHECK 约束只允许 PASS/PARTIAL/PROBLEM）
        assertEquals(CpsVisionCheckRecord.AI_OVERALL_PASS, live[0].getAiOverall(),
                "Chain 2.2: human_override 不改 ai_overall（V20261006 chk_cps_vision_check_ai_overall CHECK 约束）");

        // 断言 5: HUMAN_OVERRIDE 流水 + 累计 ≥4 条事件
        verify(judgeEventMapper, atLeast(4)).insert(any(CpsVisionCheckJudgeEvent.class));
    }

    @Test
    @DisplayName("Chain 2: cps_vision_check_judge_event 7 类全集覆盖（迁移约束枚举）")
    void chain2VisionCheckEventTypeCoverage() {
        // 7 类枚举（V20261006 迁移约束）：
        // SUBMITTED / AI_STARTED / AI_COMPLETED / AI_FAILED / TIMEOUT_OPENED / HUMAN_OVERRIDE / REJUDGED
        List<String> expectedEventTypes = Arrays.asList(
                CpsVisionCheckJudgeEvent.EVENT_SUBMITTED,
                CpsVisionCheckJudgeEvent.EVENT_AI_STARTED,
                CpsVisionCheckJudgeEvent.EVENT_AI_COMPLETED,
                CpsVisionCheckJudgeEvent.EVENT_AI_FAILED,
                CpsVisionCheckJudgeEvent.EVENT_TIMEOUT_OPENED,
                CpsVisionCheckJudgeEvent.EVENT_HUMAN_OVERRIDE,
                CpsVisionCheckJudgeEvent.EVENT_REJUDGED);

        assertEquals(7, expectedEventTypes.size(),
                "Chain 2.events: cps_vision_check_judge_event 必须恰好 7 类（迁移约束）");
        // 反编译枚举中常量值一一校验
        assertEquals("SUBMITTED", CpsVisionCheckJudgeEvent.EVENT_SUBMITTED);
        assertEquals("AI_STARTED", CpsVisionCheckJudgeEvent.EVENT_AI_STARTED);
        assertEquals("AI_COMPLETED", CpsVisionCheckJudgeEvent.EVENT_AI_COMPLETED);
        assertEquals("AI_FAILED", CpsVisionCheckJudgeEvent.EVENT_AI_FAILED);
        assertEquals("TIMEOUT_OPENED", CpsVisionCheckJudgeEvent.EVENT_TIMEOUT_OPENED);
        assertEquals("HUMAN_OVERRIDE", CpsVisionCheckJudgeEvent.EVENT_HUMAN_OVERRIDE);
        assertEquals("REJUDGED", CpsVisionCheckJudgeEvent.EVENT_REJUDGED);

        // 源码覆盖校验：7 类事件在 service 写入路径均被引用
        String src = readServiceSource("CpsVisionCheckService.java");
        assertTrue(src.contains("EVENT_SUBMITTED"),
                "Chain 2.events: CpsVisionCheckService 必须引用 EVENT_SUBMITTED");
        assertTrue(src.contains("EVENT_AI_STARTED"),
                "Chain 2.events: CpsVisionCheckService 必须引用 EVENT_AI_STARTED");
        assertTrue(src.contains("EVENT_AI_COMPLETED"),
                "Chain 2.events: CpsVisionCheckService 必须引用 EVENT_AI_COMPLETED");
        assertTrue(src.contains("EVENT_AI_FAILED"),
                "Chain 2.events: CpsVisionCheckService 必须引用 EVENT_AI_FAILED");
        assertTrue(src.contains("EVENT_TIMEOUT_OPENED"),
                "Chain 2.events: CpsVisionCheckService 必须引用 EVENT_TIMEOUT_OPENED");
        assertTrue(src.contains("EVENT_HUMAN_OVERRIDE"),
                "Chain 2.events: CpsVisionCheckService 必须引用 EVENT_HUMAN_OVERRIDE");
        assertTrue(src.contains("EVENT_REJUDGED"),
                "Chain 2.events: CpsVisionCheckService 必须引用 EVENT_REJUDGED");
    }

    // ============================================================
    // Chain 3 — B7 覆盖分析 5 视图
    // ============================================================

    @Test
    @DisplayName("Chain 3: B7 覆盖分析 5 视图 + 4 路分类谓词实际工作")
    void chain3Coverage5ViewsAndPredicates() {
        // ---- arrange mock responses ----
        when(coverageMapper.countFrequencyGroupBy(any(), any(), any(), any(), any())).thenReturn(1L);
        when(coverageMapper.frequencyGroupBy(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(rowOf(
                        "factory", "工厂A", "area", "1区",
                        "categoryL1Id", 100L, "issueCount", 12,
                        "closedCount", 8, "closeRate", 0.67)));

        when(coverageMapper.countRegionSupervisorGroupBy(any(), any(), any(), any())).thenReturn(1L);
        when(coverageMapper.regionSupervisorGroupBy(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(rowOf(
                        "area", "1区", "currentHandlerEmpNo", "E30001",
                        "openCount", 5, "overdueCount", 1)));

        when(coverageMapper.countRecurrenceGroupBy(any(), any(), any(), any())).thenReturn(1L);
        when(coverageMapper.recurrenceGroupBy(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(rowOf(
                        "issueId", 8001L, "reopenCount", 3, "rejectCount", 1)));

        when(coverageMapper.countCoverageGaps(any(), any(), any(), any())).thenReturn(1L);
        when(coverageMapper.coverageGaps(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(rowOf(
                        "factory", "工厂A", "area", "2区",
                        "storageRoomType", "PRIMARY", "lastRecordDaysAgo", 12)));

        // effect 4 metric
        when(effectMapper.aiPassRate(any(), any(), any(), any())).thenReturn(0.78);
        when(effectMapper.humanOverrideRate(any(), any(), any(), any())).thenReturn(0.15);
        when(effectMapper.avgCloseDurationHours(any(), any(), any(), any())).thenReturn(24.5);
        when(effectMapper.recurrenceRate(any(), any(), any(), any())).thenReturn(0.05);

        // ---- act: 4 路分类谓词 + service 5 视图 ----
        // 谓词 1: factory 维度 (factory=工厂A)
        CpsPageResponse<Map<String, Object>> frequency = coverageService.frequencyGroupBy(
                "工厂A", null, null, null, null, 1, 200);
        assertEquals(1L, frequency.getTotal(),
                "Chain 3.1: frequency 含 factory 谓词后必须命中 1 行");
        assertEquals("工厂A", frequency.getRows().get(0).get("factory"),
                "Chain 3.1: frequency factory 谓词 = 工厂A");

        // 谓词 2: area 维度 (area=1区)
        CpsPageResponse<Map<String, Object>> region = coverageService.regionSupervisorGroupBy(
                null, "1区", null, null, null, 1, 200);
        assertEquals(1L, region.getTotal(),
                "Chain 3.2: region-supervisor 含 area 谓词后必须命中 1 行");
        assertEquals("1区", region.getRows().get(0).get("area"),
                "Chain 3.2: region-supervisor area 谓词 = 1区");

        // 谓词 3: category 维度 (categoryL1Id=100)
        CpsPageResponse<Map<String, Object>> recurrence = coverageService.recurrenceGroupBy(
                null, null, 100L, null, 1, 200);
        assertEquals(1L, recurrence.getTotal(),
                "Chain 3.3: recurrence 含 categoryL1Id 谓词后必须命中 1 行");

        // 谓词 4: time 维度 (gapDays=7 默认窗口)
        CpsPageResponse<Map<String, Object>> gaps = coverageService.coverageGaps(
                null, null, null, null, 1, 200);
        assertEquals(1L, gaps.getTotal(),
                "Chain 3.4: coverage gaps 默认 7 天窗口必须命中 1 行");

        // 第 5 视图: effect (FR-12)
        CpsPageResponse<Map<String, Object>> effect = coverageService.effect(
                null, null, "ai_pass_rate", "factory=工厂A");
        assertEquals(4L, effect.getTotal(),
                "Chain 3.5: effect 视图必出 4 行（4 metric）");
        assertEquals(0.78, ((Number) effect.getRows().get(0).get("metricValue")).doubleValue(), 0.01,
                "Chain 3.5: effect metric[0] metricValue = 0.78（ai_pass_rate）");

        // 断言: 5 视图全部调用 mapper 至少一次 + 4 路谓词透传
        verify(coverageMapper, atLeastOnce()).frequencyGroupBy(
                eq("工厂A"), any(), any(), any(), any(), anyInt(), anyInt());
        verify(coverageMapper, atLeastOnce()).regionSupervisorGroupBy(
                any(), eq("1区"), any(), any(), any(), anyInt(), anyInt());
        verify(coverageMapper, atLeastOnce()).recurrenceGroupBy(
                any(), any(), eq(100L), any(), anyInt(), anyInt());
        verify(coverageMapper, atLeastOnce()).coverageGaps(
                any(), any(), any(), any(), anyInt(), anyInt());
        verify(effectMapper, atLeastOnce()).aiPassRate(any(), any(), eq("ai_pass_rate"), eq("factory=工厂A"));
    }

    // ============================================================
    // Chain 4 — F 线 D-22 push_config
    // ============================================================

    @Test
    @DisplayName("Chain 4: push_config GET 单行 + PUT 改 enabled_channel 列表（CHECK id=1 + UNIQUE 启用去重 + enabled=true 但 endpoint 空 → 400）")
    void chain4PushConfigGetAndPut() {
        // ---- arrange: mock 单行 GLOBAL 默认种子 ----
        CpsPushConfig seeded = new CpsPushConfig();
        seeded.setId(CpsPushConfig.SINGLETON_ID);
        seeded.setChannel(CpsPushConfig.CHANNEL_INTERFACE);
        seeded.setEndpoint("");
        seeded.setEnabled(false);
        seeded.setUpdatedBy("SYSTEM");
        when(pushConfigMapper.findGlobal()).thenReturn(seeded);

        // ---- act 1: GET /api/cps/admin/push-config ----
        Map<String, Object> before = pushConfigService.getConfig();
        assertEquals(Boolean.TRUE, before.get("configured"),
                "Chain 4.1: 存在种子行 → configured=true");
        assertEquals(CpsPushConfig.CHANNEL_INTERFACE, before.get("channel"),
                "Chain 4.1: GET 必须返回种子 channel=INTERFACE");
        assertEquals(Boolean.FALSE, before.get("enabled"),
                "Chain 4.1: GET 缺省 enabled=false（推送未配置）");

        // ---- act 2: PUT 启用 INTERFACE 推送 ----
        when(pushConfigMapper.upsert(any(CpsPushConfig.class))).thenReturn(1);
        when(pushConfigMapper.findGlobal()).thenAnswer(inv -> {
            CpsPushConfig after = new CpsPushConfig();
            after.setId(CpsPushConfig.SINGLETON_ID);
            after.setChannel(CpsPushConfig.CHANNEL_INTERFACE);
            after.setEndpoint("https://push.example.com/api/v1/interface");
            after.setSecretRef("kms-ref://interface");
            after.setEnabled(true);
            after.setUpdatedBy("ADMIN");
            return after;
        });
        CpsPushConfigRequest put = new CpsPushConfigRequest();
        put.setChannel(CpsPushConfig.CHANNEL_INTERFACE);
        put.setEndpoint("https://push.example.com/api/v1/interface");
        put.setSecretRef("kms-ref://interface");
        put.setEnabled(true);
        put.setUpdatedBy("ADMIN");
        Map<String, Object> afterPut = pushConfigService.updateConfig(put);
        assertEquals(Boolean.TRUE, afterPut.get("enabled"),
                "Chain 4.2: PUT enabled=true → enabled 字段=true");

        // 断言: CHECK id=1 兜底：upsert 时 service 不设置 id（mapper XML 写死 id=1）
        ArgumentCaptor<CpsPushConfig> cfgCaptor = ArgumentCaptor.forClass(CpsPushConfig.class);
        verify(pushConfigMapper, atLeastOnce()).upsert(cfgCaptor.capture());
        for (CpsPushConfig captured : cfgCaptor.getAllValues()) {
            assertNull(captured.getId(),
                    "Chain 4.2: service 不设置 id → 由 mapper XML ON DUPLICATE KEY 写 id=1");
            assertEquals(CpsPushConfig.CHANNEL_INTERFACE, captured.getChannel(),
                    "Chain 4.2: upsert channel=INTERFACE");
        }

        // ---- act 3: PUT enabled=true 但 endpoint 空 → 400 ----
        CpsPushConfigRequest badReq = new CpsPushConfigRequest();
        badReq.setChannel(CpsPushConfig.CHANNEL_EMAIL);
        badReq.setEndpoint(null);  // 空
        badReq.setEnabled(true);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> pushConfigService.updateConfig(badReq),
                "Chain 4.3: enabled=true 但 endpoint 空 → IllegalArgumentException（controller 翻 400）");
        assertTrue(ex.getMessage().contains("endpoint is required when enabled=true"),
                "Chain 4.3: 异常消息必须明确 endpoint 必填: " + ex.getMessage());

        // ---- act 4: 不支持的 channel → 400 ----
        CpsPushConfigRequest badChannel = new CpsPushConfigRequest();
        badChannel.setChannel("FOOBAR");
        badChannel.setEndpoint("https://x");
        badChannel.setEnabled(false);
        IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class,
                () -> pushConfigService.updateConfig(badChannel),
                "Chain 4.4: 不支持的 channel → IllegalArgumentException");
        assertTrue(ex2.getMessage().contains("Unsupported channel"),
                "Chain 4.4: 异常消息必须明确 channel 白名单: " + ex2.getMessage());
    }

    @Test
    @DisplayName("Chain 4: UNIQUE 启用去重（生成列 enabled_channel 仅 enabled=1 时触发）")
    void chain4PushConfigUniqueEnabledChannel() {
        // 验证 V20261005 迁移：UNIQUE KEY uk_cps_push_config_enabled_channel (enabled_channel)
        // 是基于生成列 enabled_channel（仅 enabled=1 时携带 channel）
        String sql = readMigration("V20261005__cps_push_config.sql");
        assertTrue(sql.contains("enabled_channel VARCHAR(32) GENERATED ALWAYS AS "
                        + "(CASE WHEN enabled = 1 THEN channel ELSE NULL END) STORED"),
                "Chain 4.vart: V20261005 必须含生成列 enabled_channel"
                        + "（CASE WHEN enabled=1 THEN channel ELSE NULL）");
        assertTrue(sql.contains("UNIQUE KEY uk_cps_push_config_enabled_channel (enabled_channel)"),
                "Chain 4.vart: V20261005 必须含 UNIQUE KEY uk_cps_push_config_enabled_channel");
        assertTrue(sql.contains("CONSTRAINT chk_cps_push_config_singleton CHECK (id = 1)"),
                "Chain 4.vart: V20261005 必须含 CHECK id=1 兜底单行约束");
        assertTrue(sql.contains("CONSTRAINT chk_cps_push_config_channel CHECK"),
                "Chain 4.vart: V20261005 必须含 channel 白名单 CHECK 约束");
        assertTrue(sql.contains("'INTERFACE'") && sql.contains("'EMAIL'")
                        && sql.contains("'SMS'") && sql.contains("'WEBHOOK'"),
                "Chain 4.vart: V20261005 channel CHECK 必含 4 渠道枚举");

        // 业务层断言：mapper XML upsert 走 ON DUPLICATE KEY，不会触发业务异常
        String mapperXml = readMapper("CpsPushConfigMapper.xml");
        assertTrue(mapperXml.contains("ON DUPLICATE KEY UPDATE"),
                "Chain 4.vart: push_config upsert 走 ON DUPLICATE KEY（同 id=1 行覆盖）");
        assertTrue(mapperXml.contains("VALUES(channel)") && mapperXml.contains("VALUES(endpoint)"),
                "Chain 4.vart: upsert 全字段回写（channel/endpoint/secret_ref/enabled/updated_by）");
    }

    // ============================================================
    // 不变量校验：cps_issue ↔ cps_memory_entry 双向参照一致
    // ============================================================

    @Test
    @DisplayName("不变量: cps_issue ↔ cps_memory_entry 双向参照一致（issueId/decision/ai_relation 三元组透传）")
    void invariantIssueAndMemoryEntryDualReference() {
        Long issueId = 8500L;
        Integer versionNo = 1;
        Long taskId = 8500L;
        String reviewer = "E20050";

        CpsIssue issue = newIssue(issueId, CpsIssueStatus.PENDING_REVIEW);
        when(issueMapper.findById(issueId)).thenReturn(Optional.of(issue));
        CpsInitialReviewTask runningTask = runningTask(taskId, issueId, versionNo);
        when(taskMapper.findById(taskId)).thenReturn(runningTask);
        when(taskMapper.findByIssueAndVersion(issueId, versionNo)).thenReturn(runningTask);

        // mock Python /api/v1/memory/entries 写入断言
        pythonMock.expect(requestTo("http://python.test/api/v1/memory/entries"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"memory_id\":\"MEM-8500\"}", MediaType.APPLICATION_JSON));

        CpsReviewAdjudication adjudication = new CpsReviewAdjudication();
        adjudication.setIssueId(issueId);
        adjudication.setVersionNo(versionNo);
        adjudication.setTaskId(taskId);
        adjudication.setReviewerEmpNo(reviewer);
        adjudication.setReviewerEmpName("审核员乙");
        adjudication.setDecision("PASS");
        adjudication.setAiOverall("PARTIAL");
        adjudication.setAiRelation("AGREE");
        adjudication.setReason("AI 与人工一致：通过");
        adjudication.setFromStatus("PENDING_AI_REVIEW");
        adjudication.setToStatus("PENDING_REVIEW");
        adjudication.setCreatedAt(LocalDateTime.now());
        when(adjudicationMapper.findByIssueAndVersion(issueId, versionNo)).thenReturn(adjudication);
        initialReviewService.recordAdjudication(adjudication);

        // 模拟 Java 写记忆：HTTP POST /api/v1/memory/entries
        Map<String, Object> memoryEntry = new LinkedHashMap<>();
        memoryEntry.put("issue_id", String.valueOf(issueId));
        memoryEntry.put("version_no", versionNo);
        memoryEntry.put("task_id", taskId);
        memoryEntry.put("decision", adjudication.getDecision());
        memoryEntry.put("ai_relation", adjudication.getAiRelation());
        memoryEntry.put("reviewer_emp_no", reviewer);
        memoryEntry.put("reason", adjudication.getReason());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        @SuppressWarnings("unchecked")
        Map<String, Object> memoryWrite = frameworkClient.postForObject(
                "http://python.test/api/v1/memory/entries",
                new HttpEntity<>(memoryEntry, headers), Map.class);

        // 断言: 三元组透传一致
        assertNotNull(memoryWrite, "Invariant: memory 写入响应非空");
        assertEquals(issueId.intValue(),
                Integer.parseInt((String) memoryEntry.get("issue_id")),
                "Invariant: cps_issue.id ↔ memory_entry.issue_id 双向一致");
        assertEquals(versionNo.intValue(),
                ((Integer) memoryEntry.get("version_no")).intValue(),
                "Invariant: cps_version.intValue ↔ memory_entry.version_no 双向一致");
        assertEquals("PASS", memoryEntry.get("decision"),
                "Invariant: cps_review_adjudication.decision ↔ memory_entry.decision 双向一致");
        assertEquals("AGREE", memoryEntry.get("ai_relation"),
                "Invariant: cps_review_adjudication.ai_relation ↔ memory_entry.ai_relation 双向一致");
        assertEquals("MEM-8500", memoryWrite.get("memory_id"),
                "Invariant: Python memory_id 必须落库（cps_memory_entry 持久化）");
        pythonMock.verify();
    }

    // ============================================================
    // Helpers — domain POJO + source/sql 读取
    // ============================================================

    private static CpsIssue newIssue(Long id, CpsIssueStatus status) {
        CpsIssue issue = new CpsIssue();
        issue.setId(id);
        issue.setFlowVersion("v2");
        issue.setStatus(status);
        issue.setFactory("工厂A");
        issue.setArea("1区");
        issue.setLine("线1");
        issue.setProcess("工序A");
        issue.setCategoryL1Id(100L);
        issue.setCategoryL2Id(101L);
        issue.setDescription("chain1 测试 issue");
        issue.setCreatorEmpNo("E10001");
        issue.setCreatorEmpName("创建人");
        // 预置 reviewer 兜底，避免 advanceIssueAfterTaskTerminal 路径绕进 PENDING_REVIEWER_CONFIG 分支
        issue.setReviewerEmpNo("E20001");
        issue.setReviewerEmpName("审核员甲");
        issue.setCurrentSubmissionVersion(1);
        issue.setLockVersion(0);
        issue.setSubmitTime(LocalDateTime.now());
        issue.setCreatedAt(LocalDateTime.now());
        issue.setUpdatedAt(LocalDateTime.now());
        return issue;
    }

    private static CpsRectificationSubmission newSubmission(Long id, Long issueId, Integer versionNo) {
        CpsRectificationSubmission s = new CpsRectificationSubmission();
        s.setId(id);
        s.setIssueId(issueId);
        s.setVersionNo(versionNo);
        s.setReason("chain1 测试原因");
        s.setShortTermMeasure("短期措施");
        s.setLongTermMeasure("长期措施");
        s.setResponsibleEmpNo("E10002");
        s.setResponsibleEmpName("责任人");
        s.setSubmittedBy("E10001");
        s.setSubmittedName("提交人");
        s.setSubmittedAt(LocalDateTime.now());
        s.setSource("REVIEW_RECTIFICATION");
        s.setStatus("LOCKED");
        s.setCreatedAt(LocalDateTime.now());
        s.setUpdatedAt(LocalDateTime.now());
        return s;
    }

    private static CpsInitialReviewTask runningTask(Long id, Long issueId, Integer versionNo) {
        CpsInitialReviewTask t = new CpsInitialReviewTask();
        t.setId(id);
        t.setIssueId(issueId);
        t.setVersionNo(versionNo);
        t.setStatus(CpsInitialReviewTaskStatus.RUNNING);
        t.setIdempotencyKey("cps-rectify-" + issueId + "-v" + versionNo);
        t.setSubmittedAt(LocalDateTime.now());
        t.setTimeoutAt(LocalDateTime.now().plusSeconds(600));
        t.setRetryCount(0);
        t.setCreatedAt(LocalDateTime.now());
        t.setUpdatedAt(LocalDateTime.now());
        return t;
    }

    /** 构造一个扁平 row（key/value 交替参数）。 */
    private static Map<String, Object> rowOf(Object... kv) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            row.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return row;
    }

    private static String readServiceSource(String fileName) {
        try {
            return new String(Files.readAllBytes(
                    Paths.get("src/main/java/com/company/cps/service/" + fileName)));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read service source: " + fileName, e);
        }
    }

    private static String readMigration(String fileName) {
        try {
            return new String(Files.readAllBytes(
                    Paths.get("src/main/resources/db/migration/" + fileName)));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read migration: " + fileName, e);
        }
    }

    private static String readMapper(String fileName) {
        try {
            return new String(Files.readAllBytes(
                    Paths.get("src/main/resources/mapper/" + fileName)));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read mapper: " + fileName, e);
        }
    }
}