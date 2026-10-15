package com.company.cps.controller;

import com.company.cps.dto.CpsRoomCheckRankingResponse;
import com.company.cps.dto.CpsRoomCheckRecordResponse;
import com.company.cps.dto.CpsRoomCheckRejudgeResponse;
import com.company.cps.dto.CpsRoomCheckRoomWeekDetailResponse;
import com.company.cps.service.CpsRoomCheckRankingService;
import com.company.cps.service.CpsRoomCheckService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** B4 评分排名 admin 端点（PRD §24.3 + D-08）。 */
@RestController
@RequestMapping("/api/cps/admin")
public class CpsAdminRoomCheckController {

    private final CpsRoomCheckRankingService rankingService;
    private final CpsRoomCheckService roomCheckService;

    public CpsAdminRoomCheckController(CpsRoomCheckRankingService rankingService,
                                       CpsRoomCheckService roomCheckService) {
        this.rankingService = rankingService;
        this.roomCheckService = roomCheckService;
    }

    /** 自然周排名：weekStart 空默认上一完整自然周；任意日期归一所在周周一（东八区）。 */
    @GetMapping("/room-check-rankings")
    public CpsRoomCheckRankingResponse ranking(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {
        return rankingService.weeklyRanking(weekStart);
    }

    /** 房间周评分明细：逐次检查时间/单次得分/不合格扣分明细/周期总分与排名。 */
    @GetMapping("/room-check-rankings/rooms/{roomId}")
    public CpsRoomCheckRoomWeekDetailResponse roomDetail(
            @PathVariable Long roomId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {
        return rankingService.roomWeekDetail(roomId, weekStart);
    }

    /** 单次点检记录明细（admin 追溯，不做"仅本人"校验）。 */
    @GetMapping("/room-check-records/{recordId}")
    public CpsRoomCheckRecordResponse recordDetail(@PathVariable Long recordId) {
        return roomCheckService.getRecordForAdmin(recordId);
    }

    /**
     * 波次7 J线（C-04 清单⑤）：PENDING 降级单补判定重跑——重调 Python C-04（新幂等键）→ 回写分数/状态。
     * 仅限 record_status=JUDGED 且 judge_status=PENDING 的单；业务结果（重拍/仍降级）不报错，以响应回告。
     */
    @PostMapping("/room-check-records/{recordId}/rejudge")
    public CpsRoomCheckRejudgeResponse rejudge(@PathVariable Long recordId) {
        return roomCheckService.rejudge(recordId);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> badRequest(IllegalArgumentException error) {
        return ResponseEntity.badRequest().body(error.getMessage());
    }
}
