CREATE TABLE knowledge_rag_turn (
    id CHAR(36) PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    question VARCHAR(300) NOT NULL,
    scope VARCHAR(10) NOT NULL,
    software_version VARCHAR(80),
    created_at DATETIME(3) NOT NULL,
    INDEX idx_rag_turn_owner (owner_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
