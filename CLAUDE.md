# Pet Hospital HIS — Claude Code Guide

Учебный проект: Hospital Information System на Spring Boot 3.2 с клиентским порталом, чат-системой, медицинской документацией и полнотекстовым поиском через OpenSearch.

---


## Быстрый запуск

```bash
docker-compose up -d          # поднять все 9 сервисов (включая OpenSearch)
docker-compose up -d --build app  # пересобрать после изменения кода
```

---

## Kubernetes (Rancher Desktop)

Альтернативный способ запуска — через Kubernetes локально в Rancher Desktop.

### Структура K8s манифестов

```
rancher/k8s/
├── 00-namespace.yaml   # namespace: pet-hospital
├── 01-secrets.yaml     # postgres password
├── 02-postgres.yaml    # PVC 1Gi + Deployment + ClusterIP
├── 03-redis.yaml       # Deployment + ClusterIP (без PVC — кэш не персистентен)
├── 04-zookeeper.yaml   # Deployment + ClusterIP
├── 05-kafka.yaml       # initContainer(wait-zookeeper) + ClusterIP :29092/:9092
├── 06-kafdrop.yaml     # initContainer(wait-kafka) + NodePort :30009
├── 07-loki.yaml        # ConfigMap + PVC 2Gi + securityContext(root) + ClusterIP
├── 08-prometheus.yaml  # ConfigMap + PVC 1Gi + NodePort :30900
├── 09-grafana.yaml     # 3×ConfigMap + PVC 256Mi + NodePort :30300
├── 10-app.yaml         # 5×initContainer + imagePullPolicy:Never + NodePort :30090
├── 11-dashboard.yaml   # K8s Dashboard в namespace kubernetes-dashboard + NodePort :30443
└── 12-opensearch.yaml  # PVC 2Gi + sysctl initContainer + Deployment + ClusterIP :9200
```

### Порты сервисов (K8s NodePort)

| Сервис | URL | Логин |
|--------|-----|-------|
| Приложение | http://localhost:30090 | admin / admin123 |
| Kafdrop | http://localhost:30009 | — |
| Prometheus | http://localhost:30901 | — |
| Grafana | http://localhost:30301 | admin / admin |
| K8s Dashboard | https://localhost:30443 | Bearer token |
| OpenSearch REST | http://localhost:9200 (port-forward) | — |

### Имена K8s Services (DNS внутри кластера)

Имена намеренно совпадают с именами сервисов в docker-compose:

| K8s Service | DNS в приложении | Файл |
|-------------|-----------------|------|
| `postgres` | `jdbc:postgresql://postgres:5432/hospital_db` | 02-postgres.yaml |
| `redis` | `SPRING_DATA_REDIS_HOST=redis` | 03-redis.yaml |
| `kafka` | `SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:29092` | 05-kafka.yaml |
| `loki` | `LOKI_URL=http://loki:3100` | 07-loki.yaml |
| `prometheus` | `http://prometheus:9090` (Grafana datasource) | 08-prometheus.yaml |
| `hospital-app` | `targets: hospital-app:8090` (Prometheus scrape) | 10-app.yaml |
| `opensearch` | `OPENSEARCH_URL=http://opensearch:9200` | 12-opensearch.yaml |

### Деплой (первый раз)

```powershell
# 1. Собрать образ и загрузить в VM Rancher Desktop
.\rancher\build-and-load.ps1
```
```powershell
# 2. Задеплоить весь стек
kubectl apply -f rancher/k8s/
```
```powershell
# 3. Следить за запуском
kubectl get pods -n pet-hospital -w
```

### Критические решения K8s

**`enableServiceLinks: false`** (05-kafka.yaml) — K8s автоматически инжектирует env-переменную `KAFKA_PORT` во все поды namespace (из Service с именем `kafka`). Confluent Kafka интерпретирует любую `KAFKA_*` переменную как конфиг → конфликт → Exit Code 1 через 2 секунды. `enableServiceLinks: false` отключает эту инжекцию.

