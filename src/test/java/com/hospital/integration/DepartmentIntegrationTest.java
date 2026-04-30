package com.hospital.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.dto.request.CreateDepartmentRequest;
import com.hospital.dto.request.UpdateDepartmentRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Интеграционные тесты REST API для управления отделениями.
 *
 * ЧЕМ ИНТЕГРАЦИОННЫЙ ТЕСТ ОТЛИЧАЕТСЯ ОТ UNIT-ТЕСТА:
 *   Unit-тест  — тестирует один класс изолированно (моки вместо зависимостей).
 *   Интеграционный — поднимает ВЕСЬ Spring-контекст: контроллер → сервис → репозиторий → БД.
 *   Здесь мы проверяем, что все слои работают вместе, HTTP-коды правильные,
 *   валидация срабатывает, JSON-ответы содержат нужные поля.
 *
 * АННОТАЦИИ НА КЛАССЕ:
 *
 * @SpringBootTest — поднимает полный Spring Boot контекст приложения.
 *   Это медленнее unit-тестов (~10 сек), но зато тест близок к реальной работе.
 *
 * @AutoConfigureMockMvc — автоматически создаёт MockMvc — инструмент для выполнения
 *   HTTP-запросов к контроллерам без реального HTTP-сервера.
 *
 * @ActiveProfiles("test") — активирует профиль "test", который использует
 *   Testcontainers PostgreSQL (реальная БД в Docker) вместо production-БД.
 *
 * @EmbeddedKafka — поднимает встроенный Kafka-брокер в памяти.
 *   Без него сервисы, публикующие события (EventPublisher), упадут при старте.
 *   Перечисляем все топики, которые используются в приложении.
 *
 * @DirtiesContext — после выполнения класса Spring уничтожает контекст.
 *   Это нужно, чтобы данные одного интеграционного теста не «протекали» в следующий.
 *   Без этого тест «создание отделения» из одного класса мог бы повлиять
 *   на тест «получение всех отделений» из другого.
 *
 * @Import(TestTransactionConfig.class) — подключает тестовую конфигурацию транзакций.
 *   В тестовом окружении некоторые транзакционные настройки требуют переопределения
 *   (например, для совместимости с EmbeddedKafka).
 *
 * @WithMockUser(roles = "ADMIN") — эмулирует авторизованного пользователя с ролью ADMIN
 *   для всех тестов класса. Без этого каждый запрос получал бы HTTP 401 Unauthorized.
 *   Это удобнее, чем вручную добавлять JWT-токен к каждому запросу.
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
                // Одна реплика — достаточно для тестов (в prod обычно 3+)
                "transaction.state.log.replication.factor=1",
                "transaction.state.log.min.isr=1"
        })
@DirtiesContext
@Import(TestTransactionConfig.class)
@WithMockUser(roles = "ADMIN")
class DepartmentIntegrationTest extends AbstractIntegrationTest {

    // MockMvc — «виртуальный HTTP-клиент»: отправляет запросы к контроллеру,
    // не открывая реальный сетевой порт. Работает быстрее, чем TestRestTemplate.
    @Autowired
    private MockMvc mockMvc;

    // ObjectMapper — Jackson-сериализатор: Java-объект ↔ JSON строка.
    // Используем для: сериализации request-DTO → JSON тело запроса,
    //                 десериализации JSON ответа → JsonNode для извлечения id.
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createDepartment_returnsCreated() throws Exception {
        CreateDepartmentRequest request = new CreateDepartmentRequest();
        request.setName("Integration Test Department");
        request.setLocation("Floor 2");

        // mockMvc.perform(post(...)) — выполняет POST-запрос
        // .contentType(MediaType.APPLICATION_JSON) — заголовок Content-Type: application/json
        // .content(objectMapper.writeValueAsString(request)) — тело запроса в JSON
        mockMvc.perform(post("/api/departments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                // andExpect — цепочка проверок ответа
                .andExpect(status().isCreated())                                    // HTTP 201
                .andExpect(jsonPath("$.name").value("Integration Test Department")) // поле name в JSON
                .andExpect(jsonPath("$.id").isNumber());                            // id присвоен БД
    }

    @Test
    void createDepartment_withBlankName_returns400() throws Exception {
        // Пустое имя нарушает @NotBlank на поле name в CreateDepartmentRequest.
        // Bean Validation срабатывает до вызова сервиса — контроллер отвечает 400.
        String invalidJson = "{\"name\": \"\"}";

        mockMvc.perform(post("/api/departments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                // GlobalExceptionHandler возвращает fieldErrors — карту {поле → сообщение}
                .andExpect(jsonPath("$.fieldErrors").exists());
    }

    @Test
    void getDepartment_whenNotFound_returns404() throws Exception {
        // ID 99999 заведомо не существует в тестовой БД
        mockMvc.perform(get("/api/departments/99999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAllDepartments_returnsArray() throws Exception {
        // GET без ID — возвращает весь список (без пагинации, т.к. отделений мало)
        mockMvc.perform(get("/api/departments"))
                .andExpect(status().isOk())
                // isArray() — проверяем, что корень JSON-ответа является массивом []
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void createAndGetDepartment_roundtrip() throws Exception {
        // ROUNDTRIP-тест: создаём → читаем → проверяем, что данные сохранились корректно.
        // Это классический интеграционный сценарий «end-to-end через API».
        CreateDepartmentRequest request = new CreateDepartmentRequest();
        request.setName("Neurology");
        request.setDescription("Brain and nervous system");

        // Сохраняем тело ответа как строку, чтобы извлечь из него id
        String responseBody = mockMvc.perform(post("/api/departments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        // readTree() — парсит JSON в дерево узлов.
        // .get("id").asLong() — безопасно извлекаем числовое значение поля id.
        Long id = objectMapper.readTree(responseBody).get("id").asLong();

        // GET по полученному id — должен вернуть то, что мы создали
        mockMvc.perform(get("/api/departments/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Neurology"))
                .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    void updateDepartment_changesName() throws Exception {
        // Создаём отделение
        CreateDepartmentRequest createRequest = new CreateDepartmentRequest();
        createRequest.setName("Old Name");

        String createBody = mockMvc.perform(post("/api/departments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(createBody).get("id").asLong();

        // Обновляем название через PUT
        UpdateDepartmentRequest updateRequest = new UpdateDepartmentRequest();
        updateRequest.setName("Updated Name");

        mockMvc.perform(put("/api/departments/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                // Ответ содержит новое название — убеждаемся, что обновление прошло
                .andExpect(jsonPath("$.name").value("Updated Name"));
    }

    @Test
    void deleteDepartment_thenGetReturns404() throws Exception {
        // Тест проверяет сразу DELETE и последующий GET:
        // физическое удаление (не soft delete!) + корректный 404 после удаления.
        CreateDepartmentRequest request = new CreateDepartmentRequest();
        request.setName("To Be Deleted");

        String body = mockMvc.perform(post("/api/departments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(body).get("id").asLong();

        // DELETE → 204 No Content (удалили, тело ответа не нужно)
        mockMvc.perform(delete("/api/departments/" + id))
                .andExpect(status().isNoContent());

        // GET после DELETE → 404 Not Found (объект удалён физически)
        mockMvc.perform(get("/api/departments/" + id))
                .andExpect(status().isNotFound());
    }
}
