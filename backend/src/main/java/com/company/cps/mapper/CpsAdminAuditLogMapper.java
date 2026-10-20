package com.company.cps.mapper;

import com.company.cps.domain.CpsAdminAuditLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CpsAdminAuditLogMapper {
    void insert(CpsAdminAuditLog log);

    List<CpsAdminAuditLog> findPage(@Param("empNo") String empNo,
                                    @Param("action") String action,
                                    @Param("status") String status,
                                    @Param("keyword") String keyword,
                                    @Param("limit") int limit,
                                    @Param("offset") int offset);
    long countPage(@Param("empNo") String empNo,
                   @Param("action") String action,
                   @Param("status") String status,
                   @Param("keyword") String keyword);
}
