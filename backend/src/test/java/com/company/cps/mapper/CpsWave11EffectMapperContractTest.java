package com.company.cps.mapper;

import com.company.cps.controller.CpsCoverageController;
import com.company.cps.controller.CpsMemoryAdminController;
import com.company.cps.service.CpsCoverageService;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 波次11 FR-11 + FR-12 契约：
 * - FR-11 procedural 调度记忆消费端：cps_review_adjudication JOIN cps_issue 聚合，
 *   按 (category_l1_id, area, decision, ai_relation) GROUP BY + 最近 5 条样例 reason 200 字截断；
 * - FR-12 效果评估四 metric：ai_pass_rate / human_override_rate / avg_close_duration_hours / recurrence_rate_30d。
 *
 * 与 CpsWave10CoverageMapperContractTest 一致：纯字符串 + 反射契约校验，不依赖运行时 DB。
 */
class CpsWave11EffectMapperContractTest {

    private static String readResource(String relativePath) throws IOException {
        return new String(Files.readAllBytes(Paths.get("src/main/resources", relativePath)), StandardCharsets.UTF_8);
    }

    // ----------------------------------------------------------------
    // FR-11 dispatcher 契约
    // ----------------------------------------------------------------

    @Test
    void dispatcherMemoryGroupByContainsFourDimGroupingAndSampleTruncation() throws IOException {
        String sql = readResource("mapper/CpsCoverageMapper.xml");
        assertTrue(sql.contains("id=\"dispatcherMemoryGroupBy\""),
                "dispatcherMemoryGroupBy 必须存在");
        assertTrue(sql.contains("id=\"countDispatcherMemoryGroupBy\""),
                "countDispatcherMemoryGroupBy 必须存在");
        // JOIN 模式与 CpsReviewAdjudicationMapper.findMemoryPage 一致（INNER JOIN cps_issue）
        assertTrue(sql.contains("INNER JOIN cps_issue i"),
                "dispatcher 必须 INNER JOIN cps_issue 透出 factory/area/category");
        // 四元 GROUP BY
        assertTrue(sql.contains("GROUP BY i.category_l1_id, i.area, a.decision, a.ai_relation"),
                "dispatcher 必须按 (category_l1_id, area, decision, ai_relation) GROUP BY");
        // 样例 reason 截断 200 字
        assertTrue(sql.contains("SUBSTRING(COALESCE(a.reason, ''), 1, 200)"),
                "dispatcher reason 必须截断 200 字");
        // 聚合 sampleReasons 由 GROUP_CONCAT + ORDER BY created_at DESC 拼出最近 N 条
        assertTrue(sql.contains("GROUP_CONCAT"),
                "dispatcher 必须用 GROUP_CONCAT 拼接多行样例");
        assertTrue(sql.contains("ORDER BY a.created_at DESC, a.id DESC"),
                "dispatcher 样例内排序必须 created_at DESC + id DESC");
        assertTrue(sql.contains("SUBSTRING_INDEX(") && sql.contains("';;', 5"),
                "dispatcher 必须用 SUBSTRING_INDEX 取最近 5 条样例");
        // 字段别名（与契约响应一致）
        for (String alias : Arrays.asList("categoryL1Id", "area", "decision", "aiRelation",
                "sampleCount", "lastAt", "sampleReasons")) {
            assertTrue(sql.contains(alias), "dispatcher 行必须含字段: " + alias);
        }
        // 必要过滤谓词
        for (String predicate : Arrays.asList(
                "i.category_l1_id = #{categoryL1Id}",
                "i.category_l2_id = #{categoryL2Id}",
                "i.factory = #{factory}",
                "i.area = #{area}",
                "a.decision = #{decision}",
                "a.ai_relation = #{aiRelation}",
                "a.created_at &gt;= #{startTime}",
                "a.created_at &lt;= #{endTime}")) {
            assertTrue(sql.contains(predicate), "dispatcher 过滤必须含谓词: " + predicate);
        }
        // 排序：sampleCount DESC + lastAt DESC 双重降序
        assertTrue(sql.contains("ORDER BY sampleCount DESC, lastAt DESC"),
                "dispatcher 排序必须 sampleCount DESC + lastAt DESC");
        // 顶层排序加上稳定二级索引
        assertTrue(sql.contains("i.category_l1_id, i.area"),
                "dispatcher 顶层排序必须含 category_l1_id + area 兜底");
        // 分页 limit/offset
        assertTrue(sql.contains("LIMIT #{limit} OFFSET #{offset}"),
                "dispatcher 必须支持 limit/offset 分页");
    }

