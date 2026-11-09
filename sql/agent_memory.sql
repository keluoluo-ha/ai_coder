CREATE TABLE agent_memory (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NOT NULL COMMENT '所属用户',
    content     VARCHAR(512) NOT NULL COMMENT '记忆内容（一句话，如：用户偏好简洁的代码风格）',
    tag         VARCHAR(32)  NOT NULL COMMENT '记忆标签：USER_PREFERENCE / PROJECT_CONVENTION / IMPORTANT_FACT',
    is_active   TINYINT      NOT NULL DEFAULT 1 COMMENT '1=生效 0=已作废（被新记忆推翻）',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_active (user_id, is_active, update_time)
) COMMENT 'Agent 长期记忆';
