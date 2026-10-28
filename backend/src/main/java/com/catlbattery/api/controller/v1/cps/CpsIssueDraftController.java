package com.catlbattery.api.controller.v1.cps;

import com.catlbattery.domain.entity.cps.dto.CpsIssueDraftDto;
import com.catlbattery.domain.entity.cps.dto.CpsIssueDraftSaveRequest;
import com.catlbattery.app.service.cps.CpsIssueDraftService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 巡检问题服务端草稿 API（Phase B2）。
 * 挂在 {@code /api/cps/issue-drafts}，供 Android 端「暂存 / 继续填写」使用。
 */
@RestController
@RequestMapping("/api/cps/issue-drafts")
public class CpsIssueDraftController {

    private final CpsIssueDraftService draftService;

    public CpsIssueDraftController(CpsIssueDraftService draftService) {
        this.draftService = draftService;
    }

    /** 暂存（新建或覆盖更新）。body.id 空=新建，非空=更新。 */
    @PostMapping
    public CpsIssueDraftDto save(@RequestBody CpsIssueDraftSaveRequest request) {
        return draftService.save(request);
    }

    /** 我的草稿列表（仅 ACTIVE，按最近暂存倒序）。 */
    @GetMapping
    public List<CpsIssueDraftDto> listMine(@RequestParam String empNo) {
        return draftService.listMyDrafts(resolveCurrentEmpNo(empNo));
    }

    @GetMapping("/{id}")
    public CpsIssueDraftDto get(@PathVariable Long id, @RequestParam String empNo) {
        return draftService.get(id, resolveCurrentEmpNo(empNo));
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id, @RequestParam String empNo) {
        draftService.delete(id, resolveCurrentEmpNo(empNo));
        Map<String, Object> resp = new HashMap<>();
        resp.put("ok", true);
        return resp;
    }

    private String resolveCurrentEmpNo(String empNo) {
        if (empNo != null && !empNo.trim().isEmpty()) {
            return empNo.trim();
        }
        return "DEV_EMP";
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error("BAD_REQUEST", e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error("CONFLICT", e.getMessage()));
    }

    private Map<String, Object> error(String code, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", code);
        body.put("message", message);
        return body;
    }
}
