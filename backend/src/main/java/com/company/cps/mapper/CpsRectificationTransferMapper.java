package com.company.cps.mapper;

import com.company.cps.domain.CpsRectificationTransfer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CpsRectificationTransferMapper {

    /** 记录转办（转办后仅当前承办人办理，责任员工不变）。 */
    void insert(CpsRectificationTransfer transfer);

    List<CpsRectificationTransfer> findByIssueId(@Param("issueId") Long issueId);
}
