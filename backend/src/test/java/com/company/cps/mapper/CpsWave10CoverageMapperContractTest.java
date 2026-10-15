package com.company.cps.mapper;

import com.company.cps.controller.CpsCoverageController;
import com.company.cps.service.CpsCoverageService;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 波次10 FR-10 历史/覆盖分析聚合查询契约：
 * - frequency / region-supervisor / recurrence / gaps 四个端点的 SQL 含 GROUP BY / LEFT JOIN / 必要字段；
 * - 超期阈值、复发阈值、缺口窗口等参数化生效；
 * - service / controller 暴露必需方法。
 *
 * 与 CpsWave9MapperContractTest 一致：纯字符串 + 反射契约校验，不依赖运行时 DB。
 */
class CpsWave10CoverageMapperContractTest {

    private static String readResource(String relativePath) throws IOException {
        return new String(Files.readAllBytes(Paths.get("src/main/resources", relativePath)), StandardCharsets.UTF_8);
    }

    // ----------------------------------------------------------------
    // frequency 契约
    // ----------------------------------------------------------------

    @Test
    void frequencyGroupByHasAllMetricsAndGrouping() throws IOException {
        String sql = readResource("mapper/CpsCoverageMapper.xml");
        assertTrue(sql.contains("id=\"frequencyGroupBy\""), "frequencyGroupBy 必须存在");
        assertTrue(sql.contains("id=\"countFrequencyGroupBy\""), "countFrequencyGroupBy 必须存在");
        // GROUP BY 三元组
        assertTrue(sql.contains("GROUP BY i.factory, i.area, i.category_l1_id"),
                "frequency 必须按 (factory, area, category_l1_id) 分组");
        // 四个聚合字段
        assertTrue(sql.contains("COUNT(1)") || sql.contains("COUNT(*)"),
                "frequency 必须含 issue_count 计数");
        assertTrue(sql.contains("DATE_SUB(NOW(), INTERVAL 30 DAY)"),
                "frequency 必须含 30 天 recent_count 窗口");
        assertTrue(sql.contains("status = 'CLOSED'"),
                "frequency 必须含 closed_count (status='CLOSED') 判定");
        assertTrue(sql.contains("ROUND(") && sql.contains("closeRate"),
                "frequency 必须含 close_rate ROUND 字段别名 closeRate");
        // 别名
        for (String alias : Arrays.asList("issueCount", "recentCount", "closedCount", "closeRate")) {
            assertTrue(sql.contains(alias), "frequency 必须含别名: " + alias);
        }
        // 排序稳定
        assertTrue(sql.contains("ORDER BY issueCount DESC"),
                "frequency 排序必须 issueCount DESC 命中主要矛盾");
        // 过滤谓词
        for (String predicate : Arrays.asList(
                "i.factory = #{factory}",
                "i.area = #{area}",
                "i.category_l1_id = #{categoryL1Id}",
                "i.submit_time &gt;= #{startTime}",
                "i.submit_time &lt;= #{endTime}")) {
            assertTrue(sql.contains(predicate), "frequency 过滤必须含谓词: " + predicate);
        }
    }

    // ----------------------------------------------------------------
    // region-supervisor 契约
    // ----------------------------------------------------------------

    @Test
    void regionSupervisorGroupByParameterizesOverdueAndHandlerFields() throws IOException {
        String sql = readResource("mapper/CpsCoverageMapper.xml");
        assertTrue(sql.contains("id=\"regionSupervisorGroupBy\""),
                "regionSupervisorGroupBy 必须存在");
        assertTrue(sql.contains("id=\"countRegionSupervisorGroupBy\""),
                "countRegionSupervisorGroupBy 必须存在");
        // GROUP BY 三元组（含姓名）
        assertTrue(sql.contains("GROUP BY i.area, i.current_handler_emp_no, i.current_handler_emp_name"),
                "region-supervisor 必须按 (area, handler_emp_no, handler_emp_name) 分组");
        // open/handled/overdue 三字段
        for (String metric : Arrays.asList("openCount", "handledCount", "overdueCount")) {
            assertTrue(sql.contains(metric), "region-supervisor 必须含别名: " + metric);
        }
        // 超期阈值参数化（不写死数字）
        assertTrue(sql.contains("DATE_SUB(NOW(), INTERVAL #{overdueDays} DAY)"),
                "overdueCount 必须用参数化 overdueDays，不得硬编码 3");
        // status 默认非 CLOSED 透传
        assertTrue(sql.contains("__OPEN__"),
                "默认 status='__OPEN__' 哨兵映射 status <> 'CLOSED'");
        assertTrue(sql.contains("status &lt;&gt; 'CLOSED'"),
                "region-supervisor 默认过滤 status <> 'CLOSED'");
        // JOIN cps_issue 含 current_handler_emp_no/emp_name
        assertTrue(sql.contains("i.current_handler_emp_no"),
                "region-supervisor 必须透出 current_handler_emp_no");
        assertTrue(sql.contains("i.current_handler_emp_name"),
                "region-supervisor 必须透出 current_handler_emp_name");
        // 排序：openCount DESC + overdueCount DESC 双重降序
        assertTrue(sql.contains("ORDER BY openCount DESC, overdueCount DESC"),
                "region-supervisor 排序必须 open DESC + overdue DESC");
    }

