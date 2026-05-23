package com.hospital.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Интеграционные тесты для MedicalController.
 *
 * Покрываемые сценарии:
 *   - Создание документа (ROLE_DOCTOR): успех (201), нет прав (403), нет токена (401), невалидные поля (400).
 *   - Создание заметки (ROLE_DOCTOR): успех (201).
 *   - Просмотр документов пациента (ROLE_DOCTOR): успех (200).
 *   - Полная история пациента (ROLE_DOCTOR): успех (200).
 *   - Мои документы (ROLE_CLIENT): успех (200).
 *   - Моя история (ROLE_CLIENT): успех (200).
 *
 * Особенности настройки тестового окружения:
 *
 *   1. Doctor-User linkage в @BeforeEach через JdbcTemplate:
 *      DataInitializer запускается ПОСЛЕ Flyway-миграций. UPDATE в V6 (doctor SET user_id=...)
 *      выполняется на этапе миграции, когда записи users ещё не существуют → это no-op.
 *      В тестах нет реального DataInitializer-порядка, поэтому привязку doctor1 ↔ профиль врача
 *      выполняем вручную в @BeforeEach через JdbcTemplate.
 *
 *   2. Первый активный пациент — из V5-seed-данных:
 *      Тесты создания документов/заметок требуют существующего patientId.
 *      Используем SELECT id FROM patient WHERE active=true LIMIT 1 — детерминированный выбор.
 *
 *   3. Client-Patient linkage не настраивается принудительно:
 *      getMyDocuments / getMyHistory возвращают пустые списки (HTTP 200), если
 *      клиент не связан с пациентом. Тесты проверяют только статус ответа, не содержимое.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1,
        topics = {"patient-events", "admission-events", "paid-service-events",
                  "doctor-events", "department-events",
                  "patient-events.DLT", "admission-events.DLT", "paid-service-events.DLT",
                  "doctor-events.DLT", "department-events.DLT"},
        brokerProperties = {
                "transaction.state.log.replication.factor=1",
                "transaction.state.log.min.isr=1"
        })
