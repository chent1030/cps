package com.company.cps.mapper;

import com.company.cps.domain.CpsInspectionPlanTask;
import com.company.cps.domain.CpsInspectionPlanTaskType;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface CpsInspectionPlanTaskMapper {

    List<CpsInspectionPlanTask> findByPlanId(@Param("planId") Long planId);

    CpsInspectionPlanTask findByPlanIdAndTaskType(
            @Param("planId") Long planId,
            @Param("taskType") CpsInspectionPlanTaskType taskType);

    /**
     * 插入：依赖 UNIQUE(plan_id, task_type) 兜底幂等（重复键 MySQL 抛 DuplicateKeyException，
     * 由 service 捕获并改走"补建"路径——查询已有 task 返回）。
     */
    int insert(CpsInspectionPlanTask task);

    List<CpsInspectionPlanTaskType> findExistingTaskTypes(@Param("planId") Long planId);
}
