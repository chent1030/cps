package com.catlbattery.api.controller.v1.cps;

import com.catlbattery.domain.entity.cps.CpsAreaPersonConfig;
import com.catlbattery.app.service.cps.CpsAssignmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/cps/assignment")
public class CpsAssignmentController {

    private final CpsAssignmentService assignmentService;

    public CpsAssignmentController(CpsAssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    /**
     * 根据工厂、区域、拉线、工序匹配问题反馈处理人。
     * 直接从 cps_area_person_config 取 emp_no + emp_name；未匹配上返回 204。
     */
    @GetMapping("/feedback-handler")
    public ResponseEntity<Map<String, String>> feedbackHandler(
            @RequestParam String factory,
            @RequestParam String area,
            @RequestParam String line,
            @RequestParam String process
    ) {
        CpsAreaPersonConfig row = assignmentService.findFeedbackHandlerRow(factory, area, line, process);
        return toResponse(row);
    }

    /**
     * 根据工厂和区域匹配审核人。
     */
    @GetMapping("/reviewer")
    public ResponseEntity<Map<String, String>> reviewer(@RequestParam String factory, @RequestParam String area) {
        CpsAreaPersonConfig row = assignmentService.findReviewerRow(factory, area);
        return toResponse(row);
    }

    private static ResponseEntity<Map<String, String>> toResponse(CpsAreaPersonConfig row) {
        if (row == null) {
            return ResponseEntity.noContent().build();
        }
        Map<String, String> result = new LinkedHashMap<>();
        result.put("empNo", row.getEmpNo());
        result.put("empName", row.getEmpName() != null ? row.getEmpName() : row.getEmpNo());
        return ResponseEntity.ok(result);
    }
}
