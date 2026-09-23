package com.company.cps.mapper;

import com.company.cps.domain.CpsRoomCheckRecordItem;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface CpsRoomCheckRecordItemMapper {

    int insertBatch(@Param("items") List<CpsRoomCheckRecordItem> items);

    List<CpsRoomCheckRecordItem> findByRecordId(@Param("recordId") Long recordId);

    CpsRoomCheckRecordItem findById(@Param("id") Long id);

    /** 照片补传/重拍：仅未判定单可更新（JUDGED 后锁定）。 */
    int updatePhoto(@Param("id") Long id,
                    @Param("photoObjectKey") String photoObjectKey,
                    @Param("photoFileName") String photoFileName);

    /** 判定结果回写明细（含降级 PENDING）。 */
    int updateJudgeResult(@Param("id") Long id,
                          @Param("judgeResult") String judgeResult,
                          @Param("judgeReason") String judgeReason,
                          @Param("finalResult") String finalResult,
                          @Param("judgedAt") java.time.LocalDateTime judgedAt);
}
