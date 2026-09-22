-- 波次2 B1/B2：辅房基础配置 + 巡检事项权限域（PRD §23 辅房点检 / §30.1 事项权限）
-- 口径：无权限配置行 = 事项开放（§30.1），配置了仅向有权限人员展示；
--      区域人员责任挂在辅房行上（房间责任人），与既有 cps_area_person_config（整改路由）互不影响。

-- 1) cps_room：辅房台账（§23.1 字段全集）
CREATE TABLE cps_room (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  room_code VARCHAR(64) NOT NULL COMMENT '辅房唯一编号',
  building VARCHAR(40) NOT NULL COMMENT '楼栋',
  door_no VARCHAR(40) NOT NULL COMMENT '门牌号',
  room_name VARCHAR(100) NOT NULL COMMENT '辅房名称',
  room_type VARCHAR(40) NOT NULL COMMENT '辅房类型（如仓库/工具间/休息室）',
  risk_level VARCHAR(16) NOT NULL DEFAULT 'LOW' COMMENT '风险等级：HIGH/MEDIUM/LOW',
  dept_name VARCHAR(100) NULL COMMENT '所属部门',
  base VARCHAR(40) NULL COMMENT '基地（PRD §23.1 基地/工厂口径待确认，先预留）',
  factory VARCHAR(40) NULL COMMENT '工厂（待确认口径，预留）',
  keeper_emp_no VARCHAR(40) NOT NULL COMMENT '责任人工号',
  keeper_emp_name VARCHAR(40) NOT NULL COMMENT '责任人姓名',
  keeper_manager_emp_no VARCHAR(40) NULL COMMENT '责任人经理工号',
  keeper_manager_emp_name VARCHAR(40) NULL COMMENT '责任人经理姓名',
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：1启用，0停用',
  created_by VARCHAR(64) NULL COMMENT '创建人工号',
  updated_by VARCHAR(64) NULL COMMENT '更新人工号',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uk_cps_room_code (room_code),
  INDEX idx_cps_room_factory_type (factory, room_type, enabled),
  INDEX idx_cps_room_keeper (keeper_emp_no)
) COMMENT='CPS辅房配置表（B1，PRD §23.1）';

-- 2) cps_check_item：辅房点检项配置（§23.2 字段全集 + 配置版本）
CREATE TABLE cps_check_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  item_code VARCHAR(64) NOT NULL COMMENT '点检项唯一编号',
  content VARCHAR(500) NOT NULL COMMENT '点检内容',
  photo_category VARCHAR(40) NOT NULL COMMENT '照片对象类别（如地面/墙面/设备）',
  deduct_score INT NOT NULL DEFAULT 0 COMMENT '不合格扣分值',
  status VARCHAR(16) NOT NULL DEFAULT 'APPLICABLE' COMMENT '状态：APPLICABLE适用/NOT_APPLICABLE不适用',
  applicable_room_types VARCHAR(200) NULL COMMENT '适用辅房类型，逗号分隔；空=全部辅房适用【待确认】',
  config_version INT NOT NULL DEFAULT 1 COMMENT '配置版本号，变更时递增',
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：1启用，0停用',
  created_by VARCHAR(64) NULL COMMENT '创建人工号',
  updated_by VARCHAR(64) NULL COMMENT '更新人工号',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uk_cps_check_item_code (item_code),
  INDEX idx_cps_check_item_photo_category (photo_category, enabled),
  INDEX idx_cps_check_item_version (config_version)
) COMMENT='CPS辅房点检项配置表（B2，PRD §23.2）';

-- 3) cps_inspection_item：巡检事项（W4，回填 cps_issue.inspection_item_id 关联）
CREATE TABLE cps_inspection_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  item_code VARCHAR(64) NOT NULL COMMENT '巡检事项唯一编号',
  item_name VARCHAR(200) NOT NULL COMMENT '巡检事项名称',
  factory VARCHAR(40) NULL COMMENT '适用工厂，空=全部工厂',
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：1启用，0停用',
  remark VARCHAR(500) NULL COMMENT '备注',
  created_by VARCHAR(64) NULL COMMENT '创建人工号',
  updated_by VARCHAR(64) NULL COMMENT '更新人工号',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uk_cps_inspection_item_code (item_code),
  INDEX idx_cps_inspection_item_factory (factory, enabled)
) COMMENT='CPS巡检事项表（W4，PRD §30）';

-- 4) cps_inspection_item_permission：事项查看权限（§30.1 无配置行=开放）
CREATE TABLE cps_inspection_item_permission (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  item_id BIGINT NOT NULL COMMENT '巡检事项ID',
  emp_no VARCHAR(40) NOT NULL COMMENT '可查看人员工号',
  emp_name VARCHAR(40) NULL COMMENT '可查看人员姓名',
  created_by VARCHAR(64) NULL COMMENT '创建人工号',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  UNIQUE KEY uk_cps_item_permission (item_id, emp_no),
  INDEX idx_cps_item_permission_emp (emp_no)
) COMMENT='CPS巡检事项查看权限表（§30.1：事项无配置行=开放，有配置行=仅授权人可见）';
