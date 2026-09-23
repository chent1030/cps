package com.company.cps.mapper;

import com.company.cps.dto.CpsAdjudicationMemoryItem;
import com.company.cps.dto.CpsInitialReviewEventMemoryItem;
import com.company.cps.dto.CpsIssueListItemResponse;
import com.company.cps.dto.CpsPushConfigRequest;
import com.company.cps.domain.CpsPushConfig;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 波次9：
 * - I 线记忆体系消费端：cps_review_adjudication / cps_initial_review_event / cps_issue 三表 JOIN + 必要过滤谓词；
 * - F 线推送配置：cps_push_config 单行 GLOBAL、CHECK 单行 + channel 枚举、UNIQUE(enabled_channel) 启用去重、迁移种子。
 *
 * 与 CpsWave8MapperContractTest 一致：纯字符串 + 反射契约校验，不依赖运行时 DB。
 */
class CpsWave9MapperContractTest {

    private static String readResource(String relativePath) throws IOException {
        return new String(Files.readAllBytes(Paths.get("src/main/resources", relativePath)), StandardCharsets.UTF_8);
    }

    // ----------------------------------------------------------------
    // I 线记忆体系消费端契约
    // ----------------------------------------------------------------

    @Test
    void adjudicationMemoryPageJoinsIssueForFactoryAreaCategory() throws IOException {
        String sql = readResource("mapper/CpsReviewAdjudicationMapper.xml");
        assertTrue(sql.contains("id=\"findMemoryPage\""), "findMemoryPage 必须存在");
        assertTrue(sql.contains("id=\"countMemory\""), "countMemory 必须存在");
        // JOIN cps_issue 取 factory/area/category
        assertTrue(sql.contains("INNER JOIN cps_issue i"), "裁决记忆分页必须 JOIN cps_issue");
        // 必要过滤谓词（与任务书 I 线契约一致）
        for (String predicate : Arrays.asList(
                "a.issue_id = #{issueId}",
                "a.decision = #{decision}",
                "a.ai_relation = #{aiRelation}",
                "a.reviewer_emp_no = #{reviewerEmpNo}",
                "i.factory = #{factory}",
                "i.area = #{area}",
                "i.category_l1_id = #{categoryL1Id}",
                "i.category_l2_id = #{categoryL2Id}",
                "a.created_at &gt;= #{startTime}",
                "a.created_at &lt;= #{endTime}")) {
            assertTrue(sql.contains(predicate), "裁决记忆过滤必须含谓词: " + predicate);
        }
        // 排序：created_at DESC + id DESC 稳定
        assertTrue(sql.contains("ORDER BY a.created_at DESC, a.id DESC"));
    }

    @Test
    void eventMemoryPageJoinsIssueForFactoryAreaCategory() throws IOException {
        String sql = readResource("mapper/CpsInitialReviewEventMapper.xml");
        assertTrue(sql.contains("id=\"findMemoryPage\""), "findMemoryPage 必须存在");
        assertTrue(sql.contains("id=\"countMemory\""), "countMemory 必须存在");
        assertTrue(sql.contains("INNER JOIN cps_issue i"), "事件记忆分页必须 JOIN cps_issue");
        for (String predicate : Arrays.asList(
                "e.issue_id = #{issueId}",
                "e.event_type = #{eventType}",
                "e.task_id = #{taskId}",
                "i.factory = #{factory}",
                "i.area = #{area}",
                "i.category_l1_id = #{categoryL1Id}",
                "i.category_l2_id = #{categoryL2Id}",
                "e.created_at &gt;= #{startTime}",
                "e.created_at &lt;= #{endTime}")) {
            assertTrue(sql.contains(predicate), "事件记忆过滤必须含谓词: " + predicate);
        }
        assertTrue(sql.contains("ORDER BY e.created_at DESC, e.id DESC"));
    }

    @Test
    void issueMemoryPageReusesAdminColumnsAndJoinsProblemCategory() throws IOException {
        String sql = readResource("mapper/CpsIssueMapper.xml");
        assertTrue(sql.contains("id=\"findMemoryPage\""), "findMemoryPage 必须存在");
        assertTrue(sql.contains("id=\"countMemory\""), "countMemory 必须存在");
        assertTrue(sql.contains("id=\"adminIssueColumns\""), "findMemoryPage 必须复用 adminIssueColumns 列集合");
        // adminIssueColumns 已内嵌 4 路 cps_problem_category LEFT JOIN（ai1/ai2/c1/c2）；findMemoryPage 不重复写 JOIN
        assertTrue(sql.contains("LEFT JOIN cps_problem_category ai1"),
                "adminIssueColumns 共享 4 路 cps_problem_category JOIN（与 listForAdmin 一致）");
        for (String predicate : Arrays.asList(
                "i.factory = #{factory}",
                "i.category_l1_id = #{categoryL1Id}",
                "i.category_l2_id = #{categoryL2Id}",
                "i.status = #{status}",
                "i.submit_time &gt;= #{startTime}",
                "i.submit_time &lt;= #{endTime}")) {
            assertTrue(sql.contains(predicate), "问题记忆过滤必须含谓词: " + predicate);
        }
        assertTrue(sql.contains("ORDER BY i.submit_time DESC, i.id DESC"));
    }

