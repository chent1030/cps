-- =====================================================================
-- 波次5 线A：A3 审核裁决域 + A4 初审触发管理（PRD §28.4/§29.3；AC-18/27；D-21）
-- ---------------------------------------------------------------------
-- A3：人工裁决记录表（每提交版本至多一条裁决=幂等锚点；裁决写回 issue 终态
--     由既有 V2 状态机+lock_version 乐观锁承接，本表只做裁决留痕与关联快照）。
-- A4：触发配置单行表（自动触发开关+技术重试策略可配，DB 运行时可改）+
--     触发/回调/超时/接管/重触发/裁决事件流水表。
-- =====================================================================

-- 1) A3 人工裁决记录：维持/改判 相对 AI 意见的关联快照
CREATE TABLE cps_review_adjudication (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  issue_id BIGINT NOT NULL COMMENT '问题ID',
  version_no INT NOT NULL COMMENT '裁决针对的提交版本号',
  task_id BIGINT NULL COMMENT '该版本初审任务ID（无任务时 NULL）',
  reviewer_emp_no VARCHAR(40) NOT NULL COMMENT '裁决审核员工号',
  reviewer_emp_name VARCHAR(40) NULL COMMENT '裁决审核员姓名',
  decision VARCHAR(16) NOT NULL COMMENT 'APPROVE=通过关闭，REJECT=退回整改人员',
  ai_overall VARCHAR(16) NULL COMMENT '裁决时 AI 初审意见快照 PASS/PARTIAL/PROBLEM（无结果为 NULL）',
  ai_relation VARCHAR(16) NOT NULL
      COMMENT 'WITH_AI=维持AI意见，AGAINST_AI=改判，NO_AI_RESULT=无AI结果（运行中/失败/超时/接管）',
  reason VARCHAR(1000) NOT NULL COMMENT '裁决理由（必填）',
  from_status VARCHAR(32) NOT NULL COMMENT '裁决前问题状态',
  to_status VARCHAR(32) NOT NULL COMMENT '裁决后问题状态',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '裁决时间',
  UNIQUE KEY uk_review_adjudication_version (issue_id, version_no),
  INDEX idx_review_adjudication_issue (issue_id, created_at)
) COMMENT='人工裁决记录（AI 仅意见无决定权，PRD §28.1；裁决终态写回走 V2 状态机+乐观锁）';

-- 2) A4 触发配置：单行 GLOBAL（应用配置 cps.initial-review.* 为缺省回退）
CREATE TABLE cps_initial_review_config (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  config_key VARCHAR(32) NOT NULL COMMENT '配置键（当前仅 GLOBAL）',
  auto_trigger_enabled TINYINT(1) NOT NULL DEFAULT 1
      COMMENT '整改单提交时自动触发 AI 初审开关：0=仅建任务待手动触发（PENDING_DISPATCH）',
  max_retry_attempts INT NOT NULL DEFAULT 1
      COMMENT 'C-01 投递失败技术重试次数（D-21：重试1次；重试不重置计时）',
  retry_backoff_ms INT NOT NULL DEFAULT 3000 COMMENT '技术重试退避毫秒（0..60000）',
  timeout_seconds INT NULL
      COMMENT '接管阈值秒（覆盖应用配置；NULL=用 cps.initial-review.timeout-seconds）',
  updated_by VARCHAR(40) NULL COMMENT '最后修改人工号',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  UNIQUE KEY uk_initial_review_config_key (config_key)
) COMMENT='AI 初审触发配置（admin 端点运行时维护）';

INSERT INTO cps_initial_review_config
  (config_key, auto_trigger_enabled, max_retry_attempts, retry_backoff_ms, timeout_seconds, created_at, updated_at)
VALUES ('GLOBAL', 1, 1, 3000, NULL, NOW(), NOW());

-- 3) A4 事件流水：触发/投递/重试/回调/超时/接管/迟到/重触发/裁决
CREATE TABLE cps_initial_review_event (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  task_id BIGINT NOT NULL COMMENT '初审任务ID',
  issue_id BIGINT NOT NULL COMMENT '问题ID',
  version_no INT NOT NULL COMMENT '提交版本号',
  event_type VARCHAR(32) NOT NULL
      COMMENT 'TRIGGERED/DISPATCH_RETRY/DISPATCH_FAILED/CALLBACK_RECEIVED/CALLBACK_DUPLICATED/COMPLETED/FAILED/TIMEOUT_OPENED/TAKEN_OVER/LATE_RESULT/RETRIGGERED/ADJUDICATED',
  detail VARCHAR(500) NULL COMMENT '事件说明（错误码/意见概要/原因）',
  operator_emp_no VARCHAR(40) NULL COMMENT '操作者（SYSTEM=系统自动，其余为审核员/管理员工号）',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '事件时间',
  INDEX idx_initial_review_event_task (task_id, id),
  INDEX idx_initial_review_event_issue (issue_id, id)
) COMMENT='AI 初审事件流水（触发记录：查看触发/回调/超时接管全链路）';
