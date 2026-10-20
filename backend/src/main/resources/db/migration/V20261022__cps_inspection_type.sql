-- ============================================================================
-- Phase B1: 巡检问题类型（后端可配置）+ 动态表单 + 扩展数据
-- 设计：每种类型由 cps_inspection_type.form_schema（JSON）描述动态字段；
--       创建问题实例时把字段值序列化到 cps_issue.extra_data，方便后续详情回显。
-- ============================================================================

CREATE TABLE cps_inspection_type (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  code VARCHAR(40) NOT NULL COMMENT '类型编码（业务唯一，如 QUALITY/SAFETY/EQUIPMENT/5S）',
  name VARCHAR(80) NOT NULL COMMENT '类型显示名',
  description VARCHAR(500) NULL COMMENT '类型描述',
  icon VARCHAR(40) NULL COMMENT '展示图标（可选）',
  color VARCHAR(20) NULL COMMENT '主色调（可选）',
  enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用：1启用，0停用',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '排序（升序）',
  form_schema JSON NOT NULL COMMENT '动态表单 schema（JSON）：{fields:[{key,label,type,required,maxLength,placeholder,options,defaultValue,helpText}]}',
  default_assignee_emp_no VARCHAR(40) NULL COMMENT '默认派单人（反馈人未指定时兜底）',
  remark VARCHAR(500) NULL COMMENT '备注',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uk_cps_inspection_type_code (code),
  KEY idx_cps_inspection_type_enabled_sort (enabled, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CPS 巡检问题类型（后端可配置）';

ALTER TABLE cps_issue
  ADD COLUMN inspection_type_code VARCHAR(40) NULL COMMENT '巡检类型编码（关联 cps_inspection_type.code）' AFTER process,
  ADD COLUMN extra_data JSON NULL COMMENT '动态字段值（JSON）：与 inspection_type 的 form_schema 对应' AFTER description,
  ADD KEY idx_cps_issue_inspection_type (inspection_type_code);

-- 预置三种类型：质量问题 / 安全隐患 / 设备故障。
-- form_schema 字段在 Android 端按 type 渲染：input/number/textarea/select/date/switch。
INSERT INTO cps_inspection_type (code, name, description, icon, color, enabled, sort_order, form_schema, remark) VALUES
('QUALITY', '质量问题', '质量缺陷、工艺偏差、检验不达标等问题', 'check-circle', 'blue', 1, 10,
 JSON_OBJECT(
   'fields', JSON_ARRAY(
     JSON_OBJECT('key','resourceNo','label','资源号','type','input','required',true,'maxLength',64,'placeholder','请输入资源编号（设备/工位）'),
     JSON_OBJECT('key','severity','label','严重程度','type','select','required',true,
       'options', JSON_ARRAY(
         JSON_OBJECT('label','一般','value','NORMAL'),
         JSON_OBJECT('label','严重','value','SEVERE'),
         JSON_OBJECT('label','致命','value','CRITICAL')
       ),
       'defaultValue','NORMAL'),
     JSON_OBJECT('key','lotNo','label','批次号','type','input','required',false,'maxLength',40,'placeholder','可选：生产批次号'),
     JSON_OBJECT('key','remark','label','补充说明','type','textarea','required',false,'maxLength',500,'placeholder','可选：补充描述')
   )
 ),
 '预置 - 质量问题'),

('SAFETY', '安全隐患', '可能导致人身伤害或事故的隐患', 'warning-circle', 'red', 1, 20,
 JSON_OBJECT(
   'fields', JSON_ARRAY(
     JSON_OBJECT('key','resourceNo','label','资源号','type','input','required',true,'maxLength',64,'placeholder','请输入资源编号'),
     JSON_OBJECT('key','severity','label','风险等级','type','select','required',true,
       'options', JSON_ARRAY(
         JSON_OBJECT('label','低','value','LOW'),
         JSON_OBJECT('label','中','value','MEDIUM'),
         JSON_OBJECT('label','高','value','HIGH')
       ),
       'defaultValue','MEDIUM'),
     JSON_OBJECT('key','deadline','label','整改期限','type','date','required',true,'placeholder','选择整改完成日期'),
     JSON_OBJECT('key','remark','label','补充说明','type','textarea','required',false,'maxLength',500)
   )
 ),
 '预置 - 安全隐患'),

('EQUIPMENT', '设备故障', '设备异常、跑冒滴漏、故障停机等问题', 'tool', 'orange', 1, 30,
 JSON_OBJECT(
   'fields', JSON_ARRAY(
     JSON_OBJECT('key','resourceNo','label','资源号','type','input','required',true,'maxLength',64,'placeholder','设备编号（与设备台账一致）'),
     JSON_OBJECT('key','isRunning','label','是否停机','type','switch','required',false,'defaultValue',false),
     JSON_OBJECT('key','errorCode','label','故障代码','type','input','required',false,'maxLength',40,'placeholder','可选：PLC/设备故障码'),
     JSON_OBJECT('key','remark','label','补充说明','type','textarea','required',false,'maxLength',500)
   )
 ),
 '预置 - 设备故障');
