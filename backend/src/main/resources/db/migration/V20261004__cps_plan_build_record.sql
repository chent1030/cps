-- =====================================================================
-- 波次8 D4：计划建单记录（append-only 留痕；AC-06/30）
-- ---------------------------------------------------------------------
-- 口径（开发计划线 D、契约 C-09）：
--   1) 批准后立即建三类任务；每类结果留痕 CREATED / SKIPPED_EXISTING /
--      CREATE_FAILED——部分失败不阻断批准，失败仅补建（AC-30）。
--   2) 补建（rebuild）仅扫 CREATE_FAILED / 缺失类型，已成功项不重复创建；
--      cps_inspection_plan_task 的 UNIQUE(plan_id, task_type) 仍为最终幂等兜底。
--   3) append-only：保留每次尝试历史，重启/重试/补建均可追溯。
-- =====================================================================

CREATE TABLE cps_plan_build_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  plan_id BIGINT NOT NULL COMMENT '计划ID（cps_inspection_plan.id）',
  task_type VARCHAR(24) NOT NULL
      COMMENT '任务类型：INSPECT_RECTIFY/INSPECT_PATROL/INSPECT_CHECK',
  result VARCHAR(16) NOT NULL
      COMMENT '结果：CREATED本次新建/SKIPPED_EXISTING已存在跳过/CREATE_FAILED失败留痕',
  error_msg VARCHAR(500) NULL COMMENT '失败原因（CREATE_FAILED 时必填）',
  built_by VARCHAR(40) NULL COMMENT '触发者工号（approve 审核人或 rebuild 操作者）',
  build_source VARCHAR(16) NOT NULL COMMENT '来源：APPROVE批准建单/REBUILD补建',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '本次建单动作时间',
  INDEX idx_cps_plan_build_plan (plan_id, id),
  INDEX idx_cps_plan_build_result (plan_id, result)
) COMMENT='CPS计划建单记录表（D4，append-only；AC-30 失败仅补建可追溯）';
