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

旧 CPS 与 Agent 框架可以在受信任网络内直接调用，不需要 JWT。Agent 框架通过 `config/cps.yaml` 的 `internal_trust: true` 和 `trusted_networks` 控制允许访问的来源网段；未匹配的外部访问仍然需要 JWT。

固定 IP 部署时，把 Agent 服务所在机器的网段写入 `trusted_networks`。Kubernetes 部署时写入旧 CPS Pod 可能使用的 Pod CIDR，并使用 NetworkPolicy 只允许旧 CPS 命名空间访问 Agent Service；不要填写 Pod 单个 IP，因为 Pod 重建后 IP 会变化。

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
