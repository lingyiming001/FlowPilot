import { CancelWorkflowButton } from "../components/CancelWorkflowButton";
import { ReconcileButton } from "../components/ReconcileButton";
import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { Link, useParams } from "react-router-dom";
import {
  Alert,
  Button,
  Card,
  Collapse,
  Descriptions,
  Input,
  Modal,
  Space,
  Steps,
  Table,
  Tabs,
  Tag,
  Timeline,
  message,
  Form,
  Select,
} from "antd";
import {
  AuditOutlined,
  CheckOutlined,
  CloseOutlined,
  RollbackOutlined,
  RobotOutlined,
  ArrowLeftOutlined,
  CodeOutlined,
} from "@ant-design/icons";
import { get, post } from "../api/client";
import { useAuth } from "../store/auth";
import { useWorkflowEvents } from "../hooks/useWorkflowEvents";
import { StatusTag, actionLabel } from "../components/StatusTag";
import { Load, PageTitle, JsonView, date } from "../components/Shared";
import type { Row } from "../types";
export function ApprovalButtons({
  approval,
  onSuccess,
}: {
  approval: Row;
  onSuccess?: () => void;
}) {
  const user = useAuth((s) => s.user);
  const [decision, setDecision] = useState(""),
    [comment, setComment] = useState("");
  const qc = useQueryClient();
  const mutation = useMutation({
    mutationFn: () =>
      post(`/approvals/${approval.id}/${decision}`, {
        expectedVersion: approval.version,
        comment,
      }),
    onSuccess: () => {
      message.success("审批已提交，后端将继续处理");
      setDecision("");
      setComment("");
      qc.invalidateQueries();
      onSuccess?.();
    },
    onError: (e) => {
      message.error(e.message);
      qc.invalidateQueries();
    },
  });
  const permitted =
    user?.permissions.includes("approvals:decide") &&
    (user.roles.includes(approval.required_role) ||
      user.roles.includes("SUPERVISOR"));
  return (
    <>
      <Space wrap>
        <Button
          type="primary"
          icon={<CheckOutlined />}
          disabled={!permitted || approval.status !== "PENDING"}
          onClick={() => setDecision("approve")}
        >
          通过
        </Button>
        <Button
          danger
          icon={<CloseOutlined />}
          disabled={!permitted || approval.status !== "PENDING"}
          onClick={() => setDecision("reject")}
        >
          拒绝
        </Button>
        <Button
          icon={<RollbackOutlined />}
          disabled={!permitted || approval.status !== "PENDING"}
          onClick={() => setDecision("return")}
        >
          退回修改
        </Button>
      </Space>
      <Modal
        title={
          decision === "approve"
            ? "确认授权此具体操作"
            : decision === "reject"
              ? "拒绝此操作"
              : "退回修改建议"
        }
        open={!!decision}
        onCancel={() => setDecision("")}
        onOk={() => mutation.mutate()}
        confirmLoading={mutation.isPending}
        okButtonProps={{ disabled: !comment.trim() }}
      >
        <Descriptions
          column={1}
          items={[
            {
              key: "a",
              label: "操作",
              children:
                actionLabel[approval.action_type] || approval.action_type,
            },
            { key: "t", label: "目标", children: approval.target_id },
            { key: "v", label: "审批版本", children: approval.version },
            { key: "e", label: "有效期", children: date(approval.expires_at) },
          ]}
        />
        <Alert
          type="info"
          message="授权仅适用于当前操作快照；退款后发送邮件需另行审批。"
          style={{ margin: "16px 0" }}
        />
        <Input.TextArea
          aria-label="审批意见"
          placeholder="请填写审批意见"
          value={comment}
          onChange={(e) => setComment(e.target.value)}
          rows={3}
          maxLength={1000}
        />
      </Modal>
    </>
  );
}
export function ApprovalCenterPage() {
  const [status, setStatus] = useState("PENDING");
  const q = useQuery({
    queryKey: ["approvals"],
    queryFn: () => get<Row[]>("/approvals"),
    refetchInterval: 4000,
  });
  return (
    <>
      <PageTitle
        title="人工审批中心"
        sub="AI 提供建议，你决定下一步。审批范围与具体操作一一绑定。"
      />
      <Card>
        <Tabs
          activeKey={status}
          onChange={setStatus}
          items={[
            { key: "PENDING", label: "待我处理" },
            { key: "ALL", label: "全部记录" },
          ]}
        />
        <Load loading={q.isLoading} error={q.error}>
          <Table
            rowKey="id"
            dataSource={q.data?.filter(
              (r) => status === "ALL" || r.status === status,
            )}
            columns={[
              {
                title: "操作计划",
                dataIndex: "action_type",
                render: (s) => (
                  <Space>
                    <span className="small-icon">
                      <AuditOutlined />
                    </span>
                    <strong>{actionLabel[s] || s}</strong>
                  </Space>
                ),
              },
              { title: "目标资源", dataIndex: "target_id" },
              {
                title: "所需角色",
                dataIndex: "required_role",
                render: (s) => <Tag>{s}</Tag>,
              },
              {
                title: "状态",
                dataIndex: "status",
                render: (s) => <StatusTag value={s} />,
              },
              { title: "有效期", dataIndex: "expires_at", render: date },
              {
                title: "操作",
                render: (_, r) => (
                  <Link to={`/proposals/${r.proposal_id}`}>
                    审查快照与决策依据 →
                  </Link>
                ),
              },
            ]}
          />
        </Load>
      </Card>
    </>
  );
}
export function WorkflowPage({
  workflowId,
  proposalId,
}: { workflowId?: string; proposalId?: string } = {}) {
  const { id: routeId } = useParams();
  const id = workflowId || routeId;
  const qc = useQueryClient();
  const user = useAuth((s) => s.user);
  const q = useQuery({
    queryKey: ["workflow", id],
    queryFn: () => get(`/workflows/${id}`),
    refetchInterval: 5000,
  });
  useWorkflowEvents(id);
  const [revision, setRevision] = useState(false);
  const [form] = Form.useForm();
  const d = q.data;
  const steps = [
    "邮件接收",
    "意图识别",
    "工具调用",
    "生成建议",
    "人工审批",
    "执行操作",
    "完成",
  ];
  const current = d
    ? ({
        RECEIVED: 0,
        ANALYZING: 1,
        QUERYING_TOOLS: 2,
        GENERATING_PROPOSAL: 3,
        WAITING_APPROVAL: 4,
        APPROVED: 4,
        EXECUTING: 5,
        COMPLETED: 6,
      }[d.state as string] ?? 4)
    : 0;
  const latest =
    (proposalId ? d?.proposals?.find((p: Row) => p.id === proposalId) : null) ||
    d?.proposals?.at(-1);
  return (
    <>
      <PageTitle
        title="工作流执行详情"
        sub={id ? `工作流 / ${id}` : "完整追踪 Agent 的每一步"}
        extra={
          <Link to="/workflows">
            <Button icon={<ArrowLeftOutlined />}>返回列表</Button>
          </Link>
        }
      />
      <Load loading={q.isLoading} error={q.error}>
        {d && (
          <>
            <Card className="workflow-summary">
              <div className="workflow-heading">
                <div>
                  <Tag color="purple" icon={<RobotOutlined />}>
                    AI AGENT
                  </Tag>
                  <StatusTag value={d.state} />
                  <h2>{d.intent || "正在理解客户请求"}</h2>
                  <p>数据由后端持久化 · 事件自动同步 · 每个操作可追溯</p>
                </div>
                <Space>
                  <CancelWorkflowButton workflow={d} />
                  <Link to={`/mails/${d.mail_id}`}>
                    <Button>查看原始邮件</Button>
                  </Link>
                </Space>
              </div>
              <Steps
                size="small"
                current={current}
                status={
                  ["FAILED", "REJECTED"].includes(d.state) ? "error" : "process"
                }
                items={steps.map((title) => ({ title }))}
              />
            </Card>
            {d.state === "MANUAL_REVIEW" && (
              <Alert
                showIcon
                type="warning"
                message="工作流需要人工接管"
                description="请核对失败原因与外部执行结果。修改建议将创建新版本并重新审批；结果未知时不能重新发送写操作。"
                action={
                  <Space>
                    {user?.permissions.includes("workflows:retry") && (
                      <Button
                        onClick={async () => {
                          try {
                            await post(`/workflows/${id}/retry`);
                            message.success("已提交核对任务");
                            qc.invalidateQueries();
                          } catch (e) {
                            message.error((e as Error).message);
                          }
                        }}
                      >
                        核对外部结果
                      </Button>
                    )}
                    {user?.permissions.includes("mails:analyze") && (
                      <Button onClick={() => setRevision(true)}>
                        修订建议
                      </Button>
                    )}
                  </Space>
                }
                style={{ marginBottom: 24 }}
              />
            )}
            <div className="workflow-grid">
              <div>
                <Card
                  title="执行轨迹"
                  extra={<Tag bordered={false}>持久化记录</Tag>}
                >
                  <Timeline
                    items={d.events.map((e: Row) => ({
                      color:
                        e.to_state === "COMPLETED"
                          ? "green"
                          : e.to_state === "MANUAL_REVIEW"
                            ? "orange"
                            : "#7964e8",
                      children: (
                        <div className="timeline-entry">
                          <div>
                            <strong>{e.to_state}</strong>
                            <time>{date(e.created_at)}</time>
                          </div>
                          <p>{e.summary}</p>
                        </div>
                      ),
                    }))}
                  />
                </Card>
                <Card
                  title={
                    <>
                      <CodeOutlined /> 工具调用轨迹
                    </>
                  }
                  style={{ marginTop: 24 }}
                >
                  <Collapse
                    ghost
                    items={d.toolCalls.map((t: Row) => ({
                      key: t.call_id,
                      label: (
                        <Space>
                          <code>{t.name}</code>
                          <StatusTag value={t.status} />
                          <span className="muted">{t.duration_ms} ms</span>
                        </Space>
                      ),
                      children: (
                        <>
                          <p>输入参数</p>
                          <JsonView value={t.arguments_json} />
                          <p>结构化结果</p>
                          <JsonView value={t.result_json} />
                          {t.error_code && (
                            <Alert type="error" message={t.error_code} />
                          )}
                        </>
                      ),
                    }))}
                  />
                  {d.toolCalls.length === 0 && (
                    <p className="muted">尚未执行工具查询</p>
                  )}
                </Card>
              </div>
              <div>
                <Card
                  title={
                    <>
                      <RobotOutlined /> AI 处理建议
                    </>
                  }
                  className="proposal-card"
                >
                  {latest ? (
                    <>
                      <Space>
                        <strong>{actionLabel[latest.action_type]}</strong>
                        <StatusTag value={latest.risk_level} />
                      </Space>
                      <p className="proposal-reason">
                        {latest.snapshot.reason}
                      </p>
                      <Descriptions
                        size="small"
                        column={1}
                        items={[
                          {
                            key: "target",
                            label: "目标资源",
                            children: latest.target_id,
                          },
                          {
                            key: "version",
                            label: "建议版本",
                            children: latest.proposal_version,
                          },
                          {
                            key: "effect",
                            label: "预期影响",
                            children: latest.snapshot.expectedEffect,
                          },
                          {
                            key: "role",
                            label: "审批策略",
                            children: latest.snapshot.approvalPolicy,
                          },
                        ]}
                      />
                      <div className="snapshot">
                        <span className="eyebrow">不可变操作快照</span>
                        <JsonView value={latest.snapshot.parameters} />
                      </div>
                      <p className="digest">SHA-256 · {latest.digest}</p>
                      {latest.snapshot.parameters.orderId && (
                        <Link
                          to={`/orders/${latest.snapshot.parameters.orderId}`}
                        >
                          查看订单详情 →
                        </Link>
                      )}
                    </>
                  ) : (
                    <p className="muted">分析完成后将在此展示建议</p>
                  )}
                </Card>
                <Card title="人工审批" style={{ marginTop: 24 }}>
                  {d.approvals
                    .filter(
                      (a: Row) => !proposalId || a.proposal_id === proposalId,
                    )
                    .map((a: Row) => (
                      <div className="approval-item" key={a.id}>
                        <Space>
                          <strong>{actionLabel[a.action_type]}</strong>
                          <StatusTag value={a.status} />
                        </Space>
                        <p className="muted">有效期：{date(a.expires_at)}</p>
                        {a.status === "PENDING" && (
                          <ApprovalButtons approval={a} />
                        )}
                      </div>
                    ))}
                  {!d.approvals.length && (
                    <p className="muted">当前没有审批任务</p>
                  )}
                </Card>
                <Card title="执行结果与重试" style={{ marginTop: 24 }}>
                  {d.executions.map((e: Row) => (
                    <div key={e.id}>
                      <Space>
                        <strong>{actionLabel[e.action_type]}</strong>
                        <StatusTag value={e.status} />
                      </Space>
                      <JsonView value={e.result_json} />
                      <ReconcileButton workflowId={id!} execution={e} />
                    </div>
                  ))}
                  <Table
                    size="small"
                    rowKey="id"
                    pagination={false}
                    dataSource={d.tasks}
                    columns={[
                      { title: "任务", dataIndex: "task_type" },
                      {
                        title: "状态",
                        dataIndex: "status",
                        render: (s) => <Tag>{s}</Tag>,
                      },
                      { title: "重试次数", dataIndex: "attempts" },
                    ]}
                  />
                </Card>
              </div>
            </div>
            <Modal
              title="修订操作建议（将重新审批）"
              open={revision}
              onCancel={() => setRevision(false)}
              onOk={() => form.submit()}
            >
              <Form
                form={form}
                layout="vertical"
                initialValues={{ actionType: "sendCustomerEmail" }}
                onFinish={async (v) => {
                  try {
                    await post(`/workflows/${id}/revise`, {
                      actionType: v.actionType,
                      orderId: v.orderId,
                      reason: v.reason,
                      parameters: {
                        body: v.body || "",
                        address: v.address || "",
                      },
                    });
                    setRevision(false);
                    qc.invalidateQueries();
                    message.success("已创建新建议与审批");
                  } catch (e) {
                    message.error((e as Error).message);
                  }
                }}
              >
                <Form.Item
                  name="actionType"
                  label="操作类型"
                  rules={[{ required: true }]}
                >
                  <Select
                    options={Object.entries(actionLabel).map(
                      ([value, label]) => ({ value, label }),
                    )}
                  />
                </Form.Item>
                <Form.Item
                  name="orderId"
                  label="订单号"
                  rules={[{ required: true }]}
                >
                  <Input />
                </Form.Item>
                <Form.Item
                  name="reason"
                  label="修订依据"
                  rules={[{ required: true }]}
                >
                  <Input.TextArea />
                </Form.Item>
                <Form.Item name="body" label="邮件草稿">
                  <Input.TextArea rows={4} />
                </Form.Item>
                <Form.Item name="address" label="收货地址（修改地址时）">
                  <Input />
                </Form.Item>
              </Form>
            </Modal>
          </>
        )}
      </Load>
    </>
  );
}

export function ProposalReviewPage() {
  const { id } = useParams();
  const q = useQuery({
    queryKey: ["proposal", id],
    queryFn: () => get(`/proposals/${id}`),
  });
  return (
    <Load loading={q.isLoading} error={q.error}>
      {q.data && (
        <WorkflowPage workflowId={q.data.workflowId} proposalId={id} />
      )}
    </Load>
  );
}
