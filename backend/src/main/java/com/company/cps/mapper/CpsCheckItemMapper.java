package com.company.cps.mapper;

import com.company.cps.domain.CpsCheckItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface CpsCheckItemMapper {

    List<CpsCheckItem> findAll(
            @Param("photoCategory") String photoCategory,
            @Param("status") String status,
            @Param("enabled") Boolean enabled
    );

    Optional<CpsCheckItem> findById(@Param("id") Long id);

    Optional<CpsCheckItem> findByCode(@Param("itemCode") String itemCode);

    int insert(CpsCheckItem item);

    int update(CpsCheckItem item);

    int setEnabled(@Param("id") Long id, @Param("enabled") Boolean enabled, @Param("updatedBy") String updatedBy);
}
