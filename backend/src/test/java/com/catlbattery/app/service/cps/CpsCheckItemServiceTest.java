package com.catlbattery.app.service.cps;

import com.catlbattery.domain.entity.cps.CpsCheckItem;
import com.catlbattery.domain.entity.cps.dto.CpsCheckItemRequest;
import com.catlbattery.infra.mapper.cps.CpsCheckItemMapper;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** B2 点检项配置（§23.2 字段 + 扣分值非负 + 状态枚举 + 照片分类/适用房型走字典校验）。 */
@ExtendWith(MockitoExtension.class)
class CpsCheckItemServiceTest {

    @Mock private CpsCheckItemMapper checkItemMapper;
    @Mock private CpsDictService dictService;

    private CpsCheckItemService service;
    private CpsCheckItemRequest request;

    @BeforeEach
    void setUp() {
        service = new CpsCheckItemService(checkItemMapper, dictService);
        request = new CpsCheckItemRequest();
        request.setItemCode("CI-001");
        request.setContent("地面无积水");
        request.setPhotoCategory("地面");
        request.setDeductScore(5);
    }

    @Test
    void createDefaultsStatusApplicableAndInserts() {
        when(dictService.valueExists(CpsDictService.DICT_PHOTO_CATEGORY, "地面")).thenReturn(true);
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
        when(dictService.valueExists(CpsDictService.DICT_PHOTO_CATEGORY, "地面")).thenReturn(true);
        CpsCheckItem existing = new CpsCheckItem();
        existing.setId(9L);
        when(checkItemMapper.findByCode("CI-001")).thenReturn(Optional.of(existing));
        assertThrows(IllegalStateException.class, () -> service.save(null, request, "admin"));
    }

    @Test
    void updateAllowsKeepingOwnCodeAndBumpsConfigVersionInSql() {
        when(dictService.valueExists(CpsDictService.DICT_PHOTO_CATEGORY, "地面")).thenReturn(true);
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

    @Test
    void rejectsPhotoCategoryAndApplicableRoomTypesNotInDict() {
        request.setPhotoCategory("未知分类");
        assertThrows(IllegalArgumentException.class, () -> service.save(null, request, "admin"));

        request.setPhotoCategory("地面");
        when(dictService.valueExists(CpsDictService.DICT_PHOTO_CATEGORY, "地面")).thenReturn(true);
        when(dictService.valueExists(CpsDictService.DICT_ROOM_TYPE, "配电房")).thenReturn(true);
        request.setApplicableRoomTypes("配电房, 未知房型");
        assertThrows(IllegalArgumentException.class, () -> service.save(null, request, "admin"));
    }

    // B8：删除护栏——被引用拒绝（409 由 controller 映射）、无引用真删、不存在 400。
    @Test
    void deleteRejectsWhenReferencedByAreaOrHistory() {
        CpsCheckItem item = checkItem(4L, "J-C1");
        when(checkItemMapper.findById(4L)).thenReturn(Optional.of(item));
        when(checkItemMapper.countReferences(4L, "J-C1")).thenReturn(29L);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.delete(4L));

        assertEquals("该点检项已被 29 个巡检区域/历史记录引用，不能删除，请改为停用", ex.getMessage());
        verify(checkItemMapper, never()).deleteById(anyLong());
    }

    @Test
    void deleteRemovesWhenNoReference() {
        CpsCheckItem item = checkItem(99L, "CI-NEW");
        when(checkItemMapper.findById(99L)).thenReturn(Optional.of(item));
        when(checkItemMapper.countReferences(99L, "CI-NEW")).thenReturn(0L);
        when(checkItemMapper.deleteById(99L)).thenReturn(1);

        assertDoesNotThrow(() -> service.delete(99L));

        verify(checkItemMapper).deleteById(99L);
    }

    @Test
    void deleteMissingThrowsBadRequest() {
        when(checkItemMapper.findById(404L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.delete(404L));
        verify(checkItemMapper, never()).deleteById(anyLong());
    }

    private static CpsCheckItem checkItem(Long id, String code) {
        CpsCheckItem item = new CpsCheckItem();
        item.setId(id);
        item.setItemCode(code);
        return item;
    }
}
