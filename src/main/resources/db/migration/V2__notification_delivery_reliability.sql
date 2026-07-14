ALTER TABLE intake_log
    ADD CONSTRAINT uk_intake_log_medicine_date
    UNIQUE (medicine_group_id, intake_date);

CREATE TABLE notification_delivery (
    id BIGINT NOT NULL AUTO_INCREMENT,
    intake_log_id BIGINT NOT NULL,
    guardian_id BIGINT NOT NULL,
    alert_key VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempt_count INTEGER NOT NULL,
    next_attempt_at DATETIME(6),
    last_attempt_at DATETIME(6),
    sent_at DATETIME(6),
    last_error VARCHAR(500),
    PRIMARY KEY (id),
    CONSTRAINT uk_notification_delivery_target
        UNIQUE (intake_log_id, guardian_id, alert_key),
    CONSTRAINT fk_notification_delivery_intake
        FOREIGN KEY (intake_log_id) REFERENCES intake_log (id) ON DELETE CASCADE,
    CONSTRAINT fk_notification_delivery_guardian
        FOREIGN KEY (guardian_id) REFERENCES guardian (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
