package com.company.cps.mapper;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 波次8 E 线（台账流水/预警）+ D 线（建单记录）mapper XML + 迁移契约：
 * 行锁串行化、前后快照、合并事件生成列唯一、append-only 建单记录。
 */
class CpsWave8MapperContractTest {

    private static String readResource(String relativePath) throws IOException {
        return new String(Files.readAllBytes(Paths.get("src/main/resources", relativePath)), StandardCharsets.UTF_8);
    }

    @Test
    void inventoryItemMapperHasSelectForUpdateRowLock() throws IOException, NoSuchMethodException {
        String sql = readResource("mapper/CpsInventoryItemMapper.xml");
        assertTrue(sql.contains("FOR UPDATE"), "selectForUpdate 必须悲观行锁串行化同件操作");
        assertTrue(sql.toLowerCase().contains("stock_qty"),
                "条件更新必须落 stock_qty");
        CpsInventoryItemMapper.class.getMethod("selectForUpdate", Long.class);
    }

    @Test
    void inventoryTxnMapperPersistsBeforeAfterSnapshots() throws IOException {
        String sql = readResource("mapper/CpsInventoryTxnMapper.xml");
        for (String col : new String[]{"txn_type", "qty", "before_qty", "after_qty", "unit",
                "operator_emp_no", "operator_name", "remark"}) {
            assertTrue(sql.contains(col), "流水必须含列: " + col);
        }
    }

    @Test
    void inventoryAlertMapperMergesOpenEventsPerItem() throws IOException {
        String sql = readResource("mapper/CpsInventoryAlertEventMapper.xml");
        assertTrue(sql.contains("OPEN"), "合并事件判定必须查 OPEN 状态");
        assertTrue(sql.contains("first_triggered_at"), "首触时间");
        assertTrue(sql.contains("last_eval_at"), "合并期最近评估时间");
        assertTrue(sql.contains("threshold_snapshot"), "阈值快照");
    }

    @Test
    void planBuildRecordMapperIsAppendOnlyWithResultEnum() throws IOException {
        String sql = readResource("mapper/CpsPlanBuildRecordMapper.xml");
        for (String col : new String[]{"plan_id", "task_type", "result", "error_msg", "built_by", "build_source"}) {
            assertTrue(sql.contains(col), "建单记录必须含列: " + col);
        }
    }

    @Test
    void migrationCoversWave8TablesAndConstraints() throws IOException {
        String txn = readResource("db/migration/V20261003__cps_inventory_txn_alert.sql");
        assertTrue(txn.contains("CREATE TABLE cps_inventory_txn"));
        assertTrue(txn.contains("chk_cps_inv_txn_after_nonneg"), "after_qty>=0 CHECK 兜底");
        assertTrue(txn.contains("CREATE TABLE cps_inventory_alert_event"));
        assertTrue(txn.contains("GENERATED ALWAYS AS"), "open_item_id 生成列");
        assertTrue(txn.contains("UNIQUE KEY uk_cps_inv_alert_open"), "同物品同时最多一行 OPEN");
        for (String status : new String[]{"OPEN", "RESOLVED_AUTO", "RESOLVED_MANUAL", "IGNORED"}) {
            assertTrue(txn.contains(status), "missing alert status: " + status);
        }
        for (String type : new String[]{"IN", "OUT", "ADJUST"}) {
            assertTrue(txn.contains("'" + type + "'"), "missing txn type: " + type);
        }

        String build = readResource("db/migration/V20261004__cps_plan_build_record.sql");
        assertTrue(build.contains("CREATE TABLE cps_plan_build_record"));
        for (String result : new String[]{"CREATED", "SKIPPED_EXISTING", "CREATE_FAILED"}) {
            assertTrue(build.contains(result), "missing build result: " + result);
        }
        for (String source : new String[]{"APPROVE", "REBUILD"}) {
            assertTrue(build.contains(source), "missing build source: " + source);
        }
    }
}