    @Test
    void memoryResponseDtoExposesAllRequiredFields() throws NoSuchMethodException {
        // 裁决
        assertEquals(Long.class, CpsAdjudicationMemoryItem.class.getMethod("getIssueId").getReturnType());
        assertEquals(Integer.class, CpsAdjudicationMemoryItem.class.getMethod("getVersionNo").getReturnType());
        assertEquals(String.class, CpsAdjudicationMemoryItem.class.getMethod("getDecision").getReturnType());
        assertEquals(String.class, CpsAdjudicationMemoryItem.class.getMethod("getAiOverall").getReturnType());
        assertEquals(String.class, CpsAdjudicationMemoryItem.class.getMethod("getAiRelation").getReturnType());
        assertEquals(String.class, CpsAdjudicationMemoryItem.class.getMethod("getReason").getReturnType());
        assertEquals(String.class, CpsAdjudicationMemoryItem.class.getMethod("getFromStatus").getReturnType());
        assertEquals(String.class, CpsAdjudicationMemoryItem.class.getMethod("getToStatus").getReturnType());
        // 事件
        assertEquals(Long.class, CpsInitialReviewEventMemoryItem.class.getMethod("getTaskId").getReturnType());
        assertEquals(String.class, CpsInitialReviewEventMemoryItem.class.getMethod("getEventType").getReturnType());
        assertEquals(String.class, CpsInitialReviewEventMemoryItem.class.getMethod("getDetail").getReturnType());
        assertEquals(String.class, CpsInitialReviewEventMemoryItem.class.getMethod("getOperatorEmpNo").getReturnType());
        // 问题侧字段对齐 CpsIssueListItemResponse
        assertEquals(String.class, CpsIssueListItemResponse.class.getMethod("getFactory").getReturnType());
        assertEquals(Long.class, CpsIssueListItemResponse.class.getMethod("getCategoryL1Id").getReturnType());
        assertEquals(Long.class, CpsIssueListItemResponse.class.getMethod("getCategoryL2Id").getReturnType());
    }

    // ----------------------------------------------------------------
    // F 线推送配置契约
    // ----------------------------------------------------------------

    @Test
    void pushConfigMapperDefinesSingletonAndUpsert() throws IOException {
        String sql = readResource("mapper/CpsPushConfigMapper.xml");
        assertTrue(sql.contains("id=\"findGlobal\""), "findGlobal 必须存在");
        assertTrue(sql.contains("WHERE id = 1"), "单行 GLOBAL 强约束 WHERE id=1");
        assertTrue(sql.contains("LIMIT 1"), "单行 GLOBAL LIMIT 1 兜底");
        assertTrue(sql.contains("id=\"upsert\""), "upsert 必须存在");
        assertTrue(sql.contains("INSERT INTO cps_push_config"), "upsert 必须写 cps_push_config");
        assertTrue(sql.contains("ON DUPLICATE KEY UPDATE"), "upsert 用 INSERT ... ON DUPLICATE KEY UPDATE");
        for (String col : Arrays.asList("channel", "endpoint", "secret_ref", "enabled", "updated_by")) {
            assertTrue(sql.contains(col), "upsert 必须含列: " + col);
        }
    }

    @Test
    void pushConfigMigrationEnforcesSingletonAndUniqueEnabledChannel() throws IOException {
        String ddl = readResource("db/migration/V20261005__cps_push_config.sql");
        assertTrue(ddl.contains("CREATE TABLE cps_push_config"), "必须建 cps_push_config");
        assertTrue(ddl.contains("chk_cps_push_config_singleton"), "单行 CHECK id=1 必须存在");
        assertTrue(ddl.contains("chk_cps_push_config_channel"), "channel 枚举 CHECK 必须存在");
        assertTrue(ddl.contains("GENERATED ALWAYS AS"), "enabled_channel 生成列必须存在");
        assertTrue(ddl.contains("UNIQUE KEY uk_cps_push_config_enabled_channel"),
                "UNIQUE(enabled_channel) 保证启用渠道唯一");
        for (String channel : Arrays.asList("'INTERFACE'", "'EMAIL'", "'SMS'", "'WEBHOOK'")) {
            assertTrue(ddl.contains(channel), "channel 枚举必须含 " + channel);
        }
        // 默认种子
        assertTrue(ddl.contains("INSERT INTO cps_push_config (id, channel, enabled, endpoint)"),
                "V20261005 末尾种子 INSERT");
        assertTrue(ddl.contains("VALUES (1, 'INTERFACE', 0, '')"), "种子：单行 INTERFACE/禁用/空 endpoint");
    }

    @Test
    void pushConfigDomainAndDtoExposeAllFields() throws NoSuchMethodException {
        assertNotNull(CpsPushConfig.SINGLETON_ID, "SINGLETON_ID 常量必须为 1");
        assertEquals(String.class, CpsPushConfig.class.getMethod("getChannel").getReturnType());
        assertEquals(Boolean.class, CpsPushConfig.class.getMethod("getEnabled").getReturnType());
        // Request 字段
        assertEquals(String.class, CpsPushConfigRequest.class.getMethod("getChannel").getReturnType());
        assertEquals(String.class, CpsPushConfigRequest.class.getMethod("getEndpoint").getReturnType());
        assertEquals(String.class, CpsPushConfigRequest.class.getMethod("getSecretRef").getReturnType());
        assertEquals(Boolean.class, CpsPushConfigRequest.class.getMethod("getEnabled").getReturnType());
        assertEquals(String.class, CpsPushConfigRequest.class.getMethod("getUpdatedBy").getReturnType());
    }
}