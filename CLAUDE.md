# Pet Hospital HIS — Claude Code Guide

Учебный проект: Hospital Information System на Spring Boot 3.2 с клиентским порталом.

---

## Быстрый запуск

```bash
docker-compose up -d          # поднять все 8 сервисов
docker-compose up -d --build app  # пересобрать после изменения кода
```

| Интерфейс | URL | Логин / Пароль |
|---|---|---|
| HIS (персонал) | http://localhost:8090 | admin / admin123 |
| Клиентский портал | http://localhost:8090/client.html | client1 / client123 |
| Swagger UI | http://localhost:8090/swagger-ui.html | — |
| Kafdrop | http://localhost:9000 | — |
| Grafana | http://localhost:3000 | admin / admin |

---

## Структура проекта

```
src/main/java/com/hospital/
├── config/
│   ├── SecurityConfig.java         # Spring Security + JWT, RBAC, CORS
│   ├── JwtUtil.java                # генерация/валидация JWT (HS256, 24ч)
│   ├── JwtAuthenticationFilter.java
│   ├── KafkaConfig.java            # топики Kafka + @Primary JpaTransactionManager
│   ├── CacheConfig.java            # Redis TTL=5мин
│   ├── AopLoggingAspect.java       # логирование времени всех сервисов
│   └── DataInitializer.java        # создание 5 дефолтных пользователей
│
├── controller/
│   ├── AuthController.java         # /api/auth/login, /register, /register-client
│   ├── ClientController.java       # /api/client/** (публичный + ROLE_CLIENT)
│   ├── PatientController.java      # /api/patients
│   ├── DoctorController.java       # /api/doctors
│   ├── DepartmentController.java   # /api/departments
│   ├── WardController.java         # /api/wards
│   ├── PaidServiceController.java  # /api/paid-services
│   └── AdminController.java        # /api/admin (ROLE_ADMIN only)
│
├── service/
│   ├── impl/
│   │   ├── ClientServiceImpl.java  # логика клиентского портала
│   │   ├── PatientServiceImpl.java
│   │   ├── WardServiceImpl.java
│   │   └── AdminServiceImpl.java
│   ├── event/                      # Kafka события + консьюмеры
│   └── strategy/                   # Strategy pattern для выписки пациентов
│
├── entity/
│   ├── Appointment.java            # запись к врачу (клиентский портал)
│   ├── ClientServiceOrder.java     # заказ услуги (клиентский портал)
│   ├── Patient.java                # soft-delete, history tracking
│   ├── Doctor.java
│   ├── Department.java
│   ├── Ward.java
│   ├── PaidService.java
│   ├── User.java                   # implements UserDetails
│   ├── OutboxEvent.java            # идемпотентность Kafka
│   └── *History.java               # аудит врачей и палат
│
├── repository/
│   ├── AppointmentRepository.java      # findByClientUserIdWithDetails()
│   ├── ClientServiceOrderRepository.java
│   └── DoctorRepository.java           # findAllActiveDoctorsWithDepartment()
│
└── dto/
    ├── request/
    │   ├── AppointmentRequest.java      # @NotNull doctorId, @Future preferredDate
    │   └── ServiceOrderRequest.java
    └── response/
        ├── AppointmentResponse.java
        ├── ServiceOrderResponse.java    # + servicePrice BigDecimal
        └── PublicDoctorResponse.java    # id, fullName, specialty, cabinet, dept
```

```
src/main/resources/
├── application.yml                 # defaults (localhost), port 8090
├── application-test.yml            # Testcontainers + EmbeddedKafka
├── db/migration/
│   ├── V1__initial_schema.sql      # core schema
│   ├── V2__test_data.sql           # базовые данные
│   ├── V3__add_users.sql           # таблица users
│   ├── V4__client_schema.sql       # appointment + client_service_order
│   └── V5__extended_seed_data.sql  # ~45 пациентов, ~25 врачей, ~10 отделений
├── logback-spring.xml              # loki4j appender (async)
└── static/
    ├── index.html                  # HIS SPA (персонал: admin/doctor/nurse)
    └── client.html                 # Клиентский портал (role: CLIENT)
```

---

## Порты

| Сервис | Порт |
|---|---|
| Spring Boot app | **8090** |
| PostgreSQL | 5432 |
| Redis | 6379 |
| Kafka (внешний) | 9092 |
| Kafka (Docker-внутренний) | 29092 |
| Kafdrop | 9000 |
| Loki | 3100 |
| Grafana | 3000 |

---

## Роли и доступ

| Роль | Интерфейс | API-префикс |
|---|---|---|
| `ROLE_ADMIN` | index.html | `/api/admin/**`, все остальные |
| `ROLE_DOCTOR` | index.html | `/api/patients/**`, `/api/doctors/**` |
| `ROLE_NURSE` | index.html | `/api/patients/**` (ограниченно) |
| `ROLE_CLIENT` | client.html | `/api/client/**` (запись, заказы) |

