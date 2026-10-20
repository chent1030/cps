package com.company.cps.service;

import com.company.cps.domain.CpsAdminAuditLog;
import com.company.cps.dto.CpsAdminAuditLogDto;
import com.company.cps.dto.CpsAdminPageResponse;
import com.company.cps.mapper.CpsAdminAuditLogMapper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 审计日志：登录、登出、密码修改、账号 CRUD、权限变更。
 * record() 用 @Async 异步落库，避免影响主业务。
 */
@Service
public class CpsAdminAuditLogService {

    private final CpsAdminAuditLogMapper mapper;

    public CpsAdminAuditLogService(CpsAdminAuditLogMapper mapper) {
        this.mapper = mapper;
    }

    @Async
    public void record(String empNo, String empName, String action,
                       String targetType, String targetId, String detail,
                       String ip, String userAgent, String status, String errorMessage) {
        try {
            CpsAdminAuditLog log = new CpsAdminAuditLog();
            log.setEmpNo(empNo == null ? "SYSTEM" : empNo);
            log.setEmpName(empName);
            log.setAction(action);
            log.setTargetType(targetType);
            log.setTargetId(targetId);
            log.setDetail(detail);
            log.setIp(ip);
            log.setUserAgent(userAgent);
            log.setStatus(status == null ? "SUCCESS" : status);
            log.setErrorMessage(errorMessage);
            mapper.insert(log);
        } catch (Exception ignored) {
            // 审计失败不抛，影响主业务
        }
    }

    public CpsAdminPageResponse<CpsAdminAuditLogDto> page(String empNo, String action, String status, String keyword, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        int offset = (safePage - 1) * safePageSize;
        List<CpsAdminAuditLog> records = mapper.findPage(empNo, action, status, keyword, safePageSize, offset);
        long total = mapper.countPage(empNo, action, status, keyword);
        List<CpsAdminAuditLogDto> dtos = records.stream().map(this::toDto).collect(Collectors.toList());
        return new CpsAdminPageResponse<>(dtos, total, safePage, safePageSize);
    }

    private CpsAdminAuditLogDto toDto(CpsAdminAuditLog log) {
        CpsAdminAuditLogDto dto = new CpsAdminAuditLogDto();
        dto.setId(log.getId());
        dto.setEmpNo(log.getEmpNo());
        dto.setEmpName(log.getEmpName());
        dto.setAction(log.getAction());
        dto.setTargetType(log.getTargetType());
        dto.setTargetId(log.getTargetId());
        dto.setDetail(log.getDetail());
        dto.setIp(log.getIp());
        dto.setUserAgent(log.getUserAgent());
        dto.setStatus(log.getStatus());
        dto.setErrorMessage(log.getErrorMessage());
        dto.setCreatedAt(log.getCreatedAt());
        return dto;
    }
}
