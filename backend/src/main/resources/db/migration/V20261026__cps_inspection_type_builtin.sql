-- ============================================================================
-- Phase D2: 固定内置类型「CPS巡检」+ 类型内置标记
-- 用户口径：类型列表须有一个固定的 CPS巡检 = 之前的标准问题上报流程；
--           内置类型不可删除、不可改编码；其他自建类型按点检项配置渲染填写表单。
-- ============================================================================

ALTER TABLE cps_inspection_type
  ADD COLUMN builtin TINYINT NOT NULL DEFAULT 0 COMMENT '内置类型：1=系统固定（CPS巡检），不可删除/不可改编码' AFTER zone_flag;

-- 固定类型：CPS 巡检（标准问题上报，不分区域、不挂点检项）
INSERT INTO cps_inspection_type (code, name, content, zone_flag, builtin, description, icon, color, enabled, sort_order, form_schema, remark)
SELECT 'CPS_INSPECTION', 'CPS巡检', '标准 CPS 现场巡检问题上报：拍照留证、描述现场情况、选择位置与资源号，AI 辅助识别问题分类后派发给反馈人', 0, 1,
       '系统内置固定类型（即原问题上报流程）', 'check-circle', 'blue', 1, 1, JSON_OBJECT('fields', JSON_ARRAY()), '内置，不可删除'
WHERE NOT EXISTS (SELECT 1 FROM cps_inspection_type WHERE code = 'CPS_INSPECTION');

-- 既有自定义类型保持 builtin=0（可自由停用/删除）