@DirtiesContext
@Import(TestTransactionConfig.class)
class MedicalIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * JdbcTemplate — прямой доступ к БД для настройки тестового состояния.
     * Используется только в @BeforeEach, а не в самих тестах.
     */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ─── Состояние, инициализируемое перед каждым тестом ─────────────────────

    /**
     * ID первого активного пациента из seed-данных (V5-миграция).
     * Используется как patientId в запросах создания документов и заметок.
     */
    private Long testPatientId;

    /**
     * @BeforeEach выполняется перед каждым тестом этого класса.
     *
     * Два действия:
     *   1. Привязываем doctor1 к профилю врача «Иванов Сергей Петрович».
     *      Без этого сервис выбросит ResourceNotFoundException ("Профиль врача не найден")
     *      при каждом вызове createDocument/createNote с токеном doctor1.
     *
     *   2. Находим ID первого активного пациента для использования в запросах.
     *      Опираемся на seed-данные V5 — они всегда присутствуют в тестовой БД.
     */
    @BeforeEach
    void setUp() {
        // Привязываем учётную запись doctor1 к профилю врача через прямой SQL.
        // Эквивалент: UPDATE doctor SET user_id = <id_doctor1> WHERE full_name = 'Иванов Сергей Петрович'.
        // Subquery (SELECT id FROM users WHERE username = 'doctor1') гарантирует независимость
        // от конкретного значения id, которое может меняться между запусками Testcontainers.
        jdbcTemplate.update(
                "UPDATE doctor SET user_id = (SELECT id FROM users WHERE username = 'doctor1') " +
                "WHERE full_name = 'Иванов Сергей Петрович'");

        // Получаем ID первого активного пациента из seed-данных.
        testPatientId = jdbcTemplate.queryForObject(
                "SELECT id FROM patient WHERE active = true ORDER BY id LIMIT 1", Long.class);
    }

    // ─── Вспомогательный метод ────────────────────────────────────────────────

    /**
     * Выполняет логин и возвращает JWT-токен.
     * Единая точка для получения токена, чтобы не дублировать логику во всех тестах.
     */
    private String login(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("username", username, "password", password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).get("token").asText();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/medical/documents — createDocument (ROLE_DOCTOR)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Врач создаёт медицинский документ для пациента — HTTP 201 Created.
     * Ответ должен содержать id, type, title, issuedAt.
     */
    @Test
    void createDocument_asDoctor_returns201WithDocumentData() throws Exception {
        String doctorToken = login("doctor1", "doctor123");

        mockMvc.perform(post("/api/medical/documents")
                        .header("Authorization", "Bearer " + doctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "patientId", testPatientId,
                                "type", "PRESCRIPTION",
                                "title", "Рецепт: Амоксициллин 500мг",
                                "content", "Принимать по 500мг 3 раза в день в течение 7 дней"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.type").value("PRESCRIPTION"))
                .andExpect(jsonPath("$.typeLabel").value("Рецепт"))
                .andExpect(jsonPath("$.title").value("Рецепт: Амоксициллин 500мг"))
                .andExpect(jsonPath("$.issuedAt").isNotEmpty())
                .andExpect(jsonPath("$.active").value(true));
    }

    /**
     * Клиент не может создавать медицинские документы — 403 Forbidden.
     * POST /api/medical/documents разрешён только ROLE_DOCTOR.
     */
    @Test
    void createDocument_asClient_returns403() throws Exception {
        String clientToken = login("client1", "client123");

        mockMvc.perform(post("/api/medical/documents")
                        .header("Authorization", "Bearer " + clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "patientId", testPatientId,
                                "type", "PRESCRIPTION",
                                "title", "Рецепт",
                                "content", "Содержание"))))
                .andExpect(status().isForbidden());
    }

    /**
     * Запрос без токена → 401 Unauthorized.
     */
    @Test
    void createDocument_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/medical/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "patientId", testPatientId,
                                "type", "PRESCRIPTION",
                                "title", "Рецепт",
                                "content", "Содержание"))))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Запрос с отсутствующим обязательным полем (title) → 400 Bad Request.
     * Bean Validation (@NotBlank на title) срабатывает до вызова сервиса.
     */
    @Test
    void createDocument_withMissingTitle_returns400() throws Exception {
        String doctorToken = login("doctor1", "doctor123");

        mockMvc.perform(post("/api/medical/documents")
                        .header("Authorization", "Bearer " + doctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "patientId", testPatientId,
                                "type", "PRESCRIPTION",
                                // title отсутствует → @NotBlank провалит валидацию
                                "content", "Содержание"))))
                .andExpect(status().isBadRequest());
    }

    /**
     * Запрос без patientId → 400 Bad Request (@NotNull на patientId).
     */
    @Test
    void createDocument_withMissingPatientId_returns400() throws Exception {
        String doctorToken = login("doctor1", "doctor123");

        mockMvc.perform(post("/api/medical/documents")
                        .header("Authorization", "Bearer " + doctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "type", "PRESCRIPTION",
                                "title", "Рецепт",
                                "content", "Содержание"))))
                .andExpect(status().isBadRequest());
    }

    /**
     * Врач создаёт документ с validUntil (ограниченный срок действия).
     * Поле опциональное, но когда передано — должно присутствовать в ответе.
     */
    @Test
    void createDocument_withValidUntil_returns201WithExpiryDate() throws Exception {
        String doctorToken = login("doctor1", "doctor123");

        mockMvc.perform(post("/api/medical/documents")
                        .header("Authorization", "Bearer " + doctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "patientId", testPatientId,
                                "type", "SICK_LEAVE",
                                "title", "Больничный лист",
                                "content", "Освобождён от работы",
                                "validUntil", "2030-12-31"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.validUntil").value("2030-12-31"))
                .andExpect(jsonPath("$.typeLabel").value("Больничный лист"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/medical/notes — createNote (ROLE_DOCTOR)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Врач создаёт заметку с visibleToClient=false (скрытую от пациента) — HTTP 201.
     * Тип DIAGNOSIS, флаг видимости проверяется в ответе.
     */
    @Test
    void createNote_asDoctor_hiddenNote_returns201() throws Exception {
        String doctorToken = login("doctor1", "doctor123");

        mockMvc.perform(post("/api/medical/notes")
                        .header("Authorization", "Bearer " + doctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "patientId", testPatientId,
                                "type", "DIAGNOSIS",
                                "content", "ОРЗ, средней степени тяжести. Осложнений нет.",
                                "visibleToClient", false))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.type").value("DIAGNOSIS"))
                .andExpect(jsonPath("$.typeLabel").value("Диагноз"))
                .andExpect(jsonPath("$.visibleToClient").value(false))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    /**
     * Врач создаёт заметку видимую пациенту (visibleToClient=true) — HTTP 201.
     */
    @Test
    void createNote_asDoctor_visibleNote_returns201() throws Exception {
        String doctorToken = login("doctor1", "doctor123");

        mockMvc.perform(post("/api/medical/notes")
                        .header("Authorization", "Bearer " + doctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "patientId", testPatientId,
                                "type", "OBSERVATION",
                                "content", "Состояние улучшается, температура нормализовалась.",
                                "visibleToClient", true))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.visibleToClient").value(true))
                .andExpect(jsonPath("$.typeLabel").value("Наблюдение"));
    }

    /**
     * Клиент не может создавать заметки — 403 Forbidden.
     * POST /api/medical/notes разрешён только ROLE_DOCTOR.
     */
    @Test
    void createNote_asClient_returns403() throws Exception {
        String clientToken = login("client1", "client123");

        mockMvc.perform(post("/api/medical/notes")
                        .header("Authorization", "Bearer " + clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "patientId", testPatientId,
                                "type", "NOTE",
                                "content", "Заметка"))))
                .andExpect(status().isForbidden());
    }

    /**
     * Заметка с пустым content → 400 Bad Request (@NotBlank).
     */
    @Test
    void createNote_withBlankContent_returns400() throws Exception {
        String doctorToken = login("doctor1", "doctor123");

        mockMvc.perform(post("/api/medical/notes")
                        .header("Authorization", "Bearer " + doctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "patientId", testPatientId,
                                "type", "NOTE",
                                "content", "   "))))
                .andExpect(status().isBadRequest());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/medical/documents/patient/{patientId} — getPatientDocuments (ROLE_DOCTOR)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Врач получает список документов конкретного пациента — HTTP 200 с массивом.
     * Список может быть пустым, если для пациента ещё нет документов — это нормально.
     */
    @Test
    void getPatientDocuments_asDoctor_returns200() throws Exception {
        String doctorToken = login("doctor1", "doctor123");

        mockMvc.perform(get("/api/medical/documents/patient/{patientId}", testPatientId)
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    /**
     * Клиент не может просматривать документы конкретного пациента по patientId — 403.
     * Для клиента предназначен эндпоинт GET /api/medical/documents/my.
     */
    @Test
    void getPatientDocuments_asClient_returns403() throws Exception {
        String clientToken = login("client1", "client123");

        mockMvc.perform(get("/api/medical/documents/patient/{patientId}", testPatientId)
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isForbidden());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/medical/history/patient/{patientId} — getPatientHistory (ROLE_DOCTOR)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Врач получает полную историю пациента (заметки + документы) — HTTP 200.
     * Ответ содержит patientName, массивы notes и documents.
     */
    @Test
    void getPatientHistory_asDoctor_returns200() throws Exception {
        String doctorToken = login("doctor1", "doctor123");

        mockMvc.perform(get("/api/medical/history/patient/{patientId}", testPatientId)
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patientId").value(testPatientId))
                .andExpect(jsonPath("$.patientName").isNotEmpty())
                .andExpect(jsonPath("$.notes").isArray())
                .andExpect(jsonPath("$.documents").isArray());
    }

    /**
     * Несуществующий пациент → 404 Not Found (ResourceNotFoundException из сервиса).
     */
    @Test
    void getPatientHistory_withNonExistentPatient_returns404() throws Exception {
        String doctorToken = login("doctor1", "doctor123");

        mockMvc.perform(get("/api/medical/history/patient/999999")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isNotFound());
    }

    /**
     * Клиент не может просматривать историю чужого пациента по patientId — 403.
     */
    @Test
    void getPatientHistory_asClient_returns403() throws Exception {
        String clientToken = login("client1", "client123");

        mockMvc.perform(get("/api/medical/history/patient/{patientId}", testPatientId)
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isForbidden());
    }

    /**
     * Запрос без токена → 401.
     */
    @Test
    void getPatientHistory_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/medical/history/patient/{patientId}", testPatientId))
                .andExpect(status().isUnauthorized());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/medical/documents/my — getMyDocuments (ROLE_CLIENT)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Клиент запрашивает свои документы — HTTP 200 с массивом.
     * Список пустой, так как client1 не привязан к пациенту в тестовой БД.
     * Тест проверяет статус и формат ответа, не содержимое.
     */
    @Test
    void getMyDocuments_asClient_returns200() throws Exception {
        String clientToken = login("client1", "client123");

        mockMvc.perform(get("/api/medical/me/documents")
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    /**
     * Врач не может использовать клиентский эндпоинт — 403 Forbidden.
     * GET /api/medical/documents/my разрешён только ROLE_CLIENT.
     */
    @Test
    void getMyDocuments_asDoctor_returns403() throws Exception {
        String doctorToken = login("doctor1", "doctor123");

        mockMvc.perform(get("/api/medical/me/documents")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden());
    }

    /**
     * Запрос без токена → 401.
     */
    @Test
    void getMyDocuments_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/medical/me/documents"))
                .andExpect(status().isUnauthorized());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/medical/history/my — getMyHistory (ROLE_CLIENT)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Клиент запрашивает свою историю болезни — HTTP 200.
     * Ответ содержит поля notes и documents (могут быть пустыми).
     * patientId отсутствует в ответе для клиента — information hiding.
     */
    @Test
    void getMyHistory_asClient_returns200() throws Exception {
        String clientToken = login("client1", "client123");

        mockMvc.perform(get("/api/medical/me/history")
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes").isArray())
                .andExpect(jsonPath("$.documents").isArray());
    }

    /**
     * Врач не может использовать клиентский эндпоинт истории — 403 Forbidden.
     */
    @Test
    void getMyHistory_asDoctor_returns403() throws Exception {
        String doctorToken = login("doctor1", "doctor123");

        mockMvc.perform(get("/api/medical/me/history")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden());
    }

    /**
     * Запрос без токена → 401.
     */
    @Test
    void getMyHistory_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/medical/me/history"))
                .andExpect(status().isUnauthorized());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Сквозной тест: создать документ → проверить в списке
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * End-to-end тест: врач создаёт документ → он появляется в списке документов пациента.
     *
     * Шаги:
     *   1. POST /api/medical/documents — создаём документ, запоминаем id.
     *   2. GET  /api/medical/documents/patient/{patientId} — проверяем, что документ присутствует.
     *
     * Это подтверждает, что созданный документ действительно сохранён в БД
     * и доступен через отдельный GET-запрос.
     */
    @Test
    void createDocument_thenGetPatientDocuments_containsNewDocument() throws Exception {
        String doctorToken = login("doctor1", "doctor123");

        // Шаг 1: создаём документ.
        String createBody = mockMvc.perform(post("/api/medical/documents")
                        .header("Authorization", "Bearer " + doctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "patientId", testPatientId,
                                "type", "REFERRAL",
                                "title", "Направление к кардиологу",
                                "content", "Направляется для консультации"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Long createdId = objectMapper.readTree(createBody).get("id").asLong();

        // Шаг 2: проверяем, что документ появился в списке.
        String listBody = mockMvc.perform(
                        get("/api/medical/documents/patient/{patientId}", testPatientId)
                                .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        // В массиве должен быть элемент с id созданного документа.
        boolean found = false;
        for (var node : objectMapper.readTree(listBody)) {
            if (node.get("id").asLong() == createdId) {
                found = true;
                break;
            }
        }
        org.assertj.core.api.Assertions.assertThat(found)
                .as("Созданный документ (id=%d) должен присутствовать в списке", createdId)
                .isTrue();
    }
}
