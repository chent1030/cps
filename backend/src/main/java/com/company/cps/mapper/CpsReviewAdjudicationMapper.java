package com.company.cps.mapper;

import com.company.cps.domain.CpsReviewAdjudication;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CpsReviewAdjudicationMapper {

    /** 写入裁决（uk issue_id+version_no，重复插入抛 DuplicateKeyException=幂等兜底）。 */
    void insert(CpsReviewAdjudication adjudication);

    CpsReviewAdjudication findByIssueAndVersion(@Param("issueId") Long issueId, @Param("versionNo") Integer versionNo);

    CpsReviewAdjudication findLatestByIssueId(@Param("issueId") Long issueId);
}
