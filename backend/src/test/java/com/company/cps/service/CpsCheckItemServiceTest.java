package com.company.cps.service;

import com.company.cps.domain.CpsCheckItem;
import com.company.cps.dto.CpsCheckItemRequest;
import com.company.cps.mapper.CpsCheckItemMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** B2 点检项配置（§23.2 字段 + 扣分值非负 + 状态枚举 + 配置版本自动递增）。 */
@ExtendWith(MockitoExtension.class)
class CpsCheckItemServiceTest {

    @Mock private CpsCheckItemMapper checkItemMapper;

    private CpsCheckItemService service;
    private CpsCheckItemRequest request;

    @BeforeEach
    void setUp() {
        service = new CpsCheckItemService(checkItemMapper);
        request = new CpsCheckItemRequest();
        request.setItemCode("CI-001");
        request.setContent("地面无积水");
        request.setPhotoCategory("地面");
        request.setDeductScore(5);
    }

    @Test
    void createDefaultsStatusApplicableAndInserts() {
        when(checkItemMapper.findByCode("CI-001")).thenReturn(Optional.empty());
        final CpsCheckItem[] inserted = new CpsCheckItem[1];
        when(checkItemMapper.insert(any(CpsCheckItem.class))).thenAnswer(inv -> {
            inserted[0] = (CpsCheckItem) inv.getArgument(0);
            inserted[0].setId(1L);
            return 1;
        });
        when(checkItemMapper.findById(1L)).thenAnswer(inv -> Optional.of(inserted[0]));

        service.save(null, request, "admin");

        verify(checkItemMapper).insert(any(CpsCheckItem.class));
        assertEquals("APPLICABLE", inserted[0].getStatus());
    }

    @Test
    void createRejectsNegativeScoreAndBadStatusAndDuplicateCode() {
        request.setDeductScore(-1);
        assertThrows(IllegalArgumentException.class, () -> service.save(null, request, "admin"));

        request.setDeductScore(5);
        request.setStatus("WHATEVER");
        assertThrows(IllegalArgumentException.class, () -> service.save(null, request, "admin"));

        request.setStatus("APPLICABLE");
        CpsCheckItem existing = new CpsCheckItem();
        existing.setId(9L);
        when(checkItemMapper.findByCode("CI-001")).thenReturn(Optional.of(existing));
        assertThrows(IllegalStateException.class, () -> service.save(null, request, "admin"));
    }

    @Test
    void updateAllowsKeepingOwnCodeAndBumpsConfigVersionInSql() {
        CpsCheckItem existing = new CpsCheckItem();
        existing.setId(1L);
        existing.setItemCode("CI-001");
        existing.setConfigVersion(3);
        when(checkItemMapper.findById(1L)).thenReturn(Optional.of(existing));
        when(checkItemMapper.findByCode("CI-001")).thenReturn(Optional.of(existing));
        when(checkItemMapper.update(any(CpsCheckItem.class))).thenReturn(1);

        assertDoesNotThrow(() -> service.save(1L, request, "admin"));
        verify(checkItemMapper).update(any(CpsCheckItem.class));
        // config_version 递增由 XML `config_version = config_version + 1` 保证（契约测试覆盖）
    }
}
