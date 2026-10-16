# FlowPilot 自动部署与运行

## 一键本地部署

需要 Java 21、Maven 3.6.3+、Node 22+、npm、Python 3、已启动的 Docker Desktop/Engine。

```sh
bash deploy/scripts/start.sh
```

脚本生成本地 `.env` 随机密钥和密码（权限0600、Git忽略），执行 Maven verify、npm ci/build，然后 Docker Compose 构建并启动、等待健康检查。默认只将前端绑定到 `127.0.0.1:18080`。容器配置 `restart: unless-stopped`，Docker 重启后会自动恢复；不创建操作系统 cron 或其他定时任务。

打开 `http://localhost:18080`。演示账号 admin / supervisor / operator / auditor；密码为 `deploy/env/.env` 中 DEMO_PASSWORD。admin 额外显式分配主管角色以便本地演示，ADMIN 权限本身不绕过业务审批。

```sh
bash deploy/scripts/health-check.sh
bash deploy/scripts/stop.sh
```

停止命令不删除数据卷；不要对有业务数据的环境使用 `down -v`。数据库状态、审批、任务和外部 Mock 结果均可跨容器重启恢复。

## 分开调试

```sh
python3 deploy/scripts/init-env.py
docker compose --env-file deploy/env/.env -f deploy/compose/docker-compose.yml -f deploy/compose/docker-compose.dev.yml up -d mysql redis
```

本地 Java 启动需将 `.env` 对应项注入进程环境（或使用 `scripts/dev-backend.py`），默认数据库端口13306、Redis16379。前端 `npm --prefix flowpilot-web run dev`，Vite 5173 将 /api 代理到 localhost:8080。开发覆盖文件仅将 DB/Redis 绑定到本机，生产不暴露它们。

## 生产差异

设置 DEMO_ENABLED=false，禁用 Fake/Mock 并提供真实 Provider、邮件与业务网关凭据；演示用户不能用于生产。生产首次部署设置 BOOTSTRAP_ENABLED=true、BOOTSTRAP_PASSWORD（至少12字符）与 BOOTSTRAP_TEAM，仅在用户表为空时创建ADMIN账号；随后通过用户管理创建业务角色用户，完成后关闭BOOTSTRAP_ENABLED并移除初始化密码。角色目录由Flyway V5建立，密码存储BCrypt。当前演示初始化不是企业身份接入方案。部署前为运行账号分配最小数据库权限，将 Flyway 迁移账号分离，并建立密钥备份/轮换迁移流程。

前置 TLS 终结代理、修改公开绑定与域名、限制数据库和管理端点网络。Nginx 配置同源 /api、SSE 不缓冲、CSP 与基础安全头。Prometheus 通过内部网络抓取 /actuator/prometheus，公网代理仅暴露 health；如开启开发后端端口，须限制其可访问范围。

```sh
docker compose --env-file deploy/env/.env -f deploy/compose/docker-compose.yml --profile monitoring up -d
```

日志输出 stdout/stderr，由容器平台采集，配置保留与轮转。应用日志不打印模型密钥、JWT、原始邮件或完整支付请求。UNKNOWN 数量、任务积压和失败任务应纳入告警。

## 故障排查

```sh
docker compose --env-file deploy/env/.env -f deploy/compose/docker-compose.yml ps
docker compose --env-file deploy/env/.env -f deploy/compose/docker-compose.yml logs --tail=100 backend
docker compose --env-file deploy/env/.env -f deploy/compose/docker-compose.yml logs --tail=100 mysql
```

数据库启动失败检查磁盘/卷权限和密码是否与已有卷一致；不能通过换 `.env` 密码修改已初始化的 MySQL 账户。Redis 不可用时登录限流失败关闭，恢复 Redis 后重试。Flyway 报错先修复迁移，不能在生产删库重建。审批409请刷新并重新核对；UNKNOWN写操作只允许核对，不无条件重发。

备份至少包含 MySQL、ENCRYPTION_KEY 和应用配置，密钥单独保管；恢复测试需在隔离环境验证审批、未知操作与幂等键。密钥丢失无法解密邮件和快照。

模型配置可由管理员在“模型与用量”中保存，无需重启；团队 Token 额度按北京时间自然月结算。HTTPS 部署请设置 `AUTH_COOKIE_SECURE=true` 保护记住登录的 HttpOnly Cookie，并正确设置代理转发头。当前 Nginx 已覆盖 X-Forwarded-Host/Proto。
