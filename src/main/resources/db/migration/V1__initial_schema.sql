CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE guardian (
    id BIGINT NOT NULL AUTO_INCREMENT,
    chat_id VARCHAR(255),
    name VARCHAR(255),
    user_id BIGINT,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE medicine_group (
    id BIGINT NOT NULL AUTO_INCREMENT,
    intake_time TIME(6),
    is_active BIT(1) NOT NULL,
    name VARCHAR(255),
    schedule_type ENUM('DAILY','INTERVAL','WEEKLY'),
    schedule_value VARCHAR(255),
    start_date DATE,
    user_id BIGINT,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE intake_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    actual_taken_time DATETIME(6),
    intake_date DATE,
    intake_time TIME(6),
    notified_count INTEGER NOT NULL,
    status ENUM('CANCELLED','MISSED','PENDING','TAKEN'),
    medicine_group_id BIGINT,
    user_id BIGINT,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE guardian
    ADD CONSTRAINT FK78238bh00pipro62xntxayb9x
    FOREIGN KEY (user_id) REFERENCES users (id);

ALTER TABLE intake_log
    ADD CONSTRAINT FKlnxe8kcfp1pc8ru7ilstrn8je
    FOREIGN KEY (medicine_group_id) REFERENCES medicine_group (id);

ALTER TABLE intake_log
    ADD CONSTRAINT FKhdvdbdp7v7ixta6rbbqsssax0
    FOREIGN KEY (user_id) REFERENCES users (id);

ALTER TABLE medicine_group
    ADD CONSTRAINT FK6crdqgogb98ragw8no44d4aq4
    FOREIGN KEY (user_id) REFERENCES users (id);
