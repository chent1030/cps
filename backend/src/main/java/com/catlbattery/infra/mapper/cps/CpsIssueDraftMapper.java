package com.catlbattery.infra.mapper.cps;

import com.catlbattery.domain.entity.cps.CpsIssueDraft;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CpsIssueDraftMapper {

    void insert(CpsIssueDraft draft);

    /** 覆盖更新（WHERE 带创建人 + ACTIVE 状态，防越权改他人/已提交草稿）。返回影响行数。 */
    int updateOwnActive(@Param("id") Long id,
                        @Param("creatorEmpNo") String creatorEmpNo,
                        @Param("title") String title,
                        @Param("inspectionTypeCode") String inspectionTypeCode,
                        @Param("factory") String factory,
                        @Param("area") String area,
                        @Param("draftData") String draftData);

    CpsIssueDraft findById(@Param("id") Long id);

    /** 本人某状态草稿列表（updated_at 倒序）。 */
    List<CpsIssueDraft> listByCreator(@Param("creatorEmpNo") String creatorEmpNo,
                                      @Param("status") String status,
                                      @Param("limit") int limit);

    /** 提交归档：ACTIVE → SUBMITTED + 回填 issueId。返回影响行数。 */
    int markSubmitted(@Param("id") Long id,
                     @Param("creatorEmpNo") String creatorEmpNo,
                     @Param("submittedIssueId") Long submittedIssueId);

    /** 物理删除（本人草稿）。 */
    int deleteOwn(@Param("id") Long id, @Param("creatorEmpNo") String creatorEmpNo);
}
