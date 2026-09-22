package com.company.cps.service;

import com.company.cps.domain.CpsCheckItem;
import com.company.cps.dto.CpsCheckItemRequest;
import com.company.cps.mapper.CpsCheckItemMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * B2 辅房点检项配置（PRD §23.2）：字段全集 + 配置版本（更新自动递增，XML 内实现）。
 * 扣分口径：单次辅房得分 = 100 − 不合格适用项扣分和（§24.3，不适用不扣分）。
 */
@Service
public class CpsCheckItemService {

    private static final Set<String> STATUSES = new HashSet<>(Arrays.asList("APPLICABLE", "NOT_APPLICABLE"));

    private final CpsCheckItemMapper checkItemMapper;

    public CpsCheckItemService(CpsCheckItemMapper checkItemMapper) {
        this.checkItemMapper = checkItemMapper;
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

    @Transactional
    public void setEnabled(Long id, Boolean enabled, String operatorEmpNo) {
        int rows = checkItemMapper.setEnabled(id, enabled, operatorEmpNo);
        if (rows != 1) {
            throw new IllegalStateException("Check item not found or not updated: " + id);
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Check item " + field + " is required");
        }
    }
}
