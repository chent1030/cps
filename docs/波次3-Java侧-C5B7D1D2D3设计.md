# 波次3 Java侧 C5/B7/D1/D2/D3 设计口径

> 范围：C5 管理端周报代理 + B7 周报数据源 + D1 巡检计划表/申请草稿 + D2 审核界面 + D3 建单引擎（C-09）。
> 基线：Java 8 / MyBatis(XML) / Flyway，`mvn test` 151 全绿（115→151），真实库已迁移 V20260927~V20260928。

## C5/C8 管理端周报代理（PRD §21.3；AC-04/05；后端架构 C-05/C-08）

- **端点**：
  - `GET /api/cps/admin/weekly-reports?inspectionType&status&periodStart&periodEnd&operatorEmpNo` —— 列表查询（按类型/周期/状态过滤）；
  - `GET /api/cps/admin/weekly-reports/{runId}/download?operatorEmpNo` —— 受控下载，Java 调 Python `C-05 /api/v1/agent/weekly-report-runs/{runId}/file` 取流后透传（含 Content-Type/Content-Disposition）；
- **实现**：Java 不持久化 `weekly_report_run`（数据源在 Python PG；后端架构 §2.3）；`CpsWeeklyReportService` 仅做参数透传 + 响应归一化为 `CpsWeeklyReportRun` DTO（`LocalDateTime` 解析兼容 Python ISO 字符串，Z/时区偏移去除后按业务约定视为 Asia/Shanghai 入库值）；
- **下载转发**：`CpsAgentFrameworkClient.downloadWeeklyReportFile` 用 `RestTemplate.exchange` 取 `byte[]` + headers，封装为 `WeeklyReportFile` 由 controller 写入 `HttpServletResponse`；
- **权限**：复用现有 `operatorEmpNo` principal 机制（admin 弱鉴权沿用波次 2 标注，生产前需补统一鉴权拦截）。

## B7 周报数据源（PRD §21.1/§21.3；后端架构 §2.1）

- **视图（V20260927）**：`weekly_report_data_*` 五张视图，仅 SELECT cps_* 业务事实表，不在 Java 侧做语义汇总：
  - `weekly_report_data_issue_summary`（按 submit_date/factory/line/process/status 聚合 `cps_issue`）；
  - `weekly_report_data_rectification_summary`（按 submit_date/source/status 聚合 `cps_rectification_submission`）；
  - `weekly_report_data_initial_review_summary`（按 submit_date/task_status/overall 聚合 `cps_initial_review_task`+`cps_initial_review_result`）；
  - `weekly_report_data_check_item_snapshot`（`cps_check_item` 投影）；
  - `weekly_report_data_inventory_low_stock`（按 factory/storage_room/责任人聚合 `cps_inventory_item`，条件 `stock_qty<=alert_threshold`）。
- **读端点**（`CpsWeeklyReportDataController`，前缀 `/api/cps/weekly-report-data`）：
  - `GET /window/current` —— 计算东八区"上一完整自然周"窗口（[上周一 00:00, 本周一 00:00)，§21.1.6）；
  - `GET /issues?factory&periodStart&periodEnd` —— 窗口内新建问题分布；
  - `GET /rectifications`、`GET /initial-reviews` —— 整改提交 / 初审结果分布；
  - `GET /check-items`、`GET /inventory/low-stock` —— 当前快照（无窗口参数）。
- **窗口语义**：`CpsWeeklyReportDataService.Window` 强制 `periodStart < periodEnd`（左闭右开）；缺省按 `ZoneId.of("Asia/Shanghai")` + `TemporalAdjusters.previousOrSame(MONDAY)` 计算；
- **权限**：面向内部 Python 进程，不做权限校验——生产环境需在网关/反向代理层做内网 CIDR 校验（沿用 `cps.callback.trusted-ips` 思路）。

## D1 巡检计划表 + 申请草稿（PRD §22.1/§22.2；C-07）

- **迁移（V20260928）**：`cps_inspection_plan` 表全字段落点：
  - 业务列：`source_run_id` / `report_id` / `plan_type` / `title` / `target_factory` / `target_area` / `risk_basis` / `draft_content_json`；
  - 状态机：`status ENUM('PENDING_REVIEW','APPROVED','REJECTED')`；
  - 幂等：`draft_idempotency_key` UNIQUE（`plan-draft-{sourceRunId}`）、`UNIQUE(source_run_id, plan_type)`（同源同型只一份草稿）；
  - 版本：`config_version` 草稿自增、`lock_version INT DEFAULT 0` 乐观锁 CAS。
- **申请草稿**（`CpsInspectionPlanService.applyDraft`）：
  1. 按 `(source_run_id, plan_type)` 查重；存在则 `updateDraftCas`（CAS：status=PENDING_REVIEW）回写草稿 + `config_version++`；
  2. 否则 INSERT 新行（status=PENDING_REVIEW, lock_version=0, config_version=1）；
  3. 草稿内容优先取请求预填 `draftContentJson`，否则调 `CpsAgentFrameworkClient.requestInspectionPlanDraft`（Python `C-07 POST /api/v1/agent/inspection-plans/draft`）——Python 缺位时 client 禁用返回 null，service 落空草稿保留可手工编辑路径；
  4. 幂等键 `draft_idempotency_key` 与 C-07 响应一致（`plan-draft-{sourceRunId}`）。

## D2 审核界面（PRD §22.2；AC-30）

