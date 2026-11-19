CREATE TABLE knowledge_source (
    id CHAR(36) PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    entry_url VARCHAR(500) NOT NULL,
    domain VARCHAR(40) NOT NULL,
    software_version VARCHAR(80),
    checked_at DATETIME(3),
    last_success_at DATETIME(3),
    last_error VARCHAR(80),
    UNIQUE KEY uk_knowledge_source_url (entry_url)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE knowledge_document (
    id CHAR(36) PRIMARY KEY,
    owner_id BIGINT,
    source_id CHAR(36),
    origin VARCHAR(500),
    unit_id VARCHAR(100),
    domain VARCHAR(40) NOT NULL,
    active_version CHAR(36),
    created_at DATETIME(3) NOT NULL,
    INDEX idx_knowledge_document_owner (owner_id,created_at),
    UNIQUE KEY uk_knowledge_document_source (source_id),
    CONSTRAINT fk_hub_source FOREIGN KEY (source_id) REFERENCES knowledge_source(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE knowledge_document_version (
    id CHAR(36) PRIMARY KEY,
    document_id CHAR(36) NOT NULL,
    status VARCHAR(20) NOT NULL,
    title VARCHAR(200) NOT NULL,
    software_version VARCHAR(80),
    body MEDIUMTEXT NOT NULL,
    content_hash CHAR(64) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    INDEX idx_knowledge_version_time (document_id,created_at),
    UNIQUE KEY uk_knowledge_version_hash (document_id,content_hash),
    CONSTRAINT fk_hub_version_document FOREIGN KEY (document_id) REFERENCES knowledge_document(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE knowledge_chunk (
    version_id CHAR(36) NOT NULL,
    ordinal INT NOT NULL,
    heading VARCHAR(200) NOT NULL,
    start_line INT NOT NULL,
    excerpt TEXT NOT NULL,
    PRIMARY KEY (version_id,ordinal),
    CONSTRAINT fk_hub_chunk_version FOREIGN KEY (version_id) REFERENCES knowledge_document_version(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE knowledge_publication (
    id CHAR(36) PRIMARY KEY,
    document_id CHAR(36) NOT NULL,
    version_id CHAR(36) NOT NULL,
    previous_version CHAR(36),
    operation VARCHAR(20) NOT NULL,
    actor_id BIGINT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    content_hash CHAR(64) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    INDEX idx_knowledge_publication (document_id,created_at),
    CONSTRAINT fk_hub_publication_document FOREIGN KEY (document_id) REFERENCES knowledge_document(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE knowledge_feedback (
    version_id CHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    category VARCHAR(32) NOT NULL,
    environment VARCHAR(80) NOT NULL,
    query_type VARCHAR(20) NOT NULL,
    ordinal INT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (version_id,user_id),
    INDEX idx_knowledge_feedback_window (created_at),
    CONSTRAINT fk_hub_feedback_chunk FOREIGN KEY (version_id,ordinal) REFERENCES knowledge_chunk(version_id,ordinal) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
