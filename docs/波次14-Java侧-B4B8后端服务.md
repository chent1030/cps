# 波次14 Java 侧 B4 / B8 后端服务设计

## 范围

- B4：周评分排名（PRD §24）
- B8：评分规则（PRD §23.1 base-factory 口径）
- 与已有波次13 E4 admin 库存台账不冲突；基线 993abac（main）。

## B4 评分排名

### 数据模型

迁移 `V20261007__cps_weekly_score.sql`：

- `cps_weekly_score`：id / week_start_date(周一 00:00 Asia/Shanghai) /
  emp_no / region_supervisor_id / total_score / room_check_count / photo_count / natural_week_flag
  - 约束：`UNIQUE(week_start_date, emp_no)`、`total_score BETWEEN 0 AND 100`、
    `natural_week_flag IN (0,1)`
  - 索引：`idx_week_supervisor(week_start_date, region_supervisor_id)` /
    `idx_week_score(week_start_date, total_score DESC)` /
    `idx_emp(emp_no)`
- `cps_weekly_score_line`：weekly_score_id / item_id / score_delta / reason
  - 约束：`score_delta BETWEEN -100 AND 100`、
    `item_id IN ('BASE','CONTENT_MISMATCH_DEDUCT','EVIDENCE_VAGUE_DEDUCT','KEYWORDS_MISSING_DEDUCT','OTHER_DEDUCT')`

### 调度与服务

- `@EnableScheduling` 已在 `CpsBackendApplication.java`，无需重复启用。
- `CpsWeeklyScoreScheduler`：cron `0 59 23 ? * SUN`（周日 23:59），
  默认值可通过 `cps.weekly-score.cron` 覆盖；JMX 关闭；
  try/catch 包住调用，单周失败不影响下一轮。
- `CpsWeeklyScoreService.recomputeLastNaturalWeek()`：
  自然周上周（周一→周日）作为重算窗口，operator = SYSTEM，naturalWeekFlag = true。
- `recomputeWeek(weekStart, operator, naturalWeekFlag)`：
  - 调 `CpsIssueMapper.findForWeeklyScore(weekStart, weekEnd, limit)`（按 created_at 区间拉候选问题）。
  - 按 `creator_emp_no` 分组；每组调 `CpsScoringRuleService.resolveRules(factory)`。
  - 按 status 分发扣分项：
    - `CLOSED` → content_mismatch
    - `PENDING_RECTIFY` / `PENDING_FEEDBACK` → evidence_vague
    - `PENDING_AI_REVIEW` → keywords_missing
    - 其它 → other
  - total = base + Σ(deduct) clamp [0,100]。
  - upsert header + deleteLinesByHeader + insertLine。
  - `region_supervisor_id` = `area.hashCode() & 0xffffffffL`（null 时不写）。

### 端点

| 方法 | 路径 | 说明 |
|------|------|------|
| GET  | `/api/cps/admin/scores/weekly` | 分页 + 区域过滤，按 total_score DESC |
| POST | `/api/cps/admin/scores/weekly/recompute` | 手动触发（默认上周；operator 默认 ADMIN） |

`IllegalArgumentException` → HTTP 400（参数错误：operator 空白、日期非法等）。

## B8 评分规则

### 数据模型

迁移 `V20261008__cps_scoring_rule.sql`：

- `cps_scoring_rule`：id / rule_key / rule_value(JSON) / version /
  effective_from / effective_to / factory_calibration_flag / factory / created_at
  - 约束：`UNIQUE(rule_key, factory)`（允许 factory NULL 多个版本共存）、
    `factory_calibration_flag = 0` 当 `factory IS NULL`（一致性 CHECK）、
    `effective_from < effective_to`。
- 5 条全局默认种子（factory=NULL, factory_calibration_flag=0）：
  - `BASE` = 100
  - `CONTENT_MISMATCH_DEDUCT` = 40
  - `EVIDENCE_VAGUE_DEDUCT` = 30
  - `KEYWORDS_MISSING_DEDUCT` = 20
  - `OTHER_DEDUCT` = 10

### 服务

`CpsScoringRuleService.resolveRules(factory)`：

1. `readIntRule(factory, KEY)`：先查 `factory` 非空命中 → 命中则取 `version DESC LIMIT 1`；
   否则回退 `factory IS NULL` 的全局有效规则。
3. DB 全无 → 内置默认 `DEFAULT_BASE=100 / CONTENT=40 / EVIDENCE=30 / KEYWORDS=20 / OTHER=10`。

`parseJsonInt(value)`：极简解析，仅支持 `{"score":N}` 或 `{"deduct":N}`；
非法 JSON 返回 null 并触发回退到下一层。

`upsert(req)`：

- 校验 `rule_key ∈ KNOWN_RULE_KEYS`（5 个常量）。
- 校验 `factory_calibration_flag` 与 `factory` 一致性（NULL 必须 false，非 NULL 必须 true）。
- 校验 `effective_from < effective_to`。
- `version = max(version)+1`（同 rule_key+factory 内单调递增）。

### 端点

| 方法 | 路径 | 说明 |
|------|------|------|
| GET  | `/api/cps/admin/scoring-rules` | 列表（含已过期、含 factory 命中） |
| PUT  | `/api/cps/admin/scoring-rules` | upsert：返回新版本号 |

## 测试覆盖

| 测试类 | 测数 | 说明 |
|--------|------|------|
| `CpsWeeklyScoreServiceTest` | 6 | 自然周 Monday/Sunday、list 排名、recompute 写入/更新、endpoint 操作员白名单 |
| `CpsWeeklyScoreControllerTest` | 2 | GET list / POST recompute 委托 |
| `CpsScoringRuleServiceTest` | 5 | parseJsonInt、factory 覆盖全局、missing→defaults、upsert 校验 3 个 |

合计 13 测，连同基线 311 总数 ≥ 324。

## 已知约束

- `region_supervisor_id` 暂用 `area.hashCode()` 派生，未来有真实督导表时切换。
- 调度 cron 假定 Asia/Shanghai（与 PRD §24 一致）；跨时区部署需调整 `cps.weekly-score.cron`。