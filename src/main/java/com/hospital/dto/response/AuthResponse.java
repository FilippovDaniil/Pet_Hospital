package com.hospital.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * DTO ответа на успешную аутентификацию (POST /api/auth/login
 * и POST /api/auth/register).
 *
 * <p>После проверки учётных данных сервер возвращает этот объект
 * в теле ответа. Клиент сохраняет {@code token} и добавляет его
 * в заголовок {@code Authorization: Bearer <token>} каждого
 * последующего запроса.
 *
 * <p><b>Почему не возвращаем Entity {@code User}?</b><br>
 * Entity содержит хэш пароля ({@code passwordHash}), внутренний
 * идентификатор и другие поля, которые не нужны и опасны для
 * отправки клиенту. DTO возвращает ровно то, что требуется фронтенду:
 * токен и базовую информацию о пользователе.
 *
 * <p>Аннотация {@code @AllArgsConstructor} генерирует конструктор
 * со всеми полями — это позволяет создавать объект одной строкой
 * {@code new AuthResponse(token, username, fullName, role)}.
 */
@Data
@AllArgsConstructor
public class AuthResponse {

    /**
     * JWT-токен для последующих аутентифицированных запросов.
     * Содержит зашифрованные claims: имя пользователя, роль и время истечения.
     * Клиент передаёт его в заголовке: {@code Authorization: Bearer <token>}.
     */
    private String token;

    /**
     * Логин пользователя — возвращается для отображения в интерфейсе.
     * Берётся напрямую из Entity {@code User.username}.
     */
    private String username;

    /**
     * Полное имя пользователя для персонализированного приветствия в UI.
     * Берётся из Entity {@code User.fullName}.
     */
    private String fullName;

    /**
     * Роль пользователя в системе (например, {@code ROLE_ADMIN}, {@code ROLE_DOCTOR}).
     * Используется фронтендом для управления видимостью элементов интерфейса.
     * Берётся из Entity {@code User.role}.
     */
    private String role;
}
