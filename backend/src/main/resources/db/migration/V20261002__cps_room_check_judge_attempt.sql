-- 波次7 J线联调（C-04 清单⑤）：PENDING 降级单补判定重跑入口。
-- judge_attempt = 已完成的判定轮数（幂等键 room-judge-{submissionId}-{itemId}-{attempt} 的 attempt 来源）。
-- 每轮 submit/rejudge 先取 judge_attempt+1 调判定，回写时持久化，保证补判定换新幂等键拿到新结果（SKIPPED 等业务结果在 Python 侧会被缓存重放）。
ALTER TABLE cps_room_check_record
    ADD COLUMN judge_attempt INT NOT NULL DEFAULT 0 COMMENT '已完成判定轮数（C-04 幂等键 attempt；submit/rejudge 递增）' AFTER judge_status;
