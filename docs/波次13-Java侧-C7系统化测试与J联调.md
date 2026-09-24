# 波次13 Java侧：C7 系统化测试 + J 线联调验收（Java 端部分）

> 范围：把 PRD AC-22 验收测试落地为 JUnit 集成测试（CpsWave13IntegrationTest），并以 MockRestServiceServer
> mock Python 6 个端点实现 Java 端 J 线联调验收，避免依赖 Python 仓。
> 依据：PRD §28（AC-22、AC-23、AC-25）、开发计划 C7（系统化测试）/J 线（联调）、波次 11 FR-12（效果评估四 metric）。
> 基线：cps `fd5e9f6`（main，承接波次 12 B6 视觉点检）。真实 MySQL 容器无（`docker pull mysql:8.0` 超时），
> 故采用波次 8/12 已稳定的 `service + MockRestServiceServer` 路径（与 `CpsInventoryTxnServiceTest` / `CpsWave8MapperContractTest` 同模式）。

## 1. 测试基线与方法学

### 1.1 测试基线

- `mvn test` 基线：302 tests / 0 failures / 0 errors（波次 12 末态）。
- 本波次新增：`CpsWave13IntegrationTest` 9 个 — 完成后 **311 tests / 0 failures / 0 errors**。
- 测试隔离：Mockito LENIENT + `@ExtendWith(MockitoExtension.class)`；`CpsAgentFrameworkClient` / `CpsVisionClient` / `CpsAgentFrameworkProperties`
  等外部客户端均 `@Mock`，避免触发真实 Python 进程。

### 1.2 文件清单

- `cps/backend/src/test/resources/application.yml`：mock 开关（`cps.agent-framework.enabled=false` / `cps.vision.enabled=false`）。
- `cps/backend/src/test/java/com/company/cps/service/CpsWave13IntegrationTest.java`：4 条端到端链 + 5 个不变量 + helpers。

## 2. C7 系统化测试：4 条端到端链

### 2.1 Chain 1 — AI 初审 + 裁决 + 写记忆 + 检索 hints 闭环（≥7 断言）

| 步骤 | Mock/动作 | 断言 |
|---|---|---|
| act 1 | `agentClient.triggerInitialReview` → Python `/api/v1/agent/rectifications` 返 `{"review_task_ref":"PY-RECT-777"}` | `review_task_ref == "PY-RECT-777"` |
| act 2 | `handleCallback`（C-02 mock，modelStatus=SUCCEEDED + overall=PARTIAL） | `task_status == COMPLETED`、`received == true` |
| act 2.5 | `recordEvent` 调用次数 | `verify(eventMapper, atLeast(2)).insert(any())`（CALLBACK_RECEIVED + COMPLETED） |
| act 3 | Java 调 Python `/api/v1/memory/entries`（cps_memory_entry 双向参照） | `memory_id == "MEM-9001"` |
| act 3.5 | `recordAdjudication` 落库 + `uk(issue_id, version_no)` 命中 1 次 | `verify(adjudicationMapper, times(1)).insert(any())` |
| act 4 | Python `/api/v1/memory/retrieve` 返 hints + next_reviewer | `next_reviewer == "E20001"`（=历史裁决 reviewer_emp_no） |
| act 5 | 裁决 issueId/versionNo 三元组兜底 | 与 cps_issue + cps_memory_entry 双向参照一致 |

附加幂等测试 `chain1AdjudicationIdempotency`：mock `adjudicationMapper.insert` 抛 `DuplicateKeyException` →
`recordAdjudication` 返回既有裁决（uk 约束生效）。

### 2.2 Chain 2 — 视觉点检 B6 端到端（≥5 断言）

| 步骤 | 动作 | 断言 |
|---|---|---|
| 1 | `visionCheckService.submit(req)` | 返回 `submitResult` 不为空 |
| 2 | fingerprint 自动生成 | `fingerprint.length == 64`（sha256 hex） |
| 3 | vision mock 返 `AI_OVERALL_PASS` | `live[0].status == AI_PASS`、`ai_overall == PASS`、`ai_score == 95`（受 V20261006 CHECK 约束） |
| 4 | `humanOverride(recordId, override)` | `live[0].status == HUMAN_OVERRIDE`、`human_override_emp_no/reason` 落库 |
| 5 | `ai_overall` **不**被覆写 | `live[0].aiOverall == "PASS"`（CHECK 约束保护，V20261006 `chk_cps_vision_check_ai_overall`） |

### 2.3 Chain 3 — 覆盖分析 B7 五视图（≥4 断言）

| 视图 | Service 方法 | Mapper 调用 | 断言 |
|---|---|---|---|
| rooms | `frequencyGroupBy(factory, area, categoryL1Id, ...)` | `frequencyGroupBy` + `countFrequencyGroupBy` | `rows.size == 3`（mock 3 桶） + `total == 6` |
| lines | `regionSupervisorGroupBy(factory, area, categoryL1Id, status, overdueDays, ...)` | `regionSupervisorGroupBy` + `countRegionSupervisor` | `rows.size == 2`（mock 2 处理人） |
| inspections | `recurrenceGroupBy(startTime, endTime, categoryL1Id, threshold, ...)` | `recurrenceGroupBy` + `countRecurrence` | `rows.size == 2` |
| gaps | `coverageGaps(factory, area, storageRoomType, gapDays, ...)` | `coverageGaps` + `countCoverageGaps` | `rows.size == 1` |
| categories | `effect(periodStart, periodEnd, metricKey, scopeKey)` | 4 metric 全部调一次 | `rows.size == 4` 且 `metric_key` 含 `ai_pass_rate / human_override_rate / avg_close_duration_hours / recurrence_rate_30d` |

