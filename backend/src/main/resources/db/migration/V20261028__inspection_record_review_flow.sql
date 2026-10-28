-- 非 CPS 巡检（区/点检类，builtin=0）提交后的人工复核与整改闭环。
--
-- 目标流程：IN_PROGRESS --提交--> PENDING_REVIEW（web 人工复核，携带 AI 初审意见）
--          --复核通过--> PENDING_RECTIFY（Android 区域责任人整改）
--          --整改提交--> JUDGED（C-01 初审留痕后结束，不再二次复核）
--          --复核退回--> IN_PROGRESS（提交人可编辑后重提，重提重跑 AI）
--
-- AI 初审口径：C-04（room-checks judge）逐项视觉判定 + 点检项扣分策略；
-- 整改提交复核口径：C-01（/agent/rectifications）。

ALTER TABLE cps_inspection_record
    ADD COLUMN ai_overall VARCHAR(16) NULL COMMENT 'AI 初审总体结论 PASS/PARTIAL/PROBLEM/NONE' AFTER score,
    ADD COLUMN ai_score INT NULL COMMENT 'AI 判定后的得分（扣分策略）' AFTER ai_overall,
    ADD COLUMN ai_reason TEXT NULL COMMENT 'AI 初审意见摘要' AFTER ai_score,
    ADD COLUMN ai_judged_at DATETIME NULL COMMENT 'AI 初审完成时间' AFTER ai_reason,
    ADD COLUMN review_round INT NOT NULL DEFAULT 0 COMMENT '提交-复核轮数（退回后+1，用于 AI 幂等键）' AFTER ai_judged_at,
    ADD COLUMN review_emp_no VARCHAR(40) NULL COMMENT '复核人（web）' AFTER review_round,
    ADD COLUMN review_emp_name VARCHAR(40) NULL COMMENT '复核人姓名' AFTER review_emp_no,
    ADD COLUMN review_opinion TEXT NULL COMMENT '复核意见（退回必填）' AFTER review_emp_name,
    ADD COLUMN reviewed_at DATETIME NULL COMMENT '复核时间' AFTER review_opinion,
    ADD COLUMN rectify_emp_no VARCHAR(40) NULL COMMENT '整改责任人（区域 keeper）' AFTER reviewed_at,
    ADD COLUMN rectify_emp_name VARCHAR(40) NULL COMMENT '整改责任人姓名' AFTER rectify_emp_no,
    ADD COLUMN rectify_reason TEXT NULL COMMENT '整改-原因分析' AFTER rectify_emp_name,
    ADD COLUMN rectify_short_term TEXT NULL COMMENT '整改-短期措施' AFTER rectify_reason,
    ADD COLUMN rectify_long_term TEXT NULL COMMENT '整改-长期措施' AFTER rectify_short_term,
    ADD COLUMN rectify_attachment_ids TEXT NULL COMMENT '整改照片（cps_issue_attachment.id 逗号分隔）' AFTER rectify_long_term,
    ADD COLUMN rectify_submitted_at DATETIME NULL COMMENT '整改提交时间' AFTER rectify_attachment_ids,
    ADD COLUMN rectify_ai_overall VARCHAR(16) NULL COMMENT '整改提交 C-01 结论' AFTER rectify_submitted_at,
    ADD COLUMN rectify_ai_reason TEXT NULL COMMENT '整改提交 C-01 意见摘要' AFTER rectify_ai_overall,
    ADD COLUMN current_handler_emp_no VARCHAR(40) NULL COMMENT '当前办理人（待整改=区域责任人）' AFTER rectify_ai_reason,
    ADD COLUMN current_handler_emp_name VARCHAR(40) NULL COMMENT '当前办理人姓名' AFTER current_handler_emp_no;

ALTER TABLE cps_inspection_record
    ADD INDEX idx_record_handler (current_handler_emp_no, record_status, updated_at);

-- 记录流转日志（web 复核页时间线 + Android 详情进度）
CREATE TABLE IF NOT EXISTS cps_inspection_record_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    record_id BIGINT NOT NULL,
    action VARCHAR(40) NOT NULL COMMENT 'START/SUBMIT/REVIEW_PASS/REVIEW_RETURN/RECTIFY_SUBMIT',
    from_status VARCHAR(16) NULL,
    to_status VARCHAR(16) NULL,
    operator_emp_no VARCHAR(40) NULL,
    operator_emp_name VARCHAR(40) NULL,
    comment VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_log_record (record_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='巡检记录流转日志（非 CPS 复核/整改）';

-- 历史数据：已判定（JUDGED）的记录保持不动；执行中的记录补办理人=检查人
UPDATE cps_inspection_record
SET current_handler_emp_no = check_emp_no,
    current_handler_emp_name = check_emp_name
WHERE current_handler_emp_no IS NULL
  AND record_status = 'IN_PROGRESS';
