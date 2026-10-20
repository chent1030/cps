package com.company.cps.service;

import com.company.cps.domain.CpsDict;
import com.company.cps.domain.CpsDictType;
import com.company.cps.dto.CpsDictRequest;
import com.company.cps.dto.CpsDictTypeRequest;
import com.company.cps.mapper.CpsDictMapper;
import com.company.cps.mapper.CpsDictTypeMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 通用字典（双表，V20261016）：cps_dict_type（类型）+ cps_dict（条目，type_id 外键）。
 * - 后端强校验组：ROOM_TYPE / RISK_LEVEL / PHOTO_CATEGORY（经 {@link #valueExists} 查启用项，
 *   口径 = 条目启用且所属类型启用）；仅下拉展示组：PLAN_TYPE / FREQUENCY；
 * - 条目级 builtin（如 RISK_LEVEL 三项）：不可删/停/改值、不可换类型；
 * - 类型级 builtin（初始五组）：不可删/停/改码，名称/说明/排序可改；删除类型前须先清空条目。
 */
@Service
public class CpsDictService {

    public static final String DICT_ROOM_TYPE = "ROOM_TYPE";
    public static final String DICT_RISK_LEVEL = "RISK_LEVEL";
    public static final String DICT_PHOTO_CATEGORY = "PHOTO_CATEGORY";
    public static final String DICT_PLAN_TYPE = "PLAN_TYPE";
    public static final String DICT_FREQUENCY = "FREQUENCY";

    private static final Pattern TYPE_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]{0,63}$");

    private final CpsDictMapper dictMapper;
    private final CpsDictTypeMapper typeMapper;

    public CpsDictService(CpsDictMapper dictMapper, CpsDictTypeMapper typeMapper) {
        this.dictMapper = dictMapper;
        this.typeMapper = typeMapper;
    }

    // ---------- 条目 ----------

    public List<CpsDict> list(String typeCode, Boolean enabled) {
        return dictMapper.findAll(typeCode, enabled);
    }

    public CpsDict getDetail(Long id) {
        return dictMapper.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Dict item not found: " + id));
    }

    /** 校验值是否为该组启用项（供房间/点检项等服务调用；空值返回 false）。 */
    public boolean valueExists(String typeCode, String value) {
        if (typeCode == null || typeCode.trim().isEmpty() || value == null || value.trim().isEmpty()) {
            return false;
        }
        return dictMapper.countEnabled(typeCode, value) > 0;
    }

    @Transactional
    public CpsDict save(Long id, CpsDictRequest request, String operatorEmpNo) {
        String typeCode = request.getDictType() == null ? "" : request.getDictType().trim();
        if (typeCode.isEmpty()) {
            throw new IllegalArgumentException("Dict dictType is required");
        }
        requireText(request.getItemValue(), "itemValue");
        requireText(request.getItemLabel(), "itemLabel");

        CpsDictType type = typeMapper.findByCode(typeCode)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Dict type not found: " + typeCode + "（请先在字典管理中创建该类型）"));

        CpsDict existing = id == null
                ? null
                : dictMapper.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Dict item not found: " + id));
        Optional<CpsDict> sameKey = dictMapper.findByTypeAndValue(typeCode, request.getItemValue().trim());
        long selfId = existing == null ? -1L : existing.getId();
        if (sameKey.isPresent() && !sameKey.get().getId().equals(selfId)) {
            throw new IllegalStateException("Dict item already exists: " + typeCode + "/" + request.getItemValue());
        }

        boolean builtin = existing != null && Boolean.TRUE.equals(existing.getBuiltin());
        if (builtin) {
            if (!typeCode.equals(existing.getTypeCode())) {
                throw new IllegalArgumentException("builtin dict item cannot change dictType: " + existing.getTypeCode());
            }
            if (!request.getItemValue().trim().equals(existing.getItemValue())) {
                throw new IllegalArgumentException("builtin dict item cannot change itemValue: " + existing.getItemValue());
            }
            if (request.getEnabled() != null && !request.getEnabled()) {
                throw new IllegalArgumentException("builtin dict item cannot be disabled: " + existing.getItemValue());
            }
        }
        if (existing == null && !Boolean.TRUE.equals(type.getEnabled())) {
            throw new IllegalArgumentException("dict type is disabled: " + typeCode + "（请先启用类型）");
        }

        CpsDict item = existing == null ? new CpsDict() : existing;
        if (existing == null) {
            item.setTypeId(type.getId());
            item.setItemValue(request.getItemValue().trim());
            item.setBuiltin(false);
            item.setCreatedBy(operatorEmpNo);
        }
        item.setItemLabel(request.getItemLabel().trim());
        item.setSortNo(request.getSortNo() == null ? 0 : request.getSortNo());
        item.setEnabled(request.getEnabled() == null || request.getEnabled());
        item.setRemark(request.getRemark());
        item.setUpdatedBy(operatorEmpNo);

        if (existing == null) {
            dictMapper.insert(item);
        } else {
            dictMapper.update(item);
        }
        return getDetail(item.getId());
    }

    @Transactional
    public void setEnabled(Long id, Boolean enabled, String operatorEmpNo) {
        CpsDict existing = getDetail(id);
        if (Boolean.TRUE.equals(existing.getBuiltin()) && Boolean.FALSE.equals(enabled)) {
            throw new IllegalArgumentException("builtin dict item cannot be disabled: " + existing.getItemValue());
        }
        requireUpdatedRows(dictMapper.setEnabled(id, enabled, operatorEmpNo), "Dict item", id);
    }

    @Transactional
    public void delete(Long id) {
        CpsDict existing = getDetail(id);
        if (Boolean.TRUE.equals(existing.getBuiltin())) {
            throw new IllegalArgumentException("builtin dict item cannot be deleted: " + existing.getItemValue());
        }
        requireUpdatedRows(dictMapper.deleteById(id), "Dict item", id);
    }

    // ---------- 类型 ----------

    public List<CpsDictType> listTypes(Boolean enabled) {
        return typeMapper.findAll(enabled);
    }

    public CpsDictType getTypeDetail(Long id) {
        return typeMapper.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Dict type not found: " + id));
    }

    @Transactional
    public CpsDictType saveType(Long id, CpsDictTypeRequest request, String operatorEmpNo) {
        String typeCode = request.getTypeCode() == null ? "" : request.getTypeCode().trim();
        requireText(request.getTypeName(), "typeName");

        CpsDictType existing = id == null
                ? null
                : typeMapper.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Dict type not found: " + id));

        if (existing == null) {
            if (typeCode.isEmpty()) {
                throw new IllegalArgumentException("Dict typeCode is required");
            }
            if (!TYPE_PATTERN.matcher(typeCode).matches()) {
                throw new IllegalArgumentException("typeCode must match ^[A-Z][A-Z0-9_]*$ (up to 64): " + typeCode);
            }
            if (typeMapper.findByCode(typeCode).isPresent()) {
                throw new IllegalStateException("Dict type already exists: " + typeCode);
            }
        }

        boolean builtin = existing != null && Boolean.TRUE.equals(existing.getBuiltin());
        if (builtin) {
            if (!typeCode.isEmpty() && !typeCode.equals(existing.getTypeCode())) {
                throw new IllegalArgumentException("builtin dict type cannot change typeCode: " + existing.getTypeCode());
            }
            if (request.getEnabled() != null && !request.getEnabled()) {
                throw new IllegalArgumentException("builtin dict type cannot be disabled: " + existing.getTypeCode());
            }
        }

        CpsDictType type = existing == null ? new CpsDictType() : existing;
        if (existing == null) {
            type.setTypeCode(typeCode);
            type.setBuiltin(false);
            type.setCreatedBy(operatorEmpNo);
        }
        type.setTypeName(request.getTypeName().trim());
        type.setDescription(request.getDescription());
        type.setSortNo(request.getSortNo() == null ? 0 : request.getSortNo());
        type.setEnabled(request.getEnabled() == null || request.getEnabled());
        type.setUpdatedBy(operatorEmpNo);

        if (existing == null) {
            typeMapper.insert(type);
        } else {
            typeMapper.update(type);
        }
        return getTypeDetail(type.getId());
    }

    @Transactional
    public void setTypeEnabled(Long id, Boolean enabled, String operatorEmpNo) {
        CpsDictType existing = getTypeDetail(id);
        if (Boolean.TRUE.equals(existing.getBuiltin()) && Boolean.FALSE.equals(enabled)) {
            throw new IllegalArgumentException("builtin dict type cannot be disabled: " + existing.getTypeCode());
        }
        requireUpdatedRows(typeMapper.setEnabled(id, enabled, operatorEmpNo), "Dict type", id);
    }

    @Transactional
    public void deleteType(Long id) {
        CpsDictType existing = getTypeDetail(id);
        if (Boolean.TRUE.equals(existing.getBuiltin())) {
            throw new IllegalArgumentException("builtin dict type cannot be deleted: " + existing.getTypeCode());
        }
        if (dictMapper.countByTypeId(id) > 0) {
            throw new IllegalStateException("dict type still has items: " + existing.getTypeCode() + "（请先删除或迁移其条目）");
        }
        requireUpdatedRows(typeMapper.deleteById(id), "Dict type", id);
    }

    // ---------- 公共 ----------

    private static void requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Dict " + field + " is required");
        }
    }

    private static void requireUpdatedRows(int rows, String entity, Long id) {
        if (rows != 1) {
            throw new IllegalStateException(entity + " not found or not updated: " + id);
        }
    }
}
