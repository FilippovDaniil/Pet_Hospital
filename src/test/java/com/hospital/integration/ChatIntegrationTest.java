package com.hospital.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Интеграционные тесты для ChatController.
 *
 * Стратегия тестирования:
 *   - Используется реальный Spring-контекст с PostgreSQL через Testcontainers.
 *   - Авторизация выполняется через настоящие JWT-токены (login → token → запрос).
 *   - Тесты покрывают:
 *       1. Разграничение доступа по ролям (CLIENT / ADMIN / DOCTOR).
 *       2. Идемпотентность getOrCreate: повторный вызов возвращает ту же комнату.
 *       3. Отправка сообщения + polling: последовательность send → poll.
 *       4. Защита без токена (401 Unauthorized).
 *
 * @DirtiesContext — каждый тест-класс стартует с чистым Spring-контекстом.
 *   Это необходимо из-за EmbeddedKafka и состояния БД между тест-классами.
 *
 * @Import(TestTransactionConfig.class) — переопределяет @Primary TransactionManager
 *   для тестов, где EmbeddedKafka отключает Kafka-транзакции.
 *
 * Тестовые пользователи созданы DataInitializer:
 *   admin / admin123   → ROLE_ADMIN
 *   doctor1 / doctor123 → ROLE_DOCTOR
 *   client1 / client123 → ROLE_CLIENT
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
class ChatIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ─── Вспомогательный метод ────────────────────────────────────────────────

    /**
     * Выполняет логин и возвращает JWT-токен.
     * Повторяет паттерн AuthIntegrationTest.login() для согласованности тестов.
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
    // POST /api/chat/support — getOrCreateSupportRoom (только ROLE_CLIENT)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Клиент открывает чат поддержки — возвращается HTTP 200 с данными комнаты.
     * Тип комнаты должен быть SUPPORT, clientUserId — совпадать с id клиента.
     */
    @Test
    void getOrCreateSupportRoom_asClient_returns200WithRoomData() throws Exception {
        String token = login("client1", "client123");

        mockMvc.perform(post("/api/chat/support")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("SUPPORT"))
                .andExpect(jsonPath("$.id").isNumber());
    }

    /**
     * Запрос без токена → 401 Unauthorized.
     * JWT-фильтр блокирует запрос до проверки роли.
     */
    @Test
    void getOrCreateSupportRoom_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/chat/support"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Администратор пытается открыть чат поддержки — 403 Forbidden.
     * POST /api/chat/support разрешён только ROLE_CLIENT (см. SecurityConfig).
     * Администратор читает все комнаты через GET /api/chat/support.
     */
    @Test
    void getOrCreateSupportRoom_asAdmin_returns403() throws Exception {
        String adminToken = login("admin", "admin123");

        mockMvc.perform(post("/api/chat/support")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
    }

    /**
     * Идемпотентность getOrCreate: два вызова подряд возвращают одну и ту же комнату.
     * Это ключевое свойство паттерна «get-or-create»: БД не накапливает дубли.
     * Гарантируется частичным уникальным индексом uq_support_room в V6-миграции.
     */
    @Test
    void getOrCreateSupportRoom_calledTwice_returnsTheSameRoomBothTimes() throws Exception {
        String token = login("client2", "client123");

        // Первый вызов — комната создаётся.
        MvcResult first = mockMvc.perform(post("/api/chat/support")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        // Второй вызов — возвращается та же комната.
        MvcResult second = mockMvc.perform(post("/api/chat/support")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        // id комнат должны совпадать — дубль не создан.
        Long firstId  = objectMapper.readTree(first.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("id").asLong();
        Long secondId = objectMapper.readTree(second.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("id").asLong();
        assertThat(firstId).isEqualTo(secondId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/chat/support — getAllSupportRooms (только ROLE_ADMIN)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Администратор получает список всех комнат поддержки — HTTP 200.
     */
    @Test
    void getAllSupportRooms_asAdmin_returns200() throws Exception {
        String adminToken = login("admin", "admin123");

        mockMvc.perform(get("/api/chat/support")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    /**
     * Клиент не может получить список всех чатов поддержки — 403 Forbidden.
     * GET /api/chat/support зарезервирован только для администраторов.
     */
    @Test
    void getAllSupportRooms_asClient_returns403() throws Exception {
        String clientToken = login("client1", "client123");

        mockMvc.perform(get("/api/chat/support")
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isForbidden());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Отправка сообщения + polling
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Последовательный тест: клиент создаёт комнату → отправляет сообщение → опрашивает новые.
     *
     * Шаги:
     *   1. POST /api/chat/support — получаем roomId.
     *   2. POST /api/chat/rooms/{roomId}/messages — отправляем сообщение (HTTP 201).
     *   3. GET  /api/chat/rooms/{roomId}/messages/poll?sinceId=0 — убеждаемся, что сообщение видно.
     *
     * Этот тест проверяет полный цикл чата «без WebSocket»:
     * сообщение сохраняется в БД и становится доступным через polling.
     */
    @Test
    void sendMessage_thenPoll_returnsNewMessage() throws Exception {
        String clientToken = login("client1", "client123");

        // Шаг 1: создаём/получаем комнату поддержки.
        MvcResult roomResult = mockMvc.perform(post("/api/chat/support")
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andReturn();
        Long roomId = objectMapper.readTree(roomResult.getResponse().getContentAsString())
                .get("id").asLong();

        // Шаг 2: отправляем сообщение.
        String messageContent = "Здравствуйте, у меня вопрос по записи";
        mockMvc.perform(post("/api/chat/rooms/{roomId}/messages", roomId)
                        .header("Authorization", "Bearer " + clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("content", messageContent))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value(messageContent));

        // Шаг 3: опрашиваем новые сообщения начиная с id=0 (все сообщения).
        // getContentAsString(UTF_8) — явно указываем кодировку, иначе на Windows
        // getContentAsString() использует системную кодировку (CP-1252) и кириллица искажается.
        MvcResult pollResult = mockMvc.perform(
                        get("/api/chat/rooms/{roomId}/messages/poll", roomId)
                                .header("Authorization", "Bearer " + clientToken)
                                .param("sinceId", "0"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode messages = objectMapper.readTree(
                pollResult.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(messages.isArray()).isTrue();
        assertThat(messages.size()).isGreaterThanOrEqualTo(1);
        assertThat(messages.get(0).get("content").asText()).isEqualTo(messageContent);
    }

    /**
     * Отправка пустого сообщения → HTTP 400 Bad Request.
     * @NotBlank на поле content в SendMessageRequest отклоняет пустую строку.
     */
    @Test
    void sendMessage_withBlankContent_returns400() throws Exception {
        String clientToken = login("client1", "client123");

        // Получаем roomId.
        MvcResult roomResult = mockMvc.perform(post("/api/chat/support")
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andReturn();
        Long roomId = objectMapper.readTree(roomResult.getResponse().getContentAsString())
                .get("id").asLong();

        // Пустое содержание → 400.
        mockMvc.perform(post("/api/chat/rooms/{roomId}/messages", roomId)
                        .header("Authorization", "Bearer " + clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", "   "))))
                .andExpect(status().isBadRequest());
    }

    /**
     * Запрос сообщений без токена → 401 Unauthorized.
     */
    @Test
    void getMessages_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/chat/rooms/1/messages"))
                .andExpect(status().isUnauthorized());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/chat/doctor/rooms — getDoctorRooms (только ROLE_DOCTOR)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Врач получает список своих чатов — HTTP 200 с массивом.
     * Список может быть пустым: врач ещё не вступил ни в один чат — это нормально.
     */
    @Test
    void getDoctorRooms_asDoctor_returns200() throws Exception {
        String doctorToken = login("doctor1", "doctor123");

        mockMvc.perform(get("/api/chat/doctor/rooms")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    /**
     * Клиент не может просматривать список чатов врача — 403 Forbidden.
     * GET /api/chat/doctor/rooms разрешён только ROLE_DOCTOR.
     */
    @Test
    void getDoctorRooms_asClient_returns403() throws Exception {
        String clientToken = login("client1", "client123");

        mockMvc.perform(get("/api/chat/doctor/rooms")
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isForbidden());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/chat/my-rooms — getMyRooms (только ROLE_CLIENT)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Клиент получает список всех своих чатов (поддержка + врачи) — HTTP 200.
     * Список может быть пустым, если клиент ещё не открывал ни одного чата.
     */
    @Test
    void getMyRooms_asClient_returns200() throws Exception {
        String clientToken = login("client1", "client123");

        mockMvc.perform(get("/api/chat/my-rooms")
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    /**
     * Врач не может просматривать «мои комнаты» клиента — 403 Forbidden.
     * GET /api/chat/my-rooms разрешён только ROLE_CLIENT.
     */
    @Test
    void getMyRooms_asDoctor_returns403() throws Exception {
        String doctorToken = login("doctor1", "doctor123");

        mockMvc.perform(get("/api/chat/my-rooms")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden());
    }

    /**
     * Запрос my-rooms без токена → 401.
     */
    @Test
    void getMyRooms_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/chat/my-rooms"))
                .andExpect(status().isUnauthorized());
    }
}
