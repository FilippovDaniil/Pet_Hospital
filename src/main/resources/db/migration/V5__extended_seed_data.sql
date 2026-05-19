-- =====================================================
-- V5 — Extended seed data: realistic hospital data
--      +8 departments, +22 doctors, +32 wards,
--      +40 patients, +20 paid services
-- =====================================================

-- =====================
-- DEPARTMENTS (8 more)
-- =====================
INSERT INTO department (name, description, location) VALUES
  ('Неврология',    'Диагностика и лечение заболеваний нервной системы',              '4 этаж, корпус А'),
  ('Ортопедия',     'Хирургическое и консервативное лечение опорно-двигательного аппарата', '5 этаж, корпус Б'),
  ('Онкология',     'Диагностика и лечение онкологических заболеваний',                '6 этаж, корпус В'),
  ('Педиатрия',     'Медицинская помощь детям от 0 до 18 лет',                         '7 этаж, корпус А'),
  ('Урология',      'Лечение заболеваний мочеполовой системы',                         '8 этаж, корпус Б'),
  ('Терапия',       'Диагностика и лечение внутренних болезней',                       '1 этаж, корпус А'),
  ('Офтальмология', 'Диагностика и лечение заболеваний органов зрения',               '2 этаж, корпус В'),
  ('Эндокринология','Лечение заболеваний эндокринной системы и обмена веществ',        '3 этаж, корпус В');

-- =====================
-- DOCTORS (22 more)
-- =====================
-- dept IDs: Кардиология=1, Хирургия=2, Неврология=3, Ортопедия=4,
--           Онкология=5, Педиатрия=6, Урология=7, Терапия=8,
--           Офтальмология=9, Эндокринология=10

-- Cardiology (2 more)
INSERT INTO doctor (full_name, specialty, cabinet_number, phone, department_id, active) VALUES
  ('Громов Виктор Евгеньевич',       'CARDIOLOGIST', '303', '+7-900-101-0004', 1, TRUE),
  ('Смирнова Наталья Борисовна',      'CARDIOLOGIST', '304', '+7-900-101-0005', 1, TRUE);

-- Surgery (2 more)
INSERT INTO doctor (full_name, specialty, cabinet_number, phone, department_id, active) VALUES
  ('Лысенко Дмитрий Александрович',   'SURGEON',      '202', '+7-900-102-0003', 2, TRUE),
  ('Власова Ирина Степановна',         'SURGEON',      '203', '+7-900-102-0004', 2, TRUE);

-- Neurology (3)
INSERT INTO doctor (full_name, specialty, cabinet_number, phone, department_id, active) VALUES
  ('Захаров Андрей Михайлович',        'NEUROLOGIST',  '401', '+7-900-103-0001', 3, TRUE),
  ('Полякова Светлана Ивановна',       'NEUROLOGIST',  '402', '+7-900-103-0002', 3, TRUE),
  ('Орехов Игорь Юрьевич',             'NEUROLOGIST',  '403', '+7-900-103-0003', 3, TRUE);

-- Orthopedics (3)
INSERT INTO doctor (full_name, specialty, cabinet_number, phone, department_id, active) VALUES
  ('Беляев Константин Семёнович',     'ORTHOPEDIST',  '501', '+7-900-104-0001', 4, TRUE),
  ('Кузьмина Ольга Романовна',         'ORTHOPEDIST',  '502', '+7-900-104-0002', 4, TRUE),
  ('Мартынов Павел Геннадьевич',       'ORTHOPEDIST',  '503', '+7-900-104-0003', 4, TRUE);

-- Oncology (3)
INSERT INTO doctor (full_name, specialty, cabinet_number, phone, department_id, active) VALUES
  ('Федосеев Алексей Владимирович',   'ONCOLOGIST',   '601', '+7-900-105-0001', 5, TRUE),
  ('Никитина Галина Фёдоровна',        'ONCOLOGIST',   '602', '+7-900-105-0002', 5, TRUE),
  ('Цветков Вячеслав Сергеевич',       'ONCOLOGIST',   '603', '+7-900-105-0003', 5, TRUE);

