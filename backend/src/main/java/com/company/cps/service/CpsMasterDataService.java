package com.company.cps.service;

import com.company.cps.domain.CpsAreaPersonConfig;
import com.company.cps.domain.CpsProblemCategory;
import com.company.cps.dto.CpsAreaPersonConfigRequest;
import com.company.cps.dto.CpsOptionResponse;
import com.company.cps.dto.CpsProblemCategoryRequest;
import com.company.cps.mapper.CpsAreaPersonConfigMapper;
import com.company.cps.mapper.CpsProblemCategoryMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CpsMasterDataService {

    private final CpsProblemCategoryMapper categoryMapper;
    private final CpsAreaPersonConfigMapper areaPersonConfigMapper;

    public CpsMasterDataService(
            CpsProblemCategoryMapper categoryMapper,
            CpsAreaPersonConfigMapper areaPersonConfigMapper
    ) {
        this.categoryMapper = categoryMapper;
        this.areaPersonConfigMapper = areaPersonConfigMapper;
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

    @Transactional
    public CpsProblemCategory saveCategory(CpsProblemCategoryRequest request, String currentEmpNo) {
        validateCategory(request);
        CpsProblemCategory category = new CpsProblemCategory();
        category.setId(request.getId());
        category.setParentId(request.getParentId() == null ? 0L : request.getParentId());
        category.setCategoryLevel(request.getCategoryLevel());
        category.setCategoryName(request.getCategoryName().trim());
        category.setCategoryCode(trimToNull(request.getCategoryCode()));
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

    @Transactional
    public CpsAreaPersonConfig saveAreaPersonConfig(CpsAreaPersonConfigRequest request, String currentEmpNo) {
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
        config.setCreatedBy(currentEmpNo);
        config.setUpdatedBy(currentEmpNo);
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
        requireText(request.getEmpNo(), "empNo is required");
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
