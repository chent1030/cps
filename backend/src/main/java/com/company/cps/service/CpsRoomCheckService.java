package com.company.cps.service;

import com.company.cps.domain.CpsCheckItem;
import com.company.cps.domain.CpsInspectionPlanTask;
import com.company.cps.domain.CpsRoom;
import com.company.cps.domain.CpsRoomCheckJudgeOutcome;
import com.company.cps.domain.CpsRoomCheckRecord;
import com.company.cps.domain.CpsRoomCheckRecordItem;
import com.company.cps.domain.CpsRoomCheckRecordStatus;
import com.company.cps.dto.CpsPageResponse;
import com.company.cps.dto.CpsRoomCheckAdminRowDto;
import com.company.cps.dto.CpsRoomCheckConfirmRequest;
import com.company.cps.dto.CpsRoomCheckRecordResponse;
import com.company.cps.dto.CpsRoomCheckRejudgeResponse;
import com.company.cps.dto.CpsRoomCheckStartRequest;
import com.company.cps.dto.CpsRoomCheckTaskResponse;
import com.company.cps.mapper.CpsCheckItemMapper;
import com.company.cps.mapper.CpsInspectionPlanTaskMapper;
import com.company.cps.mapper.CpsRoomCheckRecordItemMapper;
import com.company.cps.mapper.CpsRoomCheckRecordMapper;
import com.company.cps.mapper.CpsRoomMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * B3 辅房点检执行域（PRD §22-§24）。
 *
 * 状态机：PENDING待执行 → IN_PROGRESS执行中（首张照片）→ AWAIT_CONFIRM已判定待确认（提交+同步判定成功）
 * → JUDGED已确认锁定（点检人 confirm，计分）。
 * 判定（C-04 同步）：成功回写明细；TYPE_MISMATCH/UNJUDGEABLE 阻断提交须补拍；服务未起降级 JUDGED+PENDING 不阻塞。
 * 确认（PRD 24.2.2）：AWAIT_CONFIRM 下点检人可修正 PASS/FAIL（修正留痕 24.2.5），确认后按最终结果计分（24.2.6）。
 * 评分（B4 消费）：score=max(0,100−FAIL 项 deduct_score 和)；降级单/待确认单 score=NULL 不计入排名。
 *
 * 注：判定 HTTP 调用刻意不包 @Transactional（避免长事务占连接），写库逐条自动提交；
 * 崩溃窗口内可能出现"明细已回写、单未推进"，重试提交可修复（幂等覆盖写）。
 */
@Service
public class CpsRoomCheckService {

    private final CpsRoomCheckRecordMapper recordMapper;
    private final CpsRoomCheckRecordItemMapper itemMapper;
    private final CpsRoomMapper roomMapper;
    private final CpsCheckItemMapper checkItemMapper;
    private final CpsInspectionPlanTaskMapper planTaskMapper;
    private final CpsRoomCheckJudgeClient judgeClient;
    private final RustFsStorageService storage;

    public CpsRoomCheckService(CpsRoomCheckRecordMapper recordMapper,
                               CpsRoomCheckRecordItemMapper itemMapper,
                               CpsRoomMapper roomMapper,
                               CpsCheckItemMapper checkItemMapper,
                               CpsInspectionPlanTaskMapper planTaskMapper,
                               CpsRoomCheckJudgeClient judgeClient,
                               RustFsStorageService storage) {
        this.recordMapper = recordMapper;
        this.itemMapper = itemMapper;
        this.roomMapper = roomMapper;
        this.checkItemMapper = checkItemMapper;
        this.planTaskMapper = planTaskMapper;
        this.judgeClient = judgeClient;
        this.storage = storage;
    }

