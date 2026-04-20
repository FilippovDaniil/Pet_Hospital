-- =====================================================
-- V2 - Test seed data: 2 departments, 3 doctors,
--       5 patients, 4 wards, 2 paid services
-- =====================================================

-- Departments (no head_doctor yet; updated after doctors inserted)
INSERT INTO department (id, name, description, location)
VALUES (1, 'Кардиология', 'Отделение кардиологии и сердечно-сосудистых заболеваний', '3 этаж, корпус А'),
       (2, 'Хирургия', 'Хирургическое отделение', '2 этаж, корпус Б');

-- Doctors
INSERT INTO doctor (id, full_name, specialty, cabinet_number, phone, department_id, active)
VALUES (1, 'Иванов Сергей Петрович', 'CARDIOLOGIST', '301', '+7-900-100-0001', 1, TRUE),
       (2, 'Петрова Мария Андреевна', 'SURGEON', '201', '+7-900-100-0002', 2, TRUE),
       (3, 'Сидоров Алексей Николаевич', 'THERAPIST', '302', '+7-900-100-0003', 1, TRUE);

-- Set head doctors for departments
UPDATE department SET head_doctor_id = 1 WHERE id = 1;
UPDATE department SET head_doctor_id = 2 WHERE id = 2;

-- Wards
INSERT INTO ward (id, ward_number, capacity, current_occupancy, department_id)
VALUES (1, '301A', 3, 0, 1),
       (2, '301B', 2, 0, 1),
       (3, '201A', 4, 0, 2),
       (4, '201B', 2, 0, 2);

-- Patients
INSERT INTO patient (id, full_name, birth_date, gender, snils, phone, address, registration_date, status,
                     current_doctor_id, current_ward_id, active)
VALUES (1, 'Козлов Дмитрий Иванович', '1975-03-15', 'MALE', '123-456-789 01', '+7-900-200-0001',
        'г. Москва, ул. Ленина, д.1', '2024-01-10', 'TREATMENT', 1, 1, TRUE),
       (2, 'Новикова Елена Сергеевна', '1988-07-22', 'FEMALE', '234-567-890 12', '+7-900-200-0002',
        'г. Москва, ул. Мира, д.5', '2024-01-15', 'TREATMENT', 1, 1, TRUE),
       (3, 'Морозов Андрей Викторович', '1960-11-08', 'MALE', '345-678-901 23', '+7-900-200-0003',
        'г. Москва, пр. Победы, д.12', '2024-01-20', 'TREATMENT', 2, 3, TRUE),
       (4, 'Волкова Татьяна Олеговна', '1995-04-30', 'FEMALE', '456-789-012 34', '+7-900-200-0004',
        'г. Москва, ул. Садовая, д.7', '2024-02-01', 'DISCHARGED', NULL, NULL, TRUE),
       (5, 'Лебедев Константин Михайлович', '1952-09-14', 'MALE', '567-890-123 45', '+7-900-200-0005',
        'г. Москва, ул. Пушкина, д.3', '2024-02-05', 'TREATMENT', 3, 2, TRUE);

-- Update ward occupancy to match patients
UPDATE ward SET current_occupancy = 2 WHERE id = 1;
UPDATE ward SET current_occupancy = 1 WHERE id = 2;
UPDATE ward SET current_occupancy = 1 WHERE id = 3;

-- Paid services
INSERT INTO paid_service (id, name, price, description, active)
VALUES (1, 'ЭКГ расширенное', 2500.00, 'Расширенное электрокардиографическое исследование', TRUE),
       (2, 'УЗИ сердца', 3800.00, 'Ультразвуковое исследование сердца (эхокардиография)', TRUE);

-- History records for existing patients
INSERT INTO patient_doctor_history (patient_id, doctor_id, assigned_from, assigned_to)
VALUES (1, 1, '2024-01-10 09:00:00', NULL),
       (2, 1, '2024-01-15 10:00:00', NULL),
       (3, 2, '2024-01-20 11:00:00', NULL),
       (5, 3, '2024-02-05 08:00:00', NULL);

INSERT INTO ward_occupation_history (patient_id, ward_id, admitted_at, discharged_at)
VALUES (1, 1, '2024-01-10 12:00:00', NULL),
       (2, 1, '2024-01-15 13:00:00', NULL),
       (3, 3, '2024-01-20 14:00:00', NULL),
       (5, 2, '2024-02-05 09:00:00', NULL);

-- Reset sequences to avoid PK conflicts on new inserts
SELECT setval('department_id_seq', (SELECT MAX(id) FROM department));
SELECT setval('doctor_id_seq', (SELECT MAX(id) FROM doctor));
SELECT setval('ward_id_seq', (SELECT MAX(id) FROM ward));
SELECT setval('patient_id_seq', (SELECT MAX(id) FROM patient));
SELECT setval('paid_service_id_seq', (SELECT MAX(id) FROM paid_service));
