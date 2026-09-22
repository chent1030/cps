package com.company.cps.mapper;

import com.company.cps.domain.CpsInspectionItem;
import com.company.cps.domain.CpsInspectionItemPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface CpsInspectionItemMapper {

    List<CpsInspectionItem> findAll(
            @Param("factory") String factory,
            @Param("enabled") Boolean enabled
    );

    Optional<CpsInspectionItem> findById(@Param("id") Long id);

    Optional<CpsInspectionItem> findByCode(@Param("itemCode") String itemCode);

    int insert(CpsInspectionItem item);

    int update(CpsInspectionItem item);

    int setEnabled(@Param("id") Long id, @Param("enabled") Boolean enabled, @Param("updatedBy") String updatedBy);

    // §30.1 权限：无配置行=开放，有配置行=仅授权人可见
    List<CpsInspectionItemPermission> findPermissions(@Param("itemId") Long itemId);

    /** 返回 empNo 可见的事项ID（无任何权限行的事项=开放，有权限行的需含该工号）。 */
    List<Long> findVisibleItemIds(@Param("empNo") String empNo);

    int countPermissions(@Param("itemId") Long itemId);

    int insertPermission(CpsInspectionItemPermission permission);

    int deletePermissionsByItemId(@Param("itemId") Long itemId);
}
