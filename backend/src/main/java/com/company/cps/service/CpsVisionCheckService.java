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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/**
 * B6 视觉点检后端契约（C-04 room-checks 视觉判定端点）：
 * - submit：插入 PENDING 记录 → dispatch async judge（先落 AI_STARTED 流水）→ 返回 record+fingerprint；
 *   同 fingerprint 已判过 → 直接复用历史 AI_PASS/AI_FAIL 记录（幂等），不重复计费/计分。
 * - judge(recordId, fingerprint)：内部幂等；调 CpsVisionClient（Python /room-checks/judge）→
 *   成功写 AI_COMPLETED + 落 AI_PASS/AI_FAIL；失败/超时写 AI_FAILED + TIMEOUT_OPENED 不抛异常。
 * - humanOverride：仅 AI_JUDGING/AI_PASS/AI_FAIL 可改；写 HUMAN_OVERRIDE 状态 + 流水。
 * - rejudge：仅 PENDING/TIMEOUT 可调；写 REJUDGED 流水 + 重置 status=AI_JUDGING 重新触发 judge。
 * - list：分页查询（roomId/checkItemId/status/startTime/endTime 任选）。
 *
 * 全部写入走 @Transactional；vision client 调用本身 60s，事务内仅写状态 + 流水，不阻塞。
 *
 * 注：与 B3 辅房点检执行域 CpsRoomCheckService 业务不同（独立表 cps_vision_check_record），
 * 类名分立避免语义混淆。
 */
@Service
public class CpsVisionCheckService {

    private static final Logger log = LoggerFactory.getLogger(CpsVisionCheckService.class);

    private static final Set<String> ALLOWED_ROOM_TYPES = Set.of(
            CpsVisionCheckRecord.ROOM_TYPE_PRIMARY,
            CpsVisionCheckRecord.ROOM_TYPE_STANDARD,
            CpsVisionCheckRecord.ROOM_TYPE_SPECIAL,
            CpsVisionCheckRecord.ROOM_TYPE_TOOL,
            CpsVisionCheckRecord.ROOM_TYPE_OTHER);

    private static final Set<String> ALLOWED_OVERRIDE_DECISIONS = Set.of("PASS", "FAIL");

    private final CpsVisionCheckRecordMapper recordMapper;
    private final CpsVisionCheckJudgeEventMapper eventMapper;
    private final CpsVisionClient visionClient;

    public CpsVisionCheckService(CpsVisionCheckRecordMapper recordMapper,
                                 CpsVisionCheckJudgeEventMapper eventMapper,
                                 CpsVisionClient visionClient) {
        this.recordMapper = recordMapper;
        this.eventMapper = eventMapper;
        this.visionClient = visionClient;
    }

    /**
     * 提交视觉点检：插入 PENDING → 写 SUBMITTED 流水 → 触发 async judge → 返回 record + fingerprint。
     * 若同 fingerprint 已判过（AI_PASS/AI_FAIL/HUMAN_OVERRIDE）→ 直接复用历史记录，不重复提交。
     */
    @Transactional
    public CpsVisionCheckSubmitResult submit(CpsVisionCheckSubmitRequest request) {
        validateSubmit(request);

        String fingerprint = buildFingerprint(request.getRoomId(), request.getCheckItemId(), request.getPhotoUrl());

        // 幂等：同 fingerprint 已落地 → 直接复用
        CpsVisionCheckRecord existing = recordMapper.findJudgedByFingerprint(fingerprint);
        if (existing != null) {
            log.info("Vision check idempotent reuse: fingerprint={} existingId={} status={}",
                    fingerprint, existing.getId(), existing.getStatus());
            return new CpsVisionCheckSubmitResult(existing, fingerprint);
        }

        CpsVisionCheckRecord record = new CpsVisionCheckRecord();
        record.setRoomId(request.getRoomId());
        record.setCheckItemId(request.getCheckItemId());
        record.setRoomType(request.getRoomType());
        record.setStatus(CpsVisionCheckStatus.PENDING.name());
        record.setPhotoObjectKey(request.getPhotoObjectKey());
        record.setPhotoUrl(request.getPhotoUrl());
        record.setJudgeFingerprint(fingerprint);
        record.setCreatedBy(request.getCreatedBy());
        recordMapper.insert(record);

        appendEvent(fingerprint, CpsVisionCheckJudgeEvent.EVENT_SUBMITTED,
                "recordId=" + record.getId() + "; roomType=" + request.getRoomType(),
                request.getCreatedBy());

        // dispatch async judge (本波同步落地，事务内调用)
        return new CpsVisionCheckSubmitResult(judge(record.getId(), fingerprint), fingerprint);
    }

