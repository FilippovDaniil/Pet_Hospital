package com.hospital.controller;

import com.hospital.config.JwtUtil;
import com.hospital.dto.request.LoginRequest;
import com.hospital.dto.request.RegisterRequest;
import com.hospital.dto.response.AuthResponse;
import com.hospital.entity.Role;
import com.hospital.entity.User;
import com.hospital.exception.BusinessRuleException;
import com.hospital.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

/**
 * Контроллер аутентификации — отвечает за регистрацию и вход пользователей.
 *
 * <p><b>@RestController</b> — это комбинация двух аннотаций:
 * {@code @Controller} (регистрирует класс как Spring MVC контроллер)
 * и {@code @ResponseBody} (каждый метод автоматически сериализует
 * возвращаемый объект в JSON и пишет его в тело HTTP-ответа).
 * Используй {@code @Controller} только когда возвращаешь имена View (Thymeleaf, JSP);
 * для REST API всегда используй {@code @RestController}.</p>
 *
 * <p><b>@RequestMapping("/api/auth")</b> — задаёт общий префикс URL для всех
 * методов класса. Все эндпоинты этого контроллера будут доступны по пути
 * {@code /api/auth/...}. Это удобно: не нужно повторять префикс в каждом методе.</p>
 *
 * <p><b>@RequiredArgsConstructor</b> (Lombok) — генерирует конструктор, принимающий
 * все поля, помеченные {@code final}. Spring использует этот конструктор для
 * <b>Dependency Injection</b> (внедрение зависимостей). Это предпочтительный
 * способ DI — конструкторное внедрение гарантирует, что объект всегда
 * создаётся в валидном состоянии.</p>
 *
 * <p><b>@Tag</b> (Swagger/OpenAPI) — группирует эндпоинты в документации.
 * Все методы этого контроллера появятся в Swagger UI под разделом "Authentication".</p>
 *
 * <p><b>Принцип "тонкого контроллера":</b> контроллер занимается только
 * HTTP-слоем: принимает запрос, валидирует входные данные, вызывает сервис,
 * формирует HTTP-ответ. Бизнес-логика живёт в сервисах и репозиториях.
 * Исключение здесь — регистрация сохранена в контроллере для простоты,
 * в production-коде её стоит вынести в AuthService.</p>
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Регистрация и вход")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    /**
     * Аутентифицирует пользователя и возвращает JWT-токен.
     *
     * <p><b>@PostMapping("/login")</b> — обрабатывает HTTP POST запросы на
     * {@code /api/auth/login}. POST используется здесь, потому что мы
     * передаём чувствительные данные (пароль) в теле запроса — они не
     * попадают в URL и не сохраняются в логах/истории браузера.</p>
     *
     * <p><b>@RequestBody</b> — говорит Spring десериализовать тело запроса
     * (JSON) в Java-объект {@link LoginRequest}. Spring использует
     * Jackson для преобразования JSON → POJO.</p>
     *
     * <p><b>@Valid</b> — запускает Bean Validation (JSR-380) перед входом
     * в метод. Если поля объекта не прошли валидацию (например, пустой логин),
     * Spring автоматически вернёт HTTP 400 Bad Request с описанием ошибок.
     * Аннотация работает в паре с ограничениями на полях DTO: {@code @NotBlank},
     * {@code @Size}, {@code @Email} и т.д.</p>
     *
     * <p><b>ResponseEntity&lt;AuthResponse&gt;</b> — обёртка над HTTP-ответом,
     * позволяющая явно управлять статус-кодом, заголовками и телом ответа.
     * {@code ResponseEntity.ok(...)} устанавливает статус <b>200 OK</b>.</p>
     *
     * <p><b>Процесс аутентификации:</b></p>
     * <ol>
     *   <li>{@code authenticationManager.authenticate(...)} — Spring Security
     *       проверяет логин и пароль. Если данные неверны, выбрасывается
     *       {@code BadCredentialsException} → HTTP 401 Unauthorized.</li>
     *   <li>Загружаем пользователя из БД и генерируем JWT-токен.</li>
     *   <li>Возвращаем токен клиенту. Последующие запросы должны передавать
     *       его в заголовке {@code Authorization: Bearer <token>}.</li>
     * </ol>
     *
     * <p><b>@Operation</b> (Swagger) — добавляет описание метода в Swagger UI.
     * Параметр {@code summary} — краткое однострочное описание.</p>
     *
     * @param request DTO с полями username и password (валидируется)
     * @return JWT-токен и базовая информация о пользователе, HTTP 200
     */
    @PostMapping("/login")
    @Operation(summary = "Войти и получить JWT-токен")
    public ResponseEntity<AuthResponse> login(@RequestBody @Valid LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );
        User user = userRepository.findByUsername(request.getUsername()).orElseThrow();
        String token = jwtUtil.generateToken(user);
        return ResponseEntity.ok(new AuthResponse(token, user.getUsername(), user.getFullName(), user.getRole().name()));
    }

    /**
     * Регистрирует нового пользователя с ролью NURSE и сразу возвращает JWT-токен.
     *
     * <p><b>HTTP 201 Created</b> — правильный статус для создания нового ресурса.
     * Семантика: запрос выполнен, и в результате был создан новый ресурс.
     * Отличие от 200 OK: 200 означает "успешно выполнено", 201 означает
     * "успешно создано". При регистрации пользователя корректен именно 201.</p>
     *
     * <p><b>ResponseEntity.status(HttpStatus.CREATED).body(...)</b> — строим
     * ответ с нужным статусом вручную. Альтернатива: вернуть объект напрямую
     * и добавить {@code @ResponseStatus(HttpStatus.CREATED)} на метод.</p>
     *
     * <p><b>Проверка уникальности</b>: перед созданием убеждаемся, что логин
     * не занят. При конфликте выбрасывается {@link BusinessRuleException} —
     * глобальный {@code @ExceptionHandler} преобразует её в HTTP 409 Conflict.</p>
     *
     * <p><b>Хеширование пароля</b>: {@code passwordEncoder.encode()} применяет
     * алгоритм BCrypt. Никогда не храни пароли в открытом виде!</p>
     *
     * <p><b>Роль по умолчанию NURSE</b>: публичная регистрация всегда создаёт
     * пользователей с минимальными правами. Повышение роли доступно только
     * администратору через отдельный защищённый эндпоинт.</p>
     *
     * @param request DTO с username, password и fullName (валидируется)
     * @return JWT-токен нового пользователя, HTTP 201 Created
     */
    @PostMapping("/register")
    @Operation(summary = "Зарегистрировать нового пользователя (роль: NURSE)")
    public ResponseEntity<AuthResponse> register(@RequestBody @Valid RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessRuleException("Пользователь с логином '" + request.getUsername() + "' уже существует");
        }
        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .role(Role.ROLE_NURSE)
                .active(true)
                .build();
        userRepository.save(user);
        String token = jwtUtil.generateToken(user);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new AuthResponse(token, user.getUsername(), user.getFullName(), user.getRole().name()));
    }
}
