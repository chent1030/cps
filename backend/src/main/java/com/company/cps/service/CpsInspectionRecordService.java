package com.company.cps.service;

import com.company.cps.domain.CpsCheckItem;
import com.company.cps.domain.CpsInspectionArea;
import com.company.cps.domain.CpsInspectionPlan;
import com.company.cps.domain.CpsInspectionPlanTask;
import com.company.cps.domain.CpsInspectionRecord;
import com.company.cps.domain.CpsInspectionRecordItem;
import com.company.cps.domain.CpsInspectionType;
import com.company.cps.dto.CpsMyInspectionTaskDto;
import com.company.cps.mapper.CpsCheckItemMapper;
import com.company.cps.mapper.CpsInspectionAreaMapper;
import com.company.cps.mapper.CpsInspectionPlanMapper;
import com.company.cps.mapper.CpsInspectionPlanTaskMapper;
import com.company.cps.mapper.CpsInspectionRecordItemMapper;
import com.company.cps.mapper.CpsInspectionRecordMapper;
import com.company.cps.mapper.CpsInspectionTypeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
                                                  String photoObjectKey, String photoFileName, Integer photoCount) {
        CpsInspectionRecordItem item = mustGetItem(itemId);
        CpsInspectionRecord record = mustGetRecord(item.getRecordId());
        require("IN_PROGRESS".equals(record.getRecordStatus()),
                "记录状态不允许编辑: " + record.getRecordStatus());
        String types = item.getCheckTypes() == null ? "" : item.getCheckTypes();
        if (types.contains("PASS_FAIL") && passResult != null) {
            require("PASS".equals(passResult) || "FAIL".equals(passResult), "passResult 必须是 PASS/FAIL");
        }
        itemMapper.updateResult(itemId, trimToNull(passResult), trimToNull(textValue),
                trimToNull(photoObjectKey), trimToNull(photoFileName),
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

        int deduct = 0;
        for (CpsInspectionRecordItem item : items) {
            String types = item.getCheckTypes() == null ? "" : item.getCheckTypes();
            // 拍照项：校验最低张数
            if (types.contains("PHOTO")) {
                int min = item.getPhotoMinCount() == null ? 1 : item.getPhotoMinCount();
                int actual = item.getPhotoCount() == null ? 0 : item.getPhotoCount();
                require(actual >= min, "点检项「" + item.getContent() + "」照片不足：需至少 " + min + " 张，当前 " + actual + " 张");
            }
            // 合格判定项：必填且计分
            if (types.contains("PASS_FAIL")) {
                require(item.getPassResult() != null, "点检项「" + item.getContent() + "」尚未判定是否合格");
                // final_result = 人工判定（无 AI 判定链路的统一模型）
                itemMapper.updateJudge(item.getId(), null, null, item.getPassResult());
                if ("FAIL".equals(item.getPassResult())) {
                    deduct += item.getDeductScore() == null ? 0 : item.getDeductScore();
                }
            }
            // 文本项：必填校验
            if (types.contains("TEXT")) {
                require(item.getTextValue() != null && !item.getTextValue().trim().isEmpty(),
                        "点检项「" + item.getContent() + "」需要填写文本说明");
            }
        }
        int score = Math.max(0, 100 - deduct);
        int rows = recordMapper.submit(recordId, "IN_PROGRESS", "JUDGED", "SUCCESS", score);
        require(rows == 1, "提交失败（状态已变化，请刷新）");
        log.info("Inspection record submitted: id={} score={}", recordId, score);
        // Phase D：计划任务完成度联动（全部覆盖区域判定完成后 COMPLETED）
        advancePlanTask(record.getPlanTaskId(), "COMPLETED");
        return recordMapper.findById(recordId);
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
        return result;
    }

    /** Android「我的执行中」：当前用户 IN_PROGRESS 记录（恢复现场用） */
    public List<CpsInspectionRecord> listInProgress(String empNo) {
        requireText(empNo, "empNo");
        return recordMapper.findPage(null, null, "IN_PROGRESS", empNo.trim(), null, 50, 0);
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

    private static void require(boolean cond, String message) {
        if (!cond) throw new IllegalArgumentException(message);
    }

    private static void requireText(String value, String field) {
        require(value != null && !value.trim().isEmpty(), field + " is required");
    }
}
