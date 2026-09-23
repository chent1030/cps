# 波次5 Java 侧设计：A3 审核裁决域 + A4 初审触发管理

- 基线：cps `39ea9f0`（main），紧接波次4
- PRD 口径：§28（AI 初审三态/超时接管/裁决）、§29（接管规则 AC-27）、§28.4/§29.3（触发配置与记录）、D-21（技术重试 1 次、如实展示）
- 测试：`mvn test` 212/0（基线 176 + 本波次 36 新增）

---

## 1. A3 审核裁决域（PRD §28.3，AC-16/18）

### 1.1 语义

- **AI 只提供意见无决定权**：AI 初审结论（PASS/PARTIAL/PROBLEM）不直接关单/退回；终态由审核员人工裁决决定
- **裁决**：APPROVE=通过 → 走 `REVIEW_CLOSE`（CLOSED，直接关闭）；REJECT=不通过 → 走 `REVIEW_REJECT`（PENDING_RECTIFY，退回整改人员 latest.submittedBy）
- **理由必填**，裁决留痕含 AI 意见快照与关系（WITH_AI=维持 AI 意见 / AGAINST_AI=改判 / NO_AI_RESULT=无 AI 结果）
- **可裁决窗口**：仅 `PENDING_REVIEW` 状态（AI 完成推进后，或超时/失败接管后）；裁决权=当前办理人（handler 校验沿用 executeAction）
- **超时接管后开放态单可裁决**：接管把 current_handler 切到接管人，接管人即裁决人

### 1.2 三态呈现（PRD §28.2，界面必须区分三态）

reviewerView 的 `state_view`：

| state_view | 任务状态 | can_take_over | 说明 |
|---|---|---|---|
| running | RUNNING 且未满阈值 | false（seconds_until_takeover>0） | RUNNING 未满 10min 不可接管（AC-27） |
| timeout_open | RUNNING 已过期 / TIMEOUT_OPEN | true | 运行满 10min（600s，提交成功时刻起算） |
| failed | FAILED | true | 失败立即可接管（AC-27） |
| pending_dispatch | PENDING_DISPATCH | true（走重触发） | 自动触发关闭，仅建任务未投递 |
| taken_over | TAKEN_OVER | false | 已被接管，等待接管人裁决 |
| late_result / completed | 终态 | false | 迟到结果仅留痕，不覆盖裁决 |

### 1.3 裁决状态机与幂等

- 复用 `executeAction(REVIEW_CLOSE/REVIEW_REJECT)`：办理人校验 + `CpsWorkflowStateMachineV2` 转移 + `lock_version` 乐观锁 CAS + 流程日志 + 提交单 markReviewed（口径与 A2 完全一致）
- 幂等锚点：`cps_review_adjudication uk(issue_id, version_no)`——同版本重复裁决返回 `duplicated=true` 不重复流转；DB 层 DuplicateKey 由 `recordAdjudication` 捕获兜底返回既有行

---

## 2. A4 初审触发管理（PRD §28.4/§29.3，D-21）

### 2.1 触发配置（DB 单行 GLOBAL，运行时可改）

| 字段 | 缺省 | 约束 | 语义 |
|---|---|---|---|
| auto_trigger_enabled | 1 | bool | 提交时自动触发 AI 初审；0=仅建任务 `PENDING_DISPATCH` 待手动触发 |
| max_retry_attempts | 1 | 0..5 | C-01 投递失败技术重试次数（D-21=1）；重试不重置计时 |
| retry_backoff_ms | 3000 | 0..60000 | 投递重试退避（实际上限 30s） |
| timeout_seconds | NULL | 30..86400 或 NULL | 接管阈值秒；NULL=回退应用配置 `cps.initial-review.timeout-seconds`(600) |

- 读取回退链：DB GLOBAL 行 → null 字段回退应用配置缺省
- PUT 全量替换语义；GLOBAL 行缺失时自愈插入（迁移已种子）

### 2.2 触发记录（事件流水 cps_initial_review_event）

event_type 全集：TRIGGERED / DISPATCH_RETRY / DISPATCH_FAILED / CALLBACK_RECEIVED / CALLBACK_DUPLICATED / COMPLETED / FAILED / TIMEOUT_OPENED / TAKEN_OVER / LATE_RESULT / RETRIGGERED / ADJUDICATED；operator_emp_no=SYSTEM 或人工工号；按 task_id/issue_id 索引查询，正序回放。

### 2.3 手动重触发（retrigger）

