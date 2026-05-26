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
28. [Клиентский портал](#28-клиентский-портал)
29. [Чат-система](#29-чат-система)
30. [Медицинская документация](#30-медицинская-документация)
31. [Портал врача](#31-портал-врача)
32. [Kubernetes: запуск в Rancher Desktop](#32-kubernetes-запуск-в-rancher-desktop)
33. [OpenSearch — полнотекстовый поиск](#33-opensearch--полнотекстовый-поиск)
34. [REST-дизайн API — применённые принципы](#34-rest-дизайн-api--применённые-принципы)
35. [Платёжная интеграция: Alfa Bank](#35-платёжная-интеграция-alfa-bank)

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
| **OpenSearch** | **2.17.0** | **Полнотекстовый поиск по пациентам и врачам** |
| opensearch-java | 2.15.0 | Java-клиент для OpenSearch (Apache HTTP 5 transport) |

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
|   +-- ClientController.java           # /api/client/** (клиентский портал)
|   +-- ChatController.java             # /api/chat/** (чат-система)
|   +-- MedicalController.java          # /api/medical/** (мед. документация)
|
+-- service/                            # Интерфейсы сервисного слоя
|   +-- ClientService.java              # Клиентский портал
|   +-- ChatService.java                # Чат-система
|   +-- MedicalService.java             # Медицинская документация
|   +-- impl/                           # Реализации сервисов
|   |   +-- ClientServiceImpl.java
|   |   +-- ChatServiceImpl.java        # getOrCreate-комнаты, IDOR-защита
|   |   +-- MedicalServiceImpl.java     # документы, заметки, history
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
|   +-- Appointment.java                # Запись к врачу (клиентский портал)
|   +-- ClientServiceOrder.java         # Заказ услуги (клиентский портал)
|   +-- ChatRoom.java                   # Комната чата (SUPPORT / DOCTOR_CLIENT)
|   +-- ChatMessage.java                # Сообщение чата (short-polling)
|   +-- MedicalDocument.java            # Медицинский документ (soft-delete)
|   +-- PatientNote.java                # Заметка врача (visibleToClient flag)
|   +-- Gender.java, Specialty.java, PatientStatus.java, Role.java  # Enums
|   +-- ChatRoomType.java, DocumentType.java, NoteType.java          # Enums чата/мед
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

### Appointment — запись на приём (клиентский портал)

```java
@Entity @Table(name = "appointment")
public class Appointment {
    Long id;
    User clientUser;         // @ManyToOne — пользователь с ролью ROLE_CLIENT
    Doctor doctor;           // @ManyToOne — выбранный врач
    LocalDate preferredDate; // желаемая дата
    String preferredTime;    // желаемое время (например "14:00")
    String contactPhone;
    String notes;
    AppointmentStatus status; // PENDING → CONFIRMED / CANCELLED
    LocalDateTime createdAt;
}
```

### ClientServiceOrder — заказ платной услуги (клиентский портал)

```java
@Entity @Table(name = "client_service_order")
public class ClientServiceOrder {
    Long id;
    User clientUser;           // @ManyToOne — ROLE_CLIENT
    PaidService paidService;   // @ManyToOne — выбранная услуга
    String contactPhone;
    String notes;
    ClientServiceOrderStatus status; // PENDING → CONFIRMED / COMPLETED / CANCELLED
    LocalDateTime createdAt;
}
```

### ChatRoom — комната чата

```java
@Entity @Table(name = "chat_room")
public class ChatRoom {
    Long id;
    ChatRoomType type;    // SUPPORT (тех.поддержка) / DOCTOR_CLIENT (врач-клиент)
    User clientUser;      // @ManyToOne — клиент-инициатор
    User staffUser;       // @ManyToOne nullable — конкретный врач (null для SUPPORT)
    LocalDateTime createdAt;
}
```

Частичный уникальный индекс (`WHERE type='SUPPORT'`) гарантирует максимум одну SUPPORT-комнату на клиента — паттерн **get-or-create** без дублей.

### ChatMessage — сообщение чата

```java
@Entity @Table(name = "chat_message")
public class ChatMessage {
    Long id;
    ChatRoom room;       // @ManyToOne — принадлежность к комнате
    User sender;         // @ManyToOne — автор сообщения
    String content;      // @NotBlank
    LocalDateTime sentAt;
}
```

Сообщения читаются через **short-polling**: `GET /api/chat/rooms/{id}/messages?sinceId={lastId}` — возвращает только сообщения с `id > sinceId`. При первом открытии `sinceId=0` — возвращаются все сообщения. Клиент опрашивает каждые 3 секунды.

### MedicalDocument — медицинский документ

```java
@Entity @Table(name = "medical_document")
public class MedicalDocument {
    Long id;
    Patient patient;        // @ManyToOne
    Doctor issuedBy;        // @ManyToOne — врач, выдавший документ
    DocumentType type;      // DIAGNOSIS, PRESCRIPTION, SICK_LEAVE, REFERRAL, ANALYSIS_RESULT, OTHER
    String title;           // @NotBlank
    String content;         // полный текст
    LocalDate issuedAt;
    LocalDate validUntil;   // nullable — срок действия
    boolean active;         // soft-delete: false = документ архивирован
}
```

### PatientNote — заметка врача

```java
@Entity @Table(name = "patient_note")
public class PatientNote {
    Long id;
    Patient patient;         // @ManyToOne
    Doctor doctor;           // @ManyToOne — автор заметки
    NoteType type;           // OBSERVATION, COMPLAINT, TREATMENT_PLAN, FOLLOW_UP, OTHER
    String content;          // @NotBlank
    boolean visibleToClient; // @Builder.Default false — по умолчанию скрыта от пациента
    LocalDateTime createdAt;
}
```

### Перечисления (Enums)

```java
enum Gender                  { MALE, FEMALE }
enum PatientStatus            { TREATMENT, DISCHARGED, TRANSFERRED }
enum Role                    { ROLE_ADMIN, ROLE_DOCTOR, ROLE_NURSE, ROLE_CLIENT }
enum Specialty               { CARDIOLOGIST, SURGEON, THERAPIST, NEUROLOGIST,
                               PEDIATRICIAN, ORTHOPEDIST, ONCOLOGIST, UROLOGIST }
enum DischargeType            { NORMAL, FORCED, TRANSFER }
enum AppointmentStatus        { PENDING, CONFIRMED, CANCELLED }
enum ClientServiceOrderStatus { PENDING, CONFIRMED, COMPLETED, CANCELLED }
enum ChatRoomType             { SUPPORT, DOCTOR_CLIENT }
enum DocumentType             { DIAGNOSIS, PRESCRIPTION, SICK_LEAVE, REFERRAL,
                               ANALYSIS_RESULT, OTHER }
enum NoteType                 { OBSERVATION, COMPLAINT, TREATMENT_PLAN, FOLLOW_UP, OTHER }
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

#### V4__client_schema.sql

Создаёт таблицы клиентского портала:

```sql
CREATE TABLE appointment (
    id              BIGSERIAL    PRIMARY KEY,
    client_user_id  BIGINT       NOT NULL REFERENCES users(id),
    doctor_id       BIGINT       NOT NULL REFERENCES doctor(id),
    preferred_date  DATE         NOT NULL,
    preferred_time  VARCHAR(20),
    contact_phone   VARCHAR(25),
    notes           TEXT,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    created_at      TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE client_service_order (
    id              BIGSERIAL    PRIMARY KEY,
    client_user_id  BIGINT       NOT NULL REFERENCES users(id),
    paid_service_id BIGINT       NOT NULL REFERENCES paid_service(id),
    ...
);
```

#### V5__extended_seed_data.sql

Расширенные тестовые данные для реалистичной работы системы:

| Тип данных | Количество |
|---|---|
| Новые отделения | 8 (итого ~10) |
| Новые врачи | 22 (итого ~25) |
| Новые палаты | 32 (итого ~36) |
| Новые пациенты | 40 (итого ~45) |
| Новые платные услуги | 20 (итого ~22) |

#### V6__chat_medical_schema.sql

Создаёт таблицы чат-системы и медицинской документации:


```sql
CREATE TABLE chat_room (
    id             BIGSERIAL    PRIMARY KEY,
    type           VARCHAR(20)  NOT NULL,  -- SUPPORT / DOCTOR_CLIENT
    client_user_id BIGINT       NOT NULL REFERENCES users(id),
    staff_user_id  BIGINT                 REFERENCES users(id),  -- nullable
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Частичный уникальный индекс: один SUPPORT-чат на клиента
CREATE UNIQUE INDEX uq_support_room
    ON chat_room (client_user_id)
    WHERE type = 'SUPPORT';

CREATE TABLE chat_message (
    id      BIGSERIAL    PRIMARY KEY,
    room_id BIGINT       NOT NULL REFERENCES chat_room(id),
    sender_id BIGINT     NOT NULL REFERENCES users(id),
    content TEXT         NOT NULL,
    sent_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE medical_document (
    id           BIGSERIAL    PRIMARY KEY,
    patient_id   BIGINT       NOT NULL REFERENCES patient(id),
    issued_by_id BIGINT       NOT NULL REFERENCES doctor(id),
    type         VARCHAR(30)  NOT NULL,
    title        VARCHAR(255) NOT NULL,
    content      TEXT,
    issued_at    DATE         NOT NULL,
    valid_until  DATE,        -- nullable
    active       BOOLEAN      NOT NULL DEFAULT TRUE  -- soft-delete
);

CREATE TABLE patient_note (
    id               BIGSERIAL    PRIMARY KEY,
    patient_id       BIGINT       NOT NULL REFERENCES patient(id),
    doctor_id        BIGINT       NOT NULL REFERENCES doctor(id),
    type             VARCHAR(30)  NOT NULL,
    content          TEXT         NOT NULL,
    visible_to_client BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW()
);
```

#### V7__link_doctor_users.sql

Fallback-линковка `doctor.user_id` для врачей doctor2–doctor6. Выполняется с `EXISTS`-проверкой — идемпотентна. На свежей базе является no-op (таблица `users` пустая в момент Flyway-миграции); фактическая линковка происходит через `DataInitializer.linkDoctorUser()` после создания пользователей.

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
| POST | `/api/auth/register` | Все | Регистрация персонала. Роль: `ROLE_NURSE` по умолчанию |
| POST | `/api/auth/register-client` | Все | Регистрация пациента на портале. Роль: `ROLE_CLIENT` |

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
| GET | `/api/patients` | Список (page, size); фильтры: `?q=имя`, `?status=TREATMENT` |
| GET | `/api/patients/{id}` | По ID |
| PUT | `/api/patients/{id}` | Обновить |
| DELETE | `/api/patients/{id}` | Мягкое удаление |
| PUT | `/api/patients/{patientId}/doctor/{doctorId}` | Назначить врача |
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
| PUT | `/api/wards/{wardId}/patients/{patientId}` | Поместить пациента в палату |
| DELETE | `/api/wards/{wardId}/patients/{patientId}` | Выписать из палаты |

### Платные услуги

| Метод | URL | Описание |
|---|---|---|
| POST | `/api/paid-services` | Создать услугу |
| GET | `/api/paid-services` | Список (page, size) |
| GET | `/api/paid-services/{id}` | По ID |
| POST | `/api/patients/{patientId}/paid-services/{serviceId}` | Назначить услугу пациенту |
| PATCH | `/api/patients/{patientId}/paid-services/{linkId}` | Обновить статус оплаты (body: `{"paid": true}`) |

### Администрирование `/api/admin` (только `ROLE_ADMIN`)

| Метод | URL | Описание |
|---|---|---|
| GET | `/api/admin/reports/ward-occupancy` | Отчёт по заполненности палат (кэш Redis) |
| GET | `/api/admin/reports/paid-services-summary` | Финансовая сводка (кэш Redis) |
| POST | `/api/admin/patients/{patientId}/discharge` | Выписать (?type=NORMAL\|FORCED\|TRANSFER) |

### Клиентский портал `/api/client`

| Метод | URL | Доступ | Описание |
|---|---|---|---|
| GET | `/api/client/doctors` | Все (без токена) | Список активных врачей |
| GET | `/api/client/departments` | Все (без токена) | Список активных отделений |
| GET | `/api/client/services` | Все (без токена) | Список активных платных услуг |
| POST | `/api/client/appointments` | `ROLE_CLIENT` | Записаться на приём к врачу |
| GET | `/api/client/me/appointments` | `ROLE_CLIENT` | Мои записи на приём |
| POST | `/api/client/service-orders` | `ROLE_CLIENT` | Заказать платную услугу |
| GET | `/api/client/me/service-orders` | `ROLE_CLIENT` | Мои заказы услуг |

### Чат `/api/chat`

| Метод | URL | Доступ | Описание |
|---|---|---|---|
| POST | `/api/chat/support` | `ROLE_CLIENT` | Создать / получить SUPPORT-комнату |
| GET | `/api/chat/support` | `ROLE_ADMIN` | Все SUPPORT-комнаты |
| GET | `/api/chat/me/rooms` | `ROLE_CLIENT` | Мои чат-комнаты |
| GET | `/api/chat/doctor/rooms` | `ROLE_DOCTOR` | Чаты этого врача |
| POST | `/api/chat/rooms/{id}/messages` | Любой auth. | Отправить сообщение |
| GET | `/api/chat/rooms/{id}/messages?sinceId={n}` | Любой auth. | Сообщения; sinceId=0 = все, sinceId=N = polling новых |

### Медицинская документация `/api/medical`

| Метод | URL | Доступ | Описание |
|---|---|---|---|
| POST | `/api/medical/documents` | `ROLE_DOCTOR` | Создать документ |
| GET | `/api/medical/documents/patient/{id}` | `ROLE_DOCTOR`, `ROLE_ADMIN` | Документы пациента |
| GET | `/api/medical/me/documents` | `ROLE_CLIENT` | Мои документы |
| DELETE | `/api/medical/documents/{id}` | `ROLE_DOCTOR`, `ROLE_ADMIN` | Архивировать |
| POST | `/api/medical/notes` | `ROLE_DOCTOR` | Создать заметку |
| GET | `/api/medical/notes/patient/{id}` | `ROLE_DOCTOR`, `ROLE_ADMIN` | Заметки пациента |
| GET | `/api/medical/history/patient/{id}` | `ROLE_DOCTOR`, `ROLE_ADMIN` | История пациента |
| GET | `/api/medical/me/history` | `ROLE_CLIENT` | Моя история |

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

После запуска приложения документация доступна по адресу: `http://localhost:8090/swagger-ui.html`

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
FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q       # скачиваем зависимости отдельно (кэш слоя)
COPY src ./src
RUN mvn package -DskipTests -q

# --- Этап 2: Runtime ---
FROM eclipse-temurin:17-jre-alpine     # минимальный образ ~180MB
RUN addgroup -S hospital && adduser -S hospital -G hospital
USER hospital
COPY --from=build /app/target/pet-hospital-*.jar app.jar
EXPOSE 8090
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
```

**Многоэтапная сборка** позволяет получить маленький финальный образ. Maven и исходники в него не попадают — только скомпилированный JAR. Слой `dependency:go-offline` кэшируется Docker и не перекачивается при изменении только исходников.

`-XX:+UseContainerSupport` — JVM автоматически определяет лимиты CPU и памяти из cgroup контейнера.

### docker-compose.yml

Описывает **9 сервисов** — всю инфраструктуру и само приложение:

| Сервис | Образ | Порт | Назначение |
|---|---|---|---|
| **app** | (сборка из Dockerfile) | 8090 | Spring Boot приложение |
| postgres | postgres:15-alpine | 5432 | Основная БД |
| zookeeper | confluentinc/cp-zookeeper:7.6.0 | 2181 | Координация Kafka |
| kafka | confluentinc/cp-kafka:7.6.0 | 9092 | Брокер сообщений (хост) |
| redis | redis:7-alpine | 6379 | Кэш |
| kafdrop | obsidiandynamics/kafdrop | 9000 | Web UI для Kafka |
| **loki** | grafana/loki:2.9.0 | 3100 | Хранилище логов |
| **grafana** | grafana/grafana:10.2.3 | 3000 | Визуализация логов |
| **opensearch** | opensearchproject/opensearch:2.17.0 | 9200 | Полнотекстовый поиск |

### Kafka — двойные листенеры

Kafka настроена с двумя независимыми листенерами, чтобы работать одновременно для контейнеров и для хоста:

```
PLAINTEXT://kafka:29092      — для сервисов внутри Docker-сети (app, kafdrop)
PLAINTEXT_HOST://localhost:9092 — для подключения с хоста (Postman, тесты вне Docker)
```

Приложение `app` в docker-compose получает `SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:29092`.
При локальном запуске через Maven используется дефолт `localhost:9092` из `application.yml`.

### Переменные окружения сервиса app

Все `localhost`-адреса из `application.yml` переопределяются через env-переменные:

| Переменная | Значение в Docker | Дефолт (application.yml) |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://postgres:5432/hospital_db` | `localhost:5432` |
| `SPRING_DATA_REDIS_HOST` | `redis` | `localhost` |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `kafka:29092` | `localhost:9092` |
| `LOKI_URL` | `http://loki:3100` | `http://localhost:3100` |
| `OPENSEARCH_URL` | `http://opensearch:9200` | `http://localhost:9200` |

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
|   +-- AuthIntegrationTest.java          # 10 тестов: вход, регистрация, авторизация
|   +-- PatientIntegrationTest.java       # 10 тестов: CRUD пациентов, поиск, clientUserId, авто-чат
|   +-- ChatIntegrationTest.java          # 17 тестов: чат-система, RBAC, polling
|   +-- MedicalIntegrationTest.java       # 23 теста: документы, заметки, история
|   +-- PaidServiceIntegrationTest.java   # 7 тестов: CRUD услуг, назначение, оплата
|   +-- SearchIntegrationTest.java        # 3 теста: OpenSearch index+search+delete
|
+-- service/
|   +-- PatientServiceTest.java           # 16 юнит-тестов PatientService
|   +-- WardServiceTest.java              # 5 юнит-тестов WardService
|   +-- AdminServiceTest.java             # 9 юнит-тестов AdminService
|   +-- ChatServiceTest.java              # 24 юнит-теста ChatService
|   +-- MedicalServiceTest.java           # 21 юнит-тест MedicalService
|   +-- SearchServiceTest.java            # 6 юнит-тестов: graceful no-op без OpenSearch
|
+-- config/
    +-- JwtUtilTest.java                  # 5 тестов генерации и валидации JWT
```

**Итого: 247 тестов — все проходят.**

| Класс | Тип | Тестов | Что проверяет |
|---|---|---|---|
| `PatientServiceTest` | Unit | 16 | Создание, soft-delete, поиск, назначение врача, clientUserId, авто-чат |
| `WardServiceTest` | Unit | 5 | Поступление, выписка, заполненность палат |
| `AdminServiceTest` | Unit | 9 | Финансовые отчёты, Redis кэш, Strategy pattern |
| `ChatServiceTest` | Unit | 24 | getOrCreate (идемпотентность), IDOR, polling, двунаправленный чат |
| `MedicalServiceTest` | Unit | 21 | Документы, заметки, типы/labels, история пациента |
| `SearchServiceTest` | Unit | 6 | Graceful no-op когда OpenSearchClient=null |
| `JwtUtilTest` | Unit | 5 | Генерация/валидация/истечение JWT |
| `AuthIntegrationTest` | Integration | 10 | Login, register, RBAC 401/403 |
| `PatientIntegrationTest` | Integration | 10 | CRUD пациентов HTTP end-to-end, clientUserId, авто-чат-комната |
| `ChatIntegrationTest` | Integration | 17 | Комнаты, сообщения, polling, RBAC, двунаправленный чат |
| `MedicalIntegrationTest` | Integration | 23 | Документы и заметки HTTP end-to-end, RBAC |
| `PaidServiceIntegrationTest` | Integration | 7 | CRUD услуг, назначение пациенту, отметка оплаты |
| `SearchIntegrationTest` | Integration | 3 | Реальный OpenSearch через Testcontainers: index+search+delete |

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
| Docker Desktop | 4.x+ | Запуск всех сервисов |
| Java | 17+ | Только для локальной разработки без Docker |
| Maven | 3.9+ | Только для локальной разработки без Docker |

> **Windows**: для Testcontainers включите TCP в Docker Desktop:
> **Settings → General → Expose daemon on tcp://localhost:2375 without TLS**

---

### Быстрый старт — полный запуск через Docker (1 команда)

```bash
docker-compose up -d
```

Команда собирает образ приложения из `Dockerfile` и запускает все 8 сервисов.
После старта открыть: **http://localhost:8090** (войти: admin / admin123)

Дождаться готовности (все статусы `running` или `healthy`):
```bash
docker-compose ps
```

Просмотр логов приложения в реальном времени:
```bash
docker-compose logs -f app
```

---

### Пересборка образа приложения

При изменении кода нужно пересобрать образ:

```bash
docker-compose up -d --build app
```

---

### Альтернатива — локальная разработка (инфра в Docker, приложение в IDE)

```bash
# 1. Поднять только инфраструктуру (без приложения)
docker-compose up -d postgres redis zookeeper kafka kafdrop loki grafana

# 2. Запустить приложение локально
mvn spring-boot:run
```

При локальном запуске `application.yml` использует `localhost` для всех сервисов — это дефолтные значения. Инфраструктура доступна через проброшенные порты.

---

### Пошаговый запуск с объяснениями (Docker)

#### Шаг 1 — Запустить все сервисы

```bash
docker-compose up -d
```

Запускает 8 контейнеров. Проверить готовность:

```bash
docker-compose ps
```

Все сервисы должны быть `running` или `healthy`.

| Контейнер | Порт | Что там |
|---|---|---|
| **hospital-app** | http://localhost:8090 | Spring Boot приложение |
| hospital-postgres | 5432 | PostgreSQL — основная БД |
| hospital-redis | 6379 | Redis — кэш |
| hospital-zookeeper | 2181 | Zookeeper (для Kafka) |
| hospital-kafka | 9092 | Apache Kafka (внешний доступ с хоста) |
| hospital-kafdrop | http://localhost:9000 | UI просмотра Kafka-топиков |
| **hospital-loki** | 3100 | Хранилище логов |
| **hospital-grafana** | http://localhost:3000 | Дашборды логов |

> Сервис `app` зависит от `postgres`, `redis`, `kafka`, `loki` — Docker Compose дождётся их готовности (`service_healthy`) перед запуском приложения. Первый запуск занимает ~90 секунд (сборка JAR внутри контейнера).

При старте приложения происходит автоматически:
- Flyway применяет миграции V1 → V2 → V3 → V4 → V5 (расширенные тестовые данные)
- `DataInitializer` создаёт 5 пользователей: admin, doctor1, nurse1, client1, client2
- `loki4j` начинает отправлять логи в Loki на `http://loki:3100`

#### Шаг 2 — Открыть интерфейсы

| Интерфейс | URL | Учётные данные |
|---|---|---|
| **Администрация (HIS)** | http://localhost:8090/admin.html | admin / admin123 |
| **Медсестра (HIS)** | http://localhost:8090/nurse.html | nurse1 / nurse123 |
| **Портал врача** | http://localhost:8090/doctor.html | doctor1–doctor6 / doctor123 |
| **Клиентский портал** | http://localhost:8090/client.html | client1 / client123 |
| **Личный кабинет клиента** | http://localhost:8090/account.html | (через client.html → Кабинет) |
| Swagger UI | http://localhost:8090/swagger-ui.html | — |
| API Docs | http://localhost:8090/api-docs | — |
| Actuator Health | http://localhost:8090/actuator/health | — |
| Kafdrop (Kafka UI) | http://localhost:9000 | — |
| **Grafana** | http://localhost:3000 | admin / admin |

---

### Запуск только части сервисов

```bash
# Только инфраструктура без приложения (для разработки в IDE)
docker-compose up -d postgres redis zookeeper kafka kafdrop loki grafana

# Только БД + кэш (минимум без Kafka и мониторинга)
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

### Тестовые учётные данные — все роли

`DataInitializer` создаёт пользователей автоматически при первом старте. `login.html` перенаправляет каждую роль на нужную страницу автоматически.

**Персонал больницы:**

| Логин | Пароль | Роль | Страница |
|---|---|---|---|
| `admin` | `admin123` | ROLE_ADMIN | http://localhost:8090/admin.html |
| `nurse1` | `nurse123` | ROLE_NURSE | http://localhost:8090/nurse.html |
| `doctor1` | `doctor123` | ROLE_DOCTOR | http://localhost:8090/doctor.html |
| `doctor2` | `doctor123` | ROLE_DOCTOR | http://localhost:8090/doctor.html |
| `doctor3` | `doctor123` | ROLE_DOCTOR | http://localhost:8090/doctor.html |
| `doctor4` | `doctor123` | ROLE_DOCTOR | http://localhost:8090/doctor.html |
| `doctor5` | `doctor123` | ROLE_DOCTOR | http://localhost:8090/doctor.html |
| `doctor6` | `doctor123` | ROLE_DOCTOR | http://localhost:8090/doctor.html |

**Клиенты портала:**

| Логин | Пароль | Роль | Страница |
|---|---|---|---|
| `client1` | `client123` | ROLE_CLIENT | http://localhost:8090/client.html |
| `client2` | `client123` | ROLE_CLIENT | http://localhost:8090/client.html |

**Соответствие врачей и записей в БД:**

| Логин | ФИО врача |
|---|---|
| `doctor1` | Иванов Сергей Петрович |
| `doctor2` | Захаров Андрей Михайлович |
| `doctor3` | Беляев Константин Семёнович |
| `doctor4` | Романова Анна Викторовна |
| `doctor5` | Тарасова Людмила Витальевна |
| `doctor6` | Федосеев Алексей Владимирович |

Для регистрации нового сотрудника — http://localhost:8090/register.html (роль `ROLE_NURSE`).
Для регистрации нового клиента — http://localhost:8090/client.html → «Зарегистрироваться» (роль `ROLE_CLIENT`).

**Получить JWT-токен через curl:**
```bash
curl -X POST http://localhost:8090/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

**Использовать токен:**
```bash
curl http://localhost:8090/api/patients \
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

### Переменные окружения (Docker)

При запуске через `docker-compose up -d` сервис `app` получает следующие переменные, которые Spring Boot автоматически подставляет вместо значений `application.yml`:

| Переменная | Значение | Переопределяет |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://postgres:5432/hospital_db` | `localhost:5432` |
| `SPRING_DATASOURCE_USERNAME` | `postgres` | — |
| `SPRING_DATASOURCE_PASSWORD` | `1234` | — |
| `SPRING_DATA_REDIS_HOST` | `redis` | `localhost` |
| `SPRING_DATA_REDIS_PORT` | `6379` | — |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `kafka:29092` | `localhost:9092` |
| `LOKI_URL` | `http://loki:3100` | `http://localhost:3100` |

Spring Boot поддерживает переопределение любого свойства через env-переменные в формате `SPRING_PROPERTY_NAME` (точки заменяются на `_`, всё uppercase).

### application.yml (локальная разработка)

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

Фронтенд — страницы на чистом JS/HTML (без фреймворков). Каждая роль получает собственный интерфейс:

| Файл | Роль | Описание |
|---|---|---|
| `admin.html` + `app.js` | `ROLE_ADMIN` | HIS: пациенты, врачи, отделения, палаты, чаты, отчёты, выписка |
| `nurse.html` | `ROLE_NURSE` | HIS (урезанный): пациенты (без удаления), палаты, услуги — без отчётов и управления врачами |
| `doctor.html` | `ROLE_DOCTOR` | Портал врача: мои пациенты, приёмы (+ зачисление в HIS), история болезни, заметки, документы, чаты |
| `client.html` | Все / `ROLE_CLIENT` | Лендинг клиентского портала: врачи, услуги, отделения |
| `account.html` | `ROLE_CLIENT` | Личный кабинет: мои записи, заказы, документы, история, чат поддержки, чат с врачом |
| `index.html` + `app.js` | (legacy) | Обратная совместимость — перенаправляет на admin.html/nurse.html |

### Матрица доступа (index.html / app.js)

| Функция | ADMIN | NURSE |
|---|---|---|
| Дашборд | ✓ | ✓ |
| Пациенты — просмотр | ✓ | ✓ |
| Пациенты — добавить | ✓ | ✓ |
| Пациенты — удалить | ✓ | ✗ |
| Пациенты — назначить врача | ✓ | ✗ |
| Пациенты — услуги | ✓ | ✓ |
| Врачи — добавить / удалить | ✓ | ✗ |
| Отделения | ✓ | ✗ |
| Палаты — добавить новую | ✓ | ✗ |
| Администрация (отчёты + выписка) | ✓ | ✗ |

### Портал врача (doctor.html)

Отдельная страница для `ROLE_DOCTOR`. При загрузке вызывает `GET /api/doctors/me` (по JWT) для получения профиля. Редирект на `/login.html` при отсутствии токена.

**Разделы:**
- **Dashboard** — статистика, последние пациенты и чаты
- **Мои пациенты** — карточки с поиском и фильтром статуса; при клике открывается боковая панель (480px) с 4 вкладками:
  - *История* — все заметки и медицинские документы пациента
  - *Добавить заметку* — тип (DIAGNOSIS / OBSERVATION / NOTE), флаг видимости клиентом
  - *Добавить документ* — тип (5 видов), срок действия
  - *Чат* — открывает/создаёт комнату `DOCTOR_CLIENT` с клиентом пациента
- **Чаты** — двухколоночный интерфейс, short-polling 3с

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

---

## 28. Клиентский портал

### Назначение

Клиентский портал разбит на два файла:

| Файл | URL | Назначение |
|---|---|---|
| `client.html` | `/client.html` | Публичный лендинг: врачи, услуги, отделения. Кнопка «Кабинет» ведёт на `account.html` |
| `account.html` | `/account.html` | Личный кабинет: полный функционал зарегистрированного клиента |

Учётные данные: client1 / client123, client2 / client123. Полный список: [Тестовые учётные данные](#тестовые-учётные-данные--все-роли) в разделе 23.

Зарегистрировать нового клиента: кнопка «Зарегистрироваться» на портале, или `POST /api/auth/register-client`.

### client.html — лендинг

| Блок | Описание | Авторизация |
|---|---|---|
| Hero + CTA | Заголовок, кнопки «Записаться» и «Войти» | Нет |
| Статистика | Счётчики врачей, отделений, услуг | Нет |
| Врачи | Карточки с аватаром, специализацией, кабинетом; фильтр по специализации | Нет |
| Услуги | Карточки с ценами, кнопки «Заказать» | Нет (действие требует входа) |
| Отделения | Карточки с описанием и расположением | Нет |
| Кнопка «Кабинет» (navbar) | Переход на `/account.html` | ROLE_CLIENT |
| Модальное окно входа/регистрации | `/api/auth/login`, `/api/auth/register-client` | Нет |
| Модальное окно записи к врачу | Выбор врача, даты, времени, телефона | ROLE_CLIENT |

### account.html — личный кабинет

Auth guard: при отсутствии `clientToken` в localStorage — редирект на `/client.html`.

| Раздел (левый сайдбар) | API | Описание |
|---|---|---|
| Обзор | — | Статистика + последние записи/заказы |
| Записи к врачу | `GET /api/client/me/appointments` | Список с фильтром статуса |
| Заказы услуг | `GET /api/client/me/service-orders` | Список с фильтром статуса |
| Мои документы | `GET /api/medical/me/documents` | Медицинские документы, выданные врачом |
| История болезни | `GET /api/medical/me/history` | Заметки врача (только `visibleToClient=true`) |
| Чат поддержки | `POST /api/chat/support` + polling | Переписка с администрацией |
| Чат с врачом | `GET /api/chat/me/rooms` + polling | Список комнат + переписка |

### Backend: новые компоненты

| Компонент | Путь | Назначение |
|---|---|---|
| `ClientController` | `controller/ClientController.java` | REST `/api/client/**` |
| `ClientService` | `service/ClientService.java` | Интерфейс сервисного слоя |
| `ClientServiceImpl` | `service/impl/ClientServiceImpl.java` | Реализация |
| `Appointment` | `entity/Appointment.java` | JPA-сущность записи на приём |
| `ClientServiceOrder` | `entity/ClientServiceOrder.java` | JPA-сущность заказа услуги |
| `AppointmentRepository` | `repository/AppointmentRepository.java` | JPA-репозиторий |
| `ClientServiceOrderRepository` | `repository/ClientServiceOrderRepository.java` | JPA-репозиторий |
| `AppointmentRequest/Response` | `dto/request\|response/` | DTO записи |
| `ServiceOrderRequest/Response` | `dto/request\|response/` | DTO заказа |
| `PublicDoctorResponse` | `dto/response/PublicDoctorResponse.java` | Публичная инфо о враче |

### Безопасность

Публичные `GET`-эндпоинты (`/api/client/doctors`, `/api/client/departments`, `/api/client/services`) настроены в `SecurityConfig` как `permitAll()` — JWT не требуется.

Для операций записи (`POST /api/client/appointments`, `POST /api/client/service-orders`) и просмотра своих записей (`GET /api/client/*/my`) требуется токен с ролью `ROLE_CLIENT`.

Пользователи с ролями `ROLE_ADMIN`, `ROLE_DOCTOR`, `ROLE_NURSE` не могут войти на клиентский портал (фронтенд отвергает их при входе с подсказкой открыть `index.html`).

### Модель данных клиентского портала

```
users (ROLE_CLIENT)
    |
    +-- (1:N) appointment ----------- doctor
    |             ↓ status
    |        PENDING / CONFIRMED / CANCELLED
    |
    +-- (1:N) client_service_order -- paid_service
                  ↓ status
             PENDING / CONFIRMED / COMPLETED / CANCELLED
```

---

## 29. Чат-система

### Назначение

Встроенная система обмена сообщениями между клиентами и персоналом больницы. Реализована на **short-polling** (без WebSocket) для простоты и совместимости с обычным REST API.

### Типы комнат

| Тип | Инициатор | Получатель | Описание |
|---|---|---|---|
| `SUPPORT` | `ROLE_CLIENT` | Любой `ROLE_ADMIN` | Техническая поддержка клиента. Одна комната на клиента |
| `DOCTOR_CLIENT` | `ROLE_DOCTOR` / `ROLE_CLIENT` | Конкретный врач | Переписка с врачом по лечению |

### REST API чата

| Метод | URL | Роль | Описание |
|---|---|---|---|
| `POST` | `/api/chat/support` | `ROLE_CLIENT` | Создать или получить SUPPORT-комнату |
| `GET` | `/api/chat/support` | `ROLE_ADMIN` | Список всех SUPPORT-комнат |
| `GET` | `/api/chat/me/rooms` | `ROLE_CLIENT` | Все комнаты текущего клиента |
| `GET` | `/api/chat/doctor/rooms` | `ROLE_DOCTOR` | Все комнаты, где врач — участник |
| `POST` | `/api/chat/rooms/{roomId}/messages` | Любой auth. | Отправить сообщение |
| `GET` | `/api/chat/rooms/{roomId}/messages?sinceId={n}` | Любой auth. | Сообщения; sinceId=0 = все, sinceId=N = только новее N |

### Short-polling

Клиент периодически запрашивает новые сообщения, передавая `sinceId` — id последнего полученного сообщения:

```
Client                               Server
  |                                     |
  |-- GET /poll?sinceId=0 ------------> |
  |<-- [] (пусто) --------------------- |   (первый запрос, нет сообщений)
  |                                     |
  |-- GET /poll?sinceId=0 ------------> |
  |<-- [{id:1, content:"Привет"}] ----- |   (новое сообщение появилось)
  |                                     |
  |-- GET /poll?sinceId=1 ------------> |
  |<-- [] ----------------------------- |   (ждём следующее)
```

Клиент обновляет `sinceId` при каждом непустом ответе. Интервал опроса — 3 секунды.

### IDOR-защита

Доступ к комнате ограничен по роли: каждый пользователь видит только свои комнаты. Реализовано в `ChatServiceImpl.getAccessibleRoom()`:

```java
private ChatRoom getAccessibleRoom(Long roomId, User user) {
    ChatRoom room = chatRoomRepository.findById(roomId)
        .orElseThrow(() -> new ResourceNotFoundException("Комната не найдена"));

    boolean hasAccess = switch (user.getRole()) {
        case ROLE_CLIENT -> room.getClientUser().getId().equals(user.getId());
        case ROLE_DOCTOR -> room.getStaffUser() != null &&
                            room.getStaffUser().getId().equals(user.getId());
        case ROLE_ADMIN  -> true;
        default          -> false;
    };

    if (!hasAccess) throw new AccessDeniedException("Нет доступа к комнате");
    return room;
}
```

### Идемпотентность getOrCreate

`POST /api/chat/support` работает как **get-or-create**: повторный вызов возвращает ту же комнату, не создаёт дубль. Реализовано через `orElseGet()` — важное отличие от `orElse()`:

```java
// ПРАВИЛЬНО: save() вызывается ТОЛЬКО если комната не найдена
return chatRoomRepository.findSupportRoomByClientUser(clientUser)
    .orElseGet(() -> chatRoomRepository.save(
        ChatRoom.builder()
            .type(ChatRoomType.SUPPORT)
            .clientUser(clientUser)
            .build()
    ));

// НЕПРАВИЛЬНО: orElse() вычисляет аргумент безусловно → save() всегда вызывается
// .orElse(chatRoomRepository.save(...));  ← НЕ ИСПОЛЬЗОВАТЬ
```

На уровне БД защита дублей — частичный уникальный индекс `uq_support_room` (V6-миграция).

### Модель данных чата

```
users (ROLE_CLIENT) ──── (1:N) ──── chat_room ──── (1:N) ──── chat_message
                                        │
users (ROLE_ADMIN/DOCTOR) ──────────── │ (staff_user_id, nullable)
```

---

## 30. Медицинская документация

### Назначение

Модуль для ведения медицинской документации пациентов: официальные документы (диагнозы, рецепты, больничные) и заметки врача (наблюдения, жалобы, план лечения). Поддерживает soft-delete для документов и опциональную видимость заметок для клиентов.

### Типы документов (DocumentType)

| Тип | Описание |
|---|---|
| `DIAGNOSIS` | Диагноз |
| `PRESCRIPTION` | Рецепт |
| `SICK_LEAVE` | Больничный лист |
| `REFERRAL` | Направление |
| `ANALYSIS_RESULT` | Результаты анализов |
| `OTHER` | Прочее |

### Типы заметок (NoteType)

| Тип | Описание |
|---|---|
| `OBSERVATION` | Наблюдение |
| `COMPLAINT` | Жалоба |
| `TREATMENT_PLAN` | План лечения |
| `FOLLOW_UP` | Контрольный осмотр |
| `OTHER` | Прочее |

### REST API медицинской документации

| Метод | URL | Роль | Описание |
|---|---|---|---|
| `POST` | `/api/medical/documents` | `ROLE_DOCTOR` | Создать документ |
| `GET` | `/api/medical/documents/patient/{id}` | `ROLE_DOCTOR` / `ROLE_ADMIN` | Документы пациента |
| `GET` | `/api/medical/me/documents` | `ROLE_CLIENT` | Мои документы (только `visibleToClient`) |
| `DELETE` | `/api/medical/documents/{id}` | `ROLE_DOCTOR` / `ROLE_ADMIN` | Архивировать (soft-delete) |
| `POST` | `/api/medical/notes` | `ROLE_DOCTOR` | Создать заметку |
| `GET` | `/api/medical/notes/patient/{id}` | `ROLE_DOCTOR` / `ROLE_ADMIN` | Заметки пациента |
| `GET` | `/api/medical/history/patient/{id}` | `ROLE_DOCTOR` / `ROLE_ADMIN` | Агрегированная история |
| `GET` | `/api/medical/me/history` | `ROLE_CLIENT` | Моя история (только видимые) |

### Создание документа — пример запроса

```json
POST /api/medical/documents
{
  "patientId": 1,
  "type": "PRESCRIPTION",
  "title": "Рецепт на амоксициллин",
  "content": "Амоксициллин 500мг 3 раза в день, 7 дней",
  "issuedAt": "2026-05-19",
  "validUntil": "2026-06-19"
}
```

Врач определяется автоматически из JWT-токена (по полю `username` → `doctorRepository.findByUser`).

### Флаг visibleToClient

По умолчанию заметки скрыты от пациента — **безопасное умолчание** (`@Builder.Default private boolean visibleToClient = false`). Врач явно устанавливает `"visibleToClient": true`, чтобы клиент увидел заметку в своём личном кабинете:

```json
POST /api/medical/notes
{
  "patientId": 1,
  "type": "OBSERVATION",
  "content": "Пациент идёт на поправку",
  "visibleToClient": true
}
```

### Агрегированная история пациента

`GET /api/medical/history/patient/{id}` возвращает объединённую хронологию из документов и заметок:

```json
{
  "patientId": 1,
  "patientName": "Иванов Иван Иванович",
  "documents": [...],
  "notes": [...]
}
```

Для `GET /api/medical/me/history` (ROLE_CLIENT) документы фильтруются по `active=true`, заметки — по `visibleToClient=true`.

### Soft-delete документов

Документ не удаляется физически. `DELETE /api/medical/documents/{id}` устанавливает `active = false`. Запросы `GET` автоматически фильтруют по `active = true`. Архивированные документы остаются в БД для аудиторской цепочки.

### Безопасность

- `ROLE_DOCTOR` — создаёт документы/заметки, читает документацию своих пациентов
- `ROLE_ADMIN` — полный доступ на чтение
- `ROLE_CLIENT` — только свои документы и видимые заметки (`active=true`, `visibleToClient=true`)
- `ROLE_NURSE` — нет доступа к `/api/medical/**`

### Модель данных

```
patient ──── (1:N) ──── medical_document ←── doctor (issuedBy)
    │                        ↓ active=false (soft-delete)
    │
    └─── (1:N) ──── patient_note ←── doctor
                        ↓ visibleToClient
                   false (default) / true
```

---

## 31. Портал врача

### Назначение

Отдельный фронтенд-интерфейс `doctor.html` для пользователей с ролью `ROLE_DOCTOR`. Предоставляет доступ ко всему функционалу, доступному врачу через REST API.

**URL:** `http://localhost:8090/doctor.html`

Учётные данные врачей — doctor1–doctor6 / doctor123. Полный список с ФИО: [Тестовые учётные данные](#тестовые-учётные-данные--все-роли) в разделе 23.

Логин через `/login.html` — автоматически перенаправляет на `/doctor.html` для роли `ROLE_DOCTOR`.

### Инициализация сессии

```
GET /api/doctors/me  →  DoctorResponse
```

Вызывается при загрузке страницы. Заполняет профиль в сайдбаре (имя, специализация, отделение). При отсутствии токена или ответе 401/403 — редирект на `/login.html`.

### Разделы

| Раздел | API | Описание |
|---|---|---|
| Dashboard | `GET /api/patients`, `GET /api/chat/doctor/rooms` | Статистика + последние пациенты и чаты |
| Мои пациенты | `GET /api/patients?doctorId={id}` | Карточки пациентов с поиском и фильтром статуса |
| Приёмы | `GET /api/client/me/appointments` (по doctorId) | Записи клиентов к этому врачу. Кнопка «+ В пациенты» — зачислить клиента как HIS-пациента |
| Чаты | `GET /api/chat/doctor/rooms` + polling | Все чаты врача с клиентами |

### Панель деталей пациента

Открывается по клику на карточку пациента. Выезжает справа (480px). Содержит 4 вкладки:

| Вкладка | API | Что делает |
|---|---|---|
| История | `GET /api/medical/history/patient/{id}` | Все заметки и документы пациента |
| Добавить заметку | `POST /api/medical/notes` | Тип (DIAGNOSIS/OBSERVATION/NOTE), контент, флаг видимости клиентом |
| Добавить документ | `POST /api/medical/documents` | Тип (5 видов), заголовок, контент, срок действия |
| Чат | `GET /api/patients/{id}` → `clientUserId` → `POST /api/chat/doctor/{clientUserId}` | Открывает/создаёт комнату `DOCTOR_CLIENT` с этим клиентом |

Если пациент не зарегистрирован на клиентском портале (`clientUserId = null`) — вкладка «Чат» показывает соответствующее сообщение.

### Backend: новые компоненты

| Компонент | Что добавлено |
|---|---|
| `DoctorService.getMe(Long userId)` | Метод получения профиля врача по user_id |
| `DoctorServiceImpl.getMe()` | Реализация через `findByLinkedUserIdAndActiveTrue` |
| `DoctorController.GET /api/doctors/me` | Эндпоинт (требует `ROLE_DOCTOR`) |
| `DataInitializer` | Создание doctor2–doctor6, метод `linkDoctorUser()` |
| `V7__link_doctor_users.sql` | Fallback-миграция линковки врачей |

### Линковка врачей с учётными записями

Каждый врач в таблице `doctor` связан с пользователем через `user_id` (FK → `users.id`). Связь устанавливается в `DataInitializer.linkDoctorUser()` после создания пользователей:

```java
// Идемпотентно: AND user_id IS NULL не перезапишет уже связанных
jdbc.update("UPDATE doctor SET user_id = (SELECT id FROM users WHERE username = ?) " +
            "WHERE full_name = ? AND user_id IS NULL", username, fullName);
```

V7-миграция дублирует эту логику как fallback, но является no-op при первом запуске (таблица `users` пуста в момент Flyway-миграций).

---

## 32. Kubernetes: запуск в Rancher Desktop

Kubernetes-деплой позволяет запустить весь стек (9 сервисов) в локальном k3s-кластере.
Манифесты находятся в `rancher/k8s/` — каждый файл содержит подробные построчные комментарии.

### Предварительные требования

1. Установить [Rancher Desktop](https://rancherdesktop.io)
2. При установке выбрать Container Runtime: **dockerd (Moby)**
3. Дождаться полной инициализации (иконка в трее перестаёт крутиться)

### Структура K8s манифестов

```
rancher/k8s/
├── 00-namespace.yaml   # namespace: pet-hospital
├── 01-secrets.yaml     # postgres password (Secret)
├── 02-postgres.yaml    # PVC 1Gi + Deployment + ClusterIP Service
├── 03-redis.yaml       # Deployment + ClusterIP (без PVC: кэш не персистентен)
├── 04-zookeeper.yaml   # Deployment + ClusterIP
├── 05-kafka.yaml       # initContainer(wait-zookeeper) + ClusterIP :29092
├── 06-kafdrop.yaml     # initContainer(wait-kafka) + NodePort :30009
├── 07-loki.yaml        # ConfigMap + PVC 2Gi + runAsUser:0 + ClusterIP
├── 08-prometheus.yaml  # ConfigMap + PVC 1Gi + NodePort :30900
├── 09-grafana.yaml     # 3×ConfigMap + PVC 256Mi + NodePort :30300
├── 10-app.yaml         # 4×initContainer + imagePullPolicy:Never + NodePort :30090
└── 11-dashboard.yaml   # K8s Dashboard (namespace kubernetes-dashboard) + NodePort :30443
```

### Шаг 1: Сборка и загрузка образа

> **Почему нельзя просто `docker build`?**
> Rancher Desktop запускает k3s в отдельной Linux VM через WSL2. `docker build` кладёт
> образ в хранилище Docker Desktop, а k3s имеет своё отдельное хранилище. Образ нужно
> загрузить именно в VM через `rdctl shell` (`--provenance=false` обязателен — без него
> BuildKit создаёт manifest list, который k3s не находит с `imagePullPolicy: Never`).

```powershell
# Один скрипт вместо трёх команд
.\rancher\build-and-load.ps1
```

Скрипт делает три шага: `docker build --provenance=false` → `docker save` → `rdctl shell -- docker load`.

### Шаг 2: Деплой

```powershell
kubectl apply -f rancher/k8s/
```

### Шаг 3: Проверка запуска

```powershell
kubectl get pods -n pet-hospital -w
```

Нормальная последовательность статусов `hospital-app`:
```
Init:0/4  →  Init:1/4  →  Init:2/4  →  Init:3/4  →  Running (0/1)  →  Running (1/1)
```
Полный запуск занимает **3–5 минут**.

### Порты после запуска

| Сервис | URL | Логин |
|--------|-----|-------|
| Приложение | http://localhost:30090/admin.html | admin / admin123 |
| Kafdrop | http://localhost:30009 | — |
| Prometheus | http://localhost:30900 | — |
| Grafana | http://localhost:30300 | admin / admin |
| K8s Dashboard | https://localhost:30443 | Bearer token |

### Токен для Kubernetes Dashboard

```powershell
$b64 = kubectl -n kubernetes-dashboard get secret admin-user-token -o jsonpath='{.data.token}'
[System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($b64))
```

Вставить полученную строку (`eyJ...`) в форму входа Dashboard.
Открыть: https://localhost:30443 → игнорировать предупреждение о сертификате → Token.

### Ключевые команды отладки

```powershell
# Логи приложения
kubectl logs -n pet-hospital deployment/hospital-app -f

# Полная информация о поде (Events, probe failures)
kubectl describe pod -n pet-hospital <POD_NAME>

# Войти внутрь контейнера
kubectl exec -it -n pet-hospital deployment/hospital-app -- /bin/sh

# Port-forward к PostgreSQL для DBeaver/pgAdmin
kubectl port-forward -n pet-hospital service/postgres 5432:5432
```

### Обновление приложения

```powershell
# Сборка + загрузка + рестарт одной командой
.\rancher\build-and-load.ps1 -Restart
```

### Полный сброс

```powershell
kubectl delete namespace pet-hospital   # удаляет все ресурсы + данные PVC
kubectl apply -f rancher/k8s/           # поднять заново
```

### Критические решения K8s в этом проекте

**`imagePullPolicy: Never`** (10-app.yaml) — образ `pet-hospital:1.0.0` должен быть
загружен в VM до деплоя. K8s не ищет его в реестре.

**`securityContext.runAsUser: 0`** (07-loki.yaml) — Loki 2.x запускается от UID 10001,
но PVC создан с правами root → Permission denied. Запуск от root решает проблему.

**4 initContainers** (10-app.yaml) — Spring Boot с Flyway не запустится без PostgreSQL,
Redis, Kafka и Loki. initContainer блокирует старт основного контейнера пока зависимость недоступна.

> **Почему нужен `wait-for-kafka`?** `KafkaTemplate` (producer) подключается лениво — при первой отправке, т.е. initContainer для него не обязателен. Но `@KafkaListener` (consumer) подключается **сразу при старте** Spring Boot — без Kafka приложение падает в CrashLoopBackOff. В этом проекте есть три `@KafkaListener` (`PatientEventConsumer`, `AdmissionEventConsumer`, `PaidServiceEventConsumer`) → initContainer обязателен.

**`livenessProbe.initialDelaySeconds: 90`** — больше чем у readinessProbe (30).
Если liveness сработает до того как Spring Boot успел запуститься, K8s убьёт Pod → CrashLoopBackOff.

**3 ConfigMap для Grafana** — K8s нельзя смонтировать два ConfigMap в одну директорию.
`grafana-dashboard-config` → `/dashboards/` (dashboards.yml), `grafana-dashboards` → `/dashboards/json/` (JSON файлы).
В dashboards.yml прописан `path: /dashboards/json`.

**Kafka dual listeners** (05-kafka.yaml):
- `PLAINTEXT://kafka:29092` — для подов K8s (K8s DNS-имя)
- `PLAINTEXT_HOST://localhost:9092` — для port-forward с хоста

---

## 33. OpenSearch — полнотекстовый поиск

### Зачем OpenSearch в этом проекте

OpenSearch добавляет **full-text поиск** поверх основной PostgreSQL-базы. Реляционные `LIKE '%query%'` медленно работают на больших объёмах и не умеют нечёткое сопоставление, ранжирование по релевантности и поиск по нескольким полям одновременно.

OpenSearch решает эти задачи:
- Поиск пациента по части имени, диагнозу, палате, отделению
- Поиск врача по имени, специализации, отделению
- Ранжирование результатов по релевантности (поле `fullName` имеет boost x3)

### Архитектура интеграции

```
POST /api/patients → PatientServiceImpl.create()
                          ↓
                    patientRepository.save()   ← PostgreSQL (источник истины)
                          ↓
                    searchService.indexPatient()  ← OpenSearch (поисковый индекс)

GET /api/search/patients?q=Иванов → SearchServiceImpl.searchPatients()
                                          ↓
                                    OpenSearch multi-match query
                                          ↓
                                    List<PatientDocument>
```

PostgreSQL остаётся **единственным источником истины**. OpenSearch — вторичный индекс для поиска.

### Новые файлы

```
src/main/java/com/hospital/
+-- config/
|   +-- OpenSearchConfig.java       # бин OpenSearchClient (@ConditionalOnProperty)
|
+-- search/
|   +-- PatientDocument.java        # документ OpenSearch для пациента
|   +-- DoctorDocument.java         # документ OpenSearch для врача
|   +-- SearchService.java          # интерфейс: index, delete, search
|   +-- SearchServiceImpl.java      # реализация (@PostConstruct создаёт индексы)
|
+-- controller/
    +-- SearchController.java       # GET /api/search/patients, /api/search/doctors
```

### REST API поиска

| Метод | URL | Доступ | Описание |
|---|---|---|---|
| GET | `/api/search/patients?q={query}` | ADMIN, DOCTOR, NURSE | Поиск пациентов |
| GET | `/api/search/doctors?q={query}` | ADMIN, DOCTOR, NURSE | Поиск врачей |

**Пример:**
```bash
# Поиск пациентов
curl -H "Authorization: Bearer <token>" \
     "http://localhost:8090/api/search/patients?q=Иванов"

# Поиск врачей
curl -H "Authorization: Bearer <token>" \
     "http://localhost:8090/api/search/doctors?q=кардио"
```

**Ответ:**
```json
[
  {
    "id": "42",
    "fullName": "Иванов Сергей Петрович",
    "ward": "101",
    "department": "Кардиология",
    "active": true
  }
]
```

### Конфигурация

```yaml
# application.yml
opensearch:
  enabled: true
  url: ${OPENSEARCH_URL:http://localhost:9200}

# application-test.yml
opensearch:
  enabled: false   # OpenSearch отключён в тестах — graceful no-op
```

`@ConditionalOnProperty(name = "opensearch.enabled", havingValue = "true", matchIfMissing = true)` на `OpenSearchConfig` — бин `OpenSearchClient` не создаётся при `enabled=false`. `SearchServiceImpl` инжектирует `@Autowired(required = false) OpenSearchClient` — если null, все операции — no-op. Это позволяет всем существующим тестам работать без изменений.

### Индексирование

Индексирование происходит автоматически при каждом создании и обновлении сущностей:

| Метод | Когда индексируется |
|---|---|
| `PatientServiceImpl.create()` | При создании нового пациента |
| `PatientServiceImpl.update()` | При обновлении пациента |
| `PatientServiceImpl.softDelete()` | Удаление из индекса |
| `DoctorServiceImpl.create()` | При создании врача |
| `DoctorServiceImpl.update()` | При обновлении врача |
| `DoctorServiceImpl.softDelete()` | Удаление из индекса |

> **Важно**: существующие данные (загруженные Flyway-миграциями) не переиндексируются автоматически. Для начальной индексации существующих данных можно добавить `@PostConstruct` в `DataInitializer` или реализовать эндпоинт `/api/admin/reindex`.

### Индексы OpenSearch

| Индекс | Поля | Boost |
|---|---|---|
| `patients` | id, fullName, ward, department, active | fullName x3 |
| `doctors` | id, fullName, specialization, department, active | fullName x3 |

Оба индекса создаются при старте приложения через `@PostConstruct ensureIndexes()`.

### Docker

```yaml
# docker-compose.yml
opensearch:
  image: opensearchproject/opensearch:2.17.0
  environment:
    - discovery.type=single-node
    - DISABLE_SECURITY_PLUGIN=true   # без SSL — для dev
    - OPENSEARCH_JAVA_OPTS=-Xms512m -Xmx512m
  ports:
    - "9200:9200"
  healthcheck:
    test: ["CMD-SHELL", "curl -s http://localhost:9200/_cluster/health | grep -qE '\"status\":\"(green|yellow)\"'"]
```

`DISABLE_SECURITY_PLUGIN=true` — отключает TLS и Basic Auth для простоты dev-окружения. В production использовать с включённой security и выданными сертификатами.

### Kubernetes (Rancher Desktop)

Манифест: `rancher/k8s/12-opensearch.yaml`

Включает:
- `PersistentVolumeClaim` 2Gi для данных индексов
- `Deployment` с initContainer `sysctl -w vm.max_map_count=262144` (требование Lucene)
- `ClusterIP Service` на порту 9200 (DNS: `opensearch:9200`)

В `10-app.yaml` добавлены:
- `OPENSEARCH_URL: "http://opensearch:9200"` в ConfigMap
- initContainer `wait-for-opensearch` (nc -z opensearch 9200)

### Тесты

| Класс | Тип | Тестов | Что проверяет |
|---|---|---|---|
| `SearchServiceTest` | Unit | 6 | no-op режим при client=null (тесты без OpenSearch) |
| `SearchIntegrationTest` | Integration (TC) | 3 | Реальная индексация и поиск через Testcontainers |

---

## 34. REST-дизайн API — применённые принципы

В ходе рефакторинга контроллеры были приведены к единому REST-стилю. Ниже — принципы и конкретные решения.

### Принцип 1: только существительные в URL, глаголы — через HTTP-методы

Глагол в URL нарушает REST — метод HTTP уже является «глаголом».

| До (глагол в URL) | После (ресурс + метод) |
|---|---|
| `PUT /patients/{id}/assign-doctor/{did}` | `PUT /patients/{id}/doctor/{did}` |
| `POST /wards/{id}/admit/{pid}` | `PUT /wards/{id}/patients/{pid}` |
| `POST /wards/{id}/discharge/{pid}` | `DELETE /wards/{id}/patients/{pid}` |
| `PATCH .../paid-services/{id}/pay` | `PATCH .../paid-services/{id}` + body `{"paid": true}` |
| `PATCH /nurse/supplies/{id}/adjust` | `PATCH /nurse/supplies/{id}` + body `{"delta": N}` |
| `PATCH /nurse/assignments/{id}/status?status=X` | `PATCH /nurse/assignments/{id}` + body `{"status": "X"}` |

`PUT /wards/{id}/patients/{pid}` читается как «установить пациента в палату» — семантика HTTP PUT (установить ресурс). `DELETE` — убрать связь. Никаких `/admit`, `/discharge`, `/pay`, `/adjust` в пути.

### Принцип 2: фильтрация коллекций через query-параметры

Отдельный эндпоинт `/search` — нарушение: search не ресурс, а операция над коллекцией.

```
GET /api/patients/search?q=Иван&status=TREATMENT  ← до
GET /api/patients?q=Иван&status=TREATMENT         ← после
```

Один эндпоинт `/patients` с опциональными параметрами `q` и `status`. Если параметры не переданы — возвращается весь список.

### Принцип 3: `/me` для ресурсов текущего пользователя

`/my` — разговорная форма, не является REST-стандартом. `/me` — принятое соглашение (используется в GitHub API, Spotify Web API и др.).

```
GET /api/client/appointments/my   → GET /api/client/me/appointments
GET /api/chat/my-rooms            → GET /api/chat/me/rooms
GET /api/medical/documents/my     → GET /api/medical/me/documents
```

Структура `/me/ресурс` читается как «мои ресурсы» — субъект перед объектом.

### Принцип 4: объединение дублирующих эндпоинтов

Два эндпоинта для одного ресурса с разными режимами работы — лишняя поверхность API.

```
GET /rooms/{id}/messages         ← возвращает все сообщения
GET /rooms/{id}/messages/poll?sinceId=N  ← только новые
```

После: один эндпоинт `GET /rooms/{id}/messages?sinceId=N`. При `sinceId=0` — возвращает всё (первоначальная загрузка). При `sinceId=N` — только новые (polling). Поведение определяется параметром, а не URL.

### Принцип 5: тело запроса для PATCH, не query-параметры

Query-параметры в PATCH-запросах нарушают семантику: тело запроса предназначено для описания изменения ресурса.

```
PATCH /assignments/{id}/status?status=DONE         ← до: статус в query param
PATCH /assignments/{id}  + body {"status": "DONE"} ← после: статус в теле
```

Добавлены DTO: `UpdateAssignmentStatusRequest`, `UpdatePaidStatusRequest` для строгой типизации.

`SearchIntegrationTest` использует `GenericContainer` с `opensearchproject/opensearch:2.17.0` и `@DynamicPropertySource` для подстановки порта. Профиль `test` не используется — чтобы не отключался OpenSearch (свойства передаются inline через `properties = {...}`).

---

## 35. Платёжная интеграция: Alfa Bank

Клиентский портал поддерживает оплату платных услуг через **Alfa Bank Payment Gateway** (одностадийный платёж).

### Архитектура

```
POST /api/client/service-orders/pay  (ROLE_CLIENT, JWT)
  ↓
PaymentServiceImpl.initiatePayment()
  ↓
AlfaBankGatewayClient.registerOrder()  →  Alfa Bank register.do
  ↓
PaymentOrder(status=PENDING) сохраняется в БД
  ↓
Ответ: { formUrl, orderNumber }
  ↓
Браузер: window.location.href = formUrl  (переход на страницу банка)
  ↓
Клиент вводит тестовую карту и оплачивает
  ↓
Alfa Bank redirect: GET /api/payment/callback?orderId=<alfaOrderId>  (permitAll)
  ↓
PaymentServiceImpl.confirmPayment()
  → AlfaBankGatewayClient.getOrderStatusExtended()
  → orderStatus==2 (DEPOSITED): PaymentOrder→PAID + ClientServiceOrder(CONFIRMED)
  → orderStatus==6 (DECLINED): PaymentOrder→FAILED
  ↓
Возврат HTML-страницы с результатом (text/html;charset=UTF-8)
```

### Новые компоненты (добавлены в сессии 2026-05-26)

| Компонент | Путь | Описание |
|-----------|------|---------|
| `V11__payment_orders.sql` | `db/migration/` | Таблица `payment_orders` |
| `PaymentOrder` | `entity/` | JPA-сущность, PENDING→PAID/FAILED |
| `PaymentOrderStatus` | `entity/` | Enum: PENDING, PAID, FAILED, CANCELLED |
| `PaymentOrderRepository` | `repository/` | `findByAlfaOrderId(String)` |
| `AlfaBankProperties` | `payment/` | `@ConfigurationProperties(prefix="alfabank")` |
| `AlfaBankGatewayClient` | `payment/` | HTTP-клиент к шлюзу (RestTemplate, form-urlencoded) |
| `RegisterOrderResponse` | `payment/` | DTO ответа `register.do` |
| `OrderStatusResponse` | `payment/` | DTO ответа `getOrderStatusExtended.do` |
| `PaymentService` | `service/` | Интерфейс: `initiatePayment`, `confirmPayment` |
| `PaymentServiceImpl` | `service/impl/` | Реализация с бизнес-логикой |
| `PaymentController` | `controller/` | `/api/payment/callback`, `/api/payment/fail` |
| `PaymentInitResponse` | `dto/response/` | `{ formUrl, orderNumber }` |
| `RestTemplateConfig` | `config/` | `@Bean RestTemplate` |

### Flyway миграция V11

```sql
CREATE TABLE payment_orders (
    id              BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    order_number    VARCHAR(64)  NOT NULL UNIQUE,     -- внутренний UUID-16 (hex)
    alfa_order_id   VARCHAR(64),                      -- UUID от Alfa Bank
    client_user_id  BIGINT       NOT NULL REFERENCES users(id),
    paid_service_id BIGINT       NOT NULL REFERENCES paid_service(id),
    contact_phone   VARCHAR(32),
    notes           TEXT,
    preferred_date  DATE,
    preferred_time  TIME,
    amount_kopecks  BIGINT       NOT NULL,            -- цена × 100
    status          VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    paid_at         TIMESTAMP                         -- заполняется при PAID
);
```

### API эндпоинты

| Метод | URL | Доступ | Описание |
|-------|-----|--------|---------|
| `POST` | `/api/client/service-orders/pay` | `ROLE_CLIENT` | Инициировать оплату, получить `formUrl` |
| `GET` | `/api/payment/callback?orderId=` | `permitAll` | Callback от Альфа Банк (redirect браузера) |
| `GET` | `/api/payment/fail?orderId=` | `permitAll` | Fail-URL от Альфа Банк |

### Конфигурация (application.yml)

```yaml
alfabank:
  gateway-url: ${ALFABANK_GATEWAY_URL:https://alfa.rbsuat.com/payment/rest/}
  user-name: ${ALFABANK_USERNAME:}        # пустой дефолт — реальные значения в application-local.yml
  password: ${ALFABANK_PASSWORD:}
  return-url: ${ALFABANK_RETURN_URL:http://localhost:8090/api/payment/callback}
  fail-url: ${ALFABANK_FAIL_URL:http://localhost:8090/api/payment/fail}
```

### Безопасное хранение кредов

Реальные credentials **не коммитятся в git**:

- `application-local.yml` — gitignored, содержит `user-name` и `password` для локального запуска
- `rancher/k8s/10-app.yaml` ConfigMap — только не-чувствительные параметры (URL)
- `rancher/k8s/01-secrets.yaml` — Alfa Bank ключи с placeholder `REPLACE_ME`, заполняются вручную перед деплоем

```
.gitignore:
  src/main/resources/application-local.yml
  *.env
```

### K8s: ConfigMap + Secret

```yaml
# 10-app.yaml ConfigMap — только non-sensitive
ALFABANK_GATEWAY_URL: "https://alfa.rbsuat.com/payment/rest/"
ALFABANK_RETURN_URL:  "http://localhost:30090/api/payment/callback"
ALFABANK_FAIL_URL:    "http://localhost:30090/api/payment/fail"

# Deployment env — credentials из Secret
- name: ALFABANK_USERNAME
  valueFrom:
    secretKeyRef:
      name: pet-hospital-secrets
      key: alfabank-username
- name: ALFABANK_PASSWORD
  valueFrom:
    secretKeyRef:
      name: pet-hospital-secrets
      key: alfabank-password
```

### SecurityConfig

```java
// ПЕРЕД другими правилами — банк делает redirect без JWT
.requestMatchers("/api/payment/callback", "/api/payment/fail").permitAll()
```

### Расчёт суммы в копейках

```java
long amountKopecks = service.getPrice()
        .multiply(BigDecimal.valueOf(100))
        .longValueExact();
// 1500.00 руб → 150000 копеек
```

### confirmPayment — возвращаемые статусы

| Статус | Условие |
|--------|---------|
| `"paid"` | `orderStatus == 2` (DEPOSITED) или уже PAID (идемпотентный вызов) |
| `"failed"` | `orderStatus == 6` (DECLINED) |
| `"pending"` | `orderStatus == 0` (CREATED — ещё не оплачен) |
| `"not_found"` | `alfaOrderId` не найден в `payment_orders` |

### Тесты

| Класс | Тип | Тестов | Что покрывает |
|-------|-----|--------|--------------|
| `PaymentServiceTest` | Unit (Mockito) | 10 | `initiatePayment` (success, amount, errors), `confirmPayment` (paid/failed/pending/notFound/idempotent) |
| `PaymentControllerTest` | `@WebMvcTest` | 8 | `/callback` и `/fail` — HTML-ответы, кириллица, `permitAll` |

Итого тестов в проекте: **265** (было 247 + 18 новых).

#### Ключевые техники тестирования

```java
// PaymentServiceTest — LENIENT для @BeforeEach стабов
@MockitoSettings(strictness = Strictness.LENIENT)

// PaymentControllerTest — исключить UserDetailsServiceAutoConfiguration
// чтобы не было двух кандидатов UserDetailsService
@WebMvcTest(controllers = PaymentController.class,
        excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)

// Проверка кириллицы в HTML — явный charset!
.andExpect(content().contentType("text/html;charset=UTF-8"))
.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)
```

### Frontend (client.html)

Кнопка «Оплатить» вызывает `POST /api/client/service-orders/pay`, получает `formUrl` и делает `window.location.href = formUrl`. Кнопка блокируется (`disabled = true`) перед вызовом — предотвращает двойную оплату. `const API = ''` (same-origin) — работает на любом порту (8090 локально и 30090 в K8s).
