package com.company.cps.mapper;

import com.company.cps.domain.CpsProblemCategory;
import com.company.cps.dto.CpsOptionResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface CpsProblemCategoryMapper {

    List<CpsProblemCategory> findEnabledByParentId(@Param("parentId") Long parentId);

    List<CpsProblemCategory> findAll(@Param("parentId") Long parentId, @Param("enabled") Boolean enabled);

    List<CpsProblemCategory> findAdminPage(
            @Param("parentId") Long parentId,
            @Param("allLevels") Boolean allLevels,
            @Param("enabled") Boolean enabled,
            @Param("keyword") String keyword,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    List<CpsProblemCategory> findAdminExport(
            @Param("parentId") Long parentId,
            @Param("allLevels") Boolean allLevels,
            @Param("enabled") Boolean enabled,
            @Param("keyword") String keyword,
            @Param("limit") int limit
    );

    long countAdmin(
            @Param("parentId") Long parentId,
            @Param("allLevels") Boolean allLevels,
            @Param("enabled") Boolean enabled,
            @Param("keyword") String keyword
    );

    List<CpsOptionResponse> findEnabledOptionsByParentId(@Param("parentId") Long parentId);

    Optional<CpsProblemCategory> findById(@Param("id") Long id);

    /** 按父级 + 名称查（含已软删除的），用于新建时复用被删过的同名分类。 */
    Optional<CpsProblemCategory> findAnyByParentAndName(@Param("parentId") Long parentId,
                                                       @Param("categoryName") String categoryName);

    int upsert(CpsProblemCategory category);

    int setEnabled(@Param("id") Long id, @Param("enabled") Boolean enabled, @Param("updatedBy") String updatedBy);

    /** 软删除：deleted=1，历史数据仍按 ID 关联到分类名。 */
    int softDelete(@Param("id") Long id, @Param("updatedBy") String updatedBy);

    /** 未删除的下级分类数量：还有子分类时不允许删除。 */
    long countChildren(@Param("id") Long id);
}
