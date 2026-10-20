package com.company.cps.controller;

import com.company.cps.domain.CpsInspectionArea;
import com.company.cps.domain.CpsInspectionRecord;
import com.company.cps.domain.CpsInspectionRecordItem;
import com.company.cps.service.CpsInspectionAreaService;
import com.company.cps.service.CpsInspectionRecordService;
import com.company.cps.service.CpsInspectionTypeService;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Android 端 - 统一巡检任务执行（Phase C1）。
 * 流程：types（选类型）→ areas（分区域类型选区域）→ start（生成记录+明细快照）
 *       → items/{id}/result（逐项保存）→ submit（计分锁定）。
 */
@RestController
@RequestMapping("/api/cps/inspection-tasks")
public class CpsInspectionTaskController {

    private final CpsInspectionTypeService typeService;
    private final CpsInspectionAreaService areaService;
    private final CpsInspectionRecordService recordService;

    public CpsInspectionTaskController(CpsInspectionTypeService typeService,
                                       CpsInspectionAreaService areaService,
                                       CpsInspectionRecordService recordService) {
        this.typeService = typeService;
        this.areaService = areaService;
        this.recordService = recordService;
    }

    /** 可用巡检类型（enabled=1） */
    @GetMapping("/types")
    public Object types() {
        return typeService.listEnabled();
    }

    /** 分区域类型的区域列表 */
    @GetMapping("/areas")
    public List<CpsInspectionArea> areas(@RequestParam String typeCode) {
        return areaService.listEnabledByType(typeCode);
    }

    /** 开始执行：生成记录 + 点检项明细快照 */
    @PostMapping("/start")
    public CpsInspectionRecord start(@RequestBody Map<String, Object> body) {
        String typeCode = str(body.get("typeCode"));
        Long areaId = lng(body.get("areaId"));
        Long planTaskId = lng(body.get("planTaskId"));
        Long planId = lng(body.get("planId"));
        String empNo = str(body.get("empNo"));
        String empName = str(body.get("empName"));
        return recordService.start(typeCode, areaId, planTaskId, planId, empNo, empName);
    }

    /** 记录明细（执行页渲染用） */
    @GetMapping("/{recordId}")
    public Map<String, Object> detail(@PathVariable Long recordId) {
        return recordService.detail(recordId);
    }

    /** 保存单项结果（合格/文本/照片） */
    @PostMapping("/items/{itemId}/result")
    public CpsInspectionRecordItem saveItemResult(@PathVariable Long itemId, @RequestBody Map<String, Object> body) {
        return recordService.saveItemResult(itemId,
                str(body.get("passResult")),
                str(body.get("textValue")),
                str(body.get("photoObjectKey")),
                str(body.get("photoFileName")),
                body.get("photoCount") == null ? null : Integer.valueOf(String.valueOf(body.get("photoCount"))));
    }

    /** 提交计分（JUDGED 锁定） */
    @PostMapping("/{recordId}/submit")
    public CpsInspectionRecord submit(@PathVariable Long recordId) {
        return recordService.submit(recordId);
    }

    /** 我的执行中记录（恢复现场） */
    @GetMapping("/my-in-progress")
    public List<CpsInspectionRecord> myInProgress(@RequestParam String empNo) {
        return recordService.listInProgress(empNo);
    }

    /** Phase D：我的分配任务（计划建单分配给我的未完成任务，含类型/覆盖区域元信息） */
    @GetMapping("/my-tasks")
    public List<com.company.cps.dto.CpsMyInspectionTaskDto> myTasks(@RequestParam String empNo) {
        return recordService.listMyTasks(empNo);
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static Long lng(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o).trim();
        if (s.isEmpty() || "null".equals(s)) return null;
        try {
            return Long.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
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