    /**
     * 视觉判定（service 内部调用，亦可被 Python 契约回调）：
     * - 内部幂等：同一 fingerprint 已判过 → 直接返回；
     * - AI_JUDGING 状态 → 调 CpsVisionClient；
     *   - 成功 → 写 AI_COMPLETED + 落 AI_PASS/AI_FAIL/PARTIAL；
     *   - 失败/null → 写 AI_FAILED + TIMEOUT_OPENED，状态 TIMEOUT，不抛异常。
     */
    @Transactional
    public CpsVisionCheckRecord judge(Long recordId, String fingerprint) {
        if (recordId == null || fingerprint == null || fingerprint.isBlank()) {
            throw new IllegalArgumentException("recordId/fingerprint is required for judge");
        }
        // 内部幂等
        CpsVisionCheckRecord cached = recordMapper.findJudgedByFingerprint(fingerprint);
        if (cached != null && !cached.getId().equals(recordId)) {
            log.info("Vision judge idempotent skip: fingerprint={} cachedId={} incomingId={}",
                    fingerprint, cached.getId(), recordId);
            return cached;
        }

        CpsVisionCheckRecord record = recordMapper.findById(recordId);
        if (record == null) {
            throw new IllegalArgumentException("Vision check record not found: id=" + recordId);
        }
        if (CpsVisionCheckStatus.AI_PASS.name().equals(record.getStatus())
                || CpsVisionCheckStatus.AI_FAIL.name().equals(record.getStatus())
                || CpsVisionCheckStatus.HUMAN_OVERRIDE.name().equals(record.getStatus())) {
            // 已判过 → 直接返回（外部重放/异常回调保护）
            log.info("Vision judge already finalized: recordId={} status={}", recordId, record.getStatus());
            return record;
        }

        // 流转 AI_JUDGING（无论从 PENDING/TIMEOUT 起步）
        record.setStatus(CpsVisionCheckStatus.AI_JUDGING.name());
        recordMapper.updateStatusFields(record);
        appendEvent(fingerprint, CpsVisionCheckJudgeEvent.EVENT_AI_STARTED,
                "recordId=" + recordId, null);

        CpsVisionClient.VisionJudgeResult result = visionClient.judge(
                fingerprint,
                record.getRoomType(),
                record.getCheckItemId(),
                record.getPhotoUrl(),
                Collections.emptyList());

        if (result == null) {
            // 失败/超时 → AI_FAILED + TIMEOUT_OPENED + 状态 TIMEOUT
            record.setStatus(CpsVisionCheckStatus.TIMEOUT.name());
            recordMapper.updateStatusFields(record);
            appendEvent(fingerprint, CpsVisionCheckJudgeEvent.EVENT_AI_FAILED,
                    "vision client returned null (timeout/disabled/unparsable)", null);
            appendEvent(fingerprint, CpsVisionCheckJudgeEvent.EVENT_TIMEOUT_OPENED,
                    "recordId=" + recordId, null);
            return recordMapper.findById(recordId);
        }

        // 成功 → AI_COMPLETED + 落 AI_PASS/AI_FAIL
        String overall = result.overall;
        String targetStatus;
        if (CpsVisionCheckRecord.AI_OVERALL_PASS.equalsIgnoreCase(overall)) {
            targetStatus = CpsVisionCheckStatus.AI_PASS.name();
        } else {
            // PARTIAL / PROBLEM 视为 AI_FAIL（按业务口径，扣分覆盖）
            targetStatus = CpsVisionCheckStatus.AI_FAIL.name();
        }
        record.setAiOverall(overall);
        record.setAiScore(result.score);
        record.setAiReason(joinReasons(result.reasons));
        record.setStatus(targetStatus);
        int rows = recordMapper.updateAiJudgeResult(record);
        if (rows != 1) {
            // status 已不在 AI_JUDGING（被并发改写）→ 报 IllegalState，事务回滚
            throw new IllegalStateException("Vision judge CAS failed: recordId=" + recordId
                    + " (status not AI_JUDGING)");
        }
        appendEvent(fingerprint, CpsVisionCheckJudgeEvent.EVENT_AI_COMPLETED,
                "overall=" + overall + "; score=" + result.score, null);
        return recordMapper.findById(recordId);
    }

