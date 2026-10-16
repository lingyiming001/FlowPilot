INSERT IGNORE INTO sys_permission(id,code,name) VALUES('models:manage','models:manage','模型与额度管理');
INSERT IGNORE INTO sys_role_permission(id,role_id,permission_id) VALUES('admin-models-manage','ADMIN','models:manage');
CREATE TABLE llm_settings (
 team_id VARCHAR(36) PRIMARY KEY, routing_enabled BOOLEAN NOT NULL DEFAULT FALSE,
 monthly_token_limit BIGINT NOT NULL DEFAULT 1000000, workflow_token_limit BIGINT NOT NULL DEFAULT 100000,
 version INT NOT NULL DEFAULT 0
);
CREATE TABLE llm_provider (
 id VARCHAR(36) PRIMARY KEY, team_id VARCHAR(36) NOT NULL, name VARCHAR(100) NOT NULL,
 type VARCHAR(32) NOT NULL, base_url VARCHAR(500) NOT NULL, chat_path VARCHAR(200) NOT NULL,
 api_key_cipher TEXT NULL, timeout_seconds INT NOT NULL, enabled BOOLEAN NOT NULL,
 version INT NOT NULL DEFAULT 0, INDEX ix_provider_team(team_id)
);
CREATE TABLE llm_model (
 id VARCHAR(36) PRIMARY KEY, team_id VARCHAR(36) NOT NULL, provider_id VARCHAR(36) NOT NULL,
 name VARCHAR(100) NOT NULL, model_name VARCHAR(200) NOT NULL, temperature DOUBLE NULL,
 context_window INT NOT NULL, max_input_tokens INT NOT NULL, max_output_tokens INT NOT NULL,
 output_parameter VARCHAR(32) NOT NULL, enabled BOOLEAN NOT NULL, version INT NOT NULL DEFAULT 0,
 FOREIGN KEY(provider_id) REFERENCES llm_provider(id), INDEX ix_model_team(team_id)
);
CREATE TABLE llm_route (
 id VARCHAR(36) PRIMARY KEY, team_id VARCHAR(36) NOT NULL, route_key VARCHAR(32) NOT NULL,
 model_id VARCHAR(36) NOT NULL, priority INT NOT NULL, enabled BOOLEAN NOT NULL, version INT NOT NULL DEFAULT 0,
 FOREIGN KEY(model_id) REFERENCES llm_model(id), INDEX ix_route_team(team_id,route_key,priority)
);
CREATE TABLE llm_monthly_usage (
 team_id VARCHAR(36) NOT NULL, month_key CHAR(7) NOT NULL, used_tokens BIGINT NOT NULL DEFAULT 0,
 reserved_tokens BIGINT NOT NULL DEFAULT 0, PRIMARY KEY(team_id,month_key)
);
CREATE TABLE llm_token_ledger (
 id VARCHAR(36) PRIMARY KEY, team_id VARCHAR(36) NOT NULL, month_key CHAR(7) NOT NULL,
 workflow_id VARCHAR(36) NOT NULL, round_number INT NOT NULL, model_id VARCHAR(36) NOT NULL,
 reserved_tokens BIGINT NOT NULL, input_tokens BIGINT NULL, output_tokens BIGINT NULL,
 charged_tokens BIGINT NOT NULL DEFAULT 0, estimated BOOLEAN NOT NULL DEFAULT FALSE,
 status VARCHAR(32) NOT NULL, reply_cipher MEDIUMTEXT NULL,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_llm_round(team_id,workflow_id,round_number), INDEX ix_ledger_month(team_id,month_key)
);
