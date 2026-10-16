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
