package com.company.cps.controller;

import com.company.cps.dto.CpsAdminPageResponse;
import com.company.cps.dto.CpsWeeklyScoreRecomputeRequest;
import com.company.cps.dto.CpsWeeklyScoreResponse;
import com.company.cps.service.CpsWeeklyScoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/** B4 周评分 controller smoke。 */
@ExtendWith(MockitoExtension.class)
class CpsWeeklyScoreControllerTest {

    @Mock private CpsWeeklyScoreService service;
    private CpsWeeklyScoreController controller;

    @BeforeEach
    void setUp() {
        controller = new CpsWeeklyScoreController(service);
    }

    @Test
    @DisplayName("GET list delegates to service with normalized params")
    void listDelegates() {
        CpsWeeklyScoreResponse item = new CpsWeeklyScoreResponse();
        item.setRank(1);
        when(service.list(eq(LocalDate.of(2026, 10, 5)), eq(7L), eq(2), eq(100), eq(true)))
                .thenReturn(new CpsAdminPageResponse<>(Collections.singletonList(item), 1L, 2, 100));
        CpsAdminPageResponse<CpsWeeklyScoreResponse> page =
                controller.list(LocalDate.of(2026, 10, 5), 7L, 2, 100, true);
        assertEquals(1L, page.getTotal());
        assertEquals(Integer.valueOf(1), page.getRecords().get(0).getRank());
    }

    @Test
    @DisplayName("POST recompute passes request body through")
    void recomputeDelegates() {
        CpsWeeklyScoreRecomputeRequest req = new CpsWeeklyScoreRecomputeRequest();
        req.setOperatorEmpNo("ADMIN");
        req.setWeekStartDate(LocalDate.of(2026, 10, 5));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("weekStartDate", LocalDate.of(2026, 10, 5));
        body.put("recomputedCount", 3);
        when(service.recompute(any())).thenReturn(body);
        Map<String, Object> result = controller.recompute(req);
        assertEquals(3, result.get("recomputedCount"));
    }
}
