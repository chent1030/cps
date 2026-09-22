package com.company.cps.service;

import com.company.cps.domain.CpsInventoryItem;
import com.company.cps.dto.CpsInventoryItemRequest;
import com.company.cps.mapper.CpsInventoryItemMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** E1 台账（§25.1 必填/非负 + §25.2 库存<=预警数量含等于预警，AC-13）。 */
@ExtendWith(MockitoExtension.class)
class CpsInventoryItemServiceTest {

    @Mock private CpsInventoryItemMapper itemMapper;

    private CpsInventoryItemService service;
    private CpsInventoryItemRequest request;

    @BeforeEach
    void setUp() {
        service = new CpsInventoryItemService(itemMapper);
        request = new CpsInventoryItemRequest();
        request.setItemCode("INV-001");
        request.setItemName("灭火器");
        request.setUnit("个");
        request.setStockQty(10);
        request.setAlertThreshold(5);
        request.setFactory("F1");
        request.setStorageRoom("一号仓库");
        request.setRoomKeeperEmpNo("E10001");
        request.setRoomKeeperEmpName("张三");
    }

    @Test
    void createInsertsInventoryItemWithRoomKeeper() {
        when(itemMapper.findByCode("INV-001")).thenReturn(Optional.empty());
        final CpsInventoryItem[] inserted = new CpsInventoryItem[1];
        when(itemMapper.insert(any(CpsInventoryItem.class))).thenAnswer(inv -> {
            inserted[0] = (CpsInventoryItem) inv.getArgument(0);
            inserted[0].setId(1L);
            return 1;
        });
        when(itemMapper.findById(1L)).thenAnswer(inv -> Optional.of(inserted[0]));

        service.save(null, request, "admin");

        verify(itemMapper).insert(any(CpsInventoryItem.class));
        assertEquals(Integer.valueOf(10), inserted[0].getStockQty());
    }

    @Test
    void createRejectsNegativeStockOrThresholdAndDuplicateCode() {
        request.setStockQty(-1);
        assertThrows(IllegalArgumentException.class, () -> service.save(null, request, "admin"));

        request.setStockQty(10);
        request.setAlertThreshold(-2);
        assertThrows(IllegalArgumentException.class, () -> service.save(null, request, "admin"));

        request.setAlertThreshold(5);
        CpsInventoryItem existing = new CpsInventoryItem();
        existing.setId(9L);
        when(itemMapper.findByCode("INV-001")).thenReturn(Optional.of(existing));
        assertThrows(IllegalStateException.class, () -> service.save(null, request, "admin"));
    }

    @Test
    void lowStockBoundaryIncludesEqualPerAc13() {
        // AC-13：阈值5时库存4、5预警，6不预警（含等于）
        assertTrue(service.isLowStock(itemWithStock(4, 5)));
        assertTrue(service.isLowStock(itemWithStock(5, 5)));
        assertFalse(service.isLowStock(itemWithStock(6, 5)));
    }

    private static CpsInventoryItem itemWithStock(int stockQty, int threshold) {
        CpsInventoryItem item = new CpsInventoryItem();
        item.setStockQty(stockQty);
        item.setAlertThreshold(threshold);
        return item;
    }
}
