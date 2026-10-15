-- =====================================================================
-- 波次14 B8：评分规则配置（cps_scoring_rule）
-- ---------------------------------------------------------------------
-- 口径：
--   1) rule_key 唯一 + factory NULL = 全局默认规则；
--      rule_key + factory 唯一 = 工厂校准规则（factory_calibration_flag=1）；
--   2) rule_value JSON 格式：{"score": 100} / {"deduct": 40}；
--   3) version + effective_from / effective_to 支持规则版本生效窗口
--      （服务层按 as-of date 选最高 version 且 effective_from<=today<=effective_to）；
--   4) 种子数据：5 条全局默认规则（PRD §23.1 base-factory 口径）。
-- =====================================================================

CREATE TABLE cps_scoring_rule (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  rule_key VARCHAR(64) NOT NULL
      COMMENT '规则键：base/content_mismatch_deduct/evidence_vague_deduct/'
              'keywords_missing_deduct/other_deduct',
  factory VARCHAR(40) NULL
      COMMENT '工厂标识（NULL=全局默认；非空=工厂校准规则）',
  rule_value VARCHAR(500) NOT NULL
      COMMENT '规则值JSON（如 {"score":100} 或 {"deduct":40}）',
  version INT NOT NULL DEFAULT 1 COMMENT '规则版本号（同 rule_key+factory 单调递增）',
  effective_from DATE NULL COMMENT '生效开始日期（NULL=不限）',
  effective_to DATE NULL COMMENT '生效结束日期（NULL=不限，open-end）',
  factory_calibration_flag TINYINT(1) NOT NULL DEFAULT 0
      COMMENT '是否工厂校准规则：1=factory非空且仅在该factory生效；0=全局默认',
  updated_by VARCHAR(40) NULL COMMENT '最后更新人工号',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uk_cps_scoring_rule_key_factory (rule_key, factory),
  INDEX idx_cps_scoring_rule_factory_flag (factory, factory_calibration_flag),
  INDEX idx_cps_scoring_rule_effective (effective_from, effective_to),
  CONSTRAINT chk_cps_scoring_rule_factory_calibration CHECK (
    (factory_calibration_flag = 1 AND factory IS NOT NULL)
    OR (factory_calibration_flag = 0 AND factory IS NULL)
  )
) COMMENT='CPS评分规则配置表（B8：默认+工厂校准，version+effective窗口管理）';

-- 种子数据：5 条全局默认规则（PRD §23.1 base-factory 口径）
INSERT INTO cps_scoring_rule (rule_key, factory, rule_value, version, factory_calibration_flag, updated_by) VALUES
  ('base',                      NULL, '{"score":100}', 1, 0, 'SYSTEM'),
  ('content_mismatch_deduct',   NULL, '{"deduct":40}', 1, 0, 'SYSTEM'),
  ('evidence_vague_deduct',     NULL, '{"deduct":30}', 1, 0, 'SYSTEM'),
  ('keywords_missing_deduct',   NULL, '{"deduct":20}', 1, 0, 'SYSTEM'),
  ('other_deduct',              NULL, '{"deduct":10}', 1, 0, 'SYSTEM');
