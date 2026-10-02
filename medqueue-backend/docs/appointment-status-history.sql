-- LEGACY MANUAL PROCEDURE — superseded by
-- src/main/resources/db/migration/V2__appointment_status_history.sql.
-- Do not use as a competing schema-management path. For existing databases, follow
-- the verified Flyway baseline procedure in the repository README / AWS guide.
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
