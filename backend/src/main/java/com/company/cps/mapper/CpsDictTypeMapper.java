package com.company.cps.mapper;

import com.company.cps.domain.CpsDictType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/** 字典类型 mapper（cps_dict_type，V20261016）。 */
@Mapper
public interface CpsDictTypeMapper {

    List<CpsDictType> findAll(@Param("enabled") Boolean enabled);

    Optional<CpsDictType> findById(@Param("id") Long id);

    Optional<CpsDictType> findByCode(@Param("typeCode") String typeCode);

    int insert(CpsDictType type);

    int update(CpsDictType type);

    int setEnabled(@Param("id") Long id, @Param("enabled") Boolean enabled, @Param("updatedBy") String updatedBy);

    int deleteById(@Param("id") Long id);
}
