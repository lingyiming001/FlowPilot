import { useState } from "react";
import { Button, Form, Input, Alert, Tag, Checkbox } from "antd";
import {
  ThunderboltFilled,
  LockOutlined,
  UserOutlined,
  ArrowRightOutlined,
  CheckCircleFilled,
} from "@ant-design/icons";
import { post } from "../api/client";
import { useAuth } from "../store/auth";
export function LoginPage() {
  const login = useAuth((s) => s.login);
  const [busy, setBusy] = useState(false),
    [error, setError] = useState("");
  return (
    <div className="login">
      <section className="login-story">
        <div className="brand">
          <span className="brand-icon">
            <ThunderboltFilled />
          </span>{" "}
          FlowPilot <Tag color="purple">MAILOPS AI</Tag>
        </div>
        <div className="story-content">
          <div className="eyebrow">让每一封邮件，都有下一步</div>
          <h1>
            把重复工作交给 AI，
            <br />
            把关键决策留给你。
          </h1>
          <p>
            从客户来信到订单处理，用可追踪、可审批的智能工作流，
            <br />
            让客服团队专注于更有价值的沟通。
          </p>
          <div className="story-flow">
            客户邮件 <ArrowRightOutlined /> AI 分析 <ArrowRightOutlined />{" "}
            人工审批 <ArrowRightOutlined /> 安全执行
          </div>
          <div className="story-benefits">
            <span>
              <CheckCircleFilled /> 每个决策有据可查
            </span>
            <span>
              <CheckCircleFilled /> 每次操作经过授权
            </span>
          </div>
        </div>
        <footer>ENTERPRISE MAIL OPERATIONS · BUILT FOR TRUST</footer>
      </section>
      <section className="login-form">
        <div className="login-box">
          <span className="eyebrow">WELCOME BACK</span>
          <h2>登录你的工作空间</h2>
          <p>连接团队，开始高效的一天。</p>
          {error && (
            <Alert
              type="error"
              message={error}
              showIcon
              style={{ marginBottom: 20 }}
            />
          )}
          <Form
            layout="vertical"
            initialValues={{ username: "admin", rememberMe: false }}
            onFinish={async (values) => {
              setBusy(true);
              setError("");
              try {
                const r = await post("/auth/login", values);
                login(r.token, r.user);
              } catch (e) {
                setError((e as Error).message);
              } finally {
                setBusy(false);
              }
            }}
          >
            <Form.Item
              name="username"
              label="账号"
              rules={[{ required: true, message: "请输入账号" }]}
            >
              <Input
                prefix={<UserOutlined />}
                size="large"
                placeholder="输入你的账号"
                autoComplete="username"
              />
            </Form.Item>
            <Form.Item
              name="password"
              label="密码"
              rules={[{ required: true, message: "请输入密码" }]}
            >
              <Input.Password
                prefix={<LockOutlined />}
                size="large"
                placeholder="输入你的密码"
                autoComplete="current-password"
              />
            </Form.Item>
            <Form.Item name="rememberMe" valuePropName="checked">
              <Checkbox>记住我（30 天）</Checkbox>
            </Form.Item>
            <Button
              type="primary"
              size="large"
              htmlType="submit"
              block
              loading={busy}
            >
              进入工作台 <ArrowRightOutlined />
            </Button>
          </Form>
          <div className="login-note">
            本地演示账号：admin / supervisor / operator / auditor
            <br />
            密码见部署环境文件，由启动脚本随机生成。
          </div>
        </div>
        <small>权限隔离 · 审批留痕 · 数据加密</small>
      </section>
    </div>
  );
}
