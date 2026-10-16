# 演示与验收步骤

所有演示默认 Fake LLM + Mock 业务网关，不代表真实退款和邮件发送。

1. 执行 `bash deploy/scripts/start.sh`，打开 http://localhost:18080，以本地 admin 或 supervisor 登录，密码见 deploy/env/.env。
2. 进入客户邮件，打开“想了解我的订单物流进度”，点击启动AI分析。观察工作流：RECEIVED→ANALYZING→QUERYING_TOOLS→GENERATING_PROPOSAL→WAITING_APPROVAL。
3. 展开 getOrderById、getLogisticsInfo 工具轨迹，审查真实数据库订单与Mock物流，核对回复草稿与快照，再通过审批。只有审批后才出现邮件执行记录，最终COMPLETED。
4. 以operator查看“商品有问题，希望申请退款”，启动分析；operator没有主管权限，不能批准退款。以supervisor或演示admin登录，检查金额和资格后批准退款。
5. 退款成功后再次WAITING_APPROVAL，新操作是sendCustomerEmail，旧退款审批不能替代通知审批。审核后独立批准通知，工作流完成。
6. 打开审计，查看关联workflowId的工具/状态/审批/执行证据。API并发审批与越权、UNKNOWN不重发、撤销权限、不可变记录均有后端自动化测试。
7. 服务重启验证使用ApplicationRestartTest：完整关闭并重新建立Spring上下文，共用同一MySQL容器，验证原审批继续且只执行一次。
8. 对UNKNOWN任务先查询提供方结果；无法通过API核对时，主管可在执行结果区登记核对凭据引用与明确结果。此入口不会重发外部操作，保存凭据摘要与审计。未核实前禁止把UNKNOWN登记为成功或失败。
9. 安全等待状态可终止工作流，旧审批立即失效；有未知外部操作时禁止直接取消。退回修改会生成新快照与新审批，不修改原快照。

命令行也可使用真实公开API：

```sh
python3 scripts/demo.py --scenario query
python3 scripts/demo.py --scenario refund --approve
```

脚本先检查后端确实处于FAKE/mock模式才继续。`--approve`显式以本地演示主管身份逐项审批，不能用于真实业务环境。重复执行复用已有workflow，不创建重复业务操作。
