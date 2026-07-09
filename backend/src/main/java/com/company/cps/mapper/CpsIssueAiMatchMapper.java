package com.company.cps.mapper;

import com.company.cps.domain.CpsIssueAiMatch;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CpsIssueAiMatchMapper {

    /**
     * 新增一次 AI 知识库匹配记录，用于保存候选结果、建议分类、原因和措施。
     */
    int insert(CpsIssueAiMatch match);
}
