package com.company.cps.service;

import com.company.cps.mapper.CpsWeeklyReportDataMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** B7 周报数据源：窗口语义 §21.1.6（东八区，左闭右开）；视图查询透传。 */
@ExtendWith(MockitoExtension.class)
class CpsWeeklyReportDataServiceTest {

    @Mock private CpsWeeklyReportDataMapper dataMapper;
    private CpsWeeklyReportDataService service;

    @BeforeEach
    void setUp() {
        service = new CpsWeeklyReportDataService(dataMapper);
    }

    @Test
    void currentWeekWindowIsLeftClosedRightOpen() {
        CpsWeeklyReportDataService.Window window = service.currentWeekWindow();
        assertTrue(window.getPeriodStart().isBefore(window.getPeriodEnd()));
        // 长度恰好 7 天
        long days = java.time.Duration.between(window.getPeriodStart(), window.getPeriodEnd()).toDays();
        assertEquals(7, days);
    }

    @Test
    void currentWeekWindowMondayAligned() {
        CpsWeeklyReportDataService.Window window = service.currentWeekWindow();
        LocalDate startDate = window.getPeriodStart().toLocalDate();
        LocalDate endDate = window.getPeriodEnd().toLocalDate();
        assertEquals(java.time.DayOfWeek.MONDAY, startDate.getDayOfWeek());
        assertEquals(java.time.DayOfWeek.MONDAY, endDate.getDayOfWeek());
        assertEquals(startDate.plusWeeks(1), endDate);
    }

    @Test
    void windowConstructorRejectsInvertedRange() {
        LocalDateTime start = LocalDateTime.of(2026, 9, 28, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 9, 21, 0, 0);
        assertThrows(IllegalArgumentException.class, () -> new CpsWeeklyReportDataService.Window(start, end));
    }

    @Test
    void windowConstructorRejectsNulls() {
        assertThrows(IllegalArgumentException.class, () -> new CpsWeeklyReportDataService.Window(null, LocalDateTime.now()));
    }

    @Test
    void issueSummaryDelegatesToMapperWithWindow() {
        Map<String, Object> row = new HashMap<>();
        row.put("issue_count", 12);
        when(dataMapper.findIssueSummary(eq("F1"), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.singletonList(row));
        List<Map<String, Object>> result = service.getIssueSummary("F1",
                LocalDateTime.of(2026, 9, 21, 0, 0), LocalDateTime.of(2026, 9, 28, 0, 0));
        assertEquals(1, result.size());
        verify(dataMapper).findIssueSummary(eq("F1"), any(LocalDateTime.class), any(LocalDateTime.class));
    }

    @Test
    void mapperNullReturnsEmptyList() {
        when(dataMapper.findIssueSummary(any(), any(), any())).thenReturn(null);
        when(dataMapper.findRectificationSummary(any(), any())).thenReturn(null);
        when(dataMapper.findInitialReviewSummary(any(), any())).thenReturn(null);
        when(dataMapper.findCheckItemSnapshot()).thenReturn(null);
        when(dataMapper.findInventoryLowStock()).thenReturn(null);
        assertEquals(0, service.getIssueSummary(null, LocalDateTime.now().minusDays(7), LocalDateTime.now()).size());
        assertEquals(0, service.getRectificationSummary(LocalDateTime.now().minusDays(7), LocalDateTime.now()).size());
        assertEquals(0, service.getInitialReviewSummary(LocalDateTime.now().minusDays(7), LocalDateTime.now()).size());
        assertEquals(0, service.getCheckItemSnapshot().size());
        assertEquals(0, service.getInventoryLowStock().size());
    }
}