-- Pediatrics (3)
INSERT INTO doctor (full_name, specialty, cabinet_number, phone, department_id, active) VALUES
  ('Романова Анна Викторовна',         'PEDIATRICIAN', '701', '+7-900-106-0001', 6, TRUE),
  ('Дорогин Илья Николаевич',          'PEDIATRICIAN', '702', '+7-900-106-0002', 6, TRUE),
  ('Мельникова Екатерина Игоревна',    'PEDIATRICIAN', '703', '+7-900-106-0003', 6, TRUE);

-- Urology (3)
INSERT INTO doctor (full_name, specialty, cabinet_number, phone, department_id, active) VALUES
  ('Козырев Борис Иванович',           'UROLOGIST',    '801', '+7-900-107-0001', 7, TRUE),
  ('Шевченко Наталья Петровна',        'UROLOGIST',    '802', '+7-900-107-0002', 7, TRUE),
  ('Крылов Сергей Геннадьевич',        'UROLOGIST',    '803', '+7-900-107-0003', 7, TRUE);

-- Therapy (3) — THERAPIST specialty
INSERT INTO doctor (full_name, specialty, cabinet_number, phone, department_id, active) VALUES
  ('Тарасова Людмила Витальевна',      'THERAPIST',    '901', '+7-900-108-0001', 8, TRUE),
  ('Воробьёв Максим Александрович',    'THERAPIST',    '902', '+7-900-108-0002', 8, TRUE),
  ('Кириллова Марина Евгеньевна',      'THERAPIST',    '903', '+7-900-108-0003', 8, TRUE);

-- Ophthalmology & Endocrinology use THERAPIST/SURGEON specialties (no dedicated enum values)
INSERT INTO doctor (full_name, specialty, cabinet_number, phone, department_id, active) VALUES
  ('Громова Елена Игоревна',           'SURGEON',      '210', '+7-900-109-0001', 9, TRUE),
  ('Фомин Александр Дмитриевич',       'THERAPIST',    '211', '+7-900-109-0002', 9, TRUE),
  ('Нечаева Ольга Васильевна',         'THERAPIST',    '310', '+7-900-110-0001', 10, TRUE),
  ('Борисов Кирилл Геннадьевич',       'THERAPIST',    '311', '+7-900-110-0002', 10, TRUE);

-- Set head doctors for new departments
-- Neuro dept(3) → Захаров (id dynamically assigned, use subquery)
UPDATE department SET head_doctor_id = (SELECT id FROM doctor WHERE full_name = 'Захаров Андрей Михайлович' LIMIT 1) WHERE name = 'Неврология';
UPDATE department SET head_doctor_id = (SELECT id FROM doctor WHERE full_name = 'Беляев Константин Семёнович' LIMIT 1) WHERE name = 'Ортопедия';
UPDATE department SET head_doctor_id = (SELECT id FROM doctor WHERE full_name = 'Федосеев Алексей Владимирович' LIMIT 1) WHERE name = 'Онкология';
UPDATE department SET head_doctor_id = (SELECT id FROM doctor WHERE full_name = 'Романова Анна Викторовна' LIMIT 1) WHERE name = 'Педиатрия';
UPDATE department SET head_doctor_id = (SELECT id FROM doctor WHERE full_name = 'Козырев Борис Иванович' LIMIT 1) WHERE name = 'Урология';
UPDATE department SET head_doctor_id = (SELECT id FROM doctor WHERE full_name = 'Тарасова Людмила Витальевна' LIMIT 1) WHERE name = 'Терапия';
UPDATE department SET head_doctor_id = (SELECT id FROM doctor WHERE full_name = 'Громова Елена Игоревна' LIMIT 1) WHERE name = 'Офтальмология';
UPDATE department SET head_doctor_id = (SELECT id FROM doctor WHERE full_name = 'Нечаева Ольга Васильевна' LIMIT 1) WHERE name = 'Эндокринология';

-- =====================
-- WARDS (4 per new dept)
-- =====================
-- Cardiology 2 more + Neurology, Orthopedics, Oncology, Pediatrics, Urology, Therapy, Ophthalmology, Endocrinology

