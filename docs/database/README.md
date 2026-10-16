# 数据库设计与迁移

MySQL 8.4 LTS 是唯一业务状态源。所有 DATETIME 约定 UTC，浏览器转换为本地时间。金额使用 DECIMAL(12,2)。业务 ID 使用 UUID，演示订单保留 ORD 编号。

- `schema.sql` 包含全量建表和增量 DDL，供审查；应用只执行 Flyway，不重复导入此文件。
- `flowpilot/app/src/main/resources/db/migration/` 为迁移权威源：V1 建立需求 28 表；V2 订单执行互斥；V3 增加第 29 张模型轮次检查点表；V4 使用数据库触发器保护审计、审批记录与建议快照不可修改、不可删除。
- ER 源文件：`docs/diagrams/database-er.mmd`（全部实际表）。部分跨领域关系由应用校验与事务保证，图中 references 不全部代表物理 FK。
- 开发数据由 `DemoDataInitializer` 注入：只在 DEMO_ENABLED=true 生效。凭据由启动脚本生成，避免 SQL 固定演示密码。测试中显式配置测试凭据，不作为生产配置。

## 幂等与索引

邮件账户+message_id 唯一，缺失 ID 时使用原文摘要；一个 mail 对应一个 workflow。模型轮次使用 workflow+round，工具使用 workflow+callId，建议使用 workflow+proposalVersion。approval_request 对 proposal 唯一，approval_record 对 approval 唯一；当前单人策略为强约束，双人审批需未来迁移。

operation_execution 对 approvalId 与 idempotency_key 分别唯一。active_resource_key 对未决订单操作唯一，只有确认成功或明确失败后才释放；UNKNOWN 持续占用，以避免并发退款和变更。SMTP 发送按独立审批唯一登记，但不宣称远端邮件投递 exactly-once。

状态、任务租约和业务并发以版本 CAS 与短行锁组合。任务索引覆盖 status/next_run_at/lease_until，列表索引覆盖 team/state/created_at。所有 SQL 参数绑定；不接受任意列名、排序语句或客户范围。当前查询分页最大100条，审计按50条分页。

## 数据保留、敏感字段与边界

邮件正文、邮箱、地址、模型会话和操作快照使用 AES-256-GCM；邮箱 HMAC 用于等值检索。密钥由环境注入，当前单 key 配置；密钥轮换需要数据迁移，不能直接更换后丢弃旧密钥。日志和工具结果不保存凭据；API 对邮箱和地址脱敏。

用户以 enabled 停用，邮件账户以 enabled 停用，不提供物理删除 API。订单、邮件、工作流保留原始业务历史，无通用删除入口。审计、审批记录、建议快照数据库触发器阻止 UPDATE/DELETE。模型原始内容仅存在加密列，不把隐藏思维链写入前端。

MVP 由 Flyway 与应用共用数据库账号。生产建议将迁移账号与运行账号分开，并限制运行账号的 DDL 权限；触发器不能防御拥有 DROP TRIGGER 权限的数据库管理员。当前不宣称具备外部不可篡改归档。

事务边界详见架构文档；网络调用在事务外，外部执行前持久化 DISPATCHING，结果不明先核对。不使用跨远端系统的虚假分布式原子事务。

V5 建立角色目录；V6 增加可撤销的 auth_refresh_session；V7 增加 llm_provider、llm_model、llm_route、llm_settings、llm_monthly_usage、llm_token_ledger，当前共36表。月度账户主键为团队与北京时间月份，账本以团队、工作流、轮次唯一，配置更新及额度预留/结算共用团队设置行锁。
