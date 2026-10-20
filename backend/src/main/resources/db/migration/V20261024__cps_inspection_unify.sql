-- ============================================================================
-- Phase C1: 巡检功能重构 —— 去除辅房点检独立功能，融合为统一巡检模型
--
-- 模型：巡检类型(cps_inspection_type, 加 巡检内容/是否分区域/直挂点检项)
--        └─ 分区域=是 → 巡检区域(cps_inspection_area, 泛化自 cps_room)
--             └─ 区域挂点检项(check_item_ids 逗号分隔)
--        └─ 分区域=否 → 点检项直接挂在类型上(type.check_item_ids)
--       点检项(cps_check_item, 加 点检类型多选/拍照最低张数/拍照判定条件)
--       执行记录统一到 cps_inspection_record(_item)，存量辅房点检记录迁入。
-- ============================================================================

-- ---------- 1) 巡检类型：加固定业务字段 ----------
ALTER TABLE cps_inspection_type
  ADD COLUMN content VARCHAR(1000) NULL COMMENT '巡检内容（这个巡检做什么、怎么做）' AFTER name,
  ADD COLUMN zone_flag TINYINT NOT NULL DEFAULT 0 COMMENT '是否分区域：1=按区域执行（区域挂点检项），0=点检项直接挂类型' AFTER content,
  ADD COLUMN check_item_ids VARCHAR(1024) NULL COMMENT '不分区域时直挂的点检项编码（逗号分隔 cps_check_item.item_code）' AFTER zone_flag;

-- form_schema 保留列不再使用（历史问题 extra_data 兼容），仅注释说明
ALTER TABLE cps_inspection_type
  MODIFY COLUMN form_schema JSON NULL COMMENT '【已废弃 Phase C1】旧动态表单 schema；新模型用 zone_flag + check_item_ids';

-- ---------- 2) 点检项：加点检类型多选 + 拍照要求 ----------
ALTER TABLE cps_check_item
  ADD COLUMN check_types VARCHAR(100) NOT NULL DEFAULT 'PASS_FAIL'
      COMMENT '点检类型（多选逗号分隔）：PASS_FAIL是否合格/TEXT文本说明/PHOTO需拍照' AFTER content,
  ADD COLUMN photo_min_count INT NULL COMMENT '需拍照时最低张数（check_types 含 PHOTO 时必填）' AFTER check_types,
  ADD COLUMN photo_criteria VARCHAR(500) NULL COMMENT '拍照判定条件（自由文本，供 AI/人工判定参考）' AFTER photo_min_count,
  ADD COLUMN text_hint VARCHAR(200) NULL COMMENT '文本框提示语（check_types 含 TEXT 时展示）' AFTER photo_criteria;