INSERT INTO ward (ward_number, capacity, current_occupancy, department_id) VALUES
  -- Cardiology (2 more)
  ('301C', 3, 0, 1), ('301D', 2, 0, 1),
  -- Surgery (2 more)
  ('201C', 3, 0, 2), ('201D', 2, 0, 2),
  -- Neurology (4)
  ('401A', 4, 0, 3), ('401B', 3, 0, 3), ('401C', 2, 0, 3), ('401D', 3, 0, 3),
  -- Orthopedics (4)
  ('501A', 4, 0, 4), ('501B', 3, 0, 4), ('501C', 2, 0, 4), ('501D', 3, 0, 4),
  -- Oncology (4)
  ('601A', 3, 0, 5), ('601B', 2, 0, 5), ('601C', 3, 0, 5), ('601D', 2, 0, 5),
  -- Pediatrics (4)
  ('701A', 4, 0, 6), ('701B', 3, 0, 6), ('701C', 2, 0, 6), ('701D', 3, 0, 6),
  -- Urology (4)
  ('801A', 3, 0, 7), ('801B', 2, 0, 7), ('801C', 3, 0, 7), ('801D', 2, 0, 7),
  -- Therapy (4)
  ('901A', 4, 0, 8), ('901B', 3, 0, 8), ('901C', 3, 0, 8), ('901D', 2, 0, 8),
  -- Ophthalmology (2)
  ('210A', 3, 0, 9), ('210B', 2, 0, 9),
  -- Endocrinology (2)
  ('310A', 3, 0, 10), ('310B', 2, 0, 10);

-- =====================
-- PAID SERVICES (20 more)
-- =====================
INSERT INTO paid_service (name, price, description, active) VALUES
  ('МРТ головного мозга',          6500.00, 'Магнитно-резонансная томография головного мозга с контрастом', TRUE),
  ('КТ грудной клетки',            4200.00, 'Компьютерная томография грудной клетки',                       TRUE),
  ('Общий анализ крови',            450.00, 'Клинический анализ крови с лейкоцитарной формулой',            TRUE),
  ('Биохимический анализ крови',   1200.00, 'Расширенный биохимический анализ крови (20 показателей)',       TRUE),
  ('Общий анализ мочи',             350.00, 'Клинический анализ мочи с микроскопией',                        TRUE),
  ('Консультация кардиолога',      2000.00, 'Первичный приём кардиолога с ЭКГ',                             TRUE),
  ('Консультация невролога',       1800.00, 'Первичный приём невролога',                                    TRUE),
  ('Консультация хирурга',         1500.00, 'Первичный приём хирурга',                                      TRUE),
  ('Рентген грудной клетки',       1100.00, 'Обзорная рентгенография органов грудной клетки',               TRUE),
  ('Гастроскопия',                 3500.00, 'Эзофагогастродуоденоскопия (ЭГДС)',                            TRUE),
  ('Колоноскопия',                 4500.00, 'Эндоскопическое исследование толстого кишечника',              TRUE),
  ('УЗИ брюшной полости',          2200.00, 'Ультразвуковое исследование органов брюшной полости',          TRUE),
  ('УЗИ щитовидной железы',        1600.00, 'Ультразвуковое исследование щитовидной железы',                TRUE),
  ('Холтер-мониторирование ЭКГ',  3200.00, 'Суточное мониторирование ЭКГ по Холтеру (24 часа)',            TRUE),
  ('Суточное мониторирование АД',  2800.00, 'Амбулаторное суточное мониторирование артериального давления', TRUE),
  ('Допплерография сосудов',       2400.00, 'УЗДГ сосудов нижних конечностей',                             TRUE),
  ('Спирометрия',                  1500.00, 'Исследование функции внешнего дыхания',                        TRUE),
  ('ПЦР-анализ',                   1200.00, 'Полимеразная цепная реакция (ПЦР) — 1 показатель',            TRUE),
  ('Цитологическое исследование',  1800.00, 'Цитологическое исследование биоматериала',                    TRUE),
  ('Денситометрия',                2500.00, 'Двухэнергетическая рентгеновская абсорбциометрия (костная минерализация)', TRUE);

