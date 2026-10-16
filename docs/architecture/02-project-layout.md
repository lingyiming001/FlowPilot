# 项目目录与核心类规划

以下为各阶段将实际创建的目录树。第一阶段仅创建设计文件和项目根目录，不预先生成空业务类。Java 根包 `com.flowpilot`，Maven groupId `com.flowpilot`，父 artifactId `flowpilot-parent`。

```text
FlowPilot/
├── README.md
├── .gitignore
├── docs/
│   ├── phase-plan.md
│   ├── architecture/
│   │   ├── 01-architecture.md
│   │   └── 02-project-layout.md
│   ├── database/                  # 阶段二：ER、索引、事务、全量 SQL
│   ├── api/                       # 阶段二：OpenAPI 与请求响应示例
│   ├── testing/                   # 阶段七：验收证据与演示步骤
│   └── diagrams/                  # 后续补齐八类图的源文件
├── scripts/
│   ├── check_architecture.py
│   └── demo.sh                    # 阶段七
├── flowpilot-web/
│   ├── package.json
│   ├── package-lock.json
│   ├── tsconfig.json
│   ├── vite.config.ts
│   ├── index.html
│   └── src/
│       ├── main.tsx
│       ├── app/{App.tsx,router.tsx,QueryProvider.tsx}
│       ├── api/{client.ts,auth.ts,mails.ts,workflows.ts,approvals.ts,orders.ts,audit.ts,users.ts}
│       ├── store/auth.ts
│       ├── hooks/useWorkflowEvents.ts
│       ├── types/{api.ts,workflow.ts,approval.ts}
│       ├── layouts/AppLayout.tsx
│       ├── components/{PermissionGuard.tsx,WorkflowTimeline.tsx,ToolCallTrace.tsx,ApprovalDialog.tsx}
│       └── pages/
│           ├── LoginPage.tsx
│           ├── DashboardPage.tsx
│           ├── MailListPage.tsx
│           ├── MailDetailPage.tsx
│           ├── WorkflowDetailPage.tsx
│           ├── ToolCallsPage.tsx
│           ├── ProposalReviewPage.tsx
│           ├── ApprovalCenterPage.tsx
│           ├── OrderListPage.tsx
│           ├── OrderDetailPage.tsx
│           ├── ExceptionTasksPage.tsx
│           ├── AuditLogsPage.tsx
│           ├── UsersPage.tsx
│           └── RolesPage.tsx
├── flowpilot/
│   ├── pom.xml
│   ├── app/pom.xml
│   ├── common/pom.xml
│   ├── security/pom.xml
│   ├── customer/pom.xml
│   ├── integration/pom.xml
│   ├── order/pom.xml
│   ├── mail/pom.xml
│   ├── agent/pom.xml
│   ├── approval/pom.xml
│   ├── audit/pom.xml
│   └── workflow/pom.xml
└── deploy/
    ├── README.md
    ├── docker/{Dockerfile.backend,Dockerfile.frontend,nginx.conf}
    ├── compose/{docker-compose.yml,docker-compose.dev.yml}
    ├── env/.env.example
    ├── monitoring/prometheus.yml
    └── scripts/{start.sh,stop.sh,health-check.sh}
```

花括号表示相邻独立文件，不是实际文件名。所有 Maven 模块使用 `src/main/java/com/flowpilot/<module>/` 与 `src/test/java/com/flowpilot/<module>/`；app 资源包含 `application.yml`、`application-dev.yml`、`application-test.yml`、`logback-spring.xml`、`db/migration/V1__initial_schema.sql`、后续迁移与测试 profile 的 seed 入口。生产不自动加载演示用户。

## Java 模块内部文件规划

各业务模块统一按 controller、service、service/impl、mapper、entity、dto、vo、enums、convert、config、exception 分层；仅创建有实际职责的包，不为目录完整度生成空类。common、integration 等无 HTTP 职责的模块不强加 Controller/Mapper。

