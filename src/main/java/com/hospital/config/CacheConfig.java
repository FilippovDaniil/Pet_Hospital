package com.hospital.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

/**
 * КОНФИГУРАЦИЯ КЭШИРОВАНИЯ НА ОСНОВЕ REDIS
 *
 * Кэширование нужно для ускорения работы с "дорогими" данными:
 * отчёты агрегируют большое количество записей — при каждом запросе
 * делать полный скан таблиц неэффективно. Кэш хранит готовый результат
 * на 5 минут, возвращая его мгновенно без запросов к БД.
 *
 * Redis — in-memory хранилище данных, работает как внешний кэш.
 * Преимущества перед ConcurrentHashMap (кэш в памяти JVM):
 *   - Выживает при перезапуске приложения (опционально, зависит от настроек Redis)
 *   - Работает в кластере (несколько экземпляров приложения разделяют один кэш)
 *   - Есть встроенный мониторинг, TTL, eviction policies
 *
 * @EnableCaching — включает Spring Cache абстракцию.
 * Без этой аннотации @Cacheable / @CacheEvict игнорируются (прокси не создаётся).
 *
 * Как работает @Cacheable в связке с Redis:
 *   1. Метод вызывается
 *   2. Spring Cache проверяет Redis: есть ли ключ cache_name::args?
 *   3. Есть → возвращает из Redis (метод НЕ выполняется)
 *   4. Нет → выполняет метод, сохраняет результат в Redis с TTL=5мин
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * Константы имён кэшей — чтобы не дублировать строки в @Cacheable/@CacheEvict.
     * Использование строк напрямую ("ward-occupancy") опасно: опечатка в одном месте
     * создаёт новый кэш вместо сброса нужного. Константы устраняют эту проблему.
     */
    public static final String WARD_OCCUPANCY = "ward-occupancy";    // кэш отчёта по палатам
    public static final String SERVICES_SUMMARY = "services-summary"; // кэш отчёта по услугам

    /**
     * Конфигурация Redis-кэша.
     *
     * entryTtl(5 минут) — время жизни записи в кэше.
     *   Через 5 минут Redis автоматически удалит запись. Следующий запрос пойдёт в БД.
     *   5 минут — компромисс: данные достаточно свежие, но не нагружают БД при частых запросах.
     *
     * disableCachingNullValues() — не кэшировать null.
     *   Если метод вернул null (например, ничего не найдено), не кэшируем это значение.
     *   Иначе "пустой" результат сохранится в кэш и будет возвращаться 5 минут,
     *   даже если данные потом появились.
     *
     * serializeValuesWith(GenericJackson2JsonRedisSerializer) — сериализация в JSON.
     *   Redis хранит данные как байты. Мы сериализуем Java-объекты в JSON.
     *   GenericJackson2JsonRedisSerializer включает информацию о типе в JSON —
     *   это позволяет корректно десериализовать сложные объекты (List<WardOccupancyReport>).
     *   Альтернатива — JdkSerializationRedisSerializer (бинарный формат, не читаем в Redis CLI).
     */
    @Bean
    public RedisCacheConfiguration redisCacheConfiguration() {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5))       // TTL: 5 минут
                .disableCachingNullValues()             // null не кэшируем
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new GenericJackson2JsonRedisSerializer())); // JSON-сериализация
    }
}