- **端点**（`CpsInspectionPlanController`，前缀 `/api/cps/inspection-plans`）：
  - `POST /apply-draft` —— D1 申请草稿；
  - `GET ?status=PENDING_REVIEW` —— 审核列表（默认 PENDING_REVIEW；null=全部）；
  - `GET /{id}` —— 详情（含 `tasks`）；
  - `POST /{id}/approve?operatorEmpNo` —— 批准（body：`CpsInspectionPlanApproveRequest{lockVersion, approver, approverName, comment}`），响应含 `plan` + `createdTasks`；
  - `POST /{id}/reject?operatorEmpNo` —— 拒绝（reason 必填）。
- **乐观锁 CAS**（AC-30 与 A2 一致）：
  - `approveCas` / `rejectCas` SQL：`UPDATE ... SET status=?, lock_version=IFNULL(#{lockVersion},0)+1, ... WHERE id=? AND status='PENDING_REVIEW' AND lock_version=IFNULL(#{lockVersion},0)`；
  - service 层 rows!=1 抛 `IllegalStateException("Inspection plan approve/reject CAS failed...")`；
  - approve/reject 前 service 守卫状态必须为 PENDING_REVIEW（避免已批/已拒重放）。

## D3 建单引擎（PRD §22.2；AC-30；C-09）

- **任务表（V20260928）**：`cps_inspection_plan_task`，三类任务同表区分：
  - 列：`plan_id` / `task_type ENUM('INSPECT_RECTIFY','INSPECT_PATROL','INSPECT_CHECK')` / `title` / `target_emp_no+name` / `scheduled_at` / `frequency` / `acceptance_criteria` / `evidence_requirement` / `task_status` / `reference_issue_id` / `reference_object_key,type`；
  - 幂等：`UNIQUE KEY (plan_id, task_type)` —— 同计划同类型任务不重复建（数据库层兜底）。
- **事务流（`createPlanTasks` 在 approve 事务内）**：
  1. `findExistingTaskTypes(planId)` 拉已有 task_type 集合；
  2. 固定三类顺序 INSPECT_RECTIFY → INSPECT_PATROL → INSPECT_CHECK 遍历；
  3. 已存在的跳过（log debug）；缺失的 INSERT（UNIQUE 冲突由 `DuplicateKeyException` 捕获并视为"已被并发补建"）；
  4. 任务明细从 `plan.draft_content_json.tasks.{TASK_TYPE}` 提取（可选：title/emp_no/scheduled_at/acceptance_criteria/evidence_requirement/reference_*），缺则落兜底（title = `plan.title + " - " + taskType`、scheduled_at = approve 时间）；
  5. 部分创建失败仅补建未存在项（service 级幂等），同 plan 重复 approve 重启/重试只产 3 个任务（DB 级 UNIQUE 兜底）。
- **AC-30 验证**：
  - 单测 `CpsInspectionPlanServiceTest.approveCreatesThreeTasksAndRejectsReplayOnAlreadyApproved`：首次 approve 产 3 任务；再 approve 同 plan 抛 IllegalStateException；
  - 单测 `createTasksSkipsExistingTypesAndInsertsMissing`：findExistingTaskTypes 返回 [INSPECT_RECTIFY] 时，新 approve 仅补建 2 任务（PATROL+CHECK）；
  - 真实库冒烟：删除 INSPECT_CHECK 后再 approve，自动补建 1 项（最终 3 任务，UNIQUE 兜底验证）。

## 测试与验证

- **新增 36 测试**（mvn test 115→151）：`CpsInspectionPlanServiceTest` 18（CAS/幂等/draft 解析/draft 缺失 fallback/C-07 路径）；`CpsWeeklyReportServiceTest` 6（参数透传/归一化/窗口校验）；`CpsWeeklyReportDataServiceTest` 6（窗口左闭右开/MONDAY 对齐/Window 构造校验）；`CpsAgentFrameworkClientWave3Test` 6（C-07/C-05/C-08 序列化与禁用）。
- **真实库（127.0.0.1:3306/cps）**：Flyway 7.7.3 实际执行 V20260927（视图）+ V20260928（2 表，success=1×2）；冒烟端到端：`POST /apply-draft` → `GET ?status=PENDING_REVIEW` → `POST /{id}/approve`（3 createdTasks 落库）→ 手动删 INSPECT_CHECK 后再 approve（自动补建 1 项）。
- **B7 端到端**：`GET /api/cps/weekly-report-data/window/current` 返回 `period_start=2026-09-14T00:00, period_end=2026-09-21T00:00`（东八区今日 9-22 推回上一周）。

## 风险与待确认

- **admin 弱鉴权沿用波次 2**：`operatorEmpNo` 必填但仅日志留痕；生产前需补统一鉴权拦截（与 cps_issue/初始审查审核员鉴权统一）；
- **无员工主数据表**：`approver_name`/`target_emp_name` 均为字符串直存，与 §23 责任人/§25 房间责任人字段一致；
- **推送状态（§21.1.8）未在本波次校验**：C-05 响应中 `push_status=UNCONFIGURED` 仅透传，不阻断下载（§21.1.8 要求"推送未配置不得记成功"——本期周报文件生成与下载交付完整，推送渠道由用户接入实现，对齐 §33.1 唯一明确边界）；
- **任务类型枚举**：`INSPECT_RECTIFY/INSPECT_PATROL/INSPECT_CHECK` 与 §22.1"区域巡检/辅房点检/整改复查"映射如确认后改枚举名；
- **Python C-07 实现可后置**：本期 Java 用接口 client + stub ok（client disabled 时 service 不强拒）；Python 实现后端只需实现 POST `/api/v1/agent/inspection-plans/draft` 端点（幂等键 `plan-draft-{sourceRunId}`）即可联调。
