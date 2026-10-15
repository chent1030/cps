# 波次8 Java侧：线 E 台账库存全链收尾 + 线 D 记录状态收尾

> 范围：E2 出入库流水 → E3 库存预警 → E4 管理端 → E5 测试；D4 建单记录状态 + D5 测试。
> 依据：PRD §25（AC-12/13/33）、开发计划线 D（AC-06/30）、§26.2/§27.8 已确认口径。
> 基线：cps `71bcbee`（main）。承接波次2 E1（`cps_inventory_item` 已建，`CHECK stock_qty>=0` 已有）。

## 1. E2 出入库流水（AC-33）

### 1.1 表 `cps_inventory_txn`（V20261003）

| 列 | 说明 |
|---|---|
| id / item_id | 主键 / 台账物品 FK（逻辑关联 cps_inventory_item.id） |
| txn_type | `IN` 入库 / `OUT` 出库 / `ADJUST` 盘点调整（§25.4 建议盘点单独留痕） |
| qty | 变更数量：**IN>0、OUT<0、ADJUST≠0**（CHECK 约束） |
| before_qty / after_qty | 变更前后库存快照（§25.3 流水建议字段） |
| unit / operator_emp_no / operator_name / remark | 单位快照、操作人、说明 |
| created_at | 操作时间 |

- CHECK：`qty<>0`；`(type='IN' AND qty>0) OR (type='OUT' AND qty<0) OR type='ADJUST'`；`after_qty>=0`。
- 索引：`(item_id, id)` 流水正序回放、`(txn_type, id)`。

### 1.2 事务与并发（`CpsInventoryTxnService.register`）

单个 `@Transactional` 内：
1. `selectForUpdate(itemId)` 悲观行锁（同件多次操作串行化）；
2. 语义校验：qty 符号按类型、qty≠0、非 ADJUST 整数即原值；
3. `after = before + qty`；`after < 0` → `IllegalArgumentException`（→400 超可用库存/负库存，§25.4 已确认禁止）；
4. `update stock_qty`（行锁内条件更新）+ 插入 txn 快照；
5. 同事务调 E3 评估（见 §2）。

DB 层 `chk_cps_inventory_stock (stock_qty>=0)` 为最后兜底：绕过服务层直写时违反即整事务回滚。

### 1.3 端点（`/api/cps/admin/inventory-txns`，CpsAdminInventoryTxnController）

- `POST /api/cps/admin/inventory-txns`：登记出入库 `{itemId, txnType, qty, operatorEmpNo, operatorName?, remark?}` → 返回 txn + 最新 item + 触发的预警动作。
- `GET /api/cps/admin/inventory-txns?itemId&txnType&page&size`：流水分页（total+rows，LIMIT/OFFSET，与 admin issues 一致），行含 item_code/item_name 冗余便于展示。

## 2. E3 库存预警（AC-12/13；§25.4 已确认合并事件）

### 2.1 表 `cps_inventory_alert_event`（V20261003）

| 列 | 说明 |
|---|---|
| id / item_id | 主键 / 物品 |
| status | `OPEN` / `RESOLVED_AUTO`（回升自动解除）/ `RESOLVED_MANUAL`（人工关闭）/ `IGNORED`（人工忽略） |
| first_triggered_at / last_eval_at | 事件首触/最近评估时间（合并期） |
| last_eval_qty / threshold_snapshot | 最近评估库存、触发时阈值快照 |
| closed_at / closed_by / close_reason | 关闭信息 |

- **合并事件 DB 兜底**：生成列 `open_item_id = IF(status='OPEN', item_id, NULL)` STORED + `UNIQUE(open_item_id)`——同物品同时最多一行 OPEN（"持续不足合并为同一预警事件"）。
- 索引：`(status, last_eval_at)`、`(item_id, id)`。

### 2.2 评估算法（出入库同事务内，`evaluateAlert(item)`）

- `low = stock_qty <= alert_threshold`（**含等于**，AC-13：阈值 5 时库存 4/5 触发、6 不触发）。
- low 且无 OPEN → 插入 OPEN 事件（first_triggered_at=now）。
- low 且已有 OPEN → 仅更新 last_eval_qty/last_eval_at（**合并，不开新事件**）。
- 不 low 且有 OPEN → `RESOLVED_AUTO`（库存大于阈值自动解除，§25.4）。
- 恢复后再次不足 → 新 OPEN 行（新事件）。