**imagePullPolicy: Never** — обязательно для локальных образов в Rancher Desktop.
`docker build` кладёт образ в Docker Desktop VM, а k3s использует отдельную VM.
Без загрузки через `rdctl shell -- docker load` образ не найдётся.
`--provenance=false` при build обязателен — без него BuildKit создаёт manifest list,
который k3s не может использовать с `imagePullPolicy: Never`.

**securityContext.runAsUser: 0** (Loki) — Loki 2.x запускается от UID 10001,
но PVC создаётся с правами root. Без runAsUser:0 → Permission denied при записи в /loki.

**Kafka ADVERTISED_LISTENERS** — два listener'а обязательны:
- `PLAINTEXT://kafka:29092` — для подов внутри K8s (K8s DNS-имя)
- `PLAINTEXT_HOST://localhost:9092` — для port-forward с хоста

**5 initContainer в 10-app.yaml** — Spring Boot падает при старте если БД, Redis,
Kafka, Loki или OpenSearch недоступны. initContainer ждёт каждую зависимость (nc -z host port)
перед запуском основного контейнера.

**`@KafkaListener` vs `KafkaTemplate`** — `KafkaTemplate` (producer) подключается LAZY, при первой отправке → initContainer `wait-for-kafka` НЕ нужен. `@KafkaListener` (consumer) подключается EAGERLY при старте Spring Boot → без Kafka = CrashLoopBackOff → initContainer ОБЯЗАТЕЛЕН. Проект использует оба → initContainer нужен.

**livenessProbe.initialDelaySeconds: 90** — БОЛЬШЕ чем у readinessProbe (30).
Если liveness сработает раньше чем Spring Boot запустится, K8s убьёт Pod → цикл.

**Grafana: два ConfigMap в одну директорию** — нельзя смонтировать два ConfigMap
в одну директорию (второй перезатирает первый). Решение:
- `grafana-dashboard-config` → `/dashboards/` (только dashboards.yml)
- `grafana-dashboards` → `/dashboards/json/` (JSON файлы)
- В dashboards.yml: `path: /dashboards/json`

### Обновление приложения

```powershell
# Сборка + загрузка + рестарт одной командой
.\rancher\build-and-load.ps1 -Restart
```

### Сброс стека

```powershell
kubectl delete namespace pet-hospital   # Удаляет все ресурсы включая PVC (данные потеряются)
kubectl apply -f rancher/k8s/           # Поднять заново
```

---

| Интерфейс | URL | Логин / Пароль |
|---|---|---|
| Администрация (HIS) | http://localhost:8090/admin.html | admin / admin123 |
| Медсестра (HIS) | http://localhost:8090/nurse.html | nurse1 / nurse123 |
| Портал врача | http://localhost:8090/doctor.html | doctor1–doctor6 / doctor123 |
| Клиентский портал | http://localhost:8090/client.html | client1 / client123 |
| Личный кабинет клиента | http://localhost:8090/account.html | (через client.html → Кабинет) |
| Swagger UI | http://localhost:8090/swagger-ui.html | — |
| Kafdrop | http://localhost:9000 | — |
| Grafana | http://localhost:3000 | admin / admin |

---

## Структура проекта

