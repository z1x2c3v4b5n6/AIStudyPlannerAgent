CREATE TABLE adaptive_proposal (
    id CHAR(36) PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    kind VARCHAR(24) NOT NULL,
    digest CHAR(64) NOT NULL,
    expires_ms BIGINT NOT NULL,
    payload LONGTEXT NOT NULL,
    decision VARCHAR(32),
    result LONGTEXT,
    INDEX idx_adaptive_proposal_owner (owner_id, expires_ms),
    INDEX idx_adaptive_proposal_expiry (expires_ms)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
