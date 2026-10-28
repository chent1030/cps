package com.catlbattery.infra.mapper.cps;

import com.catlbattery.domain.entity.cps.CpsInitialReviewResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CpsInitialReviewResultMapper {

    /** 结果落库（uk_initial_review_result_task 唯一键 = 回调幂等兜底）。 */
    void insert(CpsInitialReviewResult result);

    CpsInitialReviewResult findByTaskId(@Param("taskId") Long taskId);

    List<CpsInitialReviewResult> findByIssueId(@Param("issueId") Long issueId);
}
