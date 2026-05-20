package com.hospital.config;

import com.hospital.entity.Role;
import com.hospital.entity.User;
import com.hospital.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Инициализатор начальных данных — создаёт дефолтного администратора при первом запуске.
 *
 * ApplicationRunner — интерфейс Spring Boot, метод run() которого вызывается
 * ОДИН РАЗ после того, как ApplicationContext полностью поднят и Tomcat запущен.
 * Это последнее место в процессе старта приложения.
 *
 * Порядок выполнения при старте:
 *   1. Spring Boot читает application.yml
 *   2. Создаёт ApplicationContext (регистрирует бины)
 *   3. Flyway выполняет миграции БД (создаёт таблицы)
 *   4. Поднимает Tomcat
 *   5. Вызывает ApplicationRunner.run() ← мы здесь
 *
 * Зачем нужен DataInitializer, а не Flyway-миграция?
 * Flyway работает с SQL, а нам нужно ХЭШИРОВАТЬ пароль через BCrypt.
 * Хэширование — это Java-код, который нельзя написать на чистом SQL.
 * Поэтому: Flyway создаёт таблицу users (DDL), DataInitializer заполняет её (DML через Java).
 *
 * Проверка userRepository.count() == 0 гарантирует идемпотентность:
 * при повторных запусках приложения существующие данные не перезаписываются.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder; // BCryptPasswordEncoder из SecurityConfig
    private final JdbcTemplate jdbc;

    /**
     * Создаёт администратора по умолчанию, если база данных пуста.
     *
     * passwordEncoder.encode("admin123") — хэшируем пароль через BCrypt.
     * Каждый вызов encode() генерирует НОВЫЙ хэш (разная соль), поэтому
     * два пользователя с одинаковым паролем будут иметь разные хэши в БД.
     *
     * В production-системах:
     *   - Пароль по умолчанию должен быть принудительно сменён при первом входе
     *   - Или вообще не устанавливать дефолтный пароль, а генерировать случайный
     *     и выводить его один раз в лог при первом запуске
     */
    @Override
    public void run(ApplicationArguments args) {
        createIfAbsent("admin",   "admin123",   "Главный Администратор",         Role.ROLE_ADMIN);
        createIfAbsent("doctor1", "doctor123",  "Иванов Сергей Петрович",        Role.ROLE_DOCTOR);
        createIfAbsent("doctor2", "doctor123",  "Захаров Андрей Михайлович",     Role.ROLE_DOCTOR);
        createIfAbsent("doctor3", "doctor123",  "Беляев Константин Семёнович",   Role.ROLE_DOCTOR);
        createIfAbsent("doctor4", "doctor123",  "Романова Анна Викторовна",      Role.ROLE_DOCTOR);
        createIfAbsent("doctor5", "doctor123",  "Тарасова Людмила Витальевна",   Role.ROLE_DOCTOR);
        createIfAbsent("doctor6", "doctor123",  "Федосеев Алексей Владимирович", Role.ROLE_DOCTOR);
        createIfAbsent("nurse1",  "nurse123",   "Медсестра Петрова А.В.",        Role.ROLE_NURSE);
        createIfAbsent("client1", "client123",  "Клиент Тестовый Иван",          Role.ROLE_CLIENT);
        createIfAbsent("client2", "client123",  "Клиент Тестовый Мария",         Role.ROLE_CLIENT);
        // Привязываем учётные записи врачей к их записям в таблице doctor.
        // Делается здесь (а не в Flyway-миграции), потому что Flyway запускается
        // ДО DataInitializer: на момент миграции таблица users ещё пуста.
        linkDoctorUser("doctor1", "Иванов Сергей Петрович");
        linkDoctorUser("doctor2", "Захаров Андрей Михайлович");
        linkDoctorUser("doctor3", "Беляев Константин Семёнович");
        linkDoctorUser("doctor4", "Романова Анна Викторовна");
        linkDoctorUser("doctor5", "Тарасова Людмила Витальевна");
        linkDoctorUser("doctor6", "Федосеев Алексей Владимирович");
    }

    private void createIfAbsent(String username, String password, String fullName, Role role) {
        if (!userRepository.existsByUsername(username)) {
            userRepository.save(User.builder()
                    .username(username)
                    .password(passwordEncoder.encode(password))
                    .fullName(fullName)
                    .role(role)
                    .active(true)
                    .build());
            log.info("Created default user: login={}, role={}", username, role);
        }
    }

    private void linkDoctorUser(String username, String doctorFullName) {
        int updated = jdbc.update(
            "UPDATE doctor SET user_id = (SELECT id FROM users WHERE username = ?) " +
            "WHERE full_name = ? AND user_id IS NULL",
            username, doctorFullName
        );
        if (updated > 0) {
            log.info("Linked user '{}' to doctor '{}'", username, doctorFullName);
        }
    }
}
