# 波次6 Java侧设计：B5 移动端页面 + F3/F4 语音上传与集成

基线：`90cb0de`（波次5 A3/A4 之后）。本波次交付移动端三组新页面（点检执行 / 初审三态裁决 / 语音录入）+ Java 侧语音转写上传链路 + §31 旧页面精简。

## F3 后端：语音上传→RustFS→C-06 转写（同步）

### 端点
`POST /api/cps/speech/transcriptions`（multipart）
- `file`（必填，音频，≤20MB 可配）、`field`（必填，白名单 `reason|short_term|long_term`，D-03 三字段）
- `empNo`（缺省 `DEV_EMP`）、`submissionId`（缺省服务端派生 `speech-{empNo}-{yyyyMMddHHmmss}`）、`attempt`（缺省 1，同字段重录递增）

响应 `CpsSpeechTranscriptionResponse`：`status=TRANSCRIBED|SKIPPED|UNAVAILABLE`、`text`、`fallbackMessage`、`field/attempt/audioObjectKey/model/durationMs/submissionId`。

### 链路与配置（`cps.speech.*`）
`CpsSpeechService.transcribe`：校验（空文件 400、字段白名单 400、超限 400、attempt≥1 400）→ `RustFsStorageService.put(cps/speech/{yyyyMMdd}/{uuid}-{safeName})` → `CpsSpeechTranscribeClient.transcribe`（POST JSON `{submission_id, field, attempt, audio_object_key, audio_format, idempotency_key=speech-{submissionId}-{field}-{attempt}}`）。

- `enabled`（默认 true）/`base-url`（默认 `http://127.0.0.1:8000`，对齐 Python C-06）/`path`（`/api/v1/agent/speech-to-text`）/`timeout-ms`（**默认 240000，联调清单口径，可配**）/`max-audio-bytes`（20MB）。环境变量 `CPS_SPEECH_*` 可覆盖。
- 降级不抛错：client 失败/超时/未启用/未知 status → `status=UNAVAILABLE, text="", fallbackMessage="语音转写暂不可用，可点击重试或手动输入"`；SKIPPED → `text="" + fallbackMessage="语音转写已跳过：{skip_reason}"`；storage 故障 → IllegalStateException（500）。技术失败不缓存，attempt 递增即可重试。

### F4 集成
mobile `src/api/cps/speech.ts` `transcribeSpeech(file, field, submissionId?, attempt?)`（FormData 直发 / uni.uploadFile 回退）。

## B5① 点检执行（RoomCheckListView → RoomCheckExecuteView）

- 列表页：任务卡（状态/进度 judgedRoomCount/roomCodes）→ 点房间 chip：`GET /api/cps/admin/rooms?enabled=true` 解析 roomCode→roomId → `POST /start`（幂等，已有进行中记录直接返回）→ 跳执行页 `?recordId=`。
- 执行页：明细卡（内容/照片类别/扣分/judgeResult pill：PASS合格/FAIL不合格/TYPE_MISMATCH类型不符须重拍/UNJUDGEABLE无法判定须补拍/PENDING待判定+judgeReason）；拍照 `uni.chooseImage`，H5 回退隐藏 `input[capture]`（**运行时 createElement**，避免模板 input 被 uni 编译成 uni-textarea/uni-input 依赖 uni 运行时）；上传后实时刷新。
- 降级态（判定服务不可用）：提交返回 judgeStatus=PENDING、score=null → hero 显示「分数待判定（降级）」+「判定服务暂不可用（J 线补判定）」通知，recordStatus=JUDGED 锁定拍照与提交。
- 提交拦截提示：缺照「还有 N 项未拍照，不可跳过」；TYPE_MISMATCH/UNJUDGEABLE「N 项须重拍/补拍后方可提交」（服务端 400 文案逐项回显，前端重拉 record 展示）。

## B5② 初审三态/接管/裁决（InitialReviewView）

`GET /api/cps/issues/{id}/initial-review` reviewerView 驱动，8 态：
- **running**：倒计时「约 Ns 后达到接管阈值（600s）」；**timeout_open**：「超过 10 分钟接管阈值，可人工接管」；**failed**：errorCode +「AI 明确失败可立即接管」（§28.4 界面必须区分三态）。
- 接管：can_take_over 时显示原因 textarea（必填）→ `POST /initial-review/take-over` → 重载显示「已人工接管」+接管人+原因；迟到 AI 结果仅「迟到留痕」标记展示、不覆盖裁决（late_result）。
- AI 意见卡：overall（PASS/PARTIAL/PROBLEM）+逐项 verdict/reason/problemFragment/textLength/punctuationCount/ratioOk（§29 检查项）。整改提交快照卡：三字段+责任员工+提交人。
- 裁决（completed/late_result/taken_over 且未裁决）：reason 必填，APPROVE/REJECT 两按钮，relationPreview「通过=与 AI 同向；退回=与 AI 反向」；留痕卡 decision+aiRelation（WITH_AI 绿/AGAINST_AI 红）。入口：IssueListView「初审裁决」按钮默认跳最近一条 PENDING_AI_REVIEW/PENDING_REVIEWER_CONFIG 问题。

