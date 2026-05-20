-- Отслеживание момента последнего просмотра раздела «Приёмы» врачом.
-- NULL = врач ещё ни разу не открывал раздел (бейдж показывает все PENDING-приёмы).
-- При каждом GET /api/doctors/me/appointments поле обновляется до NOW().
ALTER TABLE doctor ADD COLUMN appointments_viewed_at TIMESTAMP;
