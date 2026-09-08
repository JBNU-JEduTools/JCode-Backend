ALTER TABLE user_courses
    ADD COLUMN lifecycle_status VARCHAR(24) NOT NULL DEFAULT 'READY',
    ADD COLUMN last_error TEXT NULL,
    ADD COLUMN archived_at DATETIME(6) NULL;

ALTER TABLE jcode
    ADD COLUMN assignment_id BIGINT NULL,
    ADD COLUMN instance_key VARCHAR(96) NULL,
    ADD COLUMN kind VARCHAR(16) NULL,
    ADD COLUMN lifecycle_status VARCHAR(24) NULL,
    ADD COLUMN deployment_name VARCHAR(63) NULL,
    ADD COLUMN service_name VARCHAR(63) NULL,
    ADD COLUMN last_error TEXT NULL,
    ADD COLUMN archived_at DATETIME(6) NULL,
    MODIFY jcode_url VARCHAR(255) NULL;

UPDATE jcode j
JOIN course c ON c.id = j.course_id
JOIN `user` u ON u.id = j.user_id
SET j.kind = CASE WHEN j.snapshot = TRUE THEN 'SNAPSHOT' ELSE 'STANDARD' END,
    j.instance_key = CONCAT(j.user_course_id, ':0:', IF(j.snapshot = TRUE, 'true', 'false')),
    j.lifecycle_status = 'READY',
    j.deployment_name = CASE
        WHEN j.snapshot = TRUE THEN CONCAT('jcode-snapshot-', LOWER(c.code), '-', u.student_num)
        ELSE CONCAT('jcode-', LOWER(c.code), '-', c.clss, '-', u.student_num)
    END,
    j.service_name = CASE
        WHEN j.snapshot = TRUE THEN CONCAT('jcode-snapshot-', LOWER(c.code), '-', u.student_num, '-svc')
        ELSE CONCAT('jcode-', LOWER(c.code), '-', c.clss, '-', u.student_num, '-svc')
    END;

UPDATE jcode j
JOIN (
    SELECT instance_key, MAX(id) AS keep_id
    FROM jcode
    GROUP BY instance_key
    HAVING COUNT(*) > 1
) duplicate ON duplicate.instance_key = j.instance_key AND duplicate.keep_id <> j.id
SET j.lifecycle_status = 'ARCHIVED',
    j.archived_at = CURRENT_TIMESTAMP(6);

ALTER TABLE jcode
    MODIFY instance_key VARCHAR(96) NOT NULL,
    MODIFY kind VARCHAR(16) NOT NULL,
    MODIFY lifecycle_status VARCHAR(24) NOT NULL,
    MODIFY deployment_name VARCHAR(63) NOT NULL,
    MODIFY service_name VARCHAR(63) NOT NULL,
    ADD CONSTRAINT fk_jcode_assignment FOREIGN KEY (assignment_id) REFERENCES assignment(id),
    ADD INDEX ix_jcode_instance_key (instance_key);
