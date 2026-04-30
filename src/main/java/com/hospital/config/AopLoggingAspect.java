package com.hospital.config;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

/**
 * АОП-АСПЕКТ ДЛЯ ЛОГИРОВАНИЯ СЕРВИСНОГО СЛОЯ
 *
 * AOP (Aspect-Oriented Programming — Аспектно-ориентированное программирование)
 * — парадигма программирования, позволяющая выносить "сквозную функциональность"
 * (cross-cutting concerns) из бизнес-логики в отдельные модули — аспекты.
 *
 * СКВОЗНАЯ ФУНКЦИОНАЛЬНОСТЬ — это код, который нужен везде, но не является
 * частью бизнес-логики:
 *   - Логирование (что мы и делаем здесь)
 *   - Измерение производительности
 *   - Транзакции (@Transactional — тоже AOP под капотом!)
 *   - Кэширование (@Cacheable — тоже AOP)
 *   - Безопасность (@PreAuthorize — тоже AOP)
 *
 * БЕЗ AOP нам пришлось бы в каждом методе сервиса писать:
 *   log.debug(">>> Entering: methodName");
 *   long start = System.currentTimeMillis();
 *   try { ... }
 *   finally { log.debug("<<< Exited in {}ms", System.currentTimeMillis() - start); }
 *
 * С AOP: всё это в одном месте, сервисы остаются чистыми.
 *
 * КАК РАБОТАЕТ AOP В SPRING (через прокси):
 *   1. Spring создаёт прокси-объект вместо реального бина
 *   2. Прокси перехватывает вызовы методов
 *   3. Перед/после/вместо вызова выполняется код аспекта
 *   4. Если нужно — прокси вызывает реальный метод
 *
 *   Код вызывает serviceMethod()
 *         ↓
 *   [Proxy] → [Aspect code: до] → [Реальный метод] → [Aspect code: после]
 *
 * @Aspect — маркирует класс как аспект AspectJ. Spring обнаруживает его
 *           и применяет к соответствующим методам.
 *
 * @Component — регистрирует аспект как Spring-бин (без этого @Aspect не работает в Spring).
 *
 * @Slf4j — Lombok создаёт поле: private static final Logger log = LoggerFactory.getLogger(...)
 *          SLF4J (Simple Logging Facade for Java) — фасад над конкретными логгерами (Logback, Log4j2).
 */
@Aspect
@Component
@Slf4j
public class AopLoggingAspect {

