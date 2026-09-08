ALTER TABLE course
    ADD COLUMN namespace_key VARCHAR(63) NULL;

-- Prefer an already-running non-archived row, then the oldest row, as the namespace owner.
UPDATE course course_row
JOIN (
    SELECT CAST(SUBSTRING_INDEX(
        GROUP_CONCAT(
            id ORDER BY
                CASE status
                    WHEN 'ACTIVE' THEN 0
                    WHEN 'TERMINATING' THEN 0
                    WHEN 'ENDED' THEN 0
                    WHEN 'ARCHIVING' THEN 0
                    WHEN 'PROVISIONING' THEN 1
                    ELSE 2
                END,
                id
        ),
        ',',
        1
    ) AS UNSIGNED) AS owner_id
    FROM course
    WHERE status <> 'ARCHIVED'
    GROUP BY LOWER(TRIM(code)), clss
) owner ON owner.owner_id = course_row.id
SET course_row.namespace_key = CONCAT('jcode-', LOWER(TRIM(course_row.code)), '-', course_row.clss);

CREATE UNIQUE INDEX uk_course_namespace_key ON course (namespace_key);

-- The service already rejects duplicate names; the index closes the concurrent-request race.
CREATE UNIQUE INDEX uk_assignment_course_name ON assignment (course_id, name);