- 仅 `FAILED` / `PENDING_DISPATCH` 任务且该版本未裁决（有裁决或 issue CLOSED 拒绝）；operator+reason 必填
- 新幂等键 `cps-rectify-{issueId}-v{n}-r{newRetryCount}`，CAS 守卫（旧状态），重置 submitted_at/timeout_at（新投递轮次独立计时——区别于投递内自动重试不重置计时）
- issue 在 `PENDING_REVIEW` 时系统回退 `PENDING_AI_REVIEW`（状态机新系统转移 + 清 current_handler，`updateStatusClearHandler` 带 fromStatus CAS）；插入 flowLog（action=AI_REVIEW_RETRIGGER，operator=操作人）
- 事务提交后重新投递（dispatchAfterCommit）

---

## 3. 数据库变更（V20261001__cps_review_adjudication_a3a4.sql）

| 表 | 用途 | 关键约束 |
|---|---|---|
| cps_review_adjudication | 人工裁决留痕 | uk(issue_id,version_no)；decision APPROVE/REJECT；ai_overall 快照；ai_relation；reason 必填；from/to_status |
| cps_initial_review_config | 触发配置单行 | config_key='GLOBAL' 种子行 |
| cps_initial_review_event | 触发/回调/超时/接管/重触发/裁决流水 | idx(task_id),(issue_id) |

枚举扩展：`CpsInitialReviewTaskStatus.PENDING_DISPATCH`；`CpsIssueAction.AI_REVIEW_RETRIGGER`（管理动作，不进 availableActions）。

## 4. 端点清单

### mobile（CpsIssueController，/api/cps/issues）
| 端点 | 方法 | 说明 |
|---|---|---|
| /{id}/initial-review | GET | 审核员初审视图：三态 state_view + can_take_over + seconds_until_takeover + task/result/items/submission/adjudication/events |
| /{id}/initial-review/take-over | POST | 超时/失败接管 {empNo, reason}；RUNNING 未满 10min 拒绝 |
| /{id}/adjudicate | POST | 人工裁决 {empNo, decision APPROVE/REJECT, reason} |

### admin（CpsAdminInitialReviewController，/api/cps/admin/initial-review）
| 端点 | 方法 | 说明 |
|---|---|---|
| /config | GET/PUT | 触发配置读取/更新（校验边界见 §2.1） |
| /tasks | GET | 触发记录分页 ?status&issueId&page&pageSize（JOIN 问题状态/AI 结果/裁决视图） |
| /tasks/{id} | GET | 任务详情：task+result+items+events+adjudication |
| /tasks/{id}/retrigger | POST | 失败任务手动重触发 {operatorEmpNo, reason} |

## 5. 测试清单（36 新增，全套 212/0）

- `CpsIssueServiceA3Test`（10）：APPROVE/REJECT 写回终态、WITH_AI/AGAINST_AI/NO_AI_RESULT 关系矩阵、非 PENDING_REVIEW 拒绝、幂等 duplicated、理由/决策校验、版本缺失、非当前办理人拒绝
- `CpsInitialReviewA4Test`（12）：reviewerView 三态与接管剩余秒数（AC-27：未满不可接管/过期可接管/失败立即可接管）、retrigger 状态守卫、幂等键轮次、计时重置、PENDING_REVIEW 回退清办理人+flowLog、已裁决/已关闭拒绝、必填校验、autoTrigger=false 停留 PENDING_DISPATCH
- `CpsInitialReviewAdminServiceTest`（8）：配置缺省回退、边界校验（maxRetry 0..5/backoff 0..60000/timeout 30..86400）、全量写入、GLOBAL 自愈、分页钳制与过滤透传、详情组装、未知任务拒绝、retrigger 委托
- `CpsWave5MapperContractTest`（6）：三表字段全集、uk 幂等锚点、CAS 守卫子句、admin JOIN 视图、updateStatusClearHandler、事件类型全集注释

## 6. 风险与待确认

- admin 端点沿用波次1口径无应用层鉴权（部署层 SSO/网关注入）；如需应用层角色校验待后续波次统一
- `empName` 无员工主数据，沿用直存字符串占位（波次2口径）
- retrigger 回退 `PENDING_REVIEW→PENDING_AI_REVIEW` 为系统转移，服务层已双重校验（未裁决 + 任务 FAILED/PENDING_DISPATCH）；若并发裁决与重触发竞争，裁决侧 uk 幂等与 retrigger 侧 CAS 均会失败一方，数据不会双写
- 投递重试在 `dispatch` 内同步退避 sleep（上限 30s/次），量级小可接受；若未来重试次数放大需改异步队列
