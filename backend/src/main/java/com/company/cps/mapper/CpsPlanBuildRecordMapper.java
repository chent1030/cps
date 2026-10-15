package com.company.cps.mapper;

import com.company.cps.domain.CpsPlanBuildRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** D4 计划建单记录（cps_plan_build_record，append-only；AC-30）。 */
@Mapper
public interface CpsPlanBuildRecordMapper {

    int insert(CpsPlanBuildRecord record);

    /** 按计划查全部建单尝试（id 正序，历史可追溯）。 */
    List<CpsPlanBuildRecord> findByPlanId(@Param("planId") Long planId);
}
