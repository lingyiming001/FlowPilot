# 验收记录

更新时间：2026-10-16（Asia/Shanghai）。

本轮交付：记住登录、模型供应商/模型/路由管理、团队每月Token额度与对账。按用户“测试我来”的安排，本轮未运行自动业务测试或浏览器E2E；真实模型、IMAPS、SMTP、支付网关未作真实集成验收。

实际检查结果：
- Java21 `mvn -f flowpilot/pom.xml -DskipTests package -q`：通过，包含最后的URL校验修正。
- 前端 `npm run build`：TypeScript与Vite构建通过。
- `python3 scripts/check_project.py`：11模块、依赖无环、36表、8图、Controller仅调用Service，通过。
- Flyway日志：V6/V7迁移成功，数据库当前版本7；既有数据卷保留。
- 最终后端、前端镜像均已启动并通过Compose健康检查。
- `http://localhost:18080/`：HTTP200，返回前端HTML。
- `/actuator/health`：HTTP200，UP。
- 无Cookie且携带同源Origin、自定义会话头的 `/api/auth/refresh`：HTTP401（预期，验证代理端口与同源校验可协作）。
- 运行时OpenAPI已导出，38条路径，包含刷新会话和模型管理。

待用户验收：记住登录30天及退出撤销、供应商密钥保存与更新、模型配置、路由预览与开启、真实调用用量/额度不足/不确定结果对账。跨月周期采用Asia/Shanghai自然月分账，未执行时钟跨月模拟；不得将源码实现视为已完成跨月实测。

历史版本的测试结果不代替本轮功能验收。
