CREATE TABLE agent_session (
    id CHAR(36) PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    title VARCHAR(80) NOT NULL,
    updated_ms BIGINT NOT NULL,
    expires_ms BIGINT NOT NULL,
    document LONGTEXT NOT NULL,
    INDEX idx_agent_session_owner (owner_id, updated_ms, id),
    INDEX idx_agent_session_expiry (expires_ms)
) DEFAULT CHARSET=utf8mb4;

CREATE TABLE agent_action_receipt (
    action_id CHAR(36) PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    session_id CHAR(36) NOT NULL,
    action_type VARCHAR(60) NOT NULL,
    digest CHAR(64) NOT NULL,
    committed_ms BIGINT NOT NULL,
    result LONGTEXT NOT NULL,
    INDEX idx_agent_receipt_owner (owner_id, committed_ms),
    INDEX idx_agent_receipt_expiry (committed_ms)
) DEFAULT CHARSET=utf8mb4;
