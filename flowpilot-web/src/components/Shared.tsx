import { Alert, Empty, Spin } from "antd";
import type { ReactNode } from "react";
export function Load({
  loading,
  error,
  children,
}: {
  loading: boolean;
  error: Error | null;
  children: ReactNode;
}) {
  if (loading)
    return (
      <div className="loading">
        <Spin size="large" />
      </div>
    );
  if (error)
    return (
      <Alert
        type="error"
        showIcon
        message="无法加载"
        description={error.message}
      />
    );
  return <>{children}</>;
}
export function JsonView({ value }: { value: unknown }) {
  let data = value;
  if (typeof value === "string")
    try {
      data = JSON.parse(value);
    } catch {}
  return <pre className="json">{JSON.stringify(data, null, 2)}</pre>;
}
export function PageTitle({
  title,
  sub,
  extra,
}: {
  title: string;
  sub: string;
  extra?: ReactNode;
}) {
  return (
    <div className="page-title">
      <div>
        <h1>{title}</h1>
        <p>{sub}</p>
      </div>
      {extra}
    </div>
  );
}
export function NoData() {
  return <Empty description="暂无记录" />;
}
export const date = (s: unknown) =>
  s ? new Date(String(s)).toLocaleString("zh-CN", { hour12: false }) : "—";
