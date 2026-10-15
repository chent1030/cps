package com.company.cps.mapper;

import com.company.cps.controller.CpsVisionCheckController;
import com.company.cps.service.CpsVisionCheckService;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 波次12 B6 视觉点检后端契约（cps_vision_check_record + cps_vision_check_judge_event）：
 * - V20261006 迁移必须含两张表 + 列全集 + 索引 + UNIQUE(judge_fingerprint) + 状态/事件枚举 CHECK；
 * - mapper XML 暴露 6 个方法（含 countByFilters）；
 * - service 暴露 submit/judge/humanOverride/rejudge/list/findById；
 * - controller 暴露 POST/GET /api/cps/room-checks + override + rejudge。
 *
 * 与 CpsWave11EffectMapperContractTest 一致：纯字符串 + 反射契约校验，不依赖运行时 DB。
 */
class CpsWave12RoomCheckMapperContractTest {

    private static String readResource(String relativePath) throws IOException {
        return new String(Files.readAllBytes(Paths.get("src/main/resources", relativePath)), StandardCharsets.UTF_8);
    }

    @Test
    void v20261006MigrationContainsTwoTablesWithRequiredColumnsAndIndexes() throws IOException {
        String sql = readResource("db/migration/V20261006__cps_vision_check_record.sql");

        // 两张表
        assertTrue(sql.contains("CREATE TABLE cps_vision_check_record"),
                "V20261006 必须建 cps_vision_check_record");
        assertTrue(sql.contains("CREATE TABLE cps_vision_check_judge_event"),
                "V20261006 必须建 cps_vision_check_judge_event");

        // 主表列全集
        for (String column : Arrays.asList(
                "id BIGINT PRIMARY KEY AUTO_INCREMENT",
                "room_id BIGINT NOT NULL",
                "check_item_id BIGINT NOT NULL",
                "room_type VARCHAR(32) NOT NULL",
                "status VARCHAR(16) NOT NULL",
                "ai_overall VARCHAR(16) NULL",
                "ai_score INT NULL",
                "ai_reason VARCHAR(500) NULL",
                "photo_object_key VARCHAR(500) NULL",
                "photo_url VARCHAR(1000) NULL",
                "judge_fingerprint VARCHAR(64) NULL",
                "human_override_emp_no VARCHAR(40) NULL",
                "human_override_reason VARCHAR(500) NULL",
                "created_by VARCHAR(40) NULL",
                "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP",
                "updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP")) {
            assertTrue(sql.contains(column),
                    "cps_vision_check_record 必须含列: " + column);
        }

        // 索引（任务书要求 3 + judge_fingerprint 兜底索引）
        assertTrue(sql.contains("INDEX idx_cps_vision_check_room_created (room_id, created_at)"),
                "必须含 (room_id, created_at) 索引");
        assertTrue(sql.contains("INDEX idx_cps_vision_check_item_status (check_item_id, status)"),
                "必须含 (check_item_id, status) 索引");
        assertTrue(sql.contains("INDEX idx_cps_vision_check_status_updated (status, updated_at)"),
                "必须含 (status, updated_at) 索引");
        assertTrue(sql.contains("INDEX idx_cps_vision_check_fingerprint (judge_fingerprint)"),
                "必须含 judge_fingerprint 索引（幂等查询兜底）");

        // 状态/事件枚举 CHECK
        assertTrue(sql.contains("CONSTRAINT chk_cps_vision_check_status CHECK"),
                "status 必须有 CHECK 约束");
        assertTrue(sql.contains("'PENDING'") && sql.contains("'AI_JUDGING'")
                && sql.contains("'AI_PASS'") && sql.contains("'AI_FAIL'")
                && sql.contains("'HUMAN_OVERRIDE'") && sql.contains("'TIMEOUT'"),
                "status CHECK 必须含 6 状态枚举");
        assertTrue(sql.contains("CONSTRAINT chk_cps_vision_check_ai_overall CHECK")
                && sql.contains("'PASS'") && sql.contains("'PARTIAL'") && sql.contains("'PROBLEM'"),
                "ai_overall CHECK 必须含 PASS/PARTIAL/PROBLEM");
        assertTrue(sql.contains("CONSTRAINT chk_cps_vision_check_room_type CHECK"),
                "room_type CHECK 必须存在");
        assertTrue(sql.contains("CONSTRAINT chk_cps_vision_check_ai_score CHECK")
                && sql.contains("BETWEEN 0 AND 100"),
                "ai_score 必须 0-100 区间");

        // 事件表列 + 7 类型枚举 + 索引
        assertTrue(sql.contains("CONSTRAINT chk_cps_vision_check_event_type CHECK"),
                "事件表 event_type 必须有 CHECK 约束");
        assertTrue(sql.contains("'SUBMITTED'") && sql.contains("'AI_STARTED'")
                && sql.contains("'AI_COMPLETED'") && sql.contains("'AI_FAILED'")
                && sql.contains("'TIMEOUT_OPENED'") && sql.contains("'HUMAN_OVERRIDE'")
                && sql.contains("'REJUDGED'"),
                "事件类型 CHECK 必须含 7 枚举");
        assertTrue(sql.contains("INDEX idx_cps_vision_check_event_fp (judge_fingerprint, id)"),
                "事件表必须含 (judge_fingerprint, id) 索引");

        // 注释
        assertTrue(sql.contains("COMMENT='CPS视觉点检记录表（B6：房间×点检项目×照片）'"),
                "cps_vision_check_record 表注释缺失");
        assertTrue(sql.contains("COMMENT='CPS视觉点检事件流水（B6：12类状态转换流水）'"),
                "cps_vision_check_judge_event 表注释缺失");
    }

