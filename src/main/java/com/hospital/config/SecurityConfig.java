package com.hospital.config;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * КОНФИГУРАЦИЯ БЕЗОПАСНОСТИ ПРИЛОЖЕНИЯ (Spring Security)
 *
 * Этот класс является центральной точкой настройки безопасности всего REST API.
 * Здесь мы определяем:
 *   1. Какие эндпоинты открыты, а какие требуют авторизации
 *   2. Как обрабатываются ошибки аутентификации и авторизации
 *   3. Где в цепочке фильтров стоит наш JWT-фильтр
 *
 * @Configuration — говорит Spring, что этот класс содержит определения бинов (@Bean).
 *                  Spring сканирует его при старте и регистрирует все методы с @Bean в контексте.
 *
 * @EnableWebSecurity — активирует поддержку Spring Security для веб-приложения.
 *                      Без этой аннотации SecurityFilterChain не будет подключён к HTTP-обработке.
 *
 * @RequiredArgsConstructor — Lombok-аннотация, автоматически создаёт конструктор
 *                            для всех final-полей. Spring использует этот конструктор
 *                            для внедрения зависимостей (Dependency Injection).
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    /**
     * Наш кастомный JWT-фильтр, который извлекает токен из заголовка каждого запроса.
     * Внедряется через конструктор (благодаря @RequiredArgsConstructor).
     */
    private final JwtAuthenticationFilter jwtFilter;

    /**
     * ЦЕПОЧКА ФИЛЬТРОВ БЕЗОПАСНОСТИ (Security Filter Chain)
     *
     * SecurityFilterChain — это последовательность фильтров, через которую проходит
     * каждый HTTP-запрос к нашему приложению. Порядок фильтров важен:
     *
     *   Входящий запрос
     *       ↓
     *   [CorsFilter]
     *       ↓
     *   [JwtAuthenticationFilter]  ← наш фильтр добавлен здесь
     *       ↓
     *   [UsernamePasswordAuthenticationFilter]
     *       ↓
     *   [ExceptionTranslationFilter]  ← перехватывает ошибки авторизации
     *       ↓
     *   [FilterSecurityInterceptor]   ← проверяет права доступа
     *       ↓
     *   Контроллер (бизнес-логика)
     *
     * @Bean — регистрирует результат метода как Spring-бин. Spring Security автоматически
     *         находит бины типа SecurityFilterChain и применяет их.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            /**
             * ОТКЛЮЧЕНИЕ CSRF-ЗАЩИТЫ
             *
             * CSRF (Cross-Site Request Forgery) — атака, при которой злоумышленник заставляет
             * браузер жертвы отправить запрос от её имени на наш сервер.
             *
             * ПОЧЕМУ отключаем для REST API:
             *   - CSRF-защита работает через токен в сессии/куке, которую браузер отправляет автоматически.
             *   - Наш API использует JWT в заголовке Authorization — браузер НЕ отправляет
             *     заголовки автоматически для cross-origin запросов (нужен явный JavaScript-код).
             *   - Злоумышленник не может получить JWT из localStorage жертвы через cross-site запрос.
             *   - Следовательно, CSRF-атака против Bearer Token API физически невозможна.
             *
             * Для традиционных веб-приложений с cookie-сессиями CSRF отключать нельзя!
             * AbstractHttpConfigurer::disable — это method reference на метод disable()
             */
            .csrf(AbstractHttpConfigurer::disable)

            /**
             * ПОЛИТИКА УПРАВЛЕНИЯ СЕССИЯМИ: STATELESS (без состояния)
             *
             * SessionCreationPolicy.STATELESS означает:
             *   - Spring Security НЕ создаёт HTTP-сессию (HttpSession) для хранения аутентификации
             *   - Spring Security НЕ читает существующую сессию при каждом запросе
             *   - Каждый запрос аутентифицируется заново через JWT-токен в заголовке
             *
             * ПРЕИМУЩЕСТВА Stateless архитектуры:
             *   - Масштабируемость: запросы можно направлять на любой сервер кластера
             *     (не нужно хранить сессию на конкретном сервере или в shared-хранилище)
             *   - Производительность: не тратим память сервера на хранение сессий
             *   - Простота: нет проблем с истечением сессий, их репликацией и т.д.
             *
             * В классических Spring MVC приложениях используют ALWAYS или IF_REQUIRED,
             * там сессия хранится на сервере и идентифицируется по JSESSIONID в cookie.
             */
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            /**
             * ПРАВИЛА АВТОРИЗАЦИИ ЗАПРОСОВ
             *
             * Правила применяются по порядку — ПЕРВОЕ совпавшее правило побеждает.
             * Поэтому более специфичные правила (конкретные пути) должны идти раньше
             * более общих (.anyRequest()).
             */
            .authorizeHttpRequests(auth -> auth

                /**
                 * permitAll() — эндпоинты открыты для всех, без токена.
                 * /api/auth/** — логин и регистрация: пользователь ещё не аутентифицирован,
                 * поэтому токена у него нет и быть не может.
                 */
                .requestMatchers("/api/auth/**").permitAll()

                /**
                 * Swagger UI и OpenAPI документация открыты без авторизации.
                 * Это удобно для разработки: можно смотреть документацию и тестировать API
                 * прямо из браузера. В production-окружении это можно закрыть.
                 *
                 * /swagger-ui/**      — статические ресурсы Swagger UI (JS, CSS, HTML)
                 * /v3/api-docs/**     — OpenAPI 3.0 JSON-спецификация
                 */
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/api-docs/**", "/v3/api-docs/**").permitAll()

                /**
                 * Spring Boot Actuator — эндпоинты для мониторинга: /actuator/health,
                 * /actuator/metrics и т.д. В учебном проекте открываем всем.
                 * В production рекомендуется закрыть или вынести на отдельный порт.
                 */
                .requestMatchers("/actuator/**").permitAll()

                /**
                 * Статические ресурсы фронтенда: HTML-страницы, CSS и JS-файлы.
                 * Они отдаются браузеру до аутентификации — логика входа на /login.html
                 * выполняется через JavaScript, который уже получает токен от /api/auth/login.
                 */
                .requestMatchers("/", "/index.html", "/login.html", "/register.html",
                                 "/css/**", "/js/**", "/favicon.ico").permitAll()

                /**
                 * hasRole("ADMIN") — требует роль ADMIN у аутентифицированного пользователя.
                 *
                 * ВАЖНО: Spring Security автоматически добавляет префикс "ROLE_" к имени роли.
                 * То есть hasRole("ADMIN") проверяет наличие authority "ROLE_ADMIN" у пользователя.
                 * В базе данных у нас хранится Role.ROLE_ADMIN, что соответствует этому.
                 *
                 * Если пользователь аутентифицирован, но роли ADMIN нет — вернётся 403 Forbidden.
                 * Если пользователь вообще не аутентифицирован — вернётся 401 Unauthorized.
                 */
                .requestMatchers("/api/admin/**").hasRole("ADMIN")

                /**
                 * Все остальные запросы требуют хотя бы базовой аутентификации (валидный JWT).
                 * Роль не проверяется — подходит любой авторизованный пользователь.
                 */
                .anyRequest().authenticated()
            )

            /**
             * ОБРАБОТКА ИСКЛЮЧЕНИЙ БЕЗОПАСНОСТИ
             *
             * Spring Security различает два типа ошибок доступа:
             *   1. AuthenticationException — пользователь не аутентифицирован (нет токена / токен невалиден)
             *   2. AccessDeniedException    — пользователь аутентифицирован, но прав недостаточно
             */
            .exceptionHandling(ex -> ex

                /**
                 * AUTHENTICATION ENTRY POINT — обработчик ошибки аутентификации (HTTP 401)
                 *
                 * По умолчанию Spring Security делает REDIRECT на страницу /login.
                 * Это подходит для классических web-приложений, но для REST API это неприемлемо:
                 * клиент (мобильное приложение, SPA) ожидает JSON, а не HTML с редиректом.
                 *
                 * Мы переопределяем поведение: вместо редиректа возвращаем JSON с кодом 401.
                 *
                 * SC_UNAUTHORIZED = 401: стандартный HTTP-статус "не аутентифицирован".
                 * Название немного вводит в заблуждение — на самом деле это означает
                 * "не аутентифицирован", а не "не авторизован".
                 */
                .authenticationEntryPoint((req, res, e) -> {
                    res.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 401
                    res.setContentType("application/json;charset=UTF-8");
                    res.getWriter().write("{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Требуется авторизация\"}");
                })

                /**
                 * ACCESS DENIED HANDLER — обработчик ошибки авторизации (HTTP 403)
                 *
                 * Срабатывает когда пользователь АУТЕНТИФИЦИРОВАН (токен валиден),
                 * но у него нет нужной роли для доступа к ресурсу.
                 *
                 * Пример: обычный пользователь обращается к /api/admin/users — у него
                 * роль USER, а нужна ADMIN. Возвращаем 403 Forbidden с JSON-ответом.
                 *
                 * SC_FORBIDDEN = 403: "запрещено" — знаете кто вы, но прав нет.
                 */
                .accessDeniedHandler((req, res, e) -> {
                    res.setStatus(HttpServletResponse.SC_FORBIDDEN); // 403
                    res.setContentType("application/json;charset=UTF-8");
                    res.getWriter().write("{\"status\":403,\"error\":\"Forbidden\",\"message\":\"Недостаточно прав\"}");
                })
            )

            /**
             * ДОБАВЛЕНИЕ JWT-ФИЛЬТРА В ЦЕПОЧКУ
             *
             * addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class) означает:
             * "вставь наш jwtFilter ДО стандартного UsernamePasswordAuthenticationFilter".
             *
             * Это важно: наш фильтр должен прочитать JWT и установить аутентификацию
             * в SecurityContext ДО того, как Spring Security начнёт проверять права доступа.
             *
             * UsernamePasswordAuthenticationFilter — стандартный фильтр для форм-логина
             * (username + password из тела запроса). Мы не используем его для аутентификации,
             * но используем его позицию как ориентир в цепочке.
             */
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * КОДИРОВЩИК ПАРОЛЕЙ — BCrypt
     *
     * BCryptPasswordEncoder реализует адаптивный алгоритм хэширования BCrypt.
     * Особенности BCrypt:
     *   - Односторонний (необратимый): из хэша нельзя получить исходный пароль
     *   - Каждый вызов encode() генерирует уникальную соль — одинаковые пароли
     *     дают разные хэши, что защищает от rainbow table атак
     *   - "Медленный" по дизайну: параметр cost (по умолчанию 10) управляет
     *     количеством раундов хэширования. Это делает перебор паролей дорогим.
     *
     * Используется в:
     *   - DataInitializer: при создании пользователя admin
     *   - AuthServiceImpl: при регистрации нового пользователя
     *   - Spring Security: автоматически при проверке пароля через matches()
     *
     * НИКОГДА не храните пароли в открытом виде — только хэши!
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * МЕНЕДЖЕР АУТЕНТИФИКАЦИИ
     *
     * AuthenticationManager — центральный интерфейс Spring Security для выполнения
     * аутентификации. Метод authenticate() принимает объект Authentication (например,
     * UsernamePasswordAuthenticationToken с логином и паролем) и возвращает
     * полностью заполненный Authentication-объект в случае успеха.
     *
     * Мы получаем стандартную реализацию из AuthenticationConfiguration —
     * Spring Security создаёт её автоматически на основе настроенных UserDetailsService
     * и PasswordEncoder.
     *
     * Используется в AuthController: при логине вызываем authManager.authenticate()
     * с логином и паролем из запроса. Spring Security сам проверит пароль через BCrypt.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