    /**
     * 人工改判：仅 AI_JUDGING/AI_PASS/AI_FAIL 可改；decision=PASS|FAIL；reason 必填。
     */
    @Transactional
    public CpsVisionCheckRecord humanOverride(Long recordId, CpsVisionCheckOverrideRequest request) {
        if (recordId == null) {
            throw new IllegalArgumentException("recordId is required");
        }
        if (request == null) {
            throw new IllegalArgumentException("override request body is required");
        }
        String decision = request.getDecision() == null ? "" : request.getDecision().trim().toUpperCase();
        if (!ALLOWED_OVERRIDE_DECISIONS.contains(decision)) {
            throw new IllegalArgumentException("decision must be PASS or FAIL (got: "
                    + request.getDecision() + ")");
        }
        if (request.getReason() == null || request.getReason().trim().isEmpty()) {
            throw new IllegalArgumentException("reason is required for human override");
        }
        if (request.getOperatorEmpNo() == null || request.getOperatorEmpNo().trim().isEmpty()) {
            throw new IllegalArgumentException("operatorEmpNo is required");
        }

        CpsVisionCheckRecord record = recordMapper.findById(recordId);
        if (record == null) {
            throw new IllegalArgumentException("Vision check record not found: id=" + recordId);
        }
        if (!CpsVisionCheckStatus.valueOf(record.getStatus()).isHumanOverrideAllowed()) {
            throw new IllegalStateException("Vision check not overrideable: id=" + recordId
                    + ", status=" + record.getStatus());
        }

        record.setStatus(CpsVisionCheckStatus.HUMAN_OVERRIDE.name());
        // human_override 不改 ai_overall：ai_overall 受 CHECK 约束（仅 PASS/PARTIAL/PROBLEM），
        // 改判信息由 human_override_emp_no/human_override_reason + status=HUMAN_OVERRIDE 表达。
        record.setHumanOverrideEmpNo(request.getOperatorEmpNo());
        record.setHumanOverrideReason(request.getReason());
        recordMapper.updateStatusFields(record);

        appendEvent(record.getJudgeFingerprint(),
                CpsVisionCheckJudgeEvent.EVENT_HUMAN_OVERRIDE,
                "decision=" + decision + "; reason=" + truncate(request.getReason(), 480),
                request.getOperatorEmpNo());
        return recordMapper.findById(recordId);
    }

    /**
     * 重新触发视觉判定：仅 PENDING/TIMEOUT 可调；写 REJUDGED 流水，重置 status=AI_JUDGING 后再调 judge()。
     */
    @Transactional
    public CpsVisionCheckRecord rejudge(Long recordId, String operatorEmpNo) {
        if (recordId == null) {
            throw new IllegalArgumentException("recordId is required");
        }
        if (operatorEmpNo == null || operatorEmpNo.trim().isEmpty()) {
            throw new IllegalArgumentException("operatorEmpNo is required");
        }
        CpsVisionCheckRecord record = recordMapper.findById(recordId);
        if (record == null) {
            throw new IllegalArgumentException("Vision check record not found: id=" + recordId);
        }
        if (!CpsVisionCheckStatus.valueOf(record.getStatus()).isRejudgeAllowed()) {
            throw new IllegalStateException("Vision check not rejudgeable: id=" + recordId
                    + ", status=" + record.getStatus());
        }

        appendEvent(record.getJudgeFingerprint(),
                CpsVisionCheckJudgeEvent.EVENT_REJUDGED,
                "operator=" + operatorEmpNo + "; previousStatus=" + record.getStatus(),
                operatorEmpNo);
        return judge(recordId, record.getJudgeFingerprint());
    }

