package com.company.cps.mapper;

import com.company.cps.domain.CpsIssueFlowLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CpsIssueFlowLogMapper {

    /**
     * 新增问题流程流转日志。
     */
    void insert(CpsIssueFlowLog flowLog);

    /**
     * 查询指定问题的完整流程日志，按创建时间升序返回。
     */
    List<CpsIssueFlowLog> findByIssueId(@Param("issueId") Long issueId);
}
