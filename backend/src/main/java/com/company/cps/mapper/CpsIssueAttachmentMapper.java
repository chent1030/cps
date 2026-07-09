package com.company.cps.mapper;

import com.company.cps.domain.CpsIssueAttachment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface CpsIssueAttachmentMapper {

    /**
     * 新增上传附件记录；问题创建前 issueId 允许为空，创建问题时再绑定。
     */
    int insert(CpsIssueAttachment attachment);

    /**
     * 将已上传附件绑定到问题和指定阶段，并写入排序号及上传人信息。
     */
    int attachToIssue(
            @Param("attachmentId") Long attachmentId,
            @Param("issueId") Long issueId,
            @Param("stage") String stage,
            @Param("sortNo") int sortNo,
            @Param("createdBy") String createdBy,
            @Param("createdName") String createdName
    );

    /**
     * 查询指定问题在某个阶段下的附件列表。
     */
    List<CpsIssueAttachment> findByIssueAndStage(@Param("issueId") Long issueId, @Param("stage") String stage);

    /**
     * 根据附件 ID 查询附件信息。
     */
    Optional<CpsIssueAttachment> findById(@Param("id") Long id);
}
