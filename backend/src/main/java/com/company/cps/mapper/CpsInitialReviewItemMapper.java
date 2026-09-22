package com.company.cps.mapper;

import com.company.cps.domain.CpsInitialReviewItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CpsInitialReviewItemMapper {

    /** 逐项意见批量落库（L/P 实际值随行写入）。 */
    int insertBatch(@Param("items") List<CpsInitialReviewItem> items);

    List<CpsInitialReviewItem> findByReviewId(@Param("reviewId") Long reviewId);

    List<CpsInitialReviewItem> findByTaskId(@Param("taskId") Long taskId);
}
