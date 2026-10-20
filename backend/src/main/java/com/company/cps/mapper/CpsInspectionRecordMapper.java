package com.company.cps.mapper;

import com.company.cps.domain.CpsInspectionRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CpsInspectionRecordMapper {

    void insert(CpsInspectionRecord record);

    CpsInspectionRecord findById(@Param("id") Long id);

    /** 管理端分页（可按类型/区域/状态/巡检人筛选） */
    List<CpsInspectionRecord> findPage(@Param("typeCode") String typeCode,
                                       @Param("areaId") Long areaId,
                                       @Param("recordStatus") String recordStatus,
                                       @Param("checkEmpNo") String checkEmpNo,
                                       @Param("keyword") String keyword,
                                       @Param("limit") int limit,
                                       @Param("offset") int offset);

    long countPage(@Param("typeCode") String typeCode,
                   @Param("areaId") Long areaId,
                   @Param("recordStatus") String recordStatus,
                   @Param("checkEmpNo") String checkEmpNo,
                   @Param("keyword") String keyword);

    /** 状态推进（乐观：仅当前状态匹配才更新） */
    int updateStatus(@Param("id") Long id,
                     @Param("fromStatus") String fromStatus,
                     @Param("toStatus") String toStatus);

    /** 提交锁定：写 judge_status/score/submitted_at + 状态推进 */
    int submit(@Param("id") Long id,
               @Param("fromStatus") String fromStatus,
               @Param("toStatus") String toStatus,
               @Param("judgeStatus") String judgeStatus,
               @Param("score") Integer score);

    int updateScore(@Param("id") Long id, @Param("score") Integer score, @Param("judgeStatus") String judgeStatus);

    /** Phase D：某计划任务下已判定（JUDGED）记录数——多区域任务完成度判定 */
    long countJudgedByPlanTask(@Param("planTaskId") Long planTaskId);
}
