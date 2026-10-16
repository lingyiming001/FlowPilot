# flowpilot-web 前端

React19 / TypeScript / Vite7 / Ant Design5，接入真实后端API。包含登录、工作台、邮件详情、工作流和工具轨迹、审批、订单、异常、审计、用户和角色权限页面。

```sh
npm ci
npm run dev
npm run build
npm test
```

开发地址 localhost:5173，/api 代理至localhost:8080。生产以Docker Nginx提供dist并代理后端。真实模型或演示模式由后端配置，前端不会自行执行状态迁移。