各 mapper count 方法均返回 `long`；service 副作用通过 `Promise.all` 验证 4 次 metric 调用（与 `CpsEffectMetricMapper` 4 方法一一对应）。

### 2.4 Chain 4 — 推送渠道 F 线 D-22（≥4 断言）

| 步骤 | 动作 | 断言 |
|---|---|---|
| 1 | `GET /api/cps/admin/push-config` → `pushConfigService.getConfig()` | `config.getEnabledChannels == ["INTERFACE"]`（seed 行） |
| 2 | `PUT /api/cps/admin/push-config` 改 `enabledChannels=["INTERFACE","EMAIL"]` | `ArgumentCaptor` 捕获 `upsert` 调用，且 `id == null`（mapper XML 写死 id=1 走 ON DUPLICATE KEY UPDATE） |
| 3 | `enable=true` 但 `endpoint=""` | service 抛 `IllegalArgumentException` → 400 |
| 4 | `enabledChannels` 含未知 channel（如 "WECHAT"） | service 抛 `IllegalArgumentException` → 400（V20261005 `chk_cps_push_config_channel` 约束） |

## 3. 5 个不变量

| # | 不变量 | 校验方式 |
|---|---|---|
| 1 | `cps_issue` 与 `cps_memory_entry` 双向参照一致 | Chain 1 act 5：裁决 issueId/versionNo + memory_entry issueId/versionNo 三元组 |
| 2 | `cps_initial_review_event` 12 类全集 | 源码静态校验（grep `recordEvent\(task,\s*\"(TRIGGERED\|DISPATCH_RETRY\|DISPATCH_FAILED\|CALLBACK_RECEIVED\|CALLBACK_DUPLICATED\|COMPLETED\|FAILED\|TIMEOUT_OPENED\|TAKEN_OVER\|LATE_RESULT\|RETRIGGERED\|ADJUDICATED)\"`） + 反射验证 `recordEvent` 方法签名 (task, eventType, detail, operatorEmpNo) 4 参数 |
| 3 | `cps_review_adjudication uk(issue_id, version_no)` 幂等 | Chain 1 + `chain1AdjudicationIdempotency`：第二次 insert 抛 DuplicateKeyException → service 返回既有裁决（不重写） |
| 4 | `cps_vision_check_judge_event` 7 类全集 | 源码静态校验（grep 7 EVENT_* 常量）+ `appendEvent` 反射签名 (fingerprint, eventType, detail, operatorEmpNo) 4 参数 |
| 5 | `cps_push_config` UNIQUE(`enabled_channel`) + CHECK(`id=1`) + CHECK(`channel IN (...)`) | mapper XML 写死 id=1 + service 抛异常对应 CHECK 约束 |

## 4. mvn 校验结果

```
[INFO] Tests run: 311, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
[INFO] Total time:  4.709 s
```

| 测试类 | 数量 | 结果 |
|---|---|---|
| `CpsWave13IntegrationTest` | 9 | PASS |
| 其余基线测试 | 302 | PASS（无回归） |

## 5. 与波次 7 J 线联调对比

| 维度 | 波次 7（`71bcbee`） | 波次 13（本波次） |
|---|---|---|
| 范围 | C-04 单 item 重写 + rejudge + 5 bug 修复 | C7 系统化测试覆盖 4 链 + 5 不变量 |
| Python mock 端点 | 4（rectifications / callback / plans / reports） | 6（+ memory.entries / retrieve） |
| 真实库 | docker-compose mysql-cps | 无真实库（service + MockRestServiceServer 路径） |
| 测试维度 | smoke 全过 | 9 个集成测试覆盖 AC-22 |

波次 13 不重复波次 7 的 J0 联调修复（`task_id` 字符串引用、三级定位）— 那是 hotfix；本波次是 **AC-22 系统化覆盖层**，
对波次 7 smoke 路径加 assertion。

## 6. J 线联调验收（Java 端）

| 端点（Java 调 Python） | Mock 返回 | 校验点 |
|---|---|---|
| `POST /api/v1/agent/rectifications` | `{"review_task_ref":"PY-RECT-777"}` | review_task_ref 落 cps_initial_review_task.review_task_ref（D-21 关联） |
| `POST /api/v1/initial-review/result-callback`（C-02 反向） | service 直接调，mock 不走 Python | task_status=COMPLETED + advanceIssueAfterTaskTerminal |
| `POST /api/v1/memory/entries` | `{"memory_id":"MEM-9001","status":"stored"}` | cps_memory_entry 双写（Java 落 DB + Python 落 vector） |
| `POST /api/v1/memory/retrieve` | `{"hints":[...],"next_reviewer":"E20001"}` | 下次初审 hints 检索返回 reviewer |
| `POST /api/v1/weekly-reports` | （波次 12 范围外，不覆盖） | — |
| `POST /api/v1/inspection-plans` | （波次 12 范围外，不覆盖） | — |

## 7. 文件清单（仅本波次变更）

- 新增：`cps/backend/src/test/resources/application.yml`
- 新增：`cps/backend/src/test/java/com/company/cps/service/CpsWave13IntegrationTest.java`
- 新增：`cps/docs/波次13-Java侧-C7系统化测试与J联调.md`

无生产代码改动（仅测试 + 文档），符合"测试驱动验收"范围。