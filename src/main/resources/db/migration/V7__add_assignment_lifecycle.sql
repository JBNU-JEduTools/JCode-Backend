ALTER TABLE assignment
    ADD COLUMN workspace_key VARCHAR(80) NULL,
    ADD COLUMN legacy_dir_name VARCHAR(100) NULL,
    ADD COLUMN lifecycle_status VARCHAR(24) NULL,
    ADD COLUMN schedule_status VARCHAR(24) NULL,
    ADD COLUMN last_error TEXT NULL,
    ADD COLUMN archive_retention_days INT NOT NULL DEFAULT 90,
    ADD COLUMN archived_at DATETIME(6) NULL,
    ADD COLUMN finalized_at DATETIME(6) NULL;

UPDATE assignment
SET legacy_dir_name = NULLIF(TRIM(dir_name), ''),
    workspace_key = CONCAT('assignment-', id),
    lifecycle_status = 'PROVISIONING',
    schedule_status = CASE
        WHEN kickoff_date > CURRENT_TIMESTAMP THEN 'SCHEDULED'
        WHEN deadline_date < CURRENT_TIMESTAMP THEN 'CLOSED'
        ELSE 'OPEN'
    END;

UPDATE assignment SET dir_name = workspace_key;

ALTER TABLE assignment
    MODIFY workspace_key VARCHAR(80) NOT NULL,
    MODIFY lifecycle_status VARCHAR(24) NOT NULL,
    MODIFY schedule_status VARCHAR(24) NOT NULL,
    ADD CONSTRAINT uk_assignment_workspace_key UNIQUE (workspace_key);

CREATE TABLE starter_artifact (
    id BIGINT NOT NULL AUTO_INCREMENT,
    assignment_id BIGINT NOT NULL,
    version INT NOT NULL,
    artifact_key VARCHAR(255) NOT NULL,
    checksum VARCHAR(64) NULL,
    size_bytes BIGINT NOT NULL DEFAULT 0,
    overwrite_policy VARCHAR(24) NOT NULL,
    status VARCHAR(24) NOT NULL,
    last_error TEXT NULL,
    uploaded_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_starter_artifact_assignment FOREIGN KEY (assignment_id) REFERENCES assignment(id),
    CONSTRAINT uk_starter_artifact_version UNIQUE (assignment_id, version),
    CONSTRAINT uk_starter_artifact_key UNIQUE (artifact_key)
);

CREATE TABLE workspace_operation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    target_type VARCHAR(24) NOT NULL,
    target_id BIGINT NOT NULL,
    action VARCHAR(32) NOT NULL,
    artifact_id BIGINT NULL,
    status VARCHAR(16) NOT NULL,
    idempotency_key VARCHAR(36) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(6) NOT NULL,
    locked_at DATETIME(6) NULL,
    last_error TEXT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_workspace_operation_key UNIQUE (idempotency_key),
    INDEX ix_workspace_operation_ready (status, next_attempt_at, locked_at),
    INDEX ix_workspace_operation_target (target_type, target_id, status)
);

INSERT INTO workspace_operation (
    target_type, target_id, action, status, idempotency_key, attempts,
    next_attempt_at, created_at, updated_at
)
SELECT 'ASSIGNMENT', id, 'MIGRATE_ASSIGNMENT_PATH', 'PENDING', UUID(), 0,
       CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM assignment
WHERE workspace_key IS NOT NULL;

INSERT INTO workspace_operation (
    target_type, target_id, action, status, idempotency_key, attempts,
    next_attempt_at, created_at, updated_at
)
SELECT 'ASSIGNMENT', id, 'ARCHIVE_FINAL_SUBMISSION', 'PENDING', UUID(), 0,
       CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM assignment
WHERE schedule_status = 'CLOSED';
