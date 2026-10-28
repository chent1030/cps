# CPS 与 Agent 框架接入

旧 CPS 继续作为员工端业务系统，负责问题单、主数据、附件、责任人流转和整改闭环；
`basic-project` 的 FastAPI 服务负责 DeepAgents/CPS Agent 的动态编排、人工确认、运行监控和长期记忆。

## 配置

编辑 `src/main/resources/application.yml`：

```yaml
cps:
  agent-framework:
    enabled: true
    base-url: http://127.0.0.1:8000/api/v1
    tenant-id: local-factory
    timeout-ms: 10000
```

旧 CPS 与 Agent 框架之间不再有鉴权：双方只要把对方的地址填进配置即可（CPS 侧 `cps.agent-framework.base-url`，Agent 侧 `config/cps.yaml` 的回调地址），Java 调 Agent 不再带 Bearer 令牌，Agent 回调 `/api/cps/initial-review/callback` 也不再校验来源 IP 或共享令牌。可信边界交给部署网络，建议用内网或网关把回调入口限制在 Agent 主机。

附件存储使用 MinIO：上传接口直接写入并返回可访问的绝对 URL（`http://<minio>/<bucket>/<object>`），C-01、C-04 请求里的图片字段同样下发该 URL（不再下发对象键）。

## 调用关系

员工在旧 CPS 中提交问题后，旧 CPS 事务内创建问题记录并调用：

1. `POST /cps/inspections` 创建框架巡检，幂等键为 `legacy-cps-issue-{issueId}`。
2. `POST /cps/inspections/{id}/start` 启动主 Agent。
3. 返回的框架巡检 ID 写入 `cps_issue.agent_inspection_id`。

旧 CPS 的员工流程不暴露 Agent、模型或 JSON。主管在当前框架的人工确认页面确认主 Agent 的下一步；主 Agent 仍然动态选择后续 Agent，人工可以改派、跳过、退回或手工录入结果。

## 数据库

Flyway 迁移 `V20260911__cps_agent_framework.sql` 会为 `cps_issue` 增加 `agent_inspection_id` 唯一关联字段。

## 启动顺序

先启动 `basic-project` 的 FastAPI 服务，再启动旧 CPS backend。Agent 框架不可用时可将 `enabled` 设为 `false`，旧 CPS 原有流程仍可独立运行。
