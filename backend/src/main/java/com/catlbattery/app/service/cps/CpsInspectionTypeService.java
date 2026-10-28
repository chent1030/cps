package com.catlbattery.app.service.cps;

import com.catlbattery.domain.entity.cps.CpsCheckItem;
import com.catlbattery.domain.entity.cps.CpsInspectionType;
import com.catlbattery.domain.entity.cps.dto.CpsInspectionTypeDto;
import com.catlbattery.domain.entity.cps.dto.CpsInspectionTypeRequest;
import com.catlbattery.infra.mapper.cps.CpsCheckItemMapper;
import com.catlbattery.infra.mapper.cps.CpsInspectionAreaMapper;
import com.catlbattery.infra.mapper.cps.CpsInspectionTypeMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.catlbattery.common.util.CpsStrings;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 巡检类型服务（Phase C1 统一模型）。
 * 类型固定字段：编码/名称/巡检内容/是否分区域；
 * zoneFlag=0 时点检项直挂类型（checkItemIds 校验存在且启用）；
 * zoneFlag=1 时点检项挂在巡检区域上（CpsInspectionAreaService 校验）。
 * formSchemaJson 已废弃：传入才校验保存，不再必填。
 */
@Service
public class CpsInspectionTypeService {

    @Autowired
    private CpsInspectionTypeMapper mapper;

    @Autowired
    private CpsCheckItemMapper checkItemMapper;

