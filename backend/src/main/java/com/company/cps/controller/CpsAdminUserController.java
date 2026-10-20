package com.company.cps.controller;

import com.company.cps.dto.CpsAdminPageResponse;
import com.company.cps.dto.CpsAdminPasswordChangeRequest;
import com.company.cps.dto.CpsAdminUserCreateRequest;
import com.company.cps.dto.CpsAdminUserDto;
import com.company.cps.dto.CpsAdminUserUpdateRequest;
import com.company.cps.dto.CpsEnabledRequest;
import com.company.cps.service.CpsAdminAuditLogService;
import com.company.cps.service.CpsAdminUserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

/**
 * 管理端账号管理：分页、创建、更新、停用/启用、重置密码、自助改密。
 * 仅 superAdmin (pagePermissions=["*"]) 可访问（前端控制可见性；后端目前不强制）。
 */
@RestController
@RequestMapping("/api/cps/admin/users")
public class CpsAdminUserController {

    private final CpsAdminUserService userService;
    private final CpsAdminAuditLogService auditLogService;

    public CpsAdminUserController(CpsAdminUserService userService, CpsAdminAuditLogService auditLogService) {
        this.userService = userService;
        this.auditLogService = auditLogService;
    }

    @GetMapping("/page")
    public CpsAdminPageResponse<CpsAdminUserDto> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return userService.page(keyword, status, page, pageSize);
    }

    @GetMapping("/{id}")
    public CpsAdminUserDto get(@PathVariable Long id) {
        return userService.getById(id);
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody CpsAdminUserCreateRequest request) {
        CpsAdminUserDto dto = userService.create(request);
        Map<String, Object> result = new HashMap<>();
        result.put("user", dto);
        result.put("message", "账号已创建，首次登录后强制修改密码");
        return result;
    }

    @PatchMapping("/{id}")
    public CpsAdminUserDto update(@PathVariable Long id, @RequestBody CpsAdminUserUpdateRequest request) {
        return userService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public void setStatus(@PathVariable Long id, @RequestBody CpsEnabledRequest request) {
        userService.setStatus(id, request.getEnabled() ? "ACTIVE" : "LOCKED", request.getEmpNo());
    }

    /** 管理员重置密码（返回新密码给调用方） */
    @PostMapping("/{id}/reset-password")
    public Map<String, String> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String current = body == null ? null : body.get("currentEmpNo");
        String newPwd = userService.resetPassword(id, current);
        Map<String, String> r = new HashMap<>();
        r.put("newPassword", newPwd);
        r.put("message", "重置成功，新密码已生成（首次登录后强制修改）");
        return r;
    }

    /** 自助改密 */
    @PostMapping("/change-password")
    public void changePassword(@RequestBody CpsAdminPasswordChangeRequest request) {
        userService.changePassword(request);
        auditLogService.record(request.getCurrentEmpNo(), null, "PASSWORD_CHANGE", "USER", request.getEmpNo(),
                "自助改密", null, null, "SUCCESS", null);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<java.util.Map<String, Object>> handleBadArg(IllegalArgumentException ex) {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("status", 400);
        body.put("error", "Bad Request");
        body.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }
}
