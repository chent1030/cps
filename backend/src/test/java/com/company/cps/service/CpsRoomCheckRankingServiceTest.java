package com.company.cps.service;

import com.company.cps.domain.CpsRoomCheckRecord;
import com.company.cps.domain.CpsRoomCheckRecordItem;
import com.company.cps.dto.CpsRoomCheckRankingResponse;
import com.company.cps.dto.CpsRoomCheckRoomWeekDetailResponse;
import com.company.cps.mapper.CpsRoomCheckRecordItemMapper;
import com.company.cps.mapper.CpsRoomCheckRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** B4 排名：自然周窗口/顺序号（同分按完成时间）/扣分明细聚合。 */
class CpsRoomCheckRankingServiceTest {

    private CpsRoomCheckRecordMapper recordMapper;
    private CpsRoomCheckRecordItemMapper itemMapper;
    private CpsRoomCheckRankingService service;

    @BeforeEach
    void setUp() {
        recordMapper = mock(CpsRoomCheckRecordMapper.class);
        itemMapper = mock(CpsRoomCheckRecordItemMapper.class);
        service = new CpsRoomCheckRankingService(recordMapper, itemMapper);
    }

    private Map<String, Object> row(long roomId, String code, String name, int count, long total) {
        Map<String, Object> row = new HashMap<>();
        row.put("roomId", roomId);
        row.put("roomCode", code);
        row.put("roomName", name);
        row.put("checkCount", count);
        row.put("totalScore", total);
        row.put("firstSubmittedAt", LocalDateTime.of(2026, 9, 22, 9, 0));
        return row;
    }

    private CpsRoomCheckRecord record(long id, int score, LocalDateTime submitted) {
        CpsRoomCheckRecord record = new CpsRoomCheckRecord();
        record.setId(id);
        record.setRoomId(5L);
        record.setRoomCode("R-01");
        record.setRoomName("配电间");
        record.setScore(score);
        record.setSubmittedAt(submitted);
        record.setCheckEmpNo("E001");
        return record;
    }

    private CpsRoomCheckRecordItem item(long id, String finalResult) {
        CpsRoomCheckRecordItem item = new CpsRoomCheckRecordItem();
        item.setId(id);
        item.setCheckItemId(id);
        item.setItemCode("C" + id);
        item.setContent("无积水");
        item.setDeductScore(10);
        item.setJudgeReason("reason-" + id);
        item.setFinalResult(finalResult);
        return item;
    }

    @Test
    void emptyWeekStartDefaultsToPreviousFullNaturalWeek() {
        LocalDate weekStart = CpsRoomCheckRankingService.normalizeWeekStart(null);
        assertEquals(DayOfWeek.MONDAY, weekStart.getDayOfWeek());
        LocalDate thisMonday = LocalDate.now(CpsRoomCheckRankingService.ZONE)
                .with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        assertEquals(thisMonday.minusWeeks(1), weekStart);
    }

    @Test
    void anyDateNormalizesToItsMonday() {
        assertEquals(LocalDate.of(2026, 9, 21),
                CpsRoomCheckRankingService.normalizeWeekStart(LocalDate.of(2026, 9, 26))); // 周六→周一
    }

    @Test
    void weeklyRankingQueriesMondayWindowAndKeepsAggregateOrder() {
        AtomicReference<LocalDateTime> fromRef = new AtomicReference<>();
        when(recordMapper.aggregateWeeklyScores(any(), any())).thenAnswer(inv -> {
            fromRef.set(inv.getArgument(0));
            return Arrays.asList(
                    row(5, "R-01", "配电间", 3, 280),
                    row(6, "R-02", "水泵房", 2, 280), // 同分，首提交更晚 ⇒ 第2
                    row(7, "R-03", "电梯机房", 1, 250));
        });

        CpsRoomCheckRankingResponse response = service.weeklyRanking(LocalDate.of(2026, 9, 23));

        assertEquals(LocalDateTime.of(2026, 9, 21, 0, 0), fromRef.get());
        assertEquals("2026-09-21", response.getWeekStart());
        assertEquals("2026-09-28", response.getWeekEnd());
        assertEquals(3, response.getRows().size());
        assertEquals(1, response.getRows().get(0).getRank());
        assertEquals("R-01", response.getRows().get(0).getRoomCode());
        assertEquals(280, response.getRows().get(0).getTotalScore());
        assertEquals(2, response.getRows().get(1).getRank()); // D-08：同分不并列，顺序号
        assertEquals(3, response.getRows().get(2).getRank());
    }

    @Test
    void roomWeekDetailAggregatesFailDeductsOnly() {
        when(recordMapper.findJudgedByRoomAndSubmittedBetween(eq(5L), any(), any())).thenReturn(Arrays.asList(
                record(91, 90, LocalDateTime.of(2026, 9, 22, 9, 0)),
                record(92, 100, LocalDateTime.of(2026, 9, 24, 15, 0))));
        when(itemMapper.findByRecordId(91L)).thenReturn(Arrays.asList(
                item(1, "PASS"), item(2, "FAIL"), item(3, "FAIL")));
        when(itemMapper.findByRecordId(92L)).thenReturn(Arrays.asList(item(4, "PASS")));
        when(recordMapper.aggregateWeeklyScores(any(), any())).thenReturn(Arrays.asList(
                row(6, "R-02", "水泵房", 2, 200), row(5, "R-01", "配电间", 2, 190)));

        CpsRoomCheckRoomWeekDetailResponse detail = service.roomWeekDetail(5L, LocalDate.of(2026, 9, 22));

        assertEquals(2, detail.getCheckCount());
        assertEquals(190, detail.getTotalScore());
        assertEquals(2, detail.getRank()); // 聚合位次
        assertEquals(2, detail.getRecords().get(0).getDeducts().size()); // 仅 FAIL 进扣分明细
        assertEquals(Integer.valueOf(10), detail.getRecords().get(0).getDeducts().get(0).getDeductScore());
        assertTrue(detail.getRecords().get(1).getDeducts().isEmpty());
        assertEquals("2026-09-22T09:00", detail.getRecords().get(0).getSubmittedAt());
    }
}