-- ---------- 3) 巡检区域（泛化自辅房 cps_room） ----------
CREATE TABLE cps_inspection_area (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  area_code VARCHAR(64) NOT NULL COMMENT '区域唯一编码',
  type_code VARCHAR(40) NOT NULL COMMENT '所属巡检类型编码（cps_inspection_type.code）',
  area_name VARCHAR(100) NOT NULL COMMENT '区域名称（如房间名/拉线段名）',
  factory VARCHAR(40) NULL COMMENT '工厂',
  area VARCHAR(40) NULL COMMENT '区域（厂区内分区）',
  line_or_room_type VARCHAR(64) NULL COMMENT '拉线|房型：产线巡检填拉线，辅房类填房型',
  process VARCHAR(40) NULL COMMENT '工序（拉线场景；辅房场景可空）',
  building VARCHAR(40) NULL COMMENT '楼栋（辅房场景沿用）',
  door_no VARCHAR(40) NULL COMMENT '门牌号（辅房场景沿用）',
  risk_level VARCHAR(16) NOT NULL DEFAULT 'LOW' COMMENT '风险等级：HIGH/MEDIUM/LOW',
  keeper_emp_no VARCHAR(40) NOT NULL COMMENT '责任人工号',
  keeper_emp_name VARCHAR(40) NOT NULL COMMENT '责任人姓名',
  check_item_ids VARCHAR(1024) NULL COMMENT '本区域点检项编码（逗号分隔）；空=按点检项适用范围自动匹配',
  remark VARCHAR(500) NULL COMMENT '备注',
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
  source_room_id BIGINT NULL COMMENT '迁移来源 cps_room.id（追溯用）',
  created_by VARCHAR(64) NULL,
  updated_by VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_inspection_area_code (area_code),
  KEY idx_inspection_area_type (type_code, enabled),
  KEY idx_inspection_area_keeper (keeper_emp_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='巡检区域（统一模型：辅房/拉线皆为区域）';

-- ---------- 4) 统一巡检记录（替代 cps_room_check_record） ----------
CREATE TABLE cps_inspection_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  type_code VARCHAR(40) NOT NULL COMMENT '巡检类型编码快照',
  area_id BIGINT NULL COMMENT '巡检区域ID（分区域类型必填；不分区域为 NULL）',
  area_code VARCHAR(64) NULL COMMENT '区域编码快照',
  area_name VARCHAR(100) NULL COMMENT '区域名称快照',
  plan_task_id BIGINT NULL COMMENT '关联 cps_inspection_plan_task.id（计划性巡检；临时可空）',
  plan_id BIGINT NULL COMMENT '关联 cps_inspection_plan.id（冗余追溯）',
  check_emp_no VARCHAR(40) NOT NULL COMMENT '巡检人工号',
  check_emp_name VARCHAR(40) NULL COMMENT '巡检人姓名',
  record_status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
      COMMENT '状态机：PENDING/IN_PROGRESS/AWAIT_CONFIRM/JUDGED',
  judge_status VARCHAR(16) NULL COMMENT 'AI判定服务状态：SUCCESS/PENDING（降级）',
  score INT NULL COMMENT '得分=max(0,100-不合格扣分和)；降级待判定为NULL',
  started_at DATETIME NULL,
  submitted_at DATETIME NULL COMMENT '提交时间（自然周归属以此为准）',
  source_record_id BIGINT NULL COMMENT '迁移来源 cps_room_check_record.id（追溯用）',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_insp_record_type_area (type_code, area_id),
  KEY idx_insp_record_task (plan_task_id),
  KEY idx_insp_record_sub (submitted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统一巡检记录（一次执行一条；JUDGED 后锁定）';

CREATE TABLE cps_inspection_record_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  record_id BIGINT NOT NULL COMMENT '所属巡检记录ID',
  check_item_id BIGINT NOT NULL COMMENT '点检项ID',
  item_code VARCHAR(64) NOT NULL COMMENT '点检项编码快照',
  content VARCHAR(500) NOT NULL COMMENT '点检内容快照',
  check_types VARCHAR(100) NOT NULL DEFAULT 'PASS_FAIL' COMMENT '点检类型快照（多选逗号分隔）',
  photo_min_count INT NULL COMMENT '最低拍照张数快照',
  photo_criteria VARCHAR(500) NULL COMMENT '拍照判定条件快照',
  deduct_score INT NOT NULL DEFAULT 0 COMMENT '扣分快照',
  config_version INT NOT NULL DEFAULT 1 COMMENT '配置版本快照',
  pass_result VARCHAR(16) NULL COMMENT '是否合格：PASS/FAIL（check_types 含 PASS_FAIL）',
  text_value VARCHAR(1000) NULL COMMENT '文本说明（check_types 含 TEXT）',
  photo_object_key VARCHAR(255) NULL COMMENT '照片 object_key（多张逗号分隔）',
  photo_file_name VARCHAR(255) NULL COMMENT '照片文件名',
  photo_count INT NOT NULL DEFAULT 0 COMMENT '实际拍照张数',
  judge_result VARCHAR(16) NULL COMMENT 'AI判定：PASS/FAIL/TYPE_MISMATCH/UNJUDGEABLE/PENDING',
  judge_reason VARCHAR(500) NULL COMMENT 'AI判定理由',
  final_result VARCHAR(16) NULL COMMENT '最终计分结果',
  final_modified_by VARCHAR(40) NULL,
  final_modified_at DATETIME NULL,
  final_modify_reason VARCHAR(500) NULL,
  judged_at DATETIME NULL,
  source_item_id BIGINT NULL COMMENT '迁移来源 cps_room_check_record_item.id',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_insp_record_item (record_id, check_item_id),
  KEY idx_insp_record_item_record (record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统一巡检记录明细（记录×点检项）';

-- ---------- 5) 种子：辅房点检 → 巡检类型 ----------
INSERT INTO cps_inspection_type (code, name, content, zone_flag, description, icon, color, enabled, sort_order, form_schema, remark)
SELECT 'ROOM_CHECK', '辅房点检', '对厂区辅房（仓库/配电房/工具间等）按区域逐项点检，拍照留证并计分', 1,
       '由辅房点检功能融合而来（Phase C1）', 'home', 'green', 1, 5, JSON_OBJECT('fields', JSON_ARRAY()), '系统迁移预置'
WHERE NOT EXISTS (SELECT 1 FROM cps_inspection_type WHERE code = 'ROOM_CHECK');

-- 已有三类型（问题上报语义）默认不分区域，避免旧数据 zone_flag 歧义
UPDATE cps_inspection_type SET zone_flag = 0 WHERE code IN ('QUALITY','SAFETY','EQUIPMENT');

-- ---------- 6) 存量迁移：cps_room → cps_inspection_area ----------
INSERT INTO cps_inspection_area
  (area_code, type_code, area_name, factory, area, line_or_room_type, process,
   building, door_no, risk_level, keeper_emp_no, keeper_emp_name, check_item_ids,
   enabled, source_room_id, created_by, updated_by, created_at, updated_at)
SELECT room_code, 'ROOM_CHECK', room_name, factory, NULL, room_type, NULL,
       building, door_no, risk_level, keeper_emp_no, keeper_emp_name, check_item_ids,
       enabled, id, created_by, updated_by, created_at, updated_at
FROM cps_room
WHERE NOT EXISTS (SELECT 1 FROM cps_inspection_area a WHERE a.source_room_id = cps_room.id);

-- ---------- 7) 存量迁移：点检记录 → 统一巡检记录 ----------
INSERT INTO cps_inspection_record
  (type_code, area_id, area_code, area_name, plan_task_id, plan_id,
   check_emp_no, check_emp_name, record_status, judge_status, score,
   started_at, submitted_at, source_record_id, created_at, updated_at)