-- =====================
-- PATIENTS (40 more)
-- Using dynamic doctor/ward references to avoid hardcoded IDs
-- =====================
INSERT INTO patient (full_name, birth_date, gender, snils, phone, address, registration_date, status, current_doctor_id, current_ward_id, active)
VALUES
-- Neurology patients (in treatment, using Захаров / ward 401A)
('Алексеев Роман Борисович',     '1978-04-12', 'MALE',   '600-100-200 01', '+7-910-301-0001',
 'г. Москва, ул. Тверская, д.15', '2024-03-01', 'TREATMENT',
 (SELECT id FROM doctor WHERE full_name='Захаров Андрей Михайлович'),
 (SELECT id FROM ward   WHERE ward_number='401A'), TRUE),

('Быкова Оксана Николаевна',     '1985-09-20', 'FEMALE', '600-100-200 02', '+7-910-301-0002',
 'г. Москва, ул. Арбат, д.8',    '2024-03-05', 'TREATMENT',
 (SELECT id FROM doctor WHERE full_name='Полякова Светлана Ивановна'),
 (SELECT id FROM ward   WHERE ward_number='401A'), TRUE),

('Волков Максим Петрович',       '1962-07-30', 'MALE',   '600-100-200 03', '+7-910-301-0003',
 'г. Москва, пр. Мира, д.22',    '2024-03-10', 'TREATMENT',
 (SELECT id FROM doctor WHERE full_name='Орехов Игорь Юрьевич'),
 (SELECT id FROM ward   WHERE ward_number='401B'), TRUE),

('Герасимова Ирина Фёдоровна',   '1990-01-15', 'FEMALE', '600-100-200 04', '+7-910-301-0004',
 'г. Москва, ул. Садовая, д.3',  '2024-03-12', 'DISCHARGED',
 NULL, NULL, TRUE),

-- Orthopedics patients
('Дьяков Сергей Анатольевич',    '1955-11-25', 'MALE',   '600-100-200 05', '+7-910-302-0001',
 'г. Москва, ул. Ленинградская, д.9', '2024-02-20', 'TREATMENT',
 (SELECT id FROM doctor WHERE full_name='Беляев Константин Семёнович'),
 (SELECT id FROM ward   WHERE ward_number='501A'), TRUE),

('Егорова Светлана Игоревна',    '1972-06-08', 'FEMALE', '600-100-200 06', '+7-910-302-0002',
 'г. Москва, ул. Профсоюзная, д.47', '2024-02-25', 'TREATMENT',
 (SELECT id FROM doctor WHERE full_name='Кузьмина Ольга Романовна'),
 (SELECT id FROM ward   WHERE ward_number='501A'), TRUE),

('Жуков Владимир Иванович',      '1945-03-17', 'MALE',   '600-100-200 07', '+7-910-302-0003',
 'г. Москва, пр. Вернадского, д.15', '2024-03-01', 'TREATMENT',
 (SELECT id FROM doctor WHERE full_name='Мартынов Павел Геннадьевич'),
 (SELECT id FROM ward   WHERE ward_number='501B'), TRUE),

('Зайцева Марина Дмитриевна',    '1988-12-04', 'FEMALE', '600-100-200 08', '+7-910-302-0004',
 'г. Москва, ул. Новослободская, д.31', '2024-03-08', 'DISCHARGED',
 NULL, NULL, TRUE),

-- Oncology patients
('Иванченко Пётр Семёнович',     '1950-08-19', 'MALE',   '600-100-200 09', '+7-910-303-0001',
 'г. Москва, ул. Кировоградская, д.4', '2024-01-15', 'TREATMENT',
 (SELECT id FROM doctor WHERE full_name='Федосеев Алексей Владимирович'),
 (SELECT id FROM ward   WHERE ward_number='601A'), TRUE),

('Кириллова Надежда Васильевна', '1963-05-22', 'FEMALE', '600-100-200 10', '+7-910-303-0002',
 'г. Москва, ул. Кастанаевская, д.22', '2024-01-20', 'TREATMENT',
 (SELECT id FROM doctor WHERE full_name='Никитина Галина Фёдоровна'),
 (SELECT id FROM ward   WHERE ward_number='601A'), TRUE),

('Лазарев Григорий Юрьевич',    '1957-02-28', 'MALE',   '600-100-200 11', '+7-910-303-0003',
 'г. Москва, ул. Молодёжная, д.7', '2024-02-01', 'TREATMENT',
 (SELECT id FROM doctor WHERE full_name='Цветков Вячеслав Сергеевич'),
 (SELECT id FROM ward   WHERE ward_number='601B'), TRUE),

