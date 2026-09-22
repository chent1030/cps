package com.company.cps.mapper;

import com.company.cps.domain.CpsInspectionPlan;
import com.company.cps.domain.CpsInspectionPlanStatus;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

public interface CpsInspectionPlanMapper {

    CpsInspectionPlan findById(@Param("id") Long id);

    Optional<CpsInspectionPlan> findBySourceRunIdAndPlanType(
            @Param("sourceRunId") String sourceRunId,
            @Param("planType") String planType);

    Optional<CpsInspectionPlan> findByDraftIdempotencyKey(@Param("key") String key);

    List<CpsInspectionPlan> findByStatus(@Param("status") CpsInspectionPlanStatus status);

    int insert(CpsInspectionPlan plan);

    /**
     * 乐观锁 CAS：lock_version 命中自增 1，否则返回 0。
     * 配套 status 字段一并更新（草稿回写：PENDING_REVIEW → 仍为 PENDING_REVIEW 但 config_version++）。
     */
    int updateDraftCas(CpsInspectionPlan plan);

    /** 批准 CAS：status 流转 PENDING_REVIEW → APPROVED，lock_version 自增。 */
    int approveCas(CpsInspectionPlan plan);

    /** 拒绝 CAS：status 流转 PENDING_REVIEW → REJECTED，lock_version 自增。 */
    int rejectCas(CpsInspectionPlan plan);
}