    // ----------------------------------------------------------------
    // recurrence 契约
    // ----------------------------------------------------------------

    @Test
    void recurrenceGroupByUnionsReopenAndRejectAndHonoursThreshold() throws IOException {
        String sql = readResource("mapper/CpsCoverageMapper.xml");
        assertTrue(sql.contains("id=\"recurrenceGroupBy\""), "recurrenceGroupBy 必须存在");
        assertTrue(sql.contains("id=\"countRecurrenceGroupBy\""), "countRecurrenceGroupBy 必须存在");
        // UNION ALL 两侧信号
        assertTrue(sql.contains("UNION ALL"), "recurrence 必须 UNION ALL 合并 flow_log + adjudication");
        assertTrue(sql.contains("action = 'REOPEN'"),
                "recurrence 第一路信号 cps_issue_flow_log.action='REOPEN'");
        assertTrue(sql.contains("decision = 'REJECT'"),
                "recurrence 第二路信号 cps_review_adjudication.decision='REJECT'");
        // 阈值 HAVING 过滤
        assertTrue(sql.contains("HAVING SUM(cnt) &gt;= #{threshold}"),
                "recurrence 必须 HAVING SUM(cnt) >= threshold");
        // INNER JOIN cps_issue 透出分类/工厂/区域
        assertTrue(sql.contains("INNER JOIN"),
                "recurrence 必须 INNER JOIN 子查询");
        assertTrue(sql.contains("FROM cps_issue i"),
                "recurrence 主表 cps_issue 别名 i");
        assertTrue(sql.contains("i.factory") && sql.contains("i.area")
                        && sql.contains("i.category_l1_id"),
                "recurrence 主查询必须透出 factory/area/category_l1_id 字段");
        // 字段别名
        for (String alias : Arrays.asList("issueId", "recurrenceCount", "lastRecurrenceAt",
                "categoryL1Id", "categoryL2Id", "factory", "area")) {
            assertTrue(sql.contains(alias), "recurrence 行必须含字段: " + alias);
        }
        // 时间窗走 created_at 区间（startTime/endTime 双向）
        assertTrue(sql.contains("created_at &gt;= #{startTime}"),
                "recurrence 子查询必须支持 startTime 下界");
        assertTrue(sql.contains("created_at &lt;= #{endTime}"),
                "recurrence 子查询必须支持 endTime 上界");
        // 排序：复发次数 DESC + 最近复发时间 DESC
        assertTrue(sql.contains("ORDER BY recur.cnt DESC, recur.lastAt DESC"),
                "recurrence 排序必须 复发次数 DESC + 最近时间 DESC");
    }

    // ----------------------------------------------------------------
    // gaps 契约
    // ----------------------------------------------------------------

