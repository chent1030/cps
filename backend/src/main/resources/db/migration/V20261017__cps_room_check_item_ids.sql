-- 波次：辅房点检项独立配置
-- 口径编号：B1-EXT（用户口径：先配置点检项，再在新建/编辑辅房时勾选该房间要点检的项目）
-- cps_room.check_item_ids：房间独立配置的点检项编号（逗号分隔 cps_check_item.item_code）
-- NULL/空 = 未配置，回退按 cps_check_item.applicable_room_types 房型匹配（兼容存量数据与"全部房型"用法）
ALTER TABLE cps_room
    ADD COLUMN check_item_ids VARCHAR(1024) NULL COMMENT '房间独立配置的点检项编号（逗号分隔）；空=按房型自动匹配' AFTER risk_level;
