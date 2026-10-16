#!/usr/bin/env python3
"""检查第一阶段交付结构；不替代业务测试或 Mermaid 渲染。"""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
files = ["README.md", "docs/phase-plan.md", "docs/architecture/01-architecture.md",
         "docs/architecture/02-project-layout.md", "flowpilot/README.md",
         "flowpilot-web/README.md", "deploy/README.md"]
for name in files:
    assert (ROOT / name).is_file(), f"缺少 {name}"
text = (ROOT / files[2]).read_text()
for component in ["AgentOrchestrator", "AgentContext", "AgentStateManager", "LlmClient",
                  "PromptTemplateManager", "ToolRegistry", "ToolDispatcher",
                  "ToolExecutionService", "ProposalGenerator", "ApprovalService",
                  "WorkflowEngine", "WorkflowRecoveryService", "ExecutionPolicyService", "AuditService"]:
    assert component in text, f"缺少组件 {component}"
for tool in ["getOrderById", "getOrderByEmail", "getLogisticsInfo", "getCustomerInfo",
             "checkRefundEligibility", "requestRefund", "cancelOrder",
             "updateShippingAddress", "sendCustomerEmail"]:
    assert tool in text, f"缺少工具 {tool}"
blocks = re.findall(r"```mermaid\n(.*?)```", text, re.S)
assert len(blocks) == 5, "第一阶段应包含五个 Mermaid 图"
module_graph = next(b for b in blocks if "app --> workflow" in b)
edges = re.findall(r"^\s*(\w+) --> (\w+)\s*$", module_graph, re.M)
graph = {}
for source, target in edges:
    graph.setdefault(source, []).append(target)
def visit(node, path):
    assert node not in path, f"模块依赖存在环：{path + [node]}"
    for child in graph.get(node, []):
        visit(child, path + [node])
for node in graph:
    visit(node, [])
for name in files:
    body = (ROOT / name).read_text()
    assert body.count("```") % 2 == 0, f"代码围栏不平衡：{name}"
print(f"PASS：{len(files)} 个文档、14 个组件、9 个工具、5 个图代码块、模块依赖无环。")
print("范围：文档结构检查；未运行业务构建、集成测试或 Mermaid 渲染。")
