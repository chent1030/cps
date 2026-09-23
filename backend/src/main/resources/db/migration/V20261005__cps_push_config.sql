-- =====================================================================
-- 波次9 F线：D-22 推送渠道配置（推送未配置 → 配置 cps_push_config）
-- ---------------------------------------------------------------------
-- 1) 单行 GLOBAL 配置（与 cps_initial_review_config 同模式）；
-- 2) channel 枚举：INTERFACE / EMAIL / SMS / WEBHOOK
--    本期仅 INTERFACE 用，其他为预留；
-- 3) 启用同渠道只一行（生成列 + UNIQUE：仅当 enabled=true 触发约束）；
-- 4) secret_ref 仅存 KMS/外部密钥引用，密钥明文不入库。
-- =====================================================================

CREATE TABLE cps_push_config (
  id BIGINT PRIMARY KEY COMMENT '主键；单行配置恒为 1（chk_cps_push_config_singleton 兜底）',
  channel VARCHAR(32) NOT NULL
      COMMENT '推送渠道：INTERFACE（本期用）/EMAIL/SMS/WEBHOOK（预留）',
  endpoint VARCHAR(500) NULL COMMENT '推送接口地址（启用时必填）',
  secret_ref VARCHAR(100) NULL COMMENT '密钥引用（KMS/外部密钥管理；明文不入库）',
  enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用：1启用，0=推送未配置（默认）',
  updated_by VARCHAR(40) NULL COMMENT '最后修改人工号',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  -- 生成列：仅 enabled=true 携带 channel 用于 UNIQUE 约束；disabled 行不入 UNIQUE
  enabled_channel VARCHAR(32) GENERATED ALWAYS AS (CASE WHEN enabled = 1 THEN channel ELSE NULL END) STORED,
  UNIQUE KEY uk_cps_push_config_enabled_channel (enabled_channel),
  CONSTRAINT chk_cps_push_config_singleton CHECK (id = 1),
  CONSTRAINT chk_cps_push_config_channel CHECK (
    channel IN ('INTERFACE', 'EMAIL', 'SMS', 'WEBHOOK')
  )
) COMMENT='CPS 推送渠道配置（单行 GLOBAL；缺失/禁用=推送未配置）';

-- 默认种子：INTERFACE 渠道禁用、endpoint 空 = "推送未配置"（记忆体系 FR-09 检索时据此判定）
INSERT INTO cps_push_config (id, channel, enabled, endpoint)
VALUES (1, 'INTERFACE', 0, '');