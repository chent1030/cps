package com.company.cps.service;

import com.company.cps.domain.CpsAreaPersonConfig;
import com.company.cps.domain.CpsProblemCategory;
import com.company.cps.dto.CpsAreaPersonConfigRequest;
import com.company.cps.dto.CpsAdminLoginRequest;
import com.company.cps.dto.CpsAdminLoginResponse;
import com.company.cps.dto.CpsOptionResponse;
import com.company.cps.dto.CpsAdminPageResponse;
import com.company.cps.dto.CpsProblemCategoryRequest;
import com.company.cps.mapper.CpsAreaPersonConfigMapper;
import com.company.cps.mapper.CpsAdminUserMapper;
import com.company.cps.mapper.CpsProblemCategoryMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CpsMasterDataService {

    private static final int MAX_EXPORT_ROWS = 10000;
    private final CpsProblemCategoryMapper categoryMapper;
    private final CpsAreaPersonConfigMapper areaPersonConfigMapper;
    private final CpsAdminUserMapper adminUserMapper;
    private final CpsAdminUserService adminUserService;
    private final CpsAdminAuditLogService auditLogService;

    public CpsMasterDataService(
            CpsProblemCategoryMapper categoryMapper,
            CpsAreaPersonConfigMapper areaPersonConfigMapper,
            CpsAdminUserMapper adminUserMapper,
            CpsAdminUserService adminUserService,
            CpsAdminAuditLogService auditLogService
    ) {
        this.categoryMapper = categoryMapper;
        this.areaPersonConfigMapper = areaPersonConfigMapper;
        this.adminUserMapper = adminUserMapper;
        this.adminUserService = adminUserService;
        this.auditLogService = auditLogService;
    }

    public List<CpsOptionResponse> getFactories() {
        return areaPersonConfigMapper.findFactoryOptions();
    }

    public List<CpsOptionResponse> getAreas(String factory) {
        requireText(factory, "factory is required");
        return areaPersonConfigMapper.findAreaOptions(factory.trim());
    }

    public List<CpsOptionResponse> getLines(String factory, String area) {
        requireText(factory, "factory is required");
        requireText(area, "area is required");
        return areaPersonConfigMapper.findLineOptions(factory.trim(), area.trim());
    }

    public List<CpsOptionResponse> getProcesses(String factory, String area, String line) {
        requireText(factory, "factory is required");
        requireText(area, "area is required");
        return areaPersonConfigMapper.findProcessOptions(factory.trim(), area.trim(), trimToNull(line));
    }

    public List<CpsOptionResponse> getCategoryOptions(Long parentId) {
        return categoryMapper.findEnabledOptionsByParentId(parentId);
    }

    public List<CpsProblemCategory> listCategories(Long parentId, Boolean enabled) {
        return categoryMapper.findAll(parentId, enabled);
    }

    public CpsAdminLoginResponse login(CpsAdminLoginRequest request) {
        requireNonNull(request, "login request is required");
        requireText(request.getEmpNo(), "empNo is required");
        requireText(request.getPassword(), "password is required");
        String empNo = request.getEmpNo().trim();
        com.company.cps.domain.CpsAdminUser operator = adminUserMapper.findByEmpNo(empNo)
                .orElseThrow(() -> {
                    auditLogService.record(empNo, null, "LOGIN_FAIL", "AUTH", empNo, null, null, null, "FAILED", "账号不存在");
                    return new IllegalArgumentException("工号或密码错误");
                });
        if (Boolean.FALSE.equals(operator.getEnabled())) {
            auditLogService.record(empNo, operator.getEmpName(), "LOGIN_FAIL", "AUTH", empNo, null, null, null, "FAILED", "账号已停用");
            throw new IllegalArgumentException("账号已停用");
        }
        if ("LOCKED".equals(operator.getStatus())) {
            auditLogService.record(empNo, operator.getEmpName(), "LOGIN_FAIL", "AUTH", empNo, null, null, null, "FAILED", "账号已锁定");
            throw new IllegalArgumentException("账号已锁定，请联系管理员");
        }
        if (!com.company.cps.support.CpsPasswordUtil.matches(request.getPassword(), operator.getSalt(), operator.getPasswordHash())) {
            auditLogService.record(empNo, operator.getEmpName(), "LOGIN_FAIL", "AUTH", empNo, null, null, null, "FAILED", "密码错误");
            throw new IllegalArgumentException("工号或密码错误");
        }
        boolean superAdmin = operator.isSuperAdmin();
        java.util.List<String> permissions = superAdmin ? java.util.Arrays.asList("*") : safe(operator.parsePagePermissions());
        adminUserService.markLogin(operator.getId());
        auditLogService.record(empNo, operator.getEmpName(), "LOGIN_SUCCESS", "AUTH", empNo, null, null, null, "SUCCESS", null);
        return new CpsAdminLoginResponse(
                operator.getEmpNo(),
                operator.getEmpName(),
                superAdmin,
                Boolean.TRUE.equals(operator.getMustChangePassword()),
                permissions
        );
    }

    private static java.util.List<String> safe(java.util.List<String> list) {
        return list == null ? java.util.Collections.emptyList() : list;
    }

    /** 退出登录时记录审计日志（前端主动调用） */
    public void recordLogout(String empNo, String empName) {
        if (empNo == null || empNo.isBlank()) return;
        auditLogService.record(empNo, empName, "LOGOUT", "AUTH", empNo, null, null, null, "SUCCESS", null);
    }

    public CpsAdminPageResponse<CpsProblemCategory> pageCategories(
            Long parentId, Boolean allLevels, Boolean enabled, String keyword, int page, int pageSize
    ) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        String normalizedKeyword = trimToNull(keyword);
        int offset = (safePage - 1) * safePageSize;
        List<CpsProblemCategory> records = categoryMapper.findAdminPage(
                parentId, allLevels, enabled, normalizedKeyword, safePageSize, offset
        );
        long total = categoryMapper.countAdmin(parentId, allLevels, enabled, normalizedKeyword);
        return new CpsAdminPageResponse<>(records, total, safePage, safePageSize);
    }

    public List<CpsProblemCategory> exportCategories(Long parentId, Boolean enabled, String keyword) {
        return categoryMapper.findAdminExport(parentId, true, enabled, trimToNull(keyword), MAX_EXPORT_ROWS);
    }

    @Transactional
    public CpsProblemCategory saveCategory(CpsProblemCategoryRequest request, String currentEmpNo) {
        validateCategory(request);
        CpsProblemCategory category = new CpsProblemCategory();
        category.setId(request.getId());
        category.setParentId(request.getParentId() == null ? 0L : request.getParentId());
        category.setCategoryLevel(request.getCategoryLevel());
        category.setCategoryName(request.getCategoryName().trim());
        category.setSortNo(request.getSortNo() == null ? 0 : request.getSortNo());
        category.setEnabled(request.getEnabled() == null ? Boolean.TRUE : request.getEnabled());
        category.setCreatedBy(currentEmpNo);
        category.setUpdatedBy(currentEmpNo);
        categoryMapper.upsert(category);
        return category;
    }

    @Transactional
    public void setCategoryEnabled(Long id, Boolean enabled, String currentEmpNo) {
        requireNonNull(id, "id is required");
        requireNonNull(enabled, "enabled is required");
        int updated = categoryMapper.setEnabled(id, enabled, currentEmpNo);
        if (updated != 1) {
            throw new IllegalArgumentException("category not found: " + id);
        }
    }

    public List<CpsAreaPersonConfig> listAreaPersonConfigs(String factory, String area, Boolean enabled) {
        return areaPersonConfigMapper.findAll(trimToNull(factory), trimToNull(area), enabled);
    }

    public CpsAdminPageResponse<CpsAreaPersonConfig> pageAreaPersonConfigs(
            String factory, String area, String line, String process, Boolean enabled, String keyword, int page, int pageSize
    ) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        String normalizedFactory = trimToNull(factory);
        String normalizedArea = trimToNull(area);
        String normalizedKeyword = trimToNull(keyword);
        int offset = (safePage - 1) * safePageSize;
        List<CpsAreaPersonConfig> records = areaPersonConfigMapper.findAdminPage(
                normalizedFactory, normalizedArea, trimToNull(line), trimToNull(process), enabled, normalizedKeyword, safePageSize, offset
        );
        long total = areaPersonConfigMapper.countAdmin(
                normalizedFactory, normalizedArea, trimToNull(line), trimToNull(process), enabled, normalizedKeyword
        );
        return new CpsAdminPageResponse<>(records, total, safePage, safePageSize);
    }

    public List<CpsAreaPersonConfig> exportAreaPersonConfigs(
            String factory, String area, String line, String process, Boolean enabled, String keyword
    ) {
        return areaPersonConfigMapper.findAdminExport(
                trimToNull(factory), trimToNull(area), trimToNull(line), trimToNull(process), enabled, trimToNull(keyword), MAX_EXPORT_ROWS
        );
    }

    @Transactional
    public CpsAreaPersonConfig saveAreaPersonConfig(CpsAreaPersonConfigRequest request) {
        validateAreaPersonConfig(request);
        CpsAreaPersonConfig config = new CpsAreaPersonConfig();
        config.setId(request.getId());
        config.setFactory(request.getFactory().trim());
        config.setArea(request.getArea().trim());
        config.setLine(blankToEmpty(request.getLine()));
        config.setProcess(blankToEmpty(request.getProcess()));
        config.setEmpNo(request.getEmpNo().trim());
        config.setEmpName(firstNonBlank(request.getEmpName(), request.getEmpNo()));
        config.setEnabled(request.getEnabled() == null ? Boolean.TRUE : request.getEnabled());
        if (request.getId() != null) {
            CpsAreaPersonConfig existing = areaPersonConfigMapper.findById(request.getId()).orElseThrow(() -> new IllegalArgumentException("area person config not found: " + request.getId()));
            config.setCreatedBy(existing.getCreatedBy());
            config.setCreatedName(existing.getCreatedName());
        } else {
            config.setCreatedBy(request.getOperatorEmpNo().trim());
            config.setCreatedName(request.getOperatorEmpName().trim());
        }
        config.setUpdatedBy(request.getOperatorEmpNo().trim());
        config.setUpdatedName(request.getOperatorEmpName().trim());
        areaPersonConfigMapper.upsert(config);
        return config;
    }

    @Transactional
    public void setAreaPersonConfigEnabled(Long id, Boolean enabled, String currentEmpNo) {
        requireNonNull(id, "id is required");
        requireNonNull(enabled, "enabled is required");
        int updated = areaPersonConfigMapper.setEnabled(id, enabled, currentEmpNo);
        if (updated != 1) {
            throw new IllegalArgumentException("area person config not found: " + id);
        }
    }

    private static void validateCategory(CpsProblemCategoryRequest request) {
        requireNonNull(request, "request is required");
        requireText(request.getCategoryName(), "categoryName is required");
        requireNonNull(request.getCategoryLevel(), "categoryLevel is required");
        if (request.getCategoryLevel() != 1 && request.getCategoryLevel() != 2) {
            throw new IllegalArgumentException("categoryLevel must be 1 or 2");
        }
        if (request.getCategoryLevel() == 1 && request.getParentId() != null && request.getParentId() != 0L) {
            throw new IllegalArgumentException("level 1 category parentId must be empty or 0");
        }
        if (request.getCategoryLevel() == 2 && (request.getParentId() == null || request.getParentId() == 0L)) {
            throw new IllegalArgumentException("level 2 category parentId is required");
        }
    }

    private static void validateAreaPersonConfig(CpsAreaPersonConfigRequest request) {
        requireNonNull(request, "request is required");
        requireText(request.getFactory(), "factory is required");
        requireText(request.getArea(), "area is required");
        requireText(request.getLine(), "line is required");
        requireText(request.getProcess(), "process is required");
        requireText(request.getEmpNo(), "empNo is required");
        requireText(request.getEmpName(), "empName is required");
        requireText(request.getOperatorEmpNo(), "operatorEmpNo is required");
        requireText(request.getOperatorEmpName(), "operatorEmpName is required");
    }

    private static void requireNonNull(Object value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void requireText(String value, String message) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(message);
        }
    }

    private static String blankToEmpty(String value) {
        return isBlank(value) ? "" : value.trim();
    }

    private static String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private static String firstNonBlank(String first, String second) {
        if (!isBlank(first)) {
            return first.trim();
        }
        return trimToNull(second);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
