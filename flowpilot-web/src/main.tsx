import React, { useMemo } from "react";
import ReactDOM from "react-dom/client";
import { ConfigProvider, App as AntApp } from "antd";
import zhCN from "antd/locale/zh_CN";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import App from "./App";
import { useAuth } from "./store/auth";
import "./style.css";
function SessionQueries() {
  const token = useAuth((s) => s.token);
  const queryClient = useMemo(
    () =>
      new QueryClient({
        defaultOptions: { queries: { retry: 1, staleTime: 2000 } },
      }),
    [token],
  );
  return (
    <QueryClientProvider client={queryClient}>
      <App />
    </QueryClientProvider>
  );
}
ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <ConfigProvider
      locale={zhCN}
      theme={{
        token: {
          colorPrimary: "#7660dd",
          borderRadius: 9,
          fontFamily:
            'Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", sans-serif',
          colorBgLayout: "#f6f7fb",
          colorText: "#28283e",
          colorBorderSecondary: "#eeedf4",
        },
        components: {
          Card: { headerFontSize: 15, headerHeight: 62 },
          Table: {
            headerBg: "#fafafe",
            headerColor: "#828292",
            cellPaddingBlock: 18,
          },
          Button: { primaryShadow: "0 4px 10px #7660dd20" },
        },
      }}
    >
      <AntApp>
        <SessionQueries />
      </AntApp>
    </ConfigProvider>
  </React.StrictMode>,
);
