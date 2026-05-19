package com.hospital.config;

import com.hospital.entity.Role;
import com.hospital.entity.User;
import com.hospital.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
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
        createIfAbsent("admin",   "admin123",   "Главный Администратор",   Role.ROLE_ADMIN);
        createIfAbsent("doctor1", "doctor123",  "Иванов Сергей Петрович",  Role.ROLE_DOCTOR);
        createIfAbsent("nurse1",  "nurse123",   "Медсестра Петрова А.В.",  Role.ROLE_NURSE);
        createIfAbsent("client1", "client123",  "Клиент Тестовый Иван",    Role.ROLE_CLIENT);
        createIfAbsent("client2", "client123",  "Клиент Тестовый Мария",   Role.ROLE_CLIENT);
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
}