    @Autowired
    private CpsInspectionAreaMapper areaMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public Map<String, Object> page(String keyword, Integer enabled, int page, int pageSize) {
        if (page < 1) page = 1;
        if (pageSize < 1) pageSize = 20;
        int offset = (page - 1) * pageSize;
        List<CpsInspectionType> rows = mapper.findPage(blankToNull(keyword), enabled, offset, pageSize);
        int total = mapper.countPage(blankToNull(keyword), enabled);
        Map<String, Object> result = new HashMap<>();
        result.put("records", rows.stream().map(CpsInspectionTypeDto::from).collect(Collectors.toList()));
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);
        return result;
    }

    public List<CpsInspectionTypeDto> listEnabled() {
        return mapper.listEnabled().stream().map(CpsInspectionTypeDto::from).collect(Collectors.toList());
    }

    public CpsInspectionTypeDto getById(Long id) {
        require(id != null, "id is required");
        CpsInspectionType t = mapper.findById(id);
        require(t != null, "巡检类型不存在: id=" + id);
        return CpsInspectionTypeDto.from(t);
    }

    public CpsInspectionTypeDto findByCodeOrThrow(String code) {
        requireNotBlank(code, "code");
        CpsInspectionType t = mapper.findByCode(code.trim());
        require(t != null, "巡检类型不存在或已停用: code=" + code);
        require(t.getEnabled() != null && t.getEnabled() == 1, "巡检类型已停用: code=" + code);
        return CpsInspectionTypeDto.from(t);
    }

    /**
     * Phase D2：类型的点检项明细（Android 填写页按配置渲染）。
     * 不分区域类型取直挂 checkItemIds；分区域类型请走区域接口。停用/缺失项跳过。
     */
    public List<CpsCheckItem> listCheckItemsByType(String code) {
        CpsInspectionTypeDto type = findByCodeOrThrow(code);
        require(type.getZoneFlag() == null || type.getZoneFlag() != 1,
                "分区域类型的点检项挂在区域上，请按区域查询: code=" + code);
        List<CpsCheckItem> items = new ArrayList<>();
        String ids = type.getCheckItemIds();
        if (ids == null || CpsStrings.isBlank(ids)) return items;
        for (String part : ids.split(",")) {
            String itemCode = part.trim();
            if (itemCode.isEmpty()) continue;
            checkItemMapper.findByCode(itemCode)
                    .filter((ci) -> Boolean.TRUE.equals(ci.getEnabled()))
                    .ifPresent(items::add);
        }
        return items;
    }

    @Transactional
    public CpsInspectionTypeDto create(CpsInspectionTypeRequest req) {
        requireNotBlank(req.getCode(), "code");
        requireNotBlank(req.getName(), "name");
        CpsInspectionType exist = mapper.findByCode(req.getCode().trim());
        require(exist == null, "巡检类型编码已存在: " + req.getCode());
        int zoneFlag = req.getZoneFlag() == null ? 0 : (req.getZoneFlag() == 1 ? 1 : 0);
        String checkItemIds = normalizeCheckItemIds(req.getCheckItemIds(), zoneFlag);
        CpsInspectionType t = new CpsInspectionType();
        t.setCode(req.getCode().trim());
        t.setName(req.getName().trim());
        t.setContent(blankToNull(req.getContent()));
        t.setZoneFlag(zoneFlag);
        t.setCheckItemIds(checkItemIds);
        t.setDescription(blankToNull(req.getDescription()));
        t.setIcon(blankToNull(req.getIcon()));
        t.setColor(blankToNull(req.getColor()));
        t.setEnabled(req.getEnabled() == null ? 1 : req.getEnabled());
        t.setSortOrder(req.getSortOrder() == null ? 0 : req.getSortOrder());
        // form_schema 已废弃：传了才校验保存，否则留空
        if (req.getFormSchemaJson() != null && !CpsStrings.isBlank(req.getFormSchemaJson())) {
            validateFormSchema(req.getFormSchemaJson());
            t.setFormSchemaJson(req.getFormSchemaJson().trim());
        }
        t.setDefaultAssigneeEmpNo(blankToNull(req.getDefaultAssigneeEmpNo()));
        t.setRemark(blankToNull(req.getRemark()));
        LocalDateTime now = LocalDateTime.now();
        t.setCreatedAt(now);
        t.setUpdatedAt(now);
        mapper.insert(t);
        return CpsInspectionTypeDto.from(t);
    }

    @Transactional
    public CpsInspectionTypeDto update(Long id, CpsInspectionTypeRequest req) {
        require(id != null, "id is required");
        CpsInspectionType t = mapper.findById(id);
        require(t != null, "巡检类型不存在: id=" + id);
        boolean builtin = t.getBuiltin() != null && t.getBuiltin() == 1;
        if (req.getCode() != null && !CpsStrings.isBlank(req.getCode()) && !req.getCode().equals(t.getCode())) {
            require(!builtin, "内置类型（" + t.getName() + "）不可修改编码");
            CpsInspectionType dup = mapper.findByCode(req.getCode().trim());
            require(dup == null || dup.getId().equals(id), "巡检类型编码已被占用: " + req.getCode());
            // 编码变更需联动区域 type_code（区域挂类型）
            long areaRefs = areaMapper.countByTypeCode(t.getCode());
            require(areaRefs == 0, "该类型下已有 " + areaRefs + " 个巡检区域，不能改编码；请先迁移区域");
            t.setCode(req.getCode().trim());
        }
        if (req.getName() != null && !CpsStrings.isBlank(req.getName())) t.setName(req.getName().trim());
        if (req.getContent() != null) t.setContent(blankToNull(req.getContent()));
        if (req.getZoneFlag() != null) {
            // 内置 CPS巡检 固定为不分区域的标准上报流程
            require(!builtin || (req.getZoneFlag() == 0), "内置类型不可改为分区域");
            t.setZoneFlag(req.getZoneFlag() == 1 ? 1 : 0);
        }
        if (req.getCheckItemIds() != null) {
            t.setCheckItemIds(normalizeCheckItemIds(req.getCheckItemIds(), t.getZoneFlag()));
        }
        if (req.getDescription() != null) t.setDescription(blankToNull(req.getDescription()));
        if (req.getIcon() != null) t.setIcon(blankToNull(req.getIcon()));
        if (req.getColor() != null) t.setColor(blankToNull(req.getColor()));
        if (req.getEnabled() != null) {
            require(!builtin || req.getEnabled() == 1, "内置类型不可停用");
            t.setEnabled(req.getEnabled());
        }
        if (req.getSortOrder() != null) t.setSortOrder(req.getSortOrder());
        if (req.getFormSchemaJson() != null && !CpsStrings.isBlank(req.getFormSchemaJson())) {
            validateFormSchema(req.getFormSchemaJson());
            t.setFormSchemaJson(req.getFormSchemaJson().trim());
        }
        if (req.getDefaultAssigneeEmpNo() != null) t.setDefaultAssigneeEmpNo(blankToNull(req.getDefaultAssigneeEmpNo()));
        if (req.getRemark() != null) t.setRemark(blankToNull(req.getRemark()));
        t.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(t);
        return CpsInspectionTypeDto.from(t);
    }

    @Transactional
    public void delete(Long id) {
        require(id != null, "id is required");
        CpsInspectionType t = mapper.findById(id);
        require(t != null, "巡检类型不存在: id=" + id);
        require(t.getBuiltin() == null || t.getBuiltin() != 1, "内置类型（" + t.getName() + "）不可删除");
        // 安全护栏：问题引用或区域引用时禁止删除
        long issueUsed = mapper.countIssueByCode(t.getCode());
        require(issueUsed == 0, "已有问题引用该类型（code=" + t.getCode() + "），请先停用或迁移数据");
        long areaUsed = areaMapper.countByTypeCode(t.getCode());
        require(areaUsed == 0, "该类型下还有 " + areaUsed + " 个巡检区域，请先删除或迁移区域");
        mapper.deleteById(id);
    }

    /**
     * 规范化直挂点检项：trim + 去重 + 校验每个 item_code 存在且启用。
     * zoneFlag=1（分区域）时忽略直挂项（点检项挂在区域上），返回 null。
     */
    private String normalizeCheckItemIds(String raw, int zoneFlag) {
        if (zoneFlag == 1) return null;
        if (raw == null || CpsStrings.isBlank(raw)) return null;
        List<String> codes = new ArrayList<>();
        for (String part : raw.split(",")) {
            String code = part.trim();
            if (code.isEmpty() || codes.contains(code)) continue;
            CpsCheckItem item = checkItemMapper.findByCode(code).orElse(null);
            require(item != null, "点检项不存在: " + code);
            require(Boolean.TRUE.equals(item.getEnabled()), "点检项已停用: " + code);
            codes.add(code);
        }
        return codes.isEmpty() ? null : String.join(",", codes);
    }

    /** 【已废弃模型兼容】form_schema JSON 校验：仅当调用方仍传 formSchemaJson 时执行 */
    private void validateFormSchema(String json) {
        requireNotBlank(json, "formSchemaJson");
        try {
            Map<?, ?> schema = objectMapper.readValue(json, Map.class);
            Object fields = schema.get("fields");
            require(fields instanceof List, "formSchemaJson.fields 必须是数组");
            List<?> list = (List<?>) fields;
            java.util.Set<String> keys = new java.util.HashSet<>();
            java.util.Set<String> allowed = new java.util.LinkedHashSet<>(java.util.Arrays.asList("input", "number", "textarea", "select", "date", "switch"));
            for (Object f : list) {
                require(f instanceof Map, "formSchemaJson.fields 元素必须是对象");
                Map<?, ?> fm = (Map<?, ?>) f;
                Object key = fm.get("key");
                Object type = fm.get("type");
                require(key instanceof String && !((String) key).isEmpty(), "formSchemaJson.fields[].key 必填");
                require(type instanceof String, "formSchemaJson.fields[].type 必填");
                require(allowed.contains(type), "formSchemaJson.fields[].type 必须是 " + allowed);
                require(keys.add((String) key), "formSchemaJson.fields[].key 重复: " + key);
                if ("select".equals(type)) {
                    Object opts = fm.get("options");
                    require(opts instanceof List, "select 字段必须提供 options 数组");
                }
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("formSchemaJson 不是合法 JSON: " + e.getMessage());
        }
    }

    private static String blankToNull(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static void require(boolean cond, String message) {
        if (!cond) throw new IllegalArgumentException(message);
    }

    private static void requireNotBlank(String s, String field) {
        require(s != null && !CpsStrings.isBlank(s), field + " is required");
    }
}
