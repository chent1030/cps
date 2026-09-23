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
}