    @Test
    void coverageGapsCrossJoinsInspectionItemAndRoomWithLeftJoinRecords() throws IOException {
        String sql = readResource("mapper/CpsCoverageMapper.xml");
        assertTrue(sql.contains("id=\"coverageGaps\""), "coverageGaps 必须存在");
        assertTrue(sql.contains("id=\"countCoverageGaps\""), "countCoverageGaps 必须存在");
        // CROSS JOIN cps_inspection_item × cps_room
        assertTrue(sql.contains("CROSS JOIN cps_inspection_item ii"),
                "gaps 必须 CROSS JOIN cps_inspection_item");
        assertTrue(sql.contains("FROM cps_room r"),
                "gaps 主表 cps_room");
        // LEFT JOIN cps_room_check_record 子查询
        assertTrue(sql.contains("LEFT JOIN ("),
                "gaps 必须 LEFT JOIN 子查询");
        assertTrue(sql.contains("cps_room_check_record"),
                "gaps LEFT JOIN cps_room_check_record");
        assertTrue(sql.contains("MAX(submitted_at)"),
                "gaps 必须取 MAX(submitted_at) 作为 last_record_at");
        // 缺口窗口参数化
        assertTrue(sql.contains("DATE_SUB(NOW(), INTERVAL #{gapDays} DAY)"),
                "gaps 缺口阈值必须用参数化 gapDays");
        // 启用过滤
        assertTrue(sql.contains("ii.enabled = 1"),
                "gaps 仅统计 cps_inspection_item.enabled = 1");
        assertTrue(sql.contains("r.enabled = 1"),
                "gaps 仅统计 cps_room.enabled = 1");
        // 0 record 判定
        assertTrue(sql.contains("last_record_at IS NULL"),
                "gaps 必须把从未有记录的房间视为缺口");
        // 字段别名
        for (String alias : Arrays.asList("roomId", "roomName", "itemId", "itemName",
                "lastRecordAt", "daysSinceLastRecord")) {
            assertTrue(sql.contains(alias), "gaps 行必须含字段: " + alias);
        }
        // DATEDIFF 计算 daysSinceLastRecord
        assertTrue(sql.contains("DATEDIFF(NOW(), last_rc.last_record_at)"),
                "gaps daysSinceLastRecord 走 DATEDIFF(NOW(), last_record_at)");
        // 过滤：factory + storageRoomType(→ room_type)
        assertTrue(sql.contains("r.factory = #{factory}"),
                "gaps 过滤必须含 factory");
        assertTrue(sql.contains("r.room_type = #{storageRoomType}"),
                "gaps storageRoomType 必须映射到 cps_room.room_type");
    }

    // ----------------------------------------------------------------
    // 端点 + service + controller 暴露契约
    // ----------------------------------------------------------------

    @Test
    void coverageControllerAndServiceExposeFourEndpoints() throws NoSuchMethodException {
        // 控制器四端点
        assertEquals("frequency", CpsCoverageController.class.getMethod("frequency",
                String.class, String.class, Long.class,
                String.class, String.class, Integer.class, Integer.class).getName());
        assertEquals("regionSupervisor", CpsCoverageController.class.getMethod("regionSupervisor",
                String.class, String.class, Long.class, String.class, Integer.class,
                Integer.class, Integer.class).getName());
        assertEquals("recurrence", CpsCoverageController.class.getMethod("recurrence",
                String.class, String.class, Long.class, Integer.class,
                Integer.class, Integer.class).getName());
        assertEquals("gaps", CpsCoverageController.class.getMethod("gaps",
                String.class, String.class, String.class, Integer.class,
                Integer.class, Integer.class).getName());
        // service 四方法
        CpsCoverageService.class.getMethod("frequencyGroupBy",
                String.class, String.class, Long.class,
                String.class, String.class, Integer.class, Integer.class);
        CpsCoverageService.class.getMethod("regionSupervisorGroupBy",
                String.class, String.class, Long.class, String.class, Integer.class,
                Integer.class, Integer.class);
        CpsCoverageService.class.getMethod("recurrenceGroupBy",
                String.class, String.class, Long.class, Integer.class,
                Integer.class, Integer.class);
        CpsCoverageService.class.getMethod("coverageGaps",
                String.class, String.class, String.class, Integer.class,
                Integer.class, Integer.class);
        // mapper 接口方法
        CpsCoverageMapper.class.getMethod("frequencyGroupBy",
                String.class, String.class, Long.class,
                String.class, String.class, int.class, int.class);
        CpsCoverageMapper.class.getMethod("countFrequencyGroupBy",
                String.class, String.class, Long.class, String.class, String.class);
        CpsCoverageMapper.class.getMethod("regionSupervisorGroupBy",
                String.class, String.class, Long.class, String.class, Integer.class,
                int.class, int.class);
        CpsCoverageMapper.class.getMethod("countRegionSupervisorGroupBy",
                String.class, String.class, Long.class, String.class);
        CpsCoverageMapper.class.getMethod("recurrenceGroupBy",
                String.class, String.class, Long.class, Integer.class,
                int.class, int.class);
        CpsCoverageMapper.class.getMethod("countRecurrenceGroupBy",
                String.class, String.class, Long.class, Integer.class);
        CpsCoverageMapper.class.getMethod("coverageGaps",
                String.class, String.class, String.class, Integer.class,
                int.class, int.class);
        CpsCoverageMapper.class.getMethod("countCoverageGaps",
                String.class, String.class, String.class, Integer.class);
    }
}
