# Pet Hospital HIS — Hospital Information System

Учебный проект: полноценная backend-система для управления больницей.
Стек: **Spring Boot 3.2 · PostgreSQL · Kafka · Redis · Docker · JWT · Testcontainers**.

---

## Содержание

1. [Обзор и цели проекта](#1-обзор-и-цели-проекта)
2. [Технологический стек](#2-технологический-стек)
3. [Архитектура приложения](#3-архитектура-приложения)
4. [Структура пакетов](#4-структура-пакетов)
5. [Модель данных (сущности)](#5-модель-данных-сущности)
6. [База данных и миграции Flyway](#6-база-данных-и-миграции-flyway)
7. [Слой репозиториев (Spring Data JPA)](#7-слой-репозиториев-spring-data-jpa)
8. [Слой сервисов и бизнес-логика](#8-слой-сервисов-и-бизнес-логика)
9. [REST API — контроллеры](#9-rest-api--контроллеры)
10. [DTO и валидация](#10-dto-и-валидация)
11. [MapStruct — маппинг объектов](#11-mapstruct--маппинг-объектов)
12. [Безопасность: Spring Security + JWT](#12-безопасность-spring-security--jwt)
13. [Apache Kafka — событийная архитектура](#13-apache-kafka--событийная-архитектура)
14. [Паттерн Outbox](#14-паттерн-outbox)
15. [Redis — кэширование](#15-redis--кэширование)
16. [Паттерн Strategy — выписка пациентов](#16-паттерн-strategy--выписка-пациентов)
17. [AOP — сквозное логирование](#17-aop--сквозное-логирование)
18. [Обработка ошибок](#18-обработка-ошибок)
19. [Swagger / OpenAPI](#19-swagger--openapi)
20. [Spring Boot Actuator](#20-spring-boot-actuator)
21. [Docker и docker-compose](#21-docker-и-docker-compose)
22. [Тестирование](#22-тестирование)
23. [Запуск проекта](#23-запуск-проекта)
24. [Конфигурация](#24-конфигурация)
25. [Бизнес-правила системы](#25-бизнес-правила-системы)
26. [Ролевой интерфейс — фронтенд](#26-ролевой-интерфейс--фронтенд)
27. [Мониторинг: Loki + Grafana](#27-мониторинг-loki--grafana)

---

## 1. Обзор и цели проекта

**Pet Hospital HIS** — это бэкенд-приложение для больницы, которое охватывает:

- регистрацию и ведение **пациентов**;
- управление **врачами** и их назначением к пациентам;
- управление **отделениями** и **палатами**;
- учёт **платных услуг** и формирование финансовых отчётов;
- **аутентификацию** персонала через JWT;
- **асинхронные события** через Apache Kafka;
- **кэширование** тяжёлых отчётов в Redis;
- **аудит** всех перемещений пациентов (история врачей, история палат).

### Учебные цели

Проект создан как полигон для изучения следующих концепций:

| Тема | Что изучается |
|---|---|
| Spring Boot 3.x | Auto-configuration, starters, profiles |
| Spring Security | JWT, stateless auth, role-based access |
| Spring Data JPA | Репозитории, JPQL, страничная выборка |
| Hibernate 6 | Маппинг, ленивая загрузка, null-typed параметры |
| Apache Kafka | Producer/Consumer, транзакции, DLQ |
| Redis | Кэширование с TTL через Spring Cache |
| Flyway | Версионирование схемы БД |
| MapStruct | Кодогенерация маппинга Entity <-> DTO |
| AOP | Сквозная логика (логирование времени выполнения) |
| Strategy Pattern | Паттерн стратегия для выписки |
| Docker Compose | Оркестрация локального окружения |
| Testcontainers | Интеграционные тесты с реальной БД |

---

## 2. Технологический стек

### Ядро приложения

| Компонент | Версия | Назначение |
|---|---|---|
| Java | 17 | Язык программирования |
| Spring Boot | 3.2.5 | Основной фреймворк |
| Spring Web MVC | (в составе Boot) | REST API |
| Spring Security | (в составе Boot) | Аутентификация и авторизация |
| Spring Data JPA | (в составе Boot) | ORM-слой, репозитории |
| Hibernate | 6.x (в составе Boot) | Реализация JPA |
| Spring Kafka | (в составе Boot) | Интеграция с Apache Kafka |
| Spring Data Redis | (в составе Boot) | Интеграция с Redis |
| Spring Cache | (в составе Boot) | Абстракция кэширования |
| Spring AOP | (в составе Boot) | Аспектно-ориентированное программирование |
| Spring Actuator | (в составе Boot) | Метрики и мониторинг |

### Библиотеки

| Библиотека | Версия | Назначение |
|---|---|---|
| JJWT | 0.12.3 | Генерация и валидация JWT-токенов |
| MapStruct | 1.5.5.Final | Кодогенерация маппинга DTO <-> Entity |
| Lombok | (в составе Boot) | Сокращение boilerplate-кода |
| Springdoc OpenAPI | 2.3.0 | Swagger UI и OpenAPI 3.0 документация |
| Flyway | (в составе Boot) | Версионирование и применение миграций БД |
| Jackson JSR-310 | (в составе Boot) | Сериализация Java 8 Date/Time в JSON |

### Инфраструктура

| Компонент | Версия | Назначение |
|---|---|---|
| PostgreSQL | 15 | Основная реляционная СУБД |
| Apache Kafka | 7.6.0 (Confluent) | Брокер сообщений |
| Zookeeper | 7.6.0 (Confluent) | Координация Kafka-кластера |
| Redis | 7 | In-memory кэш |
| Kafdrop | latest | Web UI для мониторинга Kafka |
| Loki | 2.9.0 | Хранилище и индексация логов |
| Grafana | 10.2.3 | Визуализация логов (LogQL) |
| loki-logback-appender | 1.5.2 | Прямая отправка логов из Spring Boot в Loki |

### Тестирование

| Компонент | Версия | Назначение |
|---|---|---|
| JUnit 5 | (в составе Boot) | Фреймворк для тестов |
| Mockito | (в составе Boot) | Мокирование зависимостей |
| Spring Test / MockMvc | (в составе Boot) | HTTP-тестирование контроллеров |
| Spring Security Test | (в составе Boot) | Тестирование с mock-пользователем |
| Testcontainers | 1.20.4 | Запуск реального PostgreSQL в Docker во время тестов |
| Spring Kafka Test / EmbeddedKafka | (в составе spring-kafka-test) | Встроенный Kafka-брокер для тестов |

---

## 3. Архитектура приложения

Приложение построено по классической **многоуровневой (Layered) архитектуре**:

```
+----------------------------------------------------------+
|                     HTTP Clients                         |
|              (Postman, Browser, Swagger UI)              |
+------------------------+---------------------------------+
                         | HTTP
+------------------------v---------------------------------+
|                  Presentation Layer                      |
|   Controllers (@RestController) + Security Filter Chain  |
|   JWT validation . Role-based authorization              |
+------------------------+---------------------------------+
                         | DTO (Request)
+------------------------v---------------------------------+
|                   Service Layer                          |
|   Business logic . @Transactional . Event publishing     |
|   AOP Logging . Cache management . Strategy pattern      |
+----------+---------------------------+------------------+
           | JPA Entity                | Events
+----------v----------+   +-----------v------------------+
|  Repository Layer   |   |     Kafka (EventPublisher)   |
|  Spring Data JPA    |   |     Outbox Pattern           |
|  JPQL / Native SQL  |   |     Consumer listeners       |
+----------+----------+   +------------------------------+
           | SQL
+----------v--------------------------------------------------+
|              Infrastructure                                 |
|   PostgreSQL 15 . Redis 7 . Apache Kafka                    |
+-------------------------------------------------------------+
```

### Ключевые архитектурные решения

#### Stateless аутентификация
Сервер не хранит сессии. Каждый запрос содержит JWT в заголовке `Authorization: Bearer <token>`. Spring Security перехватывает запрос, валидирует токен и помещает пользователя в `SecurityContext`.

#### Event-driven (события через Kafka)
Все изменения доменных объектов (создание пациента, назначение врача, поступление в палату и т.д.) порождают события. Эти события публикуются в Kafka. Консьюмеры обрабатывают их независимо. Это позволяет развязать части системы и подключить внешние системы (например, биллинг).

#### Outbox Pattern
Событие записывается в таблицу `outbox_event` **в одной транзакции** с основным изменением данных. Это гарантирует, что событие не потеряется даже при сбое после коммита транзакции, но до отправки в Kafka.

#### Кэширование отчётов
Тяжёлые агрегирующие запросы (отчёты по заполненности палат, финансовые сводки) кэшируются в Redis на 5 минут. Это разгружает базу данных при частых запросах дашборда.

---

## 4. Структура пакетов

```
src/main/java/com/hospital/
|
+-- HospitalApplication.java            # Точка входа (@SpringBootApplication)
|
+-- config/                             # Конфигурационные классы
|   +-- SecurityConfig.java             # Spring Security + JWT настройки
|   +-- JwtUtil.java                    # Утилиты генерации/валидации JWT
|   +-- JwtAuthenticationFilter.java    # Фильтр проверки JWT в заголовке
|   +-- KafkaConfig.java                # Создание топиков, настройки Kafka
|   +-- CacheConfig.java                # Redis кэш с TTL
|   +-- SwaggerConfig.java              # OpenAPI 3.0 документация
|   +-- AopLoggingAspect.java           # AOP-аспект логирования сервисов
|   +-- DataInitializer.java            # Инициализация (admin-пользователь)
|
+-- controller/                         # REST-контроллеры
|   +-- AuthController.java             # /api/auth
|   +-- PatientController.java          # /api/patients
|   +-- DoctorController.java           # /api/doctors
|   +-- DepartmentController.java       # /api/departments
|   +-- WardController.java             # /api/wards
|   +-- PaidServiceController.java      # /api/paid-services
|   +-- AdminController.java            # /api/admin
|
+-- service/                            # Интерфейсы сервисного слоя
|   +-- impl/                           # Реализации сервисов
|   +-- event/                          # Kafka-события и консьюмеры
|   |   +-- EventPublisher.java
|   |   +-- PatientEvent.java
|   |   +-- AdmissionEvent.java
|   |   +-- DoctorEvent.java
|   |   +-- PaidServiceEvent.java
|   |   +-- DepartmentEvent.java
|   |   +-- PatientEventConsumer.java
|   |   +-- AdmissionEventConsumer.java
|   |   +-- PaidServiceEventConsumer.java
|   |
|   +-- strategy/                       # Паттерн стратегия (выписка)
|       +-- DischargeType.java          # Enum: NORMAL, FORCED, TRANSFER
|       +-- DischargeStrategy.java      # Интерфейс
|       +-- NormalDischargeStrategy.java
|       +-- ForcedDischargeStrategy.java
|       +-- TransferDischargeStrategy.java
|       +-- DischargeStrategyFactory.java
|
+-- entity/                             # JPA-сущности (таблицы БД)
|   +-- Patient.java
|   +-- Doctor.java
|   +-- Department.java
|   +-- Ward.java
|   +-- PaidService.java
|   +-- PatientPaidService.java
|   +-- PatientDoctorHistory.java       # Аудит смены врача
|   +-- WardOccupationHistory.java      # Аудит смены палаты
|   +-- OutboxEvent.java                # Идемпотентность Kafka
|   +-- User.java                       # Пользователи системы
|   +-- Gender.java, Specialty.java, PatientStatus.java, Role.java  # Enums
|
+-- repository/                         # Spring Data JPA репозитории
+-- dto/
|   +-- request/                        # Входящие данные от клиента
|   +-- response/                       # Исходящие данные клиенту
|
+-- mapper/                             # MapStruct маппинг Entity <-> DTO
+-- exception/                          # Обработка ошибок
```

---

## 5. Модель данных (сущности)

### Схема связей

```
Department (1) -------- (N) Ward
Department (1) -------- (0..1) Doctor  [headDoctor]
Department (1) -------- (N) Doctor

Doctor  (0..1) -------- (N) Patient    [currentDoctor]
Ward    (0..1) -------- (N) Patient    [currentWard]

Patient (1) -------- (N) PatientPaidService
PaidService (1) ---- (N) PatientPaidService

Patient (1) -------- (N) PatientDoctorHistory
Patient (1) -------- (N) WardOccupationHistory

[OutboxEvent]  -- таблица идемпотентности событий Kafka
[User]         -- таблица пользователей системы (персонал)
```

### Patient — пациент

```java
@Entity
@Table(name = "patient", uniqueConstraints = @UniqueConstraint(columnNames = "snils"))
public class Patient {
    Long id;
    String fullName;
    LocalDate birthDate;
    Gender gender;           // MALE / FEMALE
    String snils;            // уникальный — XXX-XXX-XXX XX
    String phone;
    String address;
    LocalDate registrationDate;
    PatientStatus status;    // TREATMENT / DISCHARGED / TRANSFERRED
    Doctor currentDoctor;    // @ManyToOne(fetch = LAZY)
    Ward currentWard;        // @ManyToOne(fetch = LAZY)
    boolean active;          // soft-delete флаг
}
```

**Мягкое удаление (Soft Delete)**: пациент никогда не удаляется физически — поле `active = false`. Это сохраняет историческую аудиторскую цепочку.

### Doctor — врач

```java
@Entity
public class Doctor {
    Long id;
    String fullName;
    Specialty specialty;     // Enum: CARDIOLOGIST, SURGEON, THERAPIST, ...
    String cabinetNumber;
    String phone;
    Department department;   // @ManyToOne(fetch = LAZY)
    boolean active;
}
```

### Department — отделение

```java
@Entity
public class Department {
    Long id;
    String name;
    String description;
    String location;
    Doctor headDoctor;       // @OneToOne(fetch = LAZY), nullable
    boolean active;
}
```

### Ward — палата

```java
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"ward_number", "department_id"}))
public class Ward {
    Long id;
    String wardNumber;
    int capacity;            // общее количество мест
    int currentOccupancy;    // занятых мест сейчас
    Department department;

    public int freeSlots() { return capacity - currentOccupancy; }
}
```

Уникальное ограничение `(wardNumber, departmentId)` позволяет иметь палату №1 одновременно в Кардиологии и Хирургии.

### PatientDoctorHistory — история назначений врачей

Аудиторская таблица. Каждый раз, когда пациенту назначается врач, создаётся запись. При смене врача у предыдущей записи заполняется `assignedTo`.

```java
@Entity
public class PatientDoctorHistory {
    Long id;
    Patient patient;
    Doctor doctor;
    LocalDateTime assignedFrom;
    LocalDateTime assignedTo;   // null = текущий врач
}
```

### WardOccupationHistory — история пребывания в палатах

```java
@Entity
public class WardOccupationHistory {
    Long id;
    Patient patient;
    Ward ward;
    LocalDateTime admittedAt;
    LocalDateTime dischargedAt; // null = пациент сейчас в палате
}
```

### OutboxEvent — таблица идемпотентности Kafka

```java
@Entity
public class OutboxEvent {
    Long id;
    String eventId;     // UUID события, уникальный
    String eventType;   // "PATIENT_ASSIGNED_DOCTOR", "WARD_ADMITTED", ...
    String payload;     // JSON-тело события
    LocalDateTime createdAt;
    boolean processed;
}
```

### User — пользователь системы

```java
@Entity
@Table(name = "users")
public class User implements UserDetails {
    Long id;
    String username;    // уникальный
    String password;    // BCrypt-хэш
    String fullName;
    Role role;          // ROLE_ADMIN / ROLE_DOCTOR / ROLE_NURSE
    boolean active;
    LocalDateTime createdAt;
}
```

`User` реализует интерфейс `UserDetails` — это требование Spring Security. Метод `getAuthorities()` возвращает роль пользователя как `GrantedAuthority`.

### Перечисления (Enums)

```java
enum Gender        { MALE, FEMALE }
enum PatientStatus { TREATMENT, DISCHARGED, TRANSFERRED }
enum Role          { ROLE_ADMIN, ROLE_DOCTOR, ROLE_NURSE }
enum Specialty     { CARDIOLOGIST, SURGEON, THERAPIST, NEUROLOGIST,
                     PEDIATRICIAN, ORTHOPEDIST, ONCOLOGIST, UROLOGIST }
enum DischargeType { NORMAL, FORCED, TRANSFER }
```

---

## 6. База данных и миграции Flyway

**Flyway** — инструмент версионирования схемы базы данных. При каждом старте приложения Flyway проверяет таблицу `flyway_schema_history` и применяет новые миграции, которые ещё не были применены.

### Как это работает

1. Приложение стартует.
2. Flyway сканирует `classpath:db/migration` в поисках файлов вида `V{версия}__{описание}.sql`.
3. Сравнивает список с `flyway_schema_history`.
4. Применяет новые файлы в порядке версий.
5. Если схема уже соответствует — ничего не делает.

### Конфигурация

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true   # не падать если БД уже существует
  jpa:
    hibernate:
      ddl-auto: validate        # Hibernate только ПРОВЕРЯЕТ схему, не изменяет
```

Параметр `ddl-auto: validate` — критически важный. Hibernate не создаёт таблицы сам — за это отвечает Flyway. Hibernate только убеждается, что схема соответствует сущностям. Если они расходятся — приложение не стартует, что служит защитой от рассинхронизации.

### Файлы миграций

#### V1__initial_schema.sql

Создаёт все основные таблицы. Важные детали:

- `department.head_doctor_id` добавляется через `ALTER TABLE` **после** создания таблицы `doctor`, чтобы избежать проблемы с порядком зависимостей при `CREATE TABLE`.
- Уникальное ограничение на `(ward_number, department_id)` — один и тот же номер палаты может существовать в разных отделениях.
- Индексы на `patient.status`, `patient.current_doctor_id`, `patient.current_ward_id`, `doctor.specialty` — для ускорения поиска.
- `outbox_event.event_id` — уникальный индекс для идемпотентности Kafka-событий.

#### V2__test_data.sql

Наполняет систему начальными данными для разработки: 2 отделения, 3 врача, 4 палаты, 5 пациентов, 2 платные услуги, исторические записи.

#### V3__add_users.sql

Добавляет таблицу `users` для аутентификации персонала:

```sql
CREATE TABLE users (
    id         BIGSERIAL    PRIMARY KEY,
    username   VARCHAR(50)  NOT NULL UNIQUE,
    password   VARCHAR(255) NOT NULL,
    full_name  VARCHAR(255) NOT NULL,
    role       VARCHAR(20)  NOT NULL DEFAULT 'ROLE_NURSE',
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);
```

---

## 7. Слой репозиториев (Spring Data JPA)

Spring Data JPA генерирует реализации репозиториев автоматически — по именам методов и аннотациям `@Query`. Никакого кода реализации писать не нужно.

### Как это работает

```java
// Spring автоматически генерирует SQL по имени метода:
Optional<Patient> findByIdAndActiveTrue(Long id);
// -> SELECT * FROM patient WHERE id = ? AND active = true

// Кастомный JPQL-запрос:
@Query("SELECT p FROM Patient p WHERE p.currentDoctor.id = :doctorId AND p.active = true")
Page<Patient> findByDoctorId(@Param("doctorId") Long doctorId, Pageable pageable);
```

### PatientRepository — ключевые методы

```java
// Пагинированный список активных пациентов
Page<Patient> findAllByActiveTrue(Pageable pageable);

// Поиск активного пациента по ID (или пустой Optional)
Optional<Patient> findByIdAndActiveTrue(Long id);

// Проверка уникальности СНИЛС
boolean existsBySnilsAndActiveTrue(String snils);

// Количество активных пациентов у врача (для проверки лимита в 20)
@Query("SELECT COUNT(p) FROM Patient p WHERE p.currentDoctor.id = :doctorId " +
       "AND p.active = true AND p.status = 'TREATMENT'")
long countActivePatientsByDoctorId(@Param("doctorId") Long doctorId);

// Поиск по имени и статусу — с cast для решения проблемы Hibernate 6
@Query("SELECT p FROM Patient p WHERE p.active = true " +
       "AND (cast(:q as String) IS NULL OR LOWER(p.fullName) LIKE LOWER(CONCAT('%', cast(:q as String), '%'))) " +
       "AND (:status IS NULL OR p.status = :status)")
Page<Patient> search(@Param("q") String q, @Param("status") PatientStatus status, Pageable pageable);
```

> **Важная техническая деталь про `cast(:q as String)`**
>
> В Hibernate 6, когда в JPQL передаётся `null` в параметр без явного типа, PostgreSQL получает
> параметр типа `bytea` (двоичные данные) — потому что JDBC не может определить тип null-значения.
> Функция `lower(bytea)` в PostgreSQL не существует, и запрос падает с ошибкой:
> `ERROR: function lower(bytea) does not exist`.
>
> Решение: явное приведение `cast(:q as String)` говорит Hibernate передать параметр как
> `character varying`, даже если значение `null`. PostgreSQL видит `lower(null::varchar)` — это корректно.

### WardRepository

```java
// Только палаты со свободными местами в конкретном отделении
@Query("SELECT w FROM Ward w WHERE w.department.id = :departmentId " +
       "AND w.currentOccupancy < w.capacity")
List<Ward> findAvailableWardsByDepartment(@Param("departmentId") Long departmentId);

// Подгрузка отделений за один запрос (JOIN FETCH — избегаем проблемы N+1)
@Query("SELECT w FROM Ward w LEFT JOIN FETCH w.department")
List<Ward> findAllWithDepartment();
```

### Проблема N+1 и как она решается

**Проблема N+1**: если загрузить 100 палат, а потом для каждой обращаться к `ward.getDepartment()`, Hibernate сделает 1 + 100 = 101 SQL-запросов — по одному на каждую палату.

**Решение**: `JOIN FETCH` в JPQL загружает связанные сущности в одном запросе:
```sql
SELECT w.*, d.* FROM ward w LEFT JOIN department d ON w.department_id = d.id
```

### Страничная навигация (Pagination)

Все списочные методы принимают `Pageable` и возвращают `Page<T>`:

```java
// В контроллере:
PageRequest.of(page, size, Sort.by("id"))

// В репозитории:
Page<Patient> findAllByActiveTrue(Pageable pageable);

// Результат — Page содержит:
// content (список записей), totalElements, totalPages, number, size
```

---

## 8. Слой сервисов и бизнес-логика

### Транзакции

Все сервисы аннотированы `@Transactional(readOnly = true)` на уровне класса. Методы, изменяющие данные, переопределяют это на `@Transactional`:

```java
@Service
@Transactional(readOnly = true)   // для всех методов — только чтение
public class PatientServiceImpl implements PatientService {

    @Override
    @Transactional                 // переопределяем — здесь запись
    public PatientResponse create(CreatePatientRequest request) { ... }
}
```

`readOnly = true` — это не только подсказка. Hibernate пропускает dirty checking (проверку изменений всех загруженных объектов) для транзакций только для чтения. На больших объёмах это существенная экономия.

### PatientServiceImpl — ключевая логика

**Создание пациента:**
```
1. Проверить уникальность СНИЛС -> BusinessRuleException если дубль
2. Маппинг DTO -> Entity (MapStruct)
3. Установить registrationDate = today, status = TREATMENT, active = true
4. Сохранить в БД
5. Маппинг Entity -> Response DTO
```

**Назначение врача:**
```
1. Загрузить пациента (404 если не найден)
2. Загрузить врача (404 если не найден)
3. Посчитать текущих пациентов у врача
4. Если >= 20 -> BusinessRuleException (бизнес-правило: максимум 20 пациентов)
5. Найти текущую запись PatientDoctorHistory с assignedTo = null
6. Заполнить assignedTo = now() (закрыть историческую запись)
7. Создать новую PatientDoctorHistory с assignedFrom = now()
8. Установить patient.currentDoctor = новый врач
9. Сохранить
10. Опубликовать PatientEvent в Kafka (в рамках той же транзакции)
```

### WardServiceImpl — логика поступления в палату

```
Поступление пациента:
1. Загрузить палату
2. Проверить freeSlots() > 0 -> BusinessRuleException если нет мест
3. Проверить, нет ли уже открытой WardOccupationHistory для пациента
4. Если есть -> ошибка (пациент уже в палате)
5. Увеличить ward.currentOccupancy++
6. Установить patient.currentWard = палата
7. Создать WardOccupationHistory { admittedAt = now(), dischargedAt = null }
8. Опубликовать AdmissionEvent { action = ADMITTED }

Выписка из палаты:
1. Загрузить открытую WardOccupationHistory
2. Установить dischargedAt = now()
3. Уменьшить ward.currentOccupancy--
4. Установить patient.currentWard = null
5. Опубликовать AdmissionEvent { action = DISCHARGED }
```

### AdminServiceImpl — отчёты и выписка

**Отчёт по заполненности палат:**
```java
@Cacheable("WARD_OCCUPANCY")   // кэшируется в Redis
public WardOccupancyReport getWardOccupancyReport() {
    // Один запрос с JOIN FETCH всех палат + отделений
    // Группировка по отделению через Java Stream
    // Вычисление totalCapacity, totalOccupied, totalFree
}
```

**Выписка пациента:**
```java
@Transactional
@CacheEvict(value = {"WARD_OCCUPANCY", "SERVICES_SUMMARY"}, allEntries = true)
public void dischargePatient(Long patientId, DischargeType type) {
    Patient patient = ...;
    DischargeStrategy strategy = strategyFactory.getStrategy(type);
    strategy.discharge(patient);   // Паттерн Strategy
    patientRepository.save(patient);
    eventPublisher.publishPatientEvent(...);
}
```

При выписке инвалидируется кэш — данные изменились и устаревший кэш не нужен.

---

## 9. REST API — контроллеры

### Аутентификация

| Метод | URL | Доступ | Описание |
|---|---|---|---|
| POST | `/api/auth/login` | Все | Вход. Возвращает JWT + info о пользователе |
| POST | `/api/auth/register` | Все | Регистрация. Роль: `ROLE_NURSE` по умолчанию |

**Запрос login:**
```json
{ "username": "admin", "password": "admin123" }
```

**Ответ login:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "username": "admin",
  "fullName": "Администратор",
  "role": "ROLE_ADMIN"
}
```

### Пациенты `/api/patients`

| Метод | URL | Описание |
|---|---|---|
| POST | `/api/patients` | Создать пациента |
| GET | `/api/patients` | Список (page, size) |
| GET | `/api/patients/{id}` | По ID |
| GET | `/api/patients/search` | Поиск по имени (?q=) и/или статусу (?status=) |
| PUT | `/api/patients/{id}` | Обновить |
| DELETE | `/api/patients/{id}` | Мягкое удаление |
| PUT | `/api/patients/{patientId}/assign-doctor/{doctorId}` | Назначить врача |
| GET | `/api/patients/{patientId}/services` | Платные услуги пациента |

### Врачи `/api/doctors`

| Метод | URL | Описание |
|---|---|---|
| POST | `/api/doctors` | Создать |
| GET | `/api/doctors` | Список (опционально: ?specialty=CARDIOLOGIST) |
| GET | `/api/doctors/{id}` | По ID |
| PUT | `/api/doctors/{id}` | Обновить |
| DELETE | `/api/doctors/{id}` | Мягкое удаление |
| GET | `/api/doctors/{id}/patients` | Пациенты врача |

### Отделения `/api/departments`

| Метод | URL | Описание |
|---|---|---|
| POST | `/api/departments` | Создать |
| GET | `/api/departments` | Список всех |
| GET | `/api/departments/{id}` | По ID |
| PUT | `/api/departments/{id}` | Обновить |
| DELETE | `/api/departments/{id}` | Удалить (физически) |

### Палаты `/api/wards`

| Метод | URL | Описание |
|---|---|---|
| POST | `/api/wards` | Создать палату |
| GET | `/api/wards` | Список всех |
| GET | `/api/wards/{id}` | По ID |
| POST | `/api/wards/{wardId}/admit/{patientId}` | Поместить пациента в палату |
| POST | `/api/wards/{wardId}/discharge/{patientId}` | Выписать из палаты |

### Платные услуги

| Метод | URL | Описание |
|---|---|---|
| POST | `/api/paid-services` | Создать услугу |
| GET | `/api/paid-services` | Список (page, size) |
| GET | `/api/paid-services/{id}` | По ID |
| POST | `/api/patients/{patientId}/paid-services/{serviceId}` | Назначить услугу пациенту |
| PATCH | `/api/patients/{patientId}/paid-services/{linkId}/pay` | Отметить как оплаченную |

### Администрирование `/api/admin` (только `ROLE_ADMIN`)

| Метод | URL | Описание |
|---|---|---|
| GET | `/api/admin/reports/ward-occupancy` | Отчёт по заполненности палат (кэш Redis) |
| GET | `/api/admin/reports/paid-services-summary` | Финансовая сводка (кэш Redis) |
| POST | `/api/admin/patients/{patientId}/discharge` | Выписать (?type=NORMAL\|FORCED\|TRANSFER) |

### Формат страничного ответа `PageResponse<T>`

```json
{
  "content": [ ... ],
  "page": 0,
  "size": 20,
  "totalElements": 150,
  "totalPages": 8,
  "last": false
}
```

---

## 10. DTO и валидация

DTO (Data Transfer Object) — это «контракт» между клиентом и сервером. Сущности JPA никогда не передаются напрямую — только DTO. Это изолирует внутреннюю структуру данных от API.

### Bean Validation (Jakarta Validation)

Аннотации валидации на полях DTO автоматически проверяются Spring MVC при обработке `@Valid @RequestBody`.

```java
public class CreatePatientRequest {
    @NotBlank
    @Size(max = 255)
    private String fullName;

    @NotNull
    @Past                           // дата рождения должна быть в прошлом
    private LocalDate birthDate;

    @NotNull
    private Gender gender;

    @NotBlank
    @Pattern(regexp = "\\d{3}-\\d{3}-\\d{3} \\d{2}",
             message = "СНИЛС должен быть в формате XXX-XXX-XXX XX")
    private String snils;

    @Pattern(regexp = "\\+?[\\d\\-() ]{7,20}")
    private String phone;           // необязательное поле
}
```

Если валидация не прошла, Spring выбрасывает `MethodArgumentNotValidException`. `GlobalExceptionHandler` перехватывает его и возвращает:

```json
{
  "status": 400,
  "error": "Validation Failed",
  "message": "Ошибки валидации",
  "fieldErrors": {
    "snils": "СНИЛС должен быть в формате XXX-XXX-XXX XX",
    "birthDate": "must be a past date"
  }
}
```

---

## 11. MapStruct — маппинг объектов

**MapStruct** — генератор кода для маппинга объектов. В отличие от рефлексивных библиотек (ModelMapper), MapStruct генерирует обычный Java-код во время компиляции. Это даёт **нулевые накладные расходы в runtime** и выявляет ошибки маппинга на этапе компиляции.

### Как это работает

```java
@Mapper(componentModel = "spring")   // Spring-бин, инжектируется через @Autowired
public interface PatientMapper {

    // Простой маппинг: поля с одинаковыми именами копируются автоматически.
    // Поля, которые нужно проигнорировать:
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "registrationDate", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "currentDoctor", ignore = true)
    @Mapping(target = "currentWard", ignore = true)
    @Mapping(target = "active", ignore = true)
    Patient toEntity(CreatePatientRequest request);

    // Маппинг вложенных объектов: patient.currentDoctor.id -> currentDoctorId
    @Mapping(source = "currentDoctor.id",       target = "currentDoctorId")
    @Mapping(source = "currentDoctor.fullName",  target = "currentDoctorName")
    @Mapping(source = "currentWard.id",          target = "currentWardId")
    @Mapping(source = "currentWard.wardNumber",  target = "currentWardNumber")
    PatientResponse toResponse(Patient patient);

    // Обновление существующего объекта (только не-null поля из request)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateFromRequest(@MappingTarget Patient patient, UpdatePatientRequest request);
}
```

MapStruct генерирует класс `PatientMapperImpl`. В runtime это обычный Java-код без рефлексии:

```java
// Сгенерированный код (упрощённо):
public PatientResponse toResponse(Patient patient) {
    if (patient == null) return null;
    PatientResponse response = new PatientResponse();
    response.setFullName(patient.getFullName());
    if (patient.getCurrentDoctor() != null) {
        response.setCurrentDoctorId(patient.getCurrentDoctor().getId());
        response.setCurrentDoctorName(patient.getCurrentDoctor().getFullName());
    }
    // и т.д.
    return response;
}
```

### WardMapper — вычисляемое поле

```java
@Mapping(source = "department.id",   target = "departmentId")
@Mapping(source = "department.name", target = "departmentName")
@Mapping(expression = "java(ward.freeSlots())", target = "freeSlots")
WardResponse toResponse(Ward ward);
```

Поле `freeSlots` — вычисляемое (`capacity - currentOccupancy`), поэтому используется `expression = "java(...)"`.

---

## 12. Безопасность: Spring Security + JWT

### Как работает JWT-аутентификация

```
1. Клиент -> POST /api/auth/login { username, password }
2. Spring Security -> AuthenticationManager.authenticate()
3. UserDetailsServiceImpl -> загружает User из БД
4. BCrypt проверяет пароль
5. JwtUtil.generateToken() -> создаёт JWT
6. Сервер -> 200 OK { token, username, role }

--- Последующие запросы ---

7. Клиент -> GET /api/patients
            Authorization: Bearer eyJhbGc...
8. JwtAuthenticationFilter.doFilterInternal()
   - Извлекает токен из заголовка
   - JwtUtil.extractUsername(token)
   - UserDetailsService.loadUserByUsername()
   - JwtUtil.validateToken(token, userDetails)
   - Помещает Authentication в SecurityContextHolder
9. Spring Security проверяет права доступа
10. Запрос передаётся контроллеру
```

### JwtUtil

```java
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration-ms}")
    private long expirationMs;    // 86400000 = 24 часа

    public String generateToken(UserDetails userDetails) {
        return Jwts.builder()
            .subject(userDetails.getUsername())
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + expirationMs))
            .signWith(getSignKey())          // алгоритм HS256
            .compact();
    }

    private SecretKey getSignKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }

    public boolean validateToken(String token, UserDetails userDetails) {
        String username = extractUsername(token);
        return username.equals(userDetails.getUsername())
            && !isTokenExpired(token);
    }
}
```

### Структура JWT-токена

JWT состоит из трёх частей, разделённых точкой: `header.payload.signature`

```
Header:    { "alg": "HS256", "typ": "JWT" }
Payload:   { "sub": "admin", "iat": 1714000000, "exp": 1714086400 }
Signature: HMACSHA256(base64(header) + "." + base64(payload), secret)
```

Payload не зашифрован (только base64). Не храни в JWT чувствительные данные. Подпись защищает от подделки — без знания секрета изменить payload и пересчитать корректную подпись невозможно.

### SecurityConfig

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) {
        return http
            .csrf(csrf -> csrf.disable())                   // REST API — CSRF не нужен
            .sessionManagement(s ->
                s.sessionCreationPolicy(STATELESS))         // без сессий
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()    // login/register открыты
                .requestMatchers("/swagger-ui/**").permitAll()  // Swagger открыт
                .requestMatchers("/api/admin/**").hasRole("ADMIN") // только ADMIN
                .anyRequest().authenticated()               // остальное — с токеном
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(...)   // 401 JSON вместо redirect на /login
                .accessDeniedHandler(...)        // 403 JSON вместо redirect
            )
            .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

**Почему `csrf.disable()`?** CSRF-атака работает только при cookie-based сессиях. Наш API использует JWT в заголовке — браузер не отправляет его автоматически при cross-origin запросах, поэтому CSRF неприменим.

**Роли**: в Spring Security роли хранятся с префиксом `ROLE_`. Метод `hasRole("ADMIN")` проверяет наличие `ROLE_ADMIN`. В БД и токене хранится `ROLE_ADMIN` (с префиксом).

---

## 13. Apache Kafka — событийная архитектура

### Зачем Kafka в этом проекте

Kafka позволяет **развязать** части системы. Например:
- Биллинговая система обрабатывает платёжные события независимо от основной логики.
- Аналитика слушает события о пациентах, не нагружая основную БД.
- При сбое консьюмера события не теряются — они ждут в Kafka.

### Топики

| Топик | Тип событий | Консьюмер |
|---|---|---|
| `patient-events` | Назначение врача, смена статуса | `PatientEventConsumer` |
| `admission-events` | Поступление/выписка из палаты | `AdmissionEventConsumer` |
| `paid-service-events` | Назначение платных услуг | `PaidServiceEventConsumer` |
| `doctor-events` | Создание врача | (логирование) |
| `department-events` | Создание/удаление отделения | (логирование) |
| `*.DLT` | Dead-Letter Topics — необработанные события | (мониторинг) |

Каждый топик имеет **3 партиции** и **1 реплику** (локальная разработка).

### KafkaConfig — настройка Producer

```java
@Bean
public ProducerFactory<String, Object> producerFactory() {
    Map<String, Object> props = new HashMap<>();
    props.put(ENABLE_IDEMPOTENCE_CONFIG, true);   // ровно одна доставка
    props.put(ACKS_CONFIG, "all");                 // подтверждение от всех реплик
    props.put(RETRIES_CONFIG, 3);                  // 3 попытки при ошибке
    props.put("spring.json.add.type.headers", false); // без type headers в JSON
    factory.setTransactionIdPrefix("tx-hospital-");   // транзакционный producer
    return factory;
}
```

**Транзакции Kafka** (`transaction-id-prefix`): если несколько сообщений отправляются в рамках одной операции, транзакция Kafka гарантирует — либо все дойдут, либо ни одно. Критично для Outbox Pattern.

**Идемпотентность** (`enable.idempotence = true`): каждое сообщение имеет уникальный sequence number. Если producer повторно отправит сообщение (при сбое сети), Kafka обнаружит дубль и откажет в записи.

**acks=all**: producer ждёт подтверждения от всех синхронных реплик. Максимальная надёжность.

### Dead-Letter Topics (DLT)

Если консьюмер не смог обработать сообщение (исключение, 2 попытки с паузой 1 секунда), сообщение перемещается в топик `<name>.DLT`. Данные не теряются — проблемное сообщение можно повторно обработать.

### Пример события

```java
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PatientEvent {
    private String eventId;           // UUID, уникальный идентификатор
    private String eventType;         // "PATIENT_ASSIGNED_DOCTOR"
    private LocalDateTime occurredAt;
    private Long patientId;
    private String patientName;
    private String newStatus;
    private Long previousDoctorId;
    private Long newDoctorId;
}
```

### Консьюмер с идемпотентностью

```java
@KafkaListener(topics = "patient-events", groupId = "hospital-patient-consumer")
public void handlePatientEvent(String message, Acknowledgment ack) {
    try {
        PatientEvent event = objectMapper.readValue(message, PatientEvent.class);

        // Идемпотентность: если событие уже обработано — пропустить
        if (outboxEventRepository.existsByEventId(event.getEventId())) {
            ack.acknowledge();
            return;
        }

        // Обработка события...
        log.info("Patient event: type={}, patientId={}", event.getEventType(), event.getPatientId());

        // Отметить как обработанное
        outboxEventRepository.findByEventId(event.getEventId())
            .ifPresent(e -> { e.setProcessed(true); outboxEventRepository.save(e); });

        ack.acknowledge();   // manual ack: подтверждаем ТОЛЬКО при успехе
    } catch (Exception e) {
        log.error("Failed to process event", e);
        // НЕ вызываем ack.acknowledge() -> Kafka повторит доставку
    }
}
```

**Manual acknowledgment** (`ack-mode: manual_immediate`): консьюмер явно подтверждает обработку вызовом `ack.acknowledge()`. Если исключение — подтверждение не приходит, Kafka считает сообщение необработанным и повторит доставку. Это надёжнее автоматического подтверждения.

---

## 14. Паттерн Outbox

### Проблема «двух записей»

Нужно сохранить изменение в БД **и** отправить событие в Kafka. Что если приложение упадёт между этими двумя операциями?

```java
// Ненадёжно:
patientRepository.save(patient);     // <- транзакция закоммичена
// <- здесь может упасть JVM
kafkaTemplate.send("events", event); // <- событие потеряно
```

### Решение: Outbox Pattern

Событие записывается в таблицу `outbox_event` **в одной транзакции** с основным изменением данных. Kafka-отправка происходит в той же транзакции.

```java
@Component
@Transactional(propagation = Propagation.MANDATORY)  // только внутри существующей транзакции
public class EventPublisher {

    public void publishPatientEvent(PatientEvent event) {
        String eventId = UUID.randomUUID().toString();
        String payload = objectMapper.writeValueAsString(event);

        // 1. Записываем событие в outbox_event (в той же транзакции что и основные данные)
        outboxEventRepository.save(OutboxEvent.builder()
            .eventId(eventId)
            .eventType(event.getEventType())
            .payload(payload)
            .createdAt(LocalDateTime.now())
            .processed(false)
            .build());

        // 2. Отправляем в Kafka (в той же транзакции Kafka)
        kafkaTemplate.send("patient-events", payload);
    }
}
```

`@Transactional(propagation = MANDATORY)` означает: этот метод **обязан** вызываться только внутри уже существующей транзакции. Если транзакции нет — Spring выбросит исключение. Это гарантирует атомарность: либо и изменение данных, и событие в outbox, либо ничего.

---

## 15. Redis — кэширование

### Конфигурация

```java
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(5))         // TTL = 5 минут
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new GenericJackson2JsonRedisSerializer()  // сериализация в JSON
                )
            );
        return RedisCacheManager.builder(factory)
            .cacheDefaults(config)
            .build();
    }
}
```

### Использование в AdminServiceImpl

```java
@Cacheable("WARD_OCCUPANCY")    // первый вызов -> выполнить метод и сохранить в Redis
public WardOccupancyReport getWardOccupancyReport() {
    // Дорогой запрос — GROUP BY, агрегаты...
}

@CacheEvict(value = {"WARD_OCCUPANCY", "SERVICES_SUMMARY"}, allEntries = true)
public void dischargePatient(Long patientId, DischargeType type) {
    // После выписки данные изменились -> инвалидируем кэш
}
```

**Как это работает:**
1. Первый запрос `GET /api/admin/reports/ward-occupancy` — метод выполняется, результат кладётся в Redis с TTL 5 минут.
2. Второй запрос через 2 минуты — Spring видит ключ в Redis, **не вызывает** метод, возвращает кэшированное значение.
3. Через 5 минут TTL истекает — следующий запрос снова выполняет метод.
4. После выписки пациента (`@CacheEvict`) — кэш принудительно сбрасывается.

### Почему тесты отключают Redis

```yaml
# application-test.yml
spring:
  cache:
    type: none    # отключить кэш в тестах
  autoconfigure:
    exclude:
      - org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration
```

В тестах Redis недоступен (нет реального сервера). Кэш отключается, Redis-автоконфигурация исключается, чтобы Spring Boot не падал при попытке подключиться.

---

## 16. Паттерн Strategy — выписка пациентов

### Проблема без Strategy

```java
// Плохо: разрастающаяся условная логика прямо в сервисе
public void dischargePatient(Long id, String type) {
    if ("NORMAL".equals(type)) {
        patient.setStatus(DISCHARGED);
        patient.setCurrentDoctor(null);
    } else if ("FORCED".equals(type)) {
        log.warn("Forced discharge!");
        patient.setStatus(DISCHARGED);
        patient.setCurrentDoctor(null);
    } else if ("TRANSFER".equals(type)) {
        patient.setStatus(TRANSFERRED);
        patient.setCurrentDoctor(null);
    }
    // Добавить новый тип = менять этот метод...
}
```

### Решение: Strategy Pattern

```java
// Интерфейс стратегии
public interface DischargeStrategy {
    DischargeType getType();
    void discharge(Patient patient);
}

// Стратегия NORMAL
@Component
public class NormalDischargeStrategy implements DischargeStrategy {
    @Override public DischargeType getType() { return DischargeType.NORMAL; }
    @Override
    public void discharge(Patient patient) {
        patient.setStatus(PatientStatus.DISCHARGED);
        patient.setCurrentDoctor(null);
    }
}

// Стратегия FORCED (добавляет предупреждение)
@Component
public class ForcedDischargeStrategy implements DischargeStrategy {
    @Override public DischargeType getType() { return DischargeType.FORCED; }
    @Override
    public void discharge(Patient patient) {
        log.warn("FORCED discharge of patient id={}", patient.getId());
        patient.setStatus(PatientStatus.DISCHARGED);
        patient.setCurrentDoctor(null);
    }
}

// Стратегия TRANSFER (другой статус)
@Component
public class TransferDischargeStrategy implements DischargeStrategy {
    @Override public DischargeType getType() { return DischargeType.TRANSFER; }
    @Override
    public void discharge(Patient patient) {
        patient.setStatus(PatientStatus.TRANSFERRED);
        patient.setCurrentDoctor(null);
    }
}

// Фабрика стратегий
@Component
public class DischargeStrategyFactory {
    private final EnumMap<DischargeType, DischargeStrategy> strategies;

    // Spring инжектирует все бины, реализующие DischargeStrategy
    public DischargeStrategyFactory(List<DischargeStrategy> strategyList) {
        strategies = new EnumMap<>(DischargeType.class);
        strategyList.forEach(s -> strategies.put(s.getType(), s));
    }

    public DischargeStrategy getStrategy(DischargeType type) {
        DischargeStrategy strategy = strategies.get(type);
        if (strategy == null) throw new IllegalArgumentException("Unknown type: " + type);
        return strategy;
    }
}
```

**Преимущества:**
- Добавление нового типа выписки = новый класс + `@Component`. Сервис `AdminServiceImpl` не меняется.
- Каждая стратегия тестируется изолированно.
- Никакого разрастающегося `if/else` в основной логике.

---

## 17. AOP — сквозное логирование

**AOP (Aspect-Oriented Programming)** — подход к вынесению сквозной логики (логирование, трассировка, замер времени) из основного кода.

### Как это работает

```java
@Aspect
@Component
@Slf4j
public class AopLoggingAspect {

    // Pointcut: все публичные методы в пакете service.impl
    @Around("execution(public * com.hospital.service.impl.*.*(..))")
    public Object logServiceMethod(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().toShortString();
        long start = System.currentTimeMillis();

        log.debug(">>> Entering: {}", methodName);
        try {
            Object result = joinPoint.proceed();   // выполнить оригинальный метод
            long elapsed = System.currentTimeMillis() - start;
            log.debug("<<< Completed: {} in {}ms", methodName, elapsed);
            return result;
        } catch (Throwable e) {
            long elapsed = System.currentTimeMillis() - start;
            log.warn("<<< Exception in {} after {}ms: {}", methodName, elapsed, e.getMessage());
            throw e;
        }
    }
}
```

В логах видно:
```
DEBUG c.h.config.AopLoggingAspect - >>> Entering: PatientServiceImpl.create(..)
DEBUG c.h.config.AopLoggingAspect - <<< Completed: PatientServiceImpl.create(..) in 45ms

DEBUG c.h.config.AopLoggingAspect - >>> Entering: PatientServiceImpl.search(..)
WARN  c.h.config.AopLoggingAspect - <<< Exception in PatientServiceImpl.search(..) after 3ms: ...
```

Ни один из 6 сервисов не содержит кода для замера времени — аспект применяется ко всем им автоматически. Это и есть суть AOP: одно место, сквозное действие.

---

## 18. Обработка ошибок

### Иерархия исключений

```
RuntimeException
+-- ResourceNotFoundException   -- сущность не найдена (-> 404 Not Found)
+-- BusinessRuleException       -- нарушение бизнес-правила (-> 409 Conflict)
```

### GlobalExceptionHandler

```java
@RestControllerAdvice   // перехватывает исключения из всех @RestController
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(ResourceNotFoundException ex, HttpServletRequest req) {
        return ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(404).error("Not Found")
            .message(ex.getMessage())
            .path(req.getRequestURI())
            .build();
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(MethodArgumentNotValidException ex, ...) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
            .forEach(e -> errors.put(e.getField(), e.getDefaultMessage()));
        return ErrorResponse.builder()
            .status(400).error("Validation Failed")
            .fieldErrors(errors)   // карта поле -> сообщение об ошибке
            .build();
    }
}
```

### Формат ответа об ошибке

```json
{
  "timestamp": "2024-01-15T12:00:00",
  "status": 404,
  "error": "Not Found",
  "message": "Patient with id 999 not found",
  "path": "/api/patients/999",
  "fieldErrors": null
}
```

---

## 19. Swagger / OpenAPI

После запуска приложения документация доступна по адресу: `http://localhost:8080/swagger-ui.html`

Все контроллеры аннотированы:
```java
@Tag(name = "Patients", description = "Patient management API")
@Operation(summary = "Register a new patient")
```

Swagger UI позволяет:
- Просматривать все эндпоинты с описанием и схемами запросов/ответов.
- Авторизоваться — ввести JWT-токен через кнопку **Authorize**.
- Отправлять тестовые запросы прямо из браузера.

---

## 20. Spring Boot Actuator

Actuator предоставляет эндпоинты для мониторинга приложения:

| Эндпоинт | Описание |
|---|---|
| `GET /actuator/health` | Состояние приложения, БД, Kafka, Redis |
| `GET /actuator/info` | Информация о приложении (name, version) |
| `GET /actuator/metrics` | Метрики JVM, HTTP-запросов, памяти |
| `GET /actuator/prometheus` | Метрики в формате для Prometheus |

Пример ответа `/actuator/health`:
```json
{
  "status": "UP",
  "components": {
    "db":    { "status": "UP" },
    "kafka": { "status": "UP" },
    "redis": { "status": "UP" }
  }
}
```

---

## 21. Docker и docker-compose

### Dockerfile — многоэтапная сборка

```dockerfile
# --- Этап 1: Сборка ---
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline          # скачиваем зависимости отдельно (кэш слоя)
COPY src ./src
RUN mvn package -DskipTests

# --- Этап 2: Runtime ---
FROM eclipse-temurin:17-jre-alpine     # минимальный образ ~180MB
RUN adduser -D hospital                # непривилегированный пользователь
USER hospital
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Многоэтапная сборка** позволяет получить маленький финальный образ. Maven и исходники в него не попадают — только скомпилированный JAR. Слой `dependency:go-offline` кэшируется Docker и не перекачивается при изменении только исходников.

### docker-compose.yml

Описывает 7 сервисов инфраструктуры (само приложение запускается локально через Maven/IDE):

| Сервис | Образ | Порт | Назначение |
|---|---|---|---|
| postgres | postgres:15-alpine | 5432 | Основная БД |
| zookeeper | confluentinc/cp-zookeeper:7.6.0 | 2181 | Координация Kafka |
| kafka | confluentinc/cp-kafka:7.6.0 | 9092 | Брокер сообщений |
| redis | redis:7-alpine | 6379 | Кэш |
| kafdrop | obsidiandynamics/kafdrop | 9000 | Web UI для Kafka |
| **loki** | grafana/loki:2.9.0 | 3100 | Хранилище логов |
| **grafana** | grafana/grafana:10.2.3 | 3000 | Визуализация логов |

**Kafdrop** — `http://localhost:9000`. Просмотр топиков, чтение сообщений, мониторинг групп консьюмеров.

**Grafana** — `http://localhost:3000` (admin / admin). Datasource Loki провизионируется автоматически из `monitoring/grafana/provisioning/datasources/loki.yml`.

**Loki** получает логи напрямую от Spring Boot через `loki4j` Logback-аппендер — без Promtail и без агентов.

---

## 22. Тестирование

### Структура тестов

```
src/test/java/com/hospital/
|
+-- integration/
|   +-- AbstractIntegrationTest.java      # базовый класс: проверка наличия Docker
|   +-- TestTransactionConfig.java        # @TestConfiguration: явный JpaTransactionManager
|   +-- AuthIntegrationTest.java          # 12 тестов: вход, регистрация, авторизация
|   +-- PatientIntegrationTest.java       # 8 тестов: CRUD пациентов, поиск
|
+-- service/
|   +-- PatientServiceTest.java           # 10 юнит-тестов PatientService
|   +-- WardServiceTest.java              # 5 юнит-тестов WardService
|   +-- AdminServiceTest.java             # 9 юнит-тестов AdminService
|
+-- config/
    +-- JwtUtilTest.java                  # 5 тестов генерации и валидации JWT
```

**Итого: 49 тестов — все проходят.**

### Юнит-тесты (Mockito)

Тестируют бизнес-логику сервисов в изоляции. Все зависимости заменены моками.

```java
@ExtendWith(MockitoExtension.class)
class PatientServiceTest {

    @Mock PatientRepository patientRepository;
    @Mock PatientMapper patientMapper;
    @Mock EventPublisher eventPublisher;
    @InjectMocks PatientServiceImpl patientService;   // внедряем моки

    @Test
    void create_withDuplicateSnils_throwsBusinessRuleException() {
        when(patientRepository.existsBySnilsAndActiveTrue("111-222-333 44")).thenReturn(true);

        CreatePatientRequest request = new CreatePatientRequest();
        request.setSnils("111-222-333 44");

        assertThrows(BusinessRuleException.class,
            () -> patientService.create(request));

        verify(patientRepository, never()).save(any());   // сохранения быть не должно
    }
}
```

### Интеграционные тесты (Testcontainers + EmbeddedKafka)

Поднимают **полный** Spring Boot контекст с реальной PostgreSQL в Docker-контейнере.

```java
@SpringBootTest                    // полный контекст Spring
@AutoConfigureMockMvc              // MockMvc для HTTP-запросов
@ActiveProfiles("test")            // профиль: test
@EmbeddedKafka(                    // встроенный Kafka-брокер
    partitions = 1,
    topics = {"patient-events", "admission-events", ...},
    brokerProperties = {
        "transaction.state.log.replication.factor=1",
        "transaction.state.log.min.isr=1"
    })
@Import(TestTransactionConfig.class)  // явный JpaTransactionManager (см. ниже)
@DirtiesContext                       // пересоздать контекст после теста
class AuthIntegrationTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;

    @Test
    void login_withValidAdminCredentials_returnsToken() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{ "username": "admin", "password": "admin123" }"""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").isNotEmpty())
            .andExpect(jsonPath("$.role").value("ROLE_ADMIN"));
    }
}
```

### Как работает Testcontainers

```yaml
# application-test.yml
spring:
  datasource:
    # Специальный JDBC URL: "tc:" говорит Testcontainers запустить контейнер
    url: jdbc:tc:postgresql:15:///hospital_test_db
    driver-class-name: org.testcontainers.jdbc.ContainerDatabaseDriver
```

1. JUnit запускает тест.
2. Testcontainers видит `jdbc:tc:postgresql:15:///` и запускает Docker-контейнер с PostgreSQL 15.
3. Flyway применяет все миграции к тестовой БД.
4. Тесты работают с реальной, изолированной БД.
5. После тестов контейнер автоматически удаляется (Ryuk reaper).

### Проблема транзакций в тестах и её решение

**Проблема**: в `application.yml` есть `transaction-id-prefix: tx-hospital-`. Это заставляет Spring Kafka создать `KafkaTransactionManager`, который реализует `PlatformTransactionManager`. Spring видит, что `PlatformTransactionManager` уже есть, и **не создаёт** `JpaTransactionManager`. JPA-репозитории падают с ошибкой: `No bean named 'transactionManager' available`.

**Решение**: добавить явный `JpaTransactionManager` в тестовую конфигурацию:

```java
@TestConfiguration
class TestTransactionConfig {
    @Bean
    @Primary   // приоритет перед KafkaTransactionManager
    PlatformTransactionManager transactionManager(EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }
}
```

Этот класс импортируется явно через `@Import(TestTransactionConfig.class)` в каждый интеграционный тест.

### Настройка Docker для тестов (Maven Surefire)

Testcontainers требует Docker. На Windows с Docker Desktop нужна дополнительная настройка, которая прописана в `pom.xml` один раз:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <systemPropertyVariables>
            <api.version>1.44</api.version>
        </systemPropertyVariables>
        <environmentVariables>
            <DOCKER_HOST>tcp://127.0.0.1:2375</DOCKER_HOST>
        </environmentVariables>
    </configuration>
</plugin>
```

**Почему `api.version=1.44`?** Docker Desktop на Windows выставляет прокси перед реальным Docker Engine. Этот прокси отвергает запросы с API версией ниже 1.40, а Testcontainers по умолчанию использует версию 1.32. Указание `api.version=1.44` заставляет Testcontainers общаться актуальной версией API.

**Почему TCP 2375?** На Windows Docker Desktop слушает именованные пайпы (`\\.\pipe\docker_engine`), с которыми Testcontainers работает нестабильно. TCP-эндпоинт — надёжная альтернатива.

Включить TCP 2375 в Docker Desktop: **Settings → General → "Expose daemon on tcp://localhost:2375 without TLS"**.

---

## 23. Запуск проекта

### Требования

| Инструмент | Версия | Зачем |
|---|---|---|
| Java | 17+ | Запуск приложения |
| Maven | 3.9+ | Сборка проекта |
| Docker Desktop | 4.x+ | Контейнеры инфраструктуры |

> **Windows**: для Testcontainers включите TCP в Docker Desktop:
> **Settings → General → Expose daemon on tcp://localhost:2375 without TLS**

---

### Быстрый старт (2 команды)

```bash
# 1. Поднять всю инфраструктуру
docker-compose up -d

# 2. Запустить приложение
mvn spring-boot:run
```

После старта открыть: **http://localhost:8080** (войти: admin / admin123)

---

### Пошаговый запуск с объяснениями

#### Шаг 1 — Поднять инфраструктуру

```bash
docker-compose up -d
```

Запускает 7 контейнеров. Проверить готовность:

```bash
docker-compose ps
```

Все сервисы должны быть `running` или `healthy`.

| Контейнер | Порт | Что там |
|---|---|---|
| hospital-postgres | 5432 | PostgreSQL — основная БД |
| hospital-redis | 6379 | Redis — кэш |
| hospital-zookeeper | 2181 | Zookeeper (для Kafka) |
| hospital-kafka | 9092 | Apache Kafka |
| hospital-kafdrop | http://localhost:9000 | UI просмотра Kafka-топиков |
| **hospital-loki** | 3100 | Хранилище логов |
| **hospital-grafana** | http://localhost:3000 | Дашборды логов |

#### Шаг 2 — Запустить приложение

```bash
mvn spring-boot:run
```

Или через IDE — запустить `HospitalApplication.java`.

При старте происходит автоматически:
- Flyway применяет миграции V1 → V2 → V3
- `DataInitializer` создаёт пользователя `admin / admin123`
- `loki4j` начинает отправлять логи в Loki на `localhost:3100`

#### Шаг 3 — Открыть интерфейсы

| Интерфейс | URL | Учётные данные |
|---|---|---|
| **Web-интерфейс (HIS)** | http://localhost:8080 | admin / admin123 |
| Swagger UI | http://localhost:8080/swagger-ui.html | — |
| API Docs | http://localhost:8080/api-docs | — |
| Actuator Health | http://localhost:8080/actuator/health | — |
| Kafdrop (Kafka UI) | http://localhost:9000 | — |
| **Grafana** | http://localhost:3000 | admin / admin |

---

### Запуск только части сервисов

```bash
# Только БД + кэш (минимум для разработки без Kafka)
docker-compose up -d postgres redis

# С Kafka (для тестирования событий)
docker-compose up -d postgres redis zookeeper kafka kafdrop

# Только стек мониторинга (если инфра уже запущена)
docker-compose up -d loki grafana
```

---

### Просмотр логов в Grafana

1. Открыть **http://localhost:3000** (admin / admin)
2. В левом меню: **Explore** (иконка компаса 🧭)
3. В выпадающем списке вверху выбрать **Loki** — он уже добавлен автоматически
4. В поле запроса ввести: `{app="pet-hospital"}`
5. Нажать **Run query**

**Полезные LogQL-запросы:**

```logql
# Все логи приложения
{app="pet-hospital"}

# Только ошибки
{app="pet-hospital"} |= "ERROR"

# Логи конкретного сервиса
{app="pet-hospital"} |= "PatientServiceImpl"

# Найти исключения
{app="pet-hospital"} |= "Exception"

# AOP-логи — замер времени выполнения
{app="pet-hospital"} |= "Completed"

# Только WARN и выше
{app="pet-hospital", level=~"WARN|ERROR"}
```

---

### Остановка

```bash
# Остановить контейнеры, данные сохраняются в volumes
docker-compose down

# Остановить и полностью удалить все данные
docker-compose down -v
```

---

### Первый вход

`DataInitializer` создаёт администратора автоматически:

```
Логин:   admin
Пароль:  admin123
Роль:    ROLE_ADMIN
```

Для создания врача или медсестры — зарегистрироваться через http://localhost:8080/register.html.
По умолчанию все новые пользователи получают роль `ROLE_NURSE`.
Сменить роль можно только напрямую в БД или через SQL-запрос.

**Получить JWT-токен через curl:**
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

**Использовать токен:**
```bash
curl http://localhost:8080/api/patients \
  -H "Authorization: Bearer <token>"
```

---

### Запуск тестов

```bash
# Все тесты (нужен Docker Desktop с открытым TCP 2375)
mvn test

# Только юнит-тесты (Docker не нужен)
mvn test -Dtest="PatientServiceTest,WardServiceTest,AdminServiceTest,JwtUtilTest"

# Только интеграционные тесты
mvn test -Dtest="AuthIntegrationTest,PatientIntegrationTest"
```

---

## 24. Конфигурация

### application.yml (production)

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/hospital_db
    username: postgres
    password: "1234"

  jpa:
    hibernate:
      ddl-auto: validate       # Flyway управляет схемой, Hibernate только проверяет
    show-sql: false

  flyway:
    enabled: true
    baseline-on-migrate: true

  data:
    redis:
      host: localhost
      port: 6379

  cache:
    type: redis
    redis:
      time-to-live: 300000     # 5 минут в миллисекундах

  kafka:
    bootstrap-servers: localhost:9092
    producer:
      transaction-id-prefix: tx-hospital-
      acks: all
      retries: 3
    consumer:
      auto-offset-reset: earliest
      group-id: hospital-group
    listener:
      ack-mode: manual_immediate

jwt:
  secret: "pet-hospital-his-jwt-secret-key-for-hs256-authentication-2024"
  expiration-ms: 86400000      # 24 часа
```

### application-test.yml (тестовый профиль)

```yaml
spring:
  datasource:
    url: jdbc:tc:postgresql:15:///hospital_test_db   # Testcontainers JDBC URL
    driver-class-name: org.testcontainers.jdbc.ContainerDatabaseDriver

  cache:
    type: none                  # Redis отключён в тестах

  autoconfigure:
    exclude:
      - org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration
      - org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration

  kafka:
    bootstrap-servers: ${spring.embedded.kafka.brokers}  # EmbeddedKafka
    producer:
      transaction-id-prefix: ""   # переопределяем — отключаем Kafka-транзакции в тестах
```

---

## 25. Бизнес-правила системы

| Правило | Где реализовано | HTTP-код ошибки |
|---|---|---|
| СНИЛС пациента уникален | `PatientServiceImpl.create()` | 409 Conflict |
| Максимум 20 пациентов на врача | `PatientServiceImpl.assignDoctor()` | 409 Conflict |
| Пациент может быть только в одной палате | `WardServiceImpl.admitPatient()` | 409 Conflict |
| Нельзя поместить в палату без мест | `WardServiceImpl.admitPatient()` | 409 Conflict |
| Пароль минимум 6 символов | `RegisterRequest` Bean Validation | 400 Bad Request |
| Дата рождения в прошлом | `CreatePatientRequest` Bean Validation | 400 Bad Request |
| СНИЛС в формате XXX-XXX-XXX XX | `CreatePatientRequest` Bean Validation | 400 Bad Request |
| Отчёты только для ROLE_ADMIN | `SecurityConfig` | 403 Forbidden |
| Мягкое удаление (не физическое) | `Patient.active = false` | — |
| Аудит смены врача | `PatientDoctorHistory` | — |
| Аудит смены палаты | `WardOccupationHistory` | — |
| Идемпотентность Kafka-событий | `OutboxEvent.eventId` + проверка в консьюмере | — |
| Транзакционность Kafka | `transaction-id-prefix: tx-hospital-` | — |

---

---

## 26. Ролевой интерфейс — фронтенд

### Обзор

Фронтенд — SPA на чистом JS/HTML (без фреймворков). Один файл `index.html` адаптируется под роль текущего пользователя. При входе роль из JWT сохраняется в `localStorage`. После загрузки страницы вызывается `applyRoleVisibility()` — функция скрывает все элементы с `data-show-roles`, если текущая роль не входит в список разрешённых.

### Матрица доступа

| Функция | ADMIN | DOCTOR | NURSE |
|---|---|---|---|
| Дашборд | ✓ | ✓ | ✓ |
| Пациенты — просмотр | ✓ | ✓ | ✓ |
| Пациенты — добавить | ✓ | ✓ | ✗ |
| Пациенты — удалить | ✓ | ✗ | ✗ |
| Пациенты — назначить врача | ✓ | ✓ | ✗ |
| Пациенты — услуги | ✓ | ✓ | ✓ |
| Врачи — просмотр | ✓ | ✓ | ✓ |
| Врачи — добавить / удалить | ✓ | ✗ | ✗ |
| Отделения — просмотр | ✓ | ✓ | ✗ |
| Отделения — добавить / удалить | ✓ | ✗ | ✗ |
| Палаты — просмотр | ✓ | ✓ | ✓ |
| Палаты — добавить новую | ✓ | ✗ | ✗ |
| Палаты — заселить пациента | ✓ | ✓ | ✓ |
| Платные услуги — просмотр | ✓ | ✓ | ✓ |
| Платные услуги — добавить | ✓ | ✗ | ✗ |
| Администрация (отчёты + выписка) | ✓ | ✗ | ✗ |

### Как реализовано

**HTML** — кнопки и пункты меню помечены атрибутом `data-show-roles`:

```html
<!-- Кнопка видна только ADMIN -->
<button data-show-roles="ROLE_ADMIN" onclick="...">+ Добавить врача</button>

<!-- Пункт меню для ADMIN и DOCTOR -->
<a data-show-roles="ROLE_ADMIN,ROLE_DOCTOR" onclick="navigate('departments')">Отделения</a>
```

**JavaScript** — функция применяет видимость по роли при загрузке:

```javascript
function applyRoleVisibility() {
  document.querySelectorAll('[data-show-roles]').forEach(el => {
    const allowed = el.dataset.showRoles.split(',');
    if (!allowed.includes(currentRole)) el.style.display = 'none';
  });
}
```

Кнопки в динамических таблицах рендерятся условно через `canDo()`:

```javascript
const PERMISSIONS = {
  'patient:delete':        ['ROLE_ADMIN'],
  'patient:assign-doctor': ['ROLE_ADMIN', 'ROLE_DOCTOR'],
  'doctor:manage':         ['ROLE_ADMIN'],
  // ...
};

function canDo(action) {
  const allowed = PERMISSIONS[action];
  return !allowed || allowed.includes(currentRole);
}

// В шаблоне строки таблицы пациентов:
${canDo('patient:assign-doctor') ? `<button onclick="openAssignDoctorModal(${p.id})">👨‍⚕️</button>` : ''}
${canDo('patient:delete')        ? `<button onclick="deletePatient(${p.id})">🗑</button>` : ''}
```

Попытка перейти в недоступный раздел через JS-консоль блокируется функцией `navigate()`:

```javascript
const SECTION_ACCESS = {
  departments: ['ROLE_ADMIN', 'ROLE_DOCTOR'],
  admin:       ['ROLE_ADMIN'],
};

function navigate(section) {
  const allowed = SECTION_ACCESS[section];
  if (allowed && !allowed.includes(currentRole)) {
    toast('Недостаточно прав для просмотра этого раздела', 'warning');
    return;
  }
  // ...
}
```

---

## 27. Мониторинг: Loki + Grafana

### Архитектура

```
Spring Boot App
      |
      | HTTP Push (loki4j Logback appender)
      v
  Loki :3100  ──── Grafana :3000
(хранилище)        (визуализация)
```

Приложение отправляет логи **напрямую** в Loki через `loki-logback-appender`. Никакого Promtail, агентов или файловых хвостов — это обычный Logback-аппендер, интегрированный в `logback-spring.xml`.

### Компоненты

| Компонент | Версия | Порт | Назначение |
|---|---|---|---|
| Loki | 2.9.0 | 3100 | Хранилище и индексация логов |
| Grafana | 10.2.3 | 3000 | LogQL-запросы, дашборды |
| loki-logback-appender | 1.5.2 | — | Java-зависимость в pom.xml |

### Конфигурация (logback-spring.xml)

```xml
<appender name="LOKI" class="com.github.loki4j.logback.Loki4jAppender">
    <http class="com.github.loki4j.logback.JavaHttpSender">
        <url>${lokiUrl}/loki/api/v1/push</url>
        <connectionTimeoutMs>5000</connectionTimeoutMs>
        <requestTimeoutMs>5000</requestTimeoutMs>
    </http>
    <format>
        <label>
            <!-- Loki-метки для фильтрации -->
            <pattern>app=pet-hospital,level=%level,logger=%logger{0}</pattern>
        </label>
        <message>
            <pattern>level=%level logger=%logger{36} thread=%thread | %msg%n%ex{full}</pattern>
        </message>
    </format>
    <verbose>false</verbose>
    <drainOnStop>false</drainOnStop>
</appender>

<!-- Асинхронная обёртка — не блокирует основной поток -->
<appender name="LOKI_ASYNC" class="ch.qos.logback.classic.AsyncAppender">
    <appender-ref ref="LOKI"/>
    <queueSize>1000</queueSize>
    <neverBlock>true</neverBlock>
</appender>
```

### URL Loki

URL читается из `application.yml` через Spring-проперти:

```yaml
logging:
  loki:
    url: ${LOKI_URL:http://localhost:3100}
```

Переменная `LOKI_URL` опциональна. При локальном запуске (приложение вне Docker) используется дефолт `localhost:3100` — Loki доступен через проброшенный порт. Если приложение запускается внутри Docker — передать `LOKI_URL=http://hospital-loki:3100`.

### Grafana — автопровизионирование

Datasource Loki добавляется автоматически при старте Grafana из файла:

```
monitoring/grafana/provisioning/datasources/loki.yml
```

Вручную ничего настраивать не нужно. После `docker-compose up -d grafana` — datasource уже доступен в Explore.

### Структура файлов мониторинга

```
monitoring/
├── loki-config.yml                          # конфиг Loki (storage, schema)
└── grafana/
    └── provisioning/
        └── datasources/
            └── loki.yml                     # автоподключение Loki в Grafana
```

### LogQL — полезные запросы

```logql
# Все логи приложения
{app="pet-hospital"}

# Только ошибки
{app="pet-hospital"} |= "ERROR"

# Логи конкретного сервиса (AOP логирует все сервисы)
{app="pet-hospital"} |= "PatientServiceImpl"

# Найти исключения
{app="pet-hospital"} |= "Exception"

# Время выполнения методов (AOP-аспект)
{app="pet-hospital"} |= "Completed"

# Kafka-события
{app="pet-hospital"} |= "patient-events"
```

---

## Итоговая схема взаимодействия компонентов

```
HTTP Request
    |
    v
JwtAuthenticationFilter          <- проверяет Bearer-токен
    |
    v
@RestController                  <- валидирует @Valid RequestBody
    |         DTO
    v
@Service + @Transactional        <- бизнес-логика, открывает транзакцию
    |                   |
    | JPA Entity         | EventPublisher (@Transactional MANDATORY)
    v                   |     |
@Repository             |     +-- outboxEventRepository.save()  <- в той же транзакции
    |                   |     +-- kafkaTemplate.send()          <- в той же транзакции
    v                   |
PostgreSQL              v
    ^              Apache Kafka
    |                   |
    +-- Flyway          v
       (migrations) KafkaListener (Consumer)
                        |
                        +-- idempotency check (outbox_event)
                        +-- ack.acknowledge()

    Redis <- @Cacheable / @CacheEvict в AdminServiceImpl
    AOP   <- оборачивает все методы Service, логирует время
```
