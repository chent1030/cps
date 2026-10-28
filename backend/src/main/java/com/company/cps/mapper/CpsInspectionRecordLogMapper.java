package com.company.cps.mapper;

import com.company.cps.domain.CpsInspectionRecordLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CpsInspectionRecordLogMapper {

    void insert(CpsInspectionRecordLog log);

    /** 记录时间线（按时间升序） */
    List<CpsInspectionRecordLog> findByRecord(@Param("recordId") Long recordId);
}
