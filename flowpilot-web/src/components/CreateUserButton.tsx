import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Button, Form, Input, Modal, Select, message } from "antd";
import { PlusOutlined } from "@ant-design/icons";
import { post } from "../api/client";

export function CreateUserButton() {
  const [open, setOpen] = useState(false);
  const [form] = Form.useForm();
  const query = useQueryClient();
  const mutation = useMutation({
    mutationFn: (values: unknown) => post("/users", values),
    onSuccess: () => {
      message.success("用户已创建");
      form.resetFields();
      setOpen(false);
      void query.invalidateQueries({ queryKey: ["users"] });
    },
    onError: (error) => message.error(error.message),
  });
  return (
    <>
      <Button
        type="primary"
        icon={<PlusOutlined />}
        onClick={() => setOpen(true)}
      >
        新增用户
      </Button>
      <Modal
        title="新增团队用户"
        open={open}
        onCancel={() => {
          form.resetFields();
          setOpen(false);
        }}
        onOk={() => form.submit()}
        confirmLoading={mutation.isPending}
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={(values) => mutation.mutate(values)}
        >
          <Form.Item
            name="username"
            label="登录账号"
            rules={[
              {
                required: true,
                pattern: /^[a-zA-Z0-9_.-]{3,64}$/,
                message: "使用3至64位字母、数字、点、下划线或连字符",
              },
            ]}
          >
            <Input autoComplete="off" />
          </Form.Item>
          <Form.Item
            name="displayName"
            label="姓名"
            rules={[{ required: true, max: 100 }]}
          >
            <Input />
          </Form.Item>
          <Form.Item
            name="password"
            label="初始密码"
            rules={[
              { required: true, min: 12, max: 72, message: "密码需为12至72位" },
            ]}
          >
            <Input.Password autoComplete="new-password" />
          </Form.Item>
          <Form.Item
            name="roles"
            label="角色"
            rules={[{ required: true, type: "array", min: 1 }]}
          >
            <Select
              mode="multiple"
              options={["ADMIN", "SUPERVISOR", "AGENT_OPERATOR", "AUDITOR"].map(
                (value) => ({ value, label: value }),
              )}
            />
          </Form.Item>
          <p className="muted">
            新用户自动归属你的团队，权限由后端校验。ADMIN管理权限与业务审批权限分别授予。
          </p>
        </Form>
      </Modal>
    </>
  );
}
