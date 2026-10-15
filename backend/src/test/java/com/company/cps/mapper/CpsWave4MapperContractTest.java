package com.company.cps.mapper;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 波次4 B3/B4 mapper XML 契约：快照列全集 + 判定回写/排名聚合关键子句。 */
class CpsWave4MapperContractTest {

    private static String readXml(String fileName) throws IOException {
        return new String(Files.readAllBytes(
                Paths.get("src/main/resources/mapper", fileName)), StandardCharsets.UTF_8);
    }

    @Test
    void recordMapperSnapshotsTaskAndRoomColumns() throws IOException {
        String sql = readXml("CpsRoomCheckRecordMapper.xml");
        for (String column : new String[]{
                "plan_task_id", "plan_id", "room_id", "room_code", "room_name",
                "check_emp_no", "check_emp_name", "record_status", "judge_status",
                "score", "started_at", "submitted_at"}) {
            assertTrue(sql.contains(column), "missing record column: " + column);
        }
    }

    @Test
    void recordMapperWeeklyAggregationFollowsD08() throws IOException {
        String sql = readXml("CpsRoomCheckRecordMapper.xml");
        // B4：仅已判定成功记录计分（降级 PENDING 不入榜）；SUM 总分降序；同分按最早提交升序（D-08）
        assertTrue(sql.contains("SUM(score)"));
        assertTrue(sql.contains("record_status = 'JUDGED'"));
        assertTrue(sql.contains("judge_status = 'SUCCESS'"));
        assertTrue(sql.contains("ORDER BY totalScore DESC, firstSubmittedAt ASC, roomCode ASC"));
        assertTrue(sql.contains("submitted_at &gt;= #{from}"));
        assertTrue(sql.contains("submitted_at &lt; #{to}"));
    }

    @Test
    void recordMapperUpdateJudgeResultGuardsPendingOrInProgress() throws IOException {
        String sql = readXml("CpsRoomCheckRecordMapper.xml");
        // 已判定单锁定：仅 待执行/执行中 可推进（PRD 24.2.4 提交后锁定）
        assertTrue(sql.contains("record_status IN ('PENDING', 'IN_PROGRESS')"));
    }

    @Test
    void itemMapperSnapshotsCheckItemConfig() throws IOException {
        String sql = readXml("CpsRoomCheckRecordItemMapper.xml");
        for (String column : new String[]{
                "record_id", "room_id", "check_item_id", "item_code", "content",
                "photo_category", "deduct_score", "config_version",
                "photo_object_key", "photo_file_name",
                "judge_result", "judge_reason", "final_result", "judged_at"}) {
            assertTrue(sql.contains(column), "missing item column: " + column);
        }
    }

    @Test
    void taskMapperCheckTaskListFiltersOwnerAndNotCancelled() throws IOException {
        String sql = readXml("CpsInspectionPlanTaskMapper.xml");
        assertTrue(sql.contains("target_emp_no IS NULL OR target_emp_no = #{targetEmpNo}"));
        assertTrue(sql.contains("task_status != 'CANCELLED'"));
        assertTrue(sql.contains("task_type = #{taskType}"));
    }
}
