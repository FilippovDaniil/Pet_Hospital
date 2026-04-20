# 🏥 Pet Hospital — Hospital Information System (HIS)

A backend pet project built with **Java 17 + Spring Boot 3.2** demonstrating clean architecture, domain events via **Apache Kafka**, and common enterprise design patterns.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.2.5 |
| Database | PostgreSQL 15 |
| Messaging | Apache Kafka + ZooKeeper |
| Migrations | Flyway |
| Mapping | MapStruct |
| API Docs | OpenAPI 3 / Swagger UI |
| Testing | JUnit 5, Mockito, MockMvc, Testcontainers |
| Build | Maven 3.9 |

---

## Quick Start

### 1. Start infrastructure

```bash
docker-compose up -d
```

This starts:
- **PostgreSQL** on `localhost:5432` (user: `postgres`, password: `1234`, db: `hospital_db`)
- **Apache Kafka** on `localhost:9092`
- **ZooKeeper** on `localhost:2181`

### 2. Run the application

```bash
mvn spring-boot:run
```

Or build and run the JAR:

```bash
mvn clean package -DskipTests
java -jar target/pet-hospital-1.0.0-SNAPSHOT.jar
```

### 3. Open the UI

| URL | Description |
|---|---|
| http://localhost:8080 | Web UI (SPA) |
| http://localhost:8080/swagger-ui.html | Swagger UI |
| http://localhost:8080/api-docs | OpenAPI JSON |
| http://localhost:8080/actuator/health | Health check |

---

## Project Structure

```
src/main/java/com/hospital/
├── config/         # KafkaConfig, SwaggerConfig, AopLoggingAspect
├── controller/     # REST controllers (Patient, Doctor, Department, Ward, PaidService, Admin)
├── dto/
│   ├── request/    # Validated request DTOs
│   └── response/   # Response DTOs (never expose Entity)
├── entity/         # JPA entities + enums (Gender, PatientStatus, Specialty)
├── exception/      # GlobalExceptionHandler, ResourceNotFoundException, BusinessRuleException
├── mapper/         # MapStruct mappers (Entity ↔ DTO)
├── repository/     # Spring Data JPA repositories
├── service/
│   ├── impl/       # Business logic implementations
│   ├── event/      # Domain events, EventPublisher, Kafka consumers
│   └── strategy/   # Discharge strategies (NORMAL / FORCED / TRANSFER)
└── util/
```

---

## REST API Overview

### Patients `/api/patients`
| Method | Path | Description |
|---|---|---|
| POST | `/api/patients` | Register patient |
| GET | `/api/patients/{id}` | Get patient |
| GET | `/api/patients?page=&size=` | List (paginated) |
| PUT | `/api/patients/{id}` | Update |
| DELETE | `/api/patients/{id}` | Soft delete |
| PUT | `/api/patients/{patientId}/assign-doctor/{doctorId}` | Assign doctor |
| GET | `/api/patients/{patientId}/services` | Patient's paid services |

### Doctors `/api/doctors`
| Method | Path | Description |
|---|---|---|
| POST | `/api/doctors` | Create doctor |
| GET | `/api/doctors?specialty=` | List, optional filter |
| GET | `/api/doctors/{id}/patients` | Doctor's patient list |
| PUT | `/api/doctors/{id}` | Update |
| DELETE | `/api/doctors/{id}` | Soft delete |

### Wards `/api/wards`
| Method | Path | Description |
|---|---|---|
| POST | `/api/wards` | Create ward |
| POST | `/api/wards/{wardId}/admit/{patientId}` | Admit patient |
| POST | `/api/wards/{wardId}/discharge/{patientId}` | Discharge from ward |

### Paid Services
| Method | Path | Description |
|---|---|---|
| POST | `/api/paid-services` | Create service |
| POST | `/api/patients/{patientId}/paid-services/{serviceId}` | Assign to patient |
| PATCH | `/api/patients/{patientId}/paid-services/{linkId}/pay` | Mark paid |

### Admin `/api/admin`
| Method | Path | Description |
|---|---|---|
| GET | `/api/admin/reports/ward-occupancy` | Ward occupancy report |
| GET | `/api/admin/reports/paid-services-summary` | Revenue summary |
| POST | `/api/admin/patients/{id}/discharge?dischargeType=NORMAL` | Full discharge |

---

## Architecture Patterns Used

| Pattern | Where |
|---|---|
| **Layered Architecture** | Controller → Service → Repository |
| **DTO / Mapper** | Request/Response DTOs, MapStruct |
| **Strategy** | `DischargeStrategy` (NORMAL / FORCED / TRANSFER) |
| **Domain Events** | `EventPublisher` → Kafka topics |
| **Observer** | Kafka consumers (PatientEventConsumer, AdmissionEventConsumer, PaidServiceEventConsumer) |
| **Transactional Outbox** | `outbox_event` table — idempotency key per event |
| **Repository** | Spring Data JPA |
| **AOP Logging** | `AopLoggingAspect` — logs all service method calls with duration |
| **Builder** | Lombok `@Builder` on all entities |
| **Factory** | `DischargeStrategyFactory` — selects strategy by enum |
| **Dependency Injection** | Spring IoC throughout |

