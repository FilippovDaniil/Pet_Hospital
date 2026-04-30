package com.hospital.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * УТИЛИТА ДЛЯ РАБОТЫ С JSON WEB TOKEN (JWT)
 *
 * JWT — это открытый стандарт (RFC 7519) для безопасной передачи информации
 * между сторонами в виде JSON-объекта, подписанного цифровой подписью.
 *
 * СТРУКТУРА JWT (три части, разделённые точкой):
 *
 *   eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9     <- HEADER  (заголовок)
 *   .
 *   eyJzdWIiOiJhZG1pbiIsImlhdCI6MTcwMDAwMH0   <- PAYLOAD (полезная нагрузка)
 *   .
 *   SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c  <- SIGNATURE (подпись)
 *
 * 1. HEADER — закодированный Base64Url JSON вида:
 *    { "alg": "HS256", "typ": "JWT" }
 *    alg: алгоритм подписи. HS256 = HMAC + SHA-256.
 *
 * 2. PAYLOAD — закодированный Base64Url JSON с "клеймами" (claims):
 *    { "sub": "admin", "iat": 1700000000, "exp": 1700086400 }
 *    sub (subject)    — кто является субъектом токена (имя пользователя)
 *    iat (issued at)  — время выпуска токена (Unix timestamp)
 *    exp (expiration) — время истечения токена
 *    ВАЖНО: Payload кодируется Base64, но НЕ шифруется — его видит любой!
 *    Никогда не кладите в payload пароли или секретные данные.
 *
 * 3. SIGNATURE — криптографическая подпись:
 *    HMAC-SHA256(Base64(header) + "." + Base64(payload), secretKey)
 *    Подпись гарантирует, что токен не был изменён после выпуска.
 *    Если кто-то изменит payload, подпись станет невалидной.
 *
 * КАК РАБОТАЕТ АУТЕНТИФИКАЦИЯ ЧЕРЕЗ JWT:
 *   1. Пользователь логинится → сервер создаёт JWT и отправляет клиенту
 *   2. Клиент сохраняет JWT (localStorage, sessionStorage)
 *   3. При каждом запросе клиент отправляет: Authorization: Bearer <token>
 *   4. Сервер проверяет подпись, извлекает username, считает пользователя аутентифицированным
 *   5. Сессия на сервере НЕ нужна — вся информация в самом токене
 *
 * @Component — регистрирует класс как Spring-бин, доступный для инъекции в другие компоненты.
 */
@Component
public class JwtUtil {

    /**
     * Секретный ключ для подписи токенов.
     *
     * @Value("${jwt.secret}") — Spring читает значение из application.yml:
     *   jwt:
     *     secret: "some-very-long-secret-key-at-least-32-chars"
     *
     * Этот ключ НИКОГДА не должен попасть в репозиторий! В production
     * его хранят в переменных окружения или в хранилищах секретов (Vault, AWS SSM).
     *
     * Минимальная длина для HS256: 32 байта (256 бит).
     * Более длинный ключ = более надёжная подпись.
     */
    @Value("${jwt.secret}")
    private String secret;

    /**
     * Время жизни токена в миллисекундах.
     *
     * Из application.yml:
     *   jwt:
     *     expiration-ms: 86400000  # 24 часа = 24 * 60 * 60 * 1000
     *
     * Короткое время жизни (15-60 мин) — безопаснее, но неудобно для пользователя.
     * Длинное (7 дней) — удобно, но украденный токен действует дольше.
     * Компромисс для учебного проекта: 24 часа.
     *
     * В production используют пару: access token (короткий) + refresh token (длинный).
     */
    @Value("${jwt.expiration-ms}")
    private long expirationMs;

