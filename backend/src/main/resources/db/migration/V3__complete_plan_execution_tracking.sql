-- V2 originally stored actual duration and feedback on both the plan item and its
-- execution record. The execution record is the single source of truth, linked by
-- the unique study_record.plan_item_id introduced in V2.
ALTER TABLE study_plan_item
    DROP CHECK chk_plan_item_actual_minutes,
    DROP COLUMN actual_minutes,
    DROP COLUMN feedback;
