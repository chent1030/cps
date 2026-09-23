package com.company.cps.service;

import com.company.cps.domain.CpsInitialReviewEvent;
import com.company.cps.domain.CpsIssueStatus;
import com.company.cps.domain.CpsReviewAdjudication;
import com.company.cps.dto.CpsAdjudicationMemoryItem;
import com.company.cps.dto.CpsInitialReviewEventMemoryItem;
import com.company.cps.dto.CpsIssueListItemResponse;
import com.company.cps.dto.CpsPageResponse;
import com.company.cps.mapper.CpsInitialReviewEventMapper;
import com.company.cps.mapper.CpsIssueMapper;
import com.company.cps.mapper.CpsReviewAdjudicationMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * I 线记忆体系消费端聚合：裁决 / 事件 / 问题三套分页查询。
 * - 默认 page=1 size=100 max=500（与 CpsInitialReviewAdminService 对齐分页语义）；
 * - 字段命名与 cps_review_adjudication/cps_initial_review_event/cps_issue 1:1 直出；
 * - 过滤维度按记忆体系列出的 FR-09 需求（issueId / 分类 / 区域 / 状态 / 时间区间等）。
 */
@Service
public class CpsMemoryAdminService {

    private static final int DEFAULT_PAGE_SIZE = 100;
    private static final int MAX_PAGE_SIZE = 500;

    private final CpsReviewAdjudicationMapper adjudicationMapper;
    private final CpsInitialReviewEventMapper eventMapper;
    private final CpsIssueMapper issueMapper;

    public CpsMemoryAdminService(
            CpsReviewAdjudicationMapper adjudicationMapper,
            CpsInitialReviewEventMapper eventMapper,
            CpsIssueMapper issueMapper) {
        this.adjudicationMapper = adjudicationMapper;
        this.eventMapper = eventMapper;
        this.issueMapper = issueMapper;
    }

    /**
     * 分页 + 过滤裁决列表。
     * 任一过滤参数为 null/空 = 不过滤；categoryL1Id/categoryL2Id 走 cps_issue JOIN（与契约一致）。
     */
    public CpsPageResponse<CpsAdjudicationMemoryItem> listAdjudications(
            Long issueId, String decision, String aiRelation, String reviewerEmpNo,
            String factory, String area,
            Long categoryL1Id, Long categoryL2Id,
            String startTime, String endTime,
            Integer page, Integer size) {
        int[] norm = normalizePage(page, size);
        long total = adjudicationMapper.countMemory(
                issueId, trim(decision), trim(aiRelation), trim(reviewerEmpNo),
                trim(factory), trim(area),
                categoryL1Id, categoryL2Id,
                trim(startTime), trim(endTime));
        List<CpsReviewAdjudication> rows = total == 0 ? List.of() : adjudicationMapper.findMemoryPage(
                issueId, trim(decision), trim(aiRelation), trim(reviewerEmpNo),
                trim(factory), trim(area),
                categoryL1Id, categoryL2Id,
                trim(startTime), trim(endTime),
                norm[1], norm[0]);
        List<CpsAdjudicationMemoryItem> items = new ArrayList<>(rows.size());
        for (CpsReviewAdjudication r : rows) {
            CpsAdjudicationMemoryItem item = new CpsAdjudicationMemoryItem();
            item.setId(r.getId());
            item.setIssueId(r.getIssueId());
            item.setVersionNo(r.getVersionNo());
            item.setTaskId(r.getTaskId());
            item.setReviewerEmpNo(r.getReviewerEmpNo());
            item.setReviewerEmpName(r.getReviewerEmpName());
            item.setDecision(r.getDecision());
            item.setAiOverall(r.getAiOverall());
            item.setAiRelation(r.getAiRelation());
            item.setReason(r.getReason());
            item.setFromStatus(r.getFromStatus());
            item.setToStatus(r.getToStatus());
            item.setCreatedAt(r.getCreatedAt());
            items.add(item);
        }
        return new CpsPageResponse<>(total, items);
    }

    /**
     * 分页 + 过滤 AI 初审事件流水。
     * 工厂/区域/分类走 cps_issue JOIN（事件 issue_id 必非空，否则被 INNER 过滤）。
     */
    public CpsPageResponse<CpsInitialReviewEventMemoryItem> listEvents(
            Long issueId, String eventType, Long taskId,
            String factory, String area,
            Long categoryL1Id, Long categoryL2Id,
            String startTime, String endTime,
            Integer page, Integer size) {
        int[] norm = normalizePage(page, size);
        long total = eventMapper.countMemory(
                issueId, trim(eventType), taskId,
                trim(factory), trim(area),
                categoryL1Id, categoryL2Id,
                trim(startTime), trim(endTime));
        List<CpsInitialReviewEvent> rows = total == 0 ? List.of() : eventMapper.findMemoryPage(
                issueId, trim(eventType), taskId,
                trim(factory), trim(area),
                categoryL1Id, categoryL2Id,
                trim(startTime), trim(endTime),
                norm[1], norm[0]);
        List<CpsInitialReviewEventMemoryItem> items = new ArrayList<>(rows.size());
        for (CpsInitialReviewEvent e : rows) {
            CpsInitialReviewEventMemoryItem item = new CpsInitialReviewEventMemoryItem();
            item.setId(e.getId());
            item.setTaskId(e.getTaskId());
            item.setIssueId(e.getIssueId());
            item.setVersionNo(e.getVersionNo());
            item.setEventType(e.getEventType());
            item.setDetail(e.getDetail());
            item.setOperatorEmpNo(e.getOperatorEmpNo());
            item.setCreatedAt(e.getCreatedAt());
            items.add(item);
        }
        return new CpsPageResponse<>(total, items);
    }

    /**
     * 分页 + 过滤问题（供记忆体系构建"问题上下文"，与 CpsAdminIssueService 字段对齐）。
     */
    public CpsPageResponse<CpsIssueListItemResponse> listIssues(
            String factory, Long categoryL1Id, Long categoryL2Id,
            CpsIssueStatus status, String startTime, String endTime,
            Integer page, Integer size) {
        int[] norm = normalizePage(page, size);
        long total = issueMapper.countMemory(
                trim(factory), categoryL1Id, categoryL2Id,
                status, trim(startTime), trim(endTime));
        List<CpsIssueListItemResponse> rows = total == 0 ? List.of() : issueMapper.findMemoryPage(
                trim(factory), categoryL1Id, categoryL2Id,
                status, trim(startTime), trim(endTime),
                norm[1], norm[0]);
        return new CpsPageResponse<>(total, rows);
    }

    /** 计算 limit/offset，并钳制 pageSize 在 [1, MAX_PAGE_SIZE]、pageSize 默认 DEFAULT_PAGE_SIZE。 */
    static int[] normalizePage(Integer page, Integer size) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null || size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        int offset = (safePage - 1) * safeSize;
        return new int[]{offset, safeSize};
    }

    private static String trim(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        return value.trim();
    }
}