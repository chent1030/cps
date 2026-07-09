CREATE TABLE cps_issue (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  issue_no VARCHAR(40) NOT NULL UNIQUE COMMENT '问题单号',
  status VARCHAR(40) NOT NULL COMMENT '问题状态',
  factory VARCHAR(40) NOT NULL COMMENT '工厂',
  area VARCHAR(40) NOT NULL COMMENT '区域',
  line VARCHAR(40) NOT NULL COMMENT '拉线',
  process VARCHAR(40) NOT NULL COMMENT '工序',
  ai_category_l1_id BIGINT NULL COMMENT 'AI识别一级分类ID',
  ai_category_l2_id BIGINT NULL COMMENT 'AI识别二级分类ID',
  category_l1_id BIGINT NOT NULL COMMENT '最终一级分类ID',
  category_l2_id BIGINT NOT NULL COMMENT '最终二级分类ID',
  category_modified_flag TINYINT NOT NULL DEFAULT 0 COMMENT '分类是否被人工修改：1是，0否',
  description VARCHAR(1000) NOT NULL COMMENT '问题描述',
  creator_emp_no VARCHAR(40) NOT NULL COMMENT '创建人工号',
  feedback_emp_no VARCHAR(40) NOT NULL COMMENT '反馈人工号',
  responsible_emp_no VARCHAR(40) NULL COMMENT '整改责任人工号',
  proof_emp_no VARCHAR(40) NULL COMMENT '整改凭证上传人工号',
  reviewer_emp_no VARCHAR(40) NULL COMMENT '审核人工号',
  current_handler_emp_no VARCHAR(40) NULL COMMENT '当前处理人工号',
  creator_emp_name VARCHAR(40) NOT NULL COMMENT '创建人姓名',
  feedback_emp_name VARCHAR(40) NOT NULL COMMENT '反馈人姓名',
  responsible_emp_name VARCHAR(40) NULL COMMENT '整改责任人姓名',
  proof_emp_name VARCHAR(40) NULL COMMENT '整改凭证上传人姓名',
  reviewer_emp_name VARCHAR(40) NULL COMMENT '审核人姓名',
  current_handler_emp_name VARCHAR(40) NULL COMMENT '当前处理人姓名',
  reason_analysis VARCHAR(1000) NULL COMMENT '原因分析',
  corrective_measure VARCHAR(1000) NULL COMMENT '整改措施',
  rectify_remark VARCHAR(1000) NULL COMMENT '整改说明',
  review_opinion VARCHAR(1000) NULL COMMENT '审核意见',
  submit_time DATETIME NOT NULL COMMENT '提交时间',
  close_time DATETIME NULL COMMENT '关闭时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  INDEX idx_cps_issue_handler_status (current_handler_emp_no, status),
  INDEX idx_cps_issue_creator (creator_emp_no),
  INDEX idx_cps_issue_status_submit_time (status, submit_time)
) COMMENT='CPS巡检问题主表';

CREATE TABLE cps_issue_attachment (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  issue_id BIGINT NULL COMMENT '问题ID，未绑定问题时为空',
  stage VARCHAR(20) NULL COMMENT '附件阶段：ISSUE问题照片，PROOF整改凭证',
  file_url VARCHAR(500) NOT NULL COMMENT '文件访问地址或存储路径',
  file_name VARCHAR(200) NOT NULL COMMENT '文件名',
  file_type VARCHAR(80) NOT NULL COMMENT '文件类型',
  sort_no INT NULL COMMENT '排序号',
  created_by VARCHAR(40) NOT NULL COMMENT '上传人工号',
  created_name VARCHAR(40) NOT NULL COMMENT '上传人姓名',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
  UNIQUE KEY uk_cps_attachment_sort (issue_id, stage, sort_no),
  INDEX idx_cps_attachment_issue_stage (issue_id, stage)
) COMMENT='CPS巡检问题附件表';

CREATE TABLE cps_issue_ai_suggestion (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  issue_id BIGINT NOT NULL COMMENT '问题ID',
  source_attachment_id BIGINT NULL COMMENT 'AI识别来源附件ID，通常为第一张问题照片',
  ai_category_l1_id BIGINT NULL COMMENT 'AI建议一级分类ID',
  ai_category_l1_name VARCHAR(100) NULL COMMENT 'AI建议一级分类名称',
  ai_category_l2_id BIGINT NULL COMMENT 'AI建议二级分类ID',
  ai_category_l2_name VARCHAR(100) NULL COMMENT 'AI建议二级分类名称',
  reason_suggestion VARCHAR(1000) NULL COMMENT 'AI建议原因',
  measure_suggestion VARCHAR(1000) NULL COMMENT 'AI建议措施',
  raw_request JSON NULL COMMENT 'AI原始请求JSON',
  raw_response JSON NULL COMMENT 'AI原始响应JSON',
  confidence DECIMAL(6, 4) NULL COMMENT 'AI置信度',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  INDEX idx_cps_ai_issue (issue_id)
) COMMENT='CPS问题AI建议表';

CREATE TABLE cps_issue_flow_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  issue_id BIGINT NOT NULL COMMENT '问题ID',
  from_status VARCHAR(40) NULL COMMENT '流转前状态',
  to_status VARCHAR(40) NOT NULL COMMENT '流转后状态',
  action VARCHAR(40) NOT NULL COMMENT '流程动作',
  operator_emp_no VARCHAR(40) NOT NULL COMMENT '操作人工号',
  from_handler_emp_no VARCHAR(40) NULL COMMENT '流转前处理人工号',
  to_handler_emp_no VARCHAR(40) NULL COMMENT '流转后处理人工号',
  operator_emp_name VARCHAR(40) NOT NULL COMMENT '操作人姓名',
  from_handler_emp_name VARCHAR(40) NULL COMMENT '流转前处理人姓名',
  to_handler_emp_name VARCHAR(40) NULL COMMENT '流转后处理人姓名',
  comment VARCHAR(1000) NULL COMMENT '流程备注',
  snapshot_json JSON NULL COMMENT '流程快照JSON',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  INDEX idx_cps_flow_issue_time (issue_id, created_at),
  INDEX idx_cps_flow_operator (operator_emp_no)
) COMMENT='CPS问题流程日志表';

CREATE TABLE cps_reminder_rule (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  node_status VARCHAR(40) NOT NULL COMMENT '节点状态',
  duration_days DECIMAL(2, 1) NOT NULL COMMENT '提醒时长天数',
  need_escalation TINYINT NOT NULL DEFAULT 0 COMMENT '是否需要升级提醒：1是，0否',
  escalation_level INT NULL COMMENT '升级提醒级别',
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：1启用，0停用',
  UNIQUE KEY uk_cps_reminder_node (node_status)
) COMMENT='CPS节点提醒规则表';