SELECT 'ROOM_CHECK',
       (SELECT a.id FROM cps_inspection_area a WHERE a.source_room_id = r.room_id LIMIT 1),
       r.room_code, r.room_name, r.plan_task_id, r.plan_id,
       r.check_emp_no, r.check_emp_name, r.record_status, r.judge_status, r.score,
       r.started_at, r.submitted_at, r.id, r.created_at, r.updated_at
FROM cps_room_check_record r
WHERE NOT EXISTS (SELECT 1 FROM cps_inspection_record n WHERE n.source_record_id = r.id);

INSERT INTO cps_inspection_record_item
  (record_id, check_item_id, item_code, content, check_types, photo_min_count, photo_criteria,
   deduct_score, config_version, pass_result, text_value,
   photo_object_key, photo_file_name, photo_count,
   judge_result, judge_reason, final_result,
   final_modified_by, final_modified_at, final_modify_reason, judged_at,
   source_item_id, created_at, updated_at)
SELECT (SELECT n.id FROM cps_inspection_record n WHERE n.source_record_id = ri.record_id LIMIT 1),
       ri.check_item_id, ri.item_code, ri.content,
       'PASS_FAIL,PHOTO', 1, NULL,
       ri.deduct_score, ri.config_version,
       CASE ri.final_result WHEN 'PASS' THEN 'PASS' WHEN 'FAIL' THEN 'FAIL' ELSE NULL END,
       NULL,
       ri.photo_object_key, ri.photo_file_name,
       CASE WHEN ri.photo_object_key IS NULL THEN 0 ELSE 1 END,
       ri.judge_result, ri.judge_reason, ri.final_result,
       ri.final_modified_by, ri.final_modified_at, ri.final_modify_reason, ri.judged_at,
       ri.id, ri.created_at, ri.updated_at
FROM cps_room_check_record_item ri
WHERE NOT EXISTS (SELECT 1 FROM cps_inspection_record_item n WHERE n.source_item_id = ri.id);

-- 旧点检项补默认点检类型（历史项都是 合格判定+拍照 模式）
UPDATE cps_check_item SET check_types = 'PASS_FAIL,PHOTO', photo_min_count = 1
WHERE check_types = 'PASS_FAIL' AND photo_category IS NOT NULL AND photo_category <> '';
