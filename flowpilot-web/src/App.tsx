import { useEffect, useState } from "react";
import {
  BrowserRouter,
  Routes,
  Route,
  NavLink,
  Navigate,
  useLocation,
} from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { Avatar, Button, Dropdown, Tag, Tooltip, Result, message } from "antd";
import {
  ThunderboltFilled,
  AppstoreOutlined,
  MailOutlined,
  ApartmentOutlined,
  AuditOutlined,
  ShoppingOutlined,
  WarningOutlined,
  FileProtectOutlined,
  TeamOutlined,
  LogoutOutlined,
  QuestionCircleOutlined,
  DownOutlined,
  SafetyCertificateOutlined,
} from "@ant-design/icons";
import { get, refreshSession, endSession } from "./api/client";
import { useAuth } from "./store/auth";
import { ModelSettingsPage } from "./pages/ModelSettingsPage";
import { LoginPage } from "./pages/LoginPage";
import { DashboardPage } from "./pages/DashboardPage";
import {
  MailsPage,
  MailDetailPage,
  WorkflowsPage,
  OrdersPage,
  AuditPage,
  ExceptionsPage,
  UsersPage,
  RolesPage,
} from "./pages/ListPages";
import {
  WorkflowPage,
  ApprovalCenterPage,
  ProposalReviewPage,
} from "./pages/WorkflowPage";
import type { ReactNode } from "react";
function Guard({
  permission,
  children,
}: {
  permission: string;
  children: ReactNode;
}) {
  const user = useAuth((s) => s.user);
  if (!user?.permissions.includes(permission))
    return (
      <Result
        status="403"
        title="无权访问"
        subTitle="此页面需要额外的角色权限。"
      />
    );
  return <>{children}</>;
}
function Workspace() {
  const location = useLocation();
  const { token, user, login } = useAuth();
  const [booting, setBooting] = useState(!token);
  const [sessionError, setSessionError] = useState("");
  async function restore() {
    setBooting(true);
    setSessionError("");
    try {
      await refreshSession();
    } catch (e: any) {
      if (e.response?.status !== 401)
        setSessionError("恢复登录失败，请检查连接后重试");
    } finally {
      setBooting(false);
    }
  }
  useEffect(() => {
    if (!useAuth.getState().token) void restore();
  }, []);
  const me = useQuery({
    queryKey: ["me", token],
    queryFn: () => get("/auth/me"),
    enabled: !!token,
    retry: false,
  });
  const info = useQuery({
    queryKey: ["info", token],
    queryFn: () => get("/system/info"),
    enabled: !!token,
  });
  useEffect(() => {
    if (me.data && token) login(token, me.data);
  }, [me.data, token, login]);
  if (booting) return <div className="loading">正在恢复登录…</div>;
  if (sessionError && !token)
    return (
      <Result
        status="warning"
        title={sessionError}
        extra={<Button onClick={() => void restore()}>重试</Button>}
      />
    );
  if (!token) return <LoginPage />;
  if (!user) return <div className="loading">正在连接工作空间…</div>;
  const items = [
    {
      to: "/dashboard",
      icon: <AppstoreOutlined />,
      label: "工作台",
      p: "workflows:read",
    },
    {
      to: "/mails",
      icon: <MailOutlined />,
      label: "客户邮件",
      p: "mails:read",
    },
    {
      to: "/workflows",
      icon: <ApartmentOutlined />,
      label: "Agent 工作流",
      p: "workflows:read",
    },
    {
      to: "/approvals",
      icon: <AuditOutlined />,
      label: "人工审批",
      p: "workflows:read",
    },
    {
      to: "/orders",
      icon: <ShoppingOutlined />,
      label: "订单管理",
      p: "orders:read",
    },
    {
      to: "/exceptions",
      icon: <WarningOutlined />,
      label: "异常任务",
      p: "workflows:read",
    },
    {
      to: "/audit-logs",
      icon: <FileProtectOutlined />,
      label: "审计日志",
      p: "audit:read",
    },
    {
      to: "/admin/users",
      icon: <TeamOutlined />,
      label: "用户与权限",
      p: "users:manage",
    },
  ];
  items.push({
    to: "/admin/models",
    icon: <ThunderboltFilled />,
    label: "模型与用量",
    p: "models:manage",
  });
  const current = items.find((i) => location.pathname.startsWith(i.to));
  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <span className="brand-icon">
            <ThunderboltFilled />
          </span>
          FlowPilot
          <span className="brand-dot" />
        </div>
        <div className="workspace-label">ENTERPRISE WORKSPACE</div>
        <div className="workspace-picker">
          <span className="workspace-avatar">F</span>
          <div>
            <strong>客户服务中心</strong>
            <small>Enterprise MailOps</small>
          </div>
          <DownOutlined />
        </div>
        <div className="nav-label">工作空间</div>
        <nav>
          {items
            .filter((i) => user.permissions.includes(i.p))
            .map((i) => (
              <NavLink key={i.to} to={i.to}>
                {i.icon}
                <span>{i.label}</span>
                {i.to === "/approvals" && <span className="nav-dot" />}
              </NavLink>
            ))}
        </nav>
        <div className="sidebar-bottom">
          <div className="trust-card">
            <SafetyCertificateOutlined />
            <strong>安全始终在线</strong>
            <p>
              AI 智能辅助，人类审批决策。
              <br />
              每一步都有完整记录。
            </p>
          </div>
          <span className="sidebar-version">
            <span className="online-dot" /> 系统运行中 <small>v1.0.0</small>
          </span>
        </div>
      </aside>
      <main className="main-shell">
        <header className="topbar">
          <span>
            工作空间 <span className="slash">/</span>
            <strong>{current?.label || "详情"}</strong>
          </span>
          <div className="topbar-right">
            <Tag
              bordered={false}
              color={info.data?.llmMode === "FAKE" ? "gold" : "green"}
            >
              {info.data?.llmMode === "MANAGED"
                ? "配置路由"
                : info.data?.llmMode === "REAL"
                  ? "真实模型"
                  : "Fake LLM 演示"}{" "}
              ·{" "}
              {info.data?.integrationMode === "real"
                ? "真实集成"
                : "Mock 业务适配"}
            </Tag>
            <Tooltip title="帮助文档见项目 README">
              <QuestionCircleOutlined />
            </Tooltip>
            <span className="topbar-divider" />
            <Dropdown
              menu={{
                items: [
                  {
                    key: "logout",
                    icon: <LogoutOutlined />,
                    label: "退出登录",
                    onClick: () => {
                      void endSession().catch((e) => message.error(e.message));
                    },
                  },
                ],
              }}
            >
              <Button type="text">
                <Avatar
                  size={30}
                  style={{ background: "#eae5ff", color: "#7159da" }}
                >
                  {user.displayName[0]}
                </Avatar>
                <span>{user.displayName}</span>
                <DownOutlined style={{ fontSize: 10 }} />
              </Button>
            </Dropdown>
          </div>
        </header>
        <div className="page-content">
          <Routes>
            <Route
              path="/"
              element={
                <Navigate
                  to={
                    user.permissions.includes("workflows:read")
                      ? "/dashboard"
                      : "/admin/users"
                  }
                  replace
                />
              }
            />
            <Route
              path="/dashboard"
              element={
                <Guard permission="workflows:read">
                  <DashboardPage />
                </Guard>
              }
            />
            <Route
              path="/mails"
              element={
                <Guard permission="mails:read">
                  <MailsPage />
                </Guard>
              }
            />
            <Route
              path="/mails/:id"
              element={
                <Guard permission="mails:read">
                  <MailDetailPage />
                </Guard>
              }
            />
            <Route
              path="/workflows"
              element={
                <Guard permission="workflows:read">
                  <WorkflowsPage />
                </Guard>
              }
            />
            <Route
              path="/workflows/:id"
              element={
                <Guard permission="workflows:read">
                  <WorkflowPage />
                </Guard>
              }
            />
            <Route
              path="/workflows/:id/tool-calls"
              element={
                <Guard permission="workflows:read">
                  <WorkflowPage />
                </Guard>
              }
            />
            <Route
              path="/proposals/:id"
              element={
                <Guard permission="workflows:read">
                  <ProposalReviewPage />
                </Guard>
              }
            />
            <Route
              path="/approvals"
              element={
                <Guard permission="workflows:read">
                  <ApprovalCenterPage />
                </Guard>
              }
            />
            <Route
              path="/orders"
              element={
                <Guard permission="orders:read">
                  <OrdersPage />
                </Guard>
              }
            />
            <Route
              path="/orders/:id"
              element={
                <Guard permission="orders:read">
                  <OrdersPage />
                </Guard>
              }
            />
            <Route
              path="/exceptions"
              element={
                <Guard permission="workflows:read">
                  <ExceptionsPage />
                </Guard>
              }
            />
            <Route
              path="/audit-logs"
              element={
                <Guard permission="audit:read">
                  <AuditPage />
                </Guard>
              }
            />
            <Route
              path="/admin/users"
              element={
                <Guard permission="users:manage">
                  <UsersPage />
                </Guard>
              }
            />
            <Route
              path="/admin/roles"
              element={
                <Guard permission="users:manage">
                  <RolesPage />
                </Guard>
              }
            />
            <Route
              path="/admin/models"
              element={
                <Guard permission="models:manage">
                  <ModelSettingsPage />
                </Guard>
              }
            />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
          <footer className="page-footer">
            FlowPilot · 让服务更有温度{" "}
            <span>
              <SafetyCertificateOutlined /> 数据加密与操作审计已启用
            </span>
          </footer>
        </div>
      </main>
    </div>
  );
}
export default function App() {
  return (
    <BrowserRouter>
      <Workspace />
    </BrowserRouter>
  );
}
