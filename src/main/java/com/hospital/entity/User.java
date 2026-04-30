package com.hospital.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * Сущность «Пользователь системы» — учётная запись для входа в HIS.
 *
 * Ключевая особенность: этот класс РЕАЛИЗУЕТ интерфейс UserDetails из Spring Security.
 * Это означает, что Spring Security будет работать с объектами User напрямую,
 * без промежуточного адаптера.
 *
 * UserDetails — контракт Spring Security: говорит фреймворку, как получить
 * данные пользователя для аутентификации и авторизации:
 *   getUsername()    → логин для проверки в базе
 *   getPassword()    → хэш пароля для сравнения через PasswordEncoder
 *   getAuthorities() → список ролей/прав (ROLE_ADMIN, ROLE_DOCTOR и т.д.)
 *   isEnabled()      → активен ли аккаунт (мягкое удаление / блокировка)
 *
 * Почему НЕ @Data, а отдельные @Getter/@Setter?
 * @Data генерирует toString() и equals()/hashCode() по всем полям.
 * Включение поля password в toString() — утечка пароля в логи.
 * Явные @Getter/@Setter безопаснее: мы контролируем, что генерировать.
 *
 * Таблица называется "users", а не "user", потому что USER — зарезервированное
 * слово в SQL-стандарте (в PostgreSQL CREATE TABLE user → синтаксическая ошибка).
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User implements UserDetails {

    /** Суррогатный первичный ключ, генерируется БД. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Логин пользователя — уникальный идентификатор для входа.
     * unique = true → уникальный индекс в БД: нельзя создать двух пользователей с одним логином.
     * length = 50 → ограничение VARCHAR(50) на уровне DDL.
     */
    @Column(unique = true, nullable = false, length = 50)
    private String username;

    /**
     * BCrypt-хэш пароля. НИКОГДА не хранить пароль в открытом виде!
     * Spring Security сам вызывает passwordEncoder.matches(raw, encoded) при входе.
     * Даже администратор БД не должен знать пароли пользователей.
     */
    @Column(nullable = false)
    private String password;

    /** Полное имя пользователя — отображается в UI после входа. */
    private String fullName;

    /**
     * Роль пользователя: ROLE_ADMIN, ROLE_DOCTOR, ROLE_NURSE.
     * EnumType.STRING → в БД хранится строка ("ROLE_ADMIN"), а не числовой индекс.
     * Это читаемо и безопасно при добавлении новых значений в enum.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    /**
     * Флаг активности — реализует мягкое удаление (soft delete) для пользователей.
     * active = false → пользователь заблокирован, вход невозможен.
     * isEnabled() возвращает это значение → Spring Security откажет в аутентификации.
     * @Builder.Default гарантирует значение true при создании через builder.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    /**
     * Дата и время создания учётной записи.
     * Устанавливается автоматически через @PrePersist — нет риска забыть.
     * Используется для аудита: когда был создан аккаунт.
     */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    /**
     * JPA lifecycle callback — вызывается Hibernate ПЕРЕД первым сохранением (INSERT).
     * Автоматически проставляет дату создания. Аналог @CreationTimestamp от Hibernate,
     * но здесь мы используем стандартный JPA механизм без зависимости от Hibernate.
     */
    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    /**
     * Возвращает список прав (GrantedAuthority) пользователя для Spring Security.
     *
     * SimpleGrantedAuthority — простая реализация GrantedAuthority, содержащая строку.
     * role.name() → "ROLE_ADMIN", "ROLE_DOCTOR", "ROLE_NURSE".
     *
     * Важно: Spring Security ожидает, что роли начинаются с "ROLE_".
     * Именно поэтому наш enum называется ROLE_ADMIN, а не просто ADMIN.
     * Метод hasRole("ADMIN") в SecurityConfig автоматически добавляет префикс ROLE_,
     * что совпадает с нашим именованием.
     *
     * List.of(...) — неизменяемый список из одного элемента (у нас одна роль на пользователя).
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.name()));
    }

    // Следующие методы — часть контракта UserDetails.
    // Возвращаем true везде (не истёк, не заблокирован, учётные данные актуальны),
    // кроме isEnabled() — там делегируем к флагу active.
    // В production можно добавить отдельные поля accountLocked, credentialsExpired и т.д.
    @Override public boolean isAccountNonExpired()     { return true; }
    @Override public boolean isAccountNonLocked()      { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }

    /**
     * Аккаунт включён, если поле active = true.
     * Если пользователь заблокирован (active = false), Spring Security
     * выбросит DisabledException при попытке аутентификации.
     */
    @Override public boolean isEnabled()               { return active; }
}
