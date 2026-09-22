-- 波次1 线A1：整改域数据底座（PRD §28 问题整改流程 v1.2 / §29 初审规则 / 详细设计 §2.2）
-- 约束：旧流程语义冻结；新流程通过 cps_issue.flow_version 路由，存量行默认 legacy 不受影响。

-- 1) cps_issue：流程版本路由列 + V2 整改域字段
ALTER TABLE cps_issue
    ADD COLUMN flow_version VARCHAR(16) NOT NULL DEFAULT 'legacy'
        COMMENT '流程版本：legacy=旧流程（冻结），v2=整改域新流程'
        AFTER agent_inspection_id,
    ADD COLUMN short_term_measure VARCHAR(1000) NULL
        COMMENT '短期措施（V2 整改域；旧单继续只读 corrective_measure）'
        AFTER corrective_measure,
    ADD COLUMN long_term_measure VARCHAR(1000) NULL
        COMMENT '长期措施（V2 整改域）'
        AFTER short_term_measure,
    ADD COLUMN current_submission_version INT NOT NULL DEFAULT 0
        COMMENT '当前整改提交版本号（V2；0=尚未提交过）'
        AFTER long_term_measure,
    ADD COLUMN inspection_item_id BIGINT NULL
        COMMENT '巡检事项ID（W4 事项域建表后回填关联）'
        AFTER current_submission_version,
    ADD INDEX idx_cps_issue_flow_version (flow_version);

-- 2) 整改提交版本表：提交即快照+锁定；退回重提生成新版本（PRD §28.3）
CREATE TABLE cps_rectification_submission (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  issue_id BIGINT NOT NULL COMMENT '问题ID',
  version_no INT NOT NULL COMMENT '提交版本号，从 1 递增',
  reason VARCHAR(1000) NULL COMMENT '原因分析（本版本快照）',
  short_term_measure VARCHAR(1000) NULL COMMENT '短期措施（本版本快照）',
  long_term_measure VARCHAR(1000) NULL COMMENT '长期措施（本版本快照）',
  responsible_emp_no VARCHAR(40) NULL COMMENT '责任员工工号（提交时快照）',
  responsible_emp_name VARCHAR(40) NULL COMMENT '责任员工姓名（提交时快照）',
  attachment_ids JSON NULL COMMENT '本版本证据附件：{"before":[ISSUE阶段ID],"after":[PROOF阶段ID]}',
  submitted_by VARCHAR(40) NOT NULL COMMENT '提交人工号（整改办理人）',
  submitted_name VARCHAR(40) NULL COMMENT '提交人姓名',
  submitted_at DATETIME NOT NULL COMMENT '提交时间',
  source VARCHAR(16) NOT NULL DEFAULT 'SUBMIT' COMMENT 'SUBMIT=首次提交，RESUBMIT=退回后重新提交',
  status VARCHAR(16) NOT NULL DEFAULT 'LOCKED' COMMENT 'LOCKED=版本锁定，REVIEWED=已有人工裁决，SUPERSEDED=已被新版本取代',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uk_rect_submission_issue_version (issue_id, version_no),
  INDEX idx_rect_submission_issue (issue_id, status)
) COMMENT='整改提交版本表（提交即快照+锁定，暂存不生成版本）';

-- 3) AI 初审任务表：Java 为唯一状态真相源（PRD §28.4 运行中/执行失败/超时可接管三态）
CREATE TABLE cps_initial_review_task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  issue_id BIGINT NOT NULL COMMENT '问题ID',
  submission_id BIGINT NOT NULL COMMENT '整改提交版本ID',
  version_no INT NOT NULL COMMENT '提交版本号（初审结果按版本隔离）',
  status VARCHAR(24) NOT NULL DEFAULT 'RUNNING'
      COMMENT 'RUNNING/FAILED/TIMEOUT_OPEN/COMPLETED/TAKEN_OVER/LATE_RESULT',
  review_task_ref VARCHAR(128) NULL COMMENT 'Python 侧初审任务引用（C-01 响应返回）',
  idempotency_key VARCHAR(64) NOT NULL COMMENT '投递幂等键 cps-rectify-{issueId}-v{n}',
  submitted_at DATETIME NOT NULL COMMENT '计时起点=提交成功时刻（PRD §28.4）',
  timeout_at DATETIME NOT NULL COMMENT '接管阈值时刻=submitted_at+600s（可配置）',
  completed_at DATETIME NULL COMMENT '终态时间（COMPLETED/FAILED/TAKEN_OVER/LATE_RESULT）',
  taken_over_by VARCHAR(40) NULL COMMENT '接管审核员工号',
  taken_over_name VARCHAR(40) NULL COMMENT '接管审核员姓名',
  taken_over_at DATETIME NULL COMMENT '接管时间',
  takeover_reason VARCHAR(500) NULL COMMENT '接管原因（必填）',
  error_code VARCHAR(64) NULL COMMENT '投递/执行失败错误码',
  retry_count INT NOT NULL DEFAULT 0 COMMENT '技术重试次数（≤2，不重置计时）',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uk_initial_review_task_version (issue_id, version_no),
  UNIQUE KEY uk_initial_review_task_idem (idempotency_key),
  INDEX idx_initial_review_task_scan (status, timeout_at)
) COMMENT='AI 初审任务表（30s 扫描：RUNNING 且超时→TIMEOUT_OPEN）';

