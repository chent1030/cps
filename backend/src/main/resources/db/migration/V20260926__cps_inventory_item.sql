-- 波次2 E1：设备/物品台账（PRD §25.1 字段全集）
-- E2 出入库流水（cps_inventory_movement）/ E3 预警（cps_inventory_alert_event）后续波次建表；
-- 本表已按 §25.2 预留：库存数量<=预警数量（含等于）触发预警。

CREATE TABLE cps_inventory_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  item_code VARCHAR(64) NOT NULL COMMENT '台账物品唯一编号',
  item_name VARCHAR(200) NOT NULL COMMENT '物品名称',
  unit VARCHAR(20) NOT NULL COMMENT '计量单位（如个/箱/卷）',
  stock_qty INT NOT NULL DEFAULT 0 COMMENT '当前库存数量',
  alert_threshold INT NOT NULL DEFAULT 0 COMMENT '预警数量（库存<=该值触发预警，含等于，§25.2）',
  base VARCHAR(40) NULL COMMENT '基地',
  factory VARCHAR(40) NOT NULL COMMENT '工厂',
  storage_room VARCHAR(100) NOT NULL COMMENT '存储房间（辅房名称或编号）',
  room_keeper_emp_no VARCHAR(40) NOT NULL COMMENT '房间责任人工号',
  room_keeper_emp_name VARCHAR(40) NOT NULL COMMENT '房间责任人姓名',
  remark VARCHAR(500) NULL COMMENT '备注',
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：1启用，0停用',
  created_by VARCHAR(64) NULL COMMENT '创建人工号',
  updated_by VARCHAR(64) NULL COMMENT '更新人工号',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uk_cps_inventory_item_code (item_code),
  INDEX idx_cps_inventory_item_room (factory, storage_room, enabled),
  INDEX idx_cps_inventory_item_keeper (room_keeper_emp_no),
  CONSTRAINT chk_cps_inventory_stock CHECK (stock_qty >= 0)
) COMMENT='CPS设备物品台账表（E1，PRD §25.1）';
