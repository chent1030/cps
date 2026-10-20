package com.company.cps.service;

import com.company.cps.domain.CpsCheckItem;
import com.company.cps.domain.CpsRoom;
import com.company.cps.dto.CpsRoomRequest;
import com.company.cps.mapper.CpsCheckItemMapper;
import com.company.cps.mapper.CpsRoomMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** B1 辅房配置（§23.1 字段必填 + 编号唯一 + 房型/风险等级走字典校验）。 */
@ExtendWith(MockitoExtension.class)
class CpsRoomServiceTest {

    @Mock private CpsRoomMapper roomMapper;
    @Mock private CpsDictService dictService;
    @Mock private CpsCheckItemMapper checkItemMapper;

    private CpsRoomService service;
    private CpsRoomRequest request;

    @BeforeEach
    void setUp() {
        service = new CpsRoomService(roomMapper, dictService, checkItemMapper);
        request = new CpsRoomRequest();
        request.setRoomCode("RM-001");
        request.setBuilding("1栋");
        request.setDoorNo("101");
        request.setRoomName("一号仓库");
        request.setRoomType("仓库");
        request.setKeeperEmpNo("E10001");
        request.setKeeperEmpName("张三");
    }

    @Test
    void createInsertsRoomWithAllKeeperFieldsAndDefaultEnabled() {
        when(dictService.valueExists(CpsDictService.DICT_ROOM_TYPE, "仓库")).thenReturn(true);
        when(dictService.valueExists(CpsDictService.DICT_RISK_LEVEL, "LOW")).thenReturn(true);
        when(roomMapper.findByCode("RM-001")).thenReturn(Optional.empty());
        final CpsRoom[] inserted = new CpsRoom[1];
        when(roomMapper.insert(any(CpsRoom.class))).thenAnswer(inv -> {
            inserted[0] = (CpsRoom) inv.getArgument(0);
            inserted[0].setId(1L);
            return 1;
        });
        when(roomMapper.findById(1L)).thenAnswer(inv -> Optional.of(inserted[0]));

        service.save(null, request, "admin");

        verify(roomMapper).insert(any(CpsRoom.class));
        assertEquals(Boolean.TRUE, inserted[0].getEnabled());
        assertEquals("E10001", inserted[0].getKeeperEmpNo());
    }

    @Test
    void createRejectsDuplicateRoomCode() {
        when(dictService.valueExists(CpsDictService.DICT_ROOM_TYPE, "仓库")).thenReturn(true);
        when(dictService.valueExists(CpsDictService.DICT_RISK_LEVEL, "LOW")).thenReturn(true);
        CpsRoom existing = new CpsRoom();
        existing.setId(9L);
        when(roomMapper.findByCode("RM-001")).thenReturn(Optional.of(existing));

        assertThrows(IllegalStateException.class, () -> service.save(null, request, "admin"));
    }

    @Test
    void createValidatesRequiredFieldsAndRiskLevel() {
        request.setKeeperEmpNo("");
        assertThrows(IllegalArgumentException.class, () -> service.save(null, request, "admin"));

        request.setKeeperEmpNo("E10001");
        request.setRiskLevel("EXTREME");
        assertThrows(IllegalArgumentException.class, () -> service.save(null, request, "admin"));
    }

    @Test
    void createRejectsRoomTypeNotInDict() {
        when(dictService.valueExists(CpsDictService.DICT_RISK_LEVEL, "LOW")).thenReturn(true);
        // roomType "仓库" 不在字典启用项（mock 默认 false）
        assertThrows(IllegalArgumentException.class, () -> service.save(null, request, "admin"));
    }

    @Test
    void saveNormalisesAndStoresConfiguredCheckItemIds() {
        when(dictService.valueExists(CpsDictService.DICT_ROOM_TYPE, "仓库")).thenReturn(true);
        when(dictService.valueExists(CpsDictService.DICT_RISK_LEVEL, "LOW")).thenReturn(true);
        when(roomMapper.findByCode("RM-001")).thenReturn(Optional.empty());
        CpsCheckItem c1 = new CpsCheckItem();
        when(checkItemMapper.findByCode("CI-1")).thenReturn(Optional.of(c1));
        when(checkItemMapper.findByCode("CI-2")).thenReturn(Optional.of(c1));
        request.setCheckItemIds(" CI-1 , CI-2 ,, CI-1 ");
        final CpsRoom[] inserted = new CpsRoom[1];
        when(roomMapper.insert(any(CpsRoom.class))).thenAnswer(inv -> {
            inserted[0] = (CpsRoom) inv.getArgument(0);
            inserted[0].setId(1L);
            return 1;
        });
        when(roomMapper.findById(1L)).thenAnswer(inv -> Optional.of(inserted[0]));

        service.save(null, request, "admin");

        // 去空白、去空片段、保序去重后回拼
        assertEquals("CI-1,CI-2", inserted[0].getCheckItemIds());
    }

    @Test
    void saveRejectsUnknownCheckItemCodes() {
        when(dictService.valueExists(CpsDictService.DICT_ROOM_TYPE, "仓库")).thenReturn(true);
        when(dictService.valueExists(CpsDictService.DICT_RISK_LEVEL, "LOW")).thenReturn(true);
        when(checkItemMapper.findByCode("CI-1")).thenReturn(Optional.of(new CpsCheckItem()));
        when(checkItemMapper.findByCode("CI-X")).thenReturn(Optional.empty());
        request.setCheckItemIds("CI-1,CI-X");

        assertThrows(IllegalArgumentException.class, () -> service.save(null, request, "admin"));
    }

    @Test
    void saveBlankCheckItemIdsStoresNull() {
        when(dictService.valueExists(CpsDictService.DICT_ROOM_TYPE, "仓库")).thenReturn(true);
        when(dictService.valueExists(CpsDictService.DICT_RISK_LEVEL, "LOW")).thenReturn(true);
        when(roomMapper.findByCode("RM-001")).thenReturn(Optional.empty());
        request.setCheckItemIds(" , ,");
        final CpsRoom[] inserted = new CpsRoom[1];
        when(roomMapper.insert(any(CpsRoom.class))).thenAnswer(inv -> {
            inserted[0] = (CpsRoom) inv.getArgument(0);
            inserted[0].setId(1L);
            return 1;
        });
        when(roomMapper.findById(1L)).thenAnswer(inv -> Optional.of(inserted[0]));

        service.save(null, request, "admin");

        assertEquals(null, inserted[0].getCheckItemIds());
    }
}
