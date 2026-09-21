ALTER TABLE cps_issue
    ADD COLUMN agent_inspection_id VARCHAR(128) NULL COMMENT '共享 Agent 框架巡检 ID' AFTER issue_no,
    ADD UNIQUE KEY uk_cps_issue_agent_inspection (agent_inspection_id);

ALTER TABLE cps_issue_attachment
    ADD COLUMN content MEDIUMBLOB NULL COMMENT '原始附件内容，用于同步视觉 Agent';
