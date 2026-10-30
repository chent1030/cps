package com.catlbattery.app.service.cps;

import com.catlbattery.domain.entity.cps.CpsCheckItem;
import com.catlbattery.domain.entity.cps.dto.CpsCheckItemRequest;
import com.catlbattery.infra.mapper.cps.CpsCheckItemMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.catlbattery.common.util.CpsStrings;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * B2 辅房点检项配置（PRD §23.2）：字段全集 + 配置版本（更新自动递增，XML 内实现）。
 * 扣分口径：单次辅房得分 = 100 − 不合格适用项扣分和（§24.3，不适用不扣分）。
 * photoCategory / applicableRoomTypes 逐值自字典 cps_dict 校验（PHOTO_CATEGORY / ROOM_TYPE 启用项）。
 */
@Service
public class CpsCheckItemService {

    private static final Set<String> STATUSES = new HashSet<>(Arrays.asList("APPLICABLE", "NOT_APPLICABLE"));

    private final CpsCheckItemMapper checkItemMapper;
    private final CpsDictService dictService;

    public CpsCheckItemService(CpsCheckItemMapper checkItemMapper, CpsDictService dictService) {
        this.checkItemMapper = checkItemMapper;
        this.dictService = dictService;
    }

    public List<CpsCheckItem> list(String photoCategory, String status, Boolean enabled) {
        return checkItemMapper.findAll(photoCategory, status, enabled);
    }

    public CpsCheckItem getDetail(Long id) {
        return checkItemMapper.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Check item not found: " + id));
    }

    @Transactional
    public CpsCheckItem save(Long id, CpsCheckItemRequest request, String operatorEmpNo) {
        requireText(request.getItemCode(), "itemCode");
        requireText(request.getContent(), "content");
        requireText(request.getPhotoCategory(), "photoCategory");
        if (request.getDeductScore() != null && request.getDeductScore() < 0) {
            throw new IllegalArgumentException("deductScore must be >= 0: " + request.getDeductScore());
        }
        String status = request.getStatus() == null ? "APPLICABLE" : request.getStatus();
        if (!STATUSES.contains(status)) {
            throw new IllegalArgumentException("status must be APPLICABLE or NOT_APPLICABLE: " + status);
        }
        if (!dictService.valueExists(CpsDictService.DICT_PHOTO_CATEGORY, request.getPhotoCategory())) {
            throw new IllegalArgumentException(
                    "photoCategory not in enabled dict PHOTO_CATEGORY: " + request.getPhotoCategory()
                            + "（请在字典管理中启用或新增）");
        }
        String applicableRoomTypes = request.getApplicableRoomTypes();
        if (applicableRoomTypes != null && !CpsStrings.isBlank(applicableRoomTypes)) {
            for (String part : applicableRoomTypes.split(",")) {
                String roomType = part.trim();
                if (!roomType.isEmpty() && !dictService.valueExists(CpsDictService.DICT_ROOM_TYPE, roomType)) {
                    throw new IllegalArgumentException(
                            "applicableRoomTypes contains value not in enabled dict ROOM_TYPE: " + roomType
                                    + "（请在字典管理中启用或新增）");
                }
            }
        }
        // Phase C1：点检类型多选校验（PASS_FAIL/TEXT/PHOTO；含 PHOTO 时最低张数必填 ≥1）
        String checkTypes = normalizeCheckTypes(request.getCheckTypes());
        if (checkTypes.contains("PHOTO")) {
            if (request.getPhotoMinCount() == null || request.getPhotoMinCount() < 1) {
                throw new IllegalArgumentException("点检类型含拍照时，最低张数必填且 ≥1");
            }
        }

        CpsCheckItem existing = id == null
                ? null
                : checkItemMapper.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Check item not found: " + id));
        Optional<CpsCheckItem> sameCode = checkItemMapper.findByCode(request.getItemCode());
        long selfId = existing == null ? -1L : existing.getId();
        if (sameCode.isPresent() && !sameCode.get().getId().equals(selfId)) {
            throw new IllegalStateException("Check item code already exists: " + request.getItemCode());
        }

        CpsCheckItem item = existing == null ? new CpsCheckItem() : existing;
        item.setItemCode(request.getItemCode());
        item.setContent(request.getContent());
        item.setCheckTypes(checkTypes);
        item.setPhotoMinCount(checkTypes.contains("PHOTO") ? request.getPhotoMinCount() : null);
        item.setPhotoCriteria(checkTypes.contains("PHOTO") ? trimToNull(request.getPhotoCriteria()) : null);
        item.setTextHint(checkTypes.contains("TEXT") ? trimToNull(request.getTextHint()) : null);
        item.setPhotoCategory(request.getPhotoCategory());
        item.setDeductScore(request.getDeductScore() == null ? 0 : request.getDeductScore());
        item.setStatus(status);
        item.setApplicableRoomTypes(request.getApplicableRoomTypes());
        item.setEnabled(request.getEnabled() == null || request.getEnabled());

        if (id == null) {
            item.setCreatedBy(operatorEmpNo);
            item.setUpdatedBy(operatorEmpNo);
            checkItemMapper.insert(item);
        } else {
            item.setUpdatedBy(operatorEmpNo);
            checkItemMapper.update(item);
        }
        return getDetail(item.getId());
    }

    /**
     * B8：删除点检项。被区域/房型/类型配置或历史执行记录引用时禁止删除（提示改为停用），
     * 避免配置删除后历史记录/覆盖率统计出现悬空 check_item_id。
     */
    @Transactional
    public void delete(Long id) {
        CpsCheckItem item = checkItemMapper.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Check item not found: " + id));
        long refs = checkItemMapper.countReferences(item.getId(), item.getItemCode());
        if (refs > 0) {
            throw new IllegalStateException("该点检项已被 " + refs + " 个巡检区域/历史记录引用，不能删除，请改为停用");
        }
        int rows = checkItemMapper.deleteById(id);
        if (rows != 1) {
            throw new IllegalStateException("Check item not found or not deleted: " + id);
        }
    }

    @Transactional
    public void setEnabled(Long id, Boolean enabled, String operatorEmpNo) {
        int rows = checkItemMapper.setEnabled(id, enabled, operatorEmpNo);
        if (rows != 1) {
            throw new IllegalStateException("Check item not found or not updated: " + id);
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || CpsStrings.isBlank(value)) {
            throw new IllegalArgumentException("Check item " + field + " is required");
        }
    }

    /** Phase C1：点检类型多选规范化。空 → 默认 PASS_FAIL；非法值报错；去重保序。 */
    private static String normalizeCheckTypes(String raw) {
        if (raw == null || CpsStrings.isBlank(raw)) {
            return "PASS_FAIL";
        }
        java.util.Set<String> allowed = new java.util.LinkedHashSet<>(java.util.Arrays.asList("PASS_FAIL", "TEXT", "PHOTO"));
        java.util.List<String> parts = new java.util.ArrayList<>();
        for (String part : raw.split(",")) {
            String v = part.trim().toUpperCase();
            if (v.isEmpty() || parts.contains(v)) continue;
            if (!allowed.contains(v)) {
                throw new IllegalArgumentException("checkTypes 非法值: " + v + "（允许 PASS_FAIL/TEXT/PHOTO）");
            }
            parts.add(v);
        }
        if (parts.isEmpty()) {
            throw new IllegalArgumentException("checkTypes 至少选择一项");
        }
        return String.join(",", parts);
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
