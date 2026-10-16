import { useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { Button, Modal, Radio, Input, Alert, message } from "antd";
import { post } from "../api/client";
import { useAuth } from "../store/auth";
import type { Row } from "../types";
export function ReconcileButton({
  workflowId,
  execution,
}: {
  workflowId: string;
  execution: Row;
}) {
  const user = useAuth((s) => s.user);
  const query = useQueryClient();
  const [open, setOpen] = useState(false),
    [outcome, setOutcome] = useState("SUCCESS"),
    [evidence, setEvidence] = useState(""),
    [busy, setBusy] = useState(false);
  if (
    !user?.roles.includes("SUPERVISOR") ||
    !["UNKNOWN", "DISPATCHING"].includes(execution.status)
  )
    return null;
  return (
    <>
      <Button size="small" onClick={() => setOpen(true)}>
        登记人工核对结果
      </Button>
      <Modal
        title="登记经外部核实的执行结果"
        open={open}
        onCancel={() => setOpen(false)}
        confirmLoading={busy}
        okButtonProps={{ disabled: evidence.trim().length < 12 }}
        onOk={async () => {
          setBusy(true);
          try {
            await post(
              `/workflows/${workflowId}/executions/${execution.id}/reconcile`,
              { expectedVersion: execution.version, outcome, evidence },
            );
            message.success("已记录核对证据并恢复后续处理");
            setOpen(false);
            setEvidence("");
            void query.invalidateQueries();
          } catch (e) {
            message.error((e as Error).message);
          } finally {
            setBusy(false);
          }
        }}
      >
        <Alert
          showIcon
          type="warning"
          message="请先在支付系统或邮件服务核实原请求结果。此操作登记事实，不会重新发起外部操作。"
        />
        <Radio.Group
          style={{ margin: "20px 0" }}
          value={outcome}
          onChange={(e) => setOutcome(e.target.value)}
          options={[
            { label: "已确认成功", value: "SUCCESS" },
            { label: "已确认失败", value: "FAILED" },
          ]}
        />
        <Input.TextArea
          rows={4}
          maxLength={1000}
          placeholder="填写核对工单、提供方回执等凭据引用（至少12字符，避免填写敏感信息）"
          value={evidence}
          onChange={(e) => setEvidence(e.target.value)}
        />
      </Modal>
    </>
  );
}
