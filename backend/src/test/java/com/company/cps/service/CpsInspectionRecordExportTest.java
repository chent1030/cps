package com.company.cps.service;

import com.company.cps.domain.CpsInspectionRecord;
import com.company.cps.domain.CpsInspectionType;
import com.company.cps.domain.CpsIssueAttachment;
import com.company.cps.mapper.CpsCheckItemMapper;
import com.company.cps.mapper.CpsInspectionAreaMapper;
import com.company.cps.mapper.CpsInspectionPlanMapper;
import com.company.cps.mapper.CpsInspectionPlanTaskMapper;
import com.company.cps.mapper.CpsInspectionRecordItemMapper;
import com.company.cps.mapper.CpsInspectionRecordLogMapper;
import com.company.cps.mapper.CpsInspectionRecordMapper;
import com.company.cps.mapper.CpsInspectionTypeMapper;
import com.company.cps.mapper.CpsIssueAttachmentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 非 CPS 巡检记录导出：列顺序、状态/AI 结论中文化、整改照片地址拼接。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CpsInspectionRecordExportTest {

    @Mock private CpsInspectionRecordMapper recordMapper;
    @Mock private CpsInspectionRecordItemMapper itemMapper;
    @Mock private CpsInspectionAreaMapper areaMapper;
    @Mock private CpsInspectionTypeMapper typeMapper;
    @Mock private CpsCheckItemMapper checkItemMapper;
    @Mock private CpsInspectionPlanTaskMapper planTaskMapper;
    @Mock private CpsInspectionPlanMapper planMapper;
    @Mock private CpsInspectionRecordLogMapper recordLogMapper;
    @Mock private CpsIssueAttachmentMapper attachmentMapper;
    @Mock private CpsInspectionJudgeClient judgeClient;
    @Mock private CpsAgentFrameworkClient agentClient;

    @InjectMocks private CpsInspectionRecordService service;

    @BeforeEach
    void setUp() {
        CpsInspectionType type = new CpsInspectionType();
        type.setCode("ROOM_CHECK");
        type.setName("辅房点检");
        when(typeMapper.findByCode("ROOM_CHECK")).thenReturn(type);

        CpsIssueAttachment photo = new CpsIssueAttachment();
        photo.setId(11L);
        photo.setFileUrl("http://127.0.0.1:9000/cps-attachments/cps/a.png");
        when(attachmentMapper.findById(11L)).thenReturn(Optional.of(photo));
        when(attachmentMapper.findById(12L)).thenReturn(Optional.empty());
    }

    private static CpsInspectionRecord record(String status, String aiOverall) {
        CpsInspectionRecord r = new CpsInspectionRecord();
        r.setId(77L);
        r.setTypeCode("ROOM_CHECK");
        r.setAreaName("配电间J1");
        r.setAreaCode("JX-01");
        r.setCheckEmpName("张三");
        r.setCheckEmpNo("61005029");
        r.setRecordStatus(status);
        r.setAiOverall(aiOverall);
        r.setAiReason("AI 认为 2 项不合格");
        r.setScore(90);
        r.setSubmittedAt(LocalDateTime.of(2026, 10, 28, 17, 30, 5));
        r.setReviewEmpName("李四");
        r.setReviewedAt(LocalDateTime.of(2026, 10, 28, 18, 0, 0));
        r.setReviewOpinion("同意进入整改");
        r.setRectifyEmpName("王五");
        r.setRectifySubmittedAt(LocalDateTime.of(2026, 10, 28, 19, 0, 0));
        r.setRectifyReason("阀门渗漏");
        r.setRectifyShortTerm("更换密封圈");
        r.setRectifyLongTerm("季度点检纳入计划");
        r.setRectifyAttachmentIds("11,12");
        return r;
    }

    @Test
    void exportRowsUsesChineseLabelsAndPhotoUrls() {
        when(recordMapper.findPage("ROOM_CHECK", 5L, "PENDING_REVIEW", null, "J1", 10000, 0))
                .thenReturn(Collections.singletonList(record("PENDING_REVIEW", "PASS")));

        List<String[]> rows = service.exportRows("ROOM_CHECK", 5L, "PENDING_REVIEW", null, "J1");

        assertEquals(1, rows.size());
        assertArrayEquals(new String[] {
                "77", "辅房点检", "配电间J1", "张三", "待复核", "2026-10-28 17:30:05", "90",
                "通过", "AI 认为 2 项不合格", "李四", "2026-10-28 18:00:00", "同意进入整改",
                "王五", "2026-10-28 19:00:00", "阀门渗漏", "更换密封圈", "季度点检纳入计划",
                "http://127.0.0.1:9000/cps-attachments/cps/a.png"
        }, rows.get(0));
        verify(recordMapper).findPage("ROOM_CHECK", 5L, "PENDING_REVIEW", null, "J1", 10000, 0);
    }

    @Test
    void exportRowsToleratesMissingOptionalFields() {
        CpsInspectionRecord bare = new CpsInspectionRecord();
        bare.setId(78L);
        bare.setTypeCode("UNKNOWN_TYPE");
        bare.setRecordStatus("IN_PROGRESS");
        when(recordMapper.findPage(null, null, null, null, null, 10000, 0))
                .thenReturn(Collections.singletonList(bare));

        List<String[]> rows = service.exportRows(null, null, null, null, null);

        assertEquals(1, rows.size());
        assertArrayEquals(new String[] {
                "78", "UNKNOWN_TYPE", "", "", "执行中", "", "", "", "", "", "", "", "", "", "", "", "", ""
        }, rows.get(0));
    }

    @Test
    void exportRowsMapsAllStatusAndVerdictLabels() {
        List<CpsInspectionRecord> records = Arrays.asList(
                record("PENDING", "PARTIAL"), record("PENDING_RECTIFY", "PROBLEM"), record("JUDGED", null));
        when(recordMapper.findPage(null, null, null, null, null, 10000, 0)).thenReturn(records);

        List<String[]> rows = service.exportRows(null, null, null, null, null);

        assertEquals("待执行", rows.get(0)[4]);
        assertEquals("部分通过", rows.get(0)[7]);
        assertEquals("待整改", rows.get(1)[4]);
        assertEquals("有问题", rows.get(1)[7]);
        assertEquals("已结束", rows.get(2)[4]);
        assertEquals("", rows.get(2)[7]);
    }
}
