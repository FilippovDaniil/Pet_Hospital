package com.hospital.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ГЛОБАЛЬНЫЙ ОБРАБОТЧИК ИСКЛЮЧЕНИЙ — централизованная обработка ошибок REST API.
 *
 * Паттерн: @RestControllerAdvice перехватывает исключения, пробрасываемые
 * из любого @RestController, и преобразует их в единообразные JSON-ответы.
 *
 * БЕЗ этого класса Spring вернул бы клиенту стандартную "белую страницу ошибки"
 * (Whitelabel Error Page) или пустой ответ — неприемлемо для REST API.
 *
 * @RestControllerAdvice = @ControllerAdvice + @ResponseBody.
 *   @ControllerAdvice — делает класс "глобальным советником" для всех контроллеров.
 *   @ResponseBody — возвращаемые объекты сериализуются в JSON (не в HTML/View).
 *
 * КАК РАБОТАЕТ ЦЕПОЧКА ИСКЛЮЧЕНИЙ:
 *   Контроллер → вызывает Сервис → сервис бросает Exception
 *       ↓
 *   Spring ищет подходящий @ExceptionHandler в этом классе (по типу исключения)
 *       ↓
 *   Вызывается соответствующий метод → возвращает ResponseEntity<ErrorResponse>
 *       ↓
 *   Jackson сериализует ErrorResponse в JSON → клиент получает понятную ошибку
 *
 * ПОРЯДОК совпадения обработчиков: Spring выбирает НАИБОЛЕЕ СПЕЦИФИЧНЫЙ тип.
 * Поэтому handleGeneral(Exception.class) — "последний рубеж", он ловит всё, что
 * не поймали более конкретные обработчики.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Обработка: ресурс не найден (HTTP 404 Not Found).
     *
     * ResourceNotFoundException выбрасывается в сервисах при вызове
     * .orElseThrow(() -> new ResourceNotFoundException("Patient", id))
     * Это означает: "ищем пациента с id=5, но его нет в базе".
     *
     * HTTP 404 — стандартный статус "ресурс не найден". Клиент понимает,
     * что запрошенный объект не существует (или был удалён).
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex, HttpServletRequest req) {
        log.warn("Resource not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(buildError(HttpStatus.NOT_FOUND, ex.getMessage(), req.getRequestURI(), null));
    }

    /**
     * Обработка: нарушение бизнес-правила (HTTP 409 Conflict).
     *
     * BusinessRuleException выбрасывается, когда запрос технически корректен,
     * но нарушает бизнес-логику:
     *   - СНИЛС уже существует в системе
     *   - Врач уже ведёт максимальное количество пациентов
     *   - Палата переполнена
     *   - Пациент уже в этой палате
     *
     * HTTP 409 Conflict — клиент пытается выполнить операцию, которая противоречит
     * текущему состоянию сервера. Это не ошибка клиента (400) и не ошибка сервера (500).
     */
    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ErrorResponse> handleBusinessRule(BusinessRuleException ex, HttpServletRequest req) {
        log.warn("Business rule violation: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(buildError(HttpStatus.CONFLICT, ex.getMessage(), req.getRequestURI(), null));
    }

    /**
     * Обработка: ошибка валидации входных данных (HTTP 400 Bad Request).
     *
     * MethodArgumentNotValidException выбрасывается Spring MVC автоматически,
     * когда @Valid-аннотация обнаруживает ошибку в @RequestBody:
     *   - @NotBlank — пустое обязательное поле
     *   - @Size — строка вне допустимого диапазона длины
     *   - @Min/@Max — число вне допустимого диапазона
     *   - @Pattern — строка не соответствует регулярному выражению
     *
     * getBindingResult().getFieldErrors() — список всех ошибок валидации.
     * Каждая ошибка содержит имя поля (FieldError::getField) и сообщение (getDefaultMessage).
     *
     * Collectors.toMap с merge-функцией (existing, replacement) -> existing:
     * если одно поле имеет несколько ошибок, берём ПЕРВУЮ (не перезаписываем).
     *
     * Возвращаем Map<String, String> {поле → сообщение об ошибке} в ErrorResponse.fieldErrors,
     * чтобы фронтенд мог показать подсказки рядом с конкретными полями формы.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(FieldError::getField, FieldError::getDefaultMessage,
                        (existing, replacement) -> existing));
        log.warn("Validation failed: {}", fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildError(HttpStatus.BAD_REQUEST, "Validation failed", req.getRequestURI(), fieldErrors));
    }

    /**
     * Обработка: ошибка аутентификации (HTTP 401 Unauthorized).
     *
     * AuthenticationException из Spring Security выбрасывается при:
     *   - Неверном логине или пароле (BadCredentialsException)
     *   - Отключённом аккаунте (DisabledException)
     *   - Истёкшем аккаунте (AccountExpiredException)
     *
     * Важно: в SecurityConfig мы уже настроили authenticationEntryPoint для случая
     * "нет токена вообще". Этот обработчик ловит другой случай — токен был,
     * но authenticationManager.authenticate() выбросил исключение (неверный пароль при /login).
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(buildError(HttpStatus.UNAUTHORIZED, "Неверный логин или пароль", req.getRequestURI(), null));
    }

    /**
     * Обработка: любое непредвиденное исключение (HTTP 500 Internal Server Error).
     *
     * Это "последний рубеж" — ловит всё, что не поймали обработчики выше.
     * Сценарии: NullPointerException, IllegalArgumentException, ошибки БД,
     * сетевые проблемы с Kafka и т.д.
     *
     * log.error (не warn) — потому что это НЕПРЕДВИДЕННАЯ ошибка, требующая внимания разработчика.
     * В ответ клиенту отправляем общее сообщение, не раскрывая внутренние детали реализации
     * (stack trace, имена классов, SQL-запросы) — это важно для безопасности.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex, HttpServletRequest req) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildError(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", req.getRequestURI(), null));
    }

    /**
     * Вспомогательный метод: создаёт объект ErrorResponse по единому шаблону.
     *
     * Централизованная фабрика ответа об ошибке гарантирует,
     * что все ошибки API имеют одинаковую структуру JSON:
     * {
     *   "timestamp": "2024-01-15T10:30:00",
     *   "status": 404,
     *   "error": "Not Found",
     *   "message": "Patient not found with id: 5",
     *   "path": "/api/patients/5",
     *   "fieldErrors": null
     * }
     *
     * req.getRequestURI() — URL запроса, вызвавшего ошибку.
     * Это помогает клиенту и при отладке: сразу понятно, какой запрос упал.
     */
    private ErrorResponse buildError(HttpStatus status, String message, String path, Map<String, String> fieldErrors) {
        return ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())          // числовой код (404, 409 и т.д.)
                .error(status.getReasonPhrase()) // текстовое название ("Not Found", "Conflict")
                .message(message)                // детальное описание ошибки
                .path(path)                      // URL запроса
                .fieldErrors(fieldErrors)        // ошибки полей (только для валидации)
                .build();
    }
}
