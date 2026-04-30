package com.hospital.repository;

import com.hospital.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Репозиторий для работы с сущностью {@link User} — пользователем системы.
 *
 * <p>Используется в слое аутентификации и авторизации (Spring Security + JWT).
 * Пользователи системы — это сотрудники больницы (врачи, администраторы),
 * которые входят в систему по логину и паролю.</p>
 *
 * <p><b>Аннотация {@code @Repository}:</b><br>
 * Технически она избыточна для интерфейсов Spring Data JPA — Spring и без неё
 * создаст реализацию и зарегистрирует её как бин. Однако аннотацию иногда
 * добавляют явно для:
 * <ul>
 *   <li>улучшения читаемости (явное указание роли класса)</li>
 *   <li>включения трансляции исключений JPA в Spring DataAccessException</li>
 *   <li>упрощения поиска репозиториев в проекте</li>
 * </ul>
 * </p>
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Ищет пользователя по имени пользователя (логину).
     *
     * <p><b>SQL, генерируемый Spring Data JPA:</b><br>
     * {@code SELECT * FROM users WHERE username = ?}</p>
     *
     * <p>Используется в реализации {@code UserDetailsService} для загрузки
     * пользователя при аутентификации:
     * <pre>{@code
     *   public UserDetails loadUserByUsername(String username) {
     *       return userRepository.findByUsername(username)
     *           .orElseThrow(() -> new UsernameNotFoundException("User not found"));
     *   }
     * }</pre>
     * А также при валидации JWT-токена: из токена извлекается {@code username},
     * и по нему загружается актуальный пользователь из БД.</p>
     *
     * @param username имя пользователя (логин)
     * @return {@link Optional} с пользователем, если он найден
     */
    Optional<User> findByUsername(String username);

    /**
     * Проверяет, существует ли пользователь с указанным логином.
     *
     * <p><b>SQL, генерируемый Spring Data JPA:</b><br>
     * {@code SELECT CASE WHEN COUNT(*) > 0 THEN true ELSE false END FROM users WHERE username = ?}</p>
     *
     * <p>Используется при регистрации нового пользователя для проверки
     * уникальности логина до попытки сохранения. Это позволяет вернуть
     * понятное сообщение об ошибке ("такой пользователь уже существует"),
     * не дожидаясь нарушения уникального ограничения (UNIQUE constraint)
     * на уровне БД.</p>
     *
     * @param username имя пользователя (логин)
     * @return {@code true}, если пользователь с таким логином уже зарегистрирован
     */
    boolean existsByUsername(String username);
}