-- 4) AI 初审结果表：仅意见无决定权（PRD §29.3）；一个任务至多一条结果，迟到结果置 is_late
CREATE TABLE cps_initial_review_result (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  task_id BIGINT NOT NULL COMMENT '初审任务ID',
  submission_id BIGINT NOT NULL COMMENT '整改提交版本ID',
  issue_id BIGINT NOT NULL COMMENT '问题ID',
  version_no INT NOT NULL COMMENT '提交版本号',
  overall VARCHAR(16) NULL COMMENT 'PASS/PARTIAL/PROBLEM（仅总览意见，无决定权）',
  model_status VARCHAR(32) NULL COMMENT '模型执行状态（回调上报）',
  is_late TINYINT(1) NOT NULL DEFAULT 0 COMMENT '迟到结果留痕：1=人工接管后到达，仅参考不覆盖裁决',
  callback_idempotency_key VARCHAR(128) NULL COMMENT '回调幂等键 initial-review-result-{taskId}',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  UNIQUE KEY uk_initial_review_result_task (task_id),
  INDEX idx_initial_review_result_issue (issue_id, version_no)
) COMMENT='AI 初审结果表（重复回调按唯一键去重，不重复写）';

-- 5) AI 初审逐项意见明细：L/P 实际值落库供核对（PRD §29.2/29.3；缺失记 SKIPPED 不伪造通过）
CREATE TABLE cps_initial_review_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  review_id BIGINT NOT NULL COMMENT '初审结果ID',
  task_id BIGINT NOT NULL COMMENT '初审任务ID',
  check_type VARCHAR(32) NOT NULL
      COMMENT 'IMAGE_COMPARE/MEASURE_SIMILARITY/TEXT_VALIDITY/TEXT_LENGTH/PUNCTUATION_RATIO',
  field_name VARCHAR(32) NULL COMMENT 'TEXT_LENGTH/PUNCTUATION_RATIO 适用：reason/short_term/long_term',
  verdict VARCHAR(16) NOT NULL COMMENT 'PASS/FAIL/WARN/SKIPPED（SKIPPED=检查缺失，不伪造通过）',
  text_length INT NULL COMMENT 'L：文本长度实际值（含空格标点，换行不计）',
  punctuation_count INT NULL COMMENT 'P：标点出现次数实际值',
  ratio_ok TINYINT(1) NULL COMMENT '10×P≤L 是否满足（等于10%满足）',
  reason VARCHAR(1000) NULL COMMENT '判断理由',
  problem_fragment VARCHAR(500) NULL COMMENT '问题片段引用',
  evidence_refs JSON NULL COMMENT '图片引用列表（attachment_id/object_key）',
  confidence DECIMAL(6,4) NULL COMMENT '模型置信度 0-1',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  INDEX idx_initial_review_item_review (review_id),
  INDEX idx_initial_review_item_task (task_id)
) COMMENT='AI 初审逐项意见明细（L/P 实际值落库）';

-- 6) 整改转办记录表：转办后仅当前承办人办理（PRD §28.3）
CREATE TABLE cps_rectification_transfer (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  issue_id BIGINT NOT NULL COMMENT '问题ID',
  version_no INT NULL COMMENT '转办时所在提交版本（未提交过为 NULL）',
  from_emp_no VARCHAR(40) NOT NULL COMMENT '原办理人工号',
  from_emp_name VARCHAR(40) NULL COMMENT '原办理人姓名',
  to_emp_no VARCHAR(40) NOT NULL COMMENT '新办理人工号',
  to_emp_name VARCHAR(40) NULL COMMENT '新办理人姓名',
  transferred_at DATETIME NOT NULL COMMENT '转办时间',
  remark VARCHAR(500) NULL COMMENT '转办备注',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  INDEX idx_rect_transfer_issue (issue_id, transferred_at)
) COMMENT='整改转办记录表（转办不改变责任员工，原办理人失权）';
