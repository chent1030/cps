package com.company.cps.mapper;

import com.company.cps.domain.CpsDict;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/** 字典条目 mapper（cps_dict i JOIN cps_dict_type t，双表结构 V20261016）。 */
@Mapper
public interface CpsDictMapper {

    List<CpsDict> findAll(
            @Param("typeCode") String typeCode,
            @Param("enabled") Boolean enabled
    );

    Optional<CpsDict> findById(@Param("id") Long id);

    Optional<CpsDict> findByTypeAndValue(
            @Param("typeCode") String typeCode,
            @Param("itemValue") String itemValue
    );

    int countEnabled(
            @Param("typeCode") String typeCode,
            @Param("itemValue") String itemValue
    );

    int countByTypeId(@Param("typeId") Long typeId);

    int insert(CpsDict dict);

    int update(CpsDict dict);

    int setEnabled(@Param("id") Long id, @Param("enabled") Boolean enabled, @Param("updatedBy") String updatedBy);

    int deleteById(@Param("id") Long id);
}
