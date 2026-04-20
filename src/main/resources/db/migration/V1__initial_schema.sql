-- =====================================================
-- V1 - Initial schema for Hospital Information System
-- =====================================================

CREATE TABLE department
(
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(255) NOT NULL,
    description   TEXT,
    location      VARCHAR(255),
    head_doctor_id BIGINT
);

CREATE TABLE doctor
(
    id             BIGSERIAL PRIMARY KEY,
    full_name      VARCHAR(255) NOT NULL,
    specialty      VARCHAR(100) NOT NULL,
    cabinet_number VARCHAR(50),
    phone          VARCHAR(20),
    department_id  BIGINT REFERENCES department (id),
    active         BOOLEAN      NOT NULL DEFAULT TRUE
);

-- Head doctor FK added after doctor table exists
ALTER TABLE department
    ADD CONSTRAINT fk_dept_head_doctor FOREIGN KEY (head_doctor_id) REFERENCES doctor (id);

CREATE TABLE ward
(
    id                BIGSERIAL PRIMARY KEY,
    ward_number       VARCHAR(50) NOT NULL,
    capacity          INT         NOT NULL,
    current_occupancy INT         NOT NULL DEFAULT 0,
    department_id     BIGINT      NOT NULL REFERENCES department (id),
    CONSTRAINT uq_ward_number_dept UNIQUE (ward_number, department_id)
);

CREATE TABLE patient
(
    id                BIGSERIAL PRIMARY KEY,
    full_name         VARCHAR(255) NOT NULL,
    birth_date        DATE         NOT NULL,
    gender            VARCHAR(10)  NOT NULL,
    snils             VARCHAR(20)  NOT NULL,
    phone             VARCHAR(20),
    address           TEXT,
    registration_date DATE         NOT NULL DEFAULT CURRENT_DATE,
    status            VARCHAR(20)  NOT NULL DEFAULT 'TREATMENT',
    current_doctor_id BIGINT REFERENCES doctor (id),
    current_ward_id   BIGINT REFERENCES ward (id),
    active            BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_patient_snils UNIQUE (snils)
);

CREATE TABLE paid_service
(
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255)   NOT NULL,
    price       DECIMAL(10, 2) NOT NULL,
    description TEXT,
    active      BOOLEAN        NOT NULL DEFAULT TRUE
);

CREATE TABLE patient_paid_service
(
    id              BIGSERIAL PRIMARY KEY,
    patient_id      BIGINT    NOT NULL REFERENCES patient (id),
    paid_service_id BIGINT    NOT NULL REFERENCES paid_service (id),
    assigned_date   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_paid         BOOLEAN   NOT NULL DEFAULT FALSE
);

CREATE TABLE patient_doctor_history
(
    id            BIGSERIAL PRIMARY KEY,
    patient_id    BIGINT    NOT NULL REFERENCES patient (id),
    doctor_id     BIGINT    NOT NULL REFERENCES doctor (id),
    assigned_from TIMESTAMP NOT NULL,
    assigned_to   TIMESTAMP
);

CREATE TABLE ward_occupation_history
(
    id           BIGSERIAL PRIMARY KEY,
    patient_id   BIGINT    NOT NULL REFERENCES patient (id),
    ward_id      BIGINT    NOT NULL REFERENCES ward (id),
    admitted_at  TIMESTAMP NOT NULL,
    discharged_at TIMESTAMP
);

-- Outbox table for event idempotency tracking
CREATE TABLE outbox_event
(
    id         BIGSERIAL PRIMARY KEY,
    event_id   VARCHAR(255) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload    TEXT         NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed  BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_outbox_event_id UNIQUE (event_id)
);

CREATE INDEX idx_patient_status ON patient (status) WHERE active = TRUE;
CREATE INDEX idx_patient_doctor ON patient (current_doctor_id) WHERE active = TRUE;
CREATE INDEX idx_patient_ward ON patient (current_ward_id);
CREATE INDEX idx_doctor_specialty ON doctor (specialty) WHERE active = TRUE;
CREATE INDEX idx_doctor_dept ON doctor (department_id);
CREATE INDEX idx_outbox_processed ON outbox_event (processed, created_at);
