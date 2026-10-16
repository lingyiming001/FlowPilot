# FlowPilot · 企业智能邮件与订单处理系统

Enterprise MailOps AI Agent：React + Spring Boot 模块化单体，串联客户来信、受控工具查询、AI 建议、人工审批、业务执行与审计。

## 一键部署

```sh
bash deploy/scripts/start.sh
```

需要 Java21、Maven、Node22+、npm、Python3 和已运行的 Docker。脚本执行构建/测试、生成本地随机凭据、启动 Compose 并等待健康检查。访问 `http://localhost:18080`。

本地账号 `admin`、`supervisor`、`operator`、`auditor`，随机密码在 **deploy/env/.env 的 DEMO_PASSWORD**。账号仅在 DEMO_ENABLED=true 初始化，admin 在演示环境显式拥有主管角色。环境文件已被 Git 忽略，不上传凭据。

```sh
bash deploy/scripts/health-check.sh
bash deploy/scripts/stop.sh
```

停止不删除数据卷，重启保留审批和工作流状态。详细部署、开发模式、真实服务切换、监控和排障见 [部署说明](deploy/README.md)。

## 项目结构

| 路径 | 内容 |
| --- | --- |
| `flowpilot/` | Java21 / Spring Boot3.5.16 / Spring AI1.1.8，11个 Maven 子模块，一个部署应用 |
| `flowpilot-web/` | React / TypeScript / Ant Design / Query / Zustand / SSE 工作台 |
| `deploy/` | Docker Compose、Nginx、环境模板、启动/健康脚本与监控 |
| `docs/architecture/` | 架构、安全边界、模块职责与规划目录 |
| `docs/database/` | 核心业务、恢复检查点、登录会话与模型额度（36表）SQL、索引及事务说明 |
| `docs/api/` | REST契约与运行时OpenAPI |
| `docs/diagrams/` | 八类 Mermaid 架构、时序、状态、ER、恢复与部署图 |
| `docs/implementation.md` | 实际实现与外部集成边界 |

## 安全业务闭环

模型只获知五个只读工具，不能自行执行退款、取消、地址修改或客户邮件发送。后端将建议固化为加密快照、绑定摘要与有效期，由指定角色审核；执行时重新检查角色、客户归属、订单版本和金额。退款成功后的客户通知创建**另一份独立审批**。

MySQL 保存上下文、模型轮次、工具callId与结果、任务租约、状态迁移、审批和幂等执行记录。外部结果未知时先核对，禁止盲目重复退款；SMTP 无法确定投递结果时转人工。

## 模式与验证边界

默认 **Fake LLM + Mock 外部业务适配**，页面显著标注。Fake仅用于两封预置邮件的确定性工具链演示，不以文本匹配冒充真实模型。真实 Spring AI 模型、IMAPS、SMTP与业务网关均有配置适配；需要实际凭据和可达服务才能进行真实集成验收。Mock退款/邮件成功不代表真实支付或发送成功。

当前支持用户创建、启停及预定义角色分配；自定义角色、双人审批、附件存储、SSO、支付供应商专用SDK和密钥轮换迁移属于后续增强。详细边界见 [实现说明](docs/implementation.md)。

## 构建与测试

```sh
# 选择 Java21，并确保 Docker 可用
mvn -f flowpilot/pom.xml clean verify
npm --prefix flowpilot-web ci
npm --prefix flowpilot-web run build
npm --prefix flowpilot-web test
```

后端测试覆盖状态、审批策略、加密、MySQL持久化、双重审批、并发审批、幂等、越权、注入工具拒绝、未知结果核对与完整应用重启。测试是否实际通过及部署结果以 [验收记录](docs/testing/acceptance.md) 为准，不能用测试源码存在代替通过证据。

## 记住登录与模型管理

登录页勾选“记住我（30 天）”可在重新打开浏览器后恢复登录；服务端使用可撤销、轮换的 HttpOnly Cookie，不保存密码。未勾选时使用浏览器会话 Cookie，最长12小时；访问令牌每小时自动更新。退出会撤销当前登录会话。

管理员进入“模型与用量”：先新增 OpenAI 兼容供应商（Base URL、接口路径、API Key），再添加模型与输入/输出上限，配置意图分析、工具推理或默认路由，最后在“额度与用量”启用配置路由。供应商、模型和路由可编辑或停用；API Key 加密保存且不回显。模型必须支持原生工具调用。

团队额度按北京时间自然月重置，默认每月100万 Token、每工作流10万 Token，可修改。运行前按保守输入字节上界与最大输出预留额度；供应商返回用量后结算，缺少用量按预留量计入。调用结果不明时保留额度，管理员核对账单后可对账释放或扣除；释放后可在异常任务中重试。历史用量保留；Fake演示不消耗额度。未启用配置路由时沿用部署环境模型，真实环境模型同样受额度控制。

生产使用 HTTPS 时设置 `AUTH_COOKIE_SECURE=true`，并配置反向代理的可信转发头。
