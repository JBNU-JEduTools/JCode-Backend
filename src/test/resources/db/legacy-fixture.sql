INSERT INTO `user` (email, student_num, role)
VALUES ('student@example.com', 20260001, 'STUDENT');

INSERT INTO course (name, code, year, term, professor, clss, vnc, course_key)
VALUES ('Algorithms', 'ALG', 2026, 1, 'Professor', 1, FALSE, 'key');

INSERT INTO user_courses (course_id, user_id, role)
VALUES (1, 1, 'STUDENT');

INSERT INTO assignment (course_id, name, description, kickoff_date, deadline_date, dir_name, has_starter_code)
VALUES
    (1, 'Homework', 'legacy path', '2026-01-01', '2026-02-01', 'homework', FALSE),
    (1, 'Empty path', 'empty legacy path', '2026-01-01', '2026-02-01', '', FALSE);

INSERT INTO jcode (user_course_id, course_id, user_id, jcode_url, snapshot)
VALUES
    (1, 1, 1, 'http://legacy-1', FALSE),
    (1, 1, 1, 'http://legacy-2', FALSE);
