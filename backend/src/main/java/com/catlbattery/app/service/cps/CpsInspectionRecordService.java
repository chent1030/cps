package com.catlbattery.app.service.cps;

import com.catlbattery.domain.entity.cps.CpsCheckItem;
import com.catlbattery.domain.entity.cps.CpsInspectionArea;
import com.catlbattery.domain.entity.cps.CpsInspectionPlan;
import com.catlbattery.domain.entity.cps.CpsInspectionPlanTask;
import com.catlbattery.domain.entity.cps.CpsInspectionRecord;
import com.catlbattery.domain.entity.cps.CpsInspectionRecordItem;
import com.catlbattery.domain.entity.cps.CpsInspectionRecordLog;
import com.catlbattery.domain.entity.cps.CpsInspectionType;
import com.catlbattery.domain.entity.cps.CpsIssueAttachment;
import com.catlbattery.domain.entity.cps.dto.CpsInspectionRectifyRequest;
import com.catlbattery.domain.entity.cps.dto.CpsMyInspectionTaskDto;
import com.catlbattery.infra.mapper.cps.CpsCheckItemMapper;
import com.catlbattery.infra.mapper.cps.CpsInspectionAreaMapper;
import com.catlbattery.infra.mapper.cps.CpsInspectionPlanMapper;
import com.catlbattery.infra.mapper.cps.CpsInspectionPlanTaskMapper;
import com.catlbattery.infra.mapper.cps.CpsInspectionRecordItemMapper;
import com.catlbattery.infra.mapper.cps.CpsInspectionRecordLogMapper;
import com.catlbattery.infra.mapper.cps.CpsInspectionRecordMapper;
import com.catlbattery.infra.mapper.cps.CpsInspectionTypeMapper;
import com.catlbattery.infra.mapper.cps.CpsIssueAttachmentMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.catlbattery.common.util.CpsStrings;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一巡检记录服务（Phase C1）：开始执行 / 保存单项结果 / 提交计分 / 确认锁定。
 *
 * 执行模型：
 * - 分区域类型：start(typeCode, areaId) → 按区域 checkItemIds 生成明细快照；
 * - 不分区域类型：start(typeCode, null) → 按类型 checkItemIds 生成明细快照；
 * - 明细为空（未配置点检项）拒绝开始；
 * - submit：校验 PHOTO 项最低张数 → 计分 max(0, 100-Σ不合格扣分) → JUDGED 锁定；
 *   PASS_FAIL 项 final_result=pass_result；无 PASS_FAIL 的项不计分。
 */
@Service
public class CpsInspectionRecordService {

    private static final Logger log = LoggerFactory.getLogger(CpsInspectionRecordService.class);

    /** 导出上限：避免一次导出把整库拉进内存。 */
    private static final int MAX_EXPORT_ROWS = 10000;

    private static final DateTimeFormatter EXPORT_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private CpsInspectionRecordMapper recordMapper;

    @Autowired
    private CpsInspectionRecordItemMapper itemMapper;

    @Autowired
    private CpsInspectionAreaMapper areaMapper;

    @Autowired
    private CpsInspectionTypeMapper typeMapper;

    @Autowired
    private CpsCheckItemMapper checkItemMapper;

    @Autowired
    private CpsInspectionPlanTaskMapper planTaskMapper;

    @Autowired
    private CpsInspectionPlanMapper planMapper;

    @Autowired
    private CpsInspectionRecordLogMapper recordLogMapper;

    @Autowired
    private CpsIssueAttachmentMapper attachmentMapper;

    @Autowired
    private CpsInspectionJudgeClient judgeClient;

    @Autowired
    private CpsAgentFrameworkClient agentClient;

    // ============================================================
    // 开始执行
    // ============================================================

