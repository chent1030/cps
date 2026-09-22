package com.company.cps.service;

import com.company.cps.domain.CpsInspectionItem;
import com.company.cps.domain.CpsInspectionItemPermission;
import com.company.cps.dto.CpsInspectionItemRequest;
import com.company.cps.mapper.CpsInspectionItemMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** §30.1 事项权限语义：无配置行=开放；有配置行=仅授权工号可见。 */
@ExtendWith(MockitoExtension.class)
class CpsInspectionItemServiceTest {

    @Mock private CpsInspectionItemMapper itemMapper;

    private CpsInspectionItemService service;

    @BeforeEach
    void setUp() {
        service = new CpsInspectionItemService(itemMapper);
    }

    private static CpsInspectionItem item(long id, String code) {
        CpsInspectionItem item = new CpsInspectionItem();
        item.setId(id);
        item.setItemCode(code);
        item.setItemName("事项" + code);
        item.setEnabled(true);
        return item;
    }

    private static CpsInspectionItemPermission permission(String empNo) {
        CpsInspectionItemPermission p = new CpsInspectionItemPermission();
        p.setItemId(1L);
        p.setEmpNo(empNo);
        return p;
    }

    @Test
    void canViewOpenWhenNoPermissionRows() {
        when(itemMapper.findById(1L)).thenReturn(Optional.of(item(1L, "I-01")));
        when(itemMapper.countPermissions(1L)).thenReturn(0);

        assertTrue(service.canView(1L, "E99999"));
        verify(itemMapper, never()).findPermissions(1L);
    }

    @Test
    void canViewRestrictedGrantsOnlyPermittedEmpNo() {
        when(itemMapper.findById(1L)).thenReturn(Optional.of(item(1L, "I-01")));
        when(itemMapper.countPermissions(1L)).thenReturn(2);
        when(itemMapper.findPermissions(1L)).thenReturn(Arrays.asList(permission("E10001"), permission("E10002")));

        assertTrue(service.canView(1L, "E10001"));
        assertFalse(service.canView(1L, "E99999"));
    }

    @Test
    void canViewFalseForMissingItem() {
        when(itemMapper.findById(404L)).thenReturn(Optional.empty());
        assertFalse(service.canView(404L, "E10001"));
    }

    @Test
    void replacePermissionsDeletesThenInsertsAndEmptyListReopens() {
        when(itemMapper.findById(1L)).thenReturn(Optional.of(item(1L, "I-01")));
        when(itemMapper.findPermissions(1L)).thenReturn(Collections.emptyList());

        List<CpsInspectionItemPermission> result =
                service.replacePermissions(1L, Arrays.asList(permission("E10001"), permission("E10002")), "admin");
        // 空名单=删除全部后不再插入 → 事项回到开放（§30.1）
        service.replacePermissions(1L, null, "admin");

        assertTrue(result.isEmpty());
        verify(itemMapper, org.mockito.Mockito.times(2)).deletePermissionsByItemId(1L);
        verify(itemMapper, org.mockito.Mockito.times(2)).insertPermission(any(CpsInspectionItemPermission.class));
    }

    @Test
    void replacePermissionsRejectsBlankEmpNo() {
        when(itemMapper.findById(1L)).thenReturn(Optional.of(item(1L, "I-01")));
        assertThrows(IllegalArgumentException.class,
                () -> service.replacePermissions(1L, Collections.singletonList(permission("  ")), "admin"));
    }

    @Test
    void saveRejectsDuplicateCodeAndMissingFields() {
        CpsInspectionItemRequest request = new CpsInspectionItemRequest();
        request.setItemCode("I-01");
        request.setItemName("消防器材点检");
        when(itemMapper.findByCode("I-01")).thenReturn(Optional.of(item(9L, "I-01")));

        assertThrows(IllegalStateException.class, () -> service.save(null, request, "admin"));

        CpsInspectionItemRequest blank = new CpsInspectionItemRequest();
        blank.setItemCode("I-02");
        assertThrows(IllegalArgumentException.class, () -> service.save(null, blank, "admin"));
    }

    @Test
    void listVisibleKeepsOpenAndPermittedItemsOnly() {
        CpsInspectionItem open = item(1L, "I-01");
        CpsInspectionItem restrictedOther = item(2L, "I-02");
        CpsInspectionItem mine = item(3L, "I-03");
        when(itemMapper.findAll(null, null)).thenReturn(Arrays.asList(open, restrictedOther, mine));
        when(itemMapper.findVisibleItemIds("E10001")).thenReturn(Arrays.asList(1L, 3L));

        List<CpsInspectionItem> visible = service.listVisible(null, null, "E10001");

        assertEquals(2, visible.size());
        assertFalse(visible.contains(restrictedOther));
    }
}
