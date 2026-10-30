package com.catlbattery.api.controller.v1.cps;

import com.catlbattery.app.service.cps.CpsCheckItemService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

/** B8：点检项 DELETE 契约——成功 {ok:true}；被引用时 409 + CONFLICT/message 响应体。 */
@ExtendWith(MockitoExtension.class)
class CpsCheckItemControllerTest {

    @Mock private CpsCheckItemService checkItemService;

    @Test
    void deleteReturnsOk() {
        CpsCheckItemController controller = new CpsCheckItemController(checkItemService);

        Map<String, Object> body = controller.delete(99L);

        verify(checkItemService).delete(99L);
        assertEquals(Boolean.TRUE, body.get("ok"));
    }

    @Test
    void conflictHandlerReturns409WithChineseMessage() {
        CpsCheckItemController controller = new CpsCheckItemController(checkItemService);
        String message = "该点检项已被 29 个巡检区域/历史记录引用，不能删除，请改为停用";

        ResponseEntity<Map<String, Object>> response =
                controller.handleConflict(new IllegalStateException(message));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("CONFLICT", response.getBody().get("error"));
        assertEquals(message, response.getBody().get("message"));
    }
}
