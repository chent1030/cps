package com.company.cps.service;

import com.company.cps.domain.CpsApprovalAction;
import com.company.cps.domain.CpsApprovalInstance;
import com.company.cps.domain.CpsApprovalInstanceStatus;
import com.company.cps.domain.CpsApprovalStep;
import com.company.cps.dto.CpsApprovalInstanceDto;
import com.company.cps.dto.CpsApprovalStepDto;
import com.company.cps.mapper.CpsApprovalInstanceMapper;
import com.company.cps.mapper.CpsApprovalStepMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 通用审批中心（Phase A2）。
 * V1 仅支持 INSPECTION_PLAN：业务方调用 {@link #ensureInstance} 提交/重提时 upsert；
 * {@link #approve}/{@link #reject}/{@link #withdraw} 由 controller 触发，更新 instance + 写 step。
 *
 * <p>事务策略：
 * <ul>
 *   <li>状态机用 {@code updateStatusCas} 乐观锁，affected=0 抛 IllegalStateException</li>
 *   <li>step 写入与状态机转移在同一事务，seq 在事务内取 MAX+1（防重号）</li>
 * </ul>
 */
@Service
public class CpsApprovalService {

    private static final Logger log = LoggerFactory.getLogger(CpsApprovalService.class);

    private final CpsApprovalInstanceMapper instanceMapper;
    private final CpsApprovalStepMapper stepMapper;
    private final ObjectMapper objectMapper;

    public CpsApprovalService(CpsApprovalInstanceMapper instanceMapper,
                              CpsApprovalStepMapper stepMapper,
                              ObjectMapper objectMapper) {
        this.instanceMapper = instanceMapper;
        this.stepMapper = stepMapper;
        this.objectMapper = objectMapper;
    }

    // ============================================================
    // 业务接入 API（被 InspectionPlanService 等调用）
    // ============================================================

    /**
     * 业务方提交 / 重提时调用。
     * 若 instance 已存在且 status=PENDING：刷新 bizMeta（标题/创建人/工厂/快照），不写 step（重提不算新步骤）。
     * 若已存在但 status=APPROVED/REJECTED：拒绝（业务方应开新 instance）。
     * 若不存在：新建 PENDING + 写 SUBMIT step。
     *
     * @return instance id
     */
    @Transactional
    public Long ensureInstance(String bizType, Long bizId, String bizTitle,
                               String creatorEmpNo, String creatorEmpName,
                               String factory, String area, Object snapshot) {
        CpsApprovalInstance existing = instanceMapper.findByBiz(bizType, bizId);
        if (existing != null) {
            if (existing.getStatus() == CpsApprovalInstanceStatus.PENDING) {
                // 重提/编辑：刷新快照 + last_action_at，不写 step
                LocalDateTime now = LocalDateTime.now();
                instanceMapper.updateBizMeta(existing.getId(), bizTitle, creatorEmpNo, creatorEmpName,
                        factory, area, toJson(snapshot), now);
                log.info("Approval instance refreshed: bizType={} bizId={} id={}", bizType, bizId, existing.getId());
                return existing.getId();
            }
            throw new IllegalStateException("Approval instance already settled: id=" + existing.getId()
                    + " status=" + existing.getStatus() + " bizType=" + bizType + " bizId=" + bizId);
        }
        LocalDateTime now = LocalDateTime.now();
        CpsApprovalInstance ins = new CpsApprovalInstance();
        ins.setBizType(bizType);
        ins.setBizId(bizId);
        ins.setBizTitle(bizTitle);
        ins.setBizCreatorEmpNo(creatorEmpNo);
        ins.setBizCreatorEmpName(creatorEmpName);
        ins.setBizFactory(factory);
        ins.setBizArea(area);
        ins.setStatus(CpsApprovalInstanceStatus.PENDING);
        ins.setCurrentApproverEmpNo(null);
        ins.setCurrentApproverEmpName(null);
        ins.setLastActionAt(now);
        ins.setBizSnapshot(toJson(snapshot));
        ins.setCreatedAt(now);
        ins.setUpdatedAt(now);
        instanceMapper.upsert(ins);
        // 写 SUBMIT step
        appendStep(ins.getId(), null, CpsApprovalInstanceStatus.PENDING, creatorEmpNo, creatorEmpName,
                CpsApprovalAction.SUBMIT, "提交审批");
        log.info("Approval instance created: bizType={} bizId={} id={}", bizType, bizId, ins.getId());
        return ins.getId();
    }

    /**
     * 业务方被驳回后修改了内容，再次提交：清掉 reject 终态并回到 PENDING。
     * 用于 InspectionPlanService：当计划被驳回后编辑再 apply。
     */
    @Transactional
    public void reopenToPending(Long instanceId, String actorEmpNo, String actorEmpName, String comment) {
        CpsApprovalInstance ins = mustGet(instanceId);
        if (ins.getStatus() == CpsApprovalInstanceStatus.PENDING) {
            return; // already pending, no-op
        }
        if (ins.getStatus() != CpsApprovalInstanceStatus.REJECTED) {
            throw new IllegalStateException("Cannot reopen non-rejected instance: id=" + instanceId + " status=" + ins.getStatus());
        }
        LocalDateTime now = LocalDateTime.now();
        int rows = instanceMapper.updateStatusCas(instanceId,
                CpsApprovalInstanceStatus.REJECTED.name(),
                CpsApprovalInstanceStatus.PENDING.name(),
                now, null, null, null, null, null);
        if (rows != 1) {
            throw new IllegalStateException("Approval reopen CAS failed: id=" + instanceId);
        }
        appendStep(instanceId, CpsApprovalInstanceStatus.REJECTED, CpsApprovalInstanceStatus.PENDING,
                actorEmpNo, actorEmpName, CpsApprovalAction.SUBMIT,
                comment == null ? "修改后重新提交" : comment);
    }

    // ============================================================
    // 审批操作（controller 入口）
    // ============================================================

    @Transactional
    public CpsApprovalInstanceDto approve(Long instanceId, String operatorEmpNo, String operatorEmpName, String comment) {
        requireOperator(operatorEmpNo);
        CpsApprovalInstance ins = mustGet(instanceId);
        if (ins.getStatus() != CpsApprovalInstanceStatus.PENDING) {
            throw new IllegalStateException("Approval not pending: id=" + instanceId + " status=" + ins.getStatus());
        }
        // 限权：currentApproverEmpNo 非空时只能由该 empNo 审
        if (ins.getCurrentApproverEmpNo() != null && !ins.getCurrentApproverEmpNo().equals(operatorEmpNo)) {
            throw new IllegalStateException("Approval is assigned to " + ins.getCurrentApproverEmpNo() + ", not " + operatorEmpNo);
        }
        LocalDateTime now = LocalDateTime.now();
        int rows = instanceMapper.updateStatusCas(instanceId,
                CpsApprovalInstanceStatus.PENDING.name(),
                CpsApprovalInstanceStatus.APPROVED.name(),
                now, now, null, null, null, null);
        if (rows != 1) {
            throw new IllegalStateException("Approval approve CAS failed: id=" + instanceId);
        }
        appendStep(instanceId, CpsApprovalInstanceStatus.PENDING, CpsApprovalInstanceStatus.APPROVED,
                operatorEmpNo, operatorEmpName, CpsApprovalAction.APPROVE, comment);
        log.info("Approval approved: id={} by {} {}", instanceId, operatorEmpNo, operatorEmpName);
        return getDetail(instanceId);
    }

    /** 给业务方调用：按 bizType+bizId 批准（在 InspectionPlanService.approve 成功后调用，保持两边同步） */
    @Transactional
    public CpsApprovalInstanceDto approveByBiz(String bizType, Long bizId, String operatorEmpNo, String operatorEmpName, String comment) {
        CpsApprovalInstance ins = instanceMapper.findByBiz(bizType, bizId);
        if (ins == null) return null; // 没有审批实例不报错（旧业务兼容）
        // 已经处于终态（APPROVED/REJECTED/WITHDRAWN）→ 不抛异常，直接返回当前 DTO；
        // 这样外层事务不会被标记为 rollback-only，调用方仍可正常 commit。
        if (ins.getStatus() != CpsApprovalInstanceStatus.PENDING) {
            log.info("approveByBiz skipped: instance {} already in status {}", ins.getId(), ins.getStatus());
            return getDetail(ins.getId());
        }
        return approve(ins.getId(), operatorEmpNo, operatorEmpName, comment);
    }

    @Transactional
    public CpsApprovalInstanceDto reject(Long instanceId, String operatorEmpNo, String operatorEmpName, String reason) {
        requireOperator(operatorEmpNo);
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("reject reason is required");
        }
        CpsApprovalInstance ins = mustGet(instanceId);
        if (ins.getStatus() != CpsApprovalInstanceStatus.PENDING) {
            throw new IllegalStateException("Approval not pending: id=" + instanceId + " status=" + ins.getStatus());
        }
        if (ins.getCurrentApproverEmpNo() != null && !ins.getCurrentApproverEmpNo().equals(operatorEmpNo)) {
            throw new IllegalStateException("Approval is assigned to " + ins.getCurrentApproverEmpNo() + ", not " + operatorEmpNo);
        }
        LocalDateTime now = LocalDateTime.now();
        int rows = instanceMapper.updateStatusCas(instanceId,
                CpsApprovalInstanceStatus.PENDING.name(),
                CpsApprovalInstanceStatus.REJECTED.name(),
                now, null, now, reason.trim(), null, null);
        if (rows != 1) {
            throw new IllegalStateException("Approval reject CAS failed: id=" + instanceId);
        }
        appendStep(instanceId, CpsApprovalInstanceStatus.PENDING, CpsApprovalInstanceStatus.REJECTED,
                operatorEmpNo, operatorEmpName, CpsApprovalAction.REJECT, reason.trim());
        log.info("Approval rejected: id={} by {} {}", instanceId, operatorEmpNo, operatorEmpName);
        return getDetail(instanceId);
    }

    @Transactional
    public CpsApprovalInstanceDto rejectByBiz(String bizType, Long bizId, String operatorEmpNo, String operatorEmpName, String reason) {
        CpsApprovalInstance ins = instanceMapper.findByBiz(bizType, bizId);
        if (ins == null) return null;
        if (ins.getStatus() != CpsApprovalInstanceStatus.PENDING) {
            log.info("rejectByBiz skipped: instance {} already in status {}", ins.getId(), ins.getStatus());
            return getDetail(ins.getId());
        }
        return reject(ins.getId(), operatorEmpNo, operatorEmpName, reason);
    }

    @Transactional
    public CpsApprovalInstanceDto withdraw(Long instanceId, String operatorEmpNo, String operatorEmpName, String comment) {
        requireOperator(operatorEmpNo);
        CpsApprovalInstance ins = mustGet(instanceId);
        if (ins.getStatus() != CpsApprovalInstanceStatus.PENDING) {
            throw new IllegalStateException("Approval not pending: id=" + instanceId + " status=" + ins.getStatus());
        }
        // 撤回方必须是提交人
        if (ins.getBizCreatorEmpNo() != null && !ins.getBizCreatorEmpNo().equals(operatorEmpNo)) {
            throw new IllegalStateException("Only the submitter can withdraw: instance=" + instanceId);
        }
        LocalDateTime now = LocalDateTime.now();
        int rows = instanceMapper.updateStatusCas(instanceId,
                CpsApprovalInstanceStatus.PENDING.name(),
                CpsApprovalInstanceStatus.WITHDRAWN.name(),
                now, null, null, null, null, null);
        if (rows != 1) {
            throw new IllegalStateException("Approval withdraw CAS failed: id=" + instanceId);
        }
        appendStep(instanceId, CpsApprovalInstanceStatus.PENDING, CpsApprovalInstanceStatus.WITHDRAWN,
                operatorEmpNo, operatorEmpName, CpsApprovalAction.WITHDRAW, comment);
        return getDetail(instanceId);
    }

    // ============================================================
    // 查询
    // ============================================================

    public List<CpsApprovalInstanceDto> listPending(String bizType, String keyword, int page, int pageSize) {
        if (page < 1) page = 1;
        if (pageSize < 1) pageSize = 20;
        if (pageSize > 200) pageSize = 200;
        return instanceMapper.findPage(CpsApprovalInstanceStatus.PENDING.name(), bizType, keyword,
                        pageSize, (page - 1) * pageSize).stream()
                .map(CpsApprovalInstanceDto::from)
                .collect(Collectors.toList());
    }

    public long countPending(String bizType, String keyword) {
        return instanceMapper.countPage(CpsApprovalInstanceStatus.PENDING.name(), bizType, keyword);
    }

    public List<CpsApprovalInstanceDto> listAll(String status, String bizType, String keyword, int page, int pageSize) {
        if (page < 1) page = 1;
        if (pageSize < 1) pageSize = 20;
        if (pageSize > 200) pageSize = 200;
        String statusFilter = (status == null || status.isEmpty()) ? null : status;
        return instanceMapper.findPage(statusFilter, bizType, keyword,
                        pageSize, (page - 1) * pageSize).stream()
                .map(CpsApprovalInstanceDto::from)
                .collect(Collectors.toList());
    }

    public long countAll(String status, String bizType, String keyword) {
        String statusFilter = (status == null || status.isEmpty()) ? null : status;
        return instanceMapper.countPage(statusFilter, bizType, keyword);
    }

    public CpsApprovalInstanceDto getDetail(Long id) {
        CpsApprovalInstance ins = mustGet(id);
        CpsApprovalInstanceDto dto = CpsApprovalInstanceDto.from(ins);
        dto.setStepCount(stepMapper.findByInstance(id).size());
        return dto;
    }

    public List<CpsApprovalStepDto> listSteps(Long instanceId) {
        return stepMapper.findByInstance(instanceId).stream()
                .map(CpsApprovalStepDto::from)
                .collect(Collectors.toList());
    }

    public List<CpsApprovalInstanceDto> listInstancesForBiz(String bizType, Long bizId) {
        CpsApprovalInstance existing = instanceMapper.findByBiz(bizType, bizId);
        if (existing == null) return java.util.Collections.emptyList();
        return java.util.Collections.singletonList(CpsApprovalInstanceDto.from(existing));
    }

    // ============================================================
    // 内部
    // ============================================================

    private CpsApprovalInstance mustGet(Long id) {
        CpsApprovalInstance ins = instanceMapper.findById(id);
        if (ins == null) {
            throw new IllegalArgumentException("Approval instance not found: id=" + id);
        }
        return ins;
    }

    private void requireOperator(String empNo) {
        if (empNo == null || empNo.trim().isEmpty()) {
            throw new IllegalArgumentException("operator empNo is required");
        }
    }

    private void appendStep(Long instanceId, CpsApprovalInstanceStatus from, CpsApprovalInstanceStatus to,
                            String empNo, String empName, CpsApprovalAction action, String comment) {
        Integer next = stepMapper.nextSeq(instanceId);
        CpsApprovalStep step = new CpsApprovalStep();
        step.setInstanceId(instanceId);
        step.setSeq(next == null ? 1 : next);
        step.setActorEmpNo(empNo);
        step.setActorEmpName(empName);
        step.setAction(action);
        step.setComment(comment);
        step.setFromStatus(from);
        step.setToStatus(to);
        step.setCreatedAt(LocalDateTime.now());
        stepMapper.insert(step);
    }

    private String toJson(Object o) {
        if (o == null) return null;
        try {
            if (o instanceof String) return (String) o;
            return objectMapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            log.warn("Approval snapshot serialize failed: {}", e.getMessage());
            return null;
        }
    }

    /** 业务方在控制器/前端拿到的快照：扁平 map + JSON。供后续 detail 面板使用。 */
    public Map<String, Object> snapshotMap(Object snapshot) {
        if (snapshot == null) return new HashMap<>();
        if (snapshot instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) snapshot;
            return new HashMap<>(m);
        }
        return new HashMap<>();
    }

    public String currentApproverFilterFor(CpsApprovalInstance ins) {
        return Objects.toString(ins.getCurrentApproverEmpNo(), null);
    }
}
