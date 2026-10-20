-- PRD 24.2.6：Android 端新建巡检问题需用户手动录入「资源号」，存入 cps_issue。
-- 资源号是现场定位的物理标识（与工厂/区域/拉线/工序共同组成位置维度），
-- 必填，便于后续按资源号搜索/筛选/统计。
-- 列位置：放在 process 之后，与其它位置字段连续。

ALTER TABLE cps_issue
  ADD COLUMN resource_no VARCHAR(64) NULL COMMENT '资源号（巡检现场手动录入，PRD 24.2.6）' AFTER process;

-- 单列索引：按资源号搜索/筛选问题
CREATE INDEX idx_cps_issue_resource_no ON cps_issue (resource_no);
