-- =====================================================================
-- 波次12 B6：视觉点检后端（cps_vision_check_record + cps_vision_check_judge_event）
-- ---------------------------------------------------------------------
-- 口径：
--   1) cps_vision_check_record：一房间一检查项一次视觉点检一条；与 cps_room_check_record
--      （B3 辅房点检执行域，状态机 PENDING/IN_PROGRESS/JUDGED）业务不同，单独建表避免耦合；
--      room_type 复用 cps_room.storage_room_type 枚举（PRIMARY/STANDARD/SPECIAL/TOOL/OTHER）。
--   2) 状态机（视觉点检）：
--      PENDING → AI_JUDGING → AI_PASS/AI_FAIL
--                  ↘ TIMEOUT(超时未回)
--                  ↘ HUMAN_OVERRIDE(人工改判)
--      PENDING/TIMEOUT/AI_FAILED 可触发 rejudge；AI_JUDGING/AI_PASS/AI_FAIL 可改判；
--   3) judge_fingerprint = room_id|check_item_id|sha256(photo_url) 64-hex
--      —— 用于视觉判定幂等：同一指纹已 AI_PASS/AI_FAIL 直接返回，避免重复计费/计分；
--   4) judge_event 表 12 类流水（SUBMITTED/AI_STARTED/AI_COMPLETED/AI_FAILED/
--      TIMEOUT_OPENED/HUMAN_OVERRIDE/REJUDGED）对应 cps_initial_review_event 模式。
-- =====================================================================

CREATE TABLE cps_vision_check_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  room_id BIGINT NOT NULL COMMENT '房间ID（FK cps_room.id 逻辑关联）',
  check_item_id BIGINT NOT NULL COMMENT '点检项目ID（FK cps_inspection_item.id）',
  room_type VARCHAR(32) NOT NULL
      COMMENT '存储房类型：PRIMARY/STANDARD/SPECIAL/TOOL/OTHER（与 cps_room.storage_room_type 同枚举）',
  status VARCHAR(16) NOT NULL
      COMMENT '状态：PENDING/AI_JUDGING/AI_PASS/AI_FAIL/HUMAN_OVERRIDE/TIMEOUT',
  ai_overall VARCHAR(16) NULL
      COMMENT 'AI 判定意见：PASS/PARTIAL/PROBLEM',
  ai_score INT NULL COMMENT 'AI 评分（100 起始扣分，0-100）',
  ai_reason VARCHAR(500) NULL COMMENT 'AI 判定理由',
  photo_object_key VARCHAR(500) NULL COMMENT 'RustFS 对象 key',
  photo_url VARCHAR(1000) NULL COMMENT 'CDN/可访问 URL',
  judge_fingerprint VARCHAR(64) NULL
      COMMENT '视觉判定幂等键（room_id|check_item_id|sha256(photo_url) 64-hex）',
  human_override_emp_no VARCHAR(40) NULL COMMENT '人工改判工号',
  human_override_reason VARCHAR(500) NULL COMMENT '人工改判理由',
  created_by VARCHAR(40) NULL COMMENT '创建人工号',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  INDEX idx_cps_vision_check_room_created (room_id, created_at),
  INDEX idx_cps_vision_check_item_status (check_item_id, status),
  INDEX idx_cps_vision_check_status_updated (status, updated_at),
  INDEX idx_cps_vision_check_fingerprint (judge_fingerprint),
  CONSTRAINT chk_cps_vision_check_room_type CHECK (
    room_type IN ('PRIMARY', 'STANDARD', 'SPECIAL', 'TOOL', 'OTHER')
  ),
  CONSTRAINT chk_cps_vision_check_status CHECK (
    status IN ('PENDING', 'AI_JUDGING', 'AI_PASS', 'AI_FAIL',
               'HUMAN_OVERRIDE', 'TIMEOUT')
  ),
  CONSTRAINT chk_cps_vision_check_ai_overall CHECK (
    ai_overall IS NULL OR ai_overall IN ('PASS', 'PARTIAL', 'PROBLEM')
  ),
  CONSTRAINT chk_cps_vision_check_ai_score CHECK (
    ai_score IS NULL OR (ai_score BETWEEN 0 AND 100)
  )
) COMMENT='CPS视觉点检记录表（B6：房间×点检项目×照片）';

CREATE TABLE cps_vision_check_judge_event (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '事件ID（自增）',
  judge_fingerprint VARCHAR(64) NOT NULL
      COMMENT '关联 cps_vision_check_record.judge_fingerprint（视觉判定幂等键）',
  event_type VARCHAR(32) NOT NULL
      COMMENT '事件类型：SUBMITTED/AI_STARTED/AI_COMPLETED/AI_FAILED/TIMEOUT_OPENED/HUMAN_OVERRIDE/REJUDGED',
  detail VARCHAR(500) NULL COMMENT '事件明细（如 AI reason、错误描述等）',
  operator_emp_no VARCHAR(40) NULL COMMENT '操作人工号（人工改判/重新触发）',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '事件时间',
  INDEX idx_cps_vision_check_event_fp (judge_fingerprint, id),
  CONSTRAINT chk_cps_vision_check_event_type CHECK (
    event_type IN ('SUBMITTED', 'AI_STARTED', 'AI_COMPLETED', 'AI_FAILED',
                   'TIMEOUT_OPENED', 'HUMAN_OVERRIDE', 'REJUDGED')
  )
) COMMENT='CPS视觉点检事件流水（B6：12类状态转换流水）';