('Макарова Галина Петровна',     '1971-10-03', 'FEMALE', '600-100-200 12', '+7-910-303-0004',
 'г. Москва, ул. Строительная, д.13', '2024-02-10', 'TRANSFERRED',
 NULL, NULL, TRUE),

-- Pediatrics patients
('Никифоров Тимофей Олегович',   '2015-07-11', 'MALE',   '600-100-200 13', '+7-910-304-0001',
 'г. Москва, ул. Фестивальная, д.5', '2024-03-15', 'TREATMENT',
 (SELECT id FROM doctor WHERE full_name='Романова Анна Викторовна'),
 (SELECT id FROM ward   WHERE ward_number='701A'), TRUE),

('Орлова Полина Ивановна',       '2018-03-25', 'FEMALE', '600-100-200 14', '+7-910-304-0002',
 'г. Москва, ул. Академика Янгеля, д.2', '2024-03-17', 'TREATMENT',
 (SELECT id FROM doctor WHERE full_name='Дорогин Илья Николаевич'),
 (SELECT id FROM ward   WHERE ward_number='701A'), TRUE),

('Павлов Антон Кириллович',      '2010-11-09', 'MALE',   '600-100-200 15', '+7-910-304-0003',
 'г. Москва, пр. Рязанский, д.18', '2024-03-20', 'TREATMENT',
 (SELECT id FROM doctor WHERE full_name='Мельникова Екатерина Игоревна'),
 (SELECT id FROM ward   WHERE ward_number='701B'), TRUE),

-- Urology patients
('Рябов Станислав Андреевич',    '1968-09-14', 'MALE',   '600-100-200 16', '+7-910-305-0001',
 'г. Москва, ул. Братиславская, д.11', '2024-02-15', 'TREATMENT',
 (SELECT id FROM doctor WHERE full_name='Козырев Борис Иванович'),
 (SELECT id FROM ward   WHERE ward_number='801A'), TRUE),

('Соколова Наталья Михайловна',  '1980-04-07', 'FEMALE', '600-100-200 17', '+7-910-305-0002',
 'г. Москва, ул. Жулебинский б-р, д.9', '2024-02-20', 'TREATMENT',
 (SELECT id FROM doctor WHERE full_name='Шевченко Наталья Петровна'),
 (SELECT id FROM ward   WHERE ward_number='801B'), TRUE),

('Тихонов Денис Алексеевич',     '1992-08-30', 'MALE',   '600-100-200 18', '+7-910-305-0003',
 'г. Москва, ул. Люблинская, д.37', '2024-03-01', 'DISCHARGED',
 NULL, NULL, TRUE),

-- Therapy patients
('Ульянова Вероника Степановна', '1975-06-18', 'FEMALE', '600-100-200 19', '+7-910-306-0001',
 'г. Москва, ул. Снежная, д.14',  '2024-01-10', 'TREATMENT',
 (SELECT id FROM doctor WHERE full_name='Тарасова Людмила Витальевна'),
 (SELECT id FROM ward   WHERE ward_number='901A'), TRUE),

('Фомин Николай Владимирович',   '1953-12-01', 'MALE',   '600-100-200 20', '+7-910-306-0002',
 'г. Москва, ул. Байкальская, д.6', '2024-01-15', 'TREATMENT',
 (SELECT id FROM doctor WHERE full_name='Воробьёв Максим Александрович'),
 (SELECT id FROM ward   WHERE ward_number='901A'), TRUE),

('Харитонова Светлана Юрьевна',  '1987-02-22', 'FEMALE', '600-100-200 21', '+7-910-306-0003',
 'г. Москва, ул. Шипиловская, д.28', '2024-01-20', 'TREATMENT',
 (SELECT id FROM doctor WHERE full_name='Кириллова Марина Евгеньевна'),
 (SELECT id FROM ward   WHERE ward_number='901B'), TRUE),

('Царёв Аркадий Борисович',      '1961-09-05', 'MALE',   '600-100-200 22', '+7-910-306-0004',
 'г. Москва, ул. Ставропольская, д.41', '2024-02-01', 'DISCHARGED',
 NULL, NULL, TRUE),

