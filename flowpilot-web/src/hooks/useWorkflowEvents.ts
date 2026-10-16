import { useEffect } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useAuth } from "../store/auth";
export function useWorkflowEvents(id?: string) {
  const token = useAuth((s) => s.token);
  const query = useQueryClient();
  useEffect(() => {
    if (!id || !token) return;
    const controller = new AbortController();
    let cursor = "",
      timer: ReturnType<typeof setTimeout>;
    let delay = 1500;
    async function sync() {
      try {
        const response = await fetch(`/api/workflows/${id}/events`, {
          headers: {
            Authorization: `Bearer ${token}`,
            ...(cursor ? { "Last-Event-ID": cursor } : {}),
          },
          signal: controller.signal,
        });
        if (response.status === 401 || response.status === 403) return;
        if (!response.ok || !response.body) throw new Error("SSE unavailable");
        const reader = response.body.getReader(),
          decoder = new TextDecoder();
        let buffer = "";
        while (true) {
          const { done, value } = await reader.read();
          if (done) break;
          buffer += decoder.decode(value, { stream: true });
          let boundary;
          while ((boundary = buffer.indexOf("\n\n")) >= 0) {
            const event = buffer.slice(0, boundary);
            buffer = buffer.slice(boundary + 2);
            for (const line of event.split("\n"))
              if (line.startsWith("id:")) cursor = line.slice(3).trim();
            if (event.includes("event:transition"))
              void query.invalidateQueries({ queryKey: ["workflow", id] });
          }
        }
        delay = 1500;
      } catch {
        delay = Math.min(delay * 2, 15000);
      }
      if (!controller.signal.aborted) timer = setTimeout(sync, delay);
    }
    void sync();
    return () => {
      controller.abort();
      clearTimeout(timer);
    };
  }, [id, token, query]);
}
