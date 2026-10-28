package com.catlbattery.app.service.cps;

import com.catlbattery.domain.entity.cps.CpsRoom;
import com.catlbattery.domain.entity.cps.dto.CpsRoomRequest;
import com.catlbattery.infra.mapper.cps.CpsCheckItemMapper;
import com.catlbattery.infra.mapper.cps.CpsRoomMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * B1 辅房基础配置（PRD §23.1）：辅房字段全集 + 责任人/经理（区域人员挂在房间行上）。
 * roomType/riskLevel 自字典 cps_dict 校验（ROOM_TYPE/RISK_LEVEL 启用项，V20261015）。
 * checkItemIds：房间独立配置的点检项编号（逗号分隔 itemCode，V20261017），保存时校验存在性；
 * 快照优先用它精确圈定（见 CpsRoomCheckService），空则回退 applicable_room_types 房型匹配。
 */
@Service
public class CpsRoomService {

    private final CpsRoomMapper roomMapper;
    private final CpsDictService dictService;
    private final CpsCheckItemMapper checkItemMapper;

    public CpsRoomService(CpsRoomMapper roomMapper, CpsDictService dictService, CpsCheckItemMapper checkItemMapper) {
        this.roomMapper = roomMapper;
        this.dictService = dictService;
        this.checkItemMapper = checkItemMapper;
    }

    public List<CpsRoom> list(String factory, String roomType, Boolean enabled) {
        return roomMapper.findAll(factory, roomType, enabled);
    }

    public CpsRoom getDetail(Long id) {
        return roomMapper.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Room not found: " + id));
    }

    @Transactional
    public CpsRoom save(Long id, CpsRoomRequest request, String operatorEmpNo) {
        validate(request);

        CpsRoom room = (id == null) ? new CpsRoom() : loadForUpdate(id);
        Optional<CpsRoom> sameCode = roomMapper.findByCode(request.getRoomCode());
        if (sameCode.isPresent() && !sameCode.get().getId().equals(room.getId() == null ? -1L : room.getId())) {
            throw new IllegalStateException("Room code already exists: " + request.getRoomCode());
        }

        room.setRoomCode(request.getRoomCode());
        room.setBuilding(request.getBuilding());
        room.setDoorNo(request.getDoorNo());
        room.setRoomName(request.getRoomName());
        room.setRoomType(request.getRoomType());
        room.setRiskLevel(request.getRiskLevel() == null ? "LOW" : request.getRiskLevel());
        room.setCheckItemIds(normaliseCheckItemIds(request.getCheckItemIds()));
        room.setDeptName(request.getDeptName());
        room.setBaseCode(request.getBaseCode());
        room.setFactory(request.getFactory());
        room.setKeeperEmpNo(request.getKeeperEmpNo());
        room.setKeeperEmpName(request.getKeeperEmpName());
        room.setKeeperManagerEmpNo(request.getKeeperManagerEmpNo());
        room.setKeeperManagerEmpName(request.getKeeperManagerEmpName());
        room.setEnabled(request.getEnabled() == null || request.getEnabled());

        if (id == null) {
            room.setCreatedBy(operatorEmpNo);
            room.setUpdatedBy(operatorEmpNo);
            roomMapper.insert(room);
        } else {
            room.setUpdatedBy(operatorEmpNo);
            roomMapper.update(room);
        }
        return getDetail(room.getId());
    }

    @Transactional
    public void setEnabled(Long id, Boolean enabled, String operatorEmpNo) {
        requireUpdatedRows(roomMapper.setEnabled(id, enabled, operatorEmpNo), "Room", id);
    }

    private CpsRoom loadForUpdate(Long id) {
        CpsRoom room = roomMapper.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Room not found: " + id));
        return room;
    }

    private void validate(CpsRoomRequest request) {
        requireText(request.getRoomCode(), "roomCode");
        requireText(request.getBuilding(), "building");
        requireText(request.getDoorNo(), "doorNo");
        requireText(request.getRoomName(), "roomName");
        requireText(request.getRoomType(), "roomType");
        requireText(request.getKeeperEmpNo(), "keeperEmpNo");
        requireText(request.getKeeperEmpName(), "keeperEmpName");
        String riskLevel = request.getRiskLevel() == null ? "LOW" : request.getRiskLevel();
        if (!dictService.valueExists(CpsDictService.DICT_RISK_LEVEL, riskLevel)) {
            throw new IllegalArgumentException(
                    "riskLevel not in enabled dict RISK_LEVEL: " + riskLevel + "（请在字典管理中启用或新增）");
        }
        if (!dictService.valueExists(CpsDictService.DICT_ROOM_TYPE, request.getRoomType())) {
            throw new IllegalArgumentException(
                    "roomType not in enabled dict ROOM_TYPE: " + request.getRoomType() + "（请在字典管理中启用或新增）");
        }
    }

    /** 规整 checkItemIds：逗号 split、去空白、去空片段、保序去重后回拼；全空返回 null。 */
    private String normaliseCheckItemIds(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        List<String> codes = new ArrayList<>();
        for (String piece : raw.split(",")) {
            String code = piece.trim();
            if (!code.isEmpty() && !codes.contains(code)) {
                codes.add(code);
            }
        }
        if (codes.isEmpty()) {
            return null;
        }
        for (String code : codes) {
            if (!checkItemMapper.findByCode(code).isPresent()) {
                throw new IllegalArgumentException("check item not found: " + code + "（请先在点检项配置中创建）");
            }
        }
        return String.join(",", codes);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Room " + field + " is required");
        }
    }

    private static void requireUpdatedRows(int rows, String entity, Long id) {
        if (rows != 1) {
            throw new IllegalStateException(entity + " not found or not updated: " + id);
        }
    }
}
