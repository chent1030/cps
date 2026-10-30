package com.catlbattery.infra.mapper.cps;

import com.catlbattery.domain.entity.cps.CpsCheckItem;
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

    /** B8：点检项被引用总数（区域/房型/类型 check_item_ids + 历史执行记录等） */
    long countReferences(@Param("id") Long id, @Param("itemCode") String itemCode);

    int deleteById(@Param("id") Long id);
}
