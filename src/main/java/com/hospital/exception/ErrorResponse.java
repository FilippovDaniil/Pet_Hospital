package com.hospital.exception;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * DTO для стандартизированного JSON-ответа об ошибке.
 *
 * Единая структура ошибок позволяет фронтенду обрабатывать их единообразно:
 * проверяем status, читаем message, при необходимости обходим fieldErrors.
 *
 * Пример JSON для ошибки валидации (HTTP 400):
 * {
 *   "timestamp": "2024-01-15T10:30:00",
 *   "status": 400,
 *   "error": "Bad Request",
 *   "message": "Validation failed",
 *   "path": "/api/patients",
 *   "fieldErrors": {
 *     "snils": "must not be blank",
 *     "fullName": "size must be between 2 and 100"
 *   }
 * }
 *
 * Пример JSON для ошибки 404:
 * {
 *   "timestamp": "2024-01-15T10:30:00",
 *   "status": 404,
 *   "error": "Not Found",
 *   "message": "Patient not found with id: 5",
 *   "path": "/api/patients/5",
 *   "fieldErrors": null
 * }
 *
 * @Data — генерирует геттеры, сеттеры, toString, equals, hashCode.
 * @Builder — позволяет собирать объект через цепочку: ErrorResponse.builder().status(404).build()
 */
@Data
@Builder
public class ErrorResponse {
    /** Момент возникновения ошибки — удобно для корреляции с логами сервера. */
    private LocalDateTime timestamp;

    /** Числовой HTTP-статус (400, 404, 409, 500 и т.д.). */
    private int status;

    /** Текстовое название статуса ("Bad Request", "Not Found", "Conflict"). */
    private String error;

    /** Детальное сообщение об ошибке для разработчика или пользователя. */
    private String message;

    /** URL запроса, который привёл к ошибке. */
    private String path;

    /**
     * Карта ошибок валидации: {имя поля → сообщение}.
     * Заполняется только для HTTP 400 (ошибки @Valid).
     * Для остальных ошибок — null.
     */
    private Map<String, String> fieldErrors;
}
