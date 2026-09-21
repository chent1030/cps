package com.company.cps.controller;

import com.company.cps.service.CpsAgentFrameworkClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Admin-only read projections for the shared basic-project Agent runtime.
 * The browser remains inside the CPS API boundary and never receives runtime credentials.
 */
@RestController
@RequestMapping("/api/cps/admin/agent")
public class CpsAdminAgentController {
    private final CpsAgentFrameworkClient agentFrameworkClient;

    public CpsAdminAgentController(CpsAgentFrameworkClient agentFrameworkClient) {
        this.agentFrameworkClient = agentFrameworkClient;
    }

    @GetMapping("/runtime")
    public Map<String, Object> runtime() {
        return agentFrameworkClient.runtimeStatus();
    }

}
