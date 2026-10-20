package com.company.cps.mapper;

import com.company.cps.domain.CpsInspectionArea;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CpsInspectionAreaMapper {

    void insert(CpsInspectionArea area);

    int update(CpsInspectionArea area);

    CpsInspectionArea findById(@Param("id") Long id);

    CpsInspectionArea findByCode(@Param("areaCode") String areaCode);

    /** 管理端分页/筛选 */
    List<CpsInspectionArea> findPage(@Param("typeCode") String typeCode,
                                     @Param("factory") String factory,
                                     @Param("keyword") String keyword,
                                     @Param("enabled") Boolean enabled,
                                     @Param("limit") int limit,
                                     @Param("offset") int offset);

    long countPage(@Param("typeCode") String typeCode,
                   @Param("factory") String factory,
                   @Param("keyword") String keyword,
                   @Param("enabled") Boolean enabled);

    /** 某类型下全部启用区域（Android 任务列表用） */
    List<CpsInspectionArea> listEnabledByType(@Param("typeCode") String typeCode);

    int setEnabled(@Param("id") Long id, @Param("enabled") Boolean enabled, @Param("updatedBy") String updatedBy);

    int deleteById(@Param("id") Long id);

    /** 类型被引用检查 */
    long countByTypeCode(@Param("typeCode") String typeCode);
}
