ALTER TABLE course
    ADD COLUMN workspace_runtime_enabled BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE course c
SET c.workspace_runtime_enabled = TRUE
WHERE c.status = 'ACTIVE'
  AND EXISTS (
      SELECT 1
      FROM course_infrastructure_operation cio
      WHERE cio.course_id = c.id
        AND cio.action = 'PROVISION_NAMESPACE'
        AND cio.status = 'SUCCEEDED'
  );

ALTER TABLE jcode
    ADD COLUMN observed_status VARCHAR(24) NOT NULL DEFAULT 'UNKNOWN',
    ADD COLUMN observed_reason VARCHAR(128) NULL,
    ADD COLUMN last_observed_at DATETIME(6) NULL;

CREATE INDEX ix_jcode_runtime_observation
    ON jcode (lifecycle_status, observed_status, last_observed_at, id);
