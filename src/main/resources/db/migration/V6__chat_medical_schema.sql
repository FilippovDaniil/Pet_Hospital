-- =====================================================
-- V6 — Chat system + medical documents + patient notes
-- =====================================================
-- Миграция добавляет три подсистемы:
--   1. Связи patient→users и doctor→users (необходимы для чата и медкарты).
--   2. Чат-систему (chat_room + chat_message).
--   3. Медицинскую документацию (medical_document + patient_note).
-- =====================================================

-- ─────────────────────────────────────────────
-- СВЯЗИ ПАЦИЕНТ ↔ ПОЛЬЗОВАТЕЛЬ ПОРТАЛА
-- ─────────────────────────────────────────────
-- Колонка nullable (без NOT NULL), потому что существующие пациенты,
-- заведённые персоналом вручную, не имеют учётной записи на портале.
-- Связь устанавливается только тогда, когда клиент регистрируется
-- через /api/auth/register-client и привязывает себя к карточке пациента.
-- Без индекса каждый запрос "документы текущего клиента" выполнял бы
-- полное сканирование таблицы patient.
ALTER TABLE patient ADD COLUMN client_user_id BIGINT REFERENCES users(id);
CREATE INDEX idx_patient_client_user ON patient(client_user_id);

-- ─────────────────────────────────────────────
-- СВЯЗЬ ВРАЧ ↔ УЧЁТНАЯ ЗАПИСЬ HIS
-- ─────────────────────────────────────────────
-- doctor.user_id нужен для двух целей:
--   а) определить, какому врачу принадлежит аутентифицированный пользователь
--      при создании документов/заметок без явного doctorId в запросе;
--   б) адресовать чат-комнату типа DOCTOR_CLIENT через users.id,
--      а не через doctor.id — чтобы единообразно использовать users
--      как идентификатор участника во всей чат-системе.
-- Тоже nullable: врачи, заведённые только в таблице doctor без
-- учётной записи в системе, продолжают работать без поломок.
ALTER TABLE doctor ADD COLUMN user_id BIGINT REFERENCES users(id);

-- Автоматически связываем тестового пользователя doctor1 с реальной
-- записью врача Иванова, добавленной в V5. EXISTS-проверка делает
-- UPDATE идемпотентным: если doctor1 или Иванов отсутствуют (например,
-- в чистой тестовой БД), запрос просто не изменяет ни одной строки
-- вместо того чтобы падать с ошибкой.
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
-- staff_user_id объявлен NULL-допустимым намеренно: комнаты поддержки
-- (type = SUPPORT) не привязаны к конкретному сотруднику — любой
-- администратор может подключиться и ответить. NOT NULL сломал бы
-- создание таких комнат без предварительного назначения оператора.
CREATE TABLE chat_room (
    id              BIGSERIAL PRIMARY KEY,
    type            VARCHAR(20) NOT NULL,
    client_user_id  BIGINT      NOT NULL REFERENCES users(id),
    staff_user_id   BIGINT               REFERENCES users(id),
    created_at      TIMESTAMP   NOT NULL DEFAULT now()
);

-- Индексы по обоим участникам: клиент запрашивает «мои чаты» (by client),
-- врач/администратор — «чаты, где я сотрудник» (by staff).
-- Без этих индексов оба запроса стали бы seq scan по всей таблице.
CREATE INDEX idx_chat_room_client ON chat_room(client_user_id);
CREATE INDEX idx_chat_room_staff  ON chat_room(staff_user_id);

-- Частичный уникальный индекс вместо обычного UNIQUE-ограничения:
-- уникальность нужна только внутри одного типа комнат, а не глобально.
-- Обычный UNIQUE по (client_user_id) запретил бы клиенту иметь
-- одновременно комнату поддержки И чат с врачом.
-- WHERE-фильтр ограничивает действие индекса только строками SUPPORT.

-- One support room per client
CREATE UNIQUE INDEX uq_support_room
    ON chat_room(client_user_id)
    WHERE type = 'SUPPORT';

