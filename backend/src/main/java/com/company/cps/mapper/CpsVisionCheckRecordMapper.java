package com.company.cps.mapper;

import com.company.cps.domain.CpsVisionCheckRecord;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface CpsVisionCheckRecordMapper {

    int insert(CpsVisionCheckRecord record);

    CpsVisionCheckRecord findById(@Param("id") Long id);

    /**
     * 视觉判定幂等：同一 fingerprint 已判过 → 取最新一条 AI_PASS/AI_FAIL/HUMAN_OVERRIDE 返回。
     * 任意指纹可能对应多条 record（重复提交照片），按 id DESC 取最近一次已落地结果。
     */
    CpsVisionCheckRecord findJudgedByFingerprint(@Param("fingerprint") String fingerprint);

    /** 仅过滤指纹的所有 record（含 PENDING/TIMEOUT）。 */
    List<CpsVisionCheckRecord> findByFingerprint(@Param("fingerprint") String fingerprint);

    /**
     * 分页列表（admin 端复盘）：
     * roomId / checkItemId / status / startTime / endTime 任选；page/size 在 service 层 off-bound 处理。
     */
    List<CpsVisionCheckRecord> listByFilters(@Param("roomId") Long roomId,
                                             @Param("checkItemId") Long checkItemId,
                                             @Param("status") String status,
                                             @Param("startTime") LocalDateTime startTime,
                                             @Param("endTime") LocalDateTime endTime,
                                             @Param("limit") int limit,
                                             @Param("offset") int offset);

    long countByFilters(@Param("roomId") Long roomId,
                        @Param("checkItemId") Long checkItemId,
                        @Param("status") String status,
                        @Param("startTime") LocalDateTime startTime,
                        @Param("endTime") LocalDateTime endTime);

    /**
     * 视觉判定结果回写（Python 侧契约回调 / async judge 完成）。
     * 携带 fingerprint 用于乐观锁——record 必须处于 AI_JUDGING 才允许覆盖；否则 0 行 = 重放幂等跳过。
     */
    int updateAiJudgeResult(CpsVisionCheckRecord record);

    /**
     * 状态流转（含 AI_JUDGING/PASS/FAIL/TIMEOUT/HUMAN_OVERRIDE）；
     * service 层用 caller 守卫 state 不变量，此处不做 CAS——纯 setter。
     */
    int updateStatusFields(CpsVisionCheckRecord record);
}