    @Transactional
    public CpsInspectionRecord start(String typeCode, Long areaId, Long planTaskId, Long planId,
                                     String empNo, String empName) {
        requireText(typeCode, "typeCode");
        requireText(empNo, "empNo");
        CpsInspectionType type = typeMapper.findByCode(typeCode.trim());
        require(type != null, "巡检类型不存在: " + typeCode);
        require(type.getEnabled() != null && type.getEnabled() == 1, "巡检类型已停用: " + typeCode);

        boolean zoned = type.getZoneFlag() != null && type.getZoneFlag() == 1;
        List<CpsCheckItem> items;
        CpsInspectionArea area = null;
        if (zoned) {
            require(areaId != null, "分区域巡检必须指定 areaId");
            area = areaMapper.findById(areaId);
            require(area != null, "巡检区域不存在: id=" + areaId);
            require(Boolean.TRUE.equals(area.getEnabled()), "巡检区域已停用: " + area.getAreaCode());
            require(typeCode.trim().equals(area.getTypeCode()),
                    "区域不属于该巡检类型: area=" + area.getAreaCode() + " type=" + typeCode);
            items = resolveItems(area.getCheckItemIds());
        } else {
            require(areaId == null, "该类型不分区域，areaId 必须为空");
            items = resolveItems(type.getCheckItemIds());
        }
        require(!items.isEmpty(), "该巡检未配置点检项，无法开始（请联系管理员）");

        LocalDateTime now = LocalDateTime.now();
        CpsInspectionRecord record = new CpsInspectionRecord();
        record.setTypeCode(type.getCode());
        record.setAreaId(area == null ? null : area.getId());
        record.setAreaCode(area == null ? null : area.getAreaCode());
        record.setAreaName(area == null ? null : area.getAreaName());
        record.setPlanTaskId(planTaskId);
        record.setPlanId(planId);
        record.setCheckEmpNo(empNo.trim());
        record.setCheckEmpName(trimToNull(empName));
        record.setRecordStatus("IN_PROGRESS");
        record.setStartedAt(now);
        recordMapper.insert(record);

        List<CpsInspectionRecordItem> snapshots = new ArrayList<>();
        for (CpsCheckItem ci : items) {
            CpsInspectionRecordItem item = new CpsInspectionRecordItem();
            item.setRecordId(record.getId());
            item.setCheckItemId(ci.getId());
            item.setItemCode(ci.getItemCode());
            item.setContent(ci.getContent());
            item.setCheckTypes(ci.getCheckTypes() == null ? "PASS_FAIL" : ci.getCheckTypes());
            item.setPhotoMinCount(ci.getPhotoMinCount());
            item.setPhotoCriteria(ci.getPhotoCriteria());
            item.setDeductScore(ci.getDeductScore() == null ? 0 : ci.getDeductScore());
            item.setConfigVersion(ci.getConfigVersion() == null ? 1 : ci.getConfigVersion());
            snapshots.add(item);
        }
        itemMapper.batchInsert(snapshots);
        log.info("Inspection record started: id={} type={} areaId={} items={}",
                record.getId(), typeCode, areaId, snapshots.size());
        // Phase D：计划任务状态联动 PENDING→IN_PROGRESS（不回退已完成/已取消）
        advancePlanTask(planTaskId, "IN_PROGRESS");
        return recordMapper.findById(record.getId());
    }

