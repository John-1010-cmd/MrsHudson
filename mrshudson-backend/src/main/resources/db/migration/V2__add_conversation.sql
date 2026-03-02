-- 添加会话表和关联字段

-- 会话表
CREATE TABLE IF NOT EXISTS conversation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL DEFAULT '新对话',
    provider VARCHAR(50) DEFAULT 'kimi-for-coding',
    last_message_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_user_last_msg (user_id, last_message_at),
    INDEX idx_deleted (deleted),
    FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 对话记录表添加会话ID字段
ALTER TABLE chat_message
ADD COLUMN IF NOT EXISTS conversation_id BIGINT NULL AFTER user_id,
ADD INDEX idx_conversation (conversation_id);

-- 添加外键约束（可选，如果不需要严格约束可以不添加）
-- ALTER TABLE chat_message
-- ADD FOREIGN KEY (conversation_id) REFERENCES conversation(id) ON DELETE SET NULL;
