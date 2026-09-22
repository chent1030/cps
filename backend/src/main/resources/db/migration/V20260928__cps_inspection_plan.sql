-- =====================================================================
-- 波次3 D1 巡检计划草稿/审核 + D3 建单引擎（PRD §22；后端架构 §3.2/§3.3；
-- 契约 C-09 AC-30）。本期仅 Java 侧；Python 计划 Agent 经 C-07 投递草稿。
-- ---------------------------------------------------------------------
-- 口径（§22.1/§22.2，AC-30）：
--   1) D1：cps_inspection_plan 记录申请草稿 → 审核状态机
--      PENDING_REVIEW → APPROVED|REJECTED；APPROVED 后由 D3 事务创建三类任务。
--   2) D3：cps_inspection_plan_task 三类任务同表区分（task_type 枚举
--      INSPECT_RECTIFY/INSPECT_PATROL/INSPECT_CHECK），UNIQUE(plan_id, task_type)
--      兜底补建；批准事务内先查询已有 task_type，未存在则创建；失败仅补建。
--   3) 乐观锁 lock_version 走 CAS（与 cps_issue 一致）；config_version 自增
--      标识计划草稿版本——Python C-07 重投同 source_run_id 时幂等键
--      plan-draft-{sourceRunId} 拦截，Java 端以最新 draft_content_json 更新。
--   4) 任务状态 PENDING/IN_PROGRESS/COMPLETED/CANCELLED；任务创建后由
--      后续波次（B 线/E 线）的派发引擎接管，本期仅落库 + 状态落点。
--   5) 与 cps_issue 解耦：三类任务不直接创建 cps_issue 行，避免和现有
--      整改流状态机冲突；后续业务执行时再视需要触发 cps_issue。
-- =====================================================================

-- D1 巡检计划表（申请草稿 + 审核）
CREATE TABLE cps_inspection_plan (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  source_run_id VARCHAR(64) NULL COMMENT '来源周报运行ID（weekly_report_run.run_id）；非周报源则为 NULL',
  report_id VARCHAR(64) NULL COMMENT '报告/历史数据来源标识（按计划 Agent 上下文自定义）',
  plan_type VARCHAR(32) NOT NULL COMMENT '计划类型：WEEKLY_RECTIFY/WEEKLY_PATROL/WEEKLY_CHECK/MANUAL',
  title VARCHAR(200) NOT NULL COMMENT '计划标题',
  target_factory VARCHAR(40) NULL COMMENT '目标工厂',
  target_area VARCHAR(40) NULL COMMENT '目标区域',
  risk_basis VARCHAR(1000) NULL COMMENT '目标及风险依据',
  draft_content_json JSON NULL COMMENT '草稿内容 JSON（计划 Agent 生成；含任务列表/验收标准等）',
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING_REVIEW'
      COMMENT 'PENDING_REVIEW/APPROVED/REJECTED',
  draft_idempotency_key VARCHAR(96) NULL COMMENT 'C-07 幂等键 plan-draft-{sourceRunId}',
  config_version INT NOT NULL DEFAULT 1 COMMENT '草稿配置版本（每次 draft 更新自增）',
  lock_version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号（CAS，approve/reject 自增）',
  draft_by VARCHAR(40) NULL COMMENT '草稿生成者（Python 标识或人工触发工号）',
  approver VARCHAR(40) NULL COMMENT '审核人工号',
  approver_name VARCHAR(40) NULL COMMENT '审核人姓名',
  approved_at DATETIME NULL COMMENT '审核时间',
  reject_reason VARCHAR(500) NULL COMMENT '拒绝原因',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uk_cps_plan_source_run (source_run_id, plan_type),
  UNIQUE KEY uk_cps_plan_draft_idem (draft_idempotency_key),
  INDEX idx_cps_plan_status_created (status, created_at),
  INDEX idx_cps_plan_target (target_factory, target_area)
) COMMENT='巡检计划表（D1 申请草稿/审核；C-09 建单引擎来源）';

-- D3 计划任务表（三类任务同表区分；UNIQUE 幂等兜底）
CREATE TABLE cps_inspection_plan_task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  plan_id BIGINT NOT NULL COMMENT '所属计划 ID',
  task_type VARCHAR(24) NOT NULL
      COMMENT '任务类型：INSPECT_RECTIFY=整改复查 / INSPECT_PATROL=区域巡检 / INSPECT_CHECK=辅房点检',
  title VARCHAR(200) NOT NULL COMMENT '任务标题',
  target_emp_no VARCHAR(40) NULL COMMENT '负责人工号',
  target_emp_name VARCHAR(40) NULL COMMENT '负责人姓名',
  scheduled_at DATETIME NULL COMMENT '计划执行时间',
  frequency VARCHAR(40) NULL COMMENT '频次（ONCE/DAILY/WEEKLY/MONTHLY）',
  acceptance_criteria VARCHAR(500) NULL COMMENT '验收标准',
  evidence_requirement VARCHAR(500) NULL COMMENT '证据要求',
  task_status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
      COMMENT 'PENDING/IN_PROGRESS/COMPLETED/CANCELLED',
  reference_issue_id BIGINT NULL COMMENT '关联 cps_issue.id（整改复查任务关联原问题）',
  reference_object_key VARCHAR(200) NULL COMMENT '关联对象（房间/拉线/区域编码）',
  reference_object_type VARCHAR(24) NULL COMMENT '对象类型 ROOM/LINE/AREA',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uk_cps_plan_task_plan_type (plan_id, task_type) COMMENT '幂等：同计划同任务类型不重复建',
  INDEX idx_cps_plan_task_type (task_type, task_status),
  INDEX idx_cps_plan_task_emp (target_emp_no, task_status),
  INDEX idx_cps_plan_task_plan (plan_id)
) COMMENT='巡检计划任务表（D3 建单引擎落点；UNIQUE(plan_id,task_type) 幂等兜底）';