    @Test
    void recordMapperXmlExposesSixOpsAndBaseColumns() throws IOException {
        String sql = readResource("mapper/CpsVisionCheckRecordMapper.xml");

        // BaseColumns
        assertTrue(sql.contains("id=\"BaseColumns\""),
                "record mapper 必须有 BaseColumns sql 片段");
        assertTrue(sql.contains("<include refid=\"BaseColumns\"/>"),
                "record mapper 必须用 BaseColumns include");

        // 6 个核心方法
        for (String id : Arrays.asList("insert", "findById", "findJudgedByFingerprint",
                "listByFilters", "countByFilters", "updateAiJudgeResult")) {
            assertTrue(sql.contains("id=\"" + id + "\""),
                    "record mapper 必须暴露方法: " + id);
        }

        // 关键 SQL 模式
        assertTrue(sql.contains("useGeneratedKeys=\"true\""),
                "insert 必须 useGeneratedKeys 拿 PK");
        assertTrue(sql.contains("keyProperty=\"id\""),
                "insert 必须 keyProperty=id");
        assertTrue(sql.contains("INSERT INTO cps_vision_check_record"),
                "insert 必须指向 cps_vision_check_record");
        assertTrue(sql.contains("FROM cps_vision_check_record"),
                "查询必须指向 cps_vision_check_record");
        assertTrue(sql.contains("status IN ('AI_PASS', 'AI_FAIL', 'HUMAN_OVERRIDE')"),
                "findJudgedByFingerprint 必须以 status IN 已判过过滤");
        assertTrue(sql.contains("LIMIT #{limit} OFFSET #{offset}"),
                "listByFilters 必须支持 limit/offset 分页");
        assertTrue(sql.contains("AND status = 'AI_JUDGING'"),
                "updateAiJudgeResult 必须以 status=AI_JUDGING 做 CAS 守卫");
    }

