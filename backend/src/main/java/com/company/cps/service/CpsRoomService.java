package com.company.cps.service;

import com.company.cps.domain.CpsRoom;
import com.company.cps.dto.CpsRoomRequest;
import com.company.cps.mapper.CpsRoomMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * B1 辅房基础配置（PRD §23.1）：辅房字段全集 + 责任人/经理（区域人员挂在房间行上）。
 */
@Service
public class CpsRoomService {

    private static final Set<String> RISK_LEVELS = new HashSet<>(Arrays.asList("HIGH", "MEDIUM", "LOW"));

    private final CpsRoomMapper roomMapper;

    public CpsRoomService(CpsRoomMapper roomMapper) {
        this.roomMapper = roomMapper;
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

    private static void validate(CpsRoomRequest request) {
        requireText(request.getRoomCode(), "roomCode");
        requireText(request.getBuilding(), "building");
        requireText(request.getDoorNo(), "doorNo");
        requireText(request.getRoomName(), "roomName");
        requireText(request.getRoomType(), "roomType");
        requireText(request.getKeeperEmpNo(), "keeperEmpNo");
        requireText(request.getKeeperEmpName(), "keeperEmpName");
        if (request.getRiskLevel() != null && !RISK_LEVELS.contains(request.getRiskLevel())) {
            throw new IllegalArgumentException("riskLevel must be one of HIGH/MEDIUM/LOW: " + request.getRiskLevel());
        }
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