| 模块 | 路径（相对该模块 Java 根包）与核心类 |
| --- | --- |
| app | FlowPilotApplication；config/SecurityConfig、PersistenceConfig、ObservabilityConfig；controller/SseController |
| common | dto/ApiResponse、PageResponse；exception/BusinessException、GlobalExceptionHandler；enums/ErrorCode；config/ClockConfig |
| security | controller/AuthController、UserController、RoleController；dto/LoginRequest；vo/LoginResponse、UserVo；service/AuthService、DataScopeService；service/impl/AuthServiceImpl；policy/ExecutionPolicyService；entity/SysUser、SysRole、SysPermission；mapper/SysUserMapper；config/JwtAuthenticationFilter；convert/UserConvert |
| customer | service/CustomerQueryService；service/impl/CustomerQueryServiceImpl；entity/Customer、CustomerIdentity；mapper/CustomerMapper、CustomerIdentityMapper；vo/CustomerVo；exception/CustomerIdentityException |
| integration | service/RefundGateway、LogisticsGateway、MailGateway；service/impl/HttpRefundGateway、HttpLogisticsGateway、SmtpMailGateway；mock/MockRefundGateway、MockLogisticsGateway、MockMailGateway；dto/IntegrationResult；entity/IntegrationRequestLog；mapper/IntegrationRequestLogMapper；config/IntegrationProperties、LlmProviderConfig |
| order | controller/OrderController；service/OrderQueryService、OrderOperationService；service/impl/OrderQueryServiceImpl、OrderOperationServiceImpl；entity/CustomerOrder、OrderItem、OrderOperation；mapper/CustomerOrderMapper；dto/RefundCommand、AddressChangeCommand；vo/OrderVo；enums/OrderStatus；convert/OrderConvert；exception/OrderBusinessException |
| mail | controller/MailController（列表与详情）；service/MailReceiveService、CustomerMailService；service/impl/MailReceiveServiceImpl、CustomerMailServiceImpl；entity/MailMessage、MailAccount、MailAttachment、MailProcessingRecord；mapper/MailMessageMapper；dto/InboundMail；vo/MailVo；convert/MailConvert；config/MailProperties |
| audit | controller/AuditLogController；service/AuditService；service/impl/AuditServiceImpl；entity/AuditLog；mapper/AuditLogMapper；dto/AuditEvent；vo/AuditLogVo；convert/AuditConvert |
| approval | controller/ApprovalQueryController（只读）；service/ApprovalService、ApprovalPolicyService；service/impl/ApprovalServiceImpl；entity/ApprovalRequest、ApprovalRecord；mapper/ApprovalRequestMapper；dto/ApprovalDecisionCommand；vo/ApprovalVo；enums/ApprovalStatus；convert/ApprovalConvert；exception/ApprovalConflictException |
| agent | orchestrator/AgentOrchestrator；context/AgentContext；llm/LlmClient、SpringAiLlmClient、FakeLlmClient；prompt/PromptTemplateManager；tool/ToolRegistry、ToolDispatcher、ToolDefinition、ToolResult；policy/ToolAccessPolicy；parser/ProposalParser；service/ProposalGenerator；entity/AgentSession、AgentMessage、AgentToolCall、AgentProposal；mapper/AgentToolCallMapper；dto/ProposedAction |
| workflow | controller/WorkflowController、MailAnalysisController、ApprovalDecisionController；service/WorkflowApplicationService；service/impl/WorkflowApplicationServiceImpl；engine/WorkflowEngine；state/AgentStateManager；transition/WorkflowTransitionRules；executor/ToolExecutionService；recovery/WorkflowRecoveryService；scheduler/WorkflowTaskScheduler；entity/WorkflowInstance、WorkflowTask、WorkflowContext、WorkflowTransitionLog、OperationExecution、FailedTask；mapper/WorkflowInstanceMapper；dto/AnalyzeMailCommand、RetryCommand；vo/WorkflowVo；enums/WorkflowState；convert/WorkflowConvert；exception/IllegalTransitionException |

### 工具实现路径

agent/tool/impl：GetOrderByIdTool、GetOrderByEmailTool、GetLogisticsInfoTool、GetCustomerInfoTool、CheckRefundEligibilityTool。workflow/executor/action：RequestRefundAction、CancelOrderAction、UpdateShippingAddressAction、SendCustomerEmailAction。ToolRegistry 以统一 ToolDefinition 发现读写元数据；ToolDispatcher 仅接受 READ，ToolExecutionService 仅从审批加载 WRITE。

### 测试文件入口

- agent：ProposalParserTest、ToolValidationTest、ToolAccessPolicyTest、AgentOrchestratorTest。
- workflow：WorkflowTransitionRulesTest、WorkflowRecoveryIntegrationTest、ApprovalConcurrencyIntegrationTest、RefundIdempotencyIntegrationTest、MailOpsEndToEndTest。
- security：AuthorizationIntegrationTest、DataScopeIntegrationTest。
- approval：ApprovalPolicyTest、ImmutableSnapshotTest。
- order：OrderBusinessRulesTest。
- app：MySqlPersistenceIntegrationTest、ApplicationRestartIntegrationTest。
- flowpilot-web：工作流和审批组件测试；浏览器测试覆盖登录、邮件分析、审批、拒绝、异常与审计。

集成测试在 app 或 workflow 测试装配层连接全模块；禁止为测试增加生产模块反向依赖。测试报告与浏览器产物统一输出到根 `.agents/test/`，源码测试仍放本模块测试目录。

## 当前实际文件与计划的区别

当前实际文件通过 `git status --short` 与文件系统核实；以上源文件从第三阶段起按模块创建。第一阶段根项目目录用 README 说明保留，避免误认为已具备脚手架或启动入口。
