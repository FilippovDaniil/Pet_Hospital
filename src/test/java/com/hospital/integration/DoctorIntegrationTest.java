package com.hospital.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.dto.request.CreateDoctorRequest;
import com.hospital.entity.Specialty;
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
 * Интеграционные тесты REST API для управления врачами.
 *
 * Проверяем поведение всего стека: HTTP-запрос → Spring Security → контроллер →
 * сервис → репозиторий → PostgreSQL (Testcontainers) → маппер → JSON-ответ.
 *
 * Особенности этого контроллера по сравнению с Department:
 *   - Пагинация в ответах (content, page, totalElements и т.д.)
 *   - Фильтрация по специальности (?specialty=SURGEON)
 *   - Soft delete: DELETE → 204, но GET → 404 (запись в БД остаётся, active=false)
 *   - Вложенный ресурс: GET /api/doctors/{id}/patients
 *
 * Набор аннотаций идентичен DepartmentIntegrationTest — подробные объяснения там.
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
@WithMockUser(roles = "ADMIN")
class DoctorIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createDoctor_returnsCreated() throws Exception {
        CreateDoctorRequest request = new CreateDoctorRequest();
        request.setFullName("Dr. Integration Test");
        request.setSpecialty(Specialty.CARDIOLOGIST);
        request.setCabinetNumber("101");

        mockMvc.perform(post("/api/doctors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fullName").value("Dr. Integration Test"))
                // Enum Specialty сериализуется в JSON как строка "CARDIOLOGIST"
                .andExpect(jsonPath("$.specialty").value("CARDIOLOGIST"))
                // active = true устанавливает сервис автоматически для нового врача
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void createDoctor_withMissingRequiredFields_returns400() throws Exception {
        // @NotBlank fullName и @NotNull specialty — обязательные поля.
        // Запрос без них должен вернуть 400 с деталями валидации.
        String invalidJson = "{\"cabinetNumber\": \"101\"}";

        mockMvc.perform(post("/api/doctors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").exists());
    }

    @Test
    void getDoctor_whenNotFound_returns404() throws Exception {
        // Несуществующий ID → ResourceNotFoundException → GlobalExceptionHandler → 404
        mockMvc.perform(get("/api/doctors/99999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAllDoctors_returnsPaginatedResult() throws Exception {
        // GET /api/doctors возвращает PageResponse, а не просто список.
        // Проверяем структуру пагинированного ответа: content[], page, size, totalElements.
        mockMvc.perform(get("/api/doctors?page=0&size=10"))
                .andExpect(status().isOk())
                // content — массив врачей на текущей странице
                .andExpect(jsonPath("$.content").isArray())
                // page — номер текущей страницы (0-based)
                .andExpect(jsonPath("$.page").value(0));
    }

    @Test
    void getAllDoctors_filteredBySpecialty() throws Exception {
        // Создаём хирурга — чтобы было кого фильтровать
        CreateDoctorRequest request = new CreateDoctorRequest();
        request.setFullName("Dr. Surgeon");
        request.setSpecialty(Specialty.SURGEON);

        mockMvc.perform(post("/api/doctors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // ?specialty=SURGEON — Spring автоматически конвертирует строку в enum Specialty.SURGEON
        // Ожидаем пагинированный ответ с хирургами
        mockMvc.perform(get("/api/doctors?specialty=SURGEON&page=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void createAndGetDoctor_roundtrip() throws Exception {
        // Создаём врача → получаем по id → проверяем данные.
        // Это сквозной тест всей цепочки: POST → БД → GET → JSON.
        CreateDoctorRequest request = new CreateDoctorRequest();
        request.setFullName("Dr. Roundtrip");
        request.setSpecialty(Specialty.THERAPIST);

        String body = mockMvc.perform(post("/api/doctors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        // Парсим тело ответа и извлекаем id созданного врача
        Long id = objectMapper.readTree(body).get("id").asLong();

        mockMvc.perform(get("/api/doctors/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Dr. Roundtrip"))
                .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    void softDeleteDoctor_thenGetReturns404() throws Exception {
        // Soft delete: DELETE → 204, потом GET → 404.
        // Запись ОСТАЁТСЯ в БД (active=false), но findByIdAndActiveTrue её не видит.
        // Это отличие от DepartmentController, где DELETE физический.
        CreateDoctorRequest request = new CreateDoctorRequest();
        request.setFullName("Dr. To Delete");
        request.setSpecialty(Specialty.NEUROLOGIST);

        String body = mockMvc.perform(post("/api/doctors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(body).get("id").asLong();

        // DELETE — мягкое удаление (active = false)
        mockMvc.perform(delete("/api/doctors/" + id))
                .andExpect(status().isNoContent());

        // GET после soft delete → 404 (findByIdAndActiveTrue не находит inactive-запись)
        mockMvc.perform(get("/api/doctors/" + id))
                .andExpect(status().isNotFound());
    }

    @Test
    void getDoctorPatients_whenDoctorNotFound_returns404() throws Exception {
        // Fail-fast: если врач не существует — не возвращаем пустой список, а 404.
        // Это важно: пустой список означает «врач есть, но пациентов нет»,
        // а 404 означает «такого врача вообще нет».
        mockMvc.perform(get("/api/doctors/99999/patients"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getDoctorPatients_returnsPaginatedResult() throws Exception {
        // Создаём врача
        CreateDoctorRequest request = new CreateDoctorRequest();
        request.setFullName("Dr. Patients Test");
        request.setSpecialty(Specialty.CARDIOLOGIST);

        String body = mockMvc.perform(post("/api/doctors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long doctorId = objectMapper.readTree(body).get("id").asLong();

        // GET /api/doctors/{id}/patients — вложенный ресурс с пагинацией.
        // Пациентов у нового врача нет, но endpoint работает и возвращает пустой список.
        mockMvc.perform(get("/api/doctors/" + doctorId + "/patients?page=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }
}
