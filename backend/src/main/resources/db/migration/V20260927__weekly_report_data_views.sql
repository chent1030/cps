-- =====================================================================
-- 波次3 B7：分类周报数据源（PRD §21.1/§21.3；后端架构 §2.1）
-- ---------------------------------------------------------------------
-- 口径（§21.1.6 东八区；§21.1.8 推送独立）：
--   1) 窗口语义 [period_start, period_end) 左闭右开；period_end 通常
--      为本周一 00:00 (Asia/Shanghai)，上周一 00:00 ~ 本周一 00:00。
--   2) 本次仅做数据视图（无新增事实表）：Python 报告 Agent 经 B7 读端点
--      按窗口取数；Java 不做语义汇总、不做周报生成。
--   3) 命名空间 weekly_report_data_* 与现有 cps_* 业务表清晰隔离；
--      视图内 cps_issue/cps_room/cps_inventory_item 均以 cps_ 物理表
--      为底，删除物理表时先 DROP VIEW。
--   4) 视图不做权限过滤——admin 端 C5/C8 已校验登录 + status=ARCHIVED；
--      B7 数据接口面向内部 Python 进程，需走反向代理 + 内网 CIDR 校验
--      （沿用 cps.callback.trusted-ips 思路）。
-- =====================================================================

-- B7.1 周期内新建问题按巡检分类汇总（按 factory/line/process）
DROP VIEW IF EXISTS weekly_report_data_issue_summary;
CREATE VIEW weekly_report_data_issue_summary AS
SELECT
    DATE_FORMAT(i.submit_time, '%Y-%m-%d') AS submit_date,
    i.factory,
    i.line,
    i.process,
    i.status,
    COUNT(*) AS issue_count,
    SUM(CASE WHEN i.status = 'CLOSED' THEN 1 ELSE 0 END) AS closed_count,
    SUM(CASE WHEN i.flow_version = 'v2' THEN 1 ELSE 0 END) AS v2_count
FROM cps_issue i
GROUP BY submit_date, i.factory, i.line, i.process, i.status;

-- B7.2 周期内整改提交按版本计数（V2 整改域 source SUBMIT/RESUBMIT）
DROP VIEW IF EXISTS weekly_report_data_rectification_summary;
CREATE VIEW weekly_report_data_rectification_summary AS
SELECT
    DATE_FORMAT(s.submitted_at, '%Y-%m-%d') AS submit_date,
    s.source,
    s.status,
    COUNT(*) AS submit_count
FROM cps_rectification_submission s
GROUP BY submit_date, s.source, s.status;

-- B7.3 周期内 AI 初审任务结果分布（PASS/PARTIAL/PROBLEM/SKIPPED）
DROP VIEW IF EXISTS weekly_report_data_initial_review_summary;
CREATE VIEW weekly_report_data_initial_review_summary AS
SELECT
    DATE_FORMAT(t.submitted_at, '%Y-%m-%d') AS submit_date,
    t.status AS task_status,
    r.overall,
    COUNT(*) AS task_count
FROM cps_initial_review_task t
LEFT JOIN cps_initial_review_result r ON r.task_id = t.id
GROUP BY submit_date, t.status, r.overall;

-- B7.4 周期内辅房点检项状态统计（仅 enabled=1；详细得分 B 线后续聚合）
DROP VIEW IF EXISTS weekly_report_data_check_item_snapshot;
CREATE VIEW weekly_report_data_check_item_snapshot AS
SELECT
    ci.id AS check_item_id,
    ci.photo_category,
    ci.status AS applicable_status,
    ci.config_version,
    ci.enabled
FROM cps_check_item ci;

-- B7.5 周期内库存预警分布（按 factory/storage_room/责任人）
DROP VIEW IF EXISTS weekly_report_data_inventory_low_stock;
CREATE VIEW weekly_report_data_inventory_low_stock AS
SELECT
    ii.factory,
    ii.storage_room,
    ii.room_keeper_emp_no,
    ii.room_keeper_emp_name,
    COUNT(*) AS low_stock_count,
    SUM(ii.alert_threshold - ii.stock_qty) AS total_short_qty
FROM cps_inventory_item ii
WHERE ii.enabled = 1 AND ii.stock_qty <= ii.alert_threshold
GROUP BY ii.factory, ii.storage_room, ii.room_keeper_emp_no, ii.room_keeper_emp_name;
