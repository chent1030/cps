package com.company.cps.service;

import com.company.cps.domain.CpsInspectionPlan;
import com.company.cps.domain.CpsInspectionPlanStatus;
import com.company.cps.domain.CpsInspectionPlanTask;
import com.company.cps.domain.CpsInspectionPlanTaskType;
import com.company.cps.dto.CpsInspectionPlanApproveRequest;
import com.company.cps.dto.CpsInspectionPlanRejectRequest;
import com.company.cps.dto.CpsInspectionPlanRequest;
import com.company.cps.mapper.CpsInspectionPlanMapper;
import com.company.cps.mapper.CpsInspectionPlanTaskMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** D1 草稿 + D2 审核 + D3 建单引擎（AC-30 幂等）：approve 重放只产 3 任务。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CpsInspectionPlanServiceTest {

    @Mock private CpsInspectionPlanMapper planMapper;
    @Mock private CpsInspectionPlanTaskMapper taskMapper;
    @Mock private CpsAgentFrameworkClient agentClient;

    private CpsInspectionPlanService service;

    @BeforeEach
    void setUp() {
        service = new CpsInspectionPlanService(planMapper, taskMapper, agentClient, new ObjectMapper());
    }

    private static CpsInspectionPlanRequest baseDraft(String sourceRunId) {
        CpsInspectionPlanRequest r = new CpsInspectionPlanRequest();
        r.setSourceRunId(sourceRunId);
        r.setPlanType("WEEKLY_RECTIFY");
        r.setTitle("9月第3周整改复查");
        r.setRiskBasis("周报指向重复发生");
        return r;
    }

    private static CpsInspectionPlan plan(long id, String sourceRunId, CpsInspectionPlanStatus status,
                                          int lockVersion, int configVersion) {
        CpsInspectionPlan p = new CpsInspectionPlan();
        p.setId(id);
        p.setSourceRunId(sourceRunId);
        p.setPlanType("WEEKLY_RECTIFY");
        p.setTitle("9月第3周整改复查");
        p.setStatus(status);
        p.setLockVersion(lockVersion);
        p.setConfigVersion(configVersion);
        p.setDraftContentJson("{\"tasks\":{}}");
        return p;
    }

    @Test
    void applyDraftInsertsWhenAbsent() {
        CpsInspectionPlanRequest r = baseDraft("weekly-RECTIFY-20260920");
        r.setDraftContentJson("{\"tasks\":{}}"); // 预填避免触发 C-07
        when(planMapper.findBySourceRunIdAndPlanType("weekly-RECTIFY-20260920", "WEEKLY_RECTIFY"))
                .thenReturn(java.util.Optional.empty());
        when(planMapper.insert(any(CpsInspectionPlan.class))).thenAnswer(inv -> {
            CpsInspectionPlan p = inv.getArgument(0);
            p.setId(101L);
            return 1;
        });
        when(planMapper.findById(101L)).thenReturn(plan(101L, "weekly-RECTIFY-20260920",
                CpsInspectionPlanStatus.PENDING_REVIEW, 0, 1));

        CpsInspectionPlan result = service.applyDraft(r);

        assertNotNull(result.getId());
        assertEquals(CpsInspectionPlanStatus.PENDING_REVIEW, result.getStatus());
        assertEquals(1, result.getConfigVersion());
        assertEquals(0, result.getLockVersion());
        verify(agentClient, never()).requestInspectionPlanDraft(anyString(), anyString(), anyString(), any(), any(), any());
    }

    @Test
    void applyDraftCallsC07WhenNoDraftContent() {
        CpsInspectionPlanRequest r = baseDraft("weekly-PATROL-20260920");
        r.setPlanType("WEEKLY_PATROL");
        Map<String, Object> agentDraft = new HashMap<>();
        agentDraft.put("tasks", Collections.emptyMap());
        when(planMapper.findBySourceRunIdAndPlanType("weekly-PATROL-20260920", "WEEKLY_PATROL"))
                .thenReturn(java.util.Optional.empty());
        when(agentClient.requestInspectionPlanDraft(eq("weekly-PATROL-20260920"), anyString(), anyString(),
                any(), any(), any())).thenReturn(agentDraft);
        when(planMapper.insert(any(CpsInspectionPlan.class))).thenAnswer(inv -> {
            CpsInspectionPlan p = inv.getArgument(0);
            p.setId(202L);
            return 1;
        });
        when(planMapper.findById(202L)).thenReturn(plan(202L, "weekly-PATROL-20260920",
                CpsInspectionPlanStatus.PENDING_REVIEW, 0, 1));

        service.applyDraft(r);

        verify(agentClient, times(1)).requestInspectionPlanDraft(anyString(), anyString(), anyString(), any(), any(), any());
        ArgumentCaptor<CpsInspectionPlan> captor = ArgumentCaptor.forClass(CpsInspectionPlan.class);
        verify(planMapper).insert(captor.capture());
        assertTrue(captor.getValue().getDraftContentJson().contains("tasks"));
    }

    @Test
    void applyDraftUpdatesExistingPendingReview() {
        CpsInspectionPlanRequest r = baseDraft("weekly-CHECK-20260920");
        r.setPlanType("WEEKLY_CHECK");
        CpsInspectionPlan existing = plan(303L, "weekly-CHECK-20260920",
                CpsInspectionPlanStatus.PENDING_REVIEW, 0, 1);
        existing.setDraftContentJson("{\"old\":true}");
        when(planMapper.findBySourceRunIdAndPlanType("weekly-CHECK-20260920", "WEEKLY_CHECK"))
                .thenReturn(java.util.Optional.of(existing));
        when(planMapper.updateDraftCas(any(CpsInspectionPlan.class))).thenReturn(1);
        when(planMapper.findById(303L)).thenReturn(existing);

        service.applyDraft(r);

        verify(planMapper).updateDraftCas(any(CpsInspectionPlan.class));
        verify(planMapper, never()).insert(any(CpsInspectionPlan.class));
    }

    @Test
    void applyDraftRejectsBlankTitle() {
        CpsInspectionPlanRequest r = baseDraft("any");
        r.setTitle("   ");
        assertThrows(IllegalArgumentException.class, () -> service.applyDraft(r));
    }

    @Test
    void approveCreatesThreeTasksAndRejectsReplayOnAlreadyApproved() {
        long planId = 11L;
        // 顺序 stub：第一次 findById → PENDING_REVIEW，approveCas 成功 → 后续 findById → APPROVED
        CpsInspectionPlan pending = plan(planId, "weekly-RECTIFY-20260920",
                CpsInspectionPlanStatus.PENDING_REVIEW, 0, 1);
        CpsInspectionPlan approved = plan(planId, "weekly-RECTIFY-20260920",
                CpsInspectionPlanStatus.APPROVED, 1, 1);
        when(planMapper.findById(planId)).thenReturn(pending, approved);
        when(planMapper.approveCas(any(CpsInspectionPlan.class))).thenReturn(1);
        when(taskMapper.findExistingTaskTypes(planId)).thenReturn(Collections.emptyList());

        CpsInspectionPlanApproveRequest req = new CpsInspectionPlanApproveRequest();
        req.setApprover("E10001");
        req.setApproverName("张三");
        req.setLockVersion(0);

        // 首次批准 → 期望 3 个新建
        CpsInspectionPlanService.ApproveResult first = service.approve(planId, req);
        assertEquals(3, first.getCreatedTasks().size());
        verify(taskMapper, times(3)).insert(any(CpsInspectionPlanTask.class));

        // 重放：plan 已是 APPROVED，service 应拒绝
        CpsInspectionPlanApproveRequest replayReq = new CpsInspectionPlanApproveRequest();
        replayReq.setApprover("E10001");
        replayReq.setApproverName("张三");
        replayReq.setLockVersion(1);
        assertThrows(IllegalStateException.class, () -> service.approve(planId, replayReq));
    }

    @Test
    void approveOnAlreadyApprovedThrows() {
        long planId = 12L;
        when(planMapper.findById(planId)).thenReturn(
                plan(planId, "x", CpsInspectionPlanStatus.APPROVED, 1, 1));
        CpsInspectionPlanApproveRequest req = new CpsInspectionPlanApproveRequest();
        req.setApprover("E10001");
        assertThrows(IllegalStateException.class, () -> service.approve(planId, req));
        verify(planMapper, never()).approveCas(any(CpsInspectionPlan.class));
    }

    @Test
    void approveCasFailureThrows() {
        long planId = 13L;
        when(planMapper.findById(planId)).thenReturn(
                plan(planId, "x", CpsInspectionPlanStatus.PENDING_REVIEW, 0, 1));
        when(planMapper.approveCas(any(CpsInspectionPlan.class))).thenReturn(0);
        CpsInspectionPlanApproveRequest req = new CpsInspectionPlanApproveRequest();
        req.setApprover("E10001");
        assertThrows(IllegalStateException.class, () -> service.approve(planId, req));
    }

    @Test
    void rejectRequiresReason() {
        CpsInspectionPlanRejectRequest req = new CpsInspectionPlanRejectRequest();
        req.setApprover("E10001");
        req.setReason("  ");
        // service 先校验 reason 空抛 IllegalArgumentException，不查 mapper
        assertThrows(IllegalArgumentException.class, () -> service.reject(14L, req));
    }

    @Test
    void rejectTransitionsToRejected() {
        long planId = 15L;
        CpsInspectionPlan pending = plan(planId, "x", CpsInspectionPlanStatus.PENDING_REVIEW, 0, 1);
        CpsInspectionPlan rejected = plan(planId, "x", CpsInspectionPlanStatus.REJECTED, 1, 1);
        rejected.setRejectReason("证据不足");
        when(planMapper.findById(planId)).thenReturn(pending, rejected);
        when(planMapper.rejectCas(any(CpsInspectionPlan.class))).thenReturn(1);

        CpsInspectionPlanRejectRequest req = new CpsInspectionPlanRejectRequest();
        req.setApprover("E10001");
        req.setReason("证据不足");
        req.setLockVersion(0);

        CpsInspectionPlan result = service.reject(planId, req);
        assertEquals(CpsInspectionPlanStatus.REJECTED, result.getStatus());
        verify(planMapper, never()).approveCas(any(CpsInspectionPlan.class));
        verify(taskMapper, never()).findExistingTaskTypes(anyLong());
    }

    @Test
    void listByStatusReturnsAllForNull() {
        when(planMapper.findByStatus(CpsInspectionPlanStatus.PENDING_REVIEW)).thenReturn(
                Collections.singletonList(plan(1L, "a", CpsInspectionPlanStatus.PENDING_REVIEW, 0, 1)));
        when(planMapper.findByStatus(CpsInspectionPlanStatus.APPROVED)).thenReturn(Collections.emptyList());
        when(planMapper.findByStatus(CpsInspectionPlanStatus.REJECTED)).thenReturn(Collections.emptyList());

        List<CpsInspectionPlan> all = service.listByStatus(null);
        assertEquals(1, all.size());
    }

    @Test
    void createTasksSkipsExistingTypesAndInsertsMissing() {
        // 模拟"批准时已有部分 task"场景：先 stub 返回 INSPECT_RECTIFY 已存在，
        // 其余两类缺失——验证仅 INSERT 缺失项（service 内部 findExistingTaskTypes + 跳过已存）
        long planId = 21L;
        CpsInspectionPlan pending = plan(planId, "x", CpsInspectionPlanStatus.PENDING_REVIEW, 0, 1);
        pending.setDraftContentJson(null);
        CpsInspectionPlan approved = plan(planId, "x", CpsInspectionPlanStatus.APPROVED, 1, 1);
        when(planMapper.findById(planId)).thenReturn(pending, approved);
        when(planMapper.approveCas(any(CpsInspectionPlan.class))).thenReturn(1);
        // 关键 stub：findExistingTaskTypes 仅 INSPECT_RECTIFY 已存在
        when(taskMapper.findExistingTaskTypes(planId))
                .thenReturn(Collections.singletonList(CpsInspectionPlanTaskType.INSPECT_RECTIFY));

        CpsInspectionPlanApproveRequest req = new CpsInspectionPlanApproveRequest();
        req.setApprover("E1");
        req.setLockVersion(0);

        CpsInspectionPlanService.ApproveResult result = service.approve(planId, req);
        // INSPECT_RECTIFY 已存在跳过；剩余两类 INSPECT_PATROL/INSPECT_CHECK 新建
        assertEquals(2, result.getCreatedTasks().size());
        // 验证新建的两类不含 INSPECT_RECTIFY
        assertFalse(result.getCreatedTasks().stream()
                .anyMatch(t -> t.getTaskType() == CpsInspectionPlanTaskType.INSPECT_RECTIFY));
        verify(taskMapper, times(2)).insert(any(CpsInspectionPlanTask.class));
    }

    @Test
    void createTasksHandlesDraftJsonParseFailureGracefully() {
        long planId = 31L;
        CpsInspectionPlan pending = plan(planId, "x", CpsInspectionPlanStatus.PENDING_REVIEW, 0, 1);
        pending.setDraftContentJson("not-json-{[");
        CpsInspectionPlan approved = plan(planId, "x", CpsInspectionPlanStatus.APPROVED, 1, 1);
        when(planMapper.findById(planId)).thenReturn(pending, approved);
        when(planMapper.approveCas(any(CpsInspectionPlan.class))).thenReturn(1);
        when(taskMapper.findExistingTaskTypes(planId)).thenReturn(Collections.emptyList());

        CpsInspectionPlanApproveRequest req = new CpsInspectionPlanApproveRequest();
        req.setApprover("E1");
        req.setLockVersion(0);

        CpsInspectionPlanService.ApproveResult result = service.approve(planId, req);
        assertEquals(3, result.getCreatedTasks().size());
        for (CpsInspectionPlanTask t : result.getCreatedTasks()) {
            assertNotNull(t.getTitle(), "默认 title 不应为 null（draft 解析失败兜底）");
        }
    }

    @Test
    void parseDraftTasksExtractsPerTypeSpec() {
        long planId = 41L;
        CpsInspectionPlan pending = plan(planId, "x", CpsInspectionPlanStatus.PENDING_REVIEW, 0, 1);
        pending.setDraftContentJson("{\"tasks\":{\"INSPECT_PATROL\":{\"title\":\"夜间巡检\",\"target_emp_no\":\"E2001\"}}}");
        CpsInspectionPlan approved = plan(planId, "x", CpsInspectionPlanStatus.APPROVED, 1, 1);
        // approved 同样需保留 draft JSON，否则 createPlanTasks 走第二次 findById 时丢失
        approved.setDraftContentJson(pending.getDraftContentJson());
        when(planMapper.findById(planId)).thenReturn(pending, approved);
        when(planMapper.approveCas(any(CpsInspectionPlan.class))).thenReturn(1);
        when(taskMapper.findExistingTaskTypes(planId)).thenReturn(Collections.emptyList());

        CpsInspectionPlanApproveRequest req = new CpsInspectionPlanApproveRequest();
        req.setApprover("E1");
        req.setLockVersion(0);

        CpsInspectionPlanService.ApproveResult result = service.approve(planId, req);
        CpsInspectionPlanTask patrolTask = result.getCreatedTasks().stream()
                .filter(t -> t.getTaskType() == CpsInspectionPlanTaskType.INSPECT_PATROL)
                .findFirst().orElseThrow();
        assertEquals("夜间巡检", patrolTask.getTitle());
        assertEquals("E2001", patrolTask.getTargetEmpNo());
    }

    @Test
    void missingDraftContentJsonUsesFallbackTitle() {
        long planId = 51L;
        CpsInspectionPlan pending = plan(planId, "x", CpsInspectionPlanStatus.PENDING_REVIEW, 0, 1);
        pending.setDraftContentJson(null);
        CpsInspectionPlan approved = plan(planId, "x", CpsInspectionPlanStatus.APPROVED, 1, 1);
        when(planMapper.findById(planId)).thenReturn(pending, approved);
        when(planMapper.approveCas(any(CpsInspectionPlan.class))).thenReturn(1);
        when(taskMapper.findExistingTaskTypes(planId)).thenReturn(Collections.emptyList());

        CpsInspectionPlanApproveRequest req = new CpsInspectionPlanApproveRequest();
        req.setApprover("E1");
        req.setLockVersion(0);

        CpsInspectionPlanService.ApproveResult result = service.approve(planId, req);
        for (CpsInspectionPlanTask t : result.getCreatedTasks()) {
            assertTrue(t.getTitle().contains("INSPECT_"));
        }
    }

    @Test
    void approveMissingApproverThrows() {
        // approver 校验在 getDetail 之前，先抛 IllegalArgumentException
        CpsInspectionPlanApproveRequest req = new CpsInspectionPlanApproveRequest();
        assertThrows(IllegalArgumentException.class, () -> service.approve(99L, req));
    }

    @Test
    void detailMissingThrows() {
        when(planMapper.findById(999L)).thenReturn(null);
        assertThrows(IllegalArgumentException.class, () -> service.getDetail(999L));
    }

    @Test
    void listTasksReturnsEmptyForPlanWithoutTasks() {
        when(planMapper.findById(71L)).thenReturn(
                plan(71L, "x", CpsInspectionPlanStatus.PENDING_REVIEW, 0, 1));
        when(taskMapper.findByPlanId(71L)).thenReturn(Collections.emptyList());
        assertTrue(service.listTasks(71L).isEmpty());
    }

    @Test
    void applyDraftUpdateCasFailureThrows() {
        CpsInspectionPlanRequest r = baseDraft("weekly-CHECK-CASFAIL");
        r.setPlanType("WEEKLY_CHECK");
        r.setDraftContentJson("{\"tasks\":{}}");
        CpsInspectionPlan existing = plan(81L, "weekly-CHECK-CASFAIL",
                CpsInspectionPlanStatus.PENDING_REVIEW, 0, 1);
        when(planMapper.findBySourceRunIdAndPlanType("weekly-CHECK-CASFAIL", "WEEKLY_CHECK"))
                .thenReturn(java.util.Optional.of(existing));
        when(planMapper.updateDraftCas(any(CpsInspectionPlan.class))).thenReturn(0);
        assertThrows(IllegalStateException.class, () -> service.applyDraft(r));
    }
}
