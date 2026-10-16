-- Source: V1__initial_schema.sql
-- FlowPilot MySQL 8.4; UTC timestamps; generated IDs; all secrets encrypted outside SQL.
CREATE TABLE sys_user (
 id VARCHAR(36) PRIMARY KEY COMMENT '业务主键',
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间 UTC',
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间 UTC',
 version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
username VARCHAR(64) NOT NULL COMMENT 'username',
 password_hash VARCHAR(100) NOT NULL COMMENT 'password hash',
 display_name VARCHAR(100) NOT NULL COMMENT 'display name',
 team_id VARCHAR(36) NOT NULL COMMENT 'team id',
 enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT 'enabled',
 UNIQUE KEY uk_username(username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='sys_user';

CREATE TABLE sys_role (
 id VARCHAR(36) PRIMARY KEY COMMENT '业务主键',
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间 UTC',
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间 UTC',
 version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
code VARCHAR(32) NOT NULL COMMENT 'code',
 name VARCHAR(100) NOT NULL COMMENT 'name',
 UNIQUE KEY uk_role_code(code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='sys_role';

CREATE TABLE sys_permission (
 id VARCHAR(36) PRIMARY KEY COMMENT '业务主键',
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间 UTC',
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间 UTC',
 version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
code VARCHAR(64) NOT NULL COMMENT 'code',
 name VARCHAR(100) NOT NULL COMMENT 'name',
 UNIQUE KEY uk_permission_code(code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='sys_permission';

CREATE TABLE sys_user_role (
 id VARCHAR(36) PRIMARY KEY COMMENT '业务主键',
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间 UTC',
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间 UTC',
 version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
user_id VARCHAR(36) NOT NULL COMMENT 'user id',
 role_id VARCHAR(36) NOT NULL COMMENT 'role id',
 UNIQUE KEY uk_user_role(user_id,role_id),
 FOREIGN KEY(user_id) REFERENCES sys_user(id),
 FOREIGN KEY(role_id) REFERENCES sys_role(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='sys_user_role';

CREATE TABLE sys_role_permission (
 id VARCHAR(36) PRIMARY KEY COMMENT '业务主键',
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间 UTC',
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间 UTC',
 version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
role_id VARCHAR(36) NOT NULL COMMENT 'role id',
 permission_id VARCHAR(36) NOT NULL COMMENT 'permission id',
 UNIQUE KEY uk_role_permission(role_id,permission_id),
 FOREIGN KEY(role_id) REFERENCES sys_role(id),
 FOREIGN KEY(permission_id) REFERENCES sys_permission(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='sys_role_permission';

CREATE TABLE customer (
 id VARCHAR(36) PRIMARY KEY COMMENT '业务主键',
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间 UTC',
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间 UTC',
 version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
team_id VARCHAR(36) NOT NULL COMMENT 'team id',
 name VARCHAR(100) NOT NULL COMMENT 'name',
 email_cipher TEXT NOT NULL COMMENT 'email cipher',
 email_hash VARCHAR(64) NOT NULL COMMENT 'email hash',
 UNIQUE KEY uk_customer_email(team_id,email_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='customer';

CREATE TABLE customer_identity (
 id VARCHAR(36) PRIMARY KEY COMMENT '业务主键',
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间 UTC',
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间 UTC',
 version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
customer_id VARCHAR(36) NOT NULL COMMENT 'customer id',
 identity_hash VARCHAR(64) NOT NULL COMMENT 'identity hash',
 verified BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'verified',
 UNIQUE KEY uk_identity(identity_hash),
 FOREIGN KEY(customer_id) REFERENCES customer(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='customer_identity';

CREATE TABLE customer_order (
 id VARCHAR(36) PRIMARY KEY COMMENT '业务主键',
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间 UTC',
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间 UTC',
 version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
team_id VARCHAR(36) NOT NULL COMMENT 'team id',
 customer_id VARCHAR(36) NOT NULL COMMENT 'customer id',
 status VARCHAR(32) NOT NULL COMMENT 'status',
 total DECIMAL(12,2) NOT NULL COMMENT 'total',
 currency CHAR(3) NOT NULL DEFAULT 'CNY' COMMENT 'currency',
 address_cipher TEXT NOT NULL COMMENT 'address cipher',
 product_name VARCHAR(200) NOT NULL COMMENT 'product name',
 FOREIGN KEY(customer_id) REFERENCES customer(id),
 KEY ix_order_scope(team_id,customer_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='customer_order';

CREATE TABLE order_item (
 id VARCHAR(36) PRIMARY KEY COMMENT '业务主键',
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间 UTC',
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间 UTC',
 version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
order_id VARCHAR(36) NOT NULL COMMENT 'order id',
 product_name VARCHAR(200) NOT NULL COMMENT 'product name',
 quantity INT NOT NULL COMMENT 'quantity',
 unit_price DECIMAL(12,2) NOT NULL COMMENT 'unit price',
 FOREIGN KEY(order_id) REFERENCES customer_order(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='order_item';

CREATE TABLE order_operation (
 id VARCHAR(36) PRIMARY KEY COMMENT '业务主键',
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间 UTC',
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间 UTC',
 version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
order_id VARCHAR(36) NOT NULL COMMENT 'order id',
 operation_type VARCHAR(40) NOT NULL COMMENT 'operation type',
 execution_id VARCHAR(36) NOT NULL COMMENT 'execution id',
 result_status VARCHAR(32) NOT NULL COMMENT 'result status',
 UNIQUE KEY uk_order_execution(execution_id),
 FOREIGN KEY(order_id) REFERENCES customer_order(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='order_operation';

CREATE TABLE mail_account (
 id VARCHAR(36) PRIMARY KEY COMMENT '业务主键',
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间 UTC',
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间 UTC',
 version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
team_id VARCHAR(36) NOT NULL COMMENT 'team id',
 name VARCHAR(100) NOT NULL COMMENT 'name',
 credentials_ref VARCHAR(100) NOT NULL COMMENT 'credentials ref',
 enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT 'enabled'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='mail_account';

CREATE TABLE mail_message (
 id VARCHAR(36) PRIMARY KEY COMMENT '业务主键',
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间 UTC',
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间 UTC',
 version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
team_id VARCHAR(36) NOT NULL COMMENT 'team id',
 customer_id VARCHAR(36) COMMENT 'customer id',
 account_id VARCHAR(36) NOT NULL COMMENT 'account id',
 message_id VARCHAR(200) NOT NULL COMMENT 'message id',
 subject VARCHAR(500) NOT NULL COMMENT 'subject',
 body_cipher MEDIUMTEXT NOT NULL COMMENT 'body cipher',
 sender_hash VARCHAR(64) NOT NULL COMMENT 'sender hash',
 identity_verified BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'identity verified',
 status VARCHAR(32) NOT NULL DEFAULT 'RECEIVED' COMMENT 'status',
 UNIQUE KEY uk_mail(account_id,message_id),
 FOREIGN KEY(account_id) REFERENCES mail_account(id),
 FOREIGN KEY(customer_id) REFERENCES customer(id),
 KEY ix_mail_scope(team_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='mail_message';

CREATE TABLE mail_attachment (
 id VARCHAR(36) PRIMARY KEY COMMENT '业务主键',
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间 UTC',
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间 UTC',
 version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
mail_id VARCHAR(36) NOT NULL COMMENT 'mail id',
 filename VARCHAR(200) NOT NULL COMMENT 'filename',
 size_bytes BIGINT NOT NULL COMMENT 'size bytes',
 storage_key VARCHAR(200) NOT NULL COMMENT 'storage key',
 FOREIGN KEY(mail_id) REFERENCES mail_message(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='mail_attachment';

CREATE TABLE mail_processing_record (
 id VARCHAR(36) PRIMARY KEY COMMENT '业务主键',
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间 UTC',
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间 UTC',
 version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
mail_id VARCHAR(36) NOT NULL COMMENT 'mail id',
 status VARCHAR(32) NOT NULL COMMENT 'status',
 summary VARCHAR(1000) COMMENT 'summary',
 FOREIGN KEY(mail_id) REFERENCES mail_message(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='mail_processing_record';

CREATE TABLE agent_session (
 id VARCHAR(36) PRIMARY KEY COMMENT '业务主键',
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间 UTC',
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间 UTC',
 version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
workflow_id VARCHAR(36) NOT NULL COMMENT 'workflow id',
 provider VARCHAR(32) NOT NULL COMMENT 'provider',
 prompt_version VARCHAR(32) NOT NULL COMMENT 'prompt version',
 UNIQUE KEY uk_session_workflow(workflow_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='agent_session';

CREATE TABLE agent_message (
 id VARCHAR(36) PRIMARY KEY COMMENT '业务主键',
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间 UTC',
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间 UTC',
 version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
workflow_id VARCHAR(36) NOT NULL COMMENT 'workflow id',
 sequence_no INT NOT NULL COMMENT 'sequence no',
 role VARCHAR(32) NOT NULL COMMENT 'role',
 content_cipher MEDIUMTEXT NOT NULL COMMENT 'content cipher',
 UNIQUE KEY uk_message_sequence(workflow_id,sequence_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='agent_message';

CREATE TABLE agent_tool_call (
 id VARCHAR(36) PRIMARY KEY COMMENT '业务主键',
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间 UTC',
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间 UTC',
 version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
workflow_id VARCHAR(36) NOT NULL COMMENT 'workflow id',
 call_id VARCHAR(100) NOT NULL COMMENT 'call id',
 name VARCHAR(64) NOT NULL COMMENT 'name',
 arguments_json JSON NOT NULL COMMENT 'arguments json',
 result_json JSON COMMENT 'result json',
 status VARCHAR(32) NOT NULL COMMENT 'status',
 duration_ms BIGINT NOT NULL DEFAULT 0 COMMENT 'duration ms',
 error_code VARCHAR(64) COMMENT 'error code',
 UNIQUE KEY uk_tool_call(workflow_id,call_id),
 KEY ix_tool_workflow(workflow_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='agent_tool_call';

CREATE TABLE agent_proposal (
 id VARCHAR(36) PRIMARY KEY COMMENT '业务主键',
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间 UTC',
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间 UTC',
 version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
workflow_id VARCHAR(36) NOT NULL COMMENT 'workflow id',
 action_type VARCHAR(40) NOT NULL COMMENT 'action type',
 target_id VARCHAR(36) NOT NULL COMMENT 'target id',
 snapshot_cipher MEDIUMTEXT NOT NULL COMMENT 'snapshot cipher',
 digest CHAR(64) NOT NULL COMMENT 'digest',
 proposal_version INT NOT NULL COMMENT 'proposal version',
 risk_level VARCHAR(16) NOT NULL COMMENT 'risk level',
 UNIQUE KEY uk_proposal_version(workflow_id,proposal_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='agent_proposal';

CREATE TABLE workflow_instance (
 id VARCHAR(36) PRIMARY KEY COMMENT '业务主键',
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间 UTC',
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间 UTC',
 version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
team_id VARCHAR(36) NOT NULL COMMENT 'team id',
 mail_id VARCHAR(36) NOT NULL COMMENT 'mail id',
 customer_id VARCHAR(36) NOT NULL COMMENT 'customer id',
 state VARCHAR(32) NOT NULL COMMENT 'state',
 intent VARCHAR(40) COMMENT 'intent',
 error_code VARCHAR(64) COMMENT 'error code',
 deadline DATETIME(6) NOT NULL COMMENT 'deadline',
 UNIQUE KEY uk_workflow_mail(mail_id),
 FOREIGN KEY(mail_id) REFERENCES mail_message(id),
 KEY ix_workflow_scope(team_id,state,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='workflow_instance';

CREATE TABLE workflow_transition_log (
 id VARCHAR(36) PRIMARY KEY COMMENT '业务主键',
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间 UTC',
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间 UTC',
 version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
workflow_id VARCHAR(36) NOT NULL COMMENT 'workflow id',
 from_state VARCHAR(32) COMMENT 'from state',
 to_state VARCHAR(32) NOT NULL COMMENT 'to state',
 summary VARCHAR(1000) COMMENT 'summary',
 FOREIGN KEY(workflow_id) REFERENCES workflow_instance(id),
 KEY ix_transition(workflow_id,created_at,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='workflow_transition_log';

CREATE TABLE workflow_task (
 id VARCHAR(36) PRIMARY KEY COMMENT '业务主键',
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间 UTC',
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间 UTC',
 version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
workflow_id VARCHAR(36) NOT NULL COMMENT 'workflow id',
 task_type VARCHAR(32) NOT NULL COMMENT 'task type',
 status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'status',
 attempts INT NOT NULL DEFAULT 0 COMMENT 'attempts',
 next_run_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'next run at',
 lease_owner VARCHAR(64) COMMENT 'lease owner',
 lease_until DATETIME(6) COMMENT 'lease until',
 fencing_token BIGINT NOT NULL DEFAULT 0 COMMENT 'fencing token',
 payload_json JSON NOT NULL COMMENT 'payload json',
 FOREIGN KEY(workflow_id) REFERENCES workflow_instance(id),
 KEY ix_task_claim(status,next_run_at,lease_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='workflow_task';

CREATE TABLE workflow_context (
 id VARCHAR(36) PRIMARY KEY COMMENT '业务主键',
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间 UTC',
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间 UTC',
 version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
workflow_id VARCHAR(36) NOT NULL COMMENT 'workflow id',
 context_cipher MEDIUMTEXT NOT NULL COMMENT 'context cipher',
 UNIQUE KEY uk_context_workflow(workflow_id),
 FOREIGN KEY(workflow_id) REFERENCES workflow_instance(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='workflow_context';

CREATE TABLE approval_request (
 id VARCHAR(36) PRIMARY KEY COMMENT '业务主键',
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间 UTC',
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间 UTC',
 version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
team_id VARCHAR(36) NOT NULL COMMENT 'team id',
 workflow_id VARCHAR(36) NOT NULL COMMENT 'workflow id',
 proposal_id VARCHAR(36) NOT NULL COMMENT 'proposal id',
 action_type VARCHAR(40) NOT NULL COMMENT 'action type',
 target_id VARCHAR(36) NOT NULL COMMENT 'target id',
 snapshot_digest CHAR(64) NOT NULL COMMENT 'snapshot digest',
 status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'status',
 requested_by VARCHAR(36) COMMENT 'requested by',
 required_role VARCHAR(32) NOT NULL COMMENT 'required role',
 expires_at DATETIME(6) NOT NULL COMMENT 'expires at',
 FOREIGN KEY(proposal_id) REFERENCES agent_proposal(id),
 FOREIGN KEY(workflow_id) REFERENCES workflow_instance(id),
 UNIQUE KEY uk_approval_proposal(proposal_id),
 KEY ix_approval_scope(team_id,status,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='approval_request';

CREATE TABLE approval_record (
 id VARCHAR(36) PRIMARY KEY COMMENT '业务主键',
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间 UTC',
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间 UTC',
 version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
approval_id VARCHAR(36) NOT NULL COMMENT 'approval id',
 actor_id VARCHAR(36) NOT NULL COMMENT 'actor id',
 decision VARCHAR(16) NOT NULL COMMENT 'decision',
 comment VARCHAR(1000) NOT NULL COMMENT 'comment',
 snapshot_digest CHAR(64) NOT NULL COMMENT 'snapshot digest',
 UNIQUE KEY uk_approval_record(approval_id),
 FOREIGN KEY(approval_id) REFERENCES approval_request(id),
 FOREIGN KEY(actor_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='approval_record';

CREATE TABLE audit_log (
 id VARCHAR(36) PRIMARY KEY COMMENT '业务主键',
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间 UTC',
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间 UTC',
 version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
team_id VARCHAR(36) NOT NULL COMMENT 'team id',
 workflow_id VARCHAR(36) COMMENT 'workflow id',
 actor_id VARCHAR(36) NOT NULL COMMENT 'actor id',
 event_type VARCHAR(64) NOT NULL COMMENT 'event type',
 summary VARCHAR(1000) NOT NULL COMMENT 'summary',
 KEY ix_audit_scope(team_id,created_at),
 KEY ix_audit_workflow(workflow_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='audit_log';

CREATE TABLE operation_execution (
 id VARCHAR(36) PRIMARY KEY COMMENT '业务主键',
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间 UTC',
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间 UTC',
 version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
workflow_id VARCHAR(36) NOT NULL COMMENT 'workflow id',
 approval_id VARCHAR(36) NOT NULL COMMENT 'approval id',
 action_type VARCHAR(40) NOT NULL COMMENT 'action type',
 idempotency_key VARCHAR(100) NOT NULL COMMENT 'idempotency key',
 status VARCHAR(32) NOT NULL COMMENT 'status',
 provider_request_id VARCHAR(100) COMMENT 'provider request id',
 result_json JSON COMMENT 'result json',
 last_checked_at DATETIME(6) COMMENT 'last checked at',
 UNIQUE KEY uk_execution_approval(approval_id),
 UNIQUE KEY uk_execution_key(idempotency_key),
 FOREIGN KEY(approval_id) REFERENCES approval_request(id),
 KEY ix_execution_status(status,updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='operation_execution';

CREATE TABLE integration_request_log (
 id VARCHAR(36) PRIMARY KEY COMMENT '业务主键',
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间 UTC',
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间 UTC',
 version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
idempotency_key VARCHAR(100) NOT NULL COMMENT 'idempotency key',
 adapter VARCHAR(32) NOT NULL COMMENT 'adapter',
 operation VARCHAR(40) NOT NULL COMMENT 'operation',
 status VARCHAR(32) NOT NULL COMMENT 'status',
 result_json JSON COMMENT 'result json',
 UNIQUE KEY uk_integration_key(adapter,idempotency_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='integration_request_log';

CREATE TABLE failed_task (
 id VARCHAR(36) PRIMARY KEY COMMENT '业务主键',
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间 UTC',
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间 UTC',
 version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
workflow_id VARCHAR(36) NOT NULL COMMENT 'workflow id',
 task_id VARCHAR(36) COMMENT 'task id',
 team_id VARCHAR(36) NOT NULL COMMENT 'team id',
 error_code VARCHAR(64) NOT NULL COMMENT 'error code',
 summary VARCHAR(1000) NOT NULL COMMENT 'summary',
 resolved BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'resolved',
 KEY ix_failed_scope(team_id,resolved,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='failed_task';



-- Source: V2__execution_resource_reservation.sql
ALTER TABLE operation_execution ADD COLUMN active_resource_key VARCHAR(80) NULL COMMENT '非终态订单操作互斥资源', ADD UNIQUE KEY uk_active_resource(active_resource_key);


-- Source: V3__agent_round_checkpoint.sql
CREATE TABLE agent_round_checkpoint (
 id VARCHAR(36) PRIMARY KEY COMMENT '主键',
 workflow_id VARCHAR(36) NOT NULL COMMENT '工作流',
 round_no INT NOT NULL COMMENT '模型轮次',
 reply_cipher MEDIUMTEXT NOT NULL COMMENT '加密的模型工具调用或最终建议',
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
 UNIQUE KEY uk_round(workflow_id,round_no),
 FOREIGN KEY(workflow_id) REFERENCES workflow_instance(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模型轮次恢复检查点';


-- Source: V4__immutable_evidence.sql
CREATE TRIGGER audit_log_no_update BEFORE UPDATE ON audit_log FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Audit logs are append-only';
CREATE TRIGGER audit_log_no_delete BEFORE DELETE ON audit_log FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Audit logs are append-only';
CREATE TRIGGER approval_record_no_update BEFORE UPDATE ON approval_record FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Approval evidence is immutable';
CREATE TRIGGER approval_record_no_delete BEFORE DELETE ON approval_record FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Approval evidence is immutable';
CREATE TRIGGER proposal_no_update BEFORE UPDATE ON agent_proposal FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Proposal snapshot is immutable';
CREATE TRIGGER proposal_no_delete BEFORE DELETE ON agent_proposal FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Proposal snapshot is immutable';


-- Source: V5__role_catalog.sql
-- Role catalog is available in both demo and production; no default passwords.
INSERT IGNORE INTO sys_role(id,code,name) VALUES('ADMIN','ADMIN','ADMIN');
INSERT IGNORE INTO sys_permission(id,code,name) VALUES('users:manage','users:manage','users:manage');
INSERT IGNORE INTO sys_role_permission(id,role_id,permission_id) VALUES('5a8a59f576f7c9d43435e7f15c63edd786f4','ADMIN','users:manage');
INSERT IGNORE INTO sys_role(id,code,name) VALUES('SUPERVISOR','SUPERVISOR','SUPERVISOR');
INSERT IGNORE INTO sys_permission(id,code,name) VALUES('mails:read','mails:read','mails:read');
INSERT IGNORE INTO sys_role_permission(id,role_id,permission_id) VALUES('3f7be57e6f7a1401e07ab28bd4e2d5bb4e78','SUPERVISOR','mails:read');
INSERT IGNORE INTO sys_permission(id,code,name) VALUES('mails:analyze','mails:analyze','mails:analyze');
INSERT IGNORE INTO sys_role_permission(id,role_id,permission_id) VALUES('93d1f77b02c16909c49afab7dbe960e098d4','SUPERVISOR','mails:analyze');
INSERT IGNORE INTO sys_permission(id,code,name) VALUES('orders:read','orders:read','orders:read');
INSERT IGNORE INTO sys_role_permission(id,role_id,permission_id) VALUES('f725c5eae1c71c304dbcae279217cc954ef2','SUPERVISOR','orders:read');
INSERT IGNORE INTO sys_permission(id,code,name) VALUES('workflows:read','workflows:read','workflows:read');
INSERT IGNORE INTO sys_role_permission(id,role_id,permission_id) VALUES('f5a0e3282fecb65b73f309a1e601200196a9','SUPERVISOR','workflows:read');
INSERT IGNORE INTO sys_permission(id,code,name) VALUES('approvals:decide','approvals:decide','approvals:decide');
INSERT IGNORE INTO sys_role_permission(id,role_id,permission_id) VALUES('85cc6062e37f88f554df1a0910d69ba992f0','SUPERVISOR','approvals:decide');
INSERT IGNORE INTO sys_permission(id,code,name) VALUES('workflows:retry','workflows:retry','workflows:retry');
INSERT IGNORE INTO sys_role_permission(id,role_id,permission_id) VALUES('6a58c9420fda640be72c0c78041c4a9d2d33','SUPERVISOR','workflows:retry');
INSERT IGNORE INTO sys_permission(id,code,name) VALUES('audit:read','audit:read','audit:read');
INSERT IGNORE INTO sys_role_permission(id,role_id,permission_id) VALUES('3e9eb4a98e024f00bb1e2ee95e1e241da243','SUPERVISOR','audit:read');
INSERT IGNORE INTO sys_role(id,code,name) VALUES('AGENT_OPERATOR','AGENT_OPERATOR','AGENT_OPERATOR');
INSERT IGNORE INTO sys_permission(id,code,name) VALUES('mails:read','mails:read','mails:read');
INSERT IGNORE INTO sys_role_permission(id,role_id,permission_id) VALUES('98049577e4df07f07d2f451ada7069dc80fd','AGENT_OPERATOR','mails:read');
INSERT IGNORE INTO sys_permission(id,code,name) VALUES('mails:analyze','mails:analyze','mails:analyze');
INSERT IGNORE INTO sys_role_permission(id,role_id,permission_id) VALUES('a59392501b4ef7aebc2ef3304ae8b678f215','AGENT_OPERATOR','mails:analyze');
INSERT IGNORE INTO sys_permission(id,code,name) VALUES('orders:read','orders:read','orders:read');
INSERT IGNORE INTO sys_role_permission(id,role_id,permission_id) VALUES('1f3ef5bbbc66b74e5226a5b4e5fa4b187554','AGENT_OPERATOR','orders:read');
INSERT IGNORE INTO sys_permission(id,code,name) VALUES('workflows:read','workflows:read','workflows:read');
INSERT IGNORE INTO sys_role_permission(id,role_id,permission_id) VALUES('0eda6372ad39d574ce73912962287401c8ab','AGENT_OPERATOR','workflows:read');
INSERT IGNORE INTO sys_permission(id,code,name) VALUES('approvals:decide','approvals:decide','approvals:decide');
INSERT IGNORE INTO sys_role_permission(id,role_id,permission_id) VALUES('74b4ef43157a84e408a91d8d65488c6bc38f','AGENT_OPERATOR','approvals:decide');
INSERT IGNORE INTO sys_permission(id,code,name) VALUES('workflows:retry','workflows:retry','workflows:retry');
INSERT IGNORE INTO sys_role_permission(id,role_id,permission_id) VALUES('94262b10109d1531144b1c5609c89fe674e2','AGENT_OPERATOR','workflows:retry');
INSERT IGNORE INTO sys_role(id,code,name) VALUES('AUDITOR','AUDITOR','AUDITOR');
INSERT IGNORE INTO sys_permission(id,code,name) VALUES('mails:read','mails:read','mails:read');
INSERT IGNORE INTO sys_role_permission(id,role_id,permission_id) VALUES('a67142113f972abafb1510c4da8538b67993','AUDITOR','mails:read');
INSERT IGNORE INTO sys_permission(id,code,name) VALUES('orders:read','orders:read','orders:read');
INSERT IGNORE INTO sys_role_permission(id,role_id,permission_id) VALUES('663ded3e1b0f03b134bf2df4a9b5f7ec0312','AUDITOR','orders:read');
INSERT IGNORE INTO sys_permission(id,code,name) VALUES('workflows:read','workflows:read','workflows:read');
INSERT IGNORE INTO sys_role_permission(id,role_id,permission_id) VALUES('315f0fdfab4eceafe2dacc8248062cbd86ef','AUDITOR','workflows:read');
INSERT IGNORE INTO sys_permission(id,code,name) VALUES('audit:read','audit:read','audit:read');
INSERT IGNORE INTO sys_role_permission(id,role_id,permission_id) VALUES('03090b1d0c5f722341729be10b6a4766d308','AUDITOR','audit:read');


-- Source: V6__remembered_sessions.sql
CREATE TABLE auth_refresh_session (
 id VARCHAR(36) PRIMARY KEY COMMENT '会话主键',
 user_id VARCHAR(36) NOT NULL COMMENT '用户',
 family_id VARCHAR(36) NOT NULL COMMENT '可撤销会话族',
 token_hash CHAR(64) NOT NULL COMMENT '随机刷新凭证摘要',
 password_stamp CHAR(64) NOT NULL COMMENT '密码版本摘要',
 remembered BOOLEAN NOT NULL COMMENT '是否记住登录',
 expires_at DATETIME(6) NOT NULL COMMENT '绝对过期时间UTC',
 revoked_at DATETIME(6) NULL COMMENT '撤销时间UTC',
 replacement_cipher TEXT NULL COMMENT '短暂并发窗口内的加密替代凭证',
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间UTC',
 UNIQUE KEY uk_refresh_hash(token_hash),
 KEY ix_refresh_family(family_id,user_id,revoked_at,expires_at),
 KEY ix_refresh_expiry(expires_at),
 FOREIGN KEY (user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='可轮换的记住登录会话';


-- Source: V7__model_management.sql
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
