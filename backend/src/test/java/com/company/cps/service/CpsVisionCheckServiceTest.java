package com.company.cps.service;

import com.company.cps.domain.CpsVisionCheckJudgeEvent;
import com.company.cps.domain.CpsVisionCheckRecord;
import com.company.cps.domain.CpsVisionCheckStatus;
import com.company.cps.dto.CpsPageResponse;
import com.company.cps.dto.CpsVisionCheckListFilter;
import com.company.cps.dto.CpsVisionCheckOverrideRequest;
import com.company.cps.dto.CpsVisionCheckSubmitRequest;
import com.company.cps.dto.CpsVisionCheckSubmitResult;
import com.company.cps.mapper.CpsVisionCheckJudgeEventMapper;
import com.company.cps.mapper.CpsVisionCheckRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * B6 视觉点检服务契约：
 * - submit：PENDING 落地 + SUBMITTED 流水 + 触发 judge → AI_PASS/AI_FAIL/TIMEOUT；
 * - 幂等：同 fingerprint 已判过 → 直接复用历史记录；
 * - humanOverride：仅 AI_JUDGING/AI_PASS/AI_FAIL 可改；
 * - rejudge：仅 PENDING/TIMEOUT 可调；写 REJUDGED 流水；
 * - list：分页 + status 校验；
 * - 事务性：service 入口用 @Transactional，方法签名级别验证。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CpsVisionCheckServiceTest {

    @Mock private CpsVisionCheckRecordMapper recordMapper;
    @Mock private CpsVisionCheckJudgeEventMapper eventMapper;
    @Mock private CpsVisionClient visionClient;

    private CpsVisionCheckService service;

    @BeforeEach
    void setUp() {
        service = new CpsVisionCheckService(recordMapper, eventMapper, visionClient);
    }

    private static CpsVisionCheckSubmitRequest baseSubmit() {
        CpsVisionCheckSubmitRequest r = new CpsVisionCheckSubmitRequest();
        r.setRoomId(11L);
        r.setCheckItemId(22L);
        r.setRoomType(CpsVisionCheckRecord.ROOM_TYPE_STANDARD);
        r.setPhotoObjectKey("checkin/room-11/item-22/2026-01-01.jpg");
        r.setPhotoUrl("https://cdn.example.com/checkin/room-11/item-22/2026-01-01.jpg");
        r.setCreatedBy("emp-007");
        return r;
    }

    private static CpsVisionCheckRecord record(long id, String fingerprint, String status) {
        CpsVisionCheckRecord rec = new CpsVisionCheckRecord();
        rec.setId(id);
        rec.setRoomId(11L);
        rec.setCheckItemId(22L);
        rec.setRoomType(CpsVisionCheckRecord.ROOM_TYPE_STANDARD);
        rec.setStatus(status);
        rec.setJudgeFingerprint(fingerprint);
        rec.setPhotoUrl("https://cdn.example.com/checkin/room-11/item-22/2026-01-01.jpg");
        rec.setCreatedAt(LocalDateTime.now());
        rec.setUpdatedAt(LocalDateTime.now());
        return rec;
    }

    @Test
    void submitInsertsPendingAndJudgeRunsToAiPass() {
        CpsVisionCheckSubmitRequest req = baseSubmit();
        // 共享可变 record，模拟数据库行被 updateAiJudgeResult 改写后的状态
        CpsVisionCheckRecord[] live = new CpsVisionCheckRecord[1];
        when(recordMapper.findJudgedByFingerprint(anyString())).thenReturn(null);
        when(recordMapper.insert(any(CpsVisionCheckRecord.class))).thenAnswer(inv -> {
            CpsVisionCheckRecord r = inv.getArgument(0);
            r.setId(1001L);
            live[0] = r;
            return 1;
        });
        when(recordMapper.findById(1001L)).thenAnswer(inv -> live[0]);
        when(recordMapper.updateAiJudgeResult(any(CpsVisionCheckRecord.class))).thenAnswer(inv -> {
            CpsVisionCheckRecord r = inv.getArgument(0);
            live[0].setStatus(r.getStatus());
            live[0].setAiOverall(r.getAiOverall());
            live[0].setAiScore(r.getAiScore());
            live[0].setAiReason(r.getAiReason());
            return 1;
        });
        when(visionClient.judge(anyString(), anyString(), anyLong(), anyString(), any()))
                .thenReturn(new CpsVisionClient.VisionJudgeResult(
                        CpsVisionCheckRecord.AI_OVERALL_PASS, 95, Arrays.asList("ok")));

        CpsVisionCheckSubmitResult result = service.submit(req);

        assertNotNull(result);
        assertEquals(1001L, result.getRecord().getId());
        assertEquals(CpsVisionCheckStatus.AI_PASS.name(), result.getRecord().getStatus());
        assertEquals("PASS", result.getRecord().getAiOverall());
        assertEquals(Integer.valueOf(95), result.getRecord().getAiScore());
        assertNotNull(result.getJudgeFingerprint());
        assertEquals(64, result.getJudgeFingerprint().length(), "judge_fingerprint must be 64-hex");

        // SUBMITTED + AI_STARTED + AI_COMPLETED 三条流水
        ArgumentCaptor<CpsVisionCheckJudgeEvent> eventCaptor =
                ArgumentCaptor.forClass(CpsVisionCheckJudgeEvent.class);
        verify(eventMapper, times(3)).insert(eventCaptor.capture());
        List<String> types = eventCaptor.getAllValues().stream()
                .map(CpsVisionCheckJudgeEvent::getEventType).toList();
        assertTrue(types.contains(CpsVisionCheckJudgeEvent.EVENT_SUBMITTED));
        assertTrue(types.contains(CpsVisionCheckJudgeEvent.EVENT_AI_STARTED));
        assertTrue(types.contains(CpsVisionCheckJudgeEvent.EVENT_AI_COMPLETED));
    }

    @Test
    void submitRunsToAiFailWhenPartialOrProblem() {
        CpsVisionCheckSubmitRequest req = baseSubmit();
        CpsVisionCheckRecord[] live = new CpsVisionCheckRecord[1];
        when(recordMapper.findJudgedByFingerprint(anyString())).thenReturn(null);
        when(recordMapper.insert(any(CpsVisionCheckRecord.class))).thenAnswer(inv -> {
            CpsVisionCheckRecord r = inv.getArgument(0);
            r.setId(2002L);
            live[0] = r;
            return 1;
        });
        when(recordMapper.findById(2002L)).thenAnswer(inv -> live[0]);
        when(recordMapper.updateAiJudgeResult(any(CpsVisionCheckRecord.class))).thenAnswer(inv -> {
            CpsVisionCheckRecord r = inv.getArgument(0);
            live[0].setStatus(r.getStatus());
            live[0].setAiOverall(r.getAiOverall());
            live[0].setAiScore(r.getAiScore());
            live[0].setAiReason(r.getAiReason());
            return 1;
        });
        when(visionClient.judge(anyString(), anyString(), anyLong(), anyString(), any()))
                .thenReturn(new CpsVisionClient.VisionJudgeResult("PROBLEM", 60, Arrays.asList("dust")));

        CpsVisionCheckSubmitResult result = service.submit(req);

        assertEquals(CpsVisionCheckStatus.AI_FAIL.name(), result.getRecord().getStatus());
        assertEquals("PROBLEM", result.getRecord().getAiOverall());
    }

    @Test
    void submitReusesIdempotentlyWhenFingerprintAlreadyJudged() {
        CpsVisionCheckSubmitRequest req = baseSubmit();
        CpsVisionCheckRecord cached = record(9000L, "fp-cached", CpsVisionCheckStatus.AI_PASS.name());
        cached.setAiOverall("PASS");
        cached.setAiScore(95);
        when(recordMapper.findJudgedByFingerprint(anyString())).thenReturn(cached);

        CpsVisionCheckSubmitResult result = service.submit(req);

        assertSame(cached, result.getRecord());
        assertEquals(9000L, result.getRecord().getId());
        // 没插新记录 → insert 0 次
        verify(recordMapper, never()).insert(any(CpsVisionCheckRecord.class));
    }

    @Test
    void submitTransitionsToTimeoutWhenVisionClientReturnsNull() {
        CpsVisionCheckSubmitRequest req = baseSubmit();
        CpsVisionCheckRecord[] live = new CpsVisionCheckRecord[1];
        when(recordMapper.findJudgedByFingerprint(anyString())).thenReturn(null);
        when(recordMapper.insert(any(CpsVisionCheckRecord.class))).thenAnswer(inv -> {
            CpsVisionCheckRecord r = inv.getArgument(0);
            r.setId(3003L);
            live[0] = r;
            return 1;
        });
        when(recordMapper.findById(3003L)).thenAnswer(inv -> live[0]);
        when(visionClient.judge(anyString(), anyString(), anyLong(), anyString(), any())).thenReturn(null);

        CpsVisionCheckSubmitResult result = service.submit(req);

        assertEquals(CpsVisionCheckStatus.TIMEOUT.name(), result.getRecord().getStatus());
        ArgumentCaptor<CpsVisionCheckJudgeEvent> eventCaptor =
                ArgumentCaptor.forClass(CpsVisionCheckJudgeEvent.class);
        verify(eventMapper, times(4)).insert(eventCaptor.capture());
        List<String> types = eventCaptor.getAllValues().stream()
                .map(CpsVisionCheckJudgeEvent::getEventType).toList();
        assertTrue(types.contains(CpsVisionCheckJudgeEvent.EVENT_AI_FAILED));
        assertTrue(types.contains(CpsVisionCheckJudgeEvent.EVENT_TIMEOUT_OPENED));
    }

    @Test
    void submitRejectsWhenRoomTypeUnsupported() {
        CpsVisionCheckSubmitRequest req = baseSubmit();
        req.setRoomType("GARAGE");
        assertThrows(IllegalArgumentException.class, () -> service.submit(req));
    }

    @Test
    void submitRejectsWhenBothPhotoFieldsMissing() {
        CpsVisionCheckSubmitRequest req = baseSubmit();
        req.setPhotoObjectKey(null);
        req.setPhotoUrl(null);
        assertThrows(IllegalArgumentException.class, () -> service.submit(req));
    }

    @Test
    void humanOverrideIsAllowedFromAiJudgingAndBlocksOnPending() {
        CpsVisionCheckOverrideRequest req = new CpsVisionCheckOverrideRequest();
        req.setDecision("FAIL");
        req.setReason("视觉漏检：缺少灭火器");
        req.setOperatorEmpNo("emp-007");

        CpsVisionCheckRecord aiJudging = record(4001L, "fp-1", CpsVisionCheckStatus.AI_JUDGING.name());
        when(recordMapper.findById(4001L)).thenReturn(aiJudging);

        CpsVisionCheckRecord result = service.humanOverride(4001L, req);
        assertEquals(CpsVisionCheckStatus.HUMAN_OVERRIDE.name(), result.getStatus());
        // aiOverall 不被改判覆写（CHECK 仅 PASS/PARTIAL/PROBLEM）
        assertNull(result.getAiOverall());
        assertEquals("emp-007", result.getHumanOverrideEmpNo());
        assertEquals("视觉漏检：缺少灭火器", result.getHumanOverrideReason());

        // PENDING 不可改判
        CpsVisionCheckRecord pending = record(4002L, "fp-2", CpsVisionCheckStatus.PENDING.name());
        when(recordMapper.findById(4002L)).thenReturn(pending);
        assertThrows(IllegalStateException.class, () -> service.humanOverride(4002L, req));

        // 非法 decision
        CpsVisionCheckOverrideRequest bad = new CpsVisionCheckOverrideRequest();
        bad.setDecision("MAYBE");
        bad.setReason("x");
        bad.setOperatorEmpNo("emp-007");
        assertThrows(IllegalArgumentException.class, () -> service.humanOverride(4001L, bad));
    }

    @Test
    void rejudgeAllowedFromPendingAndTimeoutButBlocksFromAiPass() {
        when(recordMapper.findJudgedByFingerprint(anyString())).thenReturn(null);
        when(recordMapper.updateAiJudgeResult(any(CpsVisionCheckRecord.class))).thenAnswer(inv -> {
            CpsVisionCheckRecord r = inv.getArgument(0);
            if (pending5001 != null) {
                pending5001.setStatus(r.getStatus());
                pending5001.setAiOverall(r.getAiOverall());
            }
            return 1;
        });
        when(visionClient.judge(anyString(), anyString(), anyLong(), anyString(), any()))
                .thenReturn(new CpsVisionClient.VisionJudgeResult("PASS", 90, Collections.emptyList()));

        // PENDING → REJUDGED → AI_PASS
        CpsVisionCheckRecord pending = record(5001L, "fp-rj1", CpsVisionCheckStatus.PENDING.name());
        pending5001 = pending;
        when(recordMapper.findById(5001L)).thenReturn(pending);
        CpsVisionCheckRecord after = service.rejudge(5001L, "emp-007");
        assertEquals(CpsVisionCheckStatus.AI_PASS.name(), after.getStatus());

        // AI_PASS → 不可 rejudge
        CpsVisionCheckRecord aiPass = record(5002L, "fp-rj2", CpsVisionCheckStatus.AI_PASS.name());
        when(recordMapper.findById(5002L)).thenReturn(aiPass);
        assertThrows(IllegalStateException.class, () -> service.rejudge(5002L, "emp-007"));
    }

    private static CpsVisionCheckRecord pending5001;

    @Test
    void rejudgeRequiresOperatorEmpNo() {
        CpsVisionCheckRecord pending = record(5003L, "fp-rj3", CpsVisionCheckStatus.PENDING.name());
        when(recordMapper.findById(5003L)).thenReturn(pending);
        assertThrows(IllegalArgumentException.class, () -> service.rejudge(5003L, ""));
        assertThrows(IllegalArgumentException.class, () -> service.rejudge(5003L, null));
    }

    @Test
    void listAcceptsFiltersAndAppliesPaginationBounds() {
        CpsVisionCheckListFilter filter = new CpsVisionCheckListFilter();
        filter.setRoomId(11L);
        filter.setCheckItemId(22L);
        filter.setStatus("AI_PASS");
        filter.setPage(0);   // off-bound → 1
        filter.setSize(999); // off-bound → 200

        when(recordMapper.countByFilters(any(), any(), any(), any(), any())).thenReturn(0L);

        CpsPageResponse<CpsVisionCheckRecord> page = service.list(filter);
        assertEquals(0L, page.getTotal());
        assertTrue(page.getRows().isEmpty());

        // 非法 status
        filter.setStatus("UNKNOWN_STATE");
        assertThrows(IllegalArgumentException.class, () -> service.list(filter));
    }

    @Test
    void listReturnsRowsWithCorrectLimitAndOffset() {
        CpsVisionCheckListFilter filter = new CpsVisionCheckListFilter();
        filter.setRoomId(11L);
        filter.setStatus("PENDING");
        filter.setPage(2);
        filter.setSize(10);

        CpsVisionCheckRecord r1 = record(1L, "fp-a", CpsVisionCheckStatus.PENDING.name());
        when(recordMapper.countByFilters(eq(11L), eq(null), eq("PENDING"), eq(null), eq(null)))
                .thenReturn(25L);
        when(recordMapper.listByFilters(eq(11L), eq(null), eq("PENDING"), eq(null), eq(null),
                eq(10), eq(10))).thenReturn(Arrays.asList(r1));

        CpsPageResponse<CpsVisionCheckRecord> page = service.list(filter);
        assertEquals(25L, page.getTotal());
        assertEquals(1, page.getRows().size());
        verify(recordMapper).listByFilters(eq(11L), eq(null), eq("PENDING"),
                eq(null), eq(null), eq(10), eq(10));
    }

    @Test
    void statusEnumExposesHumanOverrideAndRejudgePredicates() {
        assertTrue(CpsVisionCheckStatus.AI_JUDGING.isHumanOverrideAllowed());
        assertTrue(CpsVisionCheckStatus.AI_PASS.isHumanOverrideAllowed());
        assertTrue(CpsVisionCheckStatus.AI_FAIL.isHumanOverrideAllowed());
        assertFalse(CpsVisionCheckStatus.PENDING.isHumanOverrideAllowed());
        assertFalse(CpsVisionCheckStatus.TIMEOUT.isHumanOverrideAllowed());
        assertFalse(CpsVisionCheckStatus.HUMAN_OVERRIDE.isHumanOverrideAllowed());

        assertTrue(CpsVisionCheckStatus.PENDING.isRejudgeAllowed());
        assertTrue(CpsVisionCheckStatus.TIMEOUT.isRejudgeAllowed());
        assertFalse(CpsVisionCheckStatus.AI_JUDGING.isRejudgeAllowed());
        assertFalse(CpsVisionCheckStatus.AI_PASS.isRejudgeAllowed());

        assertTrue(CpsVisionCheckStatus.isValid("PENDING"));
        assertFalse(CpsVisionCheckStatus.isValid("UNKNOWN"));
        assertFalse(CpsVisionCheckStatus.isValid(null));
    }

    @Test
    void buildFingerprintIsSha256Hex() throws Exception {
        // reflection 验证 SHA-256 输出 64-hex
        Method m = CpsVisionCheckService.class.getDeclaredMethod(
                "buildFingerprint", Long.class, Long.class, String.class);
        m.setAccessible(true);
        String fp = (String) m.invoke(null, 11L, 22L, "https://cdn.example.com/x.jpg");
        assertEquals(64, fp.length());
        assertTrue(fp.matches("[0-9a-f]{64}"), "fingerprint must be 64 lowercase hex chars");

        // 同 (roomId, checkItemId, photoUrl) → 同 fingerprint
        String fp2 = (String) m.invoke(null, 11L, 22L, "https://cdn.example.com/x.jpg");
        assertEquals(fp, fp2);
        // 不同 photoUrl → 不同
        String fp3 = (String) m.invoke(null, 11L, 22L, "https://cdn.example.com/y.jpg");
        assertFalse(fp.equals(fp3));
        // 空 photoUrl 退化为 "no-photo" 仍能算指纹
        String fp4 = (String) m.invoke(null, 11L, 22L, null);
        assertEquals(64, fp4.length());
    }

    @Test
    void humanOverrideRejectsMissingReason() {
        CpsVisionCheckOverrideRequest req = new CpsVisionCheckOverrideRequest();
        req.setDecision("PASS");
        req.setReason("  ");
        req.setOperatorEmpNo("emp-007");
        CpsVisionCheckRecord aiPass = record(6001L, "fp-x", CpsVisionCheckStatus.AI_PASS.name());
        when(recordMapper.findById(6001L)).thenReturn(aiPass);
        assertThrows(IllegalArgumentException.class, () -> service.humanOverride(6001L, req));
    }

    @Test
    void submitCatchesAllServicePathCatches() {
        // 缺失 roomId → 拒绝
        CpsVisionCheckSubmitRequest req = baseSubmit();
        req.setRoomId(null);
        assertThrows(IllegalArgumentException.class, () -> service.submit(req));
        req.setRoomId(11L);
        req.setCheckItemId(null);
        assertThrows(IllegalArgumentException.class, () -> service.submit(req));
        req.setCheckItemId(22L);
        req.setRoomType(null);
        assertThrows(IllegalArgumentException.class, () -> service.submit(req));
        req.setRoomType("STANDARD");
        req.setCreatedBy(null);
        assertThrows(IllegalArgumentException.class, () -> service.submit(req));
    }

    @Test
    void judgeRejectsMissingRecordOrFingerprint() {
        assertThrows(IllegalArgumentException.class, () -> service.judge(null, "fp"));
        assertThrows(IllegalArgumentException.class, () -> service.judge(1L, null));
        assertThrows(IllegalArgumentException.class, () -> service.judge(1L, "  "));
    }

    @Test
    void findByIdRejectsNullAndMissing() {
        assertThrows(IllegalArgumentException.class, () -> service.findById(null));
        when(recordMapper.findById(7L)).thenReturn(null);
        assertThrows(IllegalArgumentException.class, () -> service.findById(7L));
    }
}