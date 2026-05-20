-- ============================================================
-- V10: Модуль медсестры
-- Создаёт два новых хранилища:
--   1. medical_supply   — склад медикаментов и расходников
--   2. nurse_assignment — назначения процедур клиентам
-- И заполняет начальный склад тестовыми данными.
-- ============================================================

-- Склад медикаментов и расходных материалов.
-- quantity — текущий остаток; изменяется через PATCH /api/nurse/supplies/{id}/adjust.
-- min_quantity — порог предупреждения: если quantity <= min_quantity → lowStock=true в DTO.
CREATE TABLE medical_supply (
    id           BIGSERIAL PRIMARY KEY,
    name         VARCHAR(255)  NOT NULL,
    category     VARCHAR(50)   NOT NULL, -- MEDICINE | CONSUMABLE | EQUIPMENT
    quantity     INT           NOT NULL DEFAULT 0,
    unit         VARCHAR(50)   NOT NULL DEFAULT 'шт',
    description  TEXT,
    min_quantity INT           NOT NULL DEFAULT 5
);

-- Назначения процедур клиентам от медсестры.
-- client_user_id — пользователь с ROLE_CLIENT (получатель процедуры).
-- nurse_user_id  — пользователь с ROLE_NURSE (исполнитель).
-- scheduled_date/time — необязательны: назначение можно создать без конкретного времени.
-- status — жизненный цикл: ACTIVE → DONE | CANCELLED.
-- created_at — выставляется сервером (DEFAULT NOW()), не принимается от клиента.
CREATE TABLE nurse_assignment (
    id              BIGSERIAL PRIMARY KEY,
    client_user_id  BIGINT        NOT NULL REFERENCES users(id),
    nurse_user_id   BIGINT        NOT NULL REFERENCES users(id),
    procedure_type  VARCHAR(50)   NOT NULL, -- INJECTION | PILL | DRESSING | PROCEDURE | OTHER
    title           VARCHAR(255)  NOT NULL,
    description     TEXT,
    dosage          VARCHAR(100),           -- доза/инструкция, напр. «1 мл», «1 таб. 2 р/д»
    scheduled_date  DATE,
    scheduled_time  TIME,
    status          VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE', -- ACTIVE | DONE | CANCELLED
    created_at      TIMESTAMP     NOT NULL DEFAULT NOW()
);

-- Начальный склад: 5 медикаментов + 7 расходников + 3 единицы оборудования.
-- Данные тестовые — для демонстрации интерфейса медсестры при первом запуске.
INSERT INTO medical_supply (name, category, quantity, unit, description, min_quantity) VALUES
  ('Физраствор NaCl 0.9%',  'MEDICINE',    120, 'мл',   'Изотонический раствор хлорида натрия для инфузий', 20),
  ('Гепарин 5000 ЕД/мл',    'MEDICINE',     45, 'амп',  'Антикоагулянт прямого действия',                   10),
  ('Амоксициллин 500 мг',   'MEDICINE',    200, 'таб',  'Антибиотик широкого спектра действия',             30),
  ('Парацетамол 500 мг',    'MEDICINE',    300, 'таб',  'Жаропонижающее и обезболивающее',                  50),
  ('Омепразол 20 мг',       'MEDICINE',    150, 'капс', 'Ингибитор протонной помпы',                        20),
  ('Шприц 5 мл',            'CONSUMABLE',  500, 'шт',   'Одноразовые шприцы инъекционные',                 100),
  ('Шприц 20 мл',           'CONSUMABLE',  300, 'шт',   'Одноразовые шприцы для инфузий',                   50),
  ('Игла 21G',              'CONSUMABLE',  600, 'шт',   'Иглы инъекционные стерильные',                    100),
  ('Перчатки нитрил M',     'CONSUMABLE',  400, 'пар',  'Нитриловые перчатки размер M',                     80),
  ('Бинт стерильный',       'CONSUMABLE',  250, 'шт',   'Стерильные марлевые бинты 7 см × 5 м',             40),
  ('Вата стерильная',       'CONSUMABLE',  180, 'пак',  'Медицинская вата стерильная 100 г',                 30),
  ('Пластырь бактерицидный','CONSUMABLE',  350, 'шт',   'Бактерицидный лейкопластырь 2.5×7.2 см',           60),
  ('Термометр цифровой',    'EQUIPMENT',     8, 'шт',   'Контактный электронный термометр',                  2),
  ('Тонометр',              'EQUIPMENT',     4, 'шт',   'Полуавтоматический тонометр на плечо',              1),
  ('Пульсоксиметр',         'EQUIPMENT',     6, 'шт',   'Clip-on пульсоксиметр на палец',                    2);
