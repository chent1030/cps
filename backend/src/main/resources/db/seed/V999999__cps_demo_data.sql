-- CPS 本地演示数据。可重复执行；只使用 DEMO_ 前缀业务编号，不覆盖真实数据。
-- 访问 mobile 时使用请求头 X-Emp-No: DEMO_EMP。

INSERT INTO cps_problem_category
    (id, parent_id, category_level, category_name, sort_no, enabled, created_by, updated_by)
VALUES
    (910001, 0, 1, '安全与防护', 10, 1, 'DEMO_SEED', 'DEMO_SEED'),
    (910002, 910001, 2, '防护用品未佩戴', 10, 1, 'DEMO_SEED', 'DEMO_SEED'),
    (910003, 910001, 2, '安全标识缺失', 20, 1, 'DEMO_SEED', 'DEMO_SEED'),
    (910011, 0, 1, '现场管理', 20, 1, 'DEMO_SEED', 'DEMO_SEED'),
    (910012, 910011, 2, '物料摆放不规范', 10, 1, 'DEMO_SEED', 'DEMO_SEED'),
    (910013, 910011, 2, '5S 清洁问题', 20, 1, 'DEMO_SEED', 'DEMO_SEED')
ON DUPLICATE KEY UPDATE enabled = VALUES(enabled), updated_by = VALUES(updated_by);

INSERT INTO cps_area_person_config
    (factory, area, line, process, emp_no, emp_name, enabled, created_by, updated_by)
VALUES
    ('DEMO-F1', '装配车间', 'L01', 'P01', 'DEMO_FEEDBACK', '演示反馈人', 1, 'DEMO_SEED', 'DEMO_SEED'),
    ('DEMO-F1', '装配车间', '', '', 'DEMO_FEEDBACK', '演示反馈人', 1, 'DEMO_SEED', 'DEMO_SEED'),
    ('DEMO-F1', '装配车间', 'L01', '', 'DEMO_RECTIFY', '演示整改人', 1, 'DEMO_SEED', 'DEMO_SEED'),
    ('DEMO-F1', '装配车间', '', '', 'DEMO_REVIEW', '演示审核人', 1, 'DEMO_SEED', 'DEMO_SEED')
ON DUPLICATE KEY UPDATE emp_no = VALUES(emp_no), emp_name = VALUES(emp_name), enabled = VALUES(enabled), updated_by = VALUES(updated_by);

INSERT INTO cps_issue
    (issue_no, status, factory, area, line, process,
     ai_category_l1_id, ai_category_l2_id, category_l1_id, category_l2_id, category_modified_flag,
     description, creator_emp_no, feedback_emp_no, responsible_emp_no, proof_emp_no, reviewer_emp_no,
     current_handler_emp_no, creator_emp_name, feedback_emp_name, responsible_emp_name, proof_emp_name,
     reviewer_emp_name, current_handler_emp_name, reason_analysis, corrective_measure, rectify_remark,
     review_opinion, submit_time, close_time, created_at, updated_at)
VALUES
    ('DEMO-20260915-001', 'PENDING_FEEDBACK', 'DEMO-F1', '装配车间', 'L01', 'P01',
     910001, 910002, 910001, 910002, 0, '员工未佩戴安全护目镜，请现场确认并反馈。',
     'DEMO_EMP', 'DEMO_FEEDBACK', NULL, NULL, NULL, 'DEMO_FEEDBACK', '演示员工', '演示反馈人', NULL, NULL, NULL, '演示反馈人', NULL, NULL, NULL, NULL, NOW() - INTERVAL 1 HOUR, NULL, NOW() - INTERVAL 1 HOUR, NOW() - INTERVAL 1 HOUR),
    ('DEMO-20260915-002', 'PENDING_RECTIFY', 'DEMO-F1', '装配车间', 'L01', 'P01',
     910011, 910012, 910011, 910012, 1, '工位旁物料堆放超出标线，影响通行。',
     'DEMO_EMP', 'DEMO_FEEDBACK', 'DEMO_RECTIFY', NULL, NULL, 'DEMO_RECTIFY', '演示员工', '演示反馈人', '演示整改人', NULL, NULL, '演示整改人', '现场空间规划不足', '重新划线并按区域摆放物料', NULL, NULL, NOW() - INTERVAL 2 DAY, NULL, NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 2 DAY),
    ('DEMO-20260915-003', 'PENDING_UPLOAD_PROOF', 'DEMO-F1', '装配车间', 'L01', 'P01',
     910011, 910013, 910011, 910013, 0, '设备周边存在积尘，需要完成清洁并上传整改照片。',
     'DEMO_EMP', 'DEMO_FEEDBACK', 'DEMO_RECTIFY', 'DEMO_RECTIFY', NULL, 'DEMO_EMP', '演示员工', '演示反馈人', '演示整改人', '演示整改人', NULL, '演示员工', '清洁点检执行不到位', '完成设备周边深度清洁并增加点检频次', '已完成清洁，待上传前后对比照片', NULL, NOW() - INTERVAL 3 DAY, NULL, NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 3 DAY),
    ('DEMO-20260915-004', 'PENDING_REVIEW', 'DEMO-F1', '装配车间', 'L01', 'P01',
     910001, 910003, 910001, 910003, 0, '安全标识褪色，已完成更换，等待审核关闭。',
     'DEMO_EMP', 'DEMO_FEEDBACK', 'DEMO_RECTIFY', 'DEMO_RECTIFY', 'DEMO_REVIEW', 'DEMO_REVIEW', '演示员工', '演示反馈人', '演示整改人', '演示整改人', '演示审核人', '演示审核人', '标识长期暴露导致褪色', '更换为耐磨安全标识', '已完成更换并清理现场', NULL, NOW() - INTERVAL 4 DAY, NULL, NOW() - INTERVAL 4 DAY, NOW() - INTERVAL 4 DAY),
    ('DEMO-20260915-005', 'CLOSED', 'DEMO-F1', '装配车间', 'L01', 'P01',
     910001, 910002, 910001, 910002, 0, '演示历史问题：员工未佩戴防护用品，已整改关闭。',
     'DEMO_EMP', 'DEMO_FEEDBACK', 'DEMO_RECTIFY', 'DEMO_RECTIFY', 'DEMO_REVIEW', NULL, '演示员工', '演示反馈人', '演示整改人', '演示整改人', '演示审核人', NULL, '现场培训覆盖不足', '完成班前培训并补充检查', '已培训并完成复核', '整改照片清晰，审核通过', NOW() - INTERVAL 10 DAY, NOW() - INTERVAL 7 DAY, NOW() - INTERVAL 10 DAY, NOW() - INTERVAL 7 DAY)
ON DUPLICATE KEY UPDATE updated_at = VALUES(updated_at);

UPDATE cps_issue
SET current_handler_emp_no = 'DEMO_EMP', current_handler_emp_name = '演示员工'
WHERE issue_no = 'DEMO-20260915-003';

INSERT INTO cps_issue_flow_log
    (issue_id, from_status, to_status, action, operator_emp_no, from_handler_emp_no, to_handler_emp_no,
     operator_emp_name, from_handler_emp_name, to_handler_emp_name, comment, created_at)
SELECT id, NULL, 'PENDING_FEEDBACK', 'SUBMIT', 'DEMO_EMP', NULL, 'DEMO_FEEDBACK', '演示员工', NULL, '演示反馈人', '演示种子数据', created_at
FROM cps_issue
WHERE issue_no LIKE 'DEMO-%'
  AND NOT EXISTS (SELECT 1 FROM cps_issue_flow_log log WHERE log.issue_id = cps_issue.id AND log.comment = '演示种子数据');