    /**
     * 计划任务状态推进（Phase D）：
     * - IN_PROGRESS：仅当前 PENDING 时推进；
     * - COMPLETED：仅当该任务全部覆盖区域都已判定（JUDGED 记录数 ≥ 覆盖区域数；不分区域/未限定=1）时推进。
     */
    private void advancePlanTask(Long planTaskId, String target) {
        if (planTaskId == null) return;
        try {
            CpsInspectionPlanTask task = planTaskMapper.findById(planTaskId);
            if (task == null || "CANCELLED".equals(task.getTaskStatus()) || "COMPLETED".equals(task.getTaskStatus())) {
                return;
            }
            if ("IN_PROGRESS".equals(target)) {
                if ("PENDING".equals(task.getTaskStatus())) {
                    planTaskMapper.updateTaskStatus(planTaskId, "IN_PROGRESS");
                }
                return;
            }
            if ("COMPLETED".equals(target)) {
                int coverage = 1;
                String codes = task.getCoverageAreaCodes();
                if (codes != null && !codes.trim().isEmpty()) {
                    coverage = (int) java.util.Arrays.stream(codes.split(",")).filter((s) -> !s.trim().isEmpty()).count();
                }
                long judged = recordMapper.countJudgedByPlanTask(planTaskId);
                if (judged >= coverage) {
                    planTaskMapper.updateTaskStatus(planTaskId, "COMPLETED");
                    log.info("Plan task completed: id={} judged={}/{}", planTaskId, judged, coverage);
                }
            }
        } catch (Exception e) {
            log.warn("Plan task status advance failed: planTaskId={} target={} cause={}", planTaskId, target, e.getMessage());
        }
    }

    // ============================================================
    // 执行中：保存单项结果
    // ============================================================

    @Transactional
    public CpsInspectionRecordItem saveItemResult(Long itemId, String passResult, String textValue,
                                                  String photoUrl, String photoFileName, Integer photoCount) {
        CpsInspectionRecordItem item = mustGetItem(itemId);
        CpsInspectionRecord record = mustGetRecord(item.getRecordId());
        require("IN_PROGRESS".equals(record.getRecordStatus()),
                "记录状态不允许编辑: " + record.getRecordStatus());
        itemMapper.updateResult(itemId, trimToNull(passResult), trimToNull(textValue),
                trimToNull(photoUrl), trimToNull(photoFileName),
                photoCount == null ? 0 : photoCount);
        return itemMapper.findById(itemId);
    }

    // ============================================================
    // 提交计分（JUDGED 锁定）
    // ============================================================

    @Transactional
    public CpsInspectionRecord submit(Long recordId) {
        CpsInspectionRecord record = mustGetRecord(recordId);
        require("IN_PROGRESS".equals(record.getRecordStatus()),
                "仅执行中的记录可提交: status=" + record.getRecordStatus());
        List<CpsInspectionRecordItem> items = itemMapper.findByRecord(recordId);
        require(!items.isEmpty(), "记录无点检项明细");

        // 1) 完整性校验（不合格即拒绝提交，不进入 AI 初审）
        for (CpsInspectionRecordItem item : items) {
            String types = item.getCheckTypes() == null ? "" : item.getCheckTypes();
            if (types.contains("PHOTO")) {
                int min = item.getPhotoMinCount() == null ? 1 : item.getPhotoMinCount();
                int actual = item.getPhotoCount() == null ? 0 : item.getPhotoCount();
                require(actual >= min, "点检项「" + item.getContent() + "」照片不足：需至少 " + min + " 张，当前 " + actual + " 张");
            }
            if (types.contains("PASS_FAIL")) {
                require(item.getPassResult() != null, "点检项「" + item.getContent() + "」尚未判定是否合格");
            }
            if (types.contains("TEXT")) {
                require(item.getTextValue() != null && !item.getTextValue().trim().isEmpty(),
                        "点检项「" + item.getContent() + "」需要填写文本说明");
            }
        }

        // 2) AI 初审（C-04 逐项视觉判定）+ Java 侧扣分策略
        int round = (record.getReviewRound() == null ? 0 : record.getReviewRound()) + 1;
        List<CpsInspectionJudgeClient.ItemJudge> judges = judgeClient.judge(record, items, round);
        int deduct = 0;
        boolean anyFail = false;
        boolean anyPass = false;
        List<String> summary = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            CpsInspectionRecordItem item = items.get(i);
            CpsInspectionJudgeClient.ItemJudge judge = i < judges.size() ? judges.get(i) : null;
            String verdict = judge == null ? "PENDING" : judge.outcome;
            String finalResult;
            if ("PASS".equals(verdict) || "FAIL".equals(verdict)) {
                finalResult = verdict;
            } else {
                // AI 无结论（未启用/无照片/调用失败）：沿用点检员自判，不阻塞流程
                finalResult = item.getPassResult();
            }
            itemMapper.updateJudge(item.getId(), judge == null ? null : judge.outcome,
                    judge == null ? null : judge.reason, finalResult);
            if ("FAIL".equals(finalResult)) {
                deduct += item.getDeductScore() == null ? 0 : item.getDeductScore();
            }
            if ("PASS".equals(verdict)) {
                anyPass = true;
                summary.add(item.getContent() + "=合格");
            } else if ("FAIL".equals(verdict)) {
                anyFail = true;
                summary.add(item.getContent() + "=不合格");
            }
        }
        int score = Math.max(0, 100 - deduct);
        String aiOverall = anyFail ? "PROBLEM" : (anyPass ? "PASS" : null);
        String aiReason = summary.isEmpty() ? "AI 初审无结论（人工复核）" : String.join("；", summary);
        // 整改责任人 = 区域责任人（cps_inspection_area.keeper_emp_no）；未配置区域的类型回退检查人自己
        CpsInspectionArea area = record.getAreaId() == null ? null : areaMapper.findById(record.getAreaId());
        String keeperEmpNo = area == null || CpsStrings.isBlank(area.getKeeperEmpNo()) ? record.getCheckEmpNo() : area.getKeeperEmpNo();
        String keeperEmpName = area == null || CpsStrings.isBlank(area.getKeeperEmpNo()) ? record.getCheckEmpName() : area.getKeeperEmpName();