    /**
     * ПЕРЕХВАТ МЕТОДОВ СЕРВИСНОГО СЛОЯ (Around Advice)
     *
     * ТИПЫ ADVICE (советов) в AOP:
     *   @Before  — выполняется ДО метода
     *   @After   — выполняется ПОСЛЕ метода (всегда, даже при исключении)
     *   @AfterReturning — выполняется после УСПЕШНОГО возврата
     *   @AfterThrowing  — выполняется при ИСКЛЮЧЕНИИ
     *   @Around  — оборачивает метод целиком: можно выполнять код до и после,
     *              изменять аргументы/результат, перехватывать исключения
     *
     * @Around — самый мощный тип. Мы используем его, потому что нам нужно
     *           измерить время выполнения: нужно знать момент входа И выхода.
     *           @Before + @After работали бы раздельно и не дали бы нам общий StopWatch.
     *
     * POINTCUT EXPRESSION (выражение для выбора методов):
     *   "execution(public * com.hospital.service.impl.*.*(..))"
     *
     *   Синтаксис: execution([модификатор] [тип возврата] [пакет].[класс].[метод]([аргументы]))
     *
     *   public             — только публичные методы
     *   *                  — любой тип возврата (void, String, List и т.д.)
     *   com.hospital.service.impl  — конкретный пакет
     *   .*                 — любой класс в этом пакете
     *   .*                 — любой метод этого класса
     *   (..)               — любое количество аргументов любых типов
     *
     *   Итого: перехватываем все публичные методы всех классов в пакете service.impl
     *
     * ПОЧЕМУ именно service.impl, а не service?
     *   - В service/ находятся интерфейсы (нет реализации для перехвата)
     *   - В service.impl/ — реальные реализации (PatientServiceImpl, DoctorServiceImpl и т.д.)
     *   - AOP работает с конкретными реализациями (прокси создаётся для них)
     *
     * @param pjp — ProceedingJoinPoint: представляет перехваченный вызов метода.
     *              Содержит: имя метода, аргументы, класс, метод proceed() для вызова реального кода.
     *              "Proceeding" — потому что через него мы управляем продолжением выполнения.
     *
     * throws Throwable — @Around должен объявлять throws Throwable, так как перехваченный
     *                    метод может бросать любые исключения. Мы пробрасываем их дальше.
     */
    @Around("execution(public * com.hospital.service.impl.*.*(..))")
    public Object logServiceMethod(ProceedingJoinPoint pjp) throws Throwable {

        /**
         * Получаем короткое имя метода для логирования.
         *
         * pjp.getSignature() — возвращает сигнатуру метода (MethodSignature).
         * .toShortString() — формат: "ClassName.methodName(..)"
         * Например: "PatientServiceImpl.findAll(..)"
         *
         * Альтернативы для получения более детальной информации:
         *   pjp.getSignature().toLongString()  — полный формат с типами аргументов
         *   pjp.getArgs()                       — массив реальных аргументов
         *   pjp.getTarget().getClass().getName() — имя класса реализации
         */
        String methodName = pjp.getSignature().toShortString();

        /**
         * Логируем вход в метод на уровне DEBUG.
         *
         * Уровни логирования (от менее к более критичным):
         *   TRACE → DEBUG → INFO → WARN → ERROR
         *
         * DEBUG подходит для отладочной информации: в production обычно
         * выставляют уровень INFO, и DEBUG-сообщения не попадают в лог.
         * В development-профиле (application-dev.yml) можно включить DEBUG.
         *
         * log.debug(">>> ...") — символы >>> визуально помогают найти вход в метод в логах.
         */
        log.debug(">>> Entering: {}", methodName);

        /**
         * ИЗМЕРЕНИЕ ВРЕМЕНИ ВЫПОЛНЕНИЯ
         *
         * Вариант 1 (используемый): StopWatch из Spring Utils
         *   - Удобный API: start/stop, getTotalTimeMillis()
         *   - Может измерять несколько задач и выводить сводку
         *   - Хорошо читается
         *
         * Вариант 2 (альтернативный): System.currentTimeMillis()
         *   long start = System.currentTimeMillis();
         *   // ... выполнение
         *   long duration = System.currentTimeMillis() - start;
         *   - Проще, без зависимости на Spring Utils
         *   - Подвержен корректировке системных часов (NTP sync и т.д.)
         *
         * Вариант 3 (лучший для производительности): System.nanoTime()
         *   - Монотонные часы, не зависят от системного времени
         *   - Лучше для точного измерения коротких интервалов
         *
         * StopWatch хорош для учебных целей — наглядный и функциональный.
         */
        StopWatch sw = new StopWatch();
        sw.start();

        try {
            /**
             * pjp.proceed() — КЛЮЧЕВОЙ ВЫЗОВ: выполняем реальный метод сервиса.
             *
             * Без этого вызова реальный метод НИКОГДА не выполнится — аспект его заблокирует.
             * proceed() возвращает Object — результат выполнения метода (или null для void).
             *
             * Можно передать изменённые аргументы: pjp.proceed(newArgs)
             * Можно изменить результат: вернуть что-то вместо result
             * Именно такая мощь @Around и является его главным преимуществом над @Before/@After.
             */
            Object result = pjp.proceed();

            // Метод выполнился без исключений — останавливаем таймер и логируем успех
            sw.stop();
            log.debug("<<< Exited: {} in {}ms", methodName, sw.getTotalTimeMillis());

            /**
             * ОБЯЗАТЕЛЬНО возвращаем результат методы.
             * Если не вернуть — вызывающий код получит null вместо реального результата.
             */
            return result;

        } catch (Throwable ex) {
            /**
             * ПЕРЕХВАТ ИСКЛЮЧЕНИЙ
             *
             * Если реальный метод выбросил исключение:
             *   1. Останавливаем таймер (чтобы знать, как долго выполнялось до падения)
             *   2. Логируем предупреждение (WARN, не ERROR — ERROR для критических сбоев)
             *   3. ПРОБРАСЫВАЕМ исключение дальше через throw ex
             *
             * ВАЖНО: не глотать исключение! Если не сделать throw ex,
             * вызывающий код не узнает об ошибке — это очень опасный антипаттерн.
             * Глобальный обработчик @ControllerAdvice не поймает исключение
             * и бизнес-логика ошибок не сработает.
             *
             * WARN здесь уместен: нас интересует факт и длительность до ошибки.
             * Само исключение будет поймано выше (в @ControllerAdvice) и залогировано там.
             */
            sw.stop();
            log.warn("<<< Exception in {} after {}ms: {}", methodName, sw.getTotalTimeMillis(), ex.getMessage());
            throw ex;
        }
    }
}
