-- AI成本记录表
CREATE TABLE IF NOT EXISTS ai_cost_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    conversation_id BIGINT,
    input_tokens INT DEFAULT 0,
    output_tokens INT DEFAULT 0,
    cost DECIMAL(10, 4) DEFAULT 0.0000,
    cache_hit TINYINT DEFAULT 0 COMMENT '0=未命中, 1=命中',
    model VARCHAR(50) DEFAULT 'kimi',
    call_type VARCHAR(50) DEFAULT 'chat' COMMENT 'chat, tool_call, intent_extract, summary',
    router_layer VARCHAR(50) COMMENT 'rule, lightweight_ai, full_ai',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_created_at (created_at),
    INDEX idx_call_type (call_type),
    INDEX idx_router_layer (router_layer)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI调用成本记录表';
