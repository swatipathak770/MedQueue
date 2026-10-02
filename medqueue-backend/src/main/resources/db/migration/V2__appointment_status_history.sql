CREATE TABLE appointment_status_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    appointment_id BIGINT NOT NULL,
    old_status VARCHAR(20) NULL,
    new_status VARCHAR(20) NOT NULL,
    changed_at TIMESTAMP(6) NOT NULL,
    changed_by VARCHAR(40) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_appointment_status_history_appointment_changed (appointment_id, changed_at),
    CONSTRAINT fk_appointment_status_history_appointment
        FOREIGN KEY (appointment_id) REFERENCES appointments (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
