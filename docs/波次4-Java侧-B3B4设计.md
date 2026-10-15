# 波次4 Java侧 B3/B4 设计（辅房点检执行域 + 评分排名）

基线：cps `7aa87a0`（main，波次3之后）。本波次实现 PRD §22-§24 中 B 线剩余两块：
B3 辅房点检执行域（mobile）、B4 评分排名（admin）。C-04 判定服务由 Python 侧并行交付（B6），本侧只做客户端与降级。

## 1. 范围与不做

- 做：CHECK 任务执行（房间×点检项明细快照）、照片上传关联 RustFS、提交同步判定（可配置端点 + 降级）、状态机、mobile 任务列表/明细/照片/提交端点、100 起步扣分制评分、自然周排名与评分明细 admin 端点。
- 不做：跨周拆分（D-08 明确不存在跨周）、管理员改回已判定单（PRD 24.2.4 提交后锁定，无改回入口）、库存预警（§25，另有波次）、Python 侧判定逻辑实现（J 线联调）。

## 2. 数据模型（V20260929__cps_room_check_record.sql）

无 B4 新表——排名实时聚合（数据量=周×房间，量级小），避免冗余一致性维护。

**cps_room_check_record**（点检单，房间×一次执行）
- plan_task_id / plan_id（可空：允许无任务的临时点检）、room_id / room_code / room_name（快照冗余，报告直读）
- check_emp_no / check_emp_name（工号占位，同波次2口径，员工主数据缺位）
- record_status：PENDING(待执行) → IN_PROGRESS(执行中) → JUDGED(已判定)，VARCHAR(16)
- judge_status：SUCCESS / PENDING（NULL=未提交）；score INT NULL（降级时 NULL）
- started_at（start 或首照）、submitted_at（提交=归属自然周的时间轴，D-08）
- 索引：(plan_task_id, room_id) 幂等查、(room_id, submitted_at) 明细查、(submitted_at) 周聚合

**cps_room_check_record_item**（明细=点检项快照，PRD §23.2 执行时引用检查时配置）
- check_item_id + item_code / content / photo_category / deduct_score / config_version 全快照——点检项配置后续变更（含 config_version 自增）不影响已生成明细
- photo_object_key / photo_file_name：RustFS 对象（对齐 A1 附件机制）
- judge_result（TYPE_MISMATCH/UNJUDGEABLE/PASS/FAIL/PENDING）+ judge_reason + final_result + judged_at
- UNIQUE(record_id, check_item_id) 防重

## 3. 状态机与判定流（CpsRoomCheckService）

```
PENDING --start/首照--> IN_PROGRESS --submit--> JUDGED（终态，锁定）
```

- **start**：房间存在且 enabled；若带 planTaskId：任务=INSPECT_CHECK + 本人（targetEmpNo 非空必须相等）+ 非 CANCELLED + reference_object_key 房间 CSV 含该房间码（CSV 空=不限）。幂等：同任务同房间存在未判定单直接复用。明细生成=findAll(APPLICABLE, enabled) 过滤 applicable_room_types（CSV 空=全适用，忽略大小写）。首建任务 PENDING→IN_PROGRESS。
- **uploadPhoto**：未判定 + 本人 + 明细归属校验；objectKey=`cps/room-check/{UUID}-{fileName}`；storage.put 后 updatePhoto（重拍覆盖）；首照 PENDING→IN_PROGRESS（记 started_at）。
- **submit**（PRD 24.2/24.3）：
  1. 全部明细须有照片，缺照片抛 `photo evidence missing for items (retake required, cannot bypass): {codes}`（证据不许绕过）。
  2. 同步调 judge；任一 TYPE_MISMATCH / UNJUDGEABLE ⇒ 抛 `retake required (not scored as unqualified, PRD 24.2.3): {code}:{OUTCOME}`，不锁定、不计不合格，补拍后重交。
  3. 全 PASS/FAIL ⇒ score=max(0, 100 − Σ FAIL 项 deduct_score)，judge_status=SUCCESS。
  4. 任务完成度：reference_object_key 列出的房间全部 JUDGED ⇒ 任务 COMPLETED（未列房间则任一 JUDGED 即完成）。
- **锁定**：JUDGED 后 uploadPhoto/submit 均 400（mapper 层 `record_status IN ('PENDING','IN_PROGRESS')` 守卫兜底）。
- 无 @Transactional：判定 HTTP 调用在事务外，避免长事务占连接；崩溃窗口由幂等重试覆盖（updateJudgeResult 覆盖写）。

## 4. C-04 判定客户端与降级（CpsRoomCheckJudgeClient）

