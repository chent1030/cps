package com.company.cps.controller;

import com.company.cps.domain.CpsVisionCheckRecord;
import com.company.cps.dto.CpsPageResponse;
import com.company.cps.dto.CpsVisionCheckListFilter;
import com.company.cps.dto.CpsVisionCheckOverrideRequest;
import com.company.cps.dto.CpsVisionCheckSubmitRequest;
import com.company.cps.dto.CpsVisionCheckSubmitResult;
import com.company.cps.service.CpsVisionCheckService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * B6 视觉点检后端端点（与 CpsRoomCheckController B3 mobile 端点共享前缀 /api/cps/room-checks，
 * 但 HTTP method+path 互不冲突）：
 * - POST /api/cps/room-checks（无 sub-path）= 视觉点检提交；
 * - GET /api/cps/room-checks（无 sub-path）= 视觉点检分页列表（admin 复盘）；
 * - POST /api/cps/room-checks/{id}/override = 人工改判（仅 AI_JUDGING/AI_PASS/AI_FAIL）；
 * - POST /api/cps/room-checks/{id}/rejudge = 重判（仅 PENDING/TIMEOUT）。
 *
 * Spring 路由按 method+path 区分；B3 controller 已有 /tasks /start /records/{id} /photo /submit 等，
 * 不与本端点冲突。
 */
@RestController
@RequestMapping("/api/cps/room-checks")
public class CpsVisionCheckController {

    private final CpsVisionCheckService visionCheckService;

    public CpsVisionCheckController(CpsVisionCheckService visionCheckService) {
        this.visionCheckService = visionCheckService;
    }

    /** B6 视觉点检提交：201 + record + fingerprint。 */
    @PostMapping
    public ResponseEntity<CpsVisionCheckSubmitResult> submit(@RequestBody CpsVisionCheckSubmitRequest request) {
        CpsVisionCheckSubmitResult result = visionCheckService.submit(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    /** B6 视觉点检分页列表（admin 端复盘）：roomId/checkItemId/status/startTime/endTime/page/size。 */
    @GetMapping
    public CpsPageResponse<CpsVisionCheckRecord> list(
            @RequestParam(required = false) Long roomId,
            @RequestParam(required = false) Long checkItemId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        CpsVisionCheckListFilter filter = new CpsVisionCheckListFilter();
        filter.setRoomId(roomId);
        filter.setCheckItemId(checkItemId);
        filter.setStatus(status);
        filter.setStartTime(startTime);
        filter.setEndTime(endTime);
        filter.setPage(page);
        filter.setSize(size);
        return visionCheckService.list(filter);
    }

    /** B6 人工改判：仅 AI_JUDGING/AI_PASS/AI_FAIL 可调；400/409 by service IllegalArgument/IllegalState。 */
    @PostMapping("/{id}/override")
    public CpsVisionCheckRecord override(@PathVariable Long id,
                                          @RequestBody CpsVisionCheckOverrideRequest request) {
        if (request != null && (request.getOperatorEmpNo() == null || request.getOperatorEmpNo().isBlank())) {
            request.setOperatorEmpNo("admin");
        }
        return visionCheckService.humanOverride(id, request);
    }

    /** B6 重判：仅 PENDING/TIMEOUT 可调；写 REJUDGED 流水 + 重置 AI_JUDGING 重新触发 judge。 */
    @PostMapping("/{id}/rejudge")
    public CpsVisionCheckRecord rejudge(@PathVariable Long id,
                                         @RequestParam(defaultValue = "admin") String operatorEmpNo) {
        return visionCheckService.rejudge(id, operatorEmpNo);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> badRequest(IllegalArgumentException error) {
        return ResponseEntity.badRequest().body(error.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> conflict(IllegalStateException error) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error.getMessage());
    }
}