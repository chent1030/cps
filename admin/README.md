# CPS 巡检管理端

React 19 + TypeScript + Tailwind CSS 构建的 CPS 巡检管理前端。应用不含样例数据和 Mock 服务，所有列表、指标和写入操作都请求实际的 CPS 后端。

## 功能

- 巡检概览: 在途、节点待办、超期和本月闭环的真实聚合指标。
- 问题管理: 全量问题的状态、工厂、关键字筛选、服务端分页和 CSV 导出。
- 问题分类: 新增与启停一级分类，支持检索、服务端分页和 CSV 导出。
- 区域人员: 维护区域范围和员工匹配规则，支持多条件筛选、分页和 CSV 导出。
- 案例知识库: 通过真实分类级联新增、查看和启停案例，支持检索、分页和 CSV 导出。
- 接口失败、无数据和加载状态均有独立呈现，不会降级为伪造数据。

## 本地开发

```bash
cd /Users/csai/project/cps/admin
cp .env.example .env.local
npm install
npm run dev
```

开发服务器会把 `/api` 代理到 `VITE_BACKEND_URL`。默认目标为本地 CPS Spring Boot 服务。
管理端登录校验独立的 `cps_admin_user` 管理员表，区域人员配置不参与登录认证。部署后需先在管理员表中维护可登录的工号、姓名和启用状态。

## 生产部署

```bash
npm ci
npm run build
```

将 `dist/` 发布到 Nginx、网关或静态托管服务。建议让静态站点与后端 API 处于同一站点，并由反向代理转发 `/api`。这使应用可以使用 `HttpOnly; Secure; SameSite=Lax` 的企业 SSO 会话 Cookie，前端不会保存访问令牌。

部署时配置：

- `VITE_API_BASE_URL`: API 前缀，通常为 `/api`。
- `VITE_SSO_LOGIN_URL`: API 返回 401 时跳转的企业 SSO 登录入口。

后端须发布以下管理接口：

- `GET /api/cps/admin/overview`
- `GET /api/cps/admin/issues`
- `GET /api/cps/admin/issues/export`
- `/api/cps/admin/master/*`
- `/api/cps/admin/knowledge/*`

请在网关或 Spring Security 层限制 `/api/cps/admin/**` 仅供具备 CPS 管理权限的企业身份访问，并为写请求启用 CSRF 防护或同源校验。
