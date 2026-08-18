CREATE TABLE `user` (
    id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(100) NOT NULL,
    student_num INT NULL,
    role VARCHAR(32) NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE course (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(20) NOT NULL,
    year INT NOT NULL,
    term INT NOT NULL,
    professor VARCHAR(50) NOT NULL,
    clss INT NOT NULL,
    vnc BOOLEAN NOT NULL,
    course_key VARCHAR(100) NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE assignment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(255) NULL,
    kickoff_date DATETIME(6) NOT NULL,
    deadline_date DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_assignment_course FOREIGN KEY (course_id) REFERENCES course(id)
);

CREATE TABLE user_courses (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(32) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_uc_course FOREIGN KEY (course_id) REFERENCES course(id),
    CONSTRAINT fk_uc_user FOREIGN KEY (user_id) REFERENCES `user`(id)
);

CREATE TABLE jcode (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_course_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    jcode_url VARCHAR(255) NOT NULL,
    snapshot BOOLEAN NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_jcode_uc FOREIGN KEY (user_course_id) REFERENCES user_courses(id),
    CONSTRAINT fk_jcode_course FOREIGN KEY (course_id) REFERENCES course(id),
    CONSTRAINT fk_jcode_user FOREIGN KEY (user_id) REFERENCES `user`(id)
);
