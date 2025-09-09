CREATE TABLE IF NOT EXISTS user_relation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    source_user_id BIGINT NOT NULL,
    target_user_id BIGINT NOT NULL,
    target_user_display_name VARCHAR(50) DEFAULT NULL,
    relation_flags TINYINT NOT NULL COMMENT 'bit 0: friend, bit 1: blocked, bit 3 hidden for blocked user',
    created_time DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    updated_time DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    INDEX idx_sorce_user_id (source_user_id),
    UNIQUE KEY unique_friendship (source_user_id, target_user_id)
);
