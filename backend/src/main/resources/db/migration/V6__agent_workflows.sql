CREATE TABLE agent_workflow (
    id CHAR(36) PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    session_id CHAR(36) NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    updated_ms BIGINT NOT NULL,
    expires_ms BIGINT NOT NULL,
    document LONGTEXT NOT NULL,
    INDEX idx_agent_workflow_owner (owner_id, session_id, updated_ms, id),
    INDEX idx_agent_workflow_expiry (expires_ms),
    CONSTRAINT fk_workflow_session FOREIGN KEY (session_id) REFERENCES agent_session(id) ON DELETE CASCADE
) DEFAULT CHARSET=utf8mb4;