---

## Kafka Topics

| Topic | Payload | Produced by |
|---|---|---|
| `patient-events` | PatientEvent (status change, doctor assigned, discharged) | PatientServiceImpl, AdminServiceImpl |
| `admission-events` | AdmissionEvent (ADMITTED / DISCHARGED) | WardServiceImpl |
| `paid-service-events` | PaidServiceEvent (billing stub) | PaidServiceServiceImpl |
| `doctor-events` | DoctorEvent (created / deleted) | DoctorServiceImpl |
| `department-events` | DepartmentEvent (created / deleted) | DepartmentServiceImpl |

Each producer call:
1. Saves an `outbox_event` row (idempotency key = UUID)
2. Sends a JSON message to Kafka via `KafkaTemplate`
3. Both happen inside the same `@Transactional` JPA transaction

Consumers check `outbox_event.processed` to skip duplicates.

---

## Seed Data (V2 migration)

After `docker-compose up` + app start, the DB contains:

- **2 departments** — Кардиология, Хирургия
- **3 doctors** — Иванов (Cardiologist), Петрова (Surgeon), Сидоров (Therapist)
- **4 wards** — two per department (capacity 2-4)
- **5 patients** — 3 on treatment, 1 discharged, 1 with pending services
- **2 paid services** — ЭКГ расширенное (2500₽), УЗИ сердца (3800₽)

---

## Running Tests

```bash
# Unit tests only (no Docker needed)
mvn test -Dtest="PatientServiceTest,WardServiceTest"

# All tests (requires Docker for Testcontainers)
mvn test
```

---

## Postman Collection

Import `postman_tests/HIS_Collection.postman_collection.json` into Postman.

The collection includes:
- All CRUD operations with automated assertions
- Business rule edge cases (doctor capacity limit, full ward → 409)
- Admin discharge scenarios (NORMAL / FORCED / TRANSFER)
- Error scenario tests (404, 400 validation)

Collection variable `{{baseUrl}}` defaults to `http://localhost:8080`.

---

## Monitoring

| Endpoint | Description |
|---|---|
| `/actuator/health` | App + DB health |
| `/actuator/info` | App version info |
| `/actuator/metrics` | JVM + HTTP metrics |

---

## 🚀 Ideas for Improvement

The following enhancements would make this project production-grade and are good next steps:

### Security & Auth
- [ ] Add **Spring Security + JWT** authentication
- [ ] Role-based access control: `ROLE_DOCTOR`, `ROLE_ADMIN`, `ROLE_NURSE`
- [ ] Audit logging — record who made every change (Spring Data Envers or custom `@CreatedBy`)

### Kafka & Reliability
- [ ] Implement a proper **Transactional Outbox** with a scheduled poller (Debezium CDC or a `@Scheduled` publisher) for guaranteed-at-least-once delivery even if Kafka is briefly down
- [ ] Add **Dead Letter Queue (DLQ)** for failed consumer messages
- [ ] Add **Kafka UI** (e.g. Kafdrop or Kowl) to the docker-compose for visual topic inspection
- [ ] Move to **Schema Registry + Avro** serialization instead of JSON for type safety

### Data & Business Logic
- [ ] Search/filter patients by name, status, doctor, registration date range
- [ ] Medical records / prescriptions — doctor can prescribe medications per visit
- [ ] Visit history — track each outpatient or inpatient visit as a separate entity
- [ ] Billing service — replace the billing stub consumer with a real billing module
- [ ] Notifications — send email/SMS when a patient is discharged or a service is assigned (Spring Mail / Twilio)

### Code Quality
- [ ] Add **Liquibase** as an alternative to Flyway (compare both approaches)
- [ ] API versioning — prefix routes with `/api/v1/` and plan for `/api/v2/`
- [ ] Add **Spring Cache (Redis)** for expensive read-only queries (reports, ward occupancy)
- [ ] Replace `findAll()` in consumers with `findByEventId()` (already done) and add pagination to all admin reports
- [ ] Add **rate limiting** (Bucket4j or Spring Cloud Gateway) to prevent API abuse

### Testing
- [ ] Increase integration test coverage — test the Kafka event flow end-to-end
- [ ] Add **contract tests** (Spring Cloud Contract) between producer and consumer
- [ ] Add **performance tests** with Gatling or k6

### Frontend
- [ ] Replace the vanilla JS SPA with **React** or **Vue 3** for a more maintainable UI
- [ ] Add real-time updates via **WebSocket / Server-Sent Events** (e.g. ward occupancy live dashboard)
- [ ] Patient search with debounced input
- [ ] Charts for admin dashboard (Chart.js — occupancy over time, revenue trends)

### Infrastructure
- [ ] Add **GitHub Actions CI** pipeline: build → test → Docker image push
- [ ] Kubernetes manifests (Deployment, Service, ConfigMap, Secret)
- [ ] Centralised logging with **ELK Stack** (Elasticsearch + Logstash + Kibana)
- [ ] Distributed tracing with **Micrometer + Zipkin** or OpenTelemetry
