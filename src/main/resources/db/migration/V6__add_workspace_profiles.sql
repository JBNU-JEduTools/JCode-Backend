ALTER TABLE course
    ADD COLUMN environment_profile VARCHAR(24) NULL,
    ADD COLUMN use_vnc BOOLEAN NULL,
    ADD COLUMN use_jupyter BOOLEAN NULL,
    ADD COLUMN base_image VARCHAR(512) NULL,
    ADD COLUMN resource_profile VARCHAR(24) NULL,
    ADD COLUMN egress_policy VARCHAR(24) NULL,
    ADD COLUMN workspace_scope VARCHAR(24) NULL;

UPDATE course
SET environment_profile = CASE WHEN vnc = TRUE THEN 'LAB' ELSE 'ALGORITHM' END,
    use_vnc = vnc,
    use_jupyter = vnc,
    resource_profile = 'STANDARD',
    egress_policy = 'PACKAGE_PROXY',
    workspace_scope = 'COURSE';

ALTER TABLE course
    MODIFY environment_profile VARCHAR(24) NOT NULL,
    MODIFY use_vnc BOOLEAN NOT NULL,
    MODIFY use_jupyter BOOLEAN NOT NULL,
    MODIFY resource_profile VARCHAR(24) NOT NULL,
    MODIFY egress_policy VARCHAR(24) NOT NULL,
    MODIFY workspace_scope VARCHAR(24) NOT NULL;
