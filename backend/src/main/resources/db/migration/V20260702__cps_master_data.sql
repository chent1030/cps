CREATE TABLE cps_problem_category (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  parent_id BIGINT NOT NULL DEFAULT 0 COMMENT '父级分类ID，一级分类为0',
  category_level TINYINT NOT NULL COMMENT '分类层级：1一级分类，2二级分类',
  category_name VARCHAR(100) NOT NULL COMMENT '分类名称',
  category_code VARCHAR(64) NULL COMMENT '分类编码，可用于外部系统映射',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '排序号，值越小越靠前',
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：1启用，0停用',
  created_by VARCHAR(64) NULL COMMENT '创建人工号',
  updated_by VARCHAR(64) NULL COMMENT '更新人工号',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uk_cps_category_parent_name (parent_id, category_name),
  INDEX idx_cps_category_parent_enabled (parent_id, enabled, sort_no),
  INDEX idx_cps_category_level_enabled (category_level, enabled, sort_no)
) COMMENT='CPS问题分类主数据表';

CREATE TABLE cps_area_person_config (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  factory VARCHAR(40) NOT NULL COMMENT '适用工厂',
  area VARCHAR(40) NOT NULL COMMENT '适用区域',
  line VARCHAR(40) NOT NULL DEFAULT '' COMMENT '适用拉线，空字符串表示区域级默认配置',
  process VARCHAR(40) NOT NULL DEFAULT '' COMMENT '适用工序，空字符串表示拉线或区域级默认配置',
  emp_no VARCHAR(40) NOT NULL DEFAULT '' COMMENT '工号',
  emp_name VARCHAR(40) NOT NULL DEFAULT '' COMMENT '姓名',
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：1启用，0停用',
  created_by VARCHAR(64) NULL COMMENT '创建人工号',
  updated_by VARCHAR(64) NULL COMMENT '更新人工号',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uk_cps_area_person_scope (factory, area, line, process)
) COMMENT='CPS区域人员配置表';