        // 3) 进入 web 人工复核（Android 不再承载复核动作）
        int rows = recordMapper.updateToReview(recordId, "PENDING_REVIEW", aiOverall, score, aiReason,
                round, keeperEmpNo, keeperEmpName);
        require(rows == 1, "提交失败（状态已变化，请刷新）");
        insertLog(recordId, "SUBMIT", "IN_PROGRESS", "PENDING_REVIEW", record.getCheckEmpNo(), record.getCheckEmpName(),
                "提交复核（第 " + round + " 轮），AI 初审：" + aiReason + "，得分 " + score);
        log.info("Inspection record submitted for review: id={} round={} score={} ai={}", recordId, round, score, aiOverall);
        return recordMapper.findById(recordId);
    }

    // ============================================================
    // 人工复核（web）/ 整改（Android）
    // ============================================================

    /**
     * web 复核裁决：通过 → PENDING_RECTIFY（办理人=区域整改责任人）；退回 → IN_PROGRESS（办理人=提交人，可改可重提）。
     * 退回强制填写意见；重提会重新跑 AI 初审（轮数 +1，扣分重算）。
     */
    @Transactional
    public CpsInspectionRecord review(Long recordId, String decision, String opinion, String empNo, String empName) {
        CpsInspectionRecord record = mustGetRecord(recordId);
        require("PENDING_REVIEW".equals(record.getRecordStatus()),
                "仅待复核的记录可裁决: status=" + record.getRecordStatus());
        String toStatus;
        String handlerEmpNo;
        String handlerEmpName;
        if ("PASS".equals(decision)) {
            toStatus = "PENDING_RECTIFY";
            requireText(record.getRectifyEmpNo(), "该记录未确定整改责任人（区域责任人），无法进入整改");
            handlerEmpNo = record.getRectifyEmpNo();
            handlerEmpName = record.getRectifyEmpName();
        } else {
            requireText(opinion, "opinion");
            toStatus = "IN_PROGRESS";
            handlerEmpNo = record.getCheckEmpNo();
            handlerEmpName = record.getCheckEmpName();
        }
        int rows = recordMapper.updateReviewDecision(recordId, toStatus, empNo, empName, trimToNull(opinion),
                handlerEmpNo, handlerEmpName);
        require(rows == 1, "复核失败（状态已变化，请刷新）");
        insertLog(recordId, "PASS".equals(decision) ? "REVIEW_PASS" : "REVIEW_RETURN",
                "PENDING_REVIEW", toStatus, empNo, empName, trimToNull(opinion));
        log.info("Inspection record reviewed: id={} decision={} operator={}", recordId, decision, empNo);
        return recordMapper.findById(recordId);
    }

    /**
     * Android 整改提交（区域责任人）：投递 C-01 初审后直接落 JUDGED（流程结束）；文本/照片规则由 C-01 侧判定。
     * AI 投递失败不阻塞归档：结论写进 rectify_ai_reason，交人工追溯。
     */
    @Transactional
    public CpsInspectionRecord submitRectify(Long recordId, CpsInspectionRectifyRequest request,
                                             String empNo, String empName) {
        CpsInspectionRecord record = mustGetRecord(recordId);
        require("PENDING_RECTIFY".equals(record.getRecordStatus()),
                "仅待整改的记录可提交整改: status=" + record.getRecordStatus());
        require(empNo != null && empNo.equals(record.getRectifyEmpNo()), "仅整改责任人可提交整改");
        // 文本有效性（≥15 字、标点占比等）与照片张数由 C-01 侧 A8 规则判定，这里不再重复挡。
        List<CpsIssueAttachment> afterImages = new ArrayList<>();
        if (request.getAttachmentIds() != null) {
            for (Long attachmentId : request.getAttachmentIds()) {
                CpsIssueAttachment attachment = attachmentId == null ? null
                        : attachmentMapper.findById(attachmentId).orElse(null);
                if (attachment != null) {
                    afterImages.add(attachment);
                }
            }
        }

        // 整改前证据：巡检项回传的照片（同一张照片即整改前后对比的 before）。
        // 契约同 C-01 AttachmentRef.url：落库即 MinIO 绝对 URL，原样下发。
        List<Map<String, Object>> beforeRefs = new ArrayList<>();
        for (CpsInspectionRecordItem item : itemMapper.findByRecord(recordId)) {
            if (item.getPhotoUrl() == null || item.getPhotoUrl().trim().isEmpty()) {
                continue;
            }
            Map<String, Object> ref = new HashMap<>();
            ref.put("url", item.getPhotoUrl().trim());
            ref.put("file_name", item.getPhotoFileName() == null ? item.getContent() : item.getPhotoFileName());
            beforeRefs.add(ref);
            if (beforeRefs.size() >= 20) {
                break;
            }
        }

        String aiOverall = null;
        String aiReason;
        try {
            String reviewTaskRef = agentClient.triggerInspectionRectifyReview(record, request.getReason(),
                    request.getShortTermMeasure(), request.getLongTermMeasure(), beforeRefs, afterImages);
            if (reviewTaskRef == null) {
                aiReason = "AI 初审未启用，整改已归档（人工追溯）";
            } else {
                aiOverall = "SUBMITTED";
                aiReason = "已提交 AI 初审（task_ref=" + reviewTaskRef + "）";
            }
        } catch (RuntimeException error) {
            aiReason = "AI 初审投递失败，整改已归档（人工追溯）：" + error.getMessage();
            log.warn("Inspection rectification AI review failed: recordId={} cause={}", recordId, error.getMessage());
        }

        StringBuilder ids = new StringBuilder();
        for (CpsIssueAttachment attachment : afterImages) {
            if (ids.length() > 0) {
                ids.append(',');
            }
            ids.append(attachment.getId());
        }
        int rows = recordMapper.updateRectify(recordId, "JUDGED",
                nullToEmpty(request.getReason()).trim(),
                nullToEmpty(request.getShortTermMeasure()).trim(),
                nullToEmpty(request.getLongTermMeasure()).trim(),
                ids.toString(), aiOverall, aiReason);
        require(rows == 1, "整改提交失败（状态已变化，请刷新）");
        insertLog(recordId, "RECTIFY_SUBMIT", "PENDING_RECTIFY", "JUDGED", empNo, empName, aiReason);
        log.info("Inspection rectification submitted: id={} operator={} ai={}", recordId, empNo, aiOverall);
        // 计划任务完成度联动（与提交同口径：全部区域 JUDGED 即 COMPLETED）
        advancePlanTask(record.getPlanTaskId(), "COMPLETED");
        return recordMapper.findById(recordId);
    }

    /** web 复核列表：全部待复核记录（按提交时间升序，先提交先处理） */
    public List<CpsInspectionRecord> listPendingReview(int limit) {
        return recordMapper.findByStatus("PENDING_REVIEW", limit <= 0 ? 100 : Math.min(limit, 200));
    }

    /** Android「我的待整改」：当前用户作为整改责任人的记录 */
    public List<CpsInspectionRecord> listMyRectify(String empNo) {
        return recordMapper.findByHandler("PENDING_RECTIFY", empNo == null ? "" : empNo.trim(), 50);
    }

    // ============================================================
    // 查询
    // ============================================================

    public Map<String, Object> page(String typeCode, Long areaId, String recordStatus, String checkEmpNo,
                                    String keyword, int page, int pageSize) {
        if (page < 1) page = 1;
        if (pageSize < 1) pageSize = 20;
        if (pageSize > 200) pageSize = 200;
        int offset = (page - 1) * pageSize;
        List<CpsInspectionRecord> rows = recordMapper.findPage(trimToNull(typeCode), areaId,
                trimToNull(recordStatus), trimToNull(checkEmpNo), trimToNull(keyword), pageSize, offset);
        long total = recordMapper.countPage(trimToNull(typeCode), areaId,
                trimToNull(recordStatus), trimToNull(checkEmpNo), trimToNull(keyword));
        Map<String, Object> result = new HashMap<>();
        result.put("records", rows);
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);
        return result;
    }

    public Map<String, Object> detail(Long recordId) {
        CpsInspectionRecord record = mustGetRecord(recordId);
        List<CpsInspectionRecordItem> items = itemMapper.findByRecord(recordId);
        Map<String, Object> result = new HashMap<>();
        result.put("record", record);
        result.put("items", items);
        result.put("logs", recordLogMapper.findByRecord(recordId));
        result.put("rectifyAttachments", loadRectifyAttachments(record));
        return result;
    }

    /**
     * 巡检记录导出（Excel）：与列表相同的筛选条件，一次性取上限行数，按导出列顺序返回字符串行。
     * 状态、AI 结论都翻成中文，整改照片输出可访问的地址（多张以空格分隔）。
     */
    public List<String[]> exportRows(String typeCode, Long areaId, String recordStatus, String checkEmpNo,
                                     String keyword) {
        List<CpsInspectionRecord> rows = recordMapper.findPage(trimToNull(typeCode), areaId,
                trimToNull(recordStatus), trimToNull(checkEmpNo), trimToNull(keyword), MAX_EXPORT_ROWS, 0);
        List<String[]> data = new ArrayList<>();
        for (CpsInspectionRecord record : rows) {
            data.add(new String[] {
                    String.valueOf(record.getId()),
                    typeName(record.getTypeCode()),
                    firstNonBlank(record.getAreaName(), record.getAreaCode()),
                    firstNonBlank(record.getCheckEmpName(), record.getCheckEmpNo()),
                    recordStatusLabel(record.getRecordStatus()),
                    formatTime(record.getSubmittedAt()),
                    record.getScore() == null ? "" : String.valueOf(record.getScore()),
                    aiOverallLabel(record.getAiOverall()),
                    nullToEmpty(record.getAiReason()),
                    firstNonBlank(record.getReviewEmpName(), record.getReviewEmpNo()),
                    formatTime(record.getReviewedAt()),
                    nullToEmpty(record.getReviewOpinion()),
                    firstNonBlank(record.getRectifyEmpName(), record.getRectifyEmpNo()),
                    formatTime(record.getRectifySubmittedAt()),
                    nullToEmpty(record.getRectifyReason()),
                    nullToEmpty(record.getRectifyShortTerm()),
                    nullToEmpty(record.getRectifyLongTerm()),
                    rectifyPhotoUrls(record)
            });
        }
        return data;
    }

    private String typeName(String typeCode) {
        if (CpsStrings.isBlank(typeCode)) return "";
        CpsInspectionType type = typeMapper.findByCode(typeCode.trim());
        return type == null || CpsStrings.isBlank(type.getName()) ? typeCode.trim() : type.getName();
    }

    private String rectifyPhotoUrls(CpsInspectionRecord record) {
        List<String> urls = new ArrayList<>();
        for (CpsIssueAttachment attachment : loadRectifyAttachments(record)) {
            String url = photoUrl(attachment);
            if (!url.isEmpty()) urls.add(url);
        }
        return String.join(" ", urls);
    }

    /** 整改照片附件（ids 形如 "12,13"；web 详情与 Android 回显用） */
    private List<CpsIssueAttachment> loadRectifyAttachments(CpsInspectionRecord record) {
        List<CpsIssueAttachment> list = new ArrayList<>();
        String raw = record.getRectifyAttachmentIds();
        if (raw == null || raw.trim().isEmpty()) {
            return list;
        }
        for (String part : raw.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                attachmentMapper.findById(Long.parseLong(trimmed)).ifPresent(list::add);
            } catch (NumberFormatException ignored) {
                // 历史脏数据忽略
            }
        }
        return list;
    }


    /** Android「我的执行中」：当前用户 IN_PROGRESS 记录（恢复现场用） */
    public List<CpsInspectionRecord> listInProgress(String empNo) {
        return recordMapper.findPage(null, null, "IN_PROGRESS", empNo == null ? "" : empNo.trim(), null, 50, 0);
    }

    /**
     * Phase D：我的分配任务（计划建单后 target_emp_no=我 的未完成任务）。
     * 聚合计划标题 + 巡检类型元信息（名称/是否分区域）+ 覆盖区域，供 Android 任务列表直接渲染。
     * 历史枚举类型（INSPECT_*）不在 cps_inspection_type 表中：typeName 回退编码、zoneFlag 按 1（分区域）处理。
     */
    public List<CpsMyInspectionTaskDto> listMyTasks(String empNo) {
        requireText(empNo, "empNo");
        List<CpsInspectionPlanTask> tasks = planTaskMapper.findAssignedTasks(empNo.trim());
        List<CpsMyInspectionTaskDto> result = new ArrayList<>();
        for (CpsInspectionPlanTask task : tasks) {
            CpsMyInspectionTaskDto dto = new CpsMyInspectionTaskDto();
            dto.setTaskId(task.getId());
            dto.setPlanId(task.getPlanId());
            CpsInspectionPlan plan = task.getPlanId() == null ? null : planMapper.findById(task.getPlanId());
            dto.setPlanTitle(plan == null ? null : plan.getTitle());
            String typeCode = task.getTaskType();
            dto.setTypeCode(typeCode);
            CpsInspectionType type = typeCode == null ? null : typeMapper.findByCode(typeCode);
            if (type != null) {
                dto.setTypeName(type.getName());
                dto.setZoneFlag(type.getZoneFlag());
            } else {
                // 历史枚举任务（INSPECT_CHECK 等）：按分区域处理，名称回退编码
                dto.setTypeName(typeCode);
                dto.setZoneFlag(1);
            }
            dto.setTitle(task.getTitle());
            dto.setTargetEmpNo(task.getTargetEmpNo());
            dto.setTargetEmpName(task.getTargetEmpName());
            dto.setScheduledAt(task.getScheduledAt());
            dto.setFrequency(task.getFrequency());
            dto.setAcceptanceCriteria(task.getAcceptanceCriteria());
            dto.setEvidenceRequirement(task.getEvidenceRequirement());
            dto.setCoverageAreaCodes(task.getCoverageAreaCodes());
            dto.setTaskStatus(task.getTaskStatus());
            result.add(dto);
        }
        return result;
    }

    // ============================================================
    // 内部
    // ============================================================

    /** 按逗号分隔 item_code 解析启用点检项（保序；停用/不存在的编码跳过并 warn） */
    private List<CpsCheckItem> resolveItems(String checkItemIds) {
        List<CpsCheckItem> items = new ArrayList<>();
        if (checkItemIds == null || checkItemIds.trim().isEmpty()) return items;
        for (String part : checkItemIds.split(",")) {
            String code = part.trim();
            if (code.isEmpty()) continue;
            CpsCheckItem ci = checkItemMapper.findByCode(code).orElse(null);
            if (ci == null || !Boolean.TRUE.equals(ci.getEnabled())) {
                log.warn("Check item skipped (missing/disabled): {}", code);
                continue;
            }
            items.add(ci);
        }
        return items;
    }

    private CpsInspectionRecord mustGetRecord(Long id) {
        require(id != null, "recordId is required");
        CpsInspectionRecord record = recordMapper.findById(id);
        require(record != null, "巡检记录不存在: id=" + id);
        return record;
    }

    private CpsInspectionRecordItem mustGetItem(Long id) {
        require(id != null, "itemId is required");
        CpsInspectionRecordItem item = itemMapper.findById(id);
        require(item != null, "巡检记录明细不存在: id=" + id);
        return item;
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /** 取第一个非空字符串，全部为空时返回兜底值（导出里优先显示姓名、回退工号）。 */
    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return "";
    }

    /** 整改照片地址：file_url 落库即 MinIO 绝对 URL，直接使用。 */
    private String photoUrl(CpsIssueAttachment attachment) {
        if (attachment == null) {
            return "";
        }
        String fileUrl = trimToNull(attachment.getFileUrl());
        return fileUrl == null ? "" : fileUrl;
    }

    private static String formatTime(LocalDateTime time) {
        return time == null ? "" : EXPORT_TIME_FORMAT.format(time);
    }

    /** 记录状态中文：导出与 web 列表口径一致。 */
    private static String recordStatusLabel(String status) {
        if (status == null) return "";
        switch (status) {
            case "PENDING":
                return "待执行";
            case "IN_PROGRESS":
                return "执行中";
            case "AWAIT_CONFIRM":
                return "待确认（历史）";
            case "PENDING_REVIEW":
                return "待复核";
            case "PENDING_RECTIFY":
                return "待整改";
            case "JUDGED":
                return "已结束";
            default:
                return status;
        }
    }

    private static String aiOverallLabel(String overall) {
        if (overall == null) return "";
        switch (overall) {
            case "PASS":
                return "通过";
            case "PARTIAL":
                return "部分通过";
            case "PROBLEM":
                return "有问题";
            default:
                return overall;
        }
    }

    private static void require(boolean cond, String message) {
        if (!cond) throw new IllegalArgumentException(message);
    }

    private static void requireText(String value, String field) {
        require(value != null && !value.trim().isEmpty(), field + " is required");
    }

    /** 文本字数（去首尾空白后的字符数），与 C-01 text_validity 的口径对齐。 */
    /** 流转日志（提交/复核/整改各写一条，供详情时间线与审计追溯） */
    private void insertLog(Long recordId, String action, String fromStatus, String toStatus,
                           String operatorEmpNo, String operatorEmpName, String comment) {
        CpsInspectionRecordLog entry = new CpsInspectionRecordLog();
        entry.setRecordId(recordId);
        entry.setAction(action);
        entry.setFromStatus(fromStatus);
        entry.setToStatus(toStatus);
        entry.setOperatorEmpNo(operatorEmpNo);
        entry.setOperatorEmpName(operatorEmpName);
        entry.setComment(comment);
        recordLogMapper.insert(entry);
    }
}
