ALTER TABLE study_plan
    DROP CHECK chk_plan_status,
    ADD CONSTRAINT chk_plan_status
        CHECK (status IN ('CONFIRMED', 'COMPLETED', 'PARTIALLY_COMPLETED', 'ABANDONED', 'CANCELLED'));

ALTER TABLE study_plan_item
    ADD COLUMN actual_minutes INT NULL AFTER planned_minutes,
    ADD COLUMN feedback VARCHAR(1000) NULL AFTER actual_minutes,
    ADD COLUMN task_status_before_completion VARCHAR(20) NULL AFTER feedback,
    ADD COLUMN task_completed_at DATETIME(3) NULL AFTER task_status_before_completion,
    ADD CONSTRAINT chk_plan_item_actual_minutes
        CHECK (actual_minutes IS NULL OR actual_minutes > 0),
    ADD CONSTRAINT chk_plan_item_previous_task_status
        CHECK (task_status_before_completion IS NULL
            OR task_status_before_completion IN ('TODO', 'IN_PROGRESS'));

ALTER TABLE study_record
    ADD COLUMN plan_id BIGINT NULL AFTER task_id,
    ADD COLUMN plan_item_id BIGINT NULL AFTER plan_id,
    ADD UNIQUE KEY uk_record_plan_item (plan_item_id),
    ADD KEY idx_record_user_plan (user_id, plan_id),
    ADD CONSTRAINT chk_record_plan_source
        CHECK ((plan_id IS NULL AND plan_item_id IS NULL)
            OR (plan_id IS NOT NULL AND plan_item_id IS NOT NULL)),
    ADD CONSTRAINT fk_record_plan
        FOREIGN KEY (plan_id) REFERENCES study_plan (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT fk_record_plan_item
        FOREIGN KEY (plan_item_id) REFERENCES study_plan_item (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