```
src/main/java/com/hospital/
├── config/
│   ├── SecurityConfig.java             # Spring Security + JWT, RBAC, CORS
│   ├── JwtUtil.java                    # генерация/валидация JWT (HS256, 24ч)
│   ├── JwtAuthenticationFilter.java
│   ├── KafkaConfig.java                # топики Kafka + @Primary JpaTransactionManager
│   ├── CacheConfig.java                # Redis TTL=5мин
│   ├── OpenSearchConfig.java           # @ConditionalOnProperty + ApacheHttpClient5TransportBuilder
│   ├── AopLoggingAspect.java           # логирование времени всех сервисов
│   └── DataInitializer.java            # создание 10 дефолтных пользователей + linkDoctorUser()
│
├── controller/
│   ├── AuthController.java             # /api/auth/login, /register, /register-client
│   ├── ClientController.java           # /api/client/** (публичный + ROLE_CLIENT)
│   ├── PatientController.java          # /api/patients
│   ├── DoctorController.java           # /api/doctors + GET /api/doctors/me (профиль по JWT)
│   ├── DepartmentController.java       # /api/departments
│   ├── WardController.java             # /api/wards
│   ├── PaidServiceController.java      # /api/paid-services
│   ├── AdminController.java            # /api/admin (ROLE_ADMIN only)
│   ├── ChatController.java             # /api/chat/** — чаты поддержки и врача
│   ├── MedicalController.java          # /api/medical/** — документы и история
│   └── SearchController.java           # /api/search/patients, /api/search/doctors
│
├── service/
│   ├── ChatService.java                # интерфейс: getOrCreate, send, poll, rooms
│   ├── MedicalService.java             # интерфейс: createDocument, createNote, history
│   ├── impl/
│   │   ├── ClientServiceImpl.java
│   │   ├── PatientServiceImpl.java
│   │   ├── WardServiceImpl.java
│   │   ├── AdminServiceImpl.java
│   │   ├── ChatServiceImpl.java        # short-polling, IDOR-защита через switch(role)
│   │   └── MedicalServiceImpl.java     # @Transactional(readOnly) + override на write
│   ├── event/                          # Kafka события + консьюмеры
│   └── strategy/                       # Strategy pattern для выписки пациентов
│
├── search/
│   ├── SearchService.java              # интерфейс: index/delete/search для Patient и Doctor
│   ├── SearchServiceImpl.java          # @Autowired(required=false) client + graceful null-check
│   ├── PatientDocument.java            # Lombok @Data @Builder: id, fullName, ward, department, active
│   └── DoctorDocument.java             # Lombok @Data @Builder: id, fullName, specialization, department, active
│
├── entity/
│   ├── ChatRoomType.java               # enum: SUPPORT, DOCTOR_CLIENT
│   ├── MedicalDocumentType.java        # enum: PRESCRIPTION, REFERRAL, SICK_LEAVE, ...
│   ├── PatientNoteType.java            # enum: DIAGNOSIS, OBSERVATION, NOTE
│   ├── ChatRoom.java                   # staffUser nullable (null = любой admin)
│   ├── ChatMessage.java                # read (не isRead!), id как cursor для polling
│   ├── MedicalDocument.java            # soft-delete via active, validUntil nullable
│   ├── PatientNote.java                # visibleToClient=false по умолчанию
│   ├── Appointment.java                # запись к врачу (клиентский портал)
│   ├── ClientServiceOrder.java         # заказ услуги (клиентский портал)
│   ├── Patient.java                    # soft-delete, history tracking, clientUser FK
│   ├── Doctor.java                     # linkedUser FK (для чата и меддокументов)
│   ├── Department.java                 # НЕТ поля active — не делать фильтр ::isActive
│   ├── Ward.java
│   ├── PaidService.java
│   ├── User.java                       # implements UserDetails
│   ├── OutboxEvent.java                # идемпотентность Kafka
│   └── *History.java                   # аудит врачей и палат
│
├── repository/
│   ├── ChatRoomRepository.java         # findByTypeAndClientUserId, partial unique index
│   ├── ChatMessageRepository.java      # findByRoomIdAndIdGreaterThan (polling cursor)
│   ├── MedicalDocumentRepository.java  # findByPatientId vs findByPatientClientUserId
│   ├── PatientNoteRepository.java      # findByPatientId vs findVisibleByClientUserId
│   ├── AppointmentRepository.java
│   ├── ClientServiceOrderRepository.java
│   └── DoctorRepository.java           # findByLinkedUserIdAndActiveTrue
│
└── dto/
    ├── request/
    │   ├── SendMessageRequest.java
    │   ├── CreateMedicalDocumentRequest.java
    │   ├── CreatePatientNoteRequest.java
    │   ├── AppointmentRequest.java
    │   └── ServiceOrderRequest.java
    └── response/
        ├── ChatRoomResponse.java        # unreadCount, lastMessage preview
        ├── ChatMessageResponse.java     # senderId для bubble-alignment
        ├── MedicalDocumentResponse.java # type + typeLabel (machine + human)
        ├── PatientNoteResponse.java     # visibleToClient flag
        ├── PatientHistoryResponse.java  # агрегат: notes + documents
        ├── AppointmentResponse.java
        ├── ServiceOrderResponse.java
        └── PublicDoctorResponse.java
```