    @Test
    void dispatcherServiceParsesSampleReasonsAndExposesControllerEndpoint() throws NoSuchMethodException {
        // service 暴露 dispatcherMemoryGroupBy
        CpsCoverageService.class.getMethod("dispatcherMemoryGroupBy",
                Long.class, Long.class,
                String.class, String.class, String.class, String.class,
                String.class, String.class,
                Integer.class, Integer.class);
        // mapper 接口暴露 dispatcherMemoryGroupBy + countDispatcherMemoryGroupBy
        CpsCoverageMapper.class.getMethod("dispatcherMemoryGroupBy",
                Long.class, Long.class,
                String.class, String.class, String.class, String.class,
                String.class, String.class,
                int.class, int.class);
        CpsCoverageMapper.class.getMethod("countDispatcherMemoryGroupBy",
                Long.class, Long.class,
                String.class, String.class, String.class, String.class,
                String.class, String.class);
        // 控制器暴露 dispatcher 端点（@GetMapping /dispatcher 路径前缀 /api/cps/admin/memory）
        CpsMemoryAdminController.class.getMethod("dispatcher",
                Long.class, Long.class,
                String.class, String.class, String.class, String.class,
                String.class, String.class,
                Integer.class, Integer.class);
    }

    // ----------------------------------------------------------------
    // FR-12 effect 四 metric 契约
    // ----------------------------------------------------------------

