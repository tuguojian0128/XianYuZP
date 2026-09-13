-- 每个订单的每种生命周期事件只允许发起一次邮箱通知。
CREATE TABLE xianyu_email_order_notification (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    xianyu_account_id BIGINT NULL,
    order_key VARCHAR(160) NOT NULL,
    order_id VARCHAR(100) NULL,
    event_type VARCHAR(32) NOT NULL,
    send_status TINYINT NOT NULL DEFAULT 0,
    error_message VARCHAR(500) NULL,
    sent_time DATETIME(3) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_email_order_event (tenant_id, order_key, event_type),
    KEY idx_email_order_notification_tenant_time (tenant_id, create_time),
    KEY idx_email_order_notification_status (tenant_id, send_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO xianyu_sys_setting (tenant_id, setting_key, setting_value, setting_desc)
SELECT tenant_id, 'email_notify_order_created_enabled', '1', '新订单创建时邮箱通知开关'
FROM xianyu_sys_setting GROUP BY tenant_id
UNION ALL
SELECT tenant_id, 'email_notify_delivery_success_enabled', '1', '订单发货成功时邮箱通知开关'
FROM xianyu_sys_setting GROUP BY tenant_id
UNION ALL
SELECT tenant_id, 'email_notify_delivery_failure_enabled', '1', '订单发货失败时邮箱通知开关'
FROM xianyu_sys_setting GROUP BY tenant_id
ON DUPLICATE KEY UPDATE setting_key = VALUES(setting_key);
