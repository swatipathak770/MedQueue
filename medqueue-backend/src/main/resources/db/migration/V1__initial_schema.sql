CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    email VARCHAR(190) NOT NULL,
    name VARCHAR(120) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    phone VARCHAR(30) NULL,
    role ENUM('ADMIN', 'DOCTOR', 'PATIENT') NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE departments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_departments_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE doctors (
    id BIGINT NOT NULL AUTO_INCREMENT,
    avg_consult_minutes INT NOT NULL,
    specialization VARCHAR(120) NOT NULL,
    department_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    available BIT(1) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_doctors_user UNIQUE (user_id),
    CONSTRAINT FKe9pf5qtxxkdyrwibaevo9frtk FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT FKl2mro81neln9topymd898urh1 FOREIGN KEY (department_id) REFERENCES departments (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE slots (
    id BIGINT NOT NULL AUTO_INCREMENT,
    day_of_week ENUM('FRIDAY', 'MONDAY', 'SATURDAY', 'SUNDAY', 'THURSDAY', 'TUESDAY', 'WEDNESDAY') NOT NULL,
    end_time TIME(6) NOT NULL,
    slot_capacity INT NOT NULL,
    start_time TIME(6) NOT NULL,
    doctor_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    KEY idx_slots_doctor_day (doctor_id, day_of_week),
    CONSTRAINT FKkqli1c13rmv2ee4f3c8u9utr1 FOREIGN KEY (doctor_id) REFERENCES doctors (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE appointments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    appointment_date DATE NOT NULL,
    called_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    status ENUM('CALLED', 'CANCELLED', 'DONE', 'IN_PROGRESS', 'SKIPPED', 'WAITING') NOT NULL,
    token_number INT NOT NULL,
    version BIGINT NOT NULL,
    doctor_id BIGINT NOT NULL,
    patient_id BIGINT NOT NULL,
    slot_id BIGINT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_appointments_doctor_date_token UNIQUE (doctor_id, appointment_date, token_number),
    KEY idx_appointments_doctor_date_status (doctor_id, appointment_date, status),
    CONSTRAINT FKf8qrv9g386dae81yfkj1qgs77 FOREIGN KEY (slot_id) REFERENCES slots (id),
    CONSTRAINT FKmujeo4tymoo98cmf7uj3vsv76 FOREIGN KEY (doctor_id) REFERENCES doctors (id),
    CONSTRAINT FKopb2h9yhin1rb4dqote8bws6w FOREIGN KEY (patient_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE queue_states (
    id BIGINT NOT NULL AUTO_INCREMENT,
    current_token_number INT NOT NULL,
    last_updated DATETIME(6) NOT NULL,
    queue_open BIT(1) NOT NULL,
    queue_date DATE NOT NULL,
    doctor_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_queue_state_doctor_date UNIQUE (doctor_id, queue_date),
    CONSTRAINT FKnrm1momnh0kvisaovnexm2r03 FOREIGN KEY (doctor_id) REFERENCES doctors (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
