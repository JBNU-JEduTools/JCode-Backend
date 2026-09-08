ALTER TABLE course MODIFY COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

CREATE TABLE course_infrastructure_operation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_id BIGINT NOT NULL,
    action VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    idempotency_key VARCHAR(36) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(6) NOT NULL,
    locked_at DATETIME(6) NULL,
    last_error TEXT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_course_infra_operation_idempotency UNIQUE (idempotency_key),
    INDEX ix_course_infra_operation_ready (status, next_attempt_at, locked_at),
    INDEX ix_course_infra_operation_course (course_id, status)
);
