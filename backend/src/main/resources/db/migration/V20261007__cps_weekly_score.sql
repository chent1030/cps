-- =====================================================================
-- 波次14 B4：周评分汇总与明细（cps_weekly_score + cps_weekly_score_line）
-- ---------------------------------------------------------------------
-- 口径：
--   1) cps_weekly_score：一员工一周一条汇总；
--      - week_start_date：自然周周一（Asia/Shanghai 00:00 界定），如 2026-10-05；
--      - emp_no + week_start_date 唯一（同一员工同一周只一条汇总）；
--      - region_supervisor_id 冗余存储，便于按区域督导分组排名；
--      - total_score 范围 [0,100]；base=100 + 各项 score_delta 之和（CHK 约束）；
--      - natural_week_flag=true 即按自然周周日 23:59 @Scheduled 重算落地；
--   2) cps_weekly_score_line：每周评分明细行（一评分汇总对应 N 条明细）；
--      - item_id 关联 cps_scoring_rule.rule_key（语义标识，非 FK）；
--      - score_delta 可正可负（如 base=+100、content_mismatch_deduct=-40）；
--      - reason 记录触发原因（如 "01-15 仓库照片模糊"）；
--   3) 周日 23:59 触发重算：cps.weekly-score.cron 默认 "0 59 23 ? * SUN"，
--      Asia/Shanghai 由服务层 java.time 转换实现（DB 存 UTC 即可）。
-- =====================================================================

CREATE TABLE cps_weekly_score (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  week_start_date DATE NOT NULL
      COMMENT '自然周周一日期（Asia/Shanghai 00:00 界定），如 2026-10-05',
  emp_no VARCHAR(40) NOT NULL COMMENT '员工工号',
  emp_name VARCHAR(80) NULL COMMENT '员工姓名（冗余）',
  region_supervisor_id BIGINT NULL COMMENT '区域督导ID（冗余，按区域分组聚合排名）',
  region_supervisor_name VARCHAR(80) NULL COMMENT '区域督导姓名（冗余）',
  total_score INT NOT NULL DEFAULT 100
      COMMENT '总分=base(100)+sum(score_delta)，范围 [0,100]',
  room_check_count INT NOT NULL DEFAULT 0
      COMMENT '本周点检单数（B3 辅房点检已提交记录数）',
  photo_count INT NOT NULL DEFAULT 0
      COMMENT '本周点检照片数（cps_room_check_record_item 含照片项数）',
  natural_week_flag TINYINT(1) NOT NULL DEFAULT 1
      COMMENT '是否自然周重算：1=周日23:59 @Scheduled 落地；0=人工补录/补排',
  updated_by VARCHAR(40) NULL COMMENT '最后更新人工号',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uk_cps_weekly_score_week_emp (week_start_date, emp_no),
  INDEX idx_cps_weekly_score_week_supervisor (week_start_date, region_supervisor_id),
  INDEX idx_cps_weekly_score_week_score (week_start_date, total_score DESC),
  CONSTRAINT chk_cps_weekly_score_total_range CHECK (total_score BETWEEN 0 AND 100),
  CONSTRAINT chk_cps_weekly_score_room_count CHECK (room_check_count >= 0),
  CONSTRAINT chk_cps_weekly_score_photo_count CHECK (photo_count >= 0)
) COMMENT='CPS周评分汇总表（B4：按员工+自然周聚合）';

CREATE TABLE cps_weekly_score_line (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '明细ID',
  weekly_score_id BIGINT NOT NULL
      COMMENT '关联 cps_weekly_score.id（CASCADE：父删除则子一并删除）',
  item_id VARCHAR(64) NOT NULL
      COMMENT '评分项ID（关联 cps_scoring_rule.rule_key，语义标识非FK）',
  score_delta INT NOT NULL
      COMMENT '评分变化量（base=+100；deduct 系列为负值）',
  reason VARCHAR(500) NULL
      COMMENT '触发原因（如"01-15 仓库照片模糊"、"02-03 文本不一致"）',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  INDEX idx_cps_weekly_score_line_parent (weekly_score_id),
  CONSTRAINT chk_cps_weekly_score_line_delta_range CHECK (score_delta BETWEEN -100 AND 100)
) COMMENT='CPS周评分明细表（B4：一评分汇总对应N条评分项明细）';
