CREATE TABLE adaptive_review_feedback (
    owner_id BIGINT NOT NULL,
    path_item_id BIGINT NOT NULL,
    rating VARCHAR(24),
    deferred_until DATE,
    dismissed BOOLEAN NOT NULL DEFAULT FALSE,
    updated_ms BIGINT NOT NULL,
    PRIMARY KEY (owner_id,path_item_id),
    CONSTRAINT fk_review_feedback_item FOREIGN KEY (path_item_id) REFERENCES study_path_item(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE adaptive_review_cycle (
    owner_id BIGINT NOT NULL,
    path_item_id BIGINT NOT NULL,
    cycle_key VARCHAR(30) NOT NULL,
    task_id BIGINT NOT NULL,
    created_ms BIGINT NOT NULL,
    PRIMARY KEY (owner_id,path_item_id,cycle_key),
    UNIQUE KEY uk_review_cycle_task (task_id),
    CONSTRAINT fk_review_cycle_item FOREIGN KEY (path_item_id) REFERENCES study_path_item(id) ON DELETE CASCADE,
    CONSTRAINT fk_review_cycle_task FOREIGN KEY (task_id) REFERENCES study_task(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