-- Additional cardiology patients
('Чернова Алина Васильевна',     '1995-03-14', 'FEMALE', '600-100-200 23', '+7-910-307-0001',
 'г. Москва, ул. Первомайская, д.10', '2024-03-05', 'TREATMENT',
 (SELECT id FROM doctor WHERE full_name='Громов Виктор Евгеньевич'),
 (SELECT id FROM ward   WHERE ward_number='301C'), TRUE),

('Шестаков Игорь Романович',     '1948-07-27', 'MALE',   '600-100-200 24', '+7-910-307-0002',
 'г. Москва, ул. Нагатинская, д.5', '2024-03-08', 'TREATMENT',
 (SELECT id FROM doctor WHERE full_name='Смирнова Наталья Борисовна'),
 (SELECT id FROM ward   WHERE ward_number='301C'), TRUE),

-- Additional surgery patients
('Щербаков Пётр Иванович',       '1970-01-19', 'MALE',   '600-100-200 25', '+7-910-308-0001',
 'г. Москва, ул. Артековская, д.7', '2024-02-28', 'TREATMENT',
 (SELECT id FROM doctor WHERE full_name='Лысенко Дмитрий Александрович'),
 (SELECT id FROM ward   WHERE ward_number='201C'), TRUE),

('Юдина Екатерина Сергеевна',    '1983-05-31', 'FEMALE', '600-100-200 26', '+7-910-308-0002',
 'г. Москва, ул. Краснодарская, д.19', '2024-03-03', 'TREATMENT',
 (SELECT id FROM doctor WHERE full_name='Власова Ирина Степановна'),
 (SELECT id FROM ward   WHERE ward_number='201C'), TRUE),

-- Various discharged/transferred patients (no ward/doctor)
('Яковлев Борис Фёдорович',      '1940-11-08', 'MALE',   '600-100-200 27', '+7-910-309-0001',
 'г. Москва, ул. Судакова, д.3',  '2024-01-05', 'DISCHARGED', NULL, NULL, TRUE),

('Абрамова Татьяна Алексеевна',  '1998-06-16', 'FEMALE', '600-100-200 28', '+7-910-309-0002',
 'г. Москва, пр. Олимпийский, д.8', '2024-01-12', 'DISCHARGED', NULL, NULL, TRUE),

('Белов Алексей Юрьевич',        '1977-08-23', 'MALE',   '600-100-200 29', '+7-910-309-0003',
 'г. Москва, ул. Лесная, д.20',   '2024-02-08', 'TRANSFERRED', NULL, NULL, TRUE),

('Гаврилова Ольга Петровна',     '1965-04-09', 'FEMALE', '600-100-200 30', '+7-910-309-0004',
 'г. Москва, ул. Открытое ш., д.11', '2024-02-18', 'DISCHARGED', NULL, NULL, TRUE),

-- More active treatment patients
('Дементьев Константин Юрьевич','1959-10-12', 'MALE',   '600-100-200 31', '+7-910-310-0001',
 'г. Москва, ул. Маршала Бирюзова, д.4', '2024-03-10', 'TREATMENT',
 (SELECT id FROM doctor WHERE full_name='Захаров Андрей Михайлович'),
 (SELECT id FROM ward   WHERE ward_number='401C'), TRUE),

('Ерёмина Юлия Дмитриевна',     '2001-02-28', 'FEMALE', '600-100-200 32', '+7-910-310-0002',
 'г. Москва, ул. Академика Анохина, д.8', '2024-03-12', 'TREATMENT',
 (SELECT id FROM doctor WHERE full_name='Беляев Константин Семёнович'),
 (SELECT id FROM ward   WHERE ward_number='501C'), TRUE),

('Жданов Виталий Иванович',      '1974-07-03', 'MALE',   '600-100-200 33', '+7-910-310-0003',
 'г. Москва, ул. Инессы Арманд, д.7', '2024-03-14', 'TREATMENT',
 (SELECT id FROM doctor WHERE full_name='Федосеев Алексей Владимирович'),
 (SELECT id FROM ward   WHERE ward_number='601C'), TRUE),

