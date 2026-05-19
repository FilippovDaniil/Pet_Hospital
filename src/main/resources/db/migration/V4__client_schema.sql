-- =====================================================
-- V4 — Client portal schema: appointment & service orders
-- =====================================================

CREATE TABLE appointment (
    id              BIGSERIAL    PRIMARY KEY,
    client_user_id  BIGINT       NOT NULL REFERENCES users(id),
    doctor_id       BIGINT       NOT NULL REFERENCES doctor(id),
    preferred_date  DATE         NOT NULL,
    preferred_time  VARCHAR(20),
    contact_phone   VARCHAR(25),
    notes           TEXT,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    created_at      TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_appointment_client ON appointment (client_user_id);
CREATE INDEX idx_appointment_doctor ON appointment (doctor_id);
CREATE INDEX idx_appointment_status ON appointment (status);

CREATE TABLE client_service_order (
    id              BIGSERIAL    PRIMARY KEY,
    client_user_id  BIGINT       NOT NULL REFERENCES users(id),
    paid_service_id BIGINT       NOT NULL REFERENCES paid_service(id),
    contact_phone   VARCHAR(25),
    notes           TEXT,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    created_at      TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_service_order_client  ON client_service_order (client_user_id);
CREATE INDEX idx_service_order_service ON client_service_order (paid_service_id);
