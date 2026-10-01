-- Apply once to an existing MedQueue MySQL database before deploying the audit-enabled backend.
CREATE TABLE IF NOT EXISTS appointment_status_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    appointment_id BIGINT NOT NULL,
    old_status VARCHAR(20) NULL,
    new_status VARCHAR(20) NOT NULL,
    changed_at TIMESTAMP(6) NOT NULL,
    changed_by VARCHAR(40) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_appointment_status_history_appointment_changed (appointment_id, changed_at),
    CONSTRAINT fk_appointment_status_history_appointment
        FOREIGN KEY (appointment_id) REFERENCES appointments (id)
);
