CREATE TABLE agent_round_checkpoint (
 id VARCHAR(36) PRIMARY KEY COMMENT '主键',
 workflow_id VARCHAR(36) NOT NULL COMMENT '工作流',
 round_no INT NOT NULL COMMENT '模型轮次',
 reply_cipher MEDIUMTEXT NOT NULL COMMENT '加密的模型工具调用或最终建议',
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
 UNIQUE KEY uk_round(workflow_id,round_no),
 FOREIGN KEY(workflow_id) REFERENCES workflow_instance(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模型轮次恢复检查点';