## B5③ 语音录入（VoiceInput 组件）

`field/label/modelValue/submissionId` props + `update:modelValue`。状态机 idle→recording→uploading→done/error/unsupported。按住说话（pointerdown/up/leave/cancel）→ MediaRecorder 录 webm → `transcribeSpeech(file, field, submissionId, attempt)`（attempt 递增幂等）→ TRANSCRIBED 回填文本 +「已回填，请编辑确认后提交（语音不作点检证据）」；SKIPPED/失败显示降级文案+重试按钮，**不清空已手工输入内容**（§20.2 失败允许重试或手工输入）。环境不支持录音 → 「当前环境不支持录音，请手工输入」。挂载于 IssueDetailView V2 整改块三字段（原因分析/短期措施/长期措施）+ 责任员工 picker + 整改照片（随提交触发 AI 初审）。

## §31 移动端精简（隐藏不删除，?legacy=1 全量可达）

IssueDetailView 默认态：
| 项 | 处理 |
|---|---|
| 基础信息 | 仅留稽查人姓名+稽查时间；处理/责任 6 字段与关闭时间 legacy 显示 |
| 复核照片卡片 | 整卡隐藏（legacy 显示） |
| AI 分类/人工分类 | 隐藏（legacy 显示） |
| AI 原因+AI 措施 | 合并为「AI 整改建议」单条展示（reasonSuggestion；measureSuggestion 拼接） |
| 人工原因/人工措施 | 隐藏（legacy 显示） |
| 底部节点卡片（处理轨迹） | 隐藏（legacy 显示） |

问题现场照片保留。历史数据经 URL `?legacy=1` 完整可达。

## vitest 环境修复（基线预存在问题）

基线 `90cb0de` vitest 无法启动：`Failed to resolve vue/compiler-sfc`。根因：`@dcloudio/uni-cli-shared` alias.js 在 VITEST 下用 module-alias 全局 patch `Module._resolveFilename`（vue→uni-h5-vue），泄漏进 `@vitejs/plugin-vue` 的 createRequire 解析。修复（`mobile/vite.config.ts`，仅 VITEST 分支生效）：module-alias 反注册真实 vue 路径（目录/package.json/compiler-sfc 文件）+ `cps-pin-real-vue-for-vitest` enforce:pre 插件排在 uni() 之前精确 pin 'vue'/'vue/package.json'/'vue/compiler-sfc' → 消除双 vue 实例（test-utils 真实 vue 与 SSR 管道 uni-h5-vue 响应式分裂）。附带 `src/test/setup.ts` 补 `__uniConfig` 最小桩。uni build 路径不受影响。

## 测试与验证

- 后端 `mvn test`：**230/0 全绿**（基线 212 + 新增 18：CpsSpeechServiceTest 10 + CpsSpeechTranscribeClientTest 8）。
- mobile `pnpm build`（vue-tsc -b + uni build -p h5）：**通过**。
- mobile vitest：32 绿/5 红，**5 红全部为基线遗留**（基线 vitest 从未可运行）：request.spec falls-back-to-mock 1、LocationSelector 1、CategorySelector 1（uni-button jsdom 点击不达 Vue handler）、IssueDetailView 2（旧 spec 期望'提交问题'/'审核关闭'文案与 §31 隐藏流转卡冲突）。本波次新增 16 测试全绿：RoomCheckListView 3、RoomCheckExecuteView 4、InitialReviewView 5、VoiceInput 4、IssueDetailViewRectifyV2 2（16 项中 IssueListView 2 为既有改造：hero 三入口断言更新）。

## 遗留项

1. vitest 5 红为波次2-5 旧债，建议后续波次统一修（uni-button 点击管道需完整 uni 应用运行时或改 van-button）。
2. 旧 IssueDetailView.spec 2 例与 §31 精简冲突，需按新交互重写（现由 IssueDetailViewRectifyV2.spec 覆盖新行为）。
3. 点检判定降级后的 J 线补判定（AI 判定服务接入后回填 PENDING 项）依赖联调环境。
4. 初审裁决入口暂取列表第一条待裁决问题，后续可按审核人维度过滤。
