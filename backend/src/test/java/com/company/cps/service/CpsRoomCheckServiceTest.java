package com.company.cps.service;

import com.company.cps.domain.CpsCheckItem;
import com.company.cps.domain.CpsInspectionPlanTask;
import com.company.cps.domain.CpsInspectionPlanTaskType;
import com.company.cps.domain.CpsRoom;
import com.company.cps.domain.CpsRoomCheckRecord;
import com.company.cps.domain.CpsRoomCheckRecordItem;
import com.company.cps.dto.CpsRoomCheckRecordResponse;
import com.company.cps.dto.CpsRoomCheckStartRequest;
import com.company.cps.mapper.CpsCheckItemMapper;
import com.company.cps.mapper.CpsInspectionPlanTaskMapper;
import com.company.cps.mapper.CpsRoomCheckRecordItemMapper;
import com.company.cps.mapper.CpsRoomCheckRecordMapper;
import com.company.cps.mapper.CpsRoomMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** B3 执行域：快照生成/幂等/范围校验/照片校验/降级 PENDING/计分与阻断。 */
class CpsRoomCheckServiceTest {

    private CpsRoomMapper roomMapper;
    private CpsCheckItemMapper checkItemMapper;
    private CpsRoomCheckRecordMapper recordMapper;
    private CpsRoomCheckRecordItemMapper itemMapper;
    private CpsInspectionPlanTaskMapper taskMapper;
    private CpsRoomCheckJudgeClient judgeClient;
    private RustFsStorageService storage;
    private CpsRoomCheckService service;

    @BeforeEach
    void setUp() {
        roomMapper = mock(CpsRoomMapper.class);
        checkItemMapper = mock(CpsCheckItemMapper.class);
        recordMapper = mock(CpsRoomCheckRecordMapper.class);
        itemMapper = mock(CpsRoomCheckRecordItemMapper.class);
        taskMapper = mock(CpsInspectionPlanTaskMapper.class);
        judgeClient = mock(CpsRoomCheckJudgeClient.class);
        storage = mock(RustFsStorageService.class);
        service = new CpsRoomCheckService(recordMapper, itemMapper, roomMapper,
                checkItemMapper, taskMapper, judgeClient, storage);
        when(storage.publicObjectUrl(anyString())).thenReturn("http://rustfs/x");
    }

    private CpsRoom room() {
        CpsRoom room = new CpsRoom();
        room.setId(5L);
        room.setRoomCode("R-01");
        room.setRoomName("配电间");
        room.setRoomType("配电室");
        room.setEnabled(true);
        return room;
    }

    private CpsInspectionPlanTask task(Long id, String empNo, String rooms) {
        CpsInspectionPlanTask task = new CpsInspectionPlanTask();
        task.setId(id);
        task.setTaskType(CpsInspectionPlanTaskType.INSPECT_CHECK);
        task.setTaskStatus("IN_PROGRESS");
        task.setTargetEmpNo(empNo);
        task.setReferenceObjectKey(rooms);
        return task;
    }

    private CpsCheckItem item(long id, String code, int deduct) {
        CpsCheckItem item = new CpsCheckItem();
        item.setId(id);
        item.setItemCode(code);
        item.setContent("无积水");
        item.setPhotoCategory("地面");
        item.setDeductScore(deduct);
        item.setStatus("APPLICABLE");
        item.setConfigVersion(3);
        item.setEnabled(true);
        return item;
    }

    private CpsRoomCheckRecord record(Long id, String status) {
        CpsRoomCheckRecord record = new CpsRoomCheckRecord();
        record.setId(id);
        record.setPlanTaskId(11L);
        record.setRoomId(5L);
        record.setRoomCode("R-01");
        record.setCheckEmpNo("E001");
        record.setRecordStatus(status);
        return record;
    }

    private CpsRoomCheckRecordItem itemRow(long id, long itemId, String key) {
        CpsRoomCheckRecordItem row = new CpsRoomCheckRecordItem();
        row.setId(id);
        row.setRecordId(9L);
        row.setCheckItemId(itemId);
        row.setItemCode("C" + itemId);
        row.setDeductScore(10);
        row.setPhotoObjectKey(key);
        return row;
    }

