package com.company.cps.service;

import com.company.cps.domain.CpsInitialReviewTriggerConfig;
import com.company.cps.domain.CpsInitialReviewTask;
import com.company.cps.domain.CpsInitialReviewTaskStatus;
import com.company.cps.dto.CpsAdminInitialReviewConfigRequest;
import com.company.cps.dto.CpsInitialReviewAdminTaskView;
import com.company.cps.mapper.CpsInitialReviewConfigMapper;
import com.company.cps.mapper.CpsInitialReviewEventMapper;
import com.company.cps.mapper.CpsInitialReviewItemMapper;
import com.company.cps.mapper.CpsInitialReviewResultMapper;
import com.company.cps.mapper.CpsInitialReviewTaskMapper;
import com.company.cps.mapper.CpsReviewAdjudicationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A4 admin 触发管理（PRD §28.4/§29.3，D-21）：配置校验边界、全量替换、
 * 分页查询参数透传、任务详情组装、重触发委托。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CpsInitialReviewAdminServiceTest {

    @Mock private CpsInitialReviewConfigMapper configMapper;
    @Mock private CpsInitialReviewTaskMapper taskMapper;
    @Mock private CpsInitialReviewResultMapper resultMapper;
    @Mock private CpsInitialReviewItemMapper itemMapper;
    @Mock private CpsInitialReviewEventMapper eventMapper;
    @Mock private CpsReviewAdjudicationMapper adjudicationMapper;
    @Mock private CpsInitialReviewService initialReviewService;

    private CpsInitialReviewAdminService service;

    @BeforeEach
    void setUp() {
        service = new CpsInitialReviewAdminService(configMapper, taskMapper, resultMapper, itemMapper,
                eventMapper, adjudicationMapper, initialReviewService);
    }

    // ---------- 触发配置 ----------

    @Test
    void getConfigWithoutRowFallsBackToDefaults() {
        when(configMapper.findGlobal()).thenReturn(null);

        Map<String, Object> config = service.getConfig();

        assertEquals(Boolean.TRUE, config.get("auto_trigger_enabled"));
        assertEquals(1, config.get("max_retry_attempts"), "D-21 缺省技术重试 1 次");
        assertEquals(3000, config.get("retry_backoff_ms"));
        assertNull(config.get("timeout_seconds"), "NULL=回退应用配置");
        assertEquals("application-config", config.get("timeout_source"));
    }

    @Test
    void updateConfigValidatesBoundsAndWritesGlobal() {
        CpsAdminInitialReviewConfigRequest request = new CpsAdminInitialReviewConfigRequest();
        request.setAutoTriggerEnabled(false);
        request.setMaxRetryAttempts(2);
        request.setRetryBackoffMs(5000);
        request.setTimeoutSeconds(300);
        request.setOperatorEmpNo("E1");
        // updateConfig 就地改写 fetch 到的配置对象：findGlobal 回可变对象，更新后 getConfig 重读同对象
        CpsInitialReviewTriggerConfig existing = new CpsInitialReviewTriggerConfig();
        when(configMapper.findGlobal()).thenReturn(existing);
        when(configMapper.updateGlobal(any(CpsInitialReviewTriggerConfig.class))).thenReturn(1);

        Map<String, Object> config = service.updateConfig(request);

        ArgumentCaptor<CpsInitialReviewTriggerConfig> captor =
                ArgumentCaptor.forClass(CpsInitialReviewTriggerConfig.class);
        verify(configMapper).updateGlobal(captor.capture());
        assertEquals(Boolean.FALSE, captor.getValue().getAutoTriggerEnabled());
        assertEquals(Integer.valueOf(300), captor.getValue().getTimeoutSeconds());
        assertEquals("E1", captor.getValue().getUpdatedBy());
        assertEquals(Boolean.FALSE, config.get("auto_trigger_enabled"));
        assertEquals(2, config.get("max_retry_attempts"));
        assertEquals("database", config.get("timeout_source"));
    }

    @Test
    void updateConfigRequiresCoreFieldsAndValidatesRanges() {
        CpsAdminInitialReviewConfigRequest missing = new CpsAdminInitialReviewConfigRequest();
        missing.setMaxRetryAttempts(1);
        missing.setRetryBackoffMs(0);
        assertThrows(IllegalArgumentException.class, () -> service.updateConfig(missing),
                "autoTriggerEnabled 必填");

        CpsAdminInitialReviewConfigRequest badRetry = fullRequest();
        badRetry.setMaxRetryAttempts(6);
        assertThrows(IllegalArgumentException.class, () -> service.updateConfig(badRetry), "maxRetry 0..5");

        CpsAdminInitialReviewConfigRequest badBackoff = fullRequest();
        badBackoff.setRetryBackoffMs(60_001);
        assertThrows(IllegalArgumentException.class, () -> service.updateConfig(badBackoff), "backoff 0..60000");

        CpsAdminInitialReviewConfigRequest badTimeout = fullRequest();
        badTimeout.setTimeoutSeconds(10);
        assertThrows(IllegalArgumentException.class, () -> service.updateConfig(badTimeout), "timeout 30..86400");

        verify(configMapper, never()).updateGlobal(any());
    }

    @Test
    void updateConfigHealsMissingGlobalRow() {
        when(configMapper.updateGlobal(any(CpsInitialReviewTriggerConfig.class))).thenReturn(0);

        service.updateConfig(fullRequest());

        verify(configMapper).insertGlobal(any(CpsInitialReviewTriggerConfig.class));
    }

    private CpsAdminInitialReviewConfigRequest fullRequest() {
        CpsAdminInitialReviewConfigRequest request = new CpsAdminInitialReviewConfigRequest();
        request.setAutoTriggerEnabled(true);
        request.setMaxRetryAttempts(1);
        request.setRetryBackoffMs(3000);
        return request;
    }

    // ---------- 触发记录 ----------

    @Test
    void listTasksClampsPagingAndPassesFilters() {
        List<CpsInitialReviewAdminTaskView> rows = Collections.singletonList(new CpsInitialReviewAdminTaskView());
        when(taskMapper.countAdminPage("FAILED", 900L)).thenReturn(1L);
        when(taskMapper.findAdminPage(eq("FAILED"), eq(900L), eq(20), eq(0))).thenReturn(rows);

        Map<String, Object> page = service.listTasks("FAILED", 900L, 0, 0);

        assertEquals(1L, page.get("total"));
        assertEquals(rows, page.get("tasks"));
        verify(taskMapper).findAdminPage("FAILED", 900L, 20, 0);
    }

    @Test
    void taskDetailAssemblesTaskResultItemsEventsAdjudication() {
        CpsInitialReviewTask task = new CpsInitialReviewTask();
        task.setId(501L);
        task.setIssueId(900L);
        task.setVersionNo(1);
        task.setStatus(CpsInitialReviewTaskStatus.TIMEOUT_OPEN);
        when(taskMapper.findById(501L)).thenReturn(task);

        Map<String, Object> detail = service.taskDetail(501L);

        assertEquals(task, detail.get("task"));
        assertTrue(detail.containsKey("result"));
        assertTrue(detail.containsKey("items"));
        assertTrue(detail.containsKey("events"));
        verify(adjudicationMapper).findByIssueAndVersion(900L, 1);
    }

    @Test
    void taskDetailRejectsUnknownTask() {
        when(taskMapper.findById(404L)).thenReturn(null);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.taskDetail(404L));
        assertTrue(ex.getMessage().contains("not found"));
    }

    @Test
    void retriggerDelegatesToInitialReviewService() {
        when(initialReviewService.retrigger(501L, "E1", "retry")).thenReturn(Collections.singletonMap("task_id", 501L));

        Map<String, Object> response = service.retrigger(501L, "E1", "retry");

        assertEquals(501L, response.get("task_id"));
        verify(initialReviewService).retrigger(501L, "E1", "retry");
    }
}
