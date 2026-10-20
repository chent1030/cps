package com.company.cps.mapper;

import com.company.cps.domain.CpsApprovalStep;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CpsApprovalStepMapper {
    void insert(CpsApprovalStep step);

    List<CpsApprovalStep> findByInstance(@Param("instanceId") Long instanceId);

    /** 取下一个 seq（SELECT MAX + 1，锁住避免并发写同 seq） */
    Integer nextSeq(@Param("instanceId") Long instanceId);
}
