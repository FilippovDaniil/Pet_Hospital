package com.hospital.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * DTO (Data Transfer Object) для запроса аутентификации пользователя.
 *
 * <p><b>Зачем нужен DTO?</b><br>
 * DTO — это специальный объект для передачи данных между слоями приложения
 * и между клиентом и сервером. Мы не передаём Entity (сущность базы данных)
 * напрямую в HTTP-запросах, потому что:
 * <ul>
 *   <li>Entity содержит служебные поля (id, версия, связи), которые клиент
 *       не должен видеть или изменять.</li>
 *   <li>DTO позволяет независимо менять структуру БД и API.</li>
 *   <li>На DTO удобно навешивать аннотации валидации Bean Validation.</li>
 * </ul>
 *
 * <p>Этот класс принимает учётные данные при POST /api/auth/login.
 * После успешной проверки сервис возвращает JWT-токен в {@code AuthResponse}.
 */
@Data
public class LoginRequest {

    /**
     * Имя пользователя (логин) для входа в систему.
     *
     * <p><b>@NotBlank</b> — означает «не пустой и не состоящий из одних пробелов».
     * Отличие от {@code @NotNull}: {@code @NotNull} допускает пустую строку {@code ""},
     * а {@code @NotBlank} — нет. Если поле не заполнено, Bean Validation
     * вернёт ошибку валидации до того, как запрос попадёт в сервисный слой.
     */
    @NotBlank
    private String username;

    /**
     * Пароль пользователя в открытом виде (передаётся по HTTPS).
     *
     * <p><b>@NotBlank</b> — запрещает передавать пустой пароль.
     * Сервис сравнивает его с хэшем в БД через BCryptPasswordEncoder.
     */
    @NotBlank
    private String password;
}
