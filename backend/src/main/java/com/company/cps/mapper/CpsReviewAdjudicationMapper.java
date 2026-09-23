package com.company.cps.mapper;

import com.company.cps.domain.CpsReviewAdjudication;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CpsReviewAdjudicationMapper {

    /** 写入裁决（uk issue_id+version_no，重复插入抛 DuplicateKeyException=幂等兜底）。 */
    void insert(CpsReviewAdjudication adjudication);

    CpsReviewAdjudication findByIssueAndVersion(@Param("issueId") Long issueId, @Param("versionNo") Integer versionNo);

    CpsReviewAdjudication findLatestByIssueId(@Param("issueId") Long issueId);

    /**
     * I 线记忆体系消费：分页查询裁决全集（JOIN cps_issue 透出 factory/area/category 过滤）。
     * 任一过滤参数为 null = 不过滤；created_at 区间走 adjudication.created_at。
     */
    List<CpsReviewAdjudication> findMemoryPage(
            @Param("issueId") Long issueId,
            @Param("decision") String decision,
            @Param("aiRelation") String aiRelation,
            @Param("reviewerEmpNo") String reviewerEmpNo,
            @Param("factory") String factory,
            @Param("area") String area,
            @Param("categoryL1Id") Long categoryL1Id,
            @Param("categoryL2Id") Long categoryL2Id,
            @Param("startTime") String startTime,
            @Param("endTime") String endTime,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    long countMemory(
            @Param("issueId") Long issueId,
            @Param("decision") String decision,
            @Param("aiRelation") String aiRelation,
            @Param("reviewerEmpNo") String reviewerEmpNo,
            @Param("factory") String factory,
            @Param("area") String area,
            @Param("categoryL1Id") Long categoryL1Id,
            @Param("categoryL2Id") Long categoryL2Id,
            @Param("startTime") String startTime,
            @Param("endTime") String endTime
    );
}
