package com.company.cps.service;

import com.company.cps.domain.CpsInspectionPlan;
import com.company.cps.domain.CpsInspectionPlanStatus;
import com.company.cps.domain.CpsInspectionPlanTask;
import com.company.cps.domain.CpsInspectionPlanTaskType;
import com.company.cps.domain.CpsPlanBuildRecord;
import com.company.cps.dto.CpsInspectionPlanApproveRequest;
import com.company.cps.dto.CpsInspectionPlanRejectRequest;
import com.company.cps.dto.CpsInspectionPlanRequest;
import com.company.cps.dto.CpsPlanRecordStatusResponse;
import com.company.cps.mapper.CpsInspectionPlanMapper;
import com.company.cps.mapper.CpsInspectionPlanTaskMapper;
import com.company.cps.mapper.CpsPlanBuildRecordMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * D1 巡检计划申请草稿 + 审核 + D3 建单引擎（C-09，PRD §22.2；AC-30）。
 *
 * <p>流程：C-07 草稿（service.applyDraft 调 Python requestInspectionPlanDraft）→ PENDING_REVIEW
 * → 审核 approve/reject（CPS CAS lock_version）→ approve 成功后由 D3 事务 createPlanTasks 落三类任务。
 * 三类任务 INSPECT_RECTIFY/INSPECT_PATROL/INSPECT_CHECK 同表区分（UNIQUE(plan_id, task_type) 幂等）；
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
    private final ObjectMapper objectMapper;

    public CpsInspectionPlanService(
            CpsInspectionPlanMapper planMapper,
            CpsInspectionPlanTaskMapper taskMapper,
            CpsPlanBuildRecordMapper buildRecordMapper,
            CpsAgentFrameworkClient agentFrameworkClient,
            ObjectMapper objectMapper) {
        this.planMapper = planMapper;
        this.taskMapper = taskMapper;
        this.buildRecordMapper = buildRecordMapper;
        this.agentFrameworkClient = agentFrameworkClient;
        this.objectMapper = objectMapper;
    }

    /**
     * D1：申请计划草稿。先按 sourceRunId+planType 查重，存在则更新草稿内容；否则新建。
     * 草稿内容优先取 C-07 响应，未启用/Python 缺位时使用请求预填（保留 D2 界面可手工编辑路径）。
     */
    @Transactional
    public CpsInspectionPlan applyDraft(CpsInspectionPlanRequest request) {
        requireText(request.getPlanType(), "planType");
        requireText(request.getTitle(), "title");

        String draftJson = request.getDraftContentJson();
        if (draftJson == null || draftJson.trim().isEmpty()) {
            Map<String, Object> agentDraft = agentFrameworkClient.requestInspectionPlanDraft(
                    request.getSourceRunId(),
                    request.getPlanType(),
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
                : planMapper.findBySourceRunIdAndPlanType(request.getSourceRunId(), request.getPlanType());
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
            return reload(plan.getId());
        }
        plan = new CpsInspectionPlan();
        plan.setSourceRunId(request.getSourceRunId());
        plan.setReportId(request.getReportId());
        plan.setPlanType(request.getPlanType());
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
        return reload(plan.getId());
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
        return new ApproveResult(reload(id), created);
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
        return reload(id);
    }

    /**
     * D3 建单引擎（C-09 内部事务；AC-30；D4 建单留痕）：
     * 1) 查询已有 task_type 集合，已存在跳过（记 SKIPPED_EXISTING）；
     * 2) 缺失项按固定三类顺序 INSPECT_RECTIFY → INSPECT_PATROL → INSPECT_CHECK 创建（记 CREATED）；
     * 3) 单项 INSERT 失败被 UNIQUE 唯一键兜底（DuplicateKeyException 视为已建，跳过）；
     * 4) 其余失败不阻断批准：记 CREATE_FAILED 后继续其余类型——失败仅补建（AC-30）；
     * 5) 每次尝试 append 落 cps_plan_build_record，重启/重试/补建可追溯（D4）。
     *
     * <p>任务明细从 plan.draft_content_json 提取（按 task_type 维度，缺则落空任务）。
     * Draft 字段语义：每类任务含 title/target_emp_no/target_emp_name/scheduled_at 等可选字段；
     * 空 draft 时任务 title=plan.title，targetEmpNo 由 approver 兜底，确保至少一行任务留痕。
     */
    private List<CpsInspectionPlanTask> createPlanTasks(
            Long planId, String approverComment, String builtBy, String buildSource) {
        CpsInspectionPlan plan = getDetail(planId);
        List<CpsInspectionPlanTaskType> existing = taskMapper.findExistingTaskTypes(planId);
        List<CpsInspectionPlanTaskType> want = Arrays.asList(
                CpsInspectionPlanTaskType.INSPECT_RECTIFY,
                CpsInspectionPlanTaskType.INSPECT_PATROL,
                CpsInspectionPlanTaskType.INSPECT_CHECK);
        Map<String, Map<String, Object>> planDraftTasks = parseDraftTasks(plan.getDraftContentJson());

        List<CpsInspectionPlanTask> created = new ArrayList<>();
        for (CpsInspectionPlanTaskType taskType : want) {
            if (existing.contains(taskType)) {
                log.debug("Plan task already exists: planId={} type={}, skip (idempotent)", planId, taskType);
                recordBuild(planId, taskType, CpsPlanBuildRecord.RESULT_SKIPPED_EXISTING, null, builtBy, buildSource);
                continue;
            }
            Map<String, Object> taskSpec = planDraftTasks.getOrDefault(taskType.name(), Collections.emptyMap());
            CpsInspectionPlanTask task = new CpsInspectionPlanTask();
            task.setPlanId(planId);
            task.setTaskType(taskType);
            task.setTitle(stringOrDefault((String) taskSpec.get("title"), defaultTaskTitle(plan, taskType)));
            task.setTargetEmpNo(stringOrDefault((String) taskSpec.get("target_emp_no"), null));
            task.setTargetEmpName(stringOrDefault((String) taskSpec.get("target_emp_name"), null));
            task.setScheduledAt(plan.getApprovedAt());
            task.setFrequency((String) taskSpec.get("frequency"));
            task.setAcceptanceCriteria(stringOrDefault((String) taskSpec.get("acceptance_criteria"),
                    approverComment));
            task.setEvidenceRequirement((String) taskSpec.get("evidence_requirement"));
            task.setTaskStatus("PENDING");
            task.setReferenceIssueId(null);
            task.setReferenceObjectKey((String) taskSpec.get("reference_object_key"));
            task.setReferenceObjectType((String) taskSpec.get("reference_object_type"));
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

    /** D4：append 落建单尝试记录（cps_plan_build_record）。 */
    private void recordBuild(Long planId, CpsInspectionPlanTaskType taskType, String result,
                             String errorMsg, String builtBy, String buildSource) {
        CpsPlanBuildRecord record = new CpsPlanBuildRecord();
        record.setPlanId(planId);
        record.setTaskType(taskType.name());
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
     * perType=三类任务维度：任务是否存在/任务状态/最近一次建单结果与来源；summary=created/failed/missing。
     */
    public CpsPlanRecordStatusResponse recordStatus(Long planId) {
        CpsInspectionPlan plan = getDetail(planId);
        List<CpsInspectionPlanTask> tasks = taskMapper.findByPlanId(planId);
        List<CpsPlanBuildRecord> records = buildRecordMapper.findByPlanId(planId);

        Map<String, CpsInspectionPlanTask> taskByType = new java.util.HashMap<>();
        for (CpsInspectionPlanTask task : tasks) {
            taskByType.put(task.getTaskType().name(), task);
        }
        Map<String, CpsPlanBuildRecord> lastBuildByType = new java.util.HashMap<>();
        for (CpsPlanBuildRecord record : records) {
            lastBuildByType.put(record.getTaskType(), record); // findByPlanId 按 id ASC——后者覆盖前者
        }

        List<CpsPlanRecordStatusResponse.TypeRecordStatus> perType = new ArrayList<>();
        CpsPlanRecordStatusResponse.Summary summary = new CpsPlanRecordStatusResponse.Summary();
        for (CpsInspectionPlanTaskType type : new CpsInspectionPlanTaskType[]{
                CpsInspectionPlanTaskType.INSPECT_RECTIFY,
                CpsInspectionPlanTaskType.INSPECT_PATROL,
                CpsInspectionPlanTaskType.INSPECT_CHECK}) {
            CpsPlanRecordStatusResponse.TypeRecordStatus status = new CpsPlanRecordStatusResponse.TypeRecordStatus();
            status.setTaskType(type.name());
            CpsInspectionPlanTask task = taskByType.get(type.name());
            status.setTaskExists(task != null);
            if (task != null) {
                status.setTaskId(task.getId());
                status.setTaskStatus(task.getTaskStatus());
            }
            CpsPlanBuildRecord lastBuild = lastBuildByType.get(type.name());
            if (lastBuild != null) {
                status.setLastBuildResult(lastBuild.getResult());
                status.setLastBuildErrorMsg(lastBuild.getErrorMsg());
                status.setLastBuildAt(lastBuild.getCreatedAt());
                status.setLastBuildSource(lastBuild.getBuildSource());
            }
            if (task == null) {
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

    /** 解析 draft_content_json 内的任务映射（{INSPECT_RECTIFY: {...}}）。非法 JSON 视为空 draft。 */
    private Map<String, Map<String, Object>> parseDraftTasks(String draftContentJson) {
        if (draftContentJson == null || draftContentJson.trim().isEmpty()) return Collections.emptyMap();
        try {
            Map<String, Object> root = objectMapper.readValue(draftContentJson, Map.class);
            Object tasks = root.get("tasks");
            if (!(tasks instanceof Map)) return Collections.emptyMap();
            Map<String, Map<String, Object>> typed = new java.util.HashMap<>();
            ((Map<?, ?>) tasks).forEach((k, v) -> {
                if (k instanceof String && v instanceof Map) {
                    typed.put((String) k, (Map<String, Object>) v);
                }
            });
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

    private static String defaultTaskTitle(CpsInspectionPlan plan, CpsInspectionPlanTaskType type) {
        return plan.getTitle() + " - " + type.name();
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