    /** mobile 任务列表：empNo 的 INSPECT_CHECK 任务 + 覆盖房间进度。 */
    public List<CpsRoomCheckTaskResponse> listTasks(String empNo, String taskStatus) {
        List<CpsInspectionPlanTask> tasks = planTaskMapper.findCheckTasksForEmp(
                empNo, "INSPECT_CHECK", emptyToNull(taskStatus));
        List<CpsRoomCheckTaskResponse> result = new ArrayList<>();
        for (CpsInspectionPlanTask task : tasks) {
            CpsRoomCheckTaskResponse row = new CpsRoomCheckTaskResponse();
            row.setTaskId(task.getId());
            row.setPlanId(task.getPlanId());
            row.setTitle(task.getTitle());
            row.setTaskStatus(task.getTaskStatus());
            row.setTargetEmpNo(task.getTargetEmpNo());
            row.setScheduledAt(task.getScheduledAt() == null ? null : task.getScheduledAt().toString());
            row.setFrequency(task.getFrequency());
            row.setAcceptanceCriteria(task.getAcceptanceCriteria());
            row.setEvidenceRequirement(task.getEvidenceRequirement());
            row.setRoomCodes(CpsRoomCheckTaskResponse.parseRoomCodes(task.getReferenceObjectKey()));
            row.setJudgedRoomCount(countJudgedRooms(task));
            result.add(row);
        }
        return result;
    }

    /**
     * 开启点检：按 房间×点检项 生成明细快照（幂等：同任务同房间未判定单直接复用）。
     * 点检项圈定（V20261017）：房间配置了 check_item_ids → 精确取配置项（仍需 enabled 且 APPLICABLE）；
     * 未配置 → 回退 applicable_room_types 房型匹配（enabled 且 APPLICABLE 且房型命中，PRD §23.2）。
     */
    public CpsRoomCheckRecordResponse start(CpsRoomCheckStartRequest request) {
        String empNo = requireText(request.getEmpNo(), "empNo is required");
        CpsRoom room = roomMapper.findById(request.getRoomId())
                .orElseThrow(() -> new IllegalArgumentException("room not found: " + request.getRoomId()));
        if (!Boolean.TRUE.equals(room.getEnabled())) {
            throw new IllegalArgumentException("room is disabled: " + room.getRoomCode());
        }
        CpsInspectionPlanTask task = null;
        if (request.getPlanTaskId() != null) {
            task = planTaskMapper.findById(request.getPlanTaskId());
            if (task == null) {
                throw new IllegalArgumentException("plan task not found: " + request.getPlanTaskId());
            }
            if (!"INSPECT_CHECK".equals(task.getTaskType())) {
                throw new IllegalArgumentException("plan task is not INSPECT_CHECK: " + task.getTaskType());
            }
            if (task.getTargetEmpNo() != null && !task.getTargetEmpNo().equals(empNo)) {
                throw new IllegalArgumentException("only the assigned owner can execute this task: " + task.getTargetEmpNo());
            }
            if ("CANCELLED".equals(task.getTaskStatus())) {
                throw new IllegalArgumentException("plan task is cancelled");
            }
            List<String> roomCodes = CpsRoomCheckTaskResponse.parseRoomCodes(task.getReferenceObjectKey());
            if (!roomCodes.isEmpty() && !roomCodes.contains(room.getRoomCode())) {
                throw new IllegalArgumentException("task does not cover room: " + room.getRoomCode());
            }
        }
        CpsRoomCheckRecord existing = request.getPlanTaskId() == null ? null
                : recordMapper.findActiveByTaskAndRoom(request.getPlanTaskId(), room.getId());
        if (existing != null) {
            return getRecord(existing.getId(), empNo);
        }
        CpsRoomCheckRecord record = new CpsRoomCheckRecord();
        record.setPlanTaskId(request.getPlanTaskId());
        record.setPlanId(task == null ? null : task.getPlanId());
        record.setRoomId(room.getId());
        record.setRoomCode(room.getRoomCode());
        record.setRoomName(room.getRoomName());
        record.setCheckEmpNo(empNo);
        record.setCheckEmpName(empNo); // 员工主数据缺位，暂用工号占位（同波次2口径）
        record.setRecordStatus(CpsRoomCheckRecordStatus.PENDING.name());
        recordMapper.insert(record);

        List<String> configuredCodes = parseCodes(room.getCheckItemIds());
        List<CpsRoomCheckRecordItem> items = new ArrayList<>();
        for (CpsCheckItem item : checkItemMapper.findAll(null, "APPLICABLE", true)) {
            if (!configuredCodes.isEmpty()) {
                if (!configuredCodes.contains(item.getItemCode())) {
                    continue; // 房间已精确配置：只取配置项（停用/不适用项自然跳过）
                }
            } else if (!appliesToRoom(item.getApplicableRoomTypes(), room.getRoomType())) {
                continue;
            }
            CpsRoomCheckRecordItem detail = new CpsRoomCheckRecordItem();
            detail.setRecordId(record.getId());
            detail.setRoomId(room.getId());
            detail.setCheckItemId(item.getId());
            detail.setItemCode(item.getItemCode());
            detail.setContent(item.getContent());
            detail.setPhotoCategory(item.getPhotoCategory());
            detail.setDeductScore(item.getDeductScore() == null ? 0 : item.getDeductScore());
            detail.setConfigVersion(item.getConfigVersion() == null ? 1 : item.getConfigVersion());
            items.add(detail);
        }
        if (items.isEmpty()) {
            throw new IllegalArgumentException(configuredCodes.isEmpty()
                    ? "no applicable check items for room type: " + room.getRoomType()
                    : "no available check items configured for room (codes missing/disabled): " + room.getRoomCode());
        }
        itemMapper.insertBatch(items);
        if (task != null && "PENDING".equals(task.getTaskStatus())) {
            planTaskMapper.updateTaskStatus(task.getId(), "IN_PROGRESS");
        }
        return getRecord(record.getId(), empNo);
    }

