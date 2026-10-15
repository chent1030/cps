package com.company.cps.controller;

import com.company.cps.dto.CpsRoomCheckRecordResponse;
import com.company.cps.dto.CpsRoomCheckStartRequest;
import com.company.cps.dto.CpsRoomCheckTaskResponse;
import com.company.cps.service.CpsRoomCheckService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/** B3 辅房点检执行域 mobile 端点（PRD §22-§24）。 */
@RestController
@RequestMapping("/api/cps/room-checks")
public class CpsRoomCheckController {

    private final CpsRoomCheckService roomCheckService;

    public CpsRoomCheckController(CpsRoomCheckService roomCheckService) {
        this.roomCheckService = roomCheckService;
    }

    /** 点检任务列表（本人 INSPECT_CHECK 任务，含覆盖房间与完成进度）。 */
    @GetMapping("/tasks")
    public List<CpsRoomCheckTaskResponse> tasks(
            @RequestParam String empNo,
            @RequestParam(required = false) String status) {
        return roomCheckService.listTasks(empNo, status);
    }

    /** 开启点检：按 房间×点检项 生成明细快照（幂等，重复开启返回原单）。 */
    @PostMapping("/start")
    public CpsRoomCheckRecordResponse start(@RequestBody CpsRoomCheckStartRequest request) {
        return roomCheckService.start(request);
    }

    /** 点检单详情（含明细与照片 URL）。 */
    @GetMapping("/records/{id}")
    public CpsRoomCheckRecordResponse record(@PathVariable Long id, @RequestParam String empNo) {
        return roomCheckService.getRecord(id, empNo);
    }

    /** 照片提交（multipart，关联 RustFS object_key；重拍覆盖）。 */
    @PostMapping("/records/{id}/items/{itemId}/photo")
    public CpsRoomCheckRecordResponse uploadPhoto(
            @PathVariable Long id,
            @PathVariable Long itemId,
            @RequestParam String empNo,
            @RequestParam("file") MultipartFile file) {
        return roomCheckService.uploadPhoto(id, itemId, file, empNo);
    }

    /** 提交并同步判定：成功计分锁定；类型不符/无法判定阻断须补拍；服务未起降级 PENDING 不阻塞。 */
    @PostMapping("/records/{id}/submit")
    public CpsRoomCheckRecordResponse submit(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return roomCheckService.submit(id, body.get("empNo"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> badRequest(IllegalArgumentException error) {
        return ResponseEntity.badRequest().body(error.getMessage());
    }
}
