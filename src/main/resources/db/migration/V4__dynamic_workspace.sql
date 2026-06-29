-- 과제별 동적 워크스페이스: 디렉토리명 및 스타터코드 필드 추가
ALTER TABLE assignment ADD COLUMN dir_name VARCHAR(100) NOT NULL DEFAULT '';
ALTER TABLE assignment ADD COLUMN has_starter_code BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE assignment ADD CONSTRAINT uk_assignment_dir_name_course UNIQUE (course_id, dir_name);
