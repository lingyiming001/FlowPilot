# REST API 契约

基础路径 `/api`，除登录、刷新会话、退出外使用 `Authorization: Bearer <JWT>`；OpenAPI 与 Swagger UI 可公开读取契约，但业务请求仍需 JWT。默认 Token 1小时，权限与账号启用状态每次从数据库重验。

统一成功：`{"success":true,"data":{},"code":"OK","message":""}`。失败 `success=false`，包含 code/message，禁止回传堆栈。列表 data 为数组；当前邮件/订单/工作流/审批最多100条，审计通过 page 参数50条分页。

| 方法与路径 | 请求 / 返回 | 权限 |
| --- | --- | --- |
| POST /auth/login | username/password/rememberMe → token,user,expiresIn + HttpOnly Cookie | 公开；Redis 限流 |
| POST /auth/refresh | Cookie轮换 → token,user,expiresIn；需要 X-FlowPilot-Session: 1，存在Origin时必须同源 | 刷新Cookie |
| POST /auth/logout | 撤销当前Cookie会话族并清除Cookie；同上请求头要求 | 刷新Cookie |
| GET /model-settings | 供应商（仅密钥状态）、模型、路由、额度、近12月用量、最近100次调用 | models:manage |
| PUT /model-settings | routingEnabled,monthlyTokenLimit,workflowTokenLimit,version | models:manage |
| POST /model-settings/providers、PUT /model-settings/providers/{id} | name,type,baseUrl,chatPath,apiKey,clearApiKey,timeoutSeconds,enabled,version；空密钥保留原值 | models:manage |
| POST /model-settings/models、PUT /model-settings/models/{id} | providerId,name,modelName,temperature,contextWindow,maxInputTokens,maxOutputTokens,outputParameter,enabled,version | models:manage |
| POST /model-settings/routes、PUT /model-settings/routes/{id} | routeKey,modelId,priority,enabled,version | models:manage |
| GET /model-settings/preview?routeKey=DEFAULT | 仅解析路由，不调用模型、不回显密钥 | models:manage |
| POST /model-settings/ledger/{id}/reconcile | release,reason；核对不确定调用预留，写入审计 | models:manage |
| GET /auth/me | 当前 Actor 与角色权限 | 登录 |
| GET /system/info | 模式、版本 | 登录 |
| GET /dashboard | 数量、状态分布、近期工作流 | workflows:read |
| GET /mails | 邮件列表，含 workflow_id | mails:read |
| GET /mails/{id} | 邮件详情（正文仅授权用户可见） | mails:read |
| POST /mails/{id}/analyze | workflowId；重复提交返回同一实例 | mails:analyze |
| GET /orders、/orders/{id} | 订单投影视图，不含地址明文 | orders:read |
| GET /workflows | 状态、主题、意图、版本 | workflows:read |
| GET /workflows/{id} | proposals/events/toolCalls/executions/approvals/tasks | workflows:read |
| GET /workflows/{id}/tool-calls | 脱敏工具请求参数与结果 | workflows:read |
| GET /workflows/{id}/events | SSE；Last-Event-ID 重放、周期心跳 | workflows:read |
| POST /workflows/{id}/retry | 只读重试提前调度，未知写操作只安排核对 | workflows:retry |
| POST /workflows/{id}/revise | actionType,orderId,parameters,reason → 新快照、新审批 | mails:analyze；仅安全人工接管状态 |
| POST /workflows/{id}/cancel | expectedVersion,comment；无未决外部操作时终止并撤销待审批 | workflows:retry |
| POST /workflows/{id}/executions/{executionId}/reconcile | expectedVersion,outcome,evidence；记录核实结果，不重发 | SUPERVISOR + workflows:retry |
| GET /proposals/{id} | 建议所属工作流，团队范围校验 | workflows:read |
| POST /users | username,password,displayName,roles；自动归属当前团队 | users:manage |
| GET /approvals | 范围内审批，含 version、有效期与摘要 | workflows:read |
| POST /approvals/{id}/approve | expectedVersion,comment | approvals:decide + required_role |
| POST /approvals/{id}/reject | expectedVersion,comment | 同上 |
| POST /approvals/{id}/return | expectedVersion,comment | 同上；进入人工修订 |
| GET /audit-logs?page=1 | 范围内追加审计 | audit:read |
| GET /failed-tasks | 异常与解决状态 | workflows:read |
| GET /users、/roles | 用户、角色与权限 | users:manage |
| PUT /users/{id} | enabled,roles | users:manage；禁止修改自身 |

## 请求示例

```http
POST /api/auth/login
Content-Type: application/json

{"username":"supervisor","password":"从本地环境读取，不提交到版本库"}
```

```http
POST /api/approvals/<approvalId>/approve
Authorization: Bearer <JWT>
Content-Type: application/json

{"expectedVersion":0,"comment":"已核对订单状态及实付金额，同意此退款操作"}
```

客户端不能提交执行器名称、actorId、teamId 或替换审批参数。服务从请求 ID 加载快照，执行前再次核验摘要、期限、账号权限、客户归属及订单版本。

401 未认证；403 角色或工具权限拒绝；404 资源不存在或不在团队；409 已审批、版本变化、过期或业务状态冲突；400 参数/schema/业务输入错误；429 登录频率过高；503 依赖暂不可用；500 内部未知错误。错误不含原始邮件、密钥或第三方完整响应。

SSE 是持续文本事件流，25秒连接周期后重连；认证放 header，禁止 token URL。事件 id 为持久化迁移日志 ID，按持久化顺序恢复。前端收到事件重新拉取权威详情，禁用自身状态机写入。

最终运行契约可在 `/v3/api-docs` 获取，Swagger UI 在后端 `/swagger-ui.html`；生产 Nginx 默认只代理 /api，文档供内部开发查看。部署后通过脚本导出 `docs/api/openapi.json`。