    /** 点检单详情（仅本人或 admin 场景 empNo=本人）。 */
    public CpsRoomCheckRecordResponse getRecord(Long recordId, String empNo) {
        CpsRoomCheckRecord record = requireRecord(recordId);
        requireOwner(record, empNo);
        return toResponse(record);
    }

    /** 逗号分隔编号 → 去空白去空片段列表（"A, B,,C" → [A,B,C]）。 */
    private static List<String> parseCodes(String raw) {
        List<String> codes = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) {
            return codes;
        }
        for (String piece : raw.split(",")) {
            String code = piece.trim();
            if (!code.isEmpty() && !codes.contains(code)) {
                codes.add(code);
            }
        }
        return codes;
    }

    /** admin 追溯视角：不做"仅本人"校验。 */
    public CpsRoomCheckRecordResponse getRecordForAdmin(Long recordId) {
        return toResponse(requireRecord(recordId));
    }

    /** 照片上传：关联 RustFS object_key（对齐 A1 附件机制），首张照片推进 IN_PROGRESS。 */
    public CpsRoomCheckRecordResponse uploadPhoto(Long recordId, Long itemId, MultipartFile file, String empNo) {
        CpsRoomCheckRecord record = requireRecord(recordId);
        requireOwner(record, empNo);
        requireNotJudged(record);
        CpsRoomCheckRecordItem item = itemMapper.findById(itemId);
        if (item == null || !Objects.equals(item.getRecordId(), recordId)) {
            throw new IllegalArgumentException("check item not in record: " + itemId);
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("photo file is required");
        }
        String fileName = firstNonBlank(file.getOriginalFilename(), "room-check.jpg");
        String fileType = firstNonBlank(file.getContentType(), "application/octet-stream");
        String objectKey = "cps/room-check/" + UUID.randomUUID() + "-"
                + fileName.replace('\\', '_').replace('/', '_');
        try {
            storage.put(objectKey, file.getBytes(), fileType);
        } catch (Exception error) {
            throw new IllegalStateException("Unable to store room check photo in RustFS", error);
        }
        itemMapper.updatePhoto(itemId, objectKey, fileName);
        if (CpsRoomCheckRecordStatus.PENDING.name().equals(record.getRecordStatus())) {
            recordMapper.updateStatusFields(recordId, CpsRoomCheckRecordStatus.IN_PROGRESS.name(), LocalDateTime.now());
        }
        return toResponse(requireRecord(recordId));
    }

    /**
     * 提交并同步判定（PRD 24.2/24.3）：
     * 1) 全部明细必须有照片（证据要求，不许绕过）；
     * 2) C-04 判定 TYPE_MISMATCH/UNJUDGEABLE ⇒ 阻断提交，须重拍/补拍（AC-08/09）；
     * 3) PASS/FAIL ⇒ 回写明细，进入 AWAIT_CONFIRM 待点检人确认（PRD 24.2.2），确认后计分锁定；
     * 4) 服务未起/超时 ⇒ 降级 judge_result=PENDING、score=NULL、JUDGED（锁定，admin rejudge 兜底），不阻塞流程。
     */
    public CpsRoomCheckRecordResponse submit(Long recordId, String empNo) {
        CpsRoomCheckRecord record = requireRecord(recordId);
        requireOwner(record, empNo);
        requireNotJudged(record);
        List<CpsRoomCheckRecordItem> items = itemMapper.findByRecordId(recordId);
        requirePhotos(items);

        int attempt = nextAttempt(record);
        record.setJudgeAttempt(attempt);
        List<CpsRoomCheckJudgeClient.ItemJudge> judged = judgeClient.judge(record, items, attempt);
        LocalDateTime now = LocalDateTime.now();
        if (judged == null) {
            // 降级：PENDING 回写明细，仍推 AWAIT_CONFIRM 让点检人走 confirm 流程人工判定
            // （PRD 24.2.2 显式要求：AI 不可达/降级时由点检人决策，不允许静默锁定计分）
            for (CpsRoomCheckRecordItem item : items) {
                itemMapper.updateJudgeResult(item.getId(),
                        CpsRoomCheckJudgeOutcome.PENDING.name(),
                        "judge service unavailable: degraded to PENDING, awaiting point-checker manual decision",
                        CpsRoomCheckJudgeOutcome.PENDING.name(), now);
            }
            record.setRecordStatus(CpsRoomCheckRecordStatus.AWAIT_CONFIRM.name());
            record.setJudgeStatus("PENDING");
            record.setScore(null); // 待点检人 confirm 后按 final_result 计分
            record.setSubmittedAt(now);
            recordMapper.updateJudgeResult(record);
            refreshPlanTaskStatus(record.getPlanTaskId());
            return toResponse(requireRecord(recordId));
        }

        List<String> retake = new ArrayList<>();
        boolean hasPending = false;
        int deduct = 0;
        for (CpsRoomCheckJudgeClient.ItemJudge result : judged) {
            CpsRoomCheckRecordItem item = items.stream()
                    .filter(i -> Objects.equals(i.getCheckItemId(), result.itemId)).findFirst().orElse(null);
            if (item == null) {
                continue;
            }
            String outcome = result.outcome == null ? CpsRoomCheckJudgeOutcome.UNJUDGEABLE.name() : result.outcome;
            if (CpsRoomCheckJudgeOutcome.PENDING.name().equals(outcome)) {
                hasPending = true; // 单 item 降级（SKIPPED/技术失败）：进 AWAIT_CONFIRM 由点检人决策
            }
            if (CpsRoomCheckJudgeOutcome.TYPE_MISMATCH.name().equals(outcome)
                    || CpsRoomCheckJudgeOutcome.UNJUDGEABLE.name().equals(outcome)) {
                retake.add(item.getItemCode() + ":" + outcome);
            }
            if (CpsRoomCheckJudgeOutcome.FAIL.name().equals(outcome)) {
                deduct += item.getDeductScore() == null ? 0 : item.getDeductScore();
            }
            itemMapper.updateJudgeResult(item.getId(), outcome, result.reason, outcome, now);
        }
        if (!retake.isEmpty()) {
            throw new IllegalArgumentException(
                    "retake required (not scored as unqualified, PRD 24.2.3): " + String.join(",", retake));
        }
        if (hasPending) {
            // 部分/全部明细 PENDING：进 AWAIT_CONFIRM 由点检人确认/修正（不再静默锁定计分）
            record.setRecordStatus(CpsRoomCheckRecordStatus.AWAIT_CONFIRM.name());
            record.setJudgeStatus("PENDING");
            record.setScore(null);
            record.setSubmittedAt(now);
            recordMapper.updateJudgeResult(record);
            refreshPlanTaskStatus(record.getPlanTaskId());
            return toResponse(requireRecord(recordId));
        }
        record.setRecordStatus(CpsRoomCheckRecordStatus.AWAIT_CONFIRM.name());
        record.setJudgeStatus("SUCCESS");
        record.setScore(null); // 待确认：点检人确认/修正后按 final_result 计分（PRD 24.2.6）
        record.setSubmittedAt(now);
        recordMapper.updateJudgeResult(record);
        refreshPlanTaskStatus(record.getPlanTaskId());
        return toResponse(requireRecord(recordId));
    }

    /**
     * PRD 24.2.2/24.2.5/24.2.6：点检人确认/修正判定结果并锁定。
     * - 仅 AWAIT_CONFIRM 状态、本人可操作；未传决定的项按 AI 判定原样确认；
     * - 仅 PASS/FAIL 可确认或修正（TYPE_MISMATCH/UNJUDGEABLE 在 submit 已阻断，不能手工改合格绕过证据要求）；
     * - 与 AI 判定不同的项落修正留痕（修正人/时间/原因）；
     * - score=max(0,100−final=FAIL 扣分和)：按最终结果计分。
     */
    public CpsRoomCheckRecordResponse confirm(Long recordId, CpsRoomCheckConfirmRequest request) {
        String empNo = requireText(request.getEmpNo(), "empNo is required");
        CpsRoomCheckRecord record = requireRecord(recordId);
        requireOwner(record, empNo);
        if (!CpsRoomCheckRecordStatus.AWAIT_CONFIRM.name().equals(record.getRecordStatus())) {
            throw new IllegalArgumentException(
                    "record is not awaiting confirmation (PRD 24.2.2): " + record.getRecordStatus());
        }
        List<CpsRoomCheckRecordItem> items = itemMapper.findByRecordId(recordId);
        java.util.Map<Long, CpsRoomCheckConfirmRequest.ItemDecision> decisions = new java.util.HashMap<>();
        if (request.getItems() != null) {
            for (CpsRoomCheckConfirmRequest.ItemDecision decision : request.getItems()) {
                if (decision.getItemId() == null) {
                    throw new IllegalArgumentException("itemId is required in decisions");
                }
                decisions.put(decision.getItemId(), decision);
            }
        }
        LocalDateTime now = LocalDateTime.now();
        int deduct = 0;
        for (CpsRoomCheckRecordItem item : items) {
            String judge = item.getJudgeResult() == null ? "" : item.getJudgeResult();
            if (!CpsRoomCheckJudgeOutcome.PASS.name().equals(judge)
                    && !CpsRoomCheckJudgeOutcome.FAIL.name().equals(judge)
                    && !CpsRoomCheckJudgeOutcome.PENDING.name().equals(judge)) {
                throw new IllegalArgumentException(
                        "item has no confirmable judge result (retake required): " + item.getItemCode());
            }
            String finalResult = judge;
            CpsRoomCheckConfirmRequest.ItemDecision decision = decisions.remove(item.getId());
            if (decision != null && decision.getFinalResult() != null) {
                String outcome = decision.getFinalResult().trim().toUpperCase(Locale.ROOT);
                if (!CpsRoomCheckJudgeOutcome.PASS.name().equals(outcome)
                        && !CpsRoomCheckJudgeOutcome.FAIL.name().equals(outcome)) {
                    throw new IllegalArgumentException(
                            "finalResult must be PASS or FAIL (PRD 24.2.3): " + outcome);
                }
                finalResult = outcome;
            }
            boolean modified = !finalResult.equals(judge);
            itemMapper.updateFinalResult(item.getId(), finalResult,
                    modified ? empNo : null,
                    modified ? now : null,
                    modified ? trimToNull(decision == null ? null : decision.getReason()) : null);
            if (CpsRoomCheckJudgeOutcome.FAIL.name().equals(finalResult)) {
                deduct += item.getDeductScore() == null ? 0 : item.getDeductScore();
            }
        }
        if (!decisions.isEmpty()) {
            throw new IllegalArgumentException("decision for unknown item id: " + decisions.keySet().iterator().next());
        }
        record.setRecordStatus(CpsRoomCheckRecordStatus.JUDGED.name());        record.setJudgeStatus("SUCCESS");
        record.setScore(Math.max(0, 100 - deduct));
        int updated = recordMapper.updateConfirmResult(record);
        if (updated == 0) {
            throw new IllegalStateException("record confirmation conflicted (already confirmed?)");
        }
        refreshPlanTaskStatus(record.getPlanTaskId());
        return toResponse(requireRecord(recordId));
    }

    /** B：admin 管理台点检记录分页列表（PRD §22 追溯）。 */
    public CpsPageResponse<CpsRoomCheckAdminRowDto> adminPage(
            String recordStatus, String judgeStatus, String roomCode, int page, int pageSize) {
        int size = Math.min(Math.max(pageSize, 1), 100);
        int offset = (Math.max(page, 1) - 1) * size;
        long total = recordMapper.countRecords(
                emptyToNull(recordStatus), emptyToNull(judgeStatus), trimToNull(roomCode));
        List<CpsRoomCheckAdminRowDto> rows = recordMapper.pageRecords(
                emptyToNull(recordStatus), emptyToNull(judgeStatus), trimToNull(roomCode), offset, size);
        return new CpsPageResponse<>(total, rows);
    }

    /**
     * 清单⑤（波次7 J线联调）：PENDING 降级单补判定重跑入口（admin）。
     * 仅限 record_status=JUDGED 且 judge_status=PENDING 的单；重调 C-04（attempt 递增 ⇒ 新幂等键，
     * 绕过 Python 侧 SKIPPED/业务结果的缓存重放）→ 回写明细/分数/状态；重拍类结果不抛错（单已锁定，
     * 由 admin 决策），仍降级则保持 PENDING 待下轮。
     */
    public CpsRoomCheckRejudgeResponse rejudge(Long recordId) {
        CpsRoomCheckRecord record = requireRecord(recordId);
        if (!CpsRoomCheckRecordStatus.JUDGED.name().equals(record.getRecordStatus())) {
            throw new IllegalArgumentException("record not in JUDGED state: " + record.getRecordStatus());
        }
        if (!"PENDING".equals(record.getJudgeStatus())) {
            throw new IllegalArgumentException("record judge_status is not PENDING: " + record.getJudgeStatus());
        }
        List<CpsRoomCheckRecordItem> items = itemMapper.findByRecordId(recordId);
        requirePhotos(items);

        int attempt = nextAttempt(record);
        record.setJudgeAttempt(attempt);
        List<CpsRoomCheckJudgeClient.ItemJudge> judged = judgeClient.judge(record, items, attempt);
        LocalDateTime now = LocalDateTime.now();

        List<CpsRoomCheckRejudgeResponse.ItemResult> results = new ArrayList<>();
        List<String> retake = new ArrayList<>();
        boolean degraded = judged == null;
        int deduct = 0;
        if (judged == null) {
            for (CpsRoomCheckRecordItem item : items) {
                itemMapper.updateJudgeResult(item.getId(), CpsRoomCheckJudgeOutcome.PENDING.name(),
                        "judge service unavailable: still degraded after rejudge",
                        CpsRoomCheckJudgeOutcome.PENDING.name(), now);
                results.add(new CpsRoomCheckRejudgeResponse.ItemResult(
                        item.getItemCode(), CpsRoomCheckJudgeOutcome.PENDING.name(),
                        "judge service unavailable: still degraded after rejudge"));
            }
        } else {
            for (CpsRoomCheckJudgeClient.ItemJudge result : judged) {
                CpsRoomCheckRecordItem item = items.stream()
                        .filter(i -> Objects.equals(i.getCheckItemId(), result.itemId)).findFirst().orElse(null);
                if (item == null) {
                    continue;
                }
                String outcome = result.outcome == null ? CpsRoomCheckJudgeOutcome.UNJUDGEABLE.name() : result.outcome;
                if (CpsRoomCheckJudgeOutcome.PENDING.name().equals(outcome)) {
                    degraded = true;
                }
                if (CpsRoomCheckJudgeOutcome.TYPE_MISMATCH.name().equals(outcome)
                        || CpsRoomCheckJudgeOutcome.UNJUDGEABLE.name().equals(outcome)) {
                    retake.add(item.getItemCode() + ":" + outcome);
                }
                if (CpsRoomCheckJudgeOutcome.FAIL.name().equals(outcome)) {
                    deduct += item.getDeductScore() == null ? 0 : item.getDeductScore();
                }
                itemMapper.updateJudgeResult(item.getId(), outcome, result.reason, outcome, now);
                results.add(new CpsRoomCheckRejudgeResponse.ItemResult(item.getItemCode(), outcome, result.reason));
            }
        }
        if (!degraded && retake.isEmpty()) {
            record.setJudgeStatus("SUCCESS");
            record.setScore(Math.max(0, 100 - deduct));
        } else {
            record.setJudgeStatus("PENDING"); // 仍降级或出现重拍类结果：保持待判定，可再次重跑
            record.setScore(null);
        }
        recordMapper.updateRejudgeResult(record);
        refreshPlanTaskStatus(record.getPlanTaskId());

        CpsRoomCheckRejudgeResponse response = new CpsRoomCheckRejudgeResponse();
        response.setRecordId(recordId);
        response.setAttempt(attempt);
        response.setJudgeStatus(record.getJudgeStatus());
        response.setScore(record.getScore());
        response.setRetakeRequired(retake);
        response.setResults(results);
        return response;
    }

    /** 全部明细必须有照片（证据要求，不许绕过，PRD 24.2）。 */
    private void requirePhotos(List<CpsRoomCheckRecordItem> items) {
        List<String> missingPhoto = new ArrayList<>();
        for (CpsRoomCheckRecordItem item : items) {
            if (item.getPhotoObjectKey() == null || item.getPhotoObjectKey().isEmpty()) {
                missingPhoto.add(item.getItemCode());
            }
        }
        if (!missingPhoto.isEmpty()) {
            throw new IllegalArgumentException(
                    "photo evidence missing for items (retake required, cannot bypass): " + String.join(",", missingPhoto));
        }
    }

    /** 下一判定轮数：先落库占位（judge_attempt 只增），重拍/重跑后必然换新幂等键。 */
    private int nextAttempt(CpsRoomCheckRecord record) {
        int attempt = (record.getJudgeAttempt() == null ? 0 : record.getJudgeAttempt()) + 1;
        recordMapper.updateJudgeAttempt(record.getId(), attempt);
        return attempt;
    }

    /** 任务完成度：reference_object_key 覆盖房间全部 JUDGED ⇒ COMPLETED（未列房间则任一 JUDGED 即完成）。 */
    void refreshPlanTaskStatus(Long planTaskId) {
        if (planTaskId == null) {
            return;
        }
        CpsInspectionPlanTask task = planTaskMapper.findById(planTaskId);
        if (task == null || "CANCELLED".equals(task.getTaskStatus()) || "COMPLETED".equals(task.getTaskStatus())) {
            return;
        }
        List<String> roomCodes = CpsRoomCheckTaskResponse.parseRoomCodes(task.getReferenceObjectKey());
        List<CpsRoomCheckRecord> records = recordMapper.findByPlanTaskId(planTaskId);
        long judged = records.stream()
                .filter(r -> CpsRoomCheckRecordStatus.JUDGED.name().equals(r.getRecordStatus())).count();
        boolean completed = roomCodes.isEmpty() ? judged >= 1 : judged >= roomCodes.size();
        planTaskMapper.updateTaskStatus(planTaskId, completed ? "COMPLETED" : "IN_PROGRESS");
    }

    private int countJudgedRooms(CpsInspectionPlanTask task) {
        List<CpsRoomCheckRecord> records = recordMapper.findByPlanTaskId(task.getId());
        return (int) records.stream()
                .filter(r -> CpsRoomCheckRecordStatus.JUDGED.name().equals(r.getRecordStatus())).count();
    }

    private CpsRoomCheckRecordResponse toResponse(CpsRoomCheckRecord record) {
        return CpsRoomCheckRecordResponse.from(record, itemMapper.findByRecordId(record.getId()),
                storage::publicObjectUrl);
    }

    private CpsRoomCheckRecord requireRecord(Long recordId) {
        CpsRoomCheckRecord record = recordMapper.findById(recordId);
        if (record == null) {
            throw new IllegalArgumentException("room check record not found: " + recordId);
        }
        return record;
    }

    /** PRD 24.2.1/24.2.2：以登录身份识别点检人，仅本人可操作本人单。 */
    private void requireOwner(CpsRoomCheckRecord record, String empNo) {
        if (empNo == null || empNo.trim().isEmpty()) {
            throw new IllegalArgumentException("empNo is required");
        }
        if (!record.getCheckEmpNo().equals(empNo)) {
            throw new IllegalArgumentException("only the owner can operate this record: " + record.getCheckEmpNo());
        }
    }

    private void requireNotJudged(CpsRoomCheckRecord record) {
        if (CpsRoomCheckRecordStatus.JUDGED.name().equals(record.getRecordStatus())) {
            throw new IllegalArgumentException("record already submitted and locked (PRD 24.2.4)");
        }
    }

    /** applicable_room_types CSV 空=全部适用；否则房间类型须在清单内。 */
    static boolean appliesToRoom(String applicableRoomTypes, String roomType) {
        if (applicableRoomTypes == null || applicableRoomTypes.trim().isEmpty()) {
            return true;
        }
        String normalized = roomType == null ? "" : roomType.trim();
        return Arrays.stream(applicableRoomTypes.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .anyMatch(s -> s.equalsIgnoreCase(normalized));
    }

    private static String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static String firstNonBlank(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.trim();
    }

    private static String emptyToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    /** 保留大小写的去空白（房间编码模糊匹配用，emptyToNull 会大写化不适用）。 */
    private static String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
