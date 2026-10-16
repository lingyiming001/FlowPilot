import { useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { Button, Modal, Input, Alert, message } from "antd";
import { post } from "../api/client";
import { useAuth } from "../store/auth";
import type { Row } from "../types";
export function CancelWorkflowButton({ workflow }: { workflow: Row }) {
  const user = useAuth((s) => s.user);
  const query = useQueryClient();
  const [open, setOpen] = useState(false),
    [comment, setComment] = useState(""),
    [busy, setBusy] = useState(false);
  if (
    !user?.permissions.includes("workflows:retry") ||
    ![
      "RECEIVED",
      "WAITING_APPROVAL",
      "RETRY_PENDING",
      "MANUAL_REVIEW",
    ].includes(workflow.state)
  )
    return null;
  return (
    <>
      <Button danger onClick={() => setOpen(true)}>
        终止工作流
      </Button>
      <Modal
        title="终止此工作流"
        open={open}
        onCancel={() => setOpen(false)}
        confirmLoading={busy}
        okButtonProps={{ disabled: !comment.trim() }}
        onOk={async () => {
          setBusy(true);
          try {
            await post(`/workflows/${workflow.id}/cancel`, {
              expectedVersion: workflow.version,
              comment,
            });
            message.success("工作流已终止");
            setOpen(false);
            void query.invalidateQueries();
          } catch (e) {
            message.error((e as Error).message);
          } finally {
            setBusy(false);
          }
        }}
      >
        <Alert
          type="warning"
          message="将使未处理审批失效并终止待执行任务；存在外部结果未知的操作时必须先完成核对。"
        />
        <Input.TextArea
          style={{ marginTop: 20 }}
          rows={3}
          maxLength={1000}
          value={comment}
          onChange={(e) => setComment(e.target.value)}
          placeholder="填写终止原因"
        />
      </Modal>
    </>
  );
}