    @Test
    void judgeEventMapperXmlExposesInsertAndFindByFingerprint() throws IOException {
        String sql = readResource("mapper/CpsVisionCheckJudgeEventMapper.xml");

        assertTrue(sql.contains("id=\"insert\""), "event mapper 必须暴露 insert");
        assertTrue(sql.contains("id=\"findByFingerprint\""),
                "event mapper 必须暴露 findByFingerprint");
        assertTrue(sql.contains("INSERT INTO cps_vision_check_judge_event"),
                "event insert 必须指向 cps_vision_check_judge_event");
        assertTrue(sql.contains("WHERE judge_fingerprint = #{fingerprint}"),
                "findByFingerprint 必须按 fingerprint 过滤");
        assertTrue(sql.contains("ORDER BY id ASC"),
                "事件查询必须按 id ASC 顺序回放");
    }

    @Test
    void visionCheckServiceAndControllerExposeAllRequiredSurface() throws Exception {
        // service 暴露 submit/judge/humanOverride/rejudge/list/findById
        CpsVisionCheckService.class.getMethod("submit",
                com.company.cps.dto.CpsVisionCheckSubmitRequest.class);
        CpsVisionCheckService.class.getMethod("judge", Long.class, String.class);
        CpsVisionCheckService.class.getMethod("humanOverride",
                Long.class, com.company.cps.dto.CpsVisionCheckOverrideRequest.class);
        CpsVisionCheckService.class.getMethod("rejudge", Long.class, String.class);
        CpsVisionCheckService.class.getMethod("list",
                com.company.cps.dto.CpsVisionCheckListFilter.class);
        CpsVisionCheckService.class.getMethod("findById", Long.class);

        // mapper 接口方法签名
        CpsVisionCheckRecordMapper.class.getMethod("insert",
                com.company.cps.domain.CpsVisionCheckRecord.class);
        CpsVisionCheckRecordMapper.class.getMethod("findById", Long.class);
        CpsVisionCheckRecordMapper.class.getMethod("findJudgedByFingerprint", String.class);
        CpsVisionCheckRecordMapper.class.getMethod("listByFilters",
                Long.class, Long.class, String.class,
                LocalDateTime.class, LocalDateTime.class, int.class, int.class);
        CpsVisionCheckRecordMapper.class.getMethod("countByFilters",
                Long.class, Long.class, String.class,
                LocalDateTime.class, LocalDateTime.class);
        CpsVisionCheckRecordMapper.class.getMethod("updateAiJudgeResult",
                com.company.cps.domain.CpsVisionCheckRecord.class);
        CpsVisionCheckJudgeEventMapper.class.getMethod("insert",
                com.company.cps.domain.CpsVisionCheckJudgeEvent.class);
        CpsVisionCheckJudgeEventMapper.class.getMethod("findByFingerprint", String.class);

        // controller 暴露 4 端点
        CpsVisionCheckController.class.getMethod("submit",
                com.company.cps.dto.CpsVisionCheckSubmitRequest.class);
        CpsVisionCheckController.class.getMethod("list",
                Long.class, Long.class, String.class,
                LocalDateTime.class, LocalDateTime.class, Integer.class, Integer.class);
        CpsVisionCheckController.class.getMethod("override",
                Long.class, com.company.cps.dto.CpsVisionCheckOverrideRequest.class);
        CpsVisionCheckController.class.getMethod("rejudge", Long.class, String.class);

        // 状态枚举 6 值
        assertTrue(Arrays.asList(
                com.company.cps.domain.CpsVisionCheckStatus.values())
                .containsAll(Arrays.asList(
                        com.company.cps.domain.CpsVisionCheckStatus.PENDING,
                        com.company.cps.domain.CpsVisionCheckStatus.AI_JUDGING,
                        com.company.cps.domain.CpsVisionCheckStatus.AI_PASS,
                        com.company.cps.domain.CpsVisionCheckStatus.AI_FAIL,
                        com.company.cps.domain.CpsVisionCheckStatus.HUMAN_OVERRIDE,
                        com.company.cps.domain.CpsVisionCheckStatus.TIMEOUT)));
    }
}