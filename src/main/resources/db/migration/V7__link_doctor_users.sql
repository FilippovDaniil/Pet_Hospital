-- =====================================================
-- V7 — Link doctor records to HIS user accounts
-- Привязывает 5 новых учётных записей врачей (doctor2..doctor6)
-- к соответствующим записям в таблице doctor.
-- EXISTS-проверка делает UPDATE идемпотентным: если пользователь
-- или врач отсутствует, запрос молча пропускается.
-- =====================================================

UPDATE doctor
SET user_id = (SELECT id FROM users WHERE username = 'doctor2')
WHERE full_name = 'Захаров Андрей Михайлович'
  AND EXISTS (SELECT 1 FROM users WHERE username = 'doctor2');

UPDATE doctor
SET user_id = (SELECT id FROM users WHERE username = 'doctor3')
WHERE full_name = 'Беляев Константин Семёнович'
  AND EXISTS (SELECT 1 FROM users WHERE username = 'doctor3');

UPDATE doctor
SET user_id = (SELECT id FROM users WHERE username = 'doctor4')
WHERE full_name = 'Романова Анна Викторовна'
  AND EXISTS (SELECT 1 FROM users WHERE username = 'doctor4');

UPDATE doctor
SET user_id = (SELECT id FROM users WHERE username = 'doctor5')
WHERE full_name = 'Тарасова Людмила Витальевна'
  AND EXISTS (SELECT 1 FROM users WHERE username = 'doctor5');

UPDATE doctor
SET user_id = (SELECT id FROM users WHERE username = 'doctor6')
WHERE full_name = 'Федосеев Алексей Владимирович'
  AND EXISTS (SELECT 1 FROM users WHERE username = 'doctor6');
