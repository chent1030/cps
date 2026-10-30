package com.catlbattery.app.service.cps;

import com.catlbattery.domain.entity.cps.CpsCheckItem;
import com.catlbattery.domain.entity.cps.CpsInspectionArea;
import com.catlbattery.domain.entity.cps.CpsInspectionType;
import com.catlbattery.domain.entity.cps.dto.CpsInspectionAreaRequest;
import com.catlbattery.infra.mapper.cps.CpsCheckItemMapper;
import com.catlbattery.infra.mapper.cps.CpsInspectionAreaMapper;
import com.catlbattery.infra.mapper.cps.CpsInspectionRecordMapper;
import com.catlbattery.infra.mapper.cps.CpsInspectionTypeMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.catlbattery.common.util.CpsStrings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 巡检区域服务（Phase C1）：区域 CRUD + 点检项挂载校验。
 * 区域必须挂在「分区域」的巡检类型下（zoneFlag=1）。
 */
@Service
public class CpsInspectionAreaService {

    private static final Set<String> RISK_LEVELS = new LinkedHashSet<>(Arrays.asList("HIGH", "MEDIUM", "LOW"));

    @Autowired
    private CpsInspectionAreaMapper areaMapper;

    @Autowired
    private CpsInspectionTypeMapper typeMapper;

    @Autowired
    private CpsCheckItemMapper checkItemMapper;

    @Autowired
    private CpsInspectionRecordMapper recordMapper;

    public Map<String, Object> page(String typeCode, String factory, String keyword, Boolean enabled, int page, int pageSize) {
        if (page < 1) page = 1;
        if (pageSize < 1) pageSize = 20;
        if (pageSize > 200) pageSize = 200;
        int offset = (page - 1) * pageSize;
        List<CpsInspectionArea> rows = areaMapper.findPage(trimToNull(typeCode), trimToNull(factory), trimToNull(keyword), enabled, pageSize, offset);
        long total = areaMapper.countPage(trimToNull(typeCode), trimToNull(factory), trimToNull(keyword), enabled);
        Map<String, Object> result = new HashMap<>();
        result.put("records", rows);
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);
        return result;
    }

    /** Android 任务列表：某分区域类型下全部启用区域 */
    public List<CpsInspectionArea> listEnabledByType(String typeCode) {
        requireText(typeCode, "typeCode");
        return areaMapper.listEnabledByType(typeCode.trim());
    }

    public CpsInspectionArea getById(Long id) {
        require(id != null, "id is required");
        CpsInspectionArea area = areaMapper.findById(id);
        require(area != null, "巡检区域不存在: id=" + id);
        return area;
    }

    @Transactional
    public CpsInspectionArea save(Long id, CpsInspectionAreaRequest req, String operatorEmpNo) {
        requireText(req.getAreaCode(), "areaCode");
        requireText(req.getTypeCode(), "typeCode");
        requireText(req.getAreaName(), "areaName");
        requireText(req.getKeeperEmpNo(), "keeperEmpNo");
        String risk = req.getRiskLevel() == null || CpsStrings.isBlank(req.getRiskLevel())
                ? "LOW" : req.getRiskLevel().trim().toUpperCase();
        require(RISK_LEVELS.contains(risk), "riskLevel 必须是 HIGH/MEDIUM/LOW: " + risk);

        CpsInspectionType type = typeMapper.findByCode(req.getTypeCode().trim());
        require(type != null, "巡检类型不存在: " + req.getTypeCode());
        require(type.getEnabled() != null && type.getEnabled() == 1, "巡检类型已停用: " + req.getTypeCode());
        require(type.getZoneFlag() != null && type.getZoneFlag() == 1,
                "巡检类型「" + type.getName() + "」不分区域，不能挂区域（点检项请直接配置在类型上）");

        String checkItemIds = normalizeCheckItemIds(req.getCheckItemIds());

        CpsInspectionArea existing = id == null ? null : areaMapper.findById(id);
        require(id == null || existing != null, "巡检区域不存在: id=" + id);
        CpsInspectionArea sameCode = areaMapper.findByCode(req.getAreaCode().trim());
        if (sameCode != null && (existing == null || !sameCode.getId().equals(existing.getId()))) {
            throw new IllegalStateException("区域编码已存在: " + req.getAreaCode());
        }

        CpsInspectionArea area = existing == null ? new CpsInspectionArea() : existing;
        area.setAreaCode(req.getAreaCode().trim());
        area.setTypeCode(req.getTypeCode().trim());
        area.setAreaName(req.getAreaName().trim());
        area.setFactory(trimToNull(req.getFactory()));
        area.setArea(trimToNull(req.getArea()));
        area.setLineOrRoomType(trimToNull(req.getLineOrRoomType()));
        area.setProcess(trimToNull(req.getProcess()));
        area.setBuilding(trimToNull(req.getBuilding()));
        area.setDoorNo(trimToNull(req.getDoorNo()));
        area.setRiskLevel(risk);
        area.setKeeperEmpNo(req.getKeeperEmpNo().trim());
        area.setKeeperEmpName(firstNonBlank(req.getKeeperEmpName(), req.getKeeperEmpNo()));
        area.setCheckItemIds(checkItemIds);
        area.setRemark(trimToNull(req.getRemark()));
        area.setEnabled(req.getEnabled() == null ? Boolean.TRUE : req.getEnabled());
        area.setUpdatedBy(operatorEmpNo);

        if (existing == null) {
            area.setCreatedBy(operatorEmpNo);
            areaMapper.insert(area);
        } else {
            areaMapper.update(area);
        }
        return areaMapper.findById(area.getId());
    }

    @Transactional
    public void setEnabled(Long id, Boolean enabled, String operatorEmpNo) {
        int rows = areaMapper.setEnabled(id, enabled, operatorEmpNo);
        require(rows == 1, "巡检区域不存在或未更新: id=" + id);
    }

    @Transactional
    public void delete(Long id) {
        CpsInspectionArea area = getById(id);
        // B7：有执行记录引用时禁删（此前注释存在但未实现，删除会留下悬空 area_id 的脏记录）。
        long used = recordMapper.countByArea(area.getId(), area.getAreaCode());
        if (used > 0) {
            throw new IllegalStateException("该区域已有 " + used + " 条执行记录，不能删除，请先停用该区域");
        }
        areaMapper.deleteById(id);
    }

    /** 点检项编码规范化：trim/去重/存在且启用校验 */
    private String normalizeCheckItemIds(String raw) {
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

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !CpsStrings.isBlank(a)) return a.trim();
        return b == null ? null : b.trim();
    }

    private static void require(boolean cond, String message) {
        if (!cond) throw new IllegalArgumentException(message);
    }

    private static void requireText(String value, String field) {
        require(value != null && !CpsStrings.isBlank(value), field + " is required");
    }
}
