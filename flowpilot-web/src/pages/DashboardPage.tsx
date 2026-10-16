import { useEffect, useRef } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link, useNavigate } from "react-router-dom";
import { Button, Card, Table, Tag } from "antd";
import {
  MailOutlined,
  AuditOutlined,
  CheckCircleOutlined,
  WarningOutlined,
  ArrowRightOutlined,
  ThunderboltFilled,
} from "@ant-design/icons";
import * as echarts from "echarts";
import { get } from "../api/client";
import { useAuth } from "../store/auth";
import { StatusTag } from "../components/StatusTag";
import { Load, PageTitle, date } from "../components/Shared";
import type { Row } from "../types";
function StateChart({ states }: { states: Row[] }) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!ref.current) return;
    const chart = echarts.init(ref.current);
    const groups = [
      {
        name: "已完成",
        value: states
          .filter((s) => s.state === "COMPLETED")
          .reduce((n, s) => n + s.count, 0),
        itemStyle: { color: "#7763ee" },
      },
      {
        name: "待审批",
        value: states
          .filter((s) => s.state === "WAITING_APPROVAL")
          .reduce((n, s) => n + s.count, 0),
        itemStyle: { color: "#f3bc64" },
      },
      {
        name: "其他处理中",
        value: states
          .filter((s) => !["COMPLETED", "WAITING_APPROVAL"].includes(s.state))
          .reduce((n, s) => n + s.count, 0),
        itemStyle: { color: "#95d8c6" },
      },
    ];
    const total = groups.reduce((n, g) => n + g.value, 0);
    chart.setOption({
      tooltip: { trigger: "item" },
      title: {
        text: String(total),
        subtext: "工作流总数",
        left: "center",
        top: "34%",
        textStyle: { fontSize: 34, color: "#24223c" },
        subtextStyle: { fontSize: 12 },
      },
      series: [
        {
          type: "pie",
          radius: ["65%", "85%"],
          center: ["50%", "49%"],
          label: { show: false },
          data: groups,
          padAngle: 5,
          itemStyle: { borderRadius: 6 },
        },
      ],
    });
    const resize = () => chart.resize();
    window.addEventListener("resize", resize);
    return () => {
      chart.dispose();
      window.removeEventListener("resize", resize);
    };
  }, [states]);
  return <div ref={ref} style={{ height: 240 }} />;
}
export function DashboardPage() {
  const user = useAuth((s) => s.user),
    nav = useNavigate();
  const q = useQuery({
    queryKey: ["dashboard"],
    queryFn: () => get("/dashboard"),
    refetchInterval: 5000,
  });
  const d = q.data;
  const completed =
    d?.workflowStates
      ?.filter((s: Row) => s.state === "COMPLETED")
      .reduce((n: number, s: Row) => n + s.count, 0) || 0;
  return (
    <>
      <PageTitle
        title={`你好，${user?.displayName || "伙伴"} 👋`}
        sub="每一封邮件，都在有序前进。这里是你的智能客服工作台。"
        extra={
          <Button
            icon={<MailOutlined />}
            type="primary"
            onClick={() => nav("/mails")}
          >
            处理客户邮件
          </Button>
        }
      />
      <Load loading={q.isLoading} error={q.error}>
        {d && (
          <>
            <div className="stats-grid">
              {[
                {
                  title: "客户邮件",
                  value: d.mailCount,
                  icon: <MailOutlined />,
                  note: "已安全接收的客户来信",
                  color: "purple",
                },
                {
                  title: "待人工审批",
                  value: d.pendingApprovals,
                  icon: <AuditOutlined />,
                  note: "等待你的专业判断",
                  color: "amber",
                },
                {
                  title: "已完成工作流",
                  value: completed,
                  icon: <CheckCircleOutlined />,
                  note: "完整记录每一次处理",
                  color: "green",
                },
                {
                  title: "需关注任务",
                  value: d.failedCount,
                  icon: <WarningOutlined />,
                  note: "异常任务与人工接管",
                  color: "rose",
                },
              ].map((s) => (
                <Card key={s.title} className="stat-card">
                  <div className="stat-label">
                    {s.title}
                    <span className={`stat-icon ${s.color}`}>{s.icon}</span>
                  </div>
                  <strong>{s.value}</strong>
                  <p>{s.note}</p>
                </Card>
              ))}
            </div>
            <div className="dashboard-grid">
              <Card
                title={
                  <span>
                    工作流动态 <Tag bordered={false}>实时</Tag>
                  </span>
                }
                extra={
                  <Link to="/workflows">
                    查看全部 <ArrowRightOutlined />
                  </Link>
                }
              >
                <Table<Row>
                  rowKey="id"
                  size="middle"
                  pagination={{ pageSize: 5, hideOnSinglePage: true }}
                  dataSource={d.recent}
                  locale={{
                    emptyText: "从客户邮件启动一次分析，工作流将在这里出现",
                  }}
                  columns={[
                    {
                      title: "客户请求",
                      dataIndex: "subject",
                      render: (s, id) => (
                        <Link
                          className="request-link"
                          to={`/workflows/${id.id}`}
                        >
                          <span className="small-icon">
                            <MailOutlined />
                          </span>
                          {s}
                        </Link>
                      ),
                    },
                    {
                      title: "当前状态",
                      dataIndex: "state",
                      render: (s) => <StatusTag value={s} />,
                    },
                    {
                      title: "接收时间",
                      dataIndex: "created_at",
                      render: date,
                    },
                  ]}
                />
              </Card>
              <Card title="处理状态分布">
                <StateChart states={d.workflowStates} />
                <div className="chart-legend">
                  <span>
                    <i style={{ background: "#7763ee" }} />
                    已完成
                  </span>
                  <span>
                    <i style={{ background: "#f3bc64" }} />
                    待审批
                  </span>
                  <span>
                    <i style={{ background: "#95d8c6" }} />
                    处理中
                  </span>
                </div>
              </Card>
            </div>
            <Card className="welcome-banner">
              <div className="banner-icon">
                <ThunderboltFilled />
              </div>
              <div>
                <h3>AI 加速处理，人类掌握决策</h3>
                <p>
                  只读查询自动执行。退款、订单变更与客户邮件，每一步都经过独立审批。
                </p>
              </div>
              <Button onClick={() => nav("/approvals")}>
                前往审批中心 <ArrowRightOutlined />
              </Button>
            </Card>
          </>
        )}
      </Load>
    </>
  );
}
