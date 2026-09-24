package com.company.cps.service;

import com.company.cps.domain.CpsIssue;
import com.company.cps.domain.CpsScoringRule;
import com.company.cps.domain.CpsIssueStatus;
import com.company.cps.domain.CpsWeeklyScore;
import com.company.cps.domain.CpsWeeklyScoreLine;
import com.company.cps.dto.CpsAdminPageResponse;
import com.company.cps.dto.CpsWeeklyScoreRecomputeRequest;
import com.company.cps.dto.CpsWeeklyScoreResponse;
import com.company.cps.mapper.CpsIssueMapper;
import com.company.cps.mapper.CpsWeeklyScoreMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** B4 周评分排名（PRD §24）：自然周口径 + 重算 + 排名 + 工厂命中。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CpsWeeklyScoreServiceTest {

    @Mock private CpsWeeklyScoreMapper scoreMapper;
    @Mock private CpsIssueMapper issueMapper;
    @Mock private CpsScoringRuleService scoringRuleService;

    private CpsWeeklyScoreService service;

    @BeforeEach
    void setUp() {
        service = new CpsWeeklyScoreService(scoreMapper, issueMapper, scoringRuleService);
    }

    @Test
    @DisplayName("naturalWeekStart returns Monday")
    void naturalWeekStartReturnsMonday() {
        // 2026-10-08 是周四 → 周一为 2026-10-05
        LocalDate thursday = LocalDate.of(2026, 10, 8);
        LocalDate monday = LocalDate.of(2026, 10, 5);
        assertEquals(monday, CpsWeeklyScoreService.naturalWeekStart(thursday));
        // 周一输入 → 自身
        assertEquals(monday, CpsWeeklyScoreService.naturalWeekStart(monday));
        // 周日 → 上一个周一
        LocalDate sunday = LocalDate.of(2026, 10, 11);
        assertEquals(monday, CpsWeeklyScoreService.naturalWeekStart(sunday));
    }

    @Test
    @DisplayName("naturalWeekEnd returns Sunday")
    void naturalWeekEndReturnsSunday() {
        LocalDate thursday = LocalDate.of(2026, 10, 8);
        assertEquals(LocalDate.of(2026, 10, 11), CpsWeeklyScoreService.naturalWeekEnd(thursday));
    }

    @Test
    @DisplayName("list returns paged ranking with rank assigned")
    void listReturnsPagedRanking() {
        LocalDate week = LocalDate.of(2026, 10, 5);
        CpsWeeklyScore a = buildScore(101L, "E001", "张三", 80, 3);
        CpsWeeklyScore b = buildScore(102L, "E002", "李四", 90, 5);
        when(scoreMapper.listByFilters(eq(week), eq(null), eq(0), eq(50))).thenReturn(Arrays.asList(b, a));
        when(scoreMapper.countByFilters(week, null)).thenReturn(2L);

        CpsAdminPageResponse<CpsWeeklyScoreResponse> page = service.list(week, null, 1, 50, false);

        assertEquals(2, page.getTotal());
        assertEquals(2, page.getRecords().size());
        assertEquals(Integer.valueOf(1), page.getRecords().get(0).getRank()); // 最高分排第一
        assertEquals(Integer.valueOf(2), page.getRecords().get(1).getRank());
        assertEquals(week, page.getRecords().get(0).getWeekStartDate());
    }

    @Test
    @DisplayName("recompute writes headers and lines per employee")
    void recomputeWritesHeadersAndLines() {
        when(scoringRuleService.resolveRules("F1"))
                .thenReturn(new CpsScoringRuleService.RuleSet(100, 40, 30, 20, 10));
        CpsIssue issue1 = buildIssue(1L, "E001", "张三", "F1", "A区", CpsIssueStatus.CLOSED);
        CpsIssue issue2 = buildIssue(2L, "E001", "张三", "F1", "A区", CpsIssueStatus.PENDING_RECTIFY);
        when(issueMapper.findForWeeklyScore(any(), any(), anyInt())).thenReturn(Arrays.asList(issue1, issue2));
        when(scoreMapper.findByWeekAndEmp(any(), anyString())).thenReturn(null);

        LocalDate week = LocalDate.of(2026, 10, 5);
        int count = service.recomputeWeek(week, "ADMIN", false);

        assertEquals(1, count);
        ArgumentCaptor<CpsWeeklyScore> headerCap = ArgumentCaptor.forClass(CpsWeeklyScore.class);
        verify(scoreMapper).insertHeader(headerCap.capture());
        // base=100 + content_mismatch(-40) + evidence_vague(-30) = 30
        assertEquals(Integer.valueOf(30), headerCap.getValue().getTotalScore());

        // 3 lines: base + content_mismatch + evidence_vague
        ArgumentCaptor<CpsWeeklyScoreLine> lineCap = ArgumentCaptor.forClass(CpsWeeklyScoreLine.class);
        verify(scoreMapper, org.mockito.Mockito.times(3)).insertLine(lineCap.capture());
        List<CpsWeeklyScoreLine> lines = lineCap.getAllValues();
        assertEquals(CpsScoringRule.RULE_KEY_BASE, lines.get(0).getItemId());
        assertEquals(100, lines.get(0).getScoreDelta());
    }

    @Test
    @DisplayName("recomputeWeek: existing row → update + delete-then-insert lines")
    void recomputeUpdatesExisting() {
        when(scoringRuleService.resolveRules("F2"))
                .thenReturn(new CpsScoringRuleService.RuleSet(100, 40, 30, 20, 10));
        CpsIssue issue = buildIssue(10L, "E010", "王五", "F2", "B区", CpsIssueStatus.CLOSED);
        when(issueMapper.findForWeeklyScore(any(), any(), anyInt())).thenReturn(Collections.singletonList(issue));
        CpsWeeklyScore existing = new CpsWeeklyScore();
        existing.setId(999L);
        when(scoreMapper.findByWeekAndEmp(any(), eq("E010"))).thenReturn(existing);

        int count = service.recomputeWeek(LocalDate.of(2026, 10, 5), "SYSTEM", true);
        assertEquals(1, count);
        verify(scoreMapper).deleteLinesByHeader(999L);
        verify(scoreMapper).updateHeader(any(CpsWeeklyScore.class));
        verify(scoreMapper, never()).insertHeader(any());
    }

    @Test
    @DisplayName("recompute endpoint rejects blank operator; unknown → ADMIN")
    void recomputeEndpointOperatorRules() {
        CpsWeeklyScoreRecomputeRequest req = new CpsWeeklyScoreRecomputeRequest();
        req.setOperatorEmpNo("");
        assertThrows(IllegalArgumentException.class, () -> service.recompute(req));

        CpsWeeklyScoreRecomputeRequest req2 = new CpsWeeklyScoreRecomputeRequest();
        req2.setOperatorEmpNo("ghost");
        req2.setWeekStartDate(LocalDate.of(2026, 10, 5));
        when(issueMapper.findForWeeklyScore(any(), any(), anyInt())).thenReturn(new ArrayList<>());
        Map<String, Object> result = service.recompute(req2);
        assertEquals("ADMIN", result.get("operator"));
        assertEquals(LocalDate.of(2026, 10, 5), result.get("weekStartDate"));
    }

    private static CpsWeeklyScore buildScore(Long id, String empNo, String name, int score, int count) {
        CpsWeeklyScore s = new CpsWeeklyScore();
        s.setId(id);
        s.setWeekStartDate(LocalDate.of(2026, 10, 5));
        s.setEmpNo(empNo);
        s.setEmpName(name);
        s.setTotalScore(score);
        s.setRoomCheckCount(count);
        s.setPhotoCount(0);
        s.setNaturalWeekFlag(true);
        return s;
    }

    private static CpsIssue buildIssue(Long id, String empNo, String empName, String factory,
                                       String area, CpsIssueStatus status) {
        CpsIssue i = new CpsIssue();
        i.setId(id);
        i.setCreatorEmpNo(empNo);
        i.setCreatorEmpName(empName);
        i.setFactory(factory);
        i.setArea(area);
        i.setStatus(status);
        return i;
    }
}
