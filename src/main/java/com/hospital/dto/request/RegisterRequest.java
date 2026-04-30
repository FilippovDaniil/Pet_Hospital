package com.hospital.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO для запроса регистрации нового пользователя системы.
 *
 * <p>Принимается на POST /api/auth/register. Содержит только те поля,
 * которые необходимы для создания учётной записи. Entity {@code User}
 * дополнительно хранит роль, дату создания, хэш пароля — всё это
 * устанавливается в сервисном слое, а не приходит от клиента.
 *
 * <p>Bean Validation срабатывает автоматически благодаря аннотации
 * {@code @Valid} на параметре контроллера. При нарушении любого ограничения
 * Spring бросает {@code MethodArgumentNotValidException}, который
 * перехватывается в {@code GlobalExceptionHandler}.
 */
@Data
public class RegisterRequest {

    /**
     * Уникальный логин нового пользователя.
     *
     * <p><b>@NotBlank</b> — поле обязательно и не может быть пустым.<br>
     * <b>@Size(min = 3, max = 50)</b> — ограничение длины строки.
     * {@code min = 3} исключает слишком короткие логины (например, "a"),
     * {@code max = 50} — защита от переполнения колонки в БД
     * (колонка объявлена как VARCHAR(50)).
     */
    @NotBlank
    @Size(min = 3, max = 50)
    private String username;

    /**
     * Пароль в открытом виде; хранится в БД только в виде BCrypt-хэша.
     *
     * <p><b>@NotBlank</b> — запрещает пустой пароль.<br>
     * <b>@Size(min = 6)</b> — минимальная длина пароля для базовой безопасности.
     * Параметр {@code message} переопределяет стандартное сообщение об ошибке
     * на понятный русскоязычный текст, который вернётся клиенту в JSON.
     */
    @NotBlank
    @Size(min = 6, message = "Пароль должен содержать не менее 6 символов")
    private String password;

    /**
     * Полное имя пользователя (ФИО), отображается в интерфейсе.
     *
     * <p><b>@NotBlank</b> — обязательное поле; нельзя зарегистрироваться
     * без имени.
     */
    @NotBlank
    private String fullName;
}
