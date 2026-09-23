package com.company.cps.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * FR-10 历史/覆盖分析聚合查询（波次10）：
 * - frequency / region-supervisor / recurrence / gaps 四端点 SQL 入口。
 * 字段以 Map 直出聚合行（与 CpsWeeklyReportDataMapper 视图查询口径一致）。
 */
@Mapper
public interface CpsCoverageMapper {

    /**
     * 频率分布：(factory, area, category_l1_id) 维度的 cps_issue 数量 + 最近30天新增 + 已关闭 + 关闭率。
     * 过滤维度全部可选，startTime/endTime 限定 submit_time 区间。
     */
    List<Map<String, Object>> frequencyGroupBy(
            @Param("factory") String factory,
            @Param("area") String area,
            @Param("categoryL1Id") Long categoryL1Id,
            @Param("startTime") String startTime,
            @Param("endTime") String endTime,
            @Param("limit") int limit,
            @Param("offset") int offset);

    long countFrequencyGroupBy(
            @Param("factory") String factory,
            @Param("area") String area,
            @Param("categoryL1Id") Long categoryL1Id,
            @Param("startTime") String startTime,
            @Param("endTime") String endTime);

    /**
     * 区域处理人监控：(area, current_handler_emp_no) 在手数 + 累计处理 + 超期数。
     * status 默认非 CLOSED，过滤维度全部可选。
     */
    List<Map<String, Object>> regionSupervisorGroupBy(
            @Param("factory") String factory,
            @Param("area") String area,
            @Param("categoryL1Id") Long categoryL1Id,
            @Param("status") String status,
            @Param("overdueDays") Integer overdueDays,
            @Param("limit") int limit,
            @Param("offset") int offset);

    long countRegionSupervisorGroupBy(
            @Param("factory") String factory,
            @Param("area") String area,
            @Param("categoryL1Id") Long categoryL1Id,
            @Param("status") String status);

    /**
     * 复发分析：同一 issue 在窗口内被 REOPEN 动作或 REJECT 裁决后续重新创建 ≥ threshold 次的问题。
     * 复发计数走 cps_issue_flow_log + cps_review_adjudication 两侧聚合。
     */
    List<Map<String, Object>> recurrenceGroupBy(
            @Param("startTime") String startTime,
            @Param("endTime") String endTime,
            @Param("categoryL1Id") Long categoryL1Id,
            @Param("threshold") Integer threshold,
            @Param("limit") int limit,
            @Param("offset") int offset);

    long countRecurrenceGroupBy(
            @Param("startTime") String startTime,
            @Param("endTime") String endTime,
            @Param("categoryL1Id") Long categoryL1Id,
            @Param("threshold") Integer threshold);

    /**
     * 覆盖缺口：cps_inspection_item 启用项 × cps_room（按 factory/area/room_type 过滤）× cps_room_check_record 最近一次记录时间。
     * 简化语义：过去 7 天 0 条 record 即视为缺口。
     */
    List<Map<String, Object>> coverageGaps(
            @Param("factory") String factory,
            @Param("area") String area,
            @Param("storageRoomType") String storageRoomType,
            @Param("gapDays") Integer gapDays,
            @Param("limit") int limit,
            @Param("offset") int offset);

    long countCoverageGaps(
            @Param("factory") String factory,
            @Param("area") String area,
            @Param("storageRoomType") String storageRoomType,
            @Param("gapDays") Integer gapDays);

    /**
     * FR-11 procedural 调度记忆消费端：按 (category_l1_id, area, decision, ai_relation) 维度聚合历史裁决。
     * 每聚合组 sampleReasons 字段携带最近 5 条样例（reviewer/created_at/reason 200 字截断）以 ';;' 分隔，
     * 解析由 service 层负责。
     */
    List<Map<String, Object>> dispatcherMemoryGroupBy(
            @Param("categoryL1Id") Long categoryL1Id,
            @Param("categoryL2Id") Long categoryL2Id,
            @Param("factory") String factory,
            @Param("area") String area,
            @Param("decision") String decision,
            @Param("aiRelation") String aiRelation,
            @Param("startTime") String startTime,
            @Param("endTime") String endTime,
            @Param("limit") int limit,
            @Param("offset") int offset);

    long countDispatcherMemoryGroupBy(
            @Param("categoryL1Id") Long categoryL1Id,
            @Param("categoryL2Id") Long categoryL2Id,
            @Param("factory") String factory,
            @Param("area") String area,
            @Param("decision") String decision,
            @Param("aiRelation") String aiRelation,
            @Param("startTime") String startTime,
            @Param("endTime") String endTime);
}