Публичный доступ без токена:
- `GET /api/client/doctors`
- `GET /api/client/departments`
- `GET /api/client/services`
- Статика: `/*.html`, `/css/**`, `/js/**`

---

## Критические решения

### @Primary JpaTransactionManager (KafkaConfig.java)

`transaction-id-prefix: tx-hospital-` создаёт `KafkaTransactionManager`, который вытесняет `JpaTransactionManager`. JPA-репозитории падают с `No bean named 'transactionManager'`.

Решение: явный `@Primary @Bean PlatformTransactionManager transactionManager(EntityManagerFactory)` в `KafkaConfig.java`.

В тестах аналогично через `TestTransactionConfig.java` (`@TestConfiguration`).

### Kafka dual-listener

```
PLAINTEXT://kafka:29092       # для контейнеров в Docker-сети
PLAINTEXT_HOST://localhost:9092 # для хоста
```

Приложение в Docker использует `kafka:29092`. Локальный запуск — `localhost:9092`.

### Soft delete

Пациенты не удаляются физически. `Patient.active = false`. Все запросы фильтруют по `active = true`.

---

## Migrations — важное

- Новые миграции нумеруются строго: V6, V7, ... (Flyway не переименовывает применённые)
- `ddl-auto: validate` — Hibernate ПРОВЕРЯЕТ схему, не создаёт. Расхождение entity/migration = падение при старте.
- V5 использует subquery для ID отделений (не хардкодит числа), чтобы работать поверх разных состояний БД.

---

## Тесты

```bash
mvn test                            # все тесты (нужен Docker TCP 2375)
mvn test -Dtest="PatientServiceTest,WardServiceTest,AdminServiceTest,JwtUtilTest"  # только юнит
mvn test -Dtest="AuthIntegrationTest,PatientIntegrationTest"                       # только интеграционные
```

**Windows**: Docker Desktop → Settings → General → "Expose daemon on tcp://localhost:2375 without TLS"

Тестовый профиль (`application-test.yml`):
- PostgreSQL через Testcontainers (`jdbc:tc:postgresql:15:///`)
- EmbeddedKafka (`spring.embedded.kafka.brokers`)
- Redis отключён (`cache.type: none`)
- Kafka-транзакции отключены (`transaction-id-prefix: ""`)

---

## Добавление нового функционала

### Новый REST-эндпоинт

1. DTO в `dto/request/` и `dto/response/`
2. Метод в интерфейсе `service/`
3. Реализация в `service/impl/` с `@Transactional`
4. Метод в контроллере с `@Operation` (Swagger)
5. Права в `SecurityConfig.java` если нужна новая роль

### Новая таблица БД

1. Создать `V{N}__description.sql` в `db/migration/`
2. Создать JPA-entity
3. Создать Spring Data репозиторий
4. Проверить соответствие entity и SQL (`ddl-auto: validate`)

### Новое Kafka-событие

1. Создать data-класс события в `service/event/`
2. Добавить метод в `EventPublisher` (`@Transactional(propagation = MANDATORY)`)
3. Создать `@KafkaListener` с manual ack + idempotency через `OutboxEvent`
4. Добавить топик в `KafkaConfig.java`

---

## Клиентский портал — детали

### Эндпоинты

| Метод | URL | Доступ | Описание |
|---|---|---|---|
| GET | `/api/client/doctors` | permitAll | Активные врачи с отделениями |
| GET | `/api/client/departments` | permitAll | Активные отделения |
| GET | `/api/client/services` | permitAll | Активные платные услуги |
| POST | `/api/client/appointments` | ROLE_CLIENT | Создать запись к врачу |
| GET | `/api/client/appointments/my` | ROLE_CLIENT | Записи текущего пользователя |
| POST | `/api/client/service-orders` | ROLE_CLIENT | Создать заказ услуги |
| GET | `/api/client/service-orders/my` | ROLE_CLIENT | Заказы текущего пользователя |

### AppointmentRequest

```json
{
  "doctorId": 1,
  "preferredDate": "2026-06-15",
  "preferredTime": "14:00",
  "contactPhone": "+7-999-123-45-67",
  "notes": "Первичный приём"
}
```

### Статусы

- `Appointment`: `PENDING` → `CONFIRMED` / `CANCELLED`
- `ClientServiceOrder`: `PENDING` → `CONFIRMED` → `COMPLETED` / `CANCELLED`

Смена статусов — через прямые SQL-запросы или будущий admin-API.

---

## Мониторинг

```logql
{app="pet-hospital"}                          # все логи
{app="pet-hospital"} |= "ERROR"               # только ошибки
{app="pet-hospital"} |= "ClientServiceImpl"   # клиентский портал
{app="pet-hospital", level=~"WARN|ERROR"}     # warning и выше
```

Grafana: http://localhost:3000 → Explore → Loki → `{app="pet-hospital"}`
