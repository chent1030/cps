package com.catlbattery.app.service.cps;

import com.catlbattery.domain.entity.cps.CpsIssueDraft;
import com.catlbattery.domain.entity.cps.dto.CpsIssueDraftDto;
import com.catlbattery.domain.entity.cps.dto.CpsIssueDraftSaveRequest;
import com.catlbattery.infra.mapper.cps.CpsIssueDraftMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 巡检问题服务端草稿（Phase B2）。
 * 生命周期：暂存(ACTIVE) → 提交成功(SUBMITTED，由 CpsIssueService 回调归档) / 手动删除。
 * 权限：读写都限定 creator_emp_no = 当前用户，防越权。
 */
@Service
public class CpsIssueDraftService {

    private static final Logger log = LoggerFactory.getLogger(CpsIssueDraftService.class);
    private static final int MAX_DRAFTS_PER_USER = 20;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private CpsIssueDraftMapper draftMapper;

    /** 暂存（新建或覆盖更新）。返回落库后的 DTO（含 id，Android 端记下来用于后续覆盖）。 */
    @Transactional
    public CpsIssueDraftDto save(CpsIssueDraftSaveRequest request) {
        requireText(request.getEmpNo(), "empNo is required");
        requireText(request.getDraftData(), "draftData is required");
        // JSON 合法性校验（内容结构由客户端负责）
        try {
            OBJECT_MAPPER.readTree(request.getDraftData());
        } catch (Exception e) {
            throw new IllegalArgumentException("draftData 不是合法 JSON: " + e.getMessage());
        }
        String title = buildTitle(request);
        LocalDateTime now = LocalDateTime.now();

        if (request.getId() != null) {
            CpsIssueDraft existing = draftMapper.findById(request.getId());
            if (existing == null || !existing.getCreatorEmpNo().equals(request.getEmpNo().trim())) {
                throw new IllegalArgumentException("草稿不存在或不属于当前用户: id=" + request.getId());
            }
            if (!"ACTIVE".equals(existing.getStatus())) {
                throw new IllegalStateException("草稿已提交/废弃，不能再更新: id=" + request.getId());
            }
            int rows = draftMapper.updateOwnActive(existing.getId(), request.getEmpNo().trim(), title,
                    trimToNull(request.getInspectionTypeCode()), trimToNull(request.getFactory()),
                    trimToNull(request.getArea()), request.getDraftData());
            if (rows != 1) {
                throw new IllegalStateException("草稿更新失败（可能已被提交）: id=" + request.getId());
            }
            return get(existing.getId(), request.getEmpNo());
        }

        // 新建前做数量上限保护：超出时提示先清理
        int activeCount = draftMapper.listByCreator(request.getEmpNo().trim(), "ACTIVE", MAX_DRAFTS_PER_USER + 1).size();
        if (activeCount >= MAX_DRAFTS_PER_USER) {
            throw new IllegalStateException("草稿数量已达上限 " + MAX_DRAFTS_PER_USER + " 份，请先清理旧草稿");
        }

        CpsIssueDraft draft = new CpsIssueDraft();
        draft.setCreatorEmpNo(request.getEmpNo().trim());
        draft.setCreatorEmpName(trimToNull(request.getEmpName()));
        draft.setTitle(title);
        draft.setInspectionTypeCode(trimToNull(request.getInspectionTypeCode()));
        draft.setFactory(trimToNull(request.getFactory()));
        draft.setArea(trimToNull(request.getArea()));
        draft.setDraftData(request.getDraftData());
        draft.setStatus("ACTIVE");
        draft.setSubmittedIssueId(null);
        draft.setCreatedAt(now);
        draft.setUpdatedAt(now);
        draftMapper.insert(draft);
        log.info("Issue draft created: id={} creator={}", draft.getId(), draft.getCreatorEmpNo());
        return CpsIssueDraftDto.from(draft);
    }

    /** 本人 ACTIVE 草稿列表（updated_at 倒序）。 */
    public List<CpsIssueDraftDto> listMyDrafts(String empNo) {
        requireText(empNo, "empNo is required");
        return draftMapper.listByCreator(empNo.trim(), "ACTIVE", MAX_DRAFTS_PER_USER).stream()
                .map(CpsIssueDraftDto::from)
                .collect(Collectors.toList());
    }

    /** 取单份草稿（仅限本人）。 */
    public CpsIssueDraftDto get(Long id, String empNo) {
        requireText(empNo, "empNo is required");
        CpsIssueDraft draft = mustOwn(id, empNo);
        return CpsIssueDraftDto.from(draft);
    }

    /** 删除草稿（仅限本人，物理删除）。 */
    @Transactional
    public void delete(Long id, String empNo) {
        requireText(empNo, "empNo is required");
        mustOwn(id, empNo);
        draftMapper.deleteOwn(id, empNo.trim());
    }

    /**
     * 提交归档：由 CpsIssueService.createIssue 在问题落库成功后回调。
     * 任何失败只 warn 不抛——不能因为草稿清理失败让已成功的问题提交回滚。
     */
    public void markSubmittedQuietly(Long draftId, String creatorEmpNo, Long issueId) {
        if (draftId == null) return;
        try {
            int rows = draftMapper.markSubmitted(draftId, creatorEmpNo, issueId);
            if (rows == 1) {
                log.info("Issue draft archived: id={} → issueId={}", draftId, issueId);
            } else {
                log.warn("Issue draft archive skipped (not found/not active/not own): id={} creator={}", draftId, creatorEmpNo);
            }
        } catch (Exception e) {
            log.warn("Issue draft archive failed: id={} cause={}: {}", draftId, e.getClass().getSimpleName(), e.getMessage());
        }
    }

    private CpsIssueDraft mustOwn(Long id, String empNo) {
        if (id == null) throw new IllegalArgumentException("draft id is required");
        CpsIssueDraft draft = draftMapper.findById(id);
        if (draft == null || !draft.getCreatorEmpNo().equals(empNo.trim())) {
            throw new IllegalArgumentException("草稿不存在或不属于当前用户: id=" + id);
        }
        return draft;
    }

    /** 摘要：优先客户端给的 title；否则按 类型+工厂+区域 拼一个。 */
    private static String buildTitle(CpsIssueDraftSaveRequest request) {
        if (request.getTitle() != null && !request.getTitle().trim().isEmpty()) {
            return request.getTitle().trim();
        }
        StringBuilder sb = new StringBuilder("巡检草稿");
        if (notBlank(request.getInspectionTypeCode())) sb.append("·").append(request.getInspectionTypeCode().trim());
        if (notBlank(request.getFactory())) sb.append("·").append(request.getFactory().trim());
        if (notBlank(request.getArea())) sb.append("/").append(request.getArea().trim());
        return sb.toString();
    }

    private static boolean notBlank(String s) { return s != null && !s.trim().isEmpty(); }

    private static String trimToNull(String s) {
        return notBlank(s) ? s.trim() : null;
    }

    private static void requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(message);
    }
}
