package com.company.cps.controller;

import com.company.cps.service.CpsAssignmentService;
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
     */
    @GetMapping("/feedback-handler")
    public Map<String, String> feedbackHandler(
            @RequestParam String factory,
            @RequestParam String area,
            @RequestParam String line,
            @RequestParam String process
    ) {
        String empNo = assignmentService.findFeedbackHandler(factory, area, line, process);
        return emp(empNo);
    }

    /**
     * 根据工厂和区域匹配审核人。
     */
    @GetMapping("/reviewer")
    public Map<String, String> reviewer(@RequestParam String factory, @RequestParam String area) {
        String empNo = assignmentService.findReviewer(factory, area);
        return emp(empNo);
    }

    private static Map<String, String> emp(String empNo) {
        Map<String, String> result = new LinkedHashMap<String, String>();
        result.put("empNo", empNo);
        result.put("empName", empNo);
        return result;
    }
}
