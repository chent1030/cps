package com.company.cps.mapper;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 波次5 A3/A4 mapper XML + 迁移契约：
 * 裁决表/触发配置/事件流水字段全集 + 关键语义子句（CAS 守卫、独立计时重置、admin JOIN 视图）。
 */
class CpsWave5MapperContractTest {

    private static String readResource(String relativePath) throws IOException {
        return new String(Files.readAllBytes(Paths.get("src/main/resources", relativePath)), StandardCharsets.UTF_8);
    }

    @Test
    void adjudicationMapperCoversA3AdjudicationColumns() throws IOException {
        String sql = readResource("mapper/CpsReviewAdjudicationMapper.xml");
        for (String column : new String[]{
                "issue_id", "version_no", "task_id", "reviewer_emp_no", "reviewer_emp_name",
                "decision", "ai_overall", "ai_relation", "reason", "from_status", "to_status"}) {
            assertTrue(sql.contains(column), "missing adjudication column: " + column);
        }
        // uk(issue_id, version_no) 幂等锚点：按版本点查
        assertTrue(sql.contains("WHERE issue_id = #{issueId} AND version_no = #{versionNo}"));
    }

    @Test
    void configMapperSingleRowGlobalSemantics() throws IOException {
        String sql = readResource("mapper/CpsInitialReviewConfigMapper.xml");
        for (String column : new String[]{
                "auto_trigger_enabled", "max_retry_attempts", "retry_backoff_ms", "timeout_seconds"}) {
            assertTrue(sql.contains(column), "missing trigger config column: " + column);
        }
        assertTrue(sql.contains("WHERE config_key = 'GLOBAL'"), "单行 GLOBAL 配置");
    }

    @Test
    void eventMapperCoversTriggerRecordQueries() throws IOException {
        String sql = readResource("mapper/CpsInitialReviewEventMapper.xml");
        for (String column : new String[]{
                "task_id", "issue_id", "version_no", "event_type", "detail", "operator_emp_no"}) {
            assertTrue(sql.contains(column), "missing event column: " + column);
        }
        assertTrue(sql.contains("ORDER BY id ASC"), "流水按时间正序回放（id 递增）");
    }

    @Test
    void taskMapperRetryAndRetriggerAreCasGuarded() throws IOException {
        String sql = readResource("mapper/CpsInitialReviewTaskMapper.xml");
        // 技术重试仅允许 RUNNING 中递增（不越权改终态）
        assertTrue(sql.contains("retry_count = retry_count + 1"), "incrementRetryCount exists");
        assertTrue(sql.contains("WHERE id = #{id} AND status = 'RUNNING'"), "重试 CAS 守卫");
        // 重触发仅允许 FAILED/PENDING_DISPATCH → RUNNING，且重置计时锚点（新投递轮次独立计时，D-21）
        assertTrue(sql.contains("status IN ('FAILED', 'PENDING_DISPATCH')"), "markRetriggered 状态守卫");
        assertTrue(sql.contains("timeout_at = #{timeoutAt}"), "重触发重置超时锚点");
        assertTrue(sql.contains("idempotency_key = #{idempotencyKey}"), "新幂等键覆盖");
        // admin 分页 JOIN：问题状态/AI 结果/裁决关联视图
        assertTrue(sql.contains("LEFT JOIN cps_initial_review_result"), "admin 视图关联初审结果");
        assertTrue(sql.contains("LEFT JOIN cps_review_adjudication"), "admin 视图关联裁决");
    }

    @Test
    void issueMapperClearsHandlerOnRetriggerRollback() throws IOException {
        String sql = readResource("mapper/CpsIssueMapper.xml");
        // A4 重触发回退：PENDING_REVIEW → PENDING_AI_REVIEW 清办理人（带 fromStatus CAS + 乐观锁递增）
        assertTrue(sql.contains("updateStatusClearHandler"));
        assertTrue(sql.contains("current_handler_emp_no = NULL"));
        assertTrue(sql.contains("current_handler_emp_name = NULL"));
        assertTrue(sql.contains("AND status = #{fromStatus}"));
    }

    @Test
    void migrationCoversWave5TablesAndEventTypes() throws IOException {
        String sql = readResource("db/migration/V20261001__cps_review_adjudication_a3a4.sql");
        assertTrue(sql.contains("CREATE TABLE cps_review_adjudication"));
        assertTrue(sql.contains("UNIQUE KEY uk_review_adjudication_version (issue_id, version_no)"),
                "裁决幂等锚点 uk(issue_id, version_no)");
        assertTrue(sql.contains("CREATE TABLE cps_initial_review_config"));
        assertTrue(sql.contains("INSERT INTO cps_initial_review_config"), "GLOBAL 种子行");
        assertTrue(sql.contains("CREATE TABLE cps_initial_review_event"));
        // 事件流水类型全集（触发/重试/回调/超时/接管/迟到/重触发/裁决）
        for (String eventType : new String[]{"TRIGGERED", "DISPATCH_RETRY", "DISPATCH_FAILED",
                "CALLBACK_RECEIVED", "TIMEOUT_OPENED", "TAKEN_OVER", "LATE_RESULT", "RETRIGGERED", "ADJUDICATED"}) {
            assertTrue(sql.contains(eventType), "missing event type comment: " + eventType);
        }
    }
}
