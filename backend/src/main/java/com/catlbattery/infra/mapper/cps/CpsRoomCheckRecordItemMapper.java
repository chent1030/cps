package com.catlbattery.infra.mapper.cps;

import com.catlbattery.domain.entity.cps.CpsRoomCheckRecordItem;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface CpsRoomCheckRecordItemMapper {

    int insertBatch(@Param("items") List<CpsRoomCheckRecordItem> items);

    List<CpsRoomCheckRecordItem> findByRecordId(@Param("recordId") Long recordId);

    CpsRoomCheckRecordItem findById(@Param("id") Long id);

    /** 照片补传/重拍：仅未判定单可更新（JUDGED 后锁定）。 */
    int updatePhoto(@Param("id") Long id,
                    @Param("photoUrl") String photoUrl,
                    @Param("photoFileName") String photoFileName);

    /** 判定结果回写明细（含降级 PENDING）。 */
    int updateJudgeResult(@Param("id") Long id,
                          @Param("judgeResult") String judgeResult,
                          @Param("judgeReason") String judgeReason,
                          @Param("finalResult") String finalResult,
                          @Param("judgedAt") java.time.LocalDateTime judgedAt);

    /** PRD 24.2.2/24.2.5：点检人确认/修正最终结果（仅与 AI 判定不同的项落修正留痕）。 */
    int updateFinalResult(@Param("id") Long id,
                          @Param("finalResult") String finalResult,
                          @Param("finalModifiedBy") String finalModifiedBy,
                          @Param("finalModifiedAt") java.time.LocalDateTime finalModifiedAt,
                          @Param("finalModifyReason") String finalModifyReason);
}