('Зотова Маргарита Сергеевна',   '2012-11-18', 'FEMALE', '600-100-200 34', '+7-910-310-0004',
 'г. Москва, ул. Маршала Рыбалко, д.2', '2024-03-18', 'TREATMENT',
 (SELECT id FROM doctor WHERE full_name='Романова Анна Викторовна'),
 (SELECT id FROM ward   WHERE ward_number='701C'), TRUE),

('Игнатьев Александр Борисович', '1946-03-06', 'MALE',   '600-100-200 35', '+7-910-311-0001',
 'г. Москва, ул. Болотниковская, д.16', '2024-03-20', 'TREATMENT',
 (SELECT id FROM doctor WHERE full_name='Козырев Борис Иванович'),
 (SELECT id FROM ward   WHERE ward_number='801C'), TRUE),

('Кравцова Нина Александровна',  '1980-08-14', 'FEMALE', '600-100-200 36', '+7-910-311-0002',
 'г. Москва, ул. Феодосийская, д.3', '2024-03-22', 'TREATMENT',
 (SELECT id FROM doctor WHERE full_name='Тарасова Людмила Витальевна'),
 (SELECT id FROM ward   WHERE ward_number='901C'), TRUE),

('Леонов Михаил Петрович',       '1991-05-29', 'MALE',   '600-100-200 37', '+7-910-311-0003',
 'г. Москва, ул. Генерала Белова, д.12', '2024-03-25', 'TREATMENT',
 (SELECT id FROM doctor WHERE full_name='Громов Виктор Евгеньевич'),
 (SELECT id FROM ward   WHERE ward_number='301D'), TRUE),

('Мохова Валерия Андреевна',     '1993-12-07', 'FEMALE', '600-100-200 38', '+7-910-311-0004',
 'г. Москва, ул. Елецкая, д.9',  '2024-03-27', 'TREATMENT',
 (SELECT id FROM doctor WHERE full_name='Никитина Галина Фёдоровна'),
 (SELECT id FROM ward   WHERE ward_number='601D'), TRUE),

('Носков Вадим Игоревич',        '1956-06-21', 'MALE',   '600-100-200 39', '+7-910-312-0001',
 'г. Москва, пр. Химки, д.17',   '2024-01-28', 'DISCHARGED', NULL, NULL, TRUE),

('Овчинникова Зинаида Фёдоровна','1943-09-15', 'FEMALE', '600-100-200 40', '+7-910-312-0002',
 'г. Москва, ул. Коломенская, д.5', '2024-02-14', 'TREATMENT',
 (SELECT id FROM doctor WHERE full_name='Полякова Светлана Ивановна'),
 (SELECT id FROM ward   WHERE ward_number='401D'), TRUE);

-- =====================
-- Update ward occupancy to match new patients
-- =====================
UPDATE ward SET current_occupancy = (
    SELECT COUNT(*) FROM patient
    WHERE current_ward_id = ward.id AND active = TRUE
);

-- =====================
-- History records for new patients in treatment
-- =====================
INSERT INTO patient_doctor_history (patient_id, doctor_id, assigned_from, assigned_to)
SELECT p.id, p.current_doctor_id, p.registration_date::timestamp, NULL
FROM patient p
WHERE p.current_doctor_id IS NOT NULL
  AND p.active = TRUE
  AND NOT EXISTS (
      SELECT 1 FROM patient_doctor_history h
      WHERE h.patient_id = p.id AND h.assigned_to IS NULL
  );

INSERT INTO ward_occupation_history (patient_id, ward_id, admitted_at, discharged_at)
SELECT p.id, p.current_ward_id, p.registration_date::timestamp, NULL
FROM patient p
WHERE p.current_ward_id IS NOT NULL
  AND p.active = TRUE
  AND NOT EXISTS (
      SELECT 1 FROM ward_occupation_history h
      WHERE h.patient_id = p.id AND h.discharged_at IS NULL
  );

-- =====================
-- Reset sequences
-- =====================
SELECT setval('department_id_seq',   (SELECT MAX(id) FROM department));
SELECT setval('doctor_id_seq',       (SELECT MAX(id) FROM doctor));
SELECT setval('ward_id_seq',         (SELECT MAX(id) FROM ward));
SELECT setval('patient_id_seq',      (SELECT MAX(id) FROM patient));
SELECT setval('paid_service_id_seq', (SELECT MAX(id) FROM paid_service));
