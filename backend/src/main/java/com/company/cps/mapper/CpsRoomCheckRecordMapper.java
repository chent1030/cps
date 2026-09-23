package com.company.cps.mapper;

import com.company.cps.domain.CpsRoomCheckRecord;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface CpsRoomCheckRecordMapper {

    int insert(CpsRoomCheckRecord record);

    CpsRoomCheckRecord findById(@Param("id") Long id);

    /** 幂等 start：同任务同房间存在未判定（待执行/执行中）的单则复用。 */
    CpsRoomCheckRecord findActiveByTaskAndRoom(@Param("planTaskId") Long planTaskId, @Param("roomId") Long roomId);

    /** 任务完成度计算：该计划任务下全部点检单。 */
    List<CpsRoomCheckRecord> findByPlanTaskId(@Param("planTaskId") Long planTaskId);

    List<CpsRoomCheckRecord> findByRoomAndSubmittedBetween(
            @Param("roomId") Long roomId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /** 提交判定：状态机推进 + judge_status + score + submitted_at。 */
    int updateJudgeResult(CpsRoomCheckRecord record);

    /** start/首张照片推进状态机与时间戳。 */
    int updateStatusFields(@Param("id") Long id,
                           @Param("recordStatus") String recordStatus,
                           @Param("startedAt") LocalDateTime startedAt);

    /** B4 排名聚合（D-08）：自然周内已判定成功记录按房间 SUM，总分降序、最早提交升序。 */
    List<java.util.Map<String, Object>> aggregateWeeklyScores(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /** B4 明细：自然周内某房间全部有效记录。 */
    List<CpsRoomCheckRecord> findJudgedByRoomAndSubmittedBetween(
            @Param("roomId") Long roomId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);
}
