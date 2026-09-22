-- =====================================================================
-- A2 编辑/转办锁定（WBS 线A，AC-24/26；PRD §28.3/§28.4）
-- ---------------------------------------------------------------------
-- 口径（PRD §28.3，AC-26）：
--   1) 仅“整改办理中、退回整改”允许编辑或转办；提交后锁定当前提交版本，
--      AI 初审中（PENDING_AI_REVIEW）/ 待配置审核员（PENDING_REVIEWER_CONFIG）/
--      待人工审核（PENDING_REVIEW）期间不得继续修改或转办该版本。
--      —— 该状态门控由 CpsWorkflowStateMachineV2 状态转移表（锁定期无
--         SAVE_DRAFT/TRANSFER/SUBMIT_RECTIFICATION 出边）+ 服务层守卫实现。
--   2) 并发编辑采用乐观锁：cps_issue.lock_version，更新时 CAS 校验并自增；
--      冲突（0 行受影响）由服务层抛 IllegalStateException 提示刷新重试。
--   3) 审核员改配（§30.2）不重置 AI 初审计时：不触碰 cps_initial_review_task，
--      任务推进时按最新 reviewer_emp_no 路由（本迁移无 task 表改动）。
-- 存量数据：默认 0，无需回填；legacy 流程同字段兼容。
-- =====================================================================

ALTER TABLE cps_issue
    ADD COLUMN lock_version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号：更新时校验并自增，防并发编辑（A2/AC-24）' AFTER inspection_item_id;
