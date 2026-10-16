# 当前实际项目目录

由 scripts/generate-layout.py 生成；排除依赖、构建产物、私密环境和本地测试记录。

```text
FlowPilot/
├── deploy/
│   ├── compose/
│   │   ├── docker-compose.dev.yml
│   │   └── docker-compose.yml
│   ├── docker/
│   │   ├── Dockerfile.backend
│   │   ├── Dockerfile.frontend
│   │   └── nginx.conf
│   ├── env/
│   │   ├── .env.example
│   │   └── ACCESS.local.md
│   ├── monitoring/
│   │   ├── alerts.yml
│   │   └── prometheus.yml
│   ├── scripts/
│   │   ├── health-check.sh
│   │   ├── init-env.py
│   │   ├── start.sh
│   │   └── stop.sh
│   └── README.md
├── docs/
│   ├── api/
│   │   ├── README.md
│   │   └── openapi.json
│   ├── architecture/
│   │   ├── 01-architecture.md
│   │   └── 02-project-layout.md
│   ├── database/
│   │   ├── README.md
│   │   └── schema.sql
│   ├── diagrams/
│   │   ├── approval-execution.mmd
│   │   ├── database-er.mmd
│   │   ├── deployment.mmd
│   │   ├── modules.mmd
│   │   ├── recovery.mmd
│   │   ├── system.mmd
│   │   ├── tool-calling.mmd
│   │   └── workflow-states.mmd
│   ├── testing/
│   │   ├── acceptance.md
│   │   └── demo-guide.md
│   ├── implementation.md
│   ├── phase-plan.md
│   └── project-layout.md
├── flowpilot/
│   ├── agent/
│   │   ├── src/
│   │   │   └── main/
│   │   │       └── java/
│   │   │           └── com/
│   │   │               └── flowpilot/
│   │   │                   └── agent/
│   │   │                       ├── config/
│   │   │                       │   ├── ModelSettingsController.java
│   │   │                       │   └── ModelSettingsService.java
│   │   │                       ├── context/
│   │   │                       │   └── AgentContext.java
│   │   │                       ├── dto/
│   │   │                       │   └── ProposedAction.java
│   │   │                       ├── llm/
│   │   │                       │   ├── FakeLlmClient.java
│   │   │                       │   ├── LlmClient.java
│   │   │                       │   ├── RoutedLlmClient.java
│   │   │                       │   └── SpringAiLlmClient.java
│   │   │                       ├── mapper/
│   │   │                       │   └── AgentMapper.java
│   │   │                       ├── orchestrator/
│   │   │                       │   └── AgentOrchestrator.java
│   │   │                       ├── prompt/
│   │   │                       │   └── PromptTemplateManager.java
│   │   │                       ├── service/
│   │   │                       │   └── ProposalGenerator.java
│   │   │                       └── tool/
│   │   │                           ├── ToolDispatcher.java
│   │   │                           └── ToolRegistry.java
│   │   └── pom.xml
│   ├── app/
│   │   ├── src/
│   │   │   ├── main/
│   │   │   │   ├── java/
│   │   │   │   │   └── com/
│   │   │   │   │       └── flowpilot/
│   │   │   │   │           └── app/
│   │   │   │   │               ├── config/
│   │   │   │   │               │   ├── BootstrapAdminInitializer.java
│   │   │   │   │               │   ├── DemoDataInitializer.java
│   │   │   │   │               │   ├── LoginRateLimitFilter.java
│   │   │   │   │               │   ├── OpenApiConfig.java
│   │   │   │   │               │   ├── SecurityConfig.java
│   │   │   │   │               │   └── WorkflowMetricsConfig.java
│   │   │   │   │               ├── controller/
│   │   │   │   │               │   ├── ApiController.java
│   │   │   │   │               │   └── SseController.java
│   │   │   │   │               └── FlowPilotApplication.java
│   │   │   │   └── resources/
│   │   │   │       ├── db/
│   │   │   │       │   └── migration/
│   │   │   │       │       ├── V1__initial_schema.sql
│   │   │   │       │       ├── V2__execution_resource_reservation.sql
│   │   │   │       │       ├── V3__agent_round_checkpoint.sql
│   │   │   │       │       ├── V4__immutable_evidence.sql
│   │   │   │       │       ├── V5__role_catalog.sql
│   │   │   │       │       ├── V6__remembered_sessions.sql
│   │   │   │       │       └── V7__model_management.sql
│   │   │   │       └── application.yml
│   │   │   └── test/
│   │   │       └── java/
│   │   │           └── com/
│   │   │               └── flowpilot/
│   │   │                   └── app/
│   │   │                       ├── ApplicationRestartTest.java
│   │   │                       ├── MailOpsIntegrationTest.java
│   │   │                       └── PolicyAndStateTest.java
│   │   └── pom.xml
│   ├── approval/
│   │   ├── src/
│   │   │   └── main/
│   │   │       └── java/
│   │   │           └── com/
│   │   │               └── flowpilot/
│   │   │                   └── approval/
│   │   │                       ├── mapper/
│   │   │                       │   └── ApprovalMapper.java
│   │   │                       └── service/
│   │   │                           ├── impl/
│   │   │                           │   └── ApprovalServiceImpl.java
│   │   │                           └── ApprovalService.java
│   │   └── pom.xml
│   ├── audit/
│   │   ├── src/
│   │   │   └── main/
│   │   │       └── java/
│   │   │           └── com/
│   │   │               └── flowpilot/
│   │   │                   └── audit/
│   │   │                       ├── mapper/
│   │   │                       │   └── AuditMapper.java
│   │   │                       └── service/
│   │   │                           ├── impl/
│   │   │                           │   └── AuditServiceImpl.java
│   │   │                           └── AuditService.java
│   │   └── pom.xml
│   ├── common/
│   │   ├── src/
│   │   │   └── main/
│   │   │       └── java/
│   │   │           └── com/
│   │   │               └── flowpilot/
│   │   │                   └── common/
│   │   │                       ├── dto/
│   │   │                       │   └── ApiResponse.java
│   │   │                       ├── exception/
│   │   │                       │   ├── BusinessException.java
│   │   │                       │   └── GlobalExceptionHandler.java
│   │   │                       ├── mapper/
│   │   │                       │   └── SqlMapper.java
│   │   │                       └── util/
│   │   │                           ├── Crypto.java
│   │   │                           └── Json.java
│   │   └── pom.xml
│   ├── customer/
│   │   ├── src/
│   │   │   └── main/
│   │   │       └── java/
│   │   │           └── com/
│   │   │               └── flowpilot/
│   │   │                   └── customer/
│   │   │                       ├── mapper/
│   │   │                       │   └── CustomerMapper.java
│   │   │                       └── service/
│   │   │                           └── CustomerQueryService.java
│   │   └── pom.xml
│   ├── integration/
│   │   ├── src/
│   │   │   └── main/
│   │   │       └── java/
│   │   │           └── com/
│   │   │               └── flowpilot/
│   │   │                   └── integration/
│   │   │                       ├── mapper/
│   │   │                       │   └── IntegrationMapper.java
│   │   │                       └── service/
│   │   │                           ├── impl/
│   │   │                           │   ├── MockExternalGateway.java
│   │   │                           │   └── RealExternalGateway.java
│   │   │                           ├── ExternalGateway.java
│   │   │                           └── MailIdentityVerifier.java
│   │   └── pom.xml
│   ├── mail/
│   │   ├── src/
│   │   │   └── main/
│   │   │       └── java/
│   │   │           └── com/
│   │   │               └── flowpilot/
│   │   │                   └── mail/
│   │   │                       ├── integration/
│   │   │                       │   └── ImapMailReceiver.java
│   │   │                       ├── mapper/
│   │   │                       │   └── MailMapper.java
│   │   │                       └── service/
│   │   │                           ├── impl/
│   │   │                           │   └── MailServiceImpl.java
│   │   │                           └── MailService.java
│   │   └── pom.xml
│   ├── order/
│   │   ├── src/
│   │   │   └── main/
│   │   │       └── java/
│   │   │           └── com/
│   │   │               └── flowpilot/
│   │   │                   └── order/
│   │   │                       ├── entity/
│   │   │                       │   └── CustomerOrderEntity.java
│   │   │                       ├── mapper/
│   │   │                       │   ├── CustomerOrderMapper.java
│   │   │                       │   └── OrderMapper.java
│   │   │                       └── service/
│   │   │                           ├── impl/
│   │   │                           │   └── OrderQueryServiceImpl.java
│   │   │                           ├── OrderOperationService.java
│   │   │                           └── OrderQueryService.java
│   │   └── pom.xml
│   ├── security/
│   │   ├── src/
│   │   │   └── main/
│   │   │       └── java/
│   │   │           └── com/
│   │   │               └── flowpilot/
│   │   │                   └── security/
│   │   │                       ├── controller/
│   │   │                       │   └── AuthController.java
│   │   │                       ├── dto/
│   │   │                       │   └── Actor.java
│   │   │                       ├── mapper/
│   │   │                       │   └── SecurityMapper.java
│   │   │                       ├── policy/
│   │   │                       │   └── ExecutionPolicyService.java
│   │   │                       └── service/
│   │   │                           ├── impl/
│   │   │                           │   └── AuthServiceImpl.java
│   │   │                           ├── AuthService.java
│   │   │                           ├── DataScopeService.java
│   │   │                           └── RememberMeService.java
│   │   └── pom.xml
│   ├── workflow/
│   │   ├── src/
│   │   │   └── main/
│   │   │       └── java/
│   │   │           └── com/
│   │   │               └── flowpilot/
│   │   │                   └── workflow/
│   │   │                       ├── engine/
│   │   │                       │   └── WorkflowEngine.java
│   │   │                       ├── enums/
│   │   │                       │   └── WorkflowState.java
│   │   │                       ├── executor/
│   │   │                       │   └── ToolExecutionService.java
│   │   │                       ├── mapper/
│   │   │                       │   └── WorkflowMapper.java
│   │   │                       ├── recovery/
│   │   │                       │   └── WorkflowRecoveryService.java
│   │   │                       ├── scheduler/
│   │   │                       │   ├── InboundWorkflowScheduler.java
│   │   │                       │   └── WorkflowTaskScheduler.java
│   │   │                       ├── service/
│   │   │                       │   ├── impl/
│   │   │                       │   │   └── WorkflowApplicationServiceImpl.java
│   │   │                       │   └── WorkflowApplicationService.java
│   │   │                       ├── state/
│   │   │                       │   └── AgentStateManager.java
│   │   │                       └── transition/
│   │   │                           └── WorkflowTransitionRules.java
│   │   └── pom.xml
│   ├── README.md
│   └── pom.xml
├── flowpilot-web/
│   ├── src/
│   │   ├── api/
│   │   │   └── client.ts
│   │   ├── components/
│   │   │   ├── CancelWorkflowButton.tsx
│   │   │   ├── CreateUserButton.tsx
│   │   │   ├── ReconcileButton.tsx
│   │   │   ├── Shared.tsx
│   │   │   ├── StatusTag.test.tsx
│   │   │   └── StatusTag.tsx
│   │   ├── hooks/
│   │   │   └── useWorkflowEvents.ts
│   │   ├── pages/
│   │   │   ├── DashboardPage.tsx
│   │   │   ├── ListPages.tsx
│   │   │   ├── LoginPage.tsx
│   │   │   ├── ModelSettingsPage.tsx
│   │   │   └── WorkflowPage.tsx
│   │   ├── store/
│   │   │   └── auth.ts
│   │   ├── App.tsx
│   │   ├── main.tsx
│   │   ├── style.css
│   │   └── types.ts
│   ├── README.md
│   ├── index.html
│   ├── package-lock.json
│   ├── package.json
│   ├── tsconfig.json
│   └── vite.config.ts
├── scripts/
│   ├── check_architecture.py
│   ├── check_project.py
│   ├── check_running.py
│   ├── demo.py
│   ├── dev-backend.py
│   └── generate-layout.py
├── .dockerignore
├── .gitignore
└── README.md
```