-- Аналогично: один клиент может переписываться с несколькими врачами,
-- но не может открыть два чата с одним и тем же врачом.
-- WHERE-фильтр изолирует уникальность внутри типа DOCTOR_CLIENT.

-- One doctor room per (client, doctor) pair
CREATE UNIQUE INDEX uq_doctor_room
    ON chat_room(client_user_id, staff_user_id)
    WHERE type = 'DOCTOR_CLIENT';

-- ─────────────────────────────────────────────
-- CHAT MESSAGES
-- ─────────────────────────────────────────────
-- ON DELETE CASCADE: при удалении chat_room все её сообщения удаляются
-- автоматически. Это предпочтительнее триггера или каскадного удаления
-- через приложение — атомарно и не требует дополнительного кода.
-- content объявлен TEXT, а не VARCHAR(N): длина сообщения заранее
-- неизвестна, а PostgreSQL хранит TEXT и VARCHAR одинаково эффективно;
-- ограничение длины при необходимости лучше накладывать на уровне
-- валидации DTO (@Size), а не в схеме БД.
CREATE TABLE chat_message (
    id          BIGSERIAL  PRIMARY KEY,
    room_id     BIGINT     NOT NULL REFERENCES chat_room(id) ON DELETE CASCADE,
    sender_id   BIGINT     NOT NULL REFERENCES users(id),
    content     TEXT       NOT NULL,
    sent_at     TIMESTAMP  NOT NULL DEFAULT now(),
    is_read     BOOLEAN    NOT NULL DEFAULT false
);

-- Составной индекс (room_id, sent_at): покрывает оба самых частых запроса —
-- «все сообщения комнаты» и «сообщения комнаты новее момента X» (polling).
-- Порядок колонок принципиален: room_id стоит первым для фильтрации,
-- sent_at вторым для сортировки внутри одной комнаты без доп. сортировки.
CREATE INDEX idx_chat_message_room ON chat_message(room_id, sent_at);

-- ─────────────────────────────────────────────
-- MEDICAL DOCUMENTS (рецепты, направления, справки)
-- ─────────────────────────────────────────────
-- valid_until nullable: для части документов (справки, заметки) срок
-- действия не применим. NULL здесь означает «бессрочно» или «не задан»,
-- а не ошибку данных.
-- is_active позволяет аннулировать документ (например, рецепт отозван)
-- без физического удаления — сохраняется аудиторский след.
-- type VARCHAR(30): значения PRESCRIPTION | REFERRAL | SICK_LEAVE |
-- ANALYSIS_ORDER | CERTIFICATE — без CHECK-ограничения, чтобы добавление
-- новых типов не требовало ALTER TABLE (достаточно изменить enum в коде).
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

-- Два отдельных индекса вместо одного составного: запросы «документы
-- пациента» и «документы врача» фильтруют по одному полю каждый.
-- Составной индекс (patient_id, doctor_id) ускорял бы только запросы
-- с обоими условиями одновременно, что не является типичным сценарием.
CREATE INDEX idx_medical_doc_patient ON medical_document(patient_id);
CREATE INDEX idx_medical_doc_doctor  ON medical_document(doctor_id);

-- ─────────────────────────────────────────────
-- PATIENT NOTES (диагнозы, наблюдения, заметки)
-- visible_to_client = true → пациент видит запись в личном кабинете
-- ─────────────────────────────────────────────
-- visible_to_client разделяет «медицинскую тайну» от «выписки для пациента»:
-- врач может делать внутренние заметки (false), не опасаясь, что клиент
-- увидит служебные пометки. Только явно помеченные записи (true) попадают
-- в ответ /api/medical/history/my.
-- Заметки намеренно не имеют is_active/soft-delete: они образуют
-- неизменяемую историческую хронологию — даже ошибочная запись должна
-- оставаться с пометкой об исправлении, а не исчезать.
-- content — TEXT по той же причине, что и в chat_message: длина заранее
-- не ограничена (диагноз может быть очень подробным).
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
