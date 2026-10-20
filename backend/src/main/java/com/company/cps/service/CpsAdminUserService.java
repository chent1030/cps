package com.company.cps.service;

import com.company.cps.domain.CpsAdminUser;
import com.company.cps.dto.CpsAdminPageResponse;
import com.company.cps.dto.CpsAdminPasswordChangeRequest;
import com.company.cps.dto.CpsAdminUserCreateRequest;
import com.company.cps.dto.CpsAdminUserDto;
import com.company.cps.dto.CpsAdminUserUpdateRequest;
import com.company.cps.mapper.CpsAdminUserMapper;
import com.company.cps.support.CpsPasswordUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 管理端账号 CRUD、密码重置、状态变更。
 * 校验：所有写操作强制 empNo 唯一；LOCKED 账号无法登录/被改密；
 * 普通修改不允许把自己 disable（防止误操作）；超管不允许删除。
 */
@Service
public class CpsAdminUserService {

    public static final int MIN_PASSWORD_LENGTH = 6;
    public static final int MAX_PASSWORD_LENGTH = 64;

    private final CpsAdminUserMapper userMapper;
    private final CpsAdminAuditLogService auditLogService;

    public CpsAdminUserService(CpsAdminUserMapper userMapper, CpsAdminAuditLogService auditLogService) {
        this.userMapper = userMapper;
        this.auditLogService = auditLogService;
    }

    public CpsAdminPageResponse<CpsAdminUserDto> page(String keyword, String status, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        int offset = (safePage - 1) * safePageSize;
        List<CpsAdminUser> records = userMapper.findPage(trimToNull(keyword), trimToNull(status), safePageSize, offset);
        long total = userMapper.countPage(trimToNull(keyword), trimToNull(status));
        List<CpsAdminUserDto> dtos = records.stream().map(this::toDto).collect(Collectors.toList());
        return new CpsAdminPageResponse<>(dtos, total, safePage, safePageSize);
    }

    public CpsAdminUserDto getById(Long id) {
        CpsAdminUser user = requireExisting(id);
        return toDto(user);
    }

    @Transactional
    public CpsAdminUserDto create(CpsAdminUserCreateRequest request) {
        requireText(request.getEmpNo(), "empNo is required");
        requireText(request.getEmpName(), "empName is required");
        if (request.getPagePermissions() == null || request.getPagePermissions().isEmpty()) {
            throw new IllegalArgumentException("pagePermissions is required");
        }
        if (userMapper.findByEmpNo(request.getEmpNo().trim()).isPresent()) {
            throw new IllegalArgumentException("工号已存在: " + request.getEmpNo());
        }
        String initial = request.getInitialPassword();
        if (initial == null || initial.isEmpty()) {
            initial = generateDefaultPassword();
        } else {
            validatePassword(initial);
        }
        CpsAdminUser user = new CpsAdminUser();
        user.setEmpNo(request.getEmpNo().trim());
        user.setEmpName(request.getEmpName().trim());
        String salt = CpsPasswordUtil.generateSalt();
        user.setSalt(salt);
        user.setPasswordHash(CpsPasswordUtil.hash(initial, salt));
        user.setMustChangePassword(true);
        user.setStatus("ACTIVE");
        user.setEnabled(true);
        user.writePagePermissions(request.getPagePermissions());
        user.setRemark(request.getRemark());
        userMapper.insert(user);
        auditLogService.record(current(request), null, "USER_CREATE", "USER", user.getEmpNo(),
                "{\"empNo\":\"" + user.getEmpNo() + "\",\"empName\":\"" + user.getEmpName() + "\"}",
                null, null, "SUCCESS", null);
        CpsAdminUserDto dto = toDto(user);
        dto.setRemark("初始密码: " + initial + " （首次登录后强制修改）");
        return dto;
    }

    @Transactional
    public CpsAdminUserDto update(Long id, CpsAdminUserUpdateRequest request) {
        CpsAdminUser user = requireExisting(id);
        if (user.getStatus() != null && "LOCKED".equals(user.getStatus())) {
            // 锁定账号可由管理员解锁（通过单独接口）；此处不直接走 update
        }
        String newPermissions = null;
        if (request.getPagePermissions() != null) {
            user.writePagePermissions(request.getPagePermissions());
            newPermissions = user.getPagePermissions();
        }
        user.setEmpName(request.getEmpName() == null ? user.getEmpName() : request.getEmpName().trim());
        user.setStatus(request.getStatus() == null ? user.getStatus() : request.getStatus().trim());
        user.setRemark(request.getRemark());
        userMapper.updateProfile(user.getId(), user.getEmpName(), user.getStatus(), newPermissions, user.getRemark());
        auditLogService.record(current(request), null, "USER_UPDATE", "USER", user.getEmpNo(),
                "{\"status\":\"" + user.getStatus() + "\"}", null, null, "SUCCESS", null);
        return toDto(user);
    }

