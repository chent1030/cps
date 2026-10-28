package com.catlbattery.infra.mapper.cps;

import com.catlbattery.domain.entity.cps.CpsAdminUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Mapper
public interface CpsAdminUserMapper {
    /** 仅按 empNo 查（不论 enabled），登录时用 */
    Optional<CpsAdminUser> findByEmpNo(@Param("empNo") String empNo);

    /** 按 empNo + empName + enabled=1 查询，兼容旧用法 */
    Optional<CpsAdminUser> findEnabledByCredentials(@Param("empNo") String empNo, @Param("empName") String empName);

    /** 分页查询 */
    List<CpsAdminUser> findPage(@Param("keyword") String keyword, @Param("status") String status, @Param("limit") int limit, @Param("offset") int offset);
    long countPage(@Param("keyword") String keyword, @Param("status") String status);

    List<CpsAdminUser> findAll();

    void insert(CpsAdminUser user);

    void updateProfile(@Param("id") Long id,
                       @Param("empName") String empName,
                       @Param("status") String status,
                       @Param("pagePermissions") String pagePermissions,
                       @Param("remark") String remark);

    void updatePassword(@Param("id") Long id,
                        @Param("passwordHash") String passwordHash,
                        @Param("salt") String salt,
                        @Param("mustChangePassword") Boolean mustChangePassword);

    void updateLastLoginAt(@Param("id") Long id, @Param("ts") LocalDateTime ts);

    void deleteById(@Param("id") Long id);
}
