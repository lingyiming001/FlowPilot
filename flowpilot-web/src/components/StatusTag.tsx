import { Tag } from "antd";
export const labels: Record<string, string> = {
  RECEIVED: "已接收",
  ANALYZING: "AI 分析中",
  QUERYING_TOOLS: "查询工具",
  GENERATING_PROPOSAL: "生成建议",
  WAITING_APPROVAL: "待人工审批",
  APPROVED: "已批准",
  REJECTED: "已拒绝",
  RETURNED: "已退回",
  EXPIRED: "已过期",
  EXECUTING: "执行中",
  COMPLETED: "已完成",
  FAILED: "失败",
  RETRY_PENDING: "等待重试",
  MANUAL_REVIEW: "需人工处理",
  CANCELLED: "已取消",
  PENDING: "待处理",
  PROCESSING: "处理中",
  SUCCESS: "成功",
  UNKNOWN: "结果待核对",
  DISPATCHING: "已派发",
  HIGH: "高风险",
  LOW: "低风险",
  PAID: "已付款",
  SHIPPED: "已发货",
  DELIVERED: "已签收",
  REFUNDED: "已退款",
};
export function StatusTag({ value }: { value: string }) {
  return (
    <Tag
      bordered={false}
      color={
        ["COMPLETED", "SUCCESS", "APPROVED", "REFUNDED"].includes(value)
          ? "success"
          : ["FAILED", "HIGH", "REJECTED"].includes(value)
            ? "error"
            : [
                  "WAITING_APPROVAL",
                  "MANUAL_REVIEW",
                  "UNKNOWN",
                  "PENDING",
                ].includes(value)
              ? "warning"
              : "processing"
      }
    >
      {labels[value] || value}
    </Tag>
  );
}
export const actionLabel: Record<string, string> = {
  requestRefund: "订单退款",
  cancelOrder: "取消订单",
  updateShippingAddress: "修改收货地址",
  sendCustomerEmail: "发送客户邮件",
};
