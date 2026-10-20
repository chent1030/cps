package com.company.cps.mapper;

import com.company.cps.domain.CpsInspectionRecordItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CpsInspectionRecordItemMapper {

    void insert(CpsInspectionRecordItem item);

    void batchInsert(@Param("items") List<CpsInspectionRecordItem> items);

    List<CpsInspectionRecordItem> findByRecord(@Param("recordId") Long recordId);

    CpsInspectionRecordItem findById(@Param("id") Long id);

    /** 执行时更新单项结果（合格/文本/照片） */
    int updateResult(@Param("id") Long id,
                     @Param("passResult") String passResult,
                     @Param("textValue") String textValue,
                     @Param("photoObjectKey") String photoObjectKey,
                     @Param("photoFileName") String photoFileName,
                     @Param("photoCount") Integer photoCount);

    /** 判定/确认结果更新 */
    int updateJudge(@Param("id") Long id,
                    @Param("judgeResult") String judgeResult,
                    @Param("judgeReason") String judgeReason,
                    @Param("finalResult") String finalResult);

    /** 人工修正留痕（PRD 24.2.5 口径） */
    int updateFinalWithTrace(@Param("id") Long id,
                             @Param("finalResult") String finalResult,
                             @Param("modifiedBy") String modifiedBy,
                             @Param("modifyReason") String modifyReason);
}
