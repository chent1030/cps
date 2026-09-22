package com.company.cps.mapper;

import com.company.cps.domain.CpsRectificationSubmission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CpsRectificationSubmissionMapper {

    /** 新增整改提交版本（提交时快照+锁定）。 */
    void insert(CpsRectificationSubmission submission);

    CpsRectificationSubmission findById(@Param("id") Long id);

    CpsRectificationSubmission findByIssueAndVersion(@Param("issueId") Long issueId, @Param("versionNo") Integer versionNo);

    /** 按版本倒序取该问题最新一次提交。 */
    CpsRectificationSubmission findLatestByIssueId(@Param("issueId") Long issueId);

    /** 新版本提交后，将旧 LOCKED 版本置为 SUPERSEDED。 */
    int markSuperseded(@Param("issueId") Long issueId);

    /** 人工裁决（通过/退回）后，将当前版本置为 REVIEWED。 */
    int markReviewed(@Param("issueId") Long issueId, @Param("versionNo") Integer versionNo);

    /** 管理端按问题查看全部提交版本。 */
    List<CpsRectificationSubmission> findByIssueId(@Param("issueId") Long issueId);
}
