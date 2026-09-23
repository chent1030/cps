-- =====================================================================
-- 波次8 E2/E3：出入库流水 + 库存预警事件（PRD §25；AC-12/13/33）
-- ---------------------------------------------------------------------
-- 口径（§25.2/§25.4 已确认）：
--   1) E2：cps_inventory_txn 记录每一笔出入库（含盘点 ADJUST 单独留痕），
--      qty 符号约束 IN>0 / OUT<0 / ADJUST<>0，before/after 快照；
--      事务内更新 cps_inventory_item.stock_qty（服务层悲观行锁串行化，
--      chk_cps_inventory_stock(stock_qty>=0) DB 兜底禁负库存）。
--   2) E3：stock_qty <= alert_threshold（含等于）触发预警；
--      同物品持续不足合并为同一 OPEN 事件（生成列+UNIQUE 兜底唯一 OPEN）；
--      库存回升(>阈值)自动解除 RESOLVED_AUTO；恢复后再不足形成新事件；
--      人工处理：RESOLVED_MANUAL(带原因)/IGNORED。
--   3) 通知渠道未确认（§27.8）：本表仅落事件，不做通知发送。
-- =====================================================================

CREATE TABLE cps_inventory_txn (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  item_id BIGINT NOT NULL COMMENT '台账物品ID（cps_inventory_item.id）',
  txn_type VARCHAR(10) NOT NULL COMMENT '类型：IN入库/OUT出库/ADJUST盘点调整',
  qty INT NOT NULL COMMENT '变更数量：IN>0，OUT<0，ADJUST<>0（带符号）',
  before_qty INT NOT NULL COMMENT '变更前库存快照',
  after_qty INT NOT NULL COMMENT '变更后库存快照（>=0，禁负库存）',
  unit VARCHAR(20) NOT NULL COMMENT '计量单位快照（§25.1）',
  operator_emp_no VARCHAR(40) NOT NULL COMMENT '操作人工号',
  operator_name VARCHAR(40) NULL COMMENT '操作人姓名',
  remark VARCHAR(500) NULL COMMENT '说明（§25.3 流水建议字段）',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  INDEX idx_cps_inv_txn_item (item_id, id),
  INDEX idx_cps_inv_txn_type (txn_type, id),
  CONSTRAINT chk_cps_inv_txn_qty_sign CHECK (
    (txn_type = 'IN' AND qty > 0) OR (txn_type = 'OUT' AND qty < 0) OR (txn_type = 'ADJUST' AND qty <> 0)
  ),
  CONSTRAINT chk_cps_inv_txn_after_nonneg CHECK (after_qty >= 0)
) COMMENT='CPS台账出入库流水表（E2，PRD §25.2/§25.3）';

CREATE TABLE cps_inventory_alert_event (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  item_id BIGINT NOT NULL COMMENT '台账物品ID（cps_inventory_item.id）',
  status VARCHAR(16) NOT NULL DEFAULT 'OPEN'
      COMMENT 'OPEN进行中/RESOLVED_AUTO回升自动解除/RESOLVED_MANUAL人工关闭/IGNORED人工忽略',
  first_triggered_at DATETIME NOT NULL COMMENT '事件首次触发时间（合并期起点）',
  last_eval_at DATETIME NOT NULL COMMENT '最近一次评估时间（持续不足期间每次出入库刷新）',
  last_eval_qty INT NOT NULL COMMENT '最近评估时库存数量',
  threshold_snapshot INT NOT NULL COMMENT '触发时预警阈值快照',
  closed_at DATETIME NULL COMMENT '关闭/处理时间',
  closed_by VARCHAR(40) NULL COMMENT '处理人工号（人工关闭/忽略）',
  close_reason VARCHAR(500) NULL COMMENT '处理原因（人工关闭必填）',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  open_item_id BIGINT GENERATED ALWAYS AS (IF(status = 'OPEN', item_id, NULL)) STORED
      COMMENT '合并事件兜底：OPEN 时等于 item_id，否则 NULL',
  UNIQUE KEY uk_cps_inv_alert_open (open_item_id),
  INDEX idx_cps_inv_alert_status (status, last_eval_at),
  INDEX idx_cps_inv_alert_item (item_id, id)
) COMMENT='CPS库存预警事件表（E3，PRD §25.4；同物品持续不足合并为同一OPEN事件）';
