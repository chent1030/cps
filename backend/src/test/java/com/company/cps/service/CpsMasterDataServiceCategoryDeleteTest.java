package com.company.cps.service;

import com.company.cps.domain.CpsProblemCategory;
import com.company.cps.mapper.CpsAdminUserMapper;
import com.company.cps.mapper.CpsAreaPersonConfigMapper;
import com.company.cps.mapper.CpsProblemCategoryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 问题分类删除护栏：必须已停用、无下级分类、未被历史数据引用。 */
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

    private static CpsProblemCategory category(long id, boolean enabled) {
        CpsProblemCategory c = new CpsProblemCategory();
        c.setId(id);
        c.setCategoryName("设备漏油-" + id);
        c.setEnabled(enabled);
        return c;
    }

    @Test
    void rejectsEnabledCategory() {
        when(categoryMapper.findById(1L)).thenReturn(Optional.of(category(1L, true)));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.deleteCategory(1L, "admin"));

        assertEquals("请先停用该分类再删除：设备漏油-1", ex.getMessage());
        verify(categoryMapper, never()).deleteById(any());
    }

    @Test
    void rejectsCategoryWithChildren() {
        when(categoryMapper.findById(2L)).thenReturn(Optional.of(category(2L, false)));
        when(categoryMapper.countChildren(2L)).thenReturn(3L);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.deleteCategory(2L, "admin"));

        assertEquals("该分类下还有 3 个下级分类，请先删除下级分类", ex.getMessage());
        verify(categoryMapper, never()).deleteById(any());
    }

    @Test
    void rejectsCategoryReferencedByHistory() {
        when(categoryMapper.findById(3L)).thenReturn(Optional.of(category(3L, false)));
        when(categoryMapper.countChildren(3L)).thenReturn(0L);
        when(categoryMapper.countReferences(3L)).thenReturn(7L);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.deleteCategory(3L, "admin"));

        assertEquals("该分类已被 7 条历史数据引用，删除会丢失这些问题/案例的分类信息，请保留该分类（可继续停用）",
                ex.getMessage());
        verify(categoryMapper, never()).deleteById(any());
    }

    @Test
    void deletesCleanDisabledCategoryAndLogsAudit() {
        when(categoryMapper.findById(4L)).thenReturn(Optional.of(category(4L, false)));
        when(categoryMapper.countChildren(4L)).thenReturn(0L);
        when(categoryMapper.countReferences(4L)).thenReturn(0L);
        when(categoryMapper.deleteById(4L)).thenReturn(1);

        service.deleteCategory(4L, "admin");

        verify(categoryMapper).deleteById(4L);
        verify(auditLogService).record(eq("admin"), eq(null), eq("CATEGORY_DELETE"), eq("MASTER_DATA"),
                eq("4"), eq("分类：设备漏油-4"), eq(null), eq(null), eq("SUCCESS"), eq(null));
    }

    @Test
    void rejectsMissingCategory() {
        when(categoryMapper.findById(9L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.deleteCategory(9L, "admin"));
        verify(categoryMapper, never()).deleteById(any());
    }

    @Test
    void rejectsCategoryDeletedByAnotherRequest() {
        when(categoryMapper.findById(5L)).thenReturn(Optional.of(category(5L, false)));
        when(categoryMapper.countChildren(5L)).thenReturn(0L);
        when(categoryMapper.countReferences(5L)).thenReturn(0L);
        when(categoryMapper.deleteById(5L)).thenReturn(0);

        assertThrows(IllegalArgumentException.class, () -> service.deleteCategory(5L, "admin"));
        verify(auditLogService, never()).record(anyString(), any(), anyString(), anyString(), anyString(),
                anyString(), any(), any(), anyString(), any());
    }
}
