package com.catlbattery.app.service.cps;

import com.catlbattery.domain.entity.cps.CpsInspectionPlan;
import com.catlbattery.domain.entity.cps.CpsInspectionPlanStatus;
import com.catlbattery.domain.entity.cps.CpsInspectionPlanTask;
import com.catlbattery.domain.entity.cps.CpsInspectionPlanTaskType;
import com.catlbattery.domain.entity.cps.CpsPlanBuildRecord;
import com.catlbattery.domain.entity.cps.dto.CpsInspectionPlanApproveRequest;
import com.catlbattery.domain.entity.cps.dto.CpsInspectionPlanRejectRequest;
import com.catlbattery.domain.entity.cps.dto.CpsInspectionPlanRequest;
import com.catlbattery.domain.entity.cps.dto.CpsPlanRecordStatusResponse;
import com.catlbattery.infra.mapper.cps.CpsInspectionPlanMapper;
import com.catlbattery.infra.mapper.cps.CpsInspectionPlanTaskMapper;
import com.catlbattery.infra.mapper.cps.CpsPlanBuildRecordMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * D1 巡检计划申请草稿 + 审核 + D3 建单引擎（C-09，PRD §22.2；AC-30）。
 *
 * <p>流程：C-07 草稿（service.applyDraft 调 Python requestInspectionPlanDraft）→ PENDING_REVIEW
 * → 审核 approve/reject（CPS CAS lock_version）→ approve 成功后由 D3 事务 createPlanTasks 建任务。
 * 三类任务 INSPECT_RECTIFY/INSPECT_PATROL/INSPECT_CHECK 同表区分（UNIQUE(plan_id, task_type) 幂等）；
 * 建单范围（选择性建单）：draft_content_json.tasks 非空时只建其中勾选的类型（如仅辅房点检 INSPECT_CHECK），
 * 空时三类全建——兼容 AI 草稿（C-07 蓝图恒含三类）与存量数据；
 * 部分失败仅补建未成功项——查询 cps_inspection_plan_task 已存在的 task_type 跳过，缺的继续 INSERT。
 *
 * <p>Java 端未持久化周报运行表，admin 端 C5/C8 走代理路径（CpsWeeklyReportService）；
 * plan.source_run_id 仅存字符串引用，业务上由后续 CPS 周报归档完成后回调写入。
 */
@Service
public class CpsInspectionPlanService {

    private static final Logger log = LoggerFactory.getLogger(CpsInspectionPlanService.class);

    private final CpsInspectionPlanMapper planMapper;
    private final CpsInspectionPlanTaskMapper taskMapper;
    private final CpsPlanBuildRecordMapper buildRecordMapper;
    private final CpsAgentFrameworkClient agentFrameworkClient;
    private final CpsApprovalService approvalService;
    private final ObjectMapper objectMapper;

    public CpsInspectionPlanService(
            CpsInspectionPlanMapper planMapper,
            CpsInspectionPlanTaskMapper taskMapper,
            CpsPlanBuildRecordMapper buildRecordMapper,
            CpsAgentFrameworkClient agentFrameworkClient,
            CpsApprovalService approvalService,
            ObjectMapper objectMapper) {
        this.planMapper = planMapper;
        this.taskMapper = taskMapper;
        this.buildRecordMapper = buildRecordMapper;
        this.agentFrameworkClient = agentFrameworkClient;
        this.approvalService = approvalService;
        this.objectMapper = objectMapper;
    }