通知渠道：§27.8 未确认，本波不发送通知，仅落事件（`notify-{alertEventId}-...` 幂等键留待渠道确认后接入）。

### 2.3 端点

- `GET /api/cps/admin/inventory-alerts?status&itemId&page&size`：预警分页列表。
- `POST /api/cps/admin/inventory-alerts/{id}/handle`：`{action: IGNORE|CLOSE, reason?, operatorEmpNo?}`——人工处理；OPEN→目标状态，非 OPEN 拒绝（400）。

## 3. E4 管理端（cps/admin，React+Arco）

新页 `InventoryPage`（路由 `/inventory`，导航"物品台账"）三块：
1. 台账列表：§25.1 字段 + 余量/阈值 + 低库存徽标（`stock<=threshold` 含等于）+ 启停；
2. 出入库登记：弹窗表单（物品选择/类型/数量/备注）+ 提交后刷新；流水查询（按物品/类型过滤分页）；
3. 预警处理：Tab 列表（状态过滤）+ 忽略/关闭（关闭必填原因）。

api.ts/types.ts 增补对应方法与类型；`npm run build` 过即验收（admin 前端在仓内）。

## 4. D4 建单记录状态（AC-06/30）

### 4.1 表 `cps_plan_build_record`（V20261004，append-only）

| 列 | 说明 |
|---|---|
| plan_id / task_type | 计划 / 三类任务类型 |
| result | `CREATED` / `SKIPPED_EXISTING`（幂等跳过）/ `CREATE_FAILED`（失败留痕，可补建） |
| error_msg / built_by | 失败原因 / 触发者（approve 人或 rebuild 操作者） |
| build_source | `APPROVE` / `REBUILD` |
| created_at | 本次建单动作时间 |

索引 `(plan_id, id)`；append-only 保留全部尝试历史（重启/重试可追溯）。

### 4.2 建单引擎改造（CpsInspectionPlanService.createPlanTasks）

- 每类任务 INSERT 失败（非重复键）→ 插入 `CREATE_FAILED` 记录并**继续其余类型**（部分失败不阻断批准，AC-30"失败仅补建"）；成功 → `CREATED`；已存在跳过 → `SKIPPED_EXISTING`。
- DuplicateKeyException 维持原语义（并发兜底，记 SKIPPED_EXISTING）。

### 4.3 端点（CpsInspectionPlanController 增补）

- `GET /api/cps/inspection-plans/{id}/record-status`：聚合视图 `{plan 摘要, perType: [{taskType, taskExists, taskStatus, lastBuild{result,errorMsg,at,source}}], summary{created/failed/missing}}`——计划→建单结果→任务状态一屏可查。
- `POST /api/cps/inspection-plans/{id}/rebuild-tasks`：**仅补建** CREATE_FAILED/缺失类型（已成功项不重复创建，UNIQUE 兜底），仅 APPROVED 计划可调。

## 5. E5/D5 测试

- `CpsInventoryTxnServiceTest`（E5，Mockito）：IN/OUT/ADJUST 正确增减；超出库存出库拒绝（400 路径）；AC-13 边界（阈值5：4/5 触发、6 不触发）；合并事件（持续不足不重复 OPEN）；回升自动解除；再不足新事件；人工忽略/关闭及非 OPEN 拒绝。
- `CpsInspectionPlanRecordStatusTest`（D5）：批准建三任务+记录 CREATED；重复 approve→SKIPPED_EXISTING；单项失败→CREATE_FAILED 且其余继续；rebuild 仅补缺失/失败；record-status 聚合正确；草稿→审批→建单三型→幂等重试→补建→记录查询全链。
- `CpsWave8MapperContractTest`：XML 契约（FOR UPDATE、after_qty、open_item_id 生成列、build_record 列）。

## 6. 交付与验证

- 迁移在真实库验证：`mvn spring-boot:run` 起 Flyway 后停（local MySQL cps 库）。
- `mvn test` 全绿（基线 242/0 + 新增）；`admin npm run build` 过。
- commit + push main。