```
src/main/resources/
├── application.yml                 # defaults (localhost), port 8090
├── application-test.yml            # Testcontainers + EmbeddedKafka
│                                   # + allow-bean-definition-overriding: true
├── db/migration/
│   ├── V1__initial_schema.sql      # core schema
│   ├── V2__test_data.sql           # базовые данные
│   ├── V3__add_users.sql           # таблица users
│   ├── V4__client_schema.sql       # appointment + client_service_order
│   ├── V5__extended_seed_data.sql  # ~45 пациентов, ~25 врачей, ~10 отделений
│   ├── V6__chat_medical_schema.sql # chat_room, chat_message, medical_document,
│   │                               # patient_note + partial unique indexes
│   └── V7__link_doctor_users.sql   # fallback-линковка doctor2–doctor6 → users (идемпотентно)
├── logback-spring.xml              # loki4j appender (async)
└── static/
    ├── index.html                  # legacy HIS SPA (обратная совместимость)
    ├── admin.html                  # HIS для ROLE_ADMIN (role guard + app.js)
    ├── nurse.html                  # HIS для ROLE_NURSE (standalone, без app.js)
    ├── app.js                      # SECTION_ACCESS, loadChats, openPatientHistoryModal
    ├── client.html                 # Клиентский портал (лендинг, ROLE_CLIENT/public)
    ├── account.html                # Личный кабинет клиента (ROLE_CLIENT, auth guard)
    └── doctor.html                 # Портал врача (ROLE_DOCTOR, auth guard, /api/doctors/me)
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
| OpenSearch | 9200 |

---

## Роли и доступ

| Роль | Интерфейс | API-префикс |
|---|---|---|
| `ROLE_ADMIN` | admin.html | `/api/admin/**`, `GET /api/chat/support`, все остальные |
| `ROLE_DOCTOR` | doctor.html | `/api/patients/**`, `/api/doctors/**`, `/api/doctors/me`, `/api/medical/**` (write/read), `GET /api/chat/doctor/rooms` |
| `ROLE_NURSE` | nurse.html | `/api/patients/**` (ограниченно) |
| `ROLE_CLIENT` | client.html + account.html | `/api/client/**`, `POST /api/chat/support`, `POST /api/chat/doctor/**`, `GET /api/chat/my-rooms`, `GET /api/medical/documents/my`, `GET /api/medical/history/my` |

Публичный доступ без токена:
- `GET /api/client/doctors`, `GET /api/client/departments`, `GET /api/client/services`
- Статика: `/*.html`, `/css/**`, `/js/**`

### Чат — SecurityConfig правила

```
POST /api/chat/support          → ROLE_CLIENT   (создать/получить комнату поддержки)
GET  /api/chat/support          → ROLE_ADMIN    (список всех комнат поддержки)
POST /api/chat/doctor/{userId}  → ROLE_CLIENT   (создать/получить комнату с врачом)
GET  /api/chat/doctor/rooms     → ROLE_DOCTOR   (список чатов врача)
GET  /api/chat/my-rooms         → ROLE_CLIENT   (все мои чаты)
/api/chat/rooms/**              → authenticated (любой, доступ проверяет сервис)
```

### Медицина — SecurityConfig правила

```
POST /api/medical/documents                  → ROLE_DOCTOR
POST /api/medical/notes                      → ROLE_DOCTOR
GET  /api/medical/documents/patient/{id}     → ROLE_DOCTOR
GET  /api/medical/history/patient/{id}       → ROLE_DOCTOR
GET  /api/medical/documents/my               → ROLE_CLIENT
GET  /api/medical/history/my                 → ROLE_CLIENT
```

### Поиск — SecurityConfig правила

```
GET /api/search/patients   → ROLE_ADMIN, ROLE_DOCTOR, ROLE_NURSE
GET /api/search/doctors    → ROLE_ADMIN, ROLE_DOCTOR, ROLE_NURSE
```

---

## Критические решения

### @Primary JpaTransactionManager (KafkaConfig.java)

`transaction-id-prefix: tx-hospital-` создаёт `KafkaTransactionManager`, который вытесняет `JpaTransactionManager`. JPA-репозитории падают с `No bean named 'transactionManager'`.

Решение: явный `@Primary @Bean PlatformTransactionManager transactionManager(EntityManagerFactory)` в `KafkaConfig.java`.

В тестах аналогично через `TestTransactionConfig.java` (`@TestConfiguration`).

**Конфликт двух @Primary в тестах**: `KafkaConfig.transactionManager` и `TestTransactionConfig.transactionManager` оба `@Primary`. Spring Boot 3.x по умолчанию запрещает переопределение бинов. Решение: `spring.main.allow-bean-definition-overriding: true` в `application-test.yml`.

### Kafka dual-listener

```
PLAINTEXT://kafka:29092       # для контейнеров в Docker-сети
PLAINTEXT_HOST://localhost:9092 # для хоста
```

Приложение в Docker использует `kafka:29092`. Локальный запуск — `localhost:9092`.

### Soft delete

Пациенты, врачи, платные услуги и медицинские документы не удаляются физически.
- `Patient.active = false`, `Doctor.active = false`, `MedicalDocument.active = false`
- `Department` — НЕ имеет поля `active`. Нельзя использовать фильтр `Department::isActive`.

### Chat: short-polling через id-cursor

Чат работает без WebSocket. Клиент каждые 3 секунды вызывает:
```
GET /api/chat/rooms/{roomId}/messages/poll?sinceId={lastMessageId}
```
Репозиторий возвращает только сообщения с `id > sinceId`. При первом открытии `sinceId=0`.

### Chat: orElseGet() обязателен в getOrCreate

```java
// ПРАВИЛЬНО: save() вызывается ТОЛЬКО если комнаты нет
chatRoomRepository.findByTypeAndClientUserId(type, userId)
    .orElseGet(() -> chatRoomRepository.save(newRoom));

// НЕПРАВИЛЬНО: save() вызывается ВСЕГДА (до проверки Optional)
chatRoomRepository.findByTypeAndClientUserId(type, userId)
    .orElse(chatRoomRepository.save(newRoom));
```

### Chat: IDOR-защита в getAccessibleRoom

Доступ к комнате проверяется через `switch(user.getRole())`:
- `ROLE_CLIENT` → только если `room.getClientUser().getId() == user.getId()`
- `ROLE_ADMIN` → любая комната
- `ROLE_DOCTOR` → только если `room.getStaffUser()?.getId() == user.getId()`

### MedicalDocument: два поля типа

DTO содержит:
- `type` (строка enum, напр. `"PRESCRIPTION"`) — для программной логики фронтенда
- `typeLabel` (русское название, напр. `"Рецепт"`) — для отображения в UI

### PatientNote: visibleToClient=false по умолчанию

`@Builder.Default private boolean visibleToClient = false` — консервативная политика.
Врач ЯВНО выбирает, что показать пациенту. Без явного `true` — запись скрыта.

### V6-миграция: DataInitializer-порядок в тестах

V6 содержит `UPDATE doctor SET user_id = (SELECT id FROM users WHERE username='doctor1') ...`
Этот UPDATE выполняется на этапе Flyway-миграции. DataInitializer запускается ПОСЛЕ миграций.
В момент миграции таблица `users` пустая → UPDATE — no-op.

**Решение для интеграционных тестов**: в `@BeforeEach` через `JdbcTemplate`:
```java
jdbcTemplate.update(
    "UPDATE doctor SET user_id = (SELECT id FROM users WHERE username = 'doctor1') " +
    "WHERE full_name = 'Иванов Сергей Петрович'");
```

### login.html — role-based routing

`login.html` использует `roleToPath(role)` для редиректа по роли:
- `ROLE_ADMIN` → `/admin.html`
- `ROLE_DOCTOR` → `/doctor.html`
- `ROLE_NURSE` → `/nurse.html`
- `ROLE_CLIENT` → `/client.html`

Применяется как при уже активном токене (проверка при загрузке), так и после успешного логина.

### CreatePatientRequest / PatientResponse — clientUserId

`CreatePatientRequest` содержит необязательное поле `private Long clientUserId` — для привязки нового HIS-пациента к существующей учётной записи клиентского портала.

`PatientResponse` содержит `private Long clientUserId` (маппинг через `@Mapping(target="clientUserId", source="clientUser.id")`).

Используется в doctor.html: вкладка «Чат» проверяет `patient.clientUserId != null`. Кнопка «+ В пациенты» в секции «Приёмы» передаёт `clientUserId` при регистрации.

### OpenSearch: @ConditionalOnProperty + @Autowired(required=false)

`OpenSearchConfig` создаёт бин `OpenSearchClient` только при `opensearch.enabled=true` (matchIfMissing=true).
Тестовый профиль устанавливает `opensearch.enabled=false` → бин не создаётся → `@Autowired(required=false)` в `SearchServiceImpl` получает `null`.

Все методы `SearchServiceImpl` начинаются с `if (client == null) return;` — graceful no-op.
Это позволяет всем 241 существующим тестам работать без изменений.

### OpenSearch K8s: vm.max_map_count

OpenSearch (как и Elasticsearch) требует `vm.max_map_count >= 262144`. В K8s устанавливается
через привилегированный initContainer в `12-opensearch.yaml`:
```yaml
initContainers:
  - name: sysctl
    image: busybox:1.36
    securityContext:
      privileged: true
    command: ["sysctl", "-w", "vm.max_map_count=262144"]
```
Без этого OpenSearch падает с ошибкой bootstrap checks failed.

### OpenSearch: DISABLE_SECURITY_PLUGIN=true

В dev/Docker/K8s окружениях TLS и basic auth отключены через env:
```
DISABLE_SECURITY_PLUGIN=true
```
Это позволяет подключаться по plain HTTP без сертификатов.

### OpenSearch: ApacheHttpClient5TransportBuilder

opensearch-java 2.x использует `ApacheHttpClient5TransportBuilder` (NOT legacy `RestClientTransport`).
Артефакт `httpclient5` нужен явно — Spring Boot не управляет его версией.
```java
OpenSearchTransport transport = ApacheHttpClient5TransportBuilder
        .builder(httpHost)
        .setMapper(new JacksonJsonpMapper())
        .build();
```

### Java 17 vs Java 21

Проект компилируется под Java 17. Методы Java 21+ запрещены:
- `List.getLast()` → использовать `list.get(list.size() - 1)`
- `List.getFirst()` → использовать `list.get(0)`

---

## Migrations — важное

- Новые миграции нумеруются строго: V8, V9, ... (Flyway не переименовывает применённые)
- `ddl-auto: validate` — Hibernate ПРОВЕРЯЕТ схему, не создаёт. Расхождение entity/migration = падение при старте.
- V5 использует subquery для ID отделений (не хардкодит числа).
- V6 добавляет: `chat_room`, `chat_message`, `medical_document`, `patient_note`.
  - Partial unique indexes для идемпотентности getOrCreate:
    ```sql
    CREATE UNIQUE INDEX uq_support_room ON chat_room(client_user_id)
        WHERE type = 'SUPPORT';
    CREATE UNIQUE INDEX uq_doctor_room ON chat_room(client_user_id, staff_user_id)
        WHERE type = 'DOCTOR_CLIENT';
    ```

---

## Тесты

```bash
# Через IntelliJ bundled Maven (если mvn не в PATH):
"C:\Program Files\JetBrains\IntelliJ IDEA 2025.2.4\plugins\maven\lib\maven3\bin\mvn.cmd" test

# Только юнит-тесты (без Docker):
mvn test -Dtest="PatientServiceTest,WardServiceTest,AdminServiceTest,JwtUtilTest,ChatServiceTest,MedicalServiceTest,SearchServiceTest"

# Только интеграционные (нужен Docker TCP 2375):
mvn test -Dtest="AuthIntegrationTest,PatientIntegrationTest,ChatIntegrationTest,MedicalIntegrationTest,SearchIntegrationTest"

# Все тесты:
mvn test
```

**Итого: 247 тестов — 86 юнит + 70 интеграционных + 91 из предыдущих сессий**

**Windows**: Docker Desktop → Settings → General → "Expose daemon on tcp://localhost:2375 without TLS"

Тестовый профиль (`application-test.yml`):
- PostgreSQL через Testcontainers (`jdbc:tc:postgresql:15:///`)
- EmbeddedKafka (`spring.embedded.kafka.brokers`)
- Redis отключён (`cache.type: none`)
- Kafka-транзакции отключены (`transaction-id-prefix: ""`)
- `spring.main.allow-bean-definition-overriding: true` (иначе конфликт двух @Primary transactionManager)

### Список тест-классов

| Класс | Тип | Тестов | Что покрывает |
|---|---|---|---|
| `JwtUtilTest` | Юнит | 5 | Генерация/валидация JWT |
| `AdminServiceTest` | Юнит | 9 | Выписка пациентов, Strategy pattern |
| `PatientServiceTest` | Юнит | 16 | CRUD пациентов, soft-delete, clientUserId, авто-чат |
| `WardServiceTest` | Юнит | 5 | Размещение в палате |
| `ChatServiceTest` | Юнит | 24 | Все методы ChatServiceImpl, IDOR, двунаправленный чат |
| `MedicalServiceTest` | Юнит | 21 | Все методы MedicalServiceImpl, типы/labels |
| `SearchServiceTest` | Юнит | 6 | Graceful no-op когда OpenSearchClient=null |
| `AuthIntegrationTest` | Интеграционный | 10 | Логин, регистрация, 401/403 |
| `PatientIntegrationTest` | Интеграционный | 10 | CRUD пациентов через HTTP, clientUserId, авто-чат |
| `ChatIntegrationTest` | Интеграционный | 17 | RBAC чата, идемпотентность, send+poll, двунаправленный чат |
| `MedicalIntegrationTest` | Интеграционный | 23 | RBAC медицины, создание, e2e |
| `SearchIntegrationTest` | Интеграционный | 3 | Реальный OpenSearch через Testcontainers, index+search+delete |

### Особенности тестирования на Windows

`MockMvcResult.getResponse().getContentAsString()` по умолчанию использует системную кодировку (CP-1252 на Windows).
При сравнении кириллических строк кодировка искажается. Всегда использовать:
```java
response.getContentAsString(StandardCharsets.UTF_8)
```

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
| GET | `/api/client/departments` | permitAll | Все отделения |
| GET | `/api/client/services` | permitAll | Активные платные услуги |
| POST | `/api/client/appointments` | ROLE_CLIENT | Создать запись к врачу |
| GET | `/api/client/appointments/my` | ROLE_CLIENT | Записи текущего пользователя |
| POST | `/api/client/service-orders` | ROLE_CLIENT | Создать заказ услуги |
| GET | `/api/client/service-orders/my` | ROLE_CLIENT | Заказы текущего пользователя |

### Статусы

- `Appointment`: `PENDING` → `CONFIRMED` / `CANCELLED`
- `ClientServiceOrder`: `PENDING` → `CONFIRMED` → `COMPLETED` / `CANCELLED`

---

## Чат-система — детали

### Типы комнат

| Тип | staffUser | Кто может отвечать |
|---|---|---|
| `SUPPORT` | `null` | любой ROLE_ADMIN |
| `DOCTOR_CLIENT` | конкретный User врача | только этот врач |

### AppointmentRequest для чата с врачом

```
POST /api/chat/doctor/{doctorUserId}
```
`doctorUserId` — это `users.id` (не `doctor.id`). Врач идентифицируется по учётной записи.

---

## Медицинская документация — детали

### Типы документов (MedicalDocumentType)

| Enum | Русский | validUntil |
|---|---|---|
| `PRESCRIPTION` | Рецепт | обычно 30 дней |
| `REFERRAL` | Направление | обычно несколько дней |
| `SICK_LEAVE` | Больничный лист | явная дата |
| `ANALYSIS_ORDER` | Направление на анализы | несколько дней |
| `CERTIFICATE` | Справка | null (бессрочная) |

### Типы заметок (PatientNoteType)

| Enum | Русский | visibleToClient по умолчанию |
|---|---|---|
| `DIAGNOSIS` | Диагноз | false |
| `OBSERVATION` | Наблюдение | false |
| `NOTE` | Заметка | false |

---

## Портал врача (doctor.html) — детали

### Учётные данные врачей

| Логин | Пароль | ФИО |
|---|---|---|
| `doctor1` | `doctor123` | Иванов Сергей Петрович |
| `doctor2` | `doctor123` | Захаров Андрей Михайлович |
| `doctor3` | `doctor123` | Беляев Константин Семёнович |
| `doctor4` | `doctor123` | Романова Анна Викторовна |
| `doctor5` | `doctor123` | Тарасова Людмила Витальевна |
| `doctor6` | `doctor123` | Федосеев Алексей Владимирович |

### Инициализация сессии

При загрузке `doctor.html` вызывается `GET /api/doctors/me` — возвращает запись `doctor` по `users.id` из JWT. Если пользователь не связан с записью врача → редирект на `/login.html`.

### Ключевые функции

- **Мои пациенты**: список с поиском и фильтром статуса; карточки с кнопками
- **Панель деталей пациента** (выезжает справа, 480px): 4 вкладки:
  - История: `GET /api/medical/history/patient/{id}` — все заметки + документы
  - Добавить заметку: `POST /api/medical/notes` (type, content, visibleToClient)
  - Добавить документ: `POST /api/medical/documents` (type, title, content, validUntil)
  - Чат: `GET /api/patients/{id}` → `clientUserId` → `POST /api/chat/doctor/{clientUserId}`
- **Приёмы**: записи клиентского портала к этому врачу. Кнопка «+ В пациенты» открывает модалку регистрации пациента в HIS с предзаполненным именем и автоматической привязкой `clientUserId` + назначением врача.
- **Чаты**: двухколоночный интерфейс (список комнат + переписка, short-polling 3с)

### GET /api/doctors/me

```
GET /api/doctors/me
Authorization: Bearer <ROLE_DOCTOR token>
```

Реализован через `DoctorRepository.findByLinkedUserIdAndActiveTrue(userId)`. Возвращает `DoctorResponse`.

### Линковка doctor.user_id

`DataInitializer.linkDoctorUser()` запускается ПОСЛЕ создания пользователей:
```java
// UPDATE с guard: user_id IS NULL — идемпотентно
jdbc.update("UPDATE doctor SET user_id = (SELECT id FROM users WHERE username = ?) " +
            "WHERE full_name = ? AND user_id IS NULL", username, doctorFullName);
```
V7-миграция — резервный fallback (no-op при первом запуске, когда таблица users ещё пуста).

---

## Мониторинг

```logql
{app="pet-hospital"}                          # все логи
{app="pet-hospital"} |= "ERROR"               # только ошибки
{app="pet-hospital"} |= "ChatServiceImpl"     # логи чата
{app="pet-hospital"} |= "MedicalServiceImpl"  # логи медицины
{app="pet-hospital", level=~"WARN|ERROR"}     # warning и выше
```

Grafana: http://localhost:3000 → Explore → Loki → `{app="pet-hospital"}`
