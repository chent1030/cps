-- 问题分类改为软删除：删除后不再出现在新增/筛选选项里，历史问题/案例仍按分类 ID 关联、名称照常显示
ALTER TABLE cps_problem_category
  ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0 COMMENT '是否已删除：1已删除（软删除），0正常' AFTER enabled,
  ADD INDEX idx_cps_category_deleted (deleted, parent_id, sort_no);
