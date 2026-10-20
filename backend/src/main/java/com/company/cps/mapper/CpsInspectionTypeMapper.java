package com.company.cps.mapper;

import com.company.cps.domain.CpsInspectionType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface CpsInspectionTypeMapper {

    /** 管理端分页查询（keyword 模糊匹配 code/name/description） */
    List<CpsInspectionType> findPage(@Param("keyword") String keyword,
                                     @Param("enabled") Integer enabled,
                                     @Param("offset") int offset,
                                     @Param("limit") int limit);

    int countPage(@Param("keyword") String keyword,
                  @Param("enabled") Integer enabled);

    /** 客户端只读已启用类型，按 sortOrder 升序 */
    List<CpsInspectionType> listEnabled();

    CpsInspectionType findById(@Param("id") Long id);

    CpsInspectionType findByCode(@Param("code") String code);

    int insert(CpsInspectionType t);

    int updateById(CpsInspectionType t);

    int deleteById(@Param("id") Long id);

    /** 删除前的引用检查：cps_issue 中 inspection_type_code 等于入参的记录数 */
    long countIssueByCode(@Param("code") String code);

    /** 简单状态自检：客户端启动时可拉取 [{code,name,enabled,formSchemaJson}] */
    List<Map<String, Object>> listEnabledLite();
}
