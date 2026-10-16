import { CreateUserButton } from "../components/CreateUserButton";
import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { Link, useNavigate, useParams } from "react-router-dom";
import {
  Button,
  Card,
  Table,
  Input,
  Space,
  Descriptions,
  message,
  Tag,
  Switch,
  Select,
  Modal,
} from "antd";
import {
  SearchOutlined,
  ReloadOutlined,
  ArrowRightOutlined,
  RobotOutlined,
} from "@ant-design/icons";
import { get, post, http } from "../api/client";
import { useAuth } from "../store/auth";
import { StatusTag, actionLabel } from "../components/StatusTag";
import { Load, PageTitle, date } from "../components/Shared";
import type { Row } from "../types";
export function MailsPage() {
  const [search, setSearch] = useState("");
  const q = useQuery({
    queryKey: ["mails"],
    queryFn: () => get<Row[]>("/mails"),
    refetchInterval: 5000,
  });
  return (
    <>
      <PageTitle
        title="客户邮件"
        sub="所有客户请求，一处清晰掌握。让 AI 从阅读开始，为你整理下一步。"
        extra={
          <Button icon={<ReloadOutlined />} onClick={() => q.refetch()}>
            刷新收件箱
          </Button>
        }
      />
      <Card>
        <div className="table-toolbar">
          <Space>
            <strong>收件箱</strong>
            <Tag>{q.data?.length || 0} 封邮件</Tag>
          </Space>
          <Input
            prefix={<SearchOutlined />}
            placeholder="搜索邮件主题"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            style={{ width: 280 }}
          />
        </div>
        <Load loading={q.isLoading} error={q.error}>
          <Table
            rowKey="id"
            dataSource={q.data?.filter((m) => m.subject.includes(search))}
            columns={[
              {
                title: "邮件主题",
                dataIndex: "subject",
                render: (s, r) => (
                  <Link className="request-link" to={`/mails/${r.id}`}>
                    {s}
                  </Link>
                ),
              },
              {
                title: "身份校验",
                dataIndex: "identity_verified",
                render: (v) => (
                  <Tag color={v ? "green" : "orange"} bordered={false}>
                    {v ? "已验证客户" : "待验证"}
                  </Tag>
                ),
              },
              {
                title: "处理状态",
                render: (_, r) => (
                  <StatusTag value={r.workflow_state || r.status} />
                ),
              },
              { title: "接收时间", dataIndex: "created_at", render: date },
              {
                title: "",
                render: (_, r) =>
                  r.workflow_id ? (
                    <Link to={`/workflows/${r.workflow_id}`}>
                      查看工作流 <ArrowRightOutlined />
                    </Link>
                  ) : (
                    <Link to={`/mails/${r.id}`}>查看详情</Link>
                  ),
              },
            ]}
          />
        </Load>
      </Card>
    </>
  );
}
export function MailDetailPage() {
  const { id } = useParams();
  const nav = useNavigate(),
    qc = useQueryClient();
  const user = useAuth((s) => s.user);
  const q = useQuery({
    queryKey: ["mail", id],
    queryFn: () => get(`/mails/${id}`),
  });
  const analyze = useMutation({
    mutationFn: () => post(`/mails/${id}/analyze`),
    onSuccess: (r) => {
      qc.invalidateQueries({ queryKey: ["mails"] });
      nav(`/workflows/${r.workflowId}`);
    },
    onError: (e) => message.error(e.message),
  });
  return (
    <>
      <PageTitle
        title="邮件详情"
        sub="阅读客户原始请求，核对身份后启动 AI 分析。"
        extra={<Button onClick={() => nav("/mails")}>返回收件箱</Button>}
      />
      <Load loading={q.isLoading} error={q.error}>
        {q.data && (
          <div className="detail-grid">
            <Card title={q.data.subject}>
              <Descriptions
                size="small"
                column={2}
                items={[
                  {
                    key: "time",
                    label: "接收时间",
                    children: date(q.data.created_at),
                  },
                  {
                    key: "verified",
                    label: "客户身份",
                    children: (
                      <Tag color="green">
                        {q.data.identity_verified ? "已验证" : "未验证"}
                      </Tag>
                    ),
                  },
                ]}
              />
              <div className="mail-body">{q.data.body}</div>
              <Tag bordered={false}>
                客户邮件内容是不可信输入，无法授予 Agent 执行权限
              </Tag>
            </Card>
            <Card
              className="ai-panel"
              title={
                <>
                  <RobotOutlined /> AI 处理助手
                </>
              }
            >
              <p>
                Agent 将识别意图、查询订单和业务数据，并生成可审查的处理建议。
              </p>
              <div className="mini-steps">
                <p>01 · 理解客户意图</p>
                <p>02 · 查询真实业务数据</p>
                <p>03 · 生成建议，等待你审批</p>
              </div>
              <Button
                block
                type="primary"
                size="large"
                loading={analyze.isPending}
                disabled={
                  !user?.permissions.includes("mails:analyze") ||
                  !q.data.identity_verified
                }
                onClick={() => analyze.mutate()}
              >
                启动 AI 分析 <ArrowRightOutlined />
              </Button>
              <small>写操作必须经过人工授权</small>
            </Card>
          </div>
        )}
      </Load>
    </>
  );
}
export function WorkflowsPage() {
  const q = useQuery({
    queryKey: ["workflows"],
    queryFn: () => get<Row[]>("/workflows"),
    refetchInterval: 4000,
  });
  return (
    <>
      <PageTitle
        title="Agent 工作流"
        sub="从邮件接收到处理完成，每一步都有迹可循。"
      />
      <Card>
        <Load loading={q.isLoading} error={q.error}>
          <Table
            rowKey="id"
            dataSource={q.data}
            columns={[
              {
                title: "客户请求",
                dataIndex: "subject",
                render: (s, r) => <Link to={`/workflows/${r.id}`}>{s}</Link>,
              },
              {
                title: "识别意图",
                dataIndex: "intent",
                render: (v) => v || "待分析",
              },
              {
                title: "状态",
                dataIndex: "state",
                render: (s) => <StatusTag value={s} />,
              },
              { title: "创建时间", dataIndex: "created_at", render: date },
              {
                title: "",
                render: (_, r) => (
                  <Link to={`/workflows/${r.id}`}>执行详情</Link>
                ),
              },
            ]}
          />
        </Load>
      </Card>
    </>
  );
}
export function OrdersPage() {
  const { id } = useParams();
  const [search, setSearch] = useState("");
  const q = useQuery({
    queryKey: ["orders", id],
    queryFn: () => get(id ? `/orders/${id}` : "/orders"),
  });
  return (
    <>
      <PageTitle
        title={id ? "订单详情" : "订单管理"}
        sub="订单数据由业务系统维护，所有变更均需通过审批工作流。"
      />
      <Card>
        <Load loading={q.isLoading} error={q.error}>
          {id && q.data ? (
            <Descriptions
              bordered
              column={2}
              items={Object.entries(q.data).map(([k, v]) => ({
                key: k,
                label: k,
                children:
                  k === "status" ? <StatusTag value={String(v)} /> : String(v),
              }))}
            />
          ) : (
            <>
              <Input
                prefix={<SearchOutlined />}
                placeholder="搜索订单编号"
                onChange={(e) => setSearch(e.target.value)}
                style={{ width: 300, marginBottom: 24 }}
              />
              <Table
                rowKey="id"
                dataSource={q.data?.filter((r: Row) => r.id.includes(search))}
                columns={[
                  {
                    title: "订单编号",
                    dataIndex: "id",
                    render: (s) => <Link to={`/orders/${s}`}>{s}</Link>,
                  },
                  { title: "商品", dataIndex: "product_name" },
                  {
                    title: "金额",
                    dataIndex: "total",
                    render: (v) => `¥ ${Number(v).toFixed(2)}`,
                  },
                  {
                    title: "订单状态",
                    dataIndex: "status",
                    render: (s) => <StatusTag value={s} />,
                  },
                  { title: "版本", dataIndex: "version" },
                  { title: "创建时间", dataIndex: "created_at", render: date },
                ]}
              />
            </>
          )}
        </Load>
      </Card>
    </>
  );
}
export function AuditPage() {
  const [page, setPage] = useState(1);
  const q = useQuery({
    queryKey: ["audit", page],
    queryFn: () => get<Row[]>(`/audit-logs?page=${page}`),
  });
  return (
    <>
      <PageTitle
        title="审计日志"
        sub="完整保留授权、审批与业务执行记录，支持关键操作追溯。"
      />
      <Card>
        <Load loading={q.isLoading} error={q.error}>
          <Table
            rowKey="id"
            pagination={false}
            dataSource={q.data}
            columns={[
              {
                title: "事件",
                dataIndex: "event_type",
                render: (s) => <Tag>{s}</Tag>,
              },
              { title: "操作主体", dataIndex: "actor_id" },
              { title: "摘要", dataIndex: "summary" },
              {
                title: "工作流",
                dataIndex: "workflow_id",
                render: (id) =>
                  id ? (
                    <Link to={`/workflows/${id}`}>{id.slice(0, 8)}…</Link>
                  ) : (
                    "—"
                  ),
              },
              { title: "时间", dataIndex: "created_at", render: date },
            ]}
          />
          <Space style={{ marginTop: 20 }}>
            <Button disabled={page === 1} onClick={() => setPage(page - 1)}>
              上一页
            </Button>
            <span>第 {page} 页</span>
            <Button
              disabled={!q.data || q.data.length < 50}
              onClick={() => setPage(page + 1)}
            >
              下一页
            </Button>
          </Space>
        </Load>
      </Card>
    </>
  );
}
export function ExceptionsPage() {
  const q = useQuery({
    queryKey: ["exceptions"],
    queryFn: () => get<Row[]>("/failed-tasks"),
    refetchInterval: 5000,
  });
  const user = useAuth((s) => s.user);
  return (
    <>
      <PageTitle
        title="异常任务"
        sub="安全优先。外部结果未知时先核对，避免重复退款或重复通知。"
      />
      <Card>
        <Load loading={q.isLoading} error={q.error}>
          <Table
            rowKey="id"
            dataSource={q.data}
            columns={[
              {
                title: "错误码",
                dataIndex: "error_code",
                render: (s) => <Tag color="red">{s}</Tag>,
              },
              { title: "说明", dataIndex: "summary" },
              {
                title: "状态",
                dataIndex: "resolved",
                render: (v) => (
                  <Tag color={v ? "green" : "orange"}>
                    {v ? "已解决" : "待处理"}
                  </Tag>
                ),
              },
              { title: "时间", dataIndex: "created_at", render: date },
              {
                title: "操作",
                render: (_, r) => (
                  <Space>
                    <Link to={`/workflows/${r.workflow_id}`}>查看详情</Link>
                    {!r.resolved &&
                      user?.permissions.includes("workflows:retry") && (
                        <Button
                          size="small"
                          onClick={async () => {
                            try {
                              await post(`/workflows/${r.workflow_id}/retry`);
                              message.success("已提交核对或安全重试任务");
                            } catch (e) {
                              message.error((e as Error).message);
                            }
                          }}
                        >
                          核对 / 重试
                        </Button>
                      )}
                  </Space>
                ),
              },
            ]}
          />
        </Load>
      </Card>
    </>
  );
}
export function UsersPage() {
  const qc = useQueryClient();
  const q = useQuery({
    queryKey: ["users"],
    queryFn: () => get<Row[]>("/users"),
  });
  const [edit, setEdit] = useState<Row | null>(null),
    [roles, setRoles] = useState<string[]>([]);
  return (
    <>
      <PageTitle
        title="用户与权限"
        sub="按角色分配职责，按团队隔离数据，所有变更记录审计。"
        extra={
          <Space>
            <CreateUserButton />
            <Link to="/admin/roles">
              <Button>查看角色权限</Button>
            </Link>
          </Space>
        }
      />
      <Card>
        <Load loading={q.isLoading} error={q.error}>
          <Table
            rowKey="id"
            dataSource={q.data}
            columns={[
              { title: "姓名", dataIndex: "display_name" },
              { title: "账号", dataIndex: "username" },
              {
                title: "角色",
                dataIndex: "roles",
                render: (rs: string[]) => rs.map((r) => <Tag key={r}>{r}</Tag>),
              },
              { title: "团队", dataIndex: "team_id" },
              {
                title: "启用状态",
                dataIndex: "enabled",
                render: (v) => (
                  <Tag color={v ? "green" : "default"}>
                    {v ? "已启用" : "已停用"}
                  </Tag>
                ),
              },
              {
                title: "操作",
                render: (_, r) => (
                  <Button
                    onClick={() => {
                      setEdit({ ...r });
                      setRoles(r.roles);
                    }}
                  >
                    编辑权限
                  </Button>
                ),
              },
            ]}
          />
        </Load>
      </Card>
      <Modal
        title="编辑用户权限"
        open={!!edit}
        onCancel={() => setEdit(null)}
        onOk={async () => {
          try {
            await http.put(`/users/${edit?.id}`, {
              enabled: edit?.enabled,
              roles,
            });
            message.success("已更新权限");
            setEdit(null);
            qc.invalidateQueries({ queryKey: ["users"] });
          } catch (e) {
            message.error((e as Error).message);
          }
        }}
      >
        <p>
          {edit?.display_name} · {edit?.username}
        </p>
        <Select
          mode="multiple"
          style={{ width: "100%" }}
          value={roles}
          onChange={setRoles}
          options={["ADMIN", "SUPERVISOR", "AGENT_OPERATOR", "AUDITOR"].map(
            (value) => ({ value, label: value }),
          )}
        />
        <p>
          账号启用{" "}
          <Switch
            checked={!!edit?.enabled}
            onChange={(enabled) => setEdit(edit ? { ...edit, enabled } : null)}
          />
        </p>
      </Modal>
    </>
  );
}
export function RolesPage() {
  const q = useQuery({
    queryKey: ["roles"],
    queryFn: () => get<Row[]>("/roles"),
  });
  return (
    <>
      <PageTitle
        title="角色与权限"
        sub="权限由服务端校验；界面可见性不能代替业务授权。"
      />
      <Card>
        <Load loading={q.isLoading} error={q.error}>
          <Table
            rowKey={(r) => `${r.id}-${r.permission}`}
            dataSource={q.data}
            columns={[
              { title: "角色", dataIndex: "code" },
              {
                title: "权限",
                dataIndex: "permission",
                render: (s) => <Tag color="purple">{s}</Tag>,
              },
            ]}
          />
        </Load>
      </Card>
    </>
  );
}
