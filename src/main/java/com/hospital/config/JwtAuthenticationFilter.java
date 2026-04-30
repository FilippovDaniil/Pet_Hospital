package com.hospital.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT-ФИЛЬТР АУТЕНТИФИКАЦИИ
 *
 * Этот фильтр является ключевым звеном в JWT-аутентификации.
 * При каждом HTTP-запросе он:
 *   1. Читает заголовок Authorization
 *   2. Извлекает JWT-токен
 *   3. Проверяет валидность токена
 *   4. Устанавливает аутентификацию в SecurityContext
 *
 * ПОЧЕМУ НАСЛЕДУЕМ OncePerRequestFilter, а не Filter?
 *
 * OncePerRequestFilter — абстрактный класс Spring, который гарантирует,
 * что doFilterInternal() вызывается РОВНО ОДИН РАЗ за HTTP-запрос.
 *
 * Проблема с javax.servlet.Filter: в некоторых случаях (например, forward/include
 * внутри сервлета, обработка ошибок через error dispatcher) фильтр может вызываться
 * несколько раз для одного логического запроса. OncePerRequestFilter отслеживает
 * уже обработанные запросы через атрибут request и пропускает повторные вызовы.
 *
 * Это особенно важно для фильтра аутентификации — иначе мы бы проверяли
 * токен несколько раз и могли бы получить непредсказуемое поведение.
 *
 * @Component — Spring регистрирует фильтр как бин.
 *              SecurityConfig затем добавляет его в цепочку через addFilterBefore().
 *
 * @RequiredArgsConstructor — Lombok создаёт конструктор для final-полей.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /**
     * Утилита для работы с JWT: извлечение username, валидация, проверка срока.
     * Описана в JwtUtil.java.
     */
    private final JwtUtil jwtUtil;

    /**
     * Сервис загрузки пользователя по имени.
     * Spring Security использует этот интерфейс для получения UserDetails
     * (объекта с username, password, roles) из нашего источника данных (БД).
     *
     * Реализация: UserDetailsServiceImpl — вызывает userRepository.findByUsername()
     * и оборачивает найденного пользователя в Spring Security UserDetails.
     */
    private final UserDetailsService userDetailsService;

    /**
     * ОСНОВНАЯ ЛОГИКА ФИЛЬТРА
     *
     * Вызывается Spring Security для каждого входящего HTTP-запроса.
     * Метод ОБЯЗАН в конце вызвать chain.doFilter() — иначе запрос
     * не пройдёт дальше по цепочке и ответ не будет сформирован.
     *
     * СХЕМА РАБОТЫ:
     *
     *   Запрос приходит
     *       ↓
     *   Есть заголовок "Authorization: Bearer ..."?
     *       ├── НЕТ → пропускаем (вызываем chain.doFilter) → Spring Security
     *       │          сам решит, что делать (скорее всего вернёт 401 если эндпоинт закрыт)
     *       └── ДА  → извлекаем токен → парсим username
     *                      ↓
     *               SecurityContext уже заполнен?
     *                   ├── ДА  → пропускаем (аутентификация уже была)
     *                   └── НЕТ → загружаем UserDetails из БД
     *                                  ↓
     *                           Токен валиден?
     *                               ├── НЕТ → пропускаем (catch Exception)
     *                               └── ДА  → устанавливаем Authentication
     *                                          в SecurityContext
     *                                              ↓
     *                                     chain.doFilter() → следующие фильтры → контроллер
     *
     * @param request  — входящий HTTP-запрос
     * @param response — исходящий HTTP-ответ
     * @param chain    — цепочка фильтров; вызов chain.doFilter() передаёт управление дальше
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        /**
         * ШАГ 1: Читаем заголовок Authorization
         *
         * Стандарт JWT предписывает передавать токен в заголовке:
         *   Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiJ9.xxx
         *
         * Формат: "Bearer " (с пробелом) + токен.
         * "Bearer" — схема аутентификации, определена в RFC 6750.
         * request.getHeader() возвращает null если заголовка нет.
         */
        String header = request.getHeader("Authorization");

        /**
         * ШАГ 2: Проверяем что заголовок существует и начинается с "Bearer "
         *
         * Если заголовка нет или формат неправильный — просто передаём запрос дальше.
         * МЫ НЕ ВОЗВРАЩАЕМ ОШИБКУ ЗДЕСЬ — это задача Spring Security.
         * Если эндпоинт требует аутентификации, Spring вернёт 401 через authenticationEntryPoint.
         * Если эндпоинт открыт (permitAll) — запрос пройдёт без аутентификации.
         *
         * Такое "ленивое" поведение правильно: фильтр отвечает только за аутентификацию,
         * не за авторизацию. Разделение ответственности (Single Responsibility Principle).
         */
        if (header != null && header.startsWith("Bearer ")) {

            /**
             * ШАГ 3: Извлекаем сам токен (срезаем "Bearer " = первые 7 символов)
             *
             * header = "Bearer eyJhbGciOiJIUzI1NiJ9..."
             *                  ↑ позиция 7
             * token  = "eyJhbGciOiJIUzI1NiJ9..."
             */
            String token = header.substring(7);

            try {
                /**
                 * ШАГ 4: Извлекаем username из токена
                 *
                 * jwtUtil.extractUsername() внутри парсит JWT и проверяет подпись.
                 * Если токен испорчен или подпись невалидна — выбросится исключение
                 * и мы попадём в catch(Exception ignored) ниже.
                 */
                String username = jwtUtil.extractUsername(token);

                /**
                 * ШАГ 5: Устанавливаем аутентификацию ТОЛЬКО если:
                 *   а) username успешно извлечён (не null)
                 *   б) SecurityContext ещё не содержит аутентификацию
                 *
                 * ЗАЧЕМ проверяем SecurityContext?
                 * Защита от двойной аутентификации. Если какой-то предыдущий фильтр
                 * уже установил Authentication (например, Basic Auth), мы не перезапишем его.
                 * Принцип: первый установивший аутентификацию — главный.
                 *
                 * SecurityContextHolder — ThreadLocal-хранилище, хранит аутентификацию
                 * текущего потока (каждый HTTP-запрос обрабатывается в своём потоке).
                 * После завершения запроса SecurityContext очищается автоматически.
                 */
                if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                    /**
                     * ШАГ 6: Загружаем полные данные пользователя из базы данных
                     *
                     * Зачем нам снова идти в БД, если username уже есть в токене?
                     *   - Проверяем, что пользователь ещё существует в системе
                     *   - Получаем актуальные роли пользователя (они могли измениться)
                     *   - Проверяем, активен ли аккаунт (isEnabled, isAccountNonLocked)
                     *
                     * В учебных проектах иногда пропускают этот шаг для оптимизации,
                     * беря данные прямо из токена. Но это снижает безопасность:
                     * если пользователь заблокирован, его старый токен ещё работает.
                     */
                    UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                    /**
                     * ШАГ 7: Финальная валидация токена
                     *
                     * validateToken() проверяет:
                     *   1. username в токене == username из БД (защита от подмены)
                     *   2. токен не просрочен по времени
                     *
                     * Подпись уже была проверена в extractUsername() на шаге 4.
                     */
                    if (jwtUtil.validateToken(token, userDetails)) {

                        /**
                         * ШАГ 8: Создаём объект аутентификации и помещаем в SecurityContext
                         *
                         * UsernamePasswordAuthenticationToken — стандартная реализация
                         * Authentication в Spring Security.
                         *
                         * Конструктор с 3 параметрами (principal, credentials, authorities)
                         * создаёт УЖЕ АУТЕНТИФИЦИРОВАННЫЙ токен (isAuthenticated() = true).
                         * Конструктор с 2 параметрами создаёт НЕ аутентифицированный.
                         *
                         * principal     = userDetails (кто аутентифицирован)
                         * credentials   = null (пароль не нужен после аутентификации — не храним)
                         * authorities   = роли пользователя (ROLE_ADMIN, ROLE_USER и т.д.)
                         */
                        UsernamePasswordAuthenticationToken auth =
                                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

                        /**
                         * Добавляем детали запроса в объект аутентификации:
                         * IP-адрес клиента, идентификатор сессии и т.д.
                         * Это полезно для аудита и логирования действий пользователей.
                         */
                        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                        /**
                         * УСТАНАВЛИВАЕМ АУТЕНТИФИКАЦИЮ В SecurityContextHolder
                         *
                         * После этой строки Spring Security считает текущий поток
                         * аутентифицированным как данный пользователь.
                         *
                         * SecurityContextHolder использует ThreadLocal стратегию хранения:
                         * каждый поток имеет свой независимый SecurityContext.
                         * Это потокобезопасно и гарантирует изоляцию между запросами.
                         *
                         * FilterSecurityInterceptor (в конце цепочки) прочитает этот
                         * контекст и решит, разрешён ли доступ к запрошенному ресурсу.
                         */
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    }
                }
            } catch (Exception ignored) {
                /**
                 * ОБРАБОТКА НЕВАЛИДНОГО ТОКЕНА
                 *
                 * Если что-то пошло не так (подпись невалидна, токен истёк,
                 * токен имеет неверный формат) — просто игнорируем ошибку.
                 *
                 * Мы НЕ устанавливаем аутентификацию.
                 * Запрос продолжается как неаутентифицированный.
                 * Если эндпоинт требует аутентификации, Spring Security
                 * вернёт 401 через authenticationEntryPoint.
                 *
                 * Почему не возвращаем 401 здесь?
                 * Принцип разделения ответственности: фильтр только ПЫТАЕТСЯ
                 * аутентифицировать. Решение об отказе принимает SecurityConfig.
                 */
            }
        }

        /**
         * ШАГ 9: ПЕРЕДАЁМ УПРАВЛЕНИЕ СЛЕДУЮЩЕМУ ФИЛЬТРУ В ЦЕПОЧКЕ
         *
         * Этот вызов обязателен! Без него запрос не дойдёт до контроллера.
         * chain.doFilter() выполняется ВСЕГДА — независимо от того,
         * была ли аутентификация успешной или нет.
         *
         * После всех фильтров управление перейдёт к DispatcherServlet → Controller.
         */
        chain.doFilter(request, response);
    }
}
