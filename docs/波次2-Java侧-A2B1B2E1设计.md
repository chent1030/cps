# 波次2 Java侧 A2/B1/B2/E1 设计口径

> 范围：A2 事项编辑/转办锁定+审核员改配+乐观锁；B1/B2 辅房基础配置；E1 设备台账。
> 基线：Java 8 / MyBatis(XML) / Flyway，`mvn test` 115 全绿（82→95→115），真实库已迁移 V20260924~V20260926。

## A2 事项锁定与乐观锁（PRD §28.3/§28.4/§30.2，AC-24/25/26）

- **版本锁定**：`CpsWorkflowStateMachineV2.isVersionLocked()` = AI 初审中(PENDING_AI_REVIEW)/待配置(PENDING_REVIEWER_CONFIG)/待人工审核(PENDING_REVIEW) 三态锁定；仅 PENDING_RECTIFY 可编辑（SAVE_DRAFT/SUBMIT_RECTIFICATION/TRANSFER）。锁定态抛 `Issue version is locked in status ...: edit/transfer not allowed until review finishes (PRD 28.3, AC-26)`。PENDING_FEEDBACK 的 TRANSFER 是反馈改派，不受锁约束。
- **审核员改配**：`CpsIssueService.reassignReviewer(issueId,newReviewerEmpNo,operatorEmpNo,reason)`（§30.2/AC-25）：
  - 仅三审核态可改配；PENDING_REVIEWER_CONFIG 单续路 PENDING_REVIEW（配置后继续流转不重交）；PENDING_REVIEW 时 currentHandler 同步切新审核员（原审核员失权）；AI 初审中改配不触碰初审任务（不重置计时），handler 保持 null。
  - 拒绝：CLOSED/legacy 单/新旧同员/reason 空。写流审日志 action=REVIEWER_REASSIGN（from/to=原/新审核员，comment=reason）。
  - 端点：`POST /api/cps/issues/{id}/reassign-reviewer`（body=CpsReviewerReassignRequest）。
- **乐观锁**（AC-24）：`cps_issue.lock_version INT NOT NULL DEFAULT 0`（V20260924）。`updateWorkflowFields` 带 CAS：`SET lock_version=IFNULL(#{lockVersion},0)+1 ... WHERE id=# AND lock_version=IFNULL(#{lockVersion},0)`，返回受影响行数；service 层 rows!=1 抛 `Concurrent modification detected on issue {id}, please refresh and retry`。updateStatus/updateReviewerAndHandler 亦自增。
- 待确认：empName 占位返回工号本身（无员工主数据表，后续接入）。

## B1/B2 辅房基础配置（PRD §23；V20260925）

| 表 | 口径 |
|---|---|
| cps_room | §23.1 字段全集：楼栋/门牌/名称/类型/风险等级(HIGH/MEDIUM/LOW)/部门/责任人工号+姓名/经理工号+姓名+room_code 唯一+enabled。区域人员责任落在房间行（keeper） |
| cps_check_item | §23.2 字段全集：content/photo_category(照片对象类别)/deduct_score(不合格扣分)/status(APPLICABLE/NOT_APPLICABLE)/applicable_room_types/config_version(UPDATE 自动+1) |
| cps_inspection_item | W4 巡检事项（回填 cps_issue.inspection_item_id，V20260923 预留列） |
| cps_inspection_item_permission | §30.1 查看权限：UNIQUE(item_id,emp_no)；**无配置行=开放，有配置行=仅授权工号** |

- 端点：`/api/cps/admin/rooms`、`/api/cps/admin/check-items`（GET 列表/GET {id}/POST 保存?id空=新建/PATCH {id}/enabled）；事项域 `/api/cps/inspection-items`（+GET params=empNo 可见列表、GET {id}/can-view、PUT {id}/permissions 整体替换名单，空名单=回到开放）。
- 评分口径（§24.3，B 线后续实现评分时消费）：单次=100−不合格适用项扣分和，最低 0；不适用不扣分。
- 【待确认】①"点检项目配置表"歧义（辅房点检项 vs 巡检事项）→ 两读都覆盖（cps_check_item+cps_inspection_item）；②辅房"适用辅房范围"先用 applicable_room_types CSV，空=全部适用，后续如需按房间精确关联再建关联表；③§23.1 基地/工厂口径（base/factory 列已预留 NULL）。

## E1 设备台账（PRD §25.1；V20260926）

- `cps_inventory_item`：§25.1 字段全集（物品名称/计量单位/库存数量/预警数量/基地/工厂/存储房间/房间责任人工号+姓名）+item_code 唯一+`CHECK (stock_qty>=0)`。
- 预警语义（§25.2/AC-13，E3 消费）：`stock_qty <= alert_threshold`（含等于；阈值5时库存4、5预警，6不预警）。已提供 lowStock 过滤参数与 `isLowStock()`。
- E2 出入库流水（cps_inventory_movement before/after 快照）/E3 预警事件表（cps_inventory_alert_event）留待后续波次，本波只建台账+手工 CRUD（`/api/cps/admin/inventory-items`）。

## 测试与验证

- 新增测试：CpsIssueServiceA2Test(12)+CpsIssueMapperContractTest CAS 契约(1)+CpsInspectionItemServiceTest(7)+CpsRoomServiceTest(3)+CpsCheckItemServiceTest(3)+CpsInventoryItemServiceTest(3)+CpsWave2MapperContractTest(4) → 合计 115 全绿。
- 真实库（127.0.0.1:3306/cps）经 Flyway 7.7.3 实际执行 V20260924/25/26（history success=1×3）；五表+lock_version 冒烟 INSERT 于事务内回滚，无残留数据。

## 风险

- 管理端鉴权沿用波次1 admin-login 模式（无强制 token 校验），生产前需补统一鉴权拦截。
- empName/empNo 无员工主数据源，均为直存字符串。
- PENDING_FEEDBACK+REPLY_ASSIGN/TRANSFER 未加乐观锁 CAS 之外的办理人并发保护（与 legacy 一致）。
