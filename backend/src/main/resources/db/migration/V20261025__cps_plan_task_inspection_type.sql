-- ============================================================================
-- Phase D: 巡检计划对接统一巡检类型
-- 1) plan_task.task_type 改存巡检类型编码（拓宽到 40，历史枚举值 INSPECT_* 仍合法）
-- 2) 加 coverage_area_codes：分区域类型任务的覆盖区域（逗号分隔 area_code）
-- ============================================================================

ALTER TABLE cps_inspection_plan_task
  MODIFY COLUMN task_type VARCHAR(40) NOT NULL
      COMMENT '任务类型：巡检类型编码（cps_inspection_type.code，Phase D）或历史枚举 INSPECT_RECTIFY/INSPECT_PATROL/INSPECT_CHECK',
  ADD COLUMN coverage_area_codes VARCHAR(1024) NULL
      COMMENT '分区域类型任务的覆盖区域编码（逗号分隔 cps_inspection_area.area_code）；空=该类型全部启用区域' AFTER evidence_requirement;
