package com.company.cps.mapper;

import com.company.cps.domain.CpsInitialReviewEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CpsInitialReviewEventMapper {

    /** 追加事件流水（触发/重试/回调/超时/接管/迟到/重触发/裁决）。 */
    void insert(CpsInitialReviewEvent event);

    /** 任务维度流水（admin 任务详情时间线）。 */
    List<CpsInitialReviewEvent> findByTaskId(@Param("taskId") Long taskId);

    /** 问题维度流水（mobile 裁决页可追溯）。 */
    List<CpsInitialReviewEvent> findByIssueId(@Param("issueId") Long issueId);

    /**
     * I 线记忆体系消费：分页查询 AI 初审事件全集。
     * issueId/taskId/eventType 直查；factory/area/category 走 cps_issue JOIN（仅当 issueId 为 null 时需要）。
     * 为简化记忆端聚合，所有维度过滤统一 JOIN cps_issue（事件 issue_id 必非空）。
     */
    List<CpsInitialReviewEvent> findMemoryPage(
            @Param("issueId") Long issueId,
            @Param("eventType") String eventType,
            @Param("taskId") Long taskId,
            @Param("factory") String factory,
            @Param("area") String area,
            @Param("categoryL1Id") Long categoryL1Id,
            @Param("categoryL2Id") Long categoryL2Id,
            @Param("startTime") String startTime,
            @Param("endTime") String endTime,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    long countMemory(
            @Param("issueId") Long issueId,
            @Param("eventType") String eventType,
            @Param("taskId") Long taskId,
            @Param("factory") String factory,
            @Param("area") String area,
            @Param("categoryL1Id") Long categoryL1Id,
            @Param("categoryL2Id") Long categoryL2Id,
            @Param("startTime") String startTime,
            @Param("endTime") String endTime
    );
}
