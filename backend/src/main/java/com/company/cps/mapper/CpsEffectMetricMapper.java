package com.company.cps.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * FR-12 效果评估指标查询（波次11）：
 * 四 metric（ai_pass_rate / human_override_rate / avg_close_duration_hours / recurrence_rate_30d）。
 * 复用 cps_review_adjudication JOIN cps_issue JOIN cps_problem_category（LEFT JOIN）与 cps_issue_flow_log 复发信号，
 * 计算口径见 CpsEffectMetricMapper.xml 各 select 注释。
 */
@Mapper
public interface CpsEffectMetricMapper {

    /**
     * AI 初次裁决=APPROVE 在所有裁决中占比（snapshot 化对照）。
     * ai_overall='PASS' 视为 AI 初次意见=通过，等同 APPROVE。
     */
    Double aiPassRate(
            @Param("periodStart") String periodStart,
            @Param("periodEnd") String periodEnd,
            @Param("metricKey") String metricKey,
            @Param("scopeKey") String scopeKey);

    /**
     * 人工 decision=REJECT 时 ai_relation=AGAINST_AI 占比。
     * 分子：decision='REJECT' AND ai_relation='AGAINST_AI'；分母：decision='REJECT'。
     */
    Double humanOverrideRate(
            @Param("periodStart") String periodStart,
            @Param("periodEnd") String periodEnd,
            @Param("metricKey") String metricKey,
            @Param("scopeKey") String scopeKey);

    /**
     * 从 issue.created_at 到 to_status='CLOSED' 平均小时数（按裁决 created_at 计算）。
     * 仅取 to_status='CLOSED' 的裁决；AVG(TIMESTAMPDIFF(SECOND, i.created_at, a.created_at)/3600)。
     */
    Double avgCloseDurationHours(
            @Param("periodStart") String periodStart,
            @Param("periodEnd") String periodEnd,
            @Param("metricKey") String metricKey,
            @Param("scopeKey") String scopeKey);

    /**
     * 90 天窗口内复发问题数 / 总问题数（窗口内 cps_review_adjudication JOIN cps_issue 样本）。
     * 复发判定：同一 issue 在窗口内 REOPEN+REJECT 信号累计 ≥ 1 次。
     */
    Double recurrenceRate(
            @Param("periodStart") String periodStart,
            @Param("periodEnd") String periodEnd,
            @Param("metricKey") String metricKey,
            @Param("scopeKey") String scopeKey);
}