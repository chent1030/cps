-- 波次4 B3：辅房点检执行域（PRD §22-24）
-- 点检单（房间×一次执行）+ 明细（房间×点检项，快照配置）。B4 评分排名直接聚合本表（无快照表）。

CREATE TABLE cps_room_check_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  plan_task_id BIGINT NULL COMMENT '关联 cps_inspection_plan_task.id（INSPECT_CHECK；临时点检可为空）',
  plan_id BIGINT NULL COMMENT '关联 cps_inspection_plan.id（冗余便于追溯）',
  room_id BIGINT NOT NULL COMMENT '点检房间ID（cps_room.id）',
  room_code VARCHAR(64) NOT NULL COMMENT '房间编码快照',
  room_name VARCHAR(100) NOT NULL COMMENT '房间名称快照',
  check_emp_no VARCHAR(40) NOT NULL COMMENT '点检人工号（登录身份，PRD 24.2.1）',
  check_emp_name VARCHAR(40) NULL COMMENT '点检人姓名（暂用工号占位，同波次2口径）',
  record_status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
      COMMENT '状态机：PENDING待执行/IN_PROGRESS执行中/JUDGED已判定（提交锁定，PRD 24.2.4）',
  judge_status VARCHAR(16) NULL COMMENT '判定服务状态：SUCCESS判定成功/PENDING降级待判定（Python C-04 未起）',
  score INT NULL COMMENT '单次最终得分=max(0,100-不合格适用项扣分和)；降级待判定为NULL不计入排名',
  started_at DATETIME NULL COMMENT '开始执行时间',
  submitted_at DATETIME NULL COMMENT '提交（判定）时间=检查完成时间；自然周归属以此为准（D-08）',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  INDEX idx_room_check_record_task_room (plan_task_id, room_id),
  INDEX idx_room_check_record_room_sub (room_id, submitted_at),
  INDEX idx_room_check_record_sub (submitted_at)
) COMMENT='辅房点检单（B3：一房间一次点检一条；JUDGED 后锁定不可改）';

CREATE TABLE cps_room_check_record_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  record_id BIGINT NOT NULL COMMENT '所属点检单ID',
  room_id BIGINT NOT NULL COMMENT '房间ID（冗余）',
  check_item_id BIGINT NOT NULL COMMENT '点检项ID（cps_check_item.id）',
  item_code VARCHAR(64) NOT NULL COMMENT '点检项编码快照',
  content VARCHAR(500) NOT NULL COMMENT '点检内容快照（PRD 23.2：执行记录引用检查时配置）',
  photo_category VARCHAR(40) NOT NULL COMMENT '照片对象类别快照',
  deduct_score INT NOT NULL DEFAULT 0 COMMENT '不合格扣分值快照',
  config_version INT NOT NULL DEFAULT 1 COMMENT '检查时配置版本快照',
  photo_object_key VARCHAR(255) NULL COMMENT '照片 RustFS object_key（对齐 A1 附件机制）',
  photo_file_name VARCHAR(255) NULL COMMENT '照片原始文件名',
  judge_result VARCHAR(16) NULL COMMENT 'AI 判定原始结果：TYPE_MISMATCH类型不符/UNJUDGEABLE无法判定/PASS合格/FAIL不合格/PENDING降级待判定',
  judge_reason VARCHAR(500) NULL COMMENT 'AI 判定理由',
  final_result VARCHAR(16) NULL COMMENT '最终计分结果（本期=judge_result；人工修正入口预留）',
  judged_at DATETIME NULL COMMENT '判定时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uk_room_check_item (record_id, check_item_id),
  INDEX idx_room_check_item_record (record_id)
) COMMENT='辅房点检明细（房间×点检项；提交前可补照片，判定后锁定）';
