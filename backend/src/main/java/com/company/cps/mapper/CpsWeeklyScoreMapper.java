package com.company.cps.mapper;

import com.company.cps.domain.CpsWeeklyScore;
import com.company.cps.domain.CpsWeeklyScoreLine;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

public interface CpsWeeklyScoreMapper {

    int insertHeader(CpsWeeklyScore score);

    int updateHeader(CpsWeeklyScore score);

    int insertLine(CpsWeeklyScoreLine line);

    int deleteLinesByHeader(@Param("weeklyScoreId") Long weeklyScoreId);

    CpsWeeklyScore findByWeekAndEmp(@Param("weekStartDate") LocalDate weekStartDate,
                                    @Param("empNo") String empNo);

    /**
     * 周评分列表：weekStartDate 必填；regionSupervisorId 可选（NULL=全区域）；
     * 默认按 total_score DESC, room_check_count DESC 排序。
     */
    List<CpsWeeklyScore> listByFilters(@Param("weekStartDate") LocalDate weekStartDate,
                                       @Param("regionSupervisorId") Long regionSupervisorId,
                                       @Param("offset") int offset,
                                       @Param("limit") int limit);

    long countByFilters(@Param("weekStartDate") LocalDate weekStartDate,
                        @Param("regionSupervisorId") Long regionSupervisorId);

    /** 本周所有员工汇总（recompute 时全量重建用）。 */
    List<CpsWeeklyScore> findAllByWeek(@Param("weekStartDate") LocalDate weekStartDate);

    List<CpsWeeklyScoreLine> findLinesByHeader(@Param("weeklyScoreId") Long weeklyScoreId);
}
