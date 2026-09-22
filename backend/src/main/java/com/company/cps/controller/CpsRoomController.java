package com.company.cps.controller;

import com.company.cps.domain.CpsRoom;
import com.company.cps.dto.CpsEnabledRequest;
import com.company.cps.dto.CpsRoomRequest;
import com.company.cps.service.CpsRoomService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** B1 辅房基础配置维护（PRD §23.1）。 */
@RestController
@RequestMapping("/api/cps/admin/rooms")
public class CpsRoomController {

    private final CpsRoomService roomService;

    public CpsRoomController(CpsRoomService roomService) {
        this.roomService = roomService;
    }

    @GetMapping
    public List<CpsRoom> list(
            @RequestParam(required = false) String factory,
            @RequestParam(required = false) String roomType,
            @RequestParam(required = false) Boolean enabled
    ) {
        return roomService.list(factory, roomType, enabled);
    }

    @GetMapping("/{id}")
    public CpsRoom detail(@PathVariable Long id) {
        return roomService.getDetail(id);
    }

    /** 新建或更新（id 为空=新建，body 内 roomCode 唯一）。 */
    @PostMapping
    public CpsRoom save(
            @RequestParam(required = false) Long id,
            @RequestBody CpsRoomRequest request,
            @RequestParam(defaultValue = "admin") String operatorEmpNo
    ) {
        return roomService.save(id, request, operatorEmpNo);
    }

    @PatchMapping("/{id}/enabled")
    public void setEnabled(@PathVariable Long id, @RequestBody CpsEnabledRequest request) {
        roomService.setEnabled(id, request.getEnabled(), request.getEmpNo());
    }
}