    @Test
    void effectMapperCoversFourMetricsWithRequiredJoinAndCaseLogic() throws IOException {
        String sql = readResource("mapper/CpsEffectMetricMapper.xml");
        // 四 metric 入口都存在
        for (String id : Arrays.asList("aiPassRate", "humanOverrideRate",
                "avgCloseDurationHours", "recurrenceRate")) {
            assertTrue(sql.contains("id=\"" + id + "\""),
                    "FR-12 effect mapper 必须暴露 metric 方法 " + id);
        }
        // ai_pass_rate：ai_overall='PASS' 计数 + NULLIF 防 0 除 + ROUND
        assertTrue(sql.contains("a.ai_overall = 'PASS'"),
                "ai_pass_rate 必须以 ai_overall='PASS' 计数");
        assertTrue(sql.contains("NULLIF(COUNT(1), 0)"),
                "ai_pass_rate 必须 NULLIF(COUNT(1), 0) 防 0 除");
        // human_override_rate：decision='REJECT' AND ai_relation='AGAINST_AI'
        assertTrue(sql.contains("a.decision = 'REJECT'") && sql.contains("a.ai_relation = 'AGAINST_AI'"),
                "human_override_rate 必须同时含 decision='REJECT' AND ai_relation='AGAINST_AI'");
        assertTrue(sql.contains("NULLIF(SUM(CASE WHEN a.decision = 'REJECT' THEN 1 ELSE 0 END), 0)"),
                "human_override_rate 分母必须 SUM(decision='REJECT') NULLIF 防 0 除");
        // avg_close_duration_hours：to_status='CLOSED' + TIMESTAMPDIFF(SECOND, ..., ...)/3600.0
        assertTrue(sql.contains("a.to_status = 'CLOSED'"),
                "avg_close_duration_hours 必须 to_status='CLOSED' 过滤");
        assertTrue(sql.contains("TIMESTAMPDIFF(SECOND, i.created_at, a.created_at)"),
                "avg_close_duration_hours 必须以 TIMESTAMPDIFF(SECOND, i.created_at, a.created_at) 计算差值");
        assertTrue(sql.contains("/ 3600.0") || sql.contains("/3600.0"),
                "avg_close_duration_hours 必须除以 3600.0 折算小时");
        // recurrence_rate_30d：UNION 两侧（REOPEN+REJECT）+ 90 天窗口区间参数化 + 百分数 round
        assertTrue(sql.contains("UNION"),
                "recurrence 必须 UNION 合并 cps_issue_flow_log.REOPEN + cps_review_adjudication.REJECT");
        assertTrue(sql.contains("action = 'REOPEN'"),
                "recurrence 第一路信号 cps_issue_flow_log.action='REOPEN'");
        assertTrue(sql.contains("decision = 'REJECT'"),
                "recurrence 第二路信号 cps_review_adjudication.decision='REJECT'");
        assertTrue(sql.contains("COUNT(DISTINCT recur.issue_id"),
                "recurrence 分子必须 COUNT(DISTINCT issue_id)");
        assertTrue(sql.contains("NULLIF(") && sql.contains("COUNT(DISTINCT a.issue_id)"),
                "recurrence 分母必须 COUNT(DISTINCT a.issue_id) NULLIF 防 0 除");
        // 四个 SQL 必须都使用 periodStart/periodEnd 时间窗
        for (String predicate : Arrays.asList(
                "a.created_at &gt;= #{periodStart}",
                "a.created_at &lt;= #{periodEnd}")) {
            assertTrue(sql.contains(predicate),
                    "FR-12 effect SQL 必须支持 periodStart/periodEnd 时间窗: " + predicate);
        }
        // metricKey/scopeKey 预留扩展点必须存在
        assertTrue(sql.contains("#{metricKey}"),
                "FR-12 effect SQL 必须预留 metricKey 扩展点");
        assertTrue(sql.contains("#{scopeKey}"),
                "FR-12 effect SQL 必须预留 scopeKey 扩展点");
    }

    @Test
    void effectServiceAndControllerExposeFourMetricsInCpsPageResponseShape()
            throws NoSuchMethodException {
        // service 暴露 effect(...) 方法
        CpsCoverageService.class.getMethod("effect",
                String.class, String.class, String.class, String.class);
        // controller effect 端点
        CpsCoverageController.class.getMethod("effect",
                String.class, String.class, String.class, String.class);
        // mapper 四个 metric 方法签名
        Class<?>[] stringArgs = new Class<?>[]{String.class, String.class, String.class, String.class};
        CpsEffectMetricMapper.class.getMethod("aiPassRate", stringArgs);
        CpsEffectMetricMapper.class.getMethod("humanOverrideRate", stringArgs);
        CpsEffectMetricMapper.class.getMethod("avgCloseDurationHours", stringArgs);
        CpsEffectMetricMapper.class.getMethod("recurrenceRate", stringArgs);
        // service 行字段命名契约：metricKey / metricValue / recordedAt
        java.lang.reflect.Method m = CpsCoverageService.class.getDeclaredMethod(
                "buildMetricRow", String.class, Double.class, java.time.LocalDateTime.class);
        assertNotNull(m, "buildMetricRow 必须为包内可见，便于解析 sampleReasons 复用");
        assertTrue(java.lang.reflect.Modifier.isStatic(m.getModifiers())
                        || java.lang.reflect.Modifier.isPrivate(m.getModifiers()),
                "buildMetricRow 应为 static 或 private 工具方法");
        // sampleReasons 解析器
        java.lang.reflect.Method parse = CpsCoverageService.class.getDeclaredMethod(
                "parseSampleReasons", Object.class);
        assertEquals(List.class, parse.getReturnType(),
                "parseSampleReasons 必须返回 List<Map<String,Object>> 解析样例");
    }
}