package com.company.cps.mapper;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 波次2 B1/B2/E1 mapper XML 契约：PRD 字段全集 + 关键语义子句。 */
class CpsWave2MapperContractTest {

    private static String readXml(String fileName) throws IOException {
        return new String(Files.readAllBytes(Paths.get("src/main/resources/mapper", fileName)), StandardCharsets.UTF_8);
    }

    @Test
    void roomMapperCoversPrd231KeeperAndManagerFields() throws IOException {
        String sql = readXml("CpsRoomMapper.xml");
        for (String column : new String[]{
                "room_code", "building", "door_no", "room_name", "room_type", "risk_level", "dept_name",
                "keeper_emp_no", "keeper_emp_name", "keeper_manager_emp_no", "keeper_manager_emp_name"}) {
            assertTrue(sql.contains(column), "missing §23.1 column: " + column);
        }
        assertTrue(sql.contains("base AS base_code"));
    }

    @Test
    void checkItemMapperBumpsConfigVersionOnUpdate() throws IOException {
        String sql = readXml("CpsCheckItemMapper.xml");
        for (String column : new String[]{
                "item_code", "content", "photo_category", "deduct_score", "status",
                "applicable_room_types", "config_version"}) {
            assertTrue(sql.contains(column), "missing §23.2 column: " + column);
        }
        assertTrue(sql.contains("config_version = config_version + 1"));
    }

    @Test
    void inspectionItemPermissionOpenWhenNoRowsConfigured() throws IOException {
        String sql = readXml("CpsInspectionItemMapper.xml");
        // §30.1：无配置行=开放（NOT EXISTS 全表开放）OR 有配置行且含该工号
        assertTrue(sql.contains("NOT EXISTS"));
        assertTrue(sql.contains("cps_inspection_item_permission p2"));
        assertTrue(sql.contains("p2.emp_no = #{empNo}"));
        assertTrue(sql.contains("DELETE FROM cps_inspection_item_permission"));
    }

    @Test
    void inventoryMapperCoversPrd251AndLowStockIncludesEqual() throws IOException {
        String sql = readXml("CpsInventoryItemMapper.xml");
        for (String column : new String[]{
                "item_code", "item_name", "unit", "stock_qty", "alert_threshold",
                "storage_room", "room_keeper_emp_no", "room_keeper_emp_name"}) {
            assertTrue(sql.contains(column), "missing §25.1 column: " + column);
        }
        // §25.2/AC-13：库存<=预警数量（含等于）触发预警
        assertTrue(sql.contains("stock_qty &lt;= alert_threshold"));
    }
}
