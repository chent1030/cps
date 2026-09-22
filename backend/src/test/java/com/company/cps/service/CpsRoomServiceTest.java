package com.company.cps.service;

import com.company.cps.domain.CpsRoom;
import com.company.cps.dto.CpsRoomRequest;
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

/** B1 辅房配置（§23.1 字段必填 + 编号唯一 + 风险等级枚举）。 */
@ExtendWith(MockitoExtension.class)
class CpsRoomServiceTest {

    @Mock private CpsRoomMapper roomMapper;

    private CpsRoomService service;
    private CpsRoomRequest request;

    @BeforeEach
    void setUp() {
        service = new CpsRoomService(roomMapper);
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
}
