package com.company.cps.service;

import com.company.cps.domain.CpsProblemCategory;
import com.company.cps.dto.CpsProblemCategoryRequest;
import com.company.cps.mapper.CpsAdminUserMapper;
import com.company.cps.mapper.CpsAreaPersonConfigMapper;
import com.company.cps.mapper.CpsProblemCategoryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 问题分类软删除：停用 + 无未删除下级才能删；删除只打标记；同名重建时复用被删行。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CpsMasterDataServiceCategoryDeleteTest {

    @Mock private CpsProblemCategoryMapper categoryMapper;
    @Mock private CpsAreaPersonConfigMapper areaPersonConfigMapper;
    @Mock private CpsAdminUserMapper adminUserMapper;
    @Mock private CpsAdminUserService adminUserService;
    @Mock private CpsAdminAuditLogService auditLogService;

    private CpsMasterDataService service;

    @BeforeEach
    void setUp() {
        service = new CpsMasterDataService(categoryMapper, areaPersonConfigMapper, adminUserMapper,
                adminUserService, auditLogService);
    }

    private static CpsProblemCategory category(long id, boolean enabled, boolean deleted) {
        CpsProblemCategory c = new CpsProblemCategory();
        c.setId(id);
        c.setParentId(0L);
        c.setCategoryLevel(1);
        c.setCategoryName("设备漏油-" + id);
        c.setEnabled(enabled);
        c.setDeleted(deleted);
        return c;
    }

    private static CpsProblemCategoryRequest request(String name) {
        CpsProblemCategoryRequest r = new CpsProblemCategoryRequest();
        r.setCategoryName(name);
        r.setCategoryLevel(1);
        r.setEnabled(true);
        return r;
    }

    @Test
    void rejectsEnabledCategory() {
        when(categoryMapper.findById(1L)).thenReturn(Optional.of(category(1L, true, false)));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.deleteCategory(1L, "admin"));

        assertEquals("请先停用该分类再删除：设备漏油-1", ex.getMessage());
        verify(categoryMapper, never()).softDelete(any(), anyString());
    }

    @Test
    void rejectsCategoryWithChildren() {
        when(categoryMapper.findById(2L)).thenReturn(Optional.of(category(2L, false, false)));
        when(categoryMapper.countChildren(2L)).thenReturn(3L);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.deleteCategory(2L, "admin"));

        assertEquals("该分类下还有 3 个下级分类，请先删除下级分类", ex.getMessage());
        verify(categoryMapper, never()).softDelete(any(), anyString());
    }

    @Test
    void softDeletesDisabledLeafCategoryEvenWhenReferencedByHistory() {
        when(categoryMapper.findById(3L)).thenReturn(Optional.of(category(3L, false, false)));
        when(categoryMapper.countChildren(3L)).thenReturn(0L);
        when(categoryMapper.softDelete(3L, "admin")).thenReturn(1);

        service.deleteCategory(3L, "admin");

        verify(categoryMapper).softDelete(3L, "admin");
        verify(auditLogService).record(eq("admin"), eq(null), eq("CATEGORY_DELETE"), eq("MASTER_DATA"),
                eq("3"), eq("分类（软删除）：设备漏油-3"), eq(null), eq(null), eq("SUCCESS"), eq(null));
    }

    @Test
    void rejectsMissingCategory() {
        when(categoryMapper.findById(9L)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.deleteCategory(9L, "admin"));

        assertEquals("分类不存在: 9", ex.getMessage());
    }

    @Test
    void rejectsCategoryAlreadyDeleted() {
        when(categoryMapper.findById(5L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.deleteCategory(5L, "admin"));
        verify(categoryMapper, never()).softDelete(any(), anyString());
    }

    @Test
    void reusesSoftDeletedRowWhenCreatingSameName() {
        when(categoryMapper.findAnyByParentAndName(0L, "设备漏油")).thenReturn(Optional.of(category(7L, false, true)));

        service.saveCategory(request("设备漏油"), "admin");

        ArgumentCaptor<CpsProblemCategory> captor = ArgumentCaptor.forClass(CpsProblemCategory.class);
        verify(categoryMapper).upsert(captor.capture());
        assertEquals(7L, captor.getValue().getId(), "应复用被软删除的那一行，历史引用不断链");
        assertEquals("设备漏油", captor.getValue().getCategoryName());
    }

    @Test
    void rejectsCreatingDuplicateLiveName() {
        when(categoryMapper.findAnyByParentAndName(0L, "设备漏油")).thenReturn(Optional.of(category(8L, true, false)));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.saveCategory(request("设备漏油"), "admin"));

        assertEquals("该父级下已存在同名分类：设备漏油", ex.getMessage());
        verify(categoryMapper, never()).upsert(any());
    }

    @Test
    void createsNewCategoryWhenNameNeverUsed() {
        when(categoryMapper.findAnyByParentAndName(0L, "新分类")).thenReturn(Optional.empty());

        CpsProblemCategory saved = service.saveCategory(request("新分类"), "admin");

        assertNull(saved.getId());
        verify(categoryMapper).upsert(any(CpsProblemCategory.class));
    }
}
