-- Phase A2: 通用审批中心（V1 仅接入 cps_inspection_plan）
-- 业务可扩展点：INSPECTION_PLAN / ISSUE_RECTIFY / ROOM_CHECK_RECTIFY / OTHER

CREATE TABLE IF NOT EXISTS cps_approval_instance (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  biz_type VARCHAR(40) NOT NULL COMMENT '业务类型：INSPECTION_PLAN ...',
  biz_id BIGINT NOT NULL COMMENT '业务实体 ID',
  biz_title VARCHAR(200) NULL COMMENT '业务标题（审批中心展示用）',
  biz_creator_emp_no VARCHAR(40) NULL,
  biz_creator_emp_name VARCHAR(80) NULL,
  biz_factory VARCHAR(80) NULL,
  biz_area VARCHAR(80) NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / APPROVED / REJECTED / WITHDRAWN',
  current_approver_emp_no VARCHAR(40) NULL COMMENT '当前待审人；null=任意超管可审',
  current_approver_emp_name VARCHAR(80) NULL,
  last_action_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  approved_at DATETIME NULL,
  rejected_at DATETIME NULL,
  reject_reason VARCHAR(1000) NULL,
  biz_snapshot TEXT NULL COMMENT 'JSON 快照：审批时业务数据的样子',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_biz (biz_type, biz_id),
  KEY idx_status_lastaction (status, last_action_at),
  KEY idx_approver_status (current_approver_emp_no, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '通用审批实例';

CREATE TABLE IF NOT EXISTS cps_approval_step (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  instance_id BIGINT NOT NULL,
  seq INT NOT NULL COMMENT '步骤序号（1=首次提交）',
  actor_emp_no VARCHAR(40) NOT NULL,
  actor_emp_name VARCHAR(80) NULL,
  action VARCHAR(20) NOT NULL COMMENT 'SUBMIT / APPROVE / REJECT / TRANSFER / WITHDRAW',
  comment VARCHAR(2000) NULL,
  from_status VARCHAR(16) NULL,
  to_status VARCHAR(16) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_instance_seq (instance_id, seq),
  KEY idx_actor_created (actor_emp_no, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '审批步骤历史';