    @Test
    void startCreatesSnapshotOfApplicableItemsForRoom() {
        when(roomMapper.findById(5L)).thenReturn(Optional.of(room()));
        when(taskMapper.findById(11L)).thenReturn(task(11L, "E001", "R-01,R-02"));
        when(recordMapper.findActiveByTaskAndRoom(11L, 5L)).thenReturn(null);
        AtomicReference<CpsRoomCheckRecord> saved = new AtomicReference<>();
        when(recordMapper.insert(any(CpsRoomCheckRecord.class))).thenAnswer(inv -> {
            saved.set(inv.getArgument(0));
            saved.get().setId(9L);
            return 1;
        });
        when(recordMapper.findById(9L)).thenAnswer(inv -> saved.get());
        when(itemMapper.findByRecordId(9L)).thenReturn(new ArrayList<>());
        // C2 applicable_room_types 不含该房间类型 ⇒ 不进快照
        CpsCheckItem c2 = item(2, "C2", 10);
        c2.setApplicableRoomTypes("办公室");
        when(checkItemMapper.findAll(null, "APPLICABLE", true))
                .thenReturn(Arrays.asList(item(1, "C1", 10), c2));

        CpsRoomCheckStartRequest request = new CpsRoomCheckStartRequest();
        request.setRoomId(5L);
        request.setEmpNo("E001");
        request.setPlanTaskId(11L);
        CpsRoomCheckRecordResponse response = service.start(request);

        assertEquals(Long.valueOf(9L), response.getId());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CpsRoomCheckRecordItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(itemMapper).insertBatch(captor.capture());
        assertEquals(1, captor.getValue().size());
        assertEquals("C1", captor.getValue().get(0).getItemCode());
        assertEquals(Integer.valueOf(3), captor.getValue().get(0).getConfigVersion());
    }

    @Test
    void startIsIdempotentWhenActiveRecordExists() {
        when(roomMapper.findById(5L)).thenReturn(Optional.of(room()));
        when(taskMapper.findById(11L)).thenReturn(task(11L, "E001", "R-01"));
        CpsRoomCheckRecord existing = record(9L, "IN_PROGRESS");
        when(recordMapper.findActiveByTaskAndRoom(11L, 5L)).thenReturn(existing);
        when(recordMapper.findById(9L)).thenReturn(existing);
        when(itemMapper.findByRecordId(9L)).thenReturn(new ArrayList<>());

        CpsRoomCheckStartRequest request = new CpsRoomCheckStartRequest();
        request.setRoomId(5L);
        request.setEmpNo("E001");
        request.setPlanTaskId(11L);
        CpsRoomCheckRecordResponse response = service.start(request);

        assertEquals(Long.valueOf(9L), response.getId());
        verify(recordMapper, never()).insert(any(CpsRoomCheckRecord.class));
    }

