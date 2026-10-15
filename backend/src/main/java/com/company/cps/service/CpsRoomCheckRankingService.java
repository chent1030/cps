package com.company.cps.service;

import com.company.cps.domain.CpsRoomCheckRecord;
import com.company.cps.domain.CpsRoomCheckRecordItem;
import com.company.cps.dto.CpsRoomCheckRankingResponse;
import com.company.cps.dto.CpsRoomCheckRoomWeekDetailResponse;
import com.company.cps.mapper.CpsRoomCheckRecordItemMapper;
import com.company.cps.mapper.CpsRoomCheckRecordMapper;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * B4 评分排名（PRD §24.3 + 决策 D-08）。
 *
 * 周期：东八区自然周（周一 00:00 起），默认上一完整自然周（与 PRD §21.1 周报口径一致）；
 * 归属：记录按提交时间（submitted_at）划入所在自然周，不存在跨周拆分（D-08）；
 * 聚合：周期总分=已判定成功（judge_status=SUCCESS）记录的 score 之和（降级 PENDING 单不计入，PRD：未完成/证据无效不得计入总分）；
 * 排名：总分降序；同分按完成时间（周期内最早提交时间）升序（D-08：同分不特别处理，按完成时间先后排）。
 */
@Service
public class CpsRoomCheckRankingService {

    static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final CpsRoomCheckRecordMapper recordMapper;
    private final CpsRoomCheckRecordItemMapper itemMapper;

    public CpsRoomCheckRankingService(CpsRoomCheckRecordMapper recordMapper,
                                      CpsRoomCheckRecordItemMapper itemMapper) {
        this.recordMapper = recordMapper;
        this.itemMapper = itemMapper;
    }

    /** 周排名。weekStart 为空=上一完整自然周；任意日期归一到所在周周一。 */
    public CpsRoomCheckRankingResponse weeklyRanking(LocalDate weekStartParam) {
        LocalDate weekStart = normalizeWeekStart(weekStartParam);
        LocalDateTime from = weekStart.atStartOfDay();
        LocalDateTime to = weekStart.plusDays(7).atStartOfDay();
        List<Map<String, Object>> rows = recordMapper.aggregateWeeklyScores(from, to);

        CpsRoomCheckRankingResponse response = new CpsRoomCheckRankingResponse();
        response.setWeekStart(weekStart.toString());
        response.setWeekEnd(to.toLocalDate().toString());
        List<CpsRoomCheckRankingResponse.Row> out = new ArrayList<>();
        int rank = 1;
        for (Map<String, Object> row : rows) {
            CpsRoomCheckRankingResponse.Row item = new CpsRoomCheckRankingResponse.Row();
            item.setRank(rank++);
            item.setRoomId(((Number) row.get("roomId")).longValue());
            item.setRoomCode(String.valueOf(row.get("roomCode")));
            item.setRoomName(String.valueOf(row.get("roomName")));
            item.setCheckCount(row.get("checkCount") == null ? 0 : ((Number) row.get("checkCount")).intValue());
            item.setTotalScore(row.get("totalScore") == null ? 0 : ((Number) row.get("totalScore")).longValue());
            Object first = row.get("firstSubmittedAt");
            item.setFirstSubmittedAt(first == null ? null : first.toString());
            out.add(item);
        }
        response.setRows(out);
        return response;
    }

    /** 房间周评分明细：逐次检查时间+单次得分+不合格扣分明细（PRD 24.3 报告必展示项）。 */
    public CpsRoomCheckRoomWeekDetailResponse roomWeekDetail(Long roomId, LocalDate weekStartParam) {
        LocalDate weekStart = normalizeWeekStart(weekStartParam);
        LocalDateTime from = weekStart.atStartOfDay();
        LocalDateTime to = weekStart.plusDays(7).atStartOfDay();
        List<CpsRoomCheckRecord> records = recordMapper.findJudgedByRoomAndSubmittedBetween(roomId, from, to);

        CpsRoomCheckRoomWeekDetailResponse response = new CpsRoomCheckRoomWeekDetailResponse();
        response.setWeekStart(weekStart.toString());
        response.setWeekEnd(to.toLocalDate().toString());
        response.setRoomId(roomId);
        long total = 0;
        List<CpsRoomCheckRoomWeekDetailResponse.Record> out = new ArrayList<>();
        for (CpsRoomCheckRecord record : records) {
            CpsRoomCheckRoomWeekDetailResponse.Record view = new CpsRoomCheckRoomWeekDetailResponse.Record();
            view.setRecordId(record.getId());
            view.setSubmittedAt(record.getSubmittedAt() == null ? null : record.getSubmittedAt().toString());
            view.setScore(record.getScore());
            view.setCheckEmpNo(record.getCheckEmpNo());
            List<CpsRoomCheckRoomWeekDetailResponse.Deduct> deducts = new ArrayList<>();
            if (record.getRoomCode() != null) {
                response.setRoomCode(record.getRoomCode());
                response.setRoomName(record.getRoomName());
            }
            for (CpsRoomCheckRecordItem item : itemMapper.findByRecordId(record.getId())) {
                if (!"FAIL".equals(item.getFinalResult())) {
                    continue; // 合格项/不扣分项不进扣分明细（PRD 24.3）
                }
                CpsRoomCheckRoomWeekDetailResponse.Deduct deduct = new CpsRoomCheckRoomWeekDetailResponse.Deduct();
                deduct.setItemId(item.getCheckItemId());
                deduct.setItemCode(item.getItemCode());
                deduct.setContent(item.getContent());
                deduct.setDeductScore(item.getDeductScore());
                deduct.setJudgeReason(item.getJudgeReason());
                deducts.add(deduct);
            }
            view.setDeducts(deducts);
            out.add(view);
            total += record.getScore() == null ? 0 : record.getScore();
        }
        response.setRecords(out);
        response.setCheckCount(out.size());
        response.setTotalScore(total);
        response.setRank(rankOfRoom(roomId, from, to));
        return response;
    }

    /** 该房间在聚合结果中的位次（未上榜=0）。 */
    private int rankOfRoom(Long roomId, LocalDateTime from, LocalDateTime to) {
        List<Map<String, Object>> rows = recordMapper.aggregateWeeklyScores(from, to);
        int rank = 1;
        for (Map<String, Object> row : rows) {
            if (((Number) row.get("roomId")).longValue() == roomId) {
                return rank;
            }
            rank++;
        }
        return 0;
    }

    /** 任意日期归一到所在自然周周一；空=上一完整自然周的周一（东八区）。 */
    static LocalDate normalizeWeekStart(LocalDate weekStartParam) {
        if (weekStartParam == null) {
            LocalDate today = LocalDate.now(ZONE);
            return today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1);
        }
        return weekStartParam.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
}