    /**
     * СОЗДАНИЕ СЕКРЕТНОГО КЛЮЧА ДЛЯ HMAC-SHA256
     *
     * Keys.hmacShaKeyFor() — создаёт объект SecretKey из байтов строки-секрета.
     * Библиотека JJWT (Java JWT) требует объект SecretKey, а не просто строку.
     *
     * StandardCharsets.UTF_8 — явно указываем кодировку, чтобы избежать
     * platform-dependent поведения (на разных ОС default charset может различаться).
     *
     * ПОЧЕМУ метод вызывается каждый раз (не кэшируется)?
     * SecretKey — это лёгкий объект-обёртка, создание которого стоит копейки.
     * При желании можно вынести в @PostConstruct и кэшировать в поле.
     *
     * ПОЧЕМУ secret хранится как String, а не уже как SecretKey?
     * @Value работает только со строками. Конвертация происходит в runtime.
     */
    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * ГЕНЕРАЦИЯ JWT-ТОКЕНА
     *
     * Создаёт новый JWT для переданного пользователя.
     *
     * Jwts.builder() — fluent API (цепочка методов) для построения JWT:
     *
     *   .subject(username)  — "sub" claim: кому выдан токен
     *   .issuedAt(new Date()) — "iat" claim: текущее время выдачи
     *   .expiration(...)    — "exp" claim: когда токен истечёт
     *   .signWith(key())    — подписываем секретным ключом алгоритмом HS256
     *                         (алгоритм выбирается автоматически по типу ключа)
     *   .compact()          — собираем три части и соединяем точками:
     *                         Base64(header).Base64(payload).Base64(signature)
     *
     * @param userDetails — объект Spring Security с данными пользователя.
     *                      Используем только getUsername() — имя пользователя.
     * @return строка вида "eyJ...eyJ...SfL..." — готовый JWT-токен
     */
    public String generateToken(UserDetails userDetails) {
        return Jwts.builder()
                .subject(userDetails.getUsername())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(key())
                .compact();
    }

    /**
     * ИЗВЛЕЧЕНИЕ ИМЕНИ ПОЛЬЗОВАТЕЛЯ ИЗ ТОКЕНА
     *
     * Claims — это объект, представляющий payload JWT (все клеймы).
     * getSubject() возвращает значение стандартного клейма "sub" —
     * именно туда мы положили username при генерации токена.
     *
     * ПОРЯДОК ОПЕРАЦИЙ:
     *   1. parseSignedClaims() — парсим токен, попутно ПРОВЕРЯЯ подпись.
     *      Если подпись невалидна или токен изменён — выбрасывается исключение.
     *   2. getPayload() — получаем объект Claims из проверенного токена.
     *   3. getSubject() — читаем поле "sub".
     *
     * @param token — JWT-строка из заголовка Authorization
     * @return имя пользователя (username) или исключение если токен невалиден
     */
    public String extractUsername(String token) {
        return getClaims(token).getSubject();
    }

    /**
     * ВАЛИДАЦИЯ ТОКЕНА
     *
     * Проверяет два условия:
     *   1. username из токена совпадает с username из UserDetails
     *      (защита от подмены — на случай если токен содержит данные другого пользователя)
     *   2. токен не истёк по времени (!isExpired)
     *
     * Обратите внимание: сама подпись проверяется внутри getClaims() → parseSignedClaims().
     * Если подпись невалидна, getClaims() выбросит исключение ещё до достижения этого метода.
     *
     * @param token       — JWT-строка
     * @param userDetails — объект пользователя из базы данных
     * @return true если токен валиден и принадлежит данному пользователю
     */
    public boolean validateToken(String token, UserDetails userDetails) {
        String username = extractUsername(token);
        return username.equals(userDetails.getUsername()) && !isExpired(token);
    }

    /**
     * ПРОВЕРКА ИСТЕЧЕНИЯ СРОКА ТОКЕНА
     *
     * getClaims(token).getExpiration() — читает поле "exp" из payload JWT.
     * .before(new Date()) — истёк ли срок? Если дата истечения РАНЬШЕ текущего момента — true.
     *
     * Пример: exp = 2024-01-01 10:00, now = 2024-01-01 12:00 → before → true → истёк.
     *
     * private — вспомогательный метод, используется только внутри класса.
     */
    private boolean isExpired(String token) {
        return getClaims(token).getExpiration().before(new Date());
    }

    /**
     * ПАРСИНГ И ПОЛУЧЕНИЕ КЛЕЙМОВ (PAYLOAD) JWT-ТОКЕНА
     *
     * Jwts.parser() — создаёт парсер JWT.
     * .verifyWith(key()) — настраивает парсер на проверку подписи нашим секретным ключом.
     * .build() — собирает настроенный парсер.
     * .parseSignedClaims(token) — парсит JWT строку:
     *     a) Разбивает на header, payload, signature
     *     b) Вычисляет ожидаемую подпись по header+payload с нашим ключом
     *     c) Сравнивает с подписью из токена → если не совпадает, выбрасывает SignatureException
     *     d) Проверяет не истёк ли токен → если истёк, выбрасывает ExpiredJwtException
     * .getPayload() — возвращает объект Claims (Map-like: ключ → значение клейма).
     *
     * Все JwtException-исключения перехватываются в JwtAuthenticationFilter
     * в блоке catch(Exception ignored) — невалидный токен просто игнорируется,
     * запрос продолжается как неаутентифицированный.
     *
     * private — используется только внутри этого класса.
     */
    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