    /**
     * D1：申请计划草稿。先按 sourceRunId+planType 查重，存在则更新草稿内容；否则新建。
     * 草稿内容优先取 C-07 响应，未启用/Python 缺位时使用请求预填（保留 D2 界面可手工编辑路径）。
     */
    @Transactional
    public CpsInspectionPlan applyDraft(CpsInspectionPlanRequest request) {
        // B3：前端已移除「计划类型」字段；保留字段兼容 DB NOT NULL 与 uk_cps_plan_source_run 唯一键。
        String planType = request.getPlanType();
        if (planType == null || planType.trim().isEmpty()) {
            planType = "MANUAL";
        } else {
            planType = planType.trim();
        }
        requireText(request.getTitle(), "title");

        String draftJson = request.getDraftContentJson();
        if (draftJson == null || draftJson.trim().isEmpty()) {
            Map<String, Object> agentDraft = agentFrameworkClient.requestInspectionPlanDraft(
                    request.getSourceRunId(),
                    planType,
                    request.getTitle(),
                    request.getTargetFactory(),
                    request.getTargetArea(),
                    request.getRiskBasis());
            draftJson = serializeOrEmpty(agentDraft);
        }
        String idempotencyKey = request.getSourceRunId() == null
                ? null
                : "plan-draft-" + request.getSourceRunId();

        CpsInspectionPlan plan;
        Optional<CpsInspectionPlan> existing = request.getSourceRunId() == null
                ? Optional.empty()
                : planMapper.findBySourceRunIdAndPlanType(request.getSourceRunId(), planType);
        if (existing.isPresent()) {
            plan = existing.get();
            plan.setDraftContentJson(draftJson);
            plan.setRiskBasis(request.getRiskBasis());
            plan.setTargetFactory(request.getTargetFactory());
            plan.setTargetArea(request.getTargetArea());
            int rows = planMapper.updateDraftCas(plan);
            if (rows != 1) {
                throw new IllegalStateException("Inspection plan draft update CAS failed for id " + plan.getId()
                        + " (status not PENDING_REVIEW or row vanished)");
            }
            CpsInspectionPlan reloaded = reload(plan.getId());
            // 重提：刷新审批中心实例的 biz 字段与快照（已审过的不会再被改）
            try {
                approvalService.ensureInstance("INSPECTION_PLAN", reloaded.getId(),
                        reloaded.getTitle(), reloaded.getDraftBy(), null,
                        reloaded.getTargetFactory(), reloaded.getTargetArea(),
                        snapshotOf(reloaded, draftJson));
            } catch (IllegalStateException e) {
                log.warn("ensureInstance after re-draft skipped: {}", e.getMessage());
            }
            return reloaded;
        }
        plan = new CpsInspectionPlan();
        plan.setSourceRunId(request.getSourceRunId());
        plan.setReportId(request.getReportId());
        plan.setPlanType(planType);
        plan.setTitle(request.getTitle());
        plan.setTargetFactory(request.getTargetFactory());
        plan.setTargetArea(request.getTargetArea());
        plan.setRiskBasis(request.getRiskBasis());
        plan.setDraftContentJson(draftJson);
        plan.setStatus(CpsInspectionPlanStatus.PENDING_REVIEW);
        plan.setDraftIdempotencyKey(idempotencyKey);
        plan.setConfigVersion(1);
        plan.setLockVersion(0);
        plan.setDraftBy(request.getDraftBy() == null ? "system" : request.getDraftBy());
        planMapper.insert(plan);
        CpsInspectionPlan reloaded = reload(plan.getId());
        // 通用审批中心：新建 PENDING 实例 + 写 SUBMIT step
        try {
            approvalService.ensureInstance("INSPECTION_PLAN", reloaded.getId(),
                    reloaded.getTitle(), reloaded.getDraftBy(), null,
                    reloaded.getTargetFactory(), reloaded.getTargetArea(),
                    snapshotOf(reloaded, draftJson));
        } catch (Exception e) {
            log.warn("ensureInstance failed: {}", e.getMessage());
        }
        return reloaded;
    }

    /**
     * B2：审批中心巡检计划快照（中文 key + 结构化任务数组 + 任务图片）。
     * 不再输出 planType 与原始 draftContentJson；draft_content_json 三种形态均兼容：
     * {tasks:[...]}、{tasks:{}}、agent 返回的 {draft_content_json:{tasks:[...]}}；
     * 解析失败时「巡检任务」返回空数组，绝不抛异常阻断审批建单。
     */
    private Map<String, Object> snapshotOf(CpsInspectionPlan plan, String draftJson) {
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("计划ID", plan.getId());
        snap.put("计划标题", blankToDash(plan.getTitle()));
        snap.put("目标工厂", blankToDash(plan.getTargetFactory()));
        snap.put("目标区域", blankToDash(plan.getTargetArea()));
        snap.put("风险依据", blankToDash(plan.getRiskBasis()));
        snap.put("拟稿人", blankToDash(plan.getDraftBy()));
        snap.put("状态", statusText(plan.getStatus()));
        snap.put("巡检任务", snapshotTasks(draftJson));
        return snap;
    }

