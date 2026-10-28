package com.catlbattery.infra.mapper.cps;

import com.catlbattery.domain.entity.cps.CpsInspectionRecord;
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

    /**
     * 提交复核：IN_PROGRESS → PENDING_REVIEW，落 AI 初审结论 + 得分 + 轮数 + 整改责任人（区域 keeper）。
     */
    int updateToReview(@Param("id") Long id,
                       @Param("toStatus") String toStatus,
                       @Param("aiOverall") String aiOverall,
                       @Param("aiScore") Integer aiScore,
                       @Param("aiReason") String aiReason,
                       @Param("reviewRound") Integer reviewRound,
                       @Param("keeperEmpNo") String keeperEmpNo,
                       @Param("keeperEmpName") String keeperEmpName);

    /**
     * 复核裁决：PENDING_REVIEW → 通过(PENDING_RECTIFY，办理人=整改责任人) / 退回(IN_PROGRESS，办理人=提交人)。
     */
    int updateReviewDecision(@Param("id") Long id,
                             @Param("toStatus") String toStatus,
                             @Param("reviewEmpNo") String reviewEmpNo,
                             @Param("reviewEmpName") String reviewEmpName,
                             @Param("reviewOpinion") String reviewOpinion,
                             @Param("handlerEmpNo") String handlerEmpNo,
                             @Param("handlerEmpName") String handlerEmpName);

    /**
     * 整改提交：PENDING_RECTIFY → JUDGED，落整改三要素 + 照片 + C-01 结论。
     * 整改责任人在提交复核时已写入 rectify_emp_no/name（区域 keeper），此处不覆盖。
     */
    int updateRectify(@Param("id") Long id,
                      @Param("toStatus") String toStatus,
                      @Param("reason") String reason,
                      @Param("shortTerm") String shortTerm,
                      @Param("longTerm") String longTerm,
                      @Param("attachmentIds") String attachmentIds,
                      @Param("aiOverall") String aiOverall,
                      @Param("aiReason") String aiReason);

    /** 按状态取（web 复核列表），按提交时间升序，limit 防爆 */
    List<CpsInspectionRecord> findByStatus(@Param("recordStatus") String recordStatus,
                                           @Param("limit") int limit);

    /** 按办理人+状态取（Android「我的待整改」） */
    List<CpsInspectionRecord> findByHandler(@Param("recordStatus") String recordStatus,
                                            @Param("empNo") String empNo,
                                            @Param("limit") int limit);

    /** Phase D：某计划任务下已判定（JUDGED）记录数——多区域任务完成度判定 */
    long countJudgedByPlanTask(@Param("planTaskId") Long planTaskId);
}
