-- Интервал слота для платной услуги: 30 или 60 минут.
-- Используется на фронтенде для генерации доступных временных слотов.
ALTER TABLE paid_service ADD COLUMN slot_minutes INT NOT NULL DEFAULT 60;

-- Желаемые дата и время визита для заказа платной услуги.
-- Необязательные поля: клиент может не указывать конкретное время.
ALTER TABLE client_service_order ADD COLUMN preferred_date DATE;
ALTER TABLE client_service_order ADD COLUMN preferred_time TIME;
