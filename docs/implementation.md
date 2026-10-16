# 实现说明与真实集成边界

## 当前实现

11 个 Maven 子模块构成一个 Spring Boot 应用。app 的 ApiController 作为跨领域 HTTP 装配入口；AuthController 位于 security。Controller 只调用服务，不直接访问 Mapper。领域服务通过接口或明确应用服务交互，投影 Map 用于 VO，未直接返回 MyBatis Entity。common 只放通用响应、异常、加密、JSON 与 SQL 基类，无订单或审批业务。

OrderQueryService 使用 MyBatis-Plus CustomerOrderMapper 的强类型实体查询；跨表查询与任务领取由各领域 SQL Mapper 负责。显式 SQL 是为了审查锁、CAS 和索引路径，并不绕过领域服务。当前未为所有表机械生成 CRUD；不存在无实现的关键方法占位。

AgentOrchestrator 调用 LlmClient，由 RoutedLlmClient 选择环境配置或团队路由，真实传输适配器 SpringAiLlmClient 使用 Spring AI OpenAiChatModel、ToolCallback JSON Schema、internalToolExecutionEnabled(false)，由服务端显式分发每个只读调用。检查点保存精确模型响应、callId 与轮次；工具历史保存参数/结果；加密上下文保存可恢复对话。结构化依据展示，不暴露模型隐藏思维链。

WorkflowEngine 每个任务只推进一个模型轮次，后台调度不依赖用户浏览器。任务使用 MySQL SKIP LOCKED、120秒租约与 fencing_token；网络调用后事务回写重验租约。模型默认25秒（供应商可配置5–45秒）、物流10秒超时，工具最多20次/工作流、5次/轮，模型8轮。只读故障持久化指数退避，最多3次重试。

ToolExecutionService 只从有效审批快照执行四种白名单写动作；不能从 REST 任意调用。订单 version、资格、金额、客户与团队在执行时重验。订单资源互斥持续到明确结果，UNKNOWN 不释放。外部结果核对成功后幂等记账，并独立生成通知审批。

ApprovalService 的审批行锁、状态 CAS、version 与唯一 approval_record 防止重复审批。数据库触发器禁止修改审批证据和建议快照。高风险退款、取消和地址修改要求主管；模型不能提供审批主体。当前每项操作单人审批；双人审批是未来增强。

## 模式切换

默认 `LLM_MODE=fake`：FakeLlmClient 只对 demo-query、demo-refund 精确测试夹具返回确定性原生调用结构，不解析自然语言，不冒充真实 AI。其他邮件转人工。

`LLM_MODE=real`：配置 LLM_BASE_URL（不含 /v1/chat/completions）、LLM_MODEL、LLM_API_KEY。支持 OpenAI-compatible 原生工具调用；模型必须支持工具调用及结构化 JSON。读取邮件与工具结果都视为不可信输入；模型发出 WRITE/未知工具会被拒绝。

默认 `INTEGRATION_MODE=mock`：MockExternalGateway 在 MySQL 记录模拟物流、退款与邮件结果，持久化唯一幂等键。**演示中的成功不表示真实退款或真实邮件已发送。**

`INTEGRATION_MODE=real`：SMTP 用 Spring Mail；退款/取消/地址变更经 RealExternalGateway 调用配置的业务网关。网关约定 POST /operations/{action}、Idempotency-Key header、GET /operations/{key}；返回 status、requestId、data。状态只认可 SUCCESS/FAILED，其他一律 UNKNOWN。需网关明确支持幂等和结果查询；SMTP 超时无通用核对 API，保持人工接管，不自动重发。

IMAP_ENABLED=true 以 IMAPS 接收纯文本、限制大小/层数并按 Message-ID 去重；附件与 HTML 不执行。启用前在数据库建立对应 mail_account，设置 IMAP_ACCOUNT_ID、HOST、PORT 与 MAIL_USERNAME/PASSWORD。IDENTITY_VERIFIER_URL 指向可信邮件网关的验证服务，POST accountId/messageId/sender，返回 {verified:true} 必须代表该邮件已在可信网关验证身份；未配置或失败均不自动查询客户订单。不能仅根据 From 或正文认定身份。AUTO_ANALYZE_ENABLED=true 时自动领取已验证邮件建立工作流。

当前已实现用户创建、停用与角色分配；未实现附件存储、用户物理删除、任意角色定义、企业 SSO、双人审批和真实供应商支付 SDK；数据表和边界为扩展保留。账号启停及已有角色分配已实现。不能将这些增强或未配置的外部服务称为生产验收通过。

## 依赖依据

Java 21 / Spring Boot 3.5.16 / Spring AI 1.1.8 的兼容选择依据：[Spring Boot 系统要求](https://docs.spring.io/spring-boot/3.5/system-requirements.html)、[Spring AI 项目兼容矩阵](https://github.com/spring-projects/spring-ai)、[1.1 工具调用文档](https://docs.spring.io/spring-ai/reference/1.1/api/tools.html)。前端与 Maven 版本在依赖清单固定，npm 使用 package-lock.json。

主管人工核对入口校验团队、执行版本与UNKNOWN/DISPATCHING状态，并记录凭据摘要；它仅记录经人工核实的远端结果，不重新调用写网关。成功后恢复本地记账和独立通知审批，失败则释放订单互斥等待人工修订。误报远端结果仍是业务风险，凭据必须来自权威系统。安全取消入口撤销待审批和未执行任务；存在未知操作时拒绝取消。

## 模型与登录设置

Flyway V6 增加可撤销登录刷新会话；V7 增加供应商、模型、路由、团队额度、月度账户、调用账本6张表，合计36表。管理员权限 `models:manage` 由迁移赋予ADMIN，服务端按团队校验资源归属并对配置版本做乐观锁校验。

RoutedLlmClient 先解析团队配置，未启用时沿用环境模型；首轮 INTENT_ANALYSIS、后续 TOOL_REASONING，匹配不到则 DEFAULT，相同用途按优先级和ID稳定排序。停用或未设置密钥的供应商不参与路由。预览只解析，不发起收费调用；模型异常不自动切换供应商。SpringAiLlmClient 为每次请求构造工具调用模型且只尝试一次。

额度按 Asia/Shanghai 的 YearMonth 分账，不需要重置历史计数或定时任务。预留与结算在短事务中锁定团队设置和月度账户，网络调用在事务外执行。账本以团队/工作流/轮次唯一约束避免重复计费；已完成响应加密缓存，可恢复工作流检查点。未知结果不自动释放额度，管理员须提供账单依据对账，写入审计。按上界扣除但无响应的轮次保持人工处理，不盲目再次调用。

输入上限和预留采用 UTF-8 序列化字节数加开销的保守估算，包含系统提示、邮件、历史与工具定义，不是供应商精确分词器；可能提前拒绝接近上限的请求。供应商实际总用量优先，若供应商报告超出预留则如实计账并阻止后续超额请求。缺少usage时按预留上界计入并标记估算。

刷新令牌为48字节随机数，数据库存SHA256摘要，轮换后10秒允许并发标签页读取加密后继令牌；超窗重放撤销整族。记住登录最长30天，普通登录最长12小时；JWT为1小时并绑定会话族，退出立即撤销。Cookie使用HttpOnly、SameSite=Strict和/api/auth路径；刷新/退出需要自定义同源请求头，存在Origin时检查协议/主机/端口。默认本地HTTP，生产HTTPS开启AUTH_COOKIE_SECURE。