- 配置 `cps.room-check.judge.*`（enabled / baseUrl=http://127.0.0.1:8010 / path=/api/agent/room-checks/judge / timeoutMs=60000；环境变量 CPS_ROOM_CHECK_*）。
- payload：`{submissionId, attempt, roomCode, roomName, checkEmpNo, items:[{itemId, itemCode, content, photoCategory, deductScore, configVersion, photoUrl, photoBase64}]}`（photoBase64 来自 RustFS 读回，Python 侧无 RustFS 依赖）。
- 响应解析：results[] 按 itemId（或 itemCode）匹配；judge=JUDGED ⇒ outcome=PASS|FAIL；**缺项按 UNJUDGEABLE**（须补拍，安全侧）；未知值归一 UNJUDGEABLE。
- **降级**（judge() 返回 null，不抛）：disabled / 连接失败 / 超时 / 非 2xx / 响应不可解析 / 无 results ⇒ 全部明细 judge_result=final_result=PENDING + reason 注明，单 judge_status=PENDING、score=NULL、recordStatus=JUDGED（状态推进但不计分）——不阻塞流程，J 线联调后补判定重跑。

## 5. 评分与排名（CpsRoomCheckRankingService，D-08）

- 周期：东八区（Asia/Shanghai）自然周，周一 00:00 起；weekStart 参数任意日期归一到所在周周一，空=上一完整自然周（与 §21.1 周报口径一致）。
- 归属：按 submitted_at 划入所在自然周，无跨周拆分（D-08）。
- 聚合（aggregateWeeklyScores 单 SQL）：`WHERE record_status='JUDGED' AND judge_status='SUCCESS' AND score IS NOT NULL` GROUP BY room，`SUM(score) AS totalScore, COUNT(*) AS checkCount, MIN(submitted_at) AS firstSubmittedAt`，`ORDER BY totalScore DESC, firstSubmittedAt ASC, roomCode ASC`。降级 PENDING 单不计入（未完成/证据无效不计分）。
- 排名：顺序号 1..n，同分不并列（D-08：按完成时间先后）。
- 房间周明细：逐次 记录时间/单次得分/点检人 + 仅 FAIL 项扣分明细（itemCode/content/deductScore/judgeReason）+ 周期总分与聚合位次。

## 6. 端点

**mobile `/api/cps/room-checks`**（鉴权沿用波次惯例：empNo 参数）
- `GET /tasks?empNo=&status=` 任务列表（含 roomCodes 与 judgedRoomCount 进度）
- `POST /start` body `{planTaskId?, roomId, empNo}`
- `GET /records/{id}?empNo=` 明细（photoUrl=publicObjectUrl）
- `POST /records/{id}/items/{itemId}/photo?empNo=&file=` multipart
- `POST /records/{id}/submit` body `{empNo}`

**admin `/api/cps/admin`**
- `GET /room-check-rankings?weekStart=`（空=上一完整自然周）
- `GET /room-check-rankings/rooms/{roomId}?weekStart=`
- `GET /room-check-records/{recordId}`（admin 追溯，无本人校验）

异常处理沿用现有局部 handler 惯例（IllegalArgumentException→400）。

## 7. 测试与验证

- 单测 25 个新增（总 176/0 绿，基线 151/0 无回归）：JudgeClient 6（两阶段解析/缺项 UNJUDGEABLE/500/malformed/空 results 降级、disabled 不发请求）、Service 10（快照过滤/幂等/范围拒绝/照片推进/缺照片阻断/降级 PENDING/计分 80 分/TYPE_MISMATCH 阻断/锁定后拒传/仅本人）、Ranking 4（默认上一完整周/归一周一/顺序号/FAIL-only 扣分明细）、Mapper 契约 5（列全集/D-08 聚合子句/锁定守卫/任务过滤）。
- 真实环境冒烟（MySQL 3306 + RustFS cps-rustfs:9000，应用 18092）：Flyway V20260929 应用成功；start→双照片上传（object_key 入库、photoUrl 可见）→submit 降级（judge_status=PENDING score=NULL recordStatus=JUDGED）；JUDGED 后再传照片 400；仅 PENDING 单不入榜；SQL 造 SUCCESS 双记录（90+100）后排名 SUM=190/rank=1/checkCount=2，明细仅 FAIL 项扣分。冒烟数据已清理。

## 8. 待确认（J 线联调项）

- C-04 实际字段/返回结构以 Python 侧为准（当前按契约文档实现：results[].itemId/judge/outcome/reason，itemId 匹配不到时回退 itemCode）。
- photoBase64 体积：当前全量 base64 内嵌（明细≤10 项，图片 MB 级可能撑大 payload）；如需改 URL-only 拉取或压缩，在 J 线定。
- 降级 PENDING 单的补判定入口（J 线）：预留重跑 submit 覆盖写，但需要"解锁重判"开关或定时任务策略，未在本波次实现。
- check_emp_name 工号占位：待员工主数据（波次2同款欠账）。
