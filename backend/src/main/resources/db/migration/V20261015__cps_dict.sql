-- =====================================================================
-- 通用字典（cps_dict）：选项类配置的唯一事实源
-- ---------------------------------------------------------------------
-- 口径：
--   1) dict_type + item_value 唯一；dict_type 命名 [A-Z][A-Z0-9_]；
--   2) builtin=1 内置项：不可删除、不可停用、不可改 dict_type/item_value
--      （可改 label/sort_no/remark），用于后端强校验依赖的组（RISK_LEVEL）；
--   3) 后端校验接入点（查启用项）：
--      room.roomType→ROOM_TYPE；room.riskLevel→RISK_LEVEL；
--      checkItem.photoCategory→PHOTO_CATEGORY；
--      checkItem.applicableRoomTypes 逐值→ROOM_TYPE；
--      planType / frequency 仅供 admin 下拉，后端不校验（AI C-07 路径兼容）；
--   4) 新增 dict_type 由前端新建表单直接创建（allowCreate），天然支持扩展。
-- =====================================================================

CREATE TABLE cps_dict (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  dict_type VARCHAR(64) NOT NULL COMMENT '字典类型（大写蛇形，如 ROOM_TYPE）',
  item_value VARCHAR(128) NOT NULL COMMENT '选项值（存库值，如 配电房 / HIGH / DAILY）',
  item_label VARCHAR(128) NOT NULL COMMENT '选项显示名（如 高风险 / 每日）',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '排序号（同组内升序）',
  enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用：0=停用（校验与下拉均不可见）',
  builtin TINYINT(1) NOT NULL DEFAULT 0 COMMENT '内置保护：1=不可删/不可停/不可改值与类型',
  remark VARCHAR(255) NULL COMMENT '备注（用途说明）',
  created_by VARCHAR(40) NULL COMMENT '创建人工号',
  updated_by VARCHAR(40) NULL COMMENT '最后更新人工号',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uk_cps_dict_type_value (dict_type, item_value),
  INDEX idx_cps_dict_type_sort (dict_type, enabled, sort_no)
) COMMENT='CPS通用字典表（选项类配置唯一事实源：房型/风险等级/照片分类/计划类型/频次）';

-- 种子数据
INSERT INTO cps_dict (dict_type, item_value, item_label, sort_no, builtin, remark, created_by, updated_by) VALUES
  -- 房型（值=显示名，房间 room_type / 点检项 applicable_room_types 直接存中文值）
  ('ROOM_TYPE', '配电房',   '配电房',   1, 0, '辅房房型', 'SYSTEM', 'SYSTEM'),
  ('ROOM_TYPE', '水泵房',   '水泵房',   2, 0, '辅房房型', 'SYSTEM', 'SYSTEM'),
  ('ROOM_TYPE', '空调机房', '空调机房', 3, 0, '辅房房型', 'SYSTEM', 'SYSTEM'),
  ('ROOM_TYPE', '电梯机房', '电梯机房', 4, 0, '辅房房型', 'SYSTEM', 'SYSTEM'),
  ('ROOM_TYPE', '消防泵房', '消防泵房', 5, 0, '辅房房型', 'SYSTEM', 'SYSTEM'),
  ('ROOM_TYPE', '柴发机房', '柴发机房', 6, 0, '辅房房型', 'SYSTEM', 'SYSTEM'),
  ('ROOM_TYPE', '库房',     '库房',     7, 0, '辅房房型', 'SYSTEM', 'SYSTEM'),
  ('ROOM_TYPE', '其他',     '其他',     8, 0, '辅房房型', 'SYSTEM', 'SYSTEM'),
  -- 风险等级（内置保护：房间校验依赖，三项不可删/停）
  ('RISK_LEVEL', 'HIGH',   '高风险', 1, 1, '房间风险等级（后端强校验依赖）', 'SYSTEM', 'SYSTEM'),
  ('RISK_LEVEL', 'MEDIUM', '中风险', 2, 1, '房间风险等级（后端强校验依赖）', 'SYSTEM', 'SYSTEM'),
  ('RISK_LEVEL', 'LOW',    '低风险', 3, 1, '房间风险等级（后端强校验依赖）', 'SYSTEM', 'SYSTEM'),
  -- 照片分类（示例，可自由增删）
  ('PHOTO_CATEGORY', '现场全景', '现场全景', 1, 0, '点检拍照分类（示例）', 'SYSTEM', 'SYSTEM'),
  ('PHOTO_CATEGORY', '设备状态', '设备状态', 2, 0, '点检拍照分类（示例）', 'SYSTEM', 'SYSTEM'),
  ('PHOTO_CATEGORY', '消防器材', '消防器材', 3, 0, '点检拍照分类（示例）', 'SYSTEM', 'SYSTEM'),
  ('PHOTO_CATEGORY', '仪表读数', '仪表读数', 4, 0, '点检拍照分类（示例）', 'SYSTEM', 'SYSTEM'),
  -- 计划类型（仅 admin 下拉展示，后端不校验）
  ('PLAN_TYPE', 'MANUAL',         '手动专项',   1, 0, '巡检计划类型（下拉）', 'SYSTEM', 'SYSTEM'),
  ('PLAN_TYPE', 'SPECIAL',        '专项检查',   2, 0, '巡检计划类型（下拉）', 'SYSTEM', 'SYSTEM'),
  ('PLAN_TYPE', 'WEEKLY_CHECK',   '周辅房点检', 3, 0, '巡检计划类型（下拉）', 'SYSTEM', 'SYSTEM'),
  ('PLAN_TYPE', 'WEEKLY_PATROL',  '周区域巡检', 4, 0, '巡检计划类型（下拉）', 'SYSTEM', 'SYSTEM'),
  ('PLAN_TYPE', 'WEEKLY_RECTIFY', '周整改复查', 5, 0, '巡检计划类型（下拉）', 'SYSTEM', 'SYSTEM'),
  -- 频次（仅 admin 下拉展示，后端不校验）
  ('FREQUENCY', 'DAILY',     '每日',   1, 0, '任务频次（下拉）', 'SYSTEM', 'SYSTEM'),
  ('FREQUENCY', 'WEEKLY',    '每周',   2, 0, '任务频次（下拉）', 'SYSTEM', 'SYSTEM'),
  ('FREQUENCY', 'MONTHLY',   '每月',   3, 0, '任务频次（下拉）', 'SYSTEM', 'SYSTEM'),
  ('FREQUENCY', 'QUARTERLY', '每季度', 4, 0, '任务频次（下拉）', 'SYSTEM', 'SYSTEM');