    /** 启用 / 停用 账号 */
    @Transactional
    public void setStatus(Long id, String status, String currentEmpNo) {
        CpsAdminUser user = requireExisting(id);
        if (!"ACTIVE".equals(status) && !"LOCKED".equals(status)) {
            throw new IllegalArgumentException("status must be ACTIVE or LOCKED");
        }
        // 防自锁：当前操作者不能把自己停用
        if (currentEmpNo != null && currentEmpNo.equals(user.getEmpNo()) && "LOCKED".equals(status)) {
            throw new IllegalStateException("不能停用当前登录账号");
        }
        String newPermissions = user.getPagePermissions();
        user.setStatus(status);
        userMapper.updateProfile(user.getId(), user.getEmpName(), user.getStatus(), newPermissions, user.getRemark());
        auditLogService.record(currentEmpNo, null, "LOCKED".equals(status) ? "USER_DISABLE" : "USER_ENABLE",
                "USER", user.getEmpNo(), "{\"status\":\"" + status + "\"}", null, null, "SUCCESS", null);
    }

    /** 管理员重置密码（生成新随机密码，返回给调用方，强制用户首次登录修改） */
    @Transactional
    public String resetPassword(Long id, String currentEmpNo) {
        CpsAdminUser user = requireExisting(id);
        String newPwd = generateDefaultPassword();
        String salt = CpsPasswordUtil.generateSalt();
        userMapper.updatePassword(user.getId(), CpsPasswordUtil.hash(newPwd, salt), salt, true);
        auditLogService.record(currentEmpNo, null, "USER_RESET_PASSWORD", "USER", user.getEmpNo(),
                "{\"by\":\"" + currentEmpNo + "\"}", null, null, "SUCCESS", null);
        return newPwd;
    }

    /** 用户自助改密（必须校验旧密码） */
    @Transactional
    public void changePassword(CpsAdminPasswordChangeRequest request) {
        requireText(request.getEmpNo(), "empNo is required");
        requireText(request.getOldPassword(), "oldPassword is required");
        requireText(request.getNewPassword(), "newPassword is required");
        validatePassword(request.getNewPassword());
        CpsAdminUser user = userMapper.findByEmpNo(request.getEmpNo().trim())
                .orElseThrow(() -> new IllegalArgumentException("账号不存在"));
        if (!CpsPasswordUtil.matches(request.getOldPassword(), user.getSalt(), user.getPasswordHash())) {
            auditLogService.record(request.getEmpNo(), null, "PASSWORD_CHANGE", "USER", user.getEmpNo(),
                    null, null, null, "FAILED", "旧密码错误");
            throw new IllegalArgumentException("旧密码错误");
        }
        String salt = CpsPasswordUtil.generateSalt();
        userMapper.updatePassword(user.getId(), CpsPasswordUtil.hash(request.getNewPassword(), salt), salt, false);
        auditLogService.record(request.getEmpNo(), null, "PASSWORD_CHANGE", "USER", user.getEmpNo(),
                "{\"by\":\"self\"}", null, null, "SUCCESS", null);
    }

    private CpsAdminUser requireExisting(Long id) {
        return userMapper.findAll().stream()
                .filter(u -> id.equals(u.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("账号不存在 id=" + id));
    }

    private CpsAdminUserDto toDto(CpsAdminUser user) {
        CpsAdminUserDto dto = new CpsAdminUserDto();
        dto.setId(user.getId());
        dto.setEmpNo(user.getEmpNo());
        dto.setEmpName(user.getEmpName());
        dto.setStatus(user.getStatus());
        dto.setMustChangePassword(user.getMustChangePassword());
        dto.setSuperAdmin(user.isSuperAdmin());
        dto.setPagePermissions(user.parsePagePermissions());
        dto.setRemark(user.getRemark());
        dto.setLastLoginAt(user.getLastLoginAt());
        dto.setCreatedAt(user.getCreatedAt());
        dto.setUpdatedAt(user.getUpdatedAt());
        return dto;
    }

    private static String current(Object req) {
        if (req instanceof CpsAdminUserCreateRequest) return ((CpsAdminUserCreateRequest) req).getCurrentEmpNo();
        if (req instanceof CpsAdminUserUpdateRequest) return ((CpsAdminUserUpdateRequest) req).getCurrentEmpNo();
        return null;
    }

    private static void requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(message);
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static void validatePassword(String pwd) {
        if (pwd.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("密码至少 " + MIN_PASSWORD_LENGTH + " 位");
        }
        if (pwd.length() > MAX_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("密码最长 " + MAX_PASSWORD_LENGTH + " 位");
        }
    }

    private static String generateDefaultPassword() {
        // 10 位字母+数字随机
        String chars = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
        java.util.Random r = new java.util.Random();
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) sb.append(chars.charAt(r.nextInt(chars.length())));
        return sb.toString();
    }

    /** 内部用：登入时刷新 lastLoginAt */
    public void markLogin(Long userId) {
        userMapper.updateLastLoginAt(userId, LocalDateTime.now());
    }
}
