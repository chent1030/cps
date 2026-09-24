# 波次14 Mobile 侧 — B3 初版 + B5 简化

> 子任务：B3 移动端初版（RoomCheckPhotoUploadView / ScoreRankingView）+ B5 移动端简化（v-if role 控制）。
> 基线：cps `993abac`（main）；B4/B8 后端独立 commit `e9194da`、B3 mobile `0433ac1`。

## 1. B3 移动端初版

### 1.1 新增路由（mobile/src/pages.json）

| 路由 | 视图 | 主要能力 |
| --- | --- | --- |
| `views/cps/RoomCheckPhotoUploadView` | `RoomCheckPhotoUploadView.vue` | van-uploader 拍照上传点检照片，逐张调 `/api/cps/room-checks/.../photo`（`uni.uploadFile` + FormData） |
| `views/cps/ScoreRankingView` | `ScoreRankingView.vue` | 自然周口径（周一 00:00 - 周日 23:59）调 `/api/cps/admin/scores/weekly` 拉本区域排名 |

### 1.2 关键 API 封装（mobile/src/api/cps/score.ts）

```ts
listWeeklyScores(weekStartDate, regionSupervisorId?, page?, pageSize?) → CpsWeeklyScorePage
```

类型 `CpsWeeklyScoreItem { id, weekStartDate, empNo, empName, totalScore, rank }` + `CpsWeeklyScoreLine { itemId, scoreDelta, reason }`。

### 1.3 ScoreRankingView 自然周口径

- `naturalWeekStart(date)` 工具：取 `getDay()` 计算本周一（与 `CpsWeeklyScoreService.naturalWeekStart` 口径一致，周一 00:00 Asia/Shanghai 为界）。
- 排序：`rank = offset + 1 + i`。
- top3 高亮边框 + 🥇🥈🥉；分数按 ≥85/≥60 切绿/橙/红。

### 1.4 RoomCheckPhotoUploadView

- `<van-uploader :max-count="6" :accept="'image/*'" :capture="'environment'" :after-read="onAfterRead" />`。
- 本地预览先放 `done`，提交时逐张走 `uploadRoomCheckPhoto` → `uni.uploadFile`。
- 错误走 `<van-notice-bar>`；提交完成触发 `emit('submitted', { recordId, itemId })`；返回通过 `uni.navigateBack` / `history.back()` 兜底。

## 2. B5 移动端简化（PRD §31 隐藏不删）

### 2.1 角色门控策略

- **不接真鉴权**。`userStore.ts` 仅维护 `current: Ref<CpsUserInfo>`（默认 `role: 'inspector'`），登录后由前端 mock 设置。
- 6 页全部按 `isAdmin` 控制：
  - `IssueListView.vue`：hero 「新建」/「初审裁决」/「评分排名」三个按钮加 `v-if="isAdmin"`，「点检任务」常驻。
  - `IssueCreateView.vue`：`<main v-if="!isAdmin">越权提示</main> <main v-else>原表单</main>`；onMounted + watch 提前 return 避免对 mock 不可用接口发起调用。
  - `InitialReviewView.vue`：同 `IssueCreateView` 的双分支结构。
- 真正鉴权仍由 `cps/backend` 的 `CpsPermissionInterceptor` 处理（admin / management / inspector），mobile 仅做 UI 隐藏。

### 2.2 userStore 接口

```ts
getCurrentUser(): CpsUserInfo
setCurrentUser(user: CpsUserInfo)
setCurrentRole(role: 'admin' | 'inspector')
currentUser: ComputedRef<CpsUserInfo>
currentRole: ComputedRef<CpsUserInfo['role']>
hasRole(role): boolean
```

模块级 `ref`（非 let）确保 `isAdmin` 是响应式 computed，UI 切换角色即重新渲染。

### 2.3 测试覆盖（`RoleGate.spec.ts` 4 测）

| 用例 | 角色 | 期望 |
| --- | --- | --- |
| IssueListView admin-only 入口 | inspector | 只见「点检任务」，不见「新建/初审裁决/评分排名」 |
| IssueListView admin 全部入口 | admin | 四按钮全见 |
| IssueCreateView 越权提示 | inspector | 仅渲染「仅审核员可创建」，`<form>` 不存在 |
| InitialReviewView 越权提示 | inspector | 仅渲染「仅审核员可访问」，`.cps-review-state` 不存在 |

`@dcloudio/uni-app` mock 屏蔽 `onLoad` 以避免 `injectHook` 报错；3 个原 spec（IssueListView / IssueCreateView / InitialReviewView）的 `beforeEach` 中调用 `setCurrentRole('admin')` 保证 admin 路径下原断言不变。

### 2.4 已知 h5 跨域限制

- uni-app h5 平台下，`uni.uploadFile` 走 `XMLHttpRequest`，需 `cps/backend` 开启 CORS 或在 `vite.config.ts` 配置前端 proxy（`/api/cps` → 后端 host）。
- 当前任务不要求 h5 build（m1 mac 慢、h5 平台绑定），故仅 typecheck 验证。

## 3. 验证

- `pnpm typecheck`（vue-tsc -b）：绿。
- `pnpm test`（vitest）：`36 passed / 5 failed / 41 total`。
  - 5 失败 = 基线预存（request mock fallback / CategorySelector radios / LocationSelector area person / IssueDetailView status labels / IssueDetailView image preview），与 `9027e54` 一致，无回归。
  - 新增 `RoleGate` 4 测全绿。
- `git log`：`e9194da` B4/B8 后端 → `0433ac1` B3 mobile → `<本 commit>` B5 mobile simplification。