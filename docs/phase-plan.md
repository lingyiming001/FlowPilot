# 阶段交付状态

用户后续已授权完成全项目并自动部署启动，覆盖原逐阶段确认节奏。当前按整体业务闭环继续交付。

| 阶段 | 已实现内容 | 验证边界 |
| --- | --- | --- |
| 1 架构 | 单体边界、模块依赖、业务/安全/恢复设计 | 设计与当前实现差异见 implementation.md |
| 2 数据与接口 | 需求28表+轮次检查点、Flyway V1-V5、ER、REST与OpenAPI | 实际MySQL迁移已验证，业务验收记录持续更新 |
| 3 后端工程 | Maven11模块、Java21、JWT/RBAC、异常、Mapper、服务和API | 最新版本以clean verify结果为准 |
| 4 Agent | Spring AI原生Tool Calling、统一注册与显式只读分发、上下文和检查点 | Fake夹具用于自动化；真实模型需配置密钥并验收 |
| 5 工作流/审批 | CAS状态机、任务租约、审批快照、四种写执行、核对、恢复、审计 | 并发、未知结果、重启测试进行实际验证 |
| 6 前端 | 所有主要页面、真实API、审批/修订、SSE、用户管理 | TypeScript与生产构建已通过；浏览器验收待完成 |
| 7 部署/测试 | Compose、镜像、Nginx、随机凭据、脚本、指标/告警、自动化测试 | 完整部署健康与最终测试结果见testing/acceptance.md |

## 交付边界

真实模型、IMAPS/SMTP及退款网关必须提供实际服务与凭据，不能将Mock结果当作真实集成结果。附件存储、SSO、任意角色定义、双人审批和供应商专用支付SDK未包含在当前MVP，详见implementation.md。源码、配置和测试文件存在不等于生产验收完成。

八类图统一保留在 `docs/diagrams/`，名称与实现组件保持对应；全部DDL位于Flyway迁移目录，docs/database/schema.sql为审阅合并副本。
