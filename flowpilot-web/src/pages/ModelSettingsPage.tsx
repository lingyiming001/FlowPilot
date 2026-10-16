import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Alert,
  Button,
  Card,
  Form,
  Input,
  InputNumber,
  Modal,
  Progress,
  Select,
  Space,
  Switch,
  Table,
  Tabs,
  Tag,
  message,
} from "antd";
import { PlusOutlined, ReloadOutlined } from "@ant-design/icons";
import { get, post, http } from "../api/client";
import type { Row } from "../types";
const routeNames: Record<string, string> = {
  INTENT_ANALYSIS: "意图分析（首轮）",
  TOOL_REASONING: "工具推理（后续轮次）",
  DEFAULT: "默认路由",
};
const status = (value: boolean) => (
  <Tag color={value ? "green" : "default"}>{value ? "启用" : "停用"}</Tag>
);
const number = (n: number) => (n || 0).toLocaleString();
export function ModelSettingsPage() {
  const query = useQuery({
    queryKey: ["model-settings"],
    queryFn: () => get("/model-settings"),
    refetchInterval: 15000,
  });
  const client = useQueryClient();
  const [editor, setEditor] = useState<{ kind: string; row?: Row } | null>(
    null,
  );
  const [preview, setPreview] = useState<Row | null>(null);
  const [reconcile, setReconcile] = useState<Row | null>(null);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();
  const [reconcileForm] = Form.useForm();
  const refresh = () => {
    void client.invalidateQueries({ queryKey: ["model-settings"] });
    void client.invalidateQueries({ queryKey: ["info"] });
  };
  const d = query.data;
  function edit(kind: string, row?: Row) {
    setEditor({ kind, row });
    const initial: Row = {
      version: row?.version || 0,
      enabled: row?.enabled ?? true,
    };
    if (kind === "providers")
      Object.assign(initial, {
        name: row?.name || "",
        type: row?.type || "OPENAI_COMPATIBLE",
        baseUrl: row?.base_url || "",
        chatPath: row?.chat_path || "/v1/chat/completions",
        timeoutSeconds: row?.timeout_seconds || 25,
        apiKey: "",
        clearApiKey: false,
      });
    if (kind === "models")
      Object.assign(initial, {
        name: row?.name || "",
        providerId: row?.provider_id,
        modelName: row?.model_name || "",
        temperature: row ? row.temperature : 0.1,
        contextWindow: row?.context_window || 128000,
        maxInputTokens: row?.max_input_tokens || 60000,
        maxOutputTokens: row?.max_output_tokens || 4096,
        outputParameter: row?.output_parameter || "MAX_TOKENS",
      });
    if (kind === "routes")
      Object.assign(initial, {
        routeKey: row?.route_key || "DEFAULT",
        modelId: row?.model_id,
        priority: row?.priority ?? 100,
      });
    form.resetFields();
    form.setFieldsValue(initial);
  }
  async function save() {
    try {
      const values = await form.validateFields();
      setSaving(true);
      const path = `/model-settings/${editor!.kind}${editor!.row ? `/${editor!.row.id}` : ""}`;
      if (editor!.row) await http.put(path, values);
      else await post(path, values);
      message.success("配置已保存");
      setEditor(null);
      refresh();
    } catch (e: any) {
      if (e.message) message.error(e.message);
    } finally {
      setSaving(false);
    }
  }
  if (query.isPending) return <Card loading />;
  if (query.error)
    return (
      <Alert
        type="error"
        message={query.error.message}
        action={<Button onClick={() => query.refetch()}>重试</Button>}
      />
    );
  if (!d) return null;
  const models = d.models as Row[],
    providers = d.providers as Row[];
  const used = Number(d.usage.used_tokens),
    reserved = Number(d.usage.reserved_tokens),
    limit = Number(d.settings.monthly_token_limit);
  const modelLabel = (id: string) =>
    models.find((m) => m.id === id)?.name || id;
  const providerLabel = (id: string) =>
    providers.find((p) => p.id === id)?.name || id;
  return (
    <>
      <div className="page-heading">
        <div>
          <h1>模型与用量</h1>
          <p>管理团队模型接入、任务路由和每月 Token 额度。</p>
        </div>
        <Button icon={<ReloadOutlined />} onClick={() => query.refetch()}>
          刷新
        </Button>
      </div>
      <Alert
        showIcon
        type="info"
        style={{ marginBottom: 20 }}
        message={
          d.settings.routing_enabled
            ? "配置路由已生效：首轮选择意图分析，后续选择工具推理；未匹配时使用默认路由。"
            : "当前使用部署环境模型。先配置供应商、模型与路由，再在额度设置中启用配置路由。"
        }
      />
      <Tabs
        items={[
          {
            key: "providers",
            label: "模型供应商",
            children: (
              <Card
                extra={
                  <Button
                    type="primary"
                    icon={<PlusOutlined />}
                    onClick={() => edit("providers")}
                  >
                    新增供应商
                  </Button>
                }
              >
                <Table<Row>
                  rowKey="id"
                  dataSource={providers}
                  pagination={false}
                  scroll={{ x: 800 }}
                  columns={[
                    { title: "名称", dataIndex: "name" },
                    { title: "类型", dataIndex: "type" },
                    { title: "Base URL", dataIndex: "base_url" },
                    {
                      title: "密钥",
                      dataIndex: "has_api_key",
                      render: (v) => (v ? "已设置 · 加密保存" : "未设置"),
                    },
                    { title: "状态", dataIndex: "enabled", render: status },
                    {
                      title: "操作",
                      render: (_, r) => (
                        <Button
                          type="link"
                          onClick={() => edit("providers", r)}
                        >
                          编辑
                        </Button>
                      ),
                    },
                  ]}
                />
              </Card>
            ),
          },
          {
            key: "models",
            label: "模型配置",
            children: (
              <Card
                extra={
                  <Button
                    type="primary"
                    icon={<PlusOutlined />}
                    disabled={!providers.length}
                    onClick={() => edit("models")}
                  >
                    新增模型
                  </Button>
                }
              >
                <Table<Row>
                  rowKey="id"
                  dataSource={models}
                  pagination={false}
                  scroll={{ x: 900 }}
                  columns={[
                    { title: "名称", dataIndex: "name" },
                    {
                      title: "供应商",
                      dataIndex: "provider_id",
                      render: providerLabel,
                    },
                    { title: "模型 ID", dataIndex: "model_name" },
                    {
                      title: "输入上限",
                      dataIndex: "max_input_tokens",
                      render: number,
                    },
                    {
                      title: "输出上限",
                      dataIndex: "max_output_tokens",
                      render: number,
                    },
                    { title: "状态", dataIndex: "enabled", render: status },
                    {
                      title: "操作",
                      render: (_, r) => (
                        <Button type="link" onClick={() => edit("models", r)}>
                          编辑
                        </Button>
                      ),
                    },
                  ]}
                />
              </Card>
            ),
          },
          {
            key: "routes",
            label: "模型路由",
            children: (
              <Card
                extra={
                  <Button
                    type="primary"
                    icon={<PlusOutlined />}
                    disabled={!models.length}
                    onClick={() => edit("routes")}
                  >
                    新增路由
                  </Button>
                }
              >
                <p>
                  同类路由按优先级数字从小到大选择；供应商与模型均需启用。发生调用异常后进入对账，不自动重复发送。
                </p>
                <Table<Row>
                  rowKey="id"
                  dataSource={d.routes}
                  pagination={false}
                  columns={[
                    {
                      title: "用途",
                      dataIndex: "route_key",
                      render: (v) => routeNames[v],
                    },
                    {
                      title: "模型",
                      dataIndex: "model_id",
                      render: modelLabel,
                    },
                    { title: "优先级", dataIndex: "priority" },
                    { title: "状态", dataIndex: "enabled", render: status },
                    {
                      title: "操作",
                      render: (_, r) => (
                        <Space>
                          <Button type="link" onClick={() => edit("routes", r)}>
                            编辑
                          </Button>
                          <Button
                            type="link"
                            onClick={() =>
                              get(
                                `/model-settings/preview?routeKey=${r.route_key}`,
                              )
                                .then(setPreview)
                                .catch((e) => message.error(e.message))
                            }
                          >
                            预览解析
                          </Button>
                        </Space>
                      ),
                    },
                  ]}
                />
              </Card>
            ),
          },
          {
            key: "budget",
            label: "额度与用量",
            children: (
              <Space
                direction="vertical"
                size="large"
                style={{ width: "100%" }}
              >
                <Card title={`${d.month} · 团队月度额度（北京时间）`}>
                  <Space size="large" wrap>
                    <span>
                      已使用 <strong>{number(used)}</strong>
                    </span>
                    <span>
                      调用中 / 待确认 <strong>{number(reserved)}</strong>
                    </span>
                    <span>
                      可用{" "}
                      <strong>
                        {number(Math.max(0, limit - used - reserved))}
                      </strong>
                    </span>
                    <span>
                      总额度 <strong>{number(limit)}</strong>
                    </span>
                  </Space>
                  <Progress
                    percent={
                      limit
                        ? Math.min(
                            100,
                            Math.round(((used + reserved) / limit) * 100),
                          )
                        : 100
                    }
                    success={{
                      percent: limit ? Math.min(100, (used / limit) * 100) : 0,
                    }}
                  />
                  <p>
                    每月 1 日 00:00 自动进入新额度周期，历史用量保留。Fake
                    演示不消耗额度。真实调用先按输入字节上界和最大输出预留，返回用量后结算；供应商未返回用量时按预留上界计入。
                  </p>
                </Card>
                <Card title="额度设置">
                  <Form
                    key={d.settings.version}
                    layout="vertical"
                    initialValues={{
                      routingEnabled: d.settings.routing_enabled,
                      monthlyTokenLimit: limit,
                      workflowTokenLimit: d.settings.workflow_token_limit,
                      version: d.settings.version,
                    }}
                    onFinish={async (values) => {
                      try {
                        setSaving(true);
                        await http.put("/model-settings", values);
                        refresh();
                        message.success("额度与路由开关已保存");
                      } catch (e: any) {
                        message.error(e.message);
                      } finally {
                        setSaving(false);
                      }
                    }}
                  >
                    <Form.Item name="version" hidden>
                      <InputNumber />
                    </Form.Item>
                    <Form.Item
                      label="启用配置路由"
                      name="routingEnabled"
                      valuePropName="checked"
                    >
                      <Switch />
                    </Form.Item>
                    <Form.Item
                      label="团队每月 Token 总量（0 表示禁止真实调用）"
                      name="monthlyTokenLimit"
                      rules={[{ required: true }]}
                    >
                      <InputNumber
                        min={0}
                        max={1000000000000}
                        precision={0}
                        style={{ width: 300 }}
                      />
                    </Form.Item>
                    <Form.Item
                      label="单个工作流 Token 总量上限"
                      name="workflowTokenLimit"
                      rules={[{ required: true }]}
                    >
                      <InputNumber
                        min={256}
                        max={1000000000}
                        precision={0}
                        style={{ width: 300 }}
                      />
                    </Form.Item>
                    <Button htmlType="submit" type="primary" loading={saving}>
                      保存额度设置
                    </Button>
                  </Form>
                </Card>
                <Card title="月度历史">
                  <Table<Row>
                    rowKey="month_key"
                    dataSource={d.history}
                    pagination={false}
                    columns={[
                      { title: "月份", dataIndex: "month_key" },
                      {
                        title: "已使用",
                        dataIndex: "used_tokens",
                        render: number,
                      },
                      {
                        title: "预留 / 待确认",
                        dataIndex: "reserved_tokens",
                        render: number,
                      },
                    ]}
                  />
                </Card>
                <Card title="最近 100 次真实模型调用">
                  <Table<Row>
                    rowKey="id"
                    dataSource={d.ledger}
                    scroll={{ x: 1000 }}
                    columns={[
                      { title: "月份", dataIndex: "month_key" },
                      {
                        title: "工作流 / 轮次",
                        render: (_, r) =>
                          `${r.workflow_id} / ${r.round_number}`,
                      },
                      {
                        title: "预留",
                        dataIndex: "reserved_tokens",
                        render: number,
                      },
                      {
                        title: "已扣除",
                        dataIndex: "charged_tokens",
                        render: number,
                      },
                      {
                        title: "计量",
                        dataIndex: "estimated",
                        render: (v) => (v ? "保守估算" : "供应商用量"),
                      },
                      { title: "状态", dataIndex: "status" },
                      {
                        title: "操作",
                        render: (_, r) =>
                          ["RESERVED", "UNCERTAIN"].includes(r.status) ? (
                            <Button
                              onClick={() => {
                                reconcileForm.resetFields();
                                setReconcile(r);
                              }}
                            >
                              对账
                            </Button>
                          ) : null,
                      },
                    ]}
                  />
                </Card>
              </Space>
            ),
          },
        ]}
      />
      <Modal
        open={!!editor}
        title={`${editor?.row ? "编辑" : "新增"}${editor?.kind === "providers" ? "供应商" : editor?.kind === "models" ? "模型" : "路由"}`}
        onCancel={() => setEditor(null)}
        onOk={() => void save()}
        confirmLoading={saving}
        destroyOnHidden
      >
        <Form form={form} layout="vertical">
          <Form.Item name="version" hidden>
            <InputNumber />
          </Form.Item>
          {editor?.kind === "providers" && (
            <>
              <Form.Item
                label="供应商名称"
                name="name"
                rules={[{ required: true }]}
              >
                <Input maxLength={100} />
              </Form.Item>
              <Form.Item label="协议类型" name="type">
                <Select
                  options={[
                    { value: "OPENAI_COMPATIBLE", label: "OpenAI 兼容协议" },
                    { value: "FAKE", label: "Fake 演示" },
                  ]}
                />
              </Form.Item>
              <Form.Item
                label="Base URL"
                name="baseUrl"
                extra="填写根地址或供应商前缀；接口路径将拼接在其后。"
              >
                <Input placeholder="https://api.example.com" maxLength={500} />
              </Form.Item>
              <Form.Item
                label="Chat Completions 路径"
                name="chatPath"
                rules={[{ required: true }]}
              >
                <Input placeholder="/v1/chat/completions" maxLength={200} />
              </Form.Item>
              <Form.Item
                label="API Key"
                name="apiKey"
                extra={
                  editor.row?.has_api_key
                    ? "已加密保存；留空保留原密钥。"
                    : "仅在保存时提交，保存后不回显。"
                }
              >
                <Input.Password autoComplete="new-password" maxLength={4000} />
              </Form.Item>
              {editor.row && (
                <Form.Item
                  label="清除已保存密钥"
                  name="clearApiKey"
                  valuePropName="checked"
                >
                  <Switch />
                </Form.Item>
              )}
              <Form.Item
                label="请求超时（秒）"
                name="timeoutSeconds"
                rules={[{ required: true }]}
              >
                <InputNumber min={5} max={45} precision={0} />
              </Form.Item>
            </>
          )}
          {editor?.kind === "models" && (
            <>
              <Form.Item
                label="显示名称"
                name="name"
                rules={[{ required: true }]}
              >
                <Input maxLength={100} />
              </Form.Item>
              <Form.Item
                label="供应商"
                name="providerId"
                rules={[{ required: true }]}
              >
                <Select
                  options={providers.map((p) => ({
                    value: p.id,
                    label: p.name,
                  }))}
                />
              </Form.Item>
              <Form.Item
                label="模型 ID"
                name="modelName"
                rules={[{ required: true }]}
              >
                <Input maxLength={200} placeholder="供应商提供的模型标识" />
              </Form.Item>
              <Form.Item
                label="Temperature（可清空，使用供应商默认值）"
                name="temperature"
              >
                <InputNumber min={0} max={2} step={0.1} />
              </Form.Item>
              <Form.Item
                label="上下文窗口"
                name="contextWindow"
                rules={[{ required: true }]}
              >
                <InputNumber min={256} max={2000000} precision={0} />
              </Form.Item>
              <Form.Item
                label="单次输入上限（采用保守字节上界校验）"
                name="maxInputTokens"
                rules={[{ required: true }]}
              >
                <InputNumber min={128} max={1000000} precision={0} />
              </Form.Item>
              <Form.Item
                label="单次输出上限"
                name="maxOutputTokens"
                rules={[{ required: true }]}
              >
                <InputNumber min={128} max={1000000} precision={0} />
              </Form.Item>
              <Form.Item label="输出上限参数" name="outputParameter">
                <Select
                  options={[
                    { value: "MAX_TOKENS", label: "max_tokens" },
                    {
                      value: "MAX_COMPLETION_TOKENS",
                      label: "max_completion_tokens",
                    },
                  ]}
                />
              </Form.Item>
            </>
          )}
          {editor?.kind === "routes" && (
            <>
              <Form.Item label="路由用途" name="routeKey">
                <Select
                  options={Object.entries(routeNames).map(([value, label]) => ({
                    value,
                    label,
                  }))}
                />
              </Form.Item>
              <Form.Item
                label="模型"
                name="modelId"
                rules={[{ required: true }]}
              >
                <Select
                  options={models.map((m) => ({
                    value: m.id,
                    label: `${m.name} · ${providerLabel(m.provider_id)}`,
                  }))}
                />
              </Form.Item>
              <Form.Item
                label="优先级（数字越小越优先）"
                name="priority"
                rules={[{ required: true }]}
              >
                <InputNumber min={0} max={10000} precision={0} />
              </Form.Item>
            </>
          )}
          <Form.Item label="启用" name="enabled" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
      <Modal
        open={!!preview}
        title="路由解析预览（不会调用模型）"
        onCancel={() => setPreview(null)}
        footer={<Button onClick={() => setPreview(null)}>关闭</Button>}
      >
        {preview && (
          <Space direction="vertical">
            <span>命中：{routeNames[preview.route_key]}</span>
            <span>
              供应商：{preview.provider_name} / {preview.type}
            </span>
            <span>
              模型：{preview.name} / {preview.model_name}
            </span>
            <span>
              输入 / 输出：{number(preview.max_input_tokens)} /{" "}
              {number(preview.max_output_tokens)}
            </span>
          </Space>
        )}
      </Modal>
      <Modal
        open={!!reconcile}
        title="核对调用用量"
        onCancel={() => setReconcile(null)}
        confirmLoading={saving}
        onOk={async () => {
          try {
            const values = await reconcileForm.validateFields();
            setSaving(true);
            await post(`/model-settings/ledger/${reconcile!.id}/reconcile`, {
              ...values,
              release: values.release === "release",
            });
            setReconcile(null);
            refresh();
            message.success("对账已记录");
          } catch (e: any) {
            if (e.message) message.error(e.message);
          } finally {
            setSaving(false);
          }
        }}
      >
        <Alert
          type="warning"
          showIcon
          message="先核对供应商账单。仅确认未计费时释放预留；无法确认时按预留上界扣除。对账将写入审计日志。"
        />
        <Form
          form={reconcileForm}
          layout="vertical"
          initialValues={{ release: "charge" }}
        >
          <Form.Item name="release" label="处理方式">
            <Select
              options={[
                { value: "charge", label: "按预留上界扣除" },
                { value: "release", label: "已确认未计费，释放预留" },
              ]}
            />
          </Form.Item>
          <Form.Item
            name="reason"
            label="对账依据 / 供应商请求编号"
            rules={[{ required: true, whitespace: true }]}
          >
            <Input.TextArea maxLength={500} />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
