ALTER TABLE cps_area_person_config
  ADD COLUMN created_name VARCHAR(64) NULL COMMENT '创建人姓名' AFTER created_by,
  ADD COLUMN updated_name VARCHAR(64) NULL COMMENT '更新人姓名' AFTER updated_by;
