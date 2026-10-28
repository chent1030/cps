package com.catlbattery.infra.mapper.cps;

import com.catlbattery.domain.entity.cps.CpsInspectionPlanTask;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface CpsInspectionPlanTaskMapper {

    List<CpsInspectionPlanTask> findByPlanId(@Param("planId") Long planId);

    CpsInspectionPlanTask findByPlanIdAndTaskType(
            @Param("planId") Long planId,
            @Param("taskType") String taskType);

    /**
     * 插入：依赖 UNIQUE(plan_id, task_type) 兜底幂等（重复键 MySQL 抛 DuplicateKeyException，
     * 由 service 捕获并改走"补建"路径——查询已有 task 返回）。
     */
    int insert(CpsInspectionPlanTask task);

    /** 已有任务类型集合（Phase D：存巡检类型编码字符串） */
    List<String> findExistingTaskTypes(@Param("planId") Long planId);

    /** B3 mobile 任务列表：某负责人的辅房点检任务（状态可筛，空=全部未取消）。历史遗留接口，taskType 传字符串。 */
    List<CpsInspectionPlanTask> findCheckTasksForEmp(
            @Param("targetEmpNo") String targetEmpNo,
            @Param("taskType") String taskType,
            @Param("taskStatus") String taskStatus);

    /**
     * Phase D：我的分配任务（Android 任务列表）。
     * target_emp_no = 我 且状态未取消/未完成；按计划时间排序。
     */
    List<CpsInspectionPlanTask> findAssignedTasks(@Param("targetEmpNo") String targetEmpNo);

    CpsInspectionPlanTask findById(@Param("id") Long id);

    /** B3：执行推进/完成回写计划任务状态（PENDING→IN_PROGRESS→COMPLETED）。 */
    int updateTaskStatus(@Param("id") Long id, @Param("taskStatus") String taskStatus);
}
