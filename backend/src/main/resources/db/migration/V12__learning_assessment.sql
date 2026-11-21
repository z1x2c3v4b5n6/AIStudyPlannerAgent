CREATE TABLE assessment_question (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_id BIGINT NULL,
    unit_id VARCHAR(100) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    INDEX idx_assessment_bank (unit_id, owner_id),
    INDEX idx_assessment_owner (owner_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE assessment_question_version (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    question_id BIGINT NOT NULL,
    revision INT NOT NULL,
    state VARCHAR(20) NOT NULL,
    kind VARCHAR(20) NOT NULL,
    difficulty VARCHAR(16) NOT NULL,
    software_version VARCHAR(80) NOT NULL,
    stem VARCHAR(1000) NOT NULL,
    fingerprint CHAR(64) NOT NULL,
    definition JSON NOT NULL,
    source_version CHAR(36) NOT NULL,
    source_ordinal INT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_assessment_revision (question_id, revision),
    INDEX idx_assessment_duplicate (fingerprint),
    INDEX idx_assessment_state (state, difficulty, id),
    CONSTRAINT fk_assessment_question FOREIGN KEY (question_id) REFERENCES assessment_question(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE assessment_question_review (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    version_id BIGINT NOT NULL,
    actor_id BIGINT NOT NULL,
    previous_state VARCHAR(20) NOT NULL,
    next_state VARCHAR(20) NOT NULL,
    rationale VARCHAR(500) NOT NULL,
    decided_at DATETIME(3) NOT NULL,
    CONSTRAINT fk_assessment_review_version FOREIGN KEY (version_id) REFERENCES assessment_question_version(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
