package com.company.cps.mapper;

import com.company.cps.domain.CpsInventoryItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface CpsInventoryItemMapper {

    List<CpsInventoryItem> findAll(
            @Param("factory") String factory,
            @Param("storageRoom") String storageRoom,
            @Param("keyword") String keyword,
            @Param("lowStock") Boolean lowStock,
            @Param("enabled") Boolean enabled
    );

    Optional<CpsInventoryItem> findById(@Param("id") Long id);

    Optional<CpsInventoryItem> findByCode(@Param("itemCode") String itemCode);

    int insert(CpsInventoryItem item);

    int update(CpsInventoryItem item);

    int setEnabled(@Param("id") Long id, @Param("enabled") Boolean enabled, @Param("updatedBy") String updatedBy);
}
