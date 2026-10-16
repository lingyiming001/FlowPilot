ALTER TABLE operation_execution ADD COLUMN active_resource_key VARCHAR(80) NULL COMMENT '非终态订单操作互斥资源', ADD UNIQUE KEY uk_active_resource(active_resource_key);
