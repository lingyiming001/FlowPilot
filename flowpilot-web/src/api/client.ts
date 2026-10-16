import axios from "axios";
import { useAuth } from "../store/auth";
import type { Envelope, User } from "../types";
export const http = axios.create({ baseURL: "/api", timeout: 55000 });
const sessions = axios.create({
  baseURL: "/api/auth",
  timeout: 15000,
  headers: { "X-FlowPilot-Session": "1" },
});
let refreshing: Promise<void> | null = null;
export function refreshSession(): Promise<void> {
  if (!refreshing)
    refreshing = sessions
      .post<Envelope<{ token: string; user: User }>>("/refresh")
      .then((r) => {
        useAuth.getState().login(r.data.data.token, r.data.data.user);
      })
      .catch((e) => {
        if (e.response?.status === 401) useAuth.getState().logout();
        throw e;
      })
      .finally(() => {
        refreshing = null;
      });
  return refreshing;
}
export async function endSession() {
  try {
    await sessions.post("/logout");
    useAuth.getState().logout();
  } catch (e) {
    throw new Error(
      axios.isAxiosError(e)
        ? e.response?.data?.message || "退出失败，请重试"
        : "退出失败，请重试",
    );
  }
}
http.interceptors.request.use((c) => {
  const token = useAuth.getState().token;
  if (token) c.headers.Authorization = `Bearer ${token}`;
  return c;
});
http.interceptors.response.use(
  (r) => r,
  async (e) => {
    const c = e.config;
    if (
      e.response?.status === 401 &&
      c &&
      !c._retried &&
      !c.url?.startsWith("/auth/login")
    ) {
      c._retried = true;
      try {
        await refreshSession();
        return await http.request(c);
      } catch (error) {
        if (axios.isAxiosError(error))
          throw new Error(
            error.response?.data?.message || "会话恢复失败，请重试",
          );
        throw error;
      }
    }
    throw new Error(e.response?.data?.message || "请求失败，请检查服务连接");
  },
);
export async function get<T = any>(path: string): Promise<T> {
  return (await http.get<Envelope<T>>(path)).data.data;
}
export async function post<T = any>(
  path: string,
  data: unknown = {},
): Promise<T> {
  return (await http.post<Envelope<T>>(path, data)).data.data;
}
