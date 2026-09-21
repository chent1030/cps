package com.company.cps.mapper;

import com.company.cps.domain.CpsAdminUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.Optional;

@Mapper
public interface CpsAdminUserMapper {
    Optional<CpsAdminUser> findEnabledByCredentials(@Param("empNo") String empNo, @Param("empName") String empName);
}