    /** 分页查询（admin 端复盘）：page/size off-bound 兜底。 */
    public CpsPageResponse<CpsVisionCheckRecord> list(CpsVisionCheckListFilter filter) {
        if (filter == null) {
            filter = new CpsVisionCheckListFilter();
        }
        int page = filter.getPage() == null || filter.getPage() < 1 ? 1 : filter.getPage();
        int size = filter.getSize() == null || filter.getSize() < 1 ? 20
                : Math.min(filter.getSize(), 200);
        int offset = (page - 1) * size;

        String status = filter.getStatus();
        if (status != null && !status.isBlank() && !CpsVisionCheckStatus.isValid(status)) {
            throw new IllegalArgumentException("invalid status: " + status);
        }
        long total = recordMapper.countByFilters(
                filter.getRoomId(), filter.getCheckItemId(), status,
                filter.getStartTime(), filter.getEndTime());
        List<CpsVisionCheckRecord> rows = total == 0 ? Collections.emptyList()
                : recordMapper.listByFilters(
                        filter.getRoomId(), filter.getCheckItemId(), status,
                        filter.getStartTime(), filter.getEndTime(), size, offset);
        return new CpsPageResponse<>(total, rows);
    }

    public CpsVisionCheckRecord findById(Long recordId) {
        if (recordId == null) {
            throw new IllegalArgumentException("recordId is required");
        }
        CpsVisionCheckRecord record = recordMapper.findById(recordId);
        if (record == null) {
            throw new IllegalArgumentException("Vision check record not found: id=" + recordId);
        }
        return record;
    }

    // ------------------------- 内部辅助 -------------------------

    private void validateSubmit(CpsVisionCheckSubmitRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("submit request body is required");
        }
        if (request.getRoomId() == null) {
            throw new IllegalArgumentException("roomId is required");
        }
        if (request.getCheckItemId() == null) {
            throw new IllegalArgumentException("checkItemId is required");
        }
        if (request.getRoomType() == null || request.getRoomType().isBlank()) {
            throw new IllegalArgumentException("roomType is required");
        }
        if (!ALLOWED_ROOM_TYPES.contains(request.getRoomType())) {
            throw new IllegalArgumentException("Unsupported roomType: " + request.getRoomType()
                    + " (allowed: PRIMARY/STANDARD/SPECIAL/TOOL/OTHER)");
        }
        if ((request.getPhotoObjectKey() == null || request.getPhotoObjectKey().isBlank())
                && (request.getPhotoUrl() == null || request.getPhotoUrl().isBlank())) {
            throw new IllegalArgumentException("photoObjectKey or photoUrl is required (image evidence)");
        }
        if (request.getCreatedBy() == null || request.getCreatedBy().isBlank()) {
            throw new IllegalArgumentException("createdBy is required");
        }
    }

    private void appendEvent(String fingerprint, String eventType, String detail, String operatorEmpNo) {
        if (fingerprint == null || fingerprint.isBlank()) return;
        CpsVisionCheckJudgeEvent event = new CpsVisionCheckJudgeEvent();
        event.setJudgeFingerprint(fingerprint);
        event.setEventType(eventType);
        event.setDetail(truncate(detail, 500));
        event.setOperatorEmpNo(operatorEmpNo);
        eventMapper.insert(event);
    }

    /**
     * judge_fingerprint = sha256(roomId|checkItemId|photoUrl) 64-hex。
     * photoUrl 为空时退化为 sha256(roomId|checkItemId|"no-photo") 仍能按业务上下文去重。
     */
    static String buildFingerprint(Long roomId, Long checkItemId, String photoUrl) {
        String salt = (photoUrl == null || photoUrl.isBlank()) ? "no-photo" : photoUrl;
        String raw = roomId + "|" + checkItemId + "|" + salt;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String joinReasons(List<String> reasons) {
        if (reasons == null || reasons.isEmpty()) return null;
        return String.join("; ", reasons);
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}