CREATE TABLE knowledge_review_case (
    id CHAR(36) PRIMARY KEY,
    version_id CHAR(36) NOT NULL,
    environment VARCHAR(80) NOT NULL,
    query_type VARCHAR(20) NOT NULL,
    distinct_users INT NOT NULL,
    state VARCHAR(24) NOT NULL,
    outcome VARCHAR(40),
    candidate_id CHAR(36),
    run_id CHAR(36),
    opened_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_review_case_group (version_id,environment,query_type),
    CONSTRAINT fk_review_case_version FOREIGN KEY (version_id) REFERENCES knowledge_document_version(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE knowledge_review_run (
    id CHAR(36) PRIMARY KEY,
    case_id CHAR(36) NOT NULL,
    query_text VARCHAR(300) NOT NULL,
    target_version VARCHAR(80) NOT NULL,
    expected_term VARCHAR(100) NOT NULL,
    report JSON NOT NULL,
    created_at DATETIME(3) NOT NULL,
    INDEX idx_review_run_case (case_id,created_at),
    CONSTRAINT fk_review_run_case FOREIGN KEY (case_id) REFERENCES knowledge_review_case(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
