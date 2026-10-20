-- ============================================================================
-- 流程调整（PRD §28.1）：新建问题后直接进入「整改人员」节点
-- 原流程：新建 → 待反馈（反馈人确认/指派）→ 待整改 → …；
-- 新流程：新建 → 待整改（整改人员填原因/短期/长期措施、选责任员工、传整改照片）
--         → 提交整改 → AI 初审 → 待审核 → 关闭。
-- 存量 v2 待反馈单：迁移到待整改，处理人保持原反馈人（新流程里即整改人），
-- 并补一条流程日志留痕，避免轨迹断裂。
-- ============================================================================

INSERT INTO cps_issue_flow_log (
    issue_id, from_status, to_status, action,
    operator_emp_no, from_handler_emp_no, to_handler_emp_no,
    operator_emp_name, from_handler_emp_name, to_handler_emp_name,
    comment, created_at
)
SELECT
    id,
    'PENDING_FEEDBACK',
    'PENDING_RECTIFY',
    'REPLY_ASSIGN',
    feedback_emp_no,
    current_handler_emp_no,
    feedback_emp_no,
    IFNULL(feedback_emp_name, feedback_emp_no),
    current_handler_emp_name,
    IFNULL(feedback_emp_name, feedback_emp_no),
    '流程调整：跳过反馈节点，直接进入整改',
    NOW()
FROM cps_issue
WHERE flow_version = 'v2'
  AND status = 'PENDING_FEEDBACK'
  AND feedback_emp_no IS NOT NULL;

UPDATE cps_issue
SET status = 'PENDING_RECTIFY',
    current_handler_emp_no = feedback_emp_no,
    current_handler_emp_name = IFNULL(feedback_emp_name, feedback_emp_no),
    updated_at = NOW()
WHERE flow_version = 'v2'
  AND status = 'PENDING_FEEDBACK'
  AND feedback_emp_no IS NOT NULL;
