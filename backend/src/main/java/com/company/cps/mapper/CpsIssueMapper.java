package com.company.cps.mapper;

import com.company.cps.domain.CpsIssue;
import com.company.cps.dto.CpsIssueListItemResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface CpsIssueMapper {

    /**
     * 新增巡检问题主记录。
     */
    void insert(CpsIssue issue);

    /**
     * 根据问题 ID 查询问题主记录。
     */
    Optional<CpsIssue> findById(@Param("id") Long id);

    /**
     * 更新问题流程相关字段，包括状态、处理人、原因措施、审核意见和关闭时间。
     */
    void updateWorkflowFields(CpsIssue issue);

    /**
     * 按页签查询当前用户的问题列表。
     */
    List<CpsIssueListItemResponse> listByTab(
            @Param("tab") String tab,
            @Param("empNo") String empNo,
            @Param("limit") int limit,
            @Param("offset") int offset
    );
}
