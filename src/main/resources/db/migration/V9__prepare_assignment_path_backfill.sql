ALTER TABLE assignment
    ADD COLUMN IF NOT EXISTS path_backfill_status VARCHAR(16) NULL;

UPDATE assignment a
SET a.path_backfill_status = CASE
    WHEN EXISTS (
        SELECT 1
        FROM workspace_operation completed
        WHERE completed.target_type = 'ASSIGNMENT'
          AND completed.target_id = a.id
          AND completed.action = 'MIGRATE_ASSIGNMENT_PATH'
          AND completed.status = 'SUCCEEDED'
    ) THEN 'REGISTERED'
    ELSE 'PENDING'
END
WHERE a.path_backfill_status IS NULL;

DELETE operation
FROM workspace_operation operation
JOIN assignment a
  ON operation.target_type = 'ASSIGNMENT'
 AND operation.target_id = a.id
WHERE a.path_backfill_status = 'PENDING'
  AND operation.action IN ('MIGRATE_ASSIGNMENT_PATH', 'ARCHIVE_FINAL_SUBMISSION');
