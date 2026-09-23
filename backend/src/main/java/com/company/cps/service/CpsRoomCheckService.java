package com.company.cps.service;

import com.company.cps.domain.CpsCheckItem;
import com.company.cps.domain.CpsInspectionPlanTask;
import com.company.cps.domain.CpsInspectionPlanTaskType;
import com.company.cps.domain.CpsRoom;
import com.company.cps.domain.CpsRoomCheckJudgeOutcome;
import com.company.cps.domain.CpsRoomCheckRecord;
import com.company.cps.domain.CpsRoomCheckRecordItem;
import com.company.cps.domain.CpsRoomCheckRecordStatus;
import com.company.cps.dto.CpsRoomCheckRecordResponse;
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
 * 状态机：PENDING待执行 → IN_PROGRESS执行中（首张照片）→ JUDGED已判定（提交+同步判定，锁定）。
 * 判定（C-04 同步）：成功回写明细；TYPE_MISMATCH/UNJUDGEABLE 阻断提交须补拍；服务未起降级 PENDING 不阻塞。
 * 评分（B4 消费）：score=max(0,100−FAIL 项 deduct_score 和)；降级单 score=NULL 不计入排名。
 *
 * 注：判定 HTTP 调用刻意不包 @Transactional（避免长事务占连接），写库逐条自动提交；
 * 崩溃窗口内可能出现"明细已回写、单未 JUDGED"，重试提交可修复（幂等覆盖写）。
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
                empNo, CpsInspectionPlanTaskType.INSPECT_CHECK, emptyToNull(taskStatus));
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
     * 点检项筛选：enabled 且 APPLICABLE 且 applicable_room_types 为空或含房间类型（PRD §23.2）。
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
            if (task.getTaskType() != CpsInspectionPlanTaskType.INSPECT_CHECK) {
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

        List<CpsRoomCheckRecordItem> items = new ArrayList<>();
        for (CpsCheckItem item : checkItemMapper.findAll(null, "APPLICABLE", true)) {
            if (!appliesToRoom(item.getApplicableRoomTypes(), room.getRoomType())) {
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
            throw new IllegalArgumentException("no applicable check items for room type: " + room.getRoomType());
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
     * 3) PASS/FAIL ⇒ 回写明细并计分（100−FAIL扣分和，最低0），JUDGED 锁定；
     * 4) 服务未起/超时 ⇒ 降级 judge_result=PENDING、score=NULL、JUDGED，不阻塞流程。
     */
    public CpsRoomCheckRecordResponse submit(Long recordId, String empNo) {
        CpsRoomCheckRecord record = requireRecord(recordId);
        requireOwner(record, empNo);
        requireNotJudged(record);
        List<CpsRoomCheckRecordItem> items = itemMapper.findByRecordId(recordId);
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

        List<CpsRoomCheckJudgeClient.ItemJudge> judged = judgeClient.judge(record, items);
        LocalDateTime now = LocalDateTime.now();
        if (judged == null) {
            // 降级：PENDING 回写明细与单，不阻塞流程（联调留 J 线补判定）
            for (CpsRoomCheckRecordItem item : items) {
                itemMapper.updateJudgeResult(item.getId(),
                        CpsRoomCheckJudgeOutcome.PENDING.name(),
                        "judge service unavailable: degraded to PENDING (J-line will re-judge)",
                        CpsRoomCheckJudgeOutcome.PENDING.name(), now);
            }
            record.setRecordStatus(CpsRoomCheckRecordStatus.JUDGED.name());
            record.setJudgeStatus("PENDING");
            record.setScore(null);
            record.setSubmittedAt(now);
            recordMapper.updateJudgeResult(record);
            refreshPlanTaskStatus(record.getPlanTaskId());
            return toResponse(requireRecord(recordId));
        }

        List<String> retake = new ArrayList<>();
        int deduct = 0;
        for (CpsRoomCheckJudgeClient.ItemJudge result : judged) {
            CpsRoomCheckRecordItem item = items.stream()
                    .filter(i -> Objects.equals(i.getCheckItemId(), result.itemId)).findFirst().orElse(null);
            if (item == null) {
                continue;
            }
            String outcome = result.outcome == null ? CpsRoomCheckJudgeOutcome.UNJUDGEABLE.name() : result.outcome;
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
        int score = Math.max(0, 100 - deduct);
        record.setRecordStatus(CpsRoomCheckRecordStatus.JUDGED.name());
        record.setJudgeStatus("SUCCESS");
        record.setScore(score);
        record.setSubmittedAt(now);
        recordMapper.updateJudgeResult(record);
        refreshPlanTaskStatus(record.getPlanTaskId());
        return toResponse(requireRecord(recordId));
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
}
