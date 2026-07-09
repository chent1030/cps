package com.company.cps.mapper;

import com.company.cps.domain.CpsIssueAiSuggestion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CpsIssueAiSuggestionMapper {

    /**
     * 新增问题创建时的 AI 建议记录。
     */
    void insert(CpsIssueAiSuggestion suggestion);

    /**
     * 查询指定问题最近一次 AI 建议记录。
     */
    CpsIssueAiSuggestion findLatestByIssueId(@Param("issueId") Long issueId);
}
