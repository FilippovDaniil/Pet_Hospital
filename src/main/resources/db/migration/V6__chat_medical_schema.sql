-- =====================================================
-- V6 — Chat system + medical documents + patient notes
-- =====================================================

-- Link patient records to portal client accounts (nullable)
ALTER TABLE patient ADD COLUMN client_user_id BIGINT REFERENCES users(id);
CREATE INDEX idx_patient_client_user ON patient(client_user_id);

-- Link doctor records to HIS user accounts (nullable)
ALTER TABLE doctor ADD COLUMN user_id BIGINT REFERENCES users(id);

-- Automatically link doctor1 user to Иванов doctor (if both exist)
UPDATE doctor
SET user_id = (SELECT id FROM users WHERE username = 'doctor1')
WHERE full_name = 'Иванов Сергей Петрович'
  AND EXISTS (SELECT 1 FROM users WHERE username = 'doctor1');

-- ─────────────────────────────────────────────
-- CHAT ROOMS
-- type = SUPPORT       → client ↔ any admin
-- type = DOCTOR_CLIENT → client ↔ specific doctor user
-- For SUPPORT rooms staff_user_id is NULL; all admins see all SUPPORT rooms.
-- ─────────────────────────────────────────────
CREATE TABLE chat_room (
    id              BIGSERIAL PRIMARY KEY,
    type            VARCHAR(20) NOT NULL,
    client_user_id  BIGINT      NOT NULL REFERENCES users(id),
    staff_user_id   BIGINT               REFERENCES users(id),
    created_at      TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE INDEX idx_chat_room_client ON chat_room(client_user_id);
CREATE INDEX idx_chat_room_staff  ON chat_room(staff_user_id);

-- One support room per client
CREATE UNIQUE INDEX uq_support_room
    ON chat_room(client_user_id)
    WHERE type = 'SUPPORT';

-- One doctor room per (client, doctor) pair
CREATE UNIQUE INDEX uq_doctor_room
    ON chat_room(client_user_id, staff_user_id)
    WHERE type = 'DOCTOR_CLIENT';

-- ─────────────────────────────────────────────
-- CHAT MESSAGES
-- ─────────────────────────────────────────────
CREATE TABLE chat_message (
    id          BIGSERIAL  PRIMARY KEY,
    room_id     BIGINT     NOT NULL REFERENCES chat_room(id) ON DELETE CASCADE,
    sender_id   BIGINT     NOT NULL REFERENCES users(id),
    content     TEXT       NOT NULL,
    sent_at     TIMESTAMP  NOT NULL DEFAULT now(),
    is_read     BOOLEAN    NOT NULL DEFAULT false
);

CREATE INDEX idx_chat_message_room ON chat_message(room_id, sent_at);

-- ─────────────────────────────────────────────
-- MEDICAL DOCUMENTS (рецепты, направления, справки)
-- ─────────────────────────────────────────────
CREATE TABLE medical_document (
    id          BIGSERIAL    PRIMARY KEY,
    patient_id  BIGINT       NOT NULL REFERENCES patient(id),
    doctor_id   BIGINT       NOT NULL REFERENCES doctor(id),
    type        VARCHAR(30)  NOT NULL,  -- PRESCRIPTION | REFERRAL | SICK_LEAVE | ANALYSIS_ORDER | CERTIFICATE
    title       VARCHAR(255) NOT NULL,
    content     TEXT         NOT NULL,
    issued_at   TIMESTAMP    NOT NULL DEFAULT now(),
    valid_until DATE,
    is_active   BOOLEAN      NOT NULL DEFAULT true
);

CREATE INDEX idx_medical_doc_patient ON medical_document(patient_id);
CREATE INDEX idx_medical_doc_doctor  ON medical_document(doctor_id);

-- ─────────────────────────────────────────────
-- PATIENT NOTES (диагнозы, наблюдения, заметки)
-- visible_to_client = true → пациент видит запись в личном кабинете
-- ─────────────────────────────────────────────
CREATE TABLE patient_note (
    id                BIGSERIAL   PRIMARY KEY,
    patient_id        BIGINT      NOT NULL REFERENCES patient(id),
    doctor_id         BIGINT      NOT NULL REFERENCES doctor(id),
    type              VARCHAR(20) NOT NULL,  -- NOTE | DIAGNOSIS | OBSERVATION
    content           TEXT        NOT NULL,
    visible_to_client BOOLEAN     NOT NULL DEFAULT false,
    created_at        TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE INDEX idx_patient_note_patient ON patient_note(patient_id);
CREATE INDEX idx_patient_note_doctor  ON patient_note(doctor_id);
