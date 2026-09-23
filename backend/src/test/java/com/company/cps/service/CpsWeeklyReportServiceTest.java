package com.company.cps.service;

import com.company.cps.domain.CpsWeeklyReportRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** C5/C8 周报代理：参数透传 + 响应归一化。 */
@ExtendWith(MockitoExtension.class)
class CpsWeeklyReportServiceTest {

    @Mock private CpsAgentFrameworkClient agentClient;
    private CpsWeeklyReportService service;

    @BeforeEach
    void setUp() {
        service = new CpsWeeklyReportService(agentClient);
    }

    @Test
    void listRunsPassesFiltersAndNormalizesResponse() {
        // 波次7 J线（C7 冻结 schema）：Python 行字段 report_type/window_start/window_end/archive_object_key/archive_bytes
        Map<String, Object> row = new HashMap<>();
        row.put("run_id", "uuid-1");
        row.put("run_no", "WR-2026-W39-001");
        row.put("report_type", "RECTIFY");
        row.put("status", "ARCHIVED");
        row.put("archive_bytes", 12345L);
        row.put("archive_object_key", "weekly-reports/2026-W39/uuid-1.pdf");
        row.put("window_start", "2026-09-21T00:00:00+00:00");
        row.put("window_end", "2026-09-28T00:00:00+00:00");
        row.put("push_status", "UNCONFIGURED");
        row.put("error_code", null);
        when(agentClient.listWeeklyReportRuns(eq("RECTIFY"), eq("ARCHIVED"), anyString(), anyString()))
                .thenReturn(Collections.singletonList(row));

        LocalDateTime start = LocalDateTime.of(2026, 9, 21, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 9, 28, 0, 0);
        List<CpsWeeklyReportRun> runs = service.listRuns("RECTIFY", "ARCHIVED", start, end);

        assertEquals(1, runs.size());
        CpsWeeklyReportRun r = runs.get(0);
        assertEquals("uuid-1", r.getRunId());
        assertEquals("RECTIFY", r.getInspectionType());
        assertEquals("ARCHIVED", r.getStatus());
        assertEquals(Long.valueOf(12345L), r.getFileSize());
        assertEquals("UNCONFIGURED", r.getPushStatus());
        assertEquals("weekly-reports/2026-W39/uuid-1.pdf", r.getFileKey());
        assertEquals("uuid-1.pdf", r.getFileName());
        assertNotNull(r.getPeriodStart());
        verify(agentClient, times(1)).listWeeklyReportRuns(eq("RECTIFY"), eq("ARCHIVED"), anyString(), anyString());
    }

    @Test
    void listRunsFiltersByPeriodWindowJavaSide() {
        Map<String, Object> inWindow = new HashMap<>();
        inWindow.put("run_id", "in-1");
        inWindow.put("window_start", "2026-09-22T00:00:00");
        Map<String, Object> outWindow = new HashMap<>();
        outWindow.put("run_id", "out-1");
        outWindow.put("window_start", "2026-09-15T00:00:00");
        when(agentClient.listWeeklyReportRuns(any(), any(), anyString(), anyString()))
                .thenReturn(Arrays.asList(inWindow, outWindow));

        List<CpsWeeklyReportRun> runs = service.listRuns(null, null,
                LocalDateTime.of(2026, 9, 21, 0, 0), LocalDateTime.of(2026, 9, 28, 0, 0));
        assertEquals(1, runs.size());
        assertEquals("in-1", runs.get(0).getRunId());
    }

    @Test
    void listRunsRejectsInvertedWindow() {
        LocalDateTime start = LocalDateTime.of(2026, 9, 28, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 9, 21, 0, 0);
        assertThrows(IllegalArgumentException.class, () -> service.listRuns(null, null, start, end));
    }

    @Test
    void listRunsAcceptsNullWindow() {
        when(agentClient.listWeeklyReportRuns(eq(null), eq(null), eq(null), eq(null)))
                .thenReturn(Collections.emptyList());
        assertEquals(0, service.listRuns(null, null, null, null).size());
    }

    @Test
    void listRunsReturnsEmptyWhenClientDisabled() {
        when(agentClient.listWeeklyReportRuns(any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());
        assertEquals(0, service.listRuns(null, null, null, null).size());
    }

    @Test
    void downloadFileRequiresRunId() {
        assertThrows(IllegalArgumentException.class, () -> service.downloadFile(""));
        assertThrows(IllegalArgumentException.class, () -> service.downloadFile(null));
    }

    @Test
    void downloadFileDelegatesToClient() {
        CpsAgentFrameworkClient.WeeklyReportFile file =
                new CpsAgentFrameworkClient.WeeklyReportFile(new byte[]{1, 2, 3}, "application/pdf",
                        "attachment; filename=\"weekly.pdf\"");
        when(agentClient.downloadWeeklyReportFile("uuid-1")).thenReturn(file);
        CpsAgentFrameworkClient.WeeklyReportFile result = service.downloadFile("uuid-1");
        assertEquals(3, result.getContent().length);
        assertEquals("application/pdf", result.getContentType());
    }
}
