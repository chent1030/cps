-- PRD 24.2.2/24.2.5：点检人提交前确认/修正判定结果（人工确认闭环）。
-- 状态机扩展：submit 判定成功 → AWAIT_CONFIRM（已判定待确认）→ confirm 确认/修正后 JUDGED 锁定计分。
-- 降级路径不变（judge 服务不可用 → JUDGED + judge_status=PENDING，由 admin rejudge 补判）。

ALTER TABLE cps_room_check_record
  MODIFY COLUMN record_status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
      COMMENT '状态机：PENDING待执行/IN_PROGRESS执行中/AWAIT_CONFIRM已判定待确认/JUDGED已确认锁定（PRD 24.2.4）';

ALTER TABLE cps_room_check_record_item
  ADD COLUMN final_modified_by VARCHAR(40) NULL COMMENT '最终结果修正人（点检人，PRD 24.2.5 留痕）' AFTER final_result,
  ADD COLUMN final_modified_at DATETIME NULL COMMENT '最终结果修正时间' AFTER final_modified_by,
  ADD COLUMN final_modify_reason VARCHAR(500) NULL COMMENT '最终结果修正原因（选填）' AFTER final_modified_at;
