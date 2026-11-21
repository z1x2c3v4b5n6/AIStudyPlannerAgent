CREATE TABLE assessment_session (
    id CHAR(36) PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    subject_id BIGINT,
    path_id BIGINT,
    mode VARCHAR(20) NOT NULL,
    state VARCHAR(20) NOT NULL,
    started_at DATETIME(3) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    submitted_at DATETIME(3),
    scoring_version SMALLINT NOT NULL,
    INDEX idx_assessment_session_owner (owner_id, started_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE assessment_session_item (
    session_id CHAR(36) NOT NULL,
    ordinal INT NOT NULL,
    question_version_id BIGINT NOT NULL,
    answer VARCHAR(2000),
    grade VARCHAR(20),
    error_type VARCHAR(30),
    assistance JSON,
    PRIMARY KEY (session_id, ordinal),
    UNIQUE KEY uk_assessment_session_question (session_id, question_version_id),
    INDEX idx_assessment_result_question (question_version_id, grade),
    CONSTRAINT fk_assessment_item_session FOREIGN KEY (session_id) REFERENCES assessment_session(id),
    CONSTRAINT fk_assessment_item_version FOREIGN KEY (question_version_id) REFERENCES assessment_question_version(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
