-- 设备Token表，用于存储用户的FCM推送Token
CREATE TABLE IF NOT EXISTS device_token (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    device_token VARCHAR(512) NOT NULL COMMENT 'FCM设备Token',
    platform VARCHAR(20) NOT NULL COMMENT 'android, ios',
    is_active BOOLEAN DEFAULT TRUE COMMENT '是否活跃',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_active (user_id, is_active),
    INDEX idx_device_token (device_token),
    FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