    @Test
    void startRejectsRoomOutsideTaskScope() {
        when(roomMapper.findById(5L)).thenReturn(Optional.of(room()));
        when(taskMapper.findById(11L)).thenReturn(task(11L, "E001", "R-09")); // 房间不在 CSV

        CpsRoomCheckStartRequest request = new CpsRoomCheckStartRequest();
        request.setRoomId(5L);
        request.setEmpNo("E001");
        request.setPlanTaskId(11L);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.start(request));
        assertTrue(error.getMessage().contains("does not cover room"));
    }

    @Test
    void uploadPhotoStoresObjectKeyAndAdvancesToInProgress() throws Exception {
        when(recordMapper.findById(9L)).thenReturn(record(9L, "PENDING"));
        when(itemMapper.findById(77L)).thenReturn(itemRow(77L, 1, "old.jpg"));
        when(itemMapper.findByRecordId(9L))
                .thenReturn(new ArrayList<>(Arrays.asList(itemRow(77L, 1, "old.jpg"))));

        service.uploadPhoto(9L, 77L,
                new MockMultipartFile("file", "p.jpg", "image/jpeg", new byte[]{1}), "E001");

        verify(storage).put(anyString(), any(byte[].class), eq("image/jpeg"));
        verify(itemMapper).updatePhoto(eq(77L), anyString(), eq("p.jpg"));
        verify(recordMapper).updateStatusFields(eq(9L), eq("IN_PROGRESS"), any());
    }

    @Test
    void submitBlocksWhenPhotoMissing() {
        when(recordMapper.findById(9L)).thenReturn(record(9L, "IN_PROGRESS"));
        when(itemMapper.findByRecordId(9L)).thenReturn(
                Arrays.asList(itemRow(77L, 1, "a.jpg"), itemRow(78L, 2, null)));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.submit(9L, "E001"));
        assertTrue(error.getMessage().contains("photo evidence missing"));
        verify(judgeClient, never()).judge(any(), anyList());
    }

    @Test
    void submitDegradesToPendingWhenJudgeUnavailable() {
        CpsRoomCheckRecord record = record(9L, "IN_PROGRESS");
        when(recordMapper.findById(9L)).thenReturn(record);
        when(itemMapper.findByRecordId(9L)).thenReturn(new ArrayList<>(
                Arrays.asList(itemRow(77L, 1, "a.jpg"))));
        when(judgeClient.judge(any(), anyList())).thenReturn(null); // Python 未起
        when(taskMapper.findById(11L)).thenReturn(task(11L, "E001", "R-01"));
        when(recordMapper.findByPlanTaskId(11L))
                .thenReturn(new ArrayList<>(Arrays.asList(record)));

        CpsRoomCheckRecordResponse response = service.submit(9L, "E001");

        verify(itemMapper).updateJudgeResult(eq(77L), eq("PENDING"), anyString(), eq("PENDING"), any());
        ArgumentCaptor<CpsRoomCheckRecord> captor = ArgumentCaptor.forClass(CpsRoomCheckRecord.class);
        verify(recordMapper).updateJudgeResult(captor.capture());
        assertEquals("PENDING", captor.getValue().getJudgeStatus());
        assertNull(captor.getValue().getScore());
        assertEquals("JUDGED", response.getRecordStatus()); // 降级不阻塞：单仍推进到已判定
    }

    @Test
    void submitScores100MinusFailedDeducts() {
        CpsRoomCheckRecord record = record(9L, "IN_PROGRESS");
        record.setPlanTaskId(null); // 任务进度刷新跳过（本用例聚焦计分）
        when(recordMapper.findById(9L)).thenReturn(record);
        List<CpsRoomCheckRecordItem> rows = new ArrayList<>();
        rows.add(itemRow(77L, 1, "a.jpg"));
        rows.add(itemRow(78L, 2, "b.jpg"));
        rows.add(itemRow(79L, 3, "c.jpg"));
        when(itemMapper.findByRecordId(9L)).thenReturn(rows);
        when(judgeClient.judge(any(), anyList())).thenReturn(Arrays.asList(
                new CpsRoomCheckJudgeClient.ItemJudge(1L, "PASS", null),
                new CpsRoomCheckJudgeClient.ItemJudge(2L, "FAIL", "积水"),
                new CpsRoomCheckJudgeClient.ItemJudge(3L, "FAIL", "堆物")));

        CpsRoomCheckRecordResponse response = service.submit(9L, "E001");

        ArgumentCaptor<CpsRoomCheckRecord> captor = ArgumentCaptor.forClass(CpsRoomCheckRecord.class);
        verify(recordMapper).updateJudgeResult(captor.capture());
        assertEquals(Integer.valueOf(80), captor.getValue().getScore());
        assertEquals("SUCCESS", captor.getValue().getJudgeStatus());
        assertEquals(Integer.valueOf(80), response.getScore());
        assertEquals("JUDGED", response.getRecordStatus());
    }

    @Test
    void submitRequiresRetakeOnTypeMismatch() {
        when(recordMapper.findById(9L)).thenReturn(record(9L, "IN_PROGRESS"));
        when(itemMapper.findByRecordId(9L)).thenReturn(new ArrayList<>(
                Arrays.asList(itemRow(77L, 1, "a.jpg"))));
        when(judgeClient.judge(any(), anyList())).thenReturn(Arrays.asList(
                new CpsRoomCheckJudgeClient.ItemJudge(1L, "TYPE_MISMATCH", "拍到桌面")));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.submit(9L, "E001"));
        assertTrue(error.getMessage().contains("retake required"));
        verify(recordMapper, never()).updateJudgeResult(any(CpsRoomCheckRecord.class));
    }

    @Test
    void uploadPhotoRejectedAfterJudged() throws Exception {
        when(recordMapper.findById(9L)).thenReturn(record(9L, "JUDGED"));

        assertThrows(IllegalArgumentException.class, () -> service.uploadPhoto(9L, 77L,
                new MockMultipartFile("file", "p.jpg", "image/jpeg", new byte[]{1}), "E001"));
    }

    @Test
    void recordDetailRestrictedToOwner() {
        CpsRoomCheckRecord record = record(9L, "IN_PROGRESS");
        record.setCheckEmpNo("E002");
        when(recordMapper.findById(9L)).thenReturn(record);
        assertThrows(IllegalArgumentException.class, () -> service.getRecord(9L, "E001"));
    }
}