    /** 快照用：解析草稿任务为中文 key 的数组；失败返回空数组。 */
    private List<Map<String, Object>> snapshotTasks(String draftJson) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (draftJson == null || draftJson.trim().isEmpty()) {
            return result;
        }
        try {
            Object tasks = tasksNode(objectMapper.readValue(draftJson, Object.class), 0);
            if (tasks instanceof Map) {
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) tasks).entrySet()) {
                    if (entry.getKey() instanceof String && entry.getValue() instanceof Map) {
                        result.add(taskSnapshot((Map<?, ?>) entry.getValue(), (String) entry.getKey()));
                    }
                }
            } else if (tasks instanceof List) {
                for (Object item : (List<?>) tasks) {
                    if (item instanceof Map) {
                        result.add(taskSnapshot((Map<?, ?>) item, null));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse plan draft_content_json for approval snapshot: {}", e.getMessage());
        }
        return result;
    }

    private Map<String, Object> taskSnapshot(Map<?, ?> spec, String fallbackType) {
        Map<String, Object> item = new LinkedHashMap<>();
        String typeCode = specString(spec, "inspectionTypeCode", "task_type", "taskType", "typeCode");
        item.put("巡检类型", blankToDash(typeCode == null ? fallbackType : typeCode));
        item.put("标题", blankToDash(specString(spec, "title")));
        item.put("责任人", personOf(specString(spec, "targetEmpName", "target_emp_name"),
                specString(spec, "targetEmpNo", "target_emp_no")));
        item.put("频次", blankToDash(specString(spec, "frequency")));
        item.put("验收标准", blankToDash(specString(spec, "acceptanceCriteria", "acceptance_criteria")));
        item.put("证据要求", blankToDash(specString(spec, "evidenceRequirement", "evidence_requirement")));
        item.put("覆盖区域", blankToDash(specString(spec, "coverageAreaCodes", "coverage_area_codes")));
        List<String> images = new ArrayList<>();
        collectImageUrls(spec, 0, images);
        if (!images.isEmpty()) {
            item.put("图片", images);
        }
        return item;
    }

    /** 从草稿任意节点递归提取图片地址（MinIO 绝对 URL 或附件代理路径）。 */
    private static void collectImageUrls(Object node, int depth, List<String> out) {
        if (node == null || depth > 5) {
            return;
        }
        if (node instanceof String) {
            String text = ((String) node).trim();
            if (isImageUrl(text) && !out.contains(text)) {
                out.add(text);
            }
            return;
        }
        if (node instanceof Map) {
            for (Object value : ((Map<?, ?>) node).values()) {
                collectImageUrls(value, depth + 1, out);
            }
        } else if (node instanceof List) {
            for (Object value : (List<?>) node) {
                collectImageUrls(value, depth + 1, out);
            }
        }
    }

    private static boolean isImageUrl(String text) {
        if (text == null) {
            return false;
        }
        String lower = text.toLowerCase();
        boolean http = lower.startsWith("http://") || lower.startsWith("https://");
        boolean proxy = lower.startsWith("/api/cps/attachments/");
        if (!http && !proxy) {
            return false;
        }
        return lower.contains("/api/cps/attachments/")
                || lower.contains("image")
                || lower.contains("photo")
                || lower.matches(".*\\.(jpg|jpeg|png|gif|webp|bmp)(\\?.*)?$");
    }

    /** 取 tasks 节点：兼容 {tasks:...}、{draft_content_json:{tasks:...}}、嵌套字符串 JSON。 */
    private Object tasksNode(Object node, int depth) {
        if (node == null || depth > 4) {
            return null;
        }
        if (!(node instanceof Map)) {
            return null;
        }
        Map<?, ?> map = (Map<?, ?>) node;
        Object tasks = map.get("tasks");
        if (tasks instanceof Map || tasks instanceof List) {
            return tasks;
        }
        Object wrapped = map.get("draft_content_json");
        if (wrapped == null) {
            wrapped = map.get("draftContentJson");
        }
        if (wrapped instanceof String) {
            try {
                wrapped = objectMapper.readValue((String) wrapped, Object.class);
            } catch (Exception e) {
                log.warn("Failed to parse wrapped plan draft_content_json: {}", e.getMessage());
                return null;
            }
        }
        return tasksNode(wrapped, depth + 1);
    }

    private static String statusText(CpsInspectionPlanStatus status) {
        if (status == null) {
            return "-";
        }
        switch (status) {
            case PENDING_REVIEW:
                return "待审批";
            case APPROVED:
                return "已通过";
            case REJECTED:
                return "已驳回";
            default:
                return status.name();
        }
    }

    private static String personOf(String name, String empNo) {
        String n = name == null ? "" : name.trim();
        String e = empNo == null ? "" : empNo.trim();
        if (n.isEmpty() && e.isEmpty()) {
            return "-";
        }
        if (n.isEmpty()) {
            return e;
        }
        return e.isEmpty() ? n : n + "(" + e + ")";
    }

    private static String blankToDash(String value) {
        return value == null || value.trim().isEmpty() ? "-" : value.trim();
    }

    public CpsInspectionPlan getDetail(Long id) {
        CpsInspectionPlan plan = planMapper.findById(id);
        if (plan == null) {
            throw new IllegalArgumentException("Inspection plan not found: " + id);
        }
        return plan;
    }

    public List<CpsInspectionPlan> listByStatus(CpsInspectionPlanStatus status) {
        if (status == null) {
            List<CpsInspectionPlan> all = new ArrayList<>();
            for (CpsInspectionPlanStatus s : CpsInspectionPlanStatus.values()) {
                all.addAll(planMapper.findByStatus(s));
            }
            return all;
        }
        return planMapper.findByStatus(status);
    }

    public List<CpsInspectionPlanTask> listTasks(Long planId) {
        getDetail(planId);
        return taskMapper.findByPlanId(planId);
    }

    /**
     * D2：审核批准（C-09 建单引擎入口）。
     * 1) approve CAS（lock_version 命中 → APPROVED + lock_version++）；
     * 2) 失败抛 IllegalStateException（并发冲突/状态错）；
     * 3) 成功后立即调 createPlanTasks 建三类任务；
     * 4) 部分任务失败仅补建未存在项——已存在的 task_type 跳过。
     */
    @Transactional
    public ApproveResult approve(Long id, CpsInspectionPlanApproveRequest request) {
        if (request.getApprover() == null || request.getApprover().trim().isEmpty()) {
            throw new IllegalArgumentException("approver is required");
        }
        CpsInspectionPlan plan = getDetail(id);
        if (plan.getStatus() != CpsInspectionPlanStatus.PENDING_REVIEW) {
            throw new IllegalStateException("Inspection plan not in PENDING_REVIEW: id=" + id + ", status=" + plan.getStatus());
        }
        Integer expectedLock = request.getLockVersion() == null ? plan.getLockVersion() : request.getLockVersion();
        plan.setApprover(request.getApprover());
        plan.setApproverName(request.getApproverName());
        plan.setApprovedAt(LocalDateTime.now());
        plan.setLockVersion(expectedLock);
        int rows = planMapper.approveCas(plan);
        if (rows != 1) {
            throw new IllegalStateException("Inspection plan approve CAS failed for id " + id
                    + " (lockVersion mismatch or status changed)");
        }
        List<CpsInspectionPlanTask> created = createPlanTasks(id, request.getComment(),
                request.getApprover(), CpsPlanBuildRecord.SOURCE_APPROVE);
        CpsInspectionPlan approved = reload(id);
        // 同步审批中心
        try {
            approvalService.approveByBiz("INSPECTION_PLAN", id, request.getApprover(),
                    request.getApproverName(), request.getComment());
        } catch (Exception e) {
            log.warn("Approval sync after plan approve failed: id={} {}", id, e.getMessage());
        }
        return new ApproveResult(approved, created);
    }

    @Transactional
    public CpsInspectionPlan reject(Long id, CpsInspectionPlanRejectRequest request) {
        if (request.getReason() == null || request.getReason().trim().isEmpty()) {
            throw new IllegalArgumentException("reject reason is required");
        }
        if (request.getApprover() == null || request.getApprover().trim().isEmpty()) {
            throw new IllegalArgumentException("approver is required");
        }
        CpsInspectionPlan plan = getDetail(id);
        if (plan.getStatus() != CpsInspectionPlanStatus.PENDING_REVIEW) {
            throw new IllegalStateException("Inspection plan not in PENDING_REVIEW: id=" + id + ", status=" + plan.getStatus());
        }
        Integer expectedLock = request.getLockVersion() == null ? plan.getLockVersion() : request.getLockVersion();
        plan.setApprover(request.getApprover());
        plan.setApproverName(request.getApproverName());
        plan.setApprovedAt(LocalDateTime.now());
        plan.setRejectReason(request.getReason());
        plan.setLockVersion(expectedLock);
        int rows = planMapper.rejectCas(plan);
        if (rows != 1) {
            throw new IllegalStateException("Inspection plan reject CAS failed for id " + id
                    + " (lockVersion mismatch or status changed)");
        }
        // 同步审批中心
        try {
            approvalService.rejectByBiz("INSPECTION_PLAN", id, request.getApprover(),
                    request.getApproverName(), request.getReason());
        } catch (Exception e) {
            log.warn("Approval sync after plan reject failed: id={} {}", id, e.getMessage());
        }
        return reload(id);
    }

    /**
     * D3 建单引擎（C-09 内部事务；AC-30；D4 建单留痕；Phase D 对接统一巡检类型）：
     * 1) 查询已有 task_type 集合（=巡检类型编码或历史枚举），已存在跳过（记 SKIPPED_EXISTING）；
     * 2) 缺失项按建单范围创建（记 CREATED）：
     *    - 新草稿格式 tasks=[{inspectionTypeCode,title,targetEmpNo,coverageAreaCodes,...}] → 按类型编码建任务；
     *    - 旧格式（map 或 task_type 列表）→ 按固定三类取交集；空草稿 → 三类全建（AI 草稿/存量兼容）；
     * 3) 单项 INSERT 失败被 UNIQUE 唯一键兜底（DuplicateKeyException 视为已建，跳过）；
     * 4) 其余失败不阻断批准：记 CREATE_FAILED 后继续其余类型——失败仅补建（AC-30）；
     * 5) 每次尝试 append 落 cps_plan_build_record，重启/重试/补建可追溯（D4）。
     */
    private List<CpsInspectionPlanTask> createPlanTasks(
            Long planId, String approverComment, String builtBy, String buildSource) {
        CpsInspectionPlan plan = getDetail(planId);
        List<String> existing = taskMapper.findExistingTaskTypes(planId);
        Map<String, Map<String, Object>> planDraftTasks = parseDraftTasks(plan.getDraftContentJson());
        List<String> want = wantedTaskTypes(planDraftTasks);

        List<CpsInspectionPlanTask> created = new ArrayList<>();
        for (String taskType : want) {
            if (existing.contains(taskType)) {
                log.debug("Plan task already exists: planId={} type={}, skip (idempotent)", planId, taskType);
                recordBuild(planId, taskType, CpsPlanBuildRecord.RESULT_SKIPPED_EXISTING, null, builtBy, buildSource);
                continue;
            }
            Map<String, Object> taskSpec = planDraftTasks.getOrDefault(taskType, Collections.emptyMap());
            CpsInspectionPlanTask task = new CpsInspectionPlanTask();
            task.setPlanId(planId);
            task.setTaskType(taskType);
            task.setTitle(stringOrDefault(specString(taskSpec, "title"), defaultTaskTitle(plan, taskType)));
            // B6：草稿未带责任人时回退拟稿人，避免生成「target_emp_no 为空 → 谁查都看不到」的任务。
            // 草稿显式带 targetEmpNo 时保持原优先级（如 plan 13）。
            String targetEmpNo = specString(taskSpec, "targetEmpNo", "target_emp_no");
            if (targetEmpNo == null || targetEmpNo.trim().isEmpty()) {
                targetEmpNo = plan.getDraftBy() == null ? null : plan.getDraftBy().trim();
                if (targetEmpNo != null && !targetEmpNo.isEmpty()) {
                    log.warn("Plan task target_emp_no missing in draft, fallback to plan.draftBy: "
                            + "planId={} type={} draftBy={}", planId, taskType, targetEmpNo);
                } else {
                    log.warn("Plan task target_emp_no missing and plan.draftBy empty, task will be invisible: "
                            + "planId={} type={}", planId, taskType);
                }
            }
            task.setTargetEmpNo(targetEmpNo);
            task.setTargetEmpName(specString(taskSpec, "targetEmpName", "target_emp_name"));
            task.setScheduledAt(plan.getApprovedAt());
            task.setFrequency(specString(taskSpec, "frequency"));
            task.setAcceptanceCriteria(stringOrDefault(
                    specString(taskSpec, "acceptanceCriteria", "acceptance_criteria"), approverComment));
            task.setEvidenceRequirement(specString(taskSpec, "evidenceRequirement", "evidence_requirement"));
            // Phase D：分区域类型的覆盖区域（逗号分隔 area_code；空=全部启用区域）
            task.setCoverageAreaCodes(specString(taskSpec, "coverageAreaCodes"));
            task.setTaskStatus("PENDING");
            task.setReferenceIssueId(null);
            task.setReferenceObjectKey(specString(taskSpec, "referenceObjectKey", "reference_object_key"));
            task.setReferenceObjectType(specString(taskSpec, "referenceObjectType", "reference_object_type"));
            try {
                taskMapper.insert(task);
                created.add(task);
                recordBuild(planId, taskType, CpsPlanBuildRecord.RESULT_CREATED, null, builtBy, buildSource);
            } catch (org.springframework.dao.DuplicateKeyException dup) {
                log.info("Plan task UNIQUE conflict (concurrent create): planId={} type={}, treat as already-created",
                        planId, taskType);
                recordBuild(planId, taskType, CpsPlanBuildRecord.RESULT_SKIPPED_EXISTING, null, builtBy, buildSource);
            } catch (RuntimeException failure) {
                // 部分失败不阻断批准（AC-30 失败仅补建）：留痕后继续其余类型
                log.warn("Plan task create failed: planId={} type={}", planId, taskType, failure);
                recordBuild(planId, taskType, CpsPlanBuildRecord.RESULT_CREATE_FAILED,
                        truncate(failure.getMessage(), 500), builtBy, buildSource);
            }
        }
        return created;
    }

    /** 从 taskSpec 里按多个候选键取字符串（新格式 camelCase 优先，旧格式 snake_case 兜底）。 */
    private static String specString(Map<?, ?> spec, String... keys) {
        for (String key : keys) {
            Object v = spec.get(key);
            if (v instanceof String && !((String) v).trim().isEmpty()) return (String) v;
        }
        return null;
    }

    /**
     * 建单范围（Phase D）：
     * - 新格式条目（spec 含 inspectionTypeCode）→ 类型编码直接生效；
     * - 旧格式键（map/task_type 列表）→ 仅保留固定三类中的合法键；
     * - 全部非法/空草稿 → 回退历史三类全建（AI 草稿与存量兼容）。
     */
    private static List<String> wantedTaskTypes(Map<String, Map<String, Object>> planDraftTasks) {
        List<String> legacy = Arrays.asList("INSPECT_RECTIFY", "INSPECT_PATROL", "INSPECT_CHECK");
        if (planDraftTasks.isEmpty()) {
            return legacy;
        }
        List<String> selected = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> entry : planDraftTasks.entrySet()) {
            String key = entry.getKey();
            Map<String, Object> spec = entry.getValue();
            // 新格式：spec 内带 inspectionTypeCode（parseDraftTasks 以此键化）→ 任意类型编码合法
            if (spec != null && spec.get("inspectionTypeCode") instanceof String) {
                selected.add(key);
            } else if (legacy.contains(key)) {
                // 旧格式：仅固定三类合法
                selected.add(key);
            }
        }
        return selected.isEmpty() ? legacy : selected;
    }

    /** D4：append 落建单尝试记录（cps_plan_build_record）。 */
    private void recordBuild(Long planId, String taskType, String result,
                             String errorMsg, String builtBy, String buildSource) {
        CpsPlanBuildRecord record = new CpsPlanBuildRecord();
        record.setPlanId(planId);
        record.setTaskType(taskType);
        record.setResult(result);
        record.setErrorMsg(errorMsg);
        record.setBuiltBy(builtBy);
        record.setBuildSource(buildSource);
        buildRecordMapper.insert(record);
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }

    /**
     * D4：补建（AC-30 失败仅补建）。仅 APPROVED 计划可调；createPlanTasks 内部
     * 已按"已存在跳过"幂等——仅 CREATE_FAILED/缺失类型会实际重试，已成功项不重复创建。
     */
    @Transactional
    public List<CpsInspectionPlanTask> rebuildTasks(Long planId, String operatorEmpNo) {
        CpsInspectionPlan plan = getDetail(planId);
        if (plan.getStatus() != CpsInspectionPlanStatus.APPROVED) {
            throw new IllegalStateException("Only APPROVED plan can rebuild tasks: id=" + planId
                    + ", status=" + plan.getStatus());
        }
        if (operatorEmpNo == null || operatorEmpNo.trim().isEmpty()) {
            throw new IllegalArgumentException("operatorEmpNo is required");
        }
        return createPlanTasks(planId, null, operatorEmpNo, CpsPlanBuildRecord.SOURCE_REBUILD);
    }

    /**
     * D4：计划→建单结果→任务状态聚合视图（AC-06/30 可查/可追溯）。
     * perType=三类任务维度：任务是否存在/任务状态/最近一次建单结果与来源（requested=是否在建单范围内）；
     * summary=created/failed/missing——草稿未勾选（requested=false）的类型不计 missing。
     */
    public CpsPlanRecordStatusResponse recordStatus(Long planId) {
        CpsInspectionPlan plan = getDetail(planId);
        List<CpsInspectionPlanTask> tasks = taskMapper.findByPlanId(planId);
        List<CpsPlanBuildRecord> records = buildRecordMapper.findByPlanId(planId);
        Map<String, Map<String, Object>> planDraftTasks = parseDraftTasks(plan.getDraftContentJson());
        java.util.Set<String> wantedTypeNames = new java.util.HashSet<>(wantedTaskTypes(planDraftTasks));

        Map<String, CpsInspectionPlanTask> taskByType = new java.util.HashMap<>();
        for (CpsInspectionPlanTask task : tasks) {
            taskByType.put(task.getTaskType(), task);
        }
        Map<String, CpsPlanBuildRecord> lastBuildByType = new java.util.HashMap<>();
        for (CpsPlanBuildRecord record : records) {
            lastBuildByType.put(record.getTaskType(), record); // findByPlanId 按 id ASC——后者覆盖前者
        }

        // Phase D：类型全集 = 草稿勾选 ∪ 已建任务 ∪ 建单留痕；
        // 旧格式草稿（无 inspectionTypeCode 条目）额外并入固定三类，保持历史 perType 视图完整。
        java.util.LinkedHashSet<String> allTypes = new java.util.LinkedHashSet<>(wantedTypeNames);
        allTypes.addAll(taskByType.keySet());
        allTypes.addAll(lastBuildByType.keySet());
        boolean newFormat = planDraftTasks.values().stream()
                .anyMatch(spec -> spec != null && spec.get("inspectionTypeCode") instanceof String);
        if (!newFormat) {
            allTypes.addAll(Arrays.asList("INSPECT_RECTIFY", "INSPECT_PATROL", "INSPECT_CHECK"));
        }

        List<CpsPlanRecordStatusResponse.TypeRecordStatus> perType = new ArrayList<>();
        CpsPlanRecordStatusResponse.Summary summary = new CpsPlanRecordStatusResponse.Summary();
        for (String typeName : allTypes) {
            CpsPlanRecordStatusResponse.TypeRecordStatus status = new CpsPlanRecordStatusResponse.TypeRecordStatus();
            status.setTaskType(typeName);
            status.setRequested(wantedTypeNames.contains(typeName));
            CpsInspectionPlanTask task = taskByType.get(typeName);
            status.setTaskExists(task != null);
            if (task != null) {
                status.setTaskId(task.getId());
                status.setTaskStatus(task.getTaskStatus());
            }
            CpsPlanBuildRecord lastBuild = lastBuildByType.get(typeName);
            if (lastBuild != null) {
                status.setLastBuildResult(lastBuild.getResult());
                status.setLastBuildErrorMsg(lastBuild.getErrorMsg());
                status.setLastBuildAt(lastBuild.getCreatedAt());
                status.setLastBuildSource(lastBuild.getBuildSource());
            }
            if (!wantedTypeNames.contains(typeName)) {
                // 草稿未勾选的类型：不在建单范围，不计 missing（选择性建单）
            } else if (task == null) {
                summary.setTypesMissing(summary.getTypesMissing() + 1);
            } else if (lastBuild != null
                    && CpsPlanBuildRecord.RESULT_CREATE_FAILED.equals(lastBuild.getResult())) {
                summary.setTypesFailed(summary.getTypesFailed() + 1);
            } else {
                summary.setTypesCreated(summary.getTypesCreated() + 1);
            }
            perType.add(status);
        }

        CpsPlanRecordStatusResponse response = new CpsPlanRecordStatusResponse();
        response.setPlanId(plan.getId());
        response.setPlanTitle(plan.getTitle());
        response.setPlanStatus(plan.getStatus().name());
        response.setApprovedAt(plan.getApprovedAt());
        response.setPerType(perType);
        response.setSummary(summary);
        return response;
    }

    /**
     * 解析 draft_content_json 内的任务映射（Phase D）：
     * - 新格式 tasks=[{inspectionTypeCode,title,targetEmpNo,coverageAreaCodes,...}] → 按 inspectionTypeCode 键化；
     * - 旧格式 tasks={INSPECT_*:{...}}（map）或 tasks=[{task_type,...}]（Python C-07 列表）→ 按 task_type 键化；
     * 非法 JSON 视为空 draft（回退历史三类全建）。
     */
    private Map<String, Map<String, Object>> parseDraftTasks(String draftContentJson) {
        if (draftContentJson == null || draftContentJson.trim().isEmpty()) return Collections.emptyMap();
        try {
            Object root = objectMapper.readValue(draftContentJson, Object.class);
            // 兼容 agent 包装形态 {draft_content_json:{tasks:[...]}}
            Object tasks = tasksNode(root, 0);
            if (!(tasks instanceof Map) && !(tasks instanceof List)) return Collections.emptyMap();
            Map<String, Map<String, Object>> typed = new java.util.LinkedHashMap<>();
            if (tasks instanceof Map) {
                ((Map<?, ?>) tasks).forEach((k, v) -> {
                    if (k instanceof String && v instanceof Map) {
                        typed.put((String) k, (Map<String, Object>) v);
                    }
                });
            } else {
                for (Object item : (List<?>) tasks) {
                    if (item instanceof Map) {
                        Map<?, ?> m = (Map<?, ?>) item;
                        // Phase D 新格式优先：inspectionTypeCode；旧格式：task_type
                        Object typeKey = m.get("inspectionTypeCode") != null ? m.get("inspectionTypeCode") : m.get("task_type");
                        if (typeKey instanceof String && !((String) typeKey).trim().isEmpty()) {
                            typed.put(((String) typeKey).trim(), (Map<String, Object>) item);
                        }
                    }
                }
            }
            return typed;
        } catch (Exception e) {
            log.warn("Failed to parse plan draft_content_json: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private CpsInspectionPlan reload(Long id) {
        CpsInspectionPlan plan = planMapper.findById(id);
        if (plan == null) {
            throw new IllegalStateException("Inspection plan vanished after write: id=" + id);
        }
        return plan;
    }

    private String serializeOrEmpty(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("Failed to serialize draft agent response: {}", e.getMessage());
            return null;
        }
    }

    private static String defaultTaskTitle(CpsInspectionPlan plan, String type) {
        return plan.getTitle() + " - " + type;
    }

    private static String stringOrDefault(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) return fallback;
        return value;
    }

    private static void requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    /** 审核批准结果：plan 实体 + 本次新增的任务列表（重放/重复执行返回空列表）。 */
    public static class ApproveResult {
        private final CpsInspectionPlan plan;
        private final List<CpsInspectionPlanTask> createdTasks;

        public ApproveResult(CpsInspectionPlan plan, List<CpsInspectionPlanTask> createdTasks) {
            this.plan = plan;
            this.createdTasks = createdTasks;
        }
        public CpsInspectionPlan getPlan() { return plan; }
        public List<CpsInspectionPlanTask> getCreatedTasks() { return createdTasks; }
    }
}
