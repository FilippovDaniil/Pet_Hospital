package com.hospital.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.dto.request.CreatePaidServiceRequest;
import com.hospital.dto.request.CreatePatientRequest;
import com.hospital.entity.Gender;
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

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Интеграционные тесты REST API для управления платными услугами.
 *
 * Этот контроллер охватывает два разных URL-пространства:
 *   /api/paid-services          — справочник услуг (CRUD)
 *   /api/patients/{id}/paid-services — назначение и оплата услуг пациентам
 *
 * Тестируем полный бизнес-процесс:
 *   1. Создаём платную услугу в справочнике
 *   2. Создаём пациента
 *   3. Назначаем услугу пациенту (paid=false)
 *   4. Отмечаем услугу оплаченной (paid=true)
 *
 * Два вспомогательных метода createService() и createPatient() выносят
 * повторяющийся код создания данных — это DRY (Don't Repeat Yourself).
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
class PaidServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Вспомогательный метод: создаёт платную услугу через API и возвращает её id.
     * Используется в тестах, которым нужна реально существующая услуга.
     *
     * throws Exception — MockMvc выбрасывает checked исключения, поэтому метод их пробрасывает.
     */
    private Long createService(String name, BigDecimal price) throws Exception {
        CreatePaidServiceRequest request = new CreatePaidServiceRequest();
        request.setName(name);
        request.setPrice(price);

        String body = mockMvc.perform(post("/api/paid-services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        // Извлекаем id из JSON-ответа: {"id": 5, "name": "...", ...}
        return objectMapper.readTree(body).get("id").asLong();
    }

    /**
     * Вспомогательный метод: создаёт пациента через API и возвращает его id.
     *
     * snils передаётся параметром — каждый тест использует уникальный СНИЛС,
     * чтобы избежать конфликта уникального ограничения в БД (unique = true на поле snils).
     */
    private Long createPatient(String snils) throws Exception {
        CreatePatientRequest request = new CreatePatientRequest();
        request.setFullName("Test Patient");
        request.setBirthDate(LocalDate.of(2015, 5, 10));
        request.setGender(Gender.MALE);
        request.setSnils(snils); // уникальный СНИЛС для каждого теста

        String body = mockMvc.perform(post("/api/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body).get("id").asLong();
    }

    @Test
    void createPaidService_returnsCreated() throws Exception {
        CreatePaidServiceRequest request = new CreatePaidServiceRequest();
        request.setName("MRI Scan");
        request.setPrice(new BigDecimal("8000.00"));
        request.setDescription("Full body MRI");

        mockMvc.perform(post("/api/paid-services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("MRI Scan"))
                // BigDecimal 8000.00 сериализуется Jackson в число 8000.0
                .andExpect(jsonPath("$.price").value(8000.00))
                .andExpect(jsonPath("$.id").isNumber());
    }

    @Test
    void createPaidService_withMissingFields_returns400() throws Exception {
        // Отсутствует обязательное поле name (@NotBlank) и price (@NotNull)
        String invalidJson = "{\"description\": \"No name or price\"}";

        mockMvc.perform(post("/api/paid-services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").exists());
    }

    @Test
    void createPaidService_withZeroPrice_returns400() throws Exception {
        // @DecimalMin(value = "0.01") — цена должна быть хотя бы 1 копейка.
        // BigDecimal.ZERO (0.00) нарушает это ограничение → 400 Bad Request.
        CreatePaidServiceRequest request = new CreatePaidServiceRequest();
        request.setName("Free Service");
        request.setPrice(BigDecimal.ZERO);

        mockMvc.perform(post("/api/paid-services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getPaidService_whenNotFound_returns404() throws Exception {
        mockMvc.perform(get("/api/paid-services/99999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAllPaidServices_returnsPaginatedResult() throws Exception {
        mockMvc.perform(get("/api/paid-services?page=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").value(0));
    }

    @Test
    void assignServiceToPatient_returnsCreated() throws Exception {
        // Полный сценарий: создаём услугу и пациента, назначаем, проверяем статус
        Long serviceId = createService("Ultrasound", new BigDecimal("3000.00"));
        Long patientId = createPatient("555-666-777 88");

        // POST /api/patients/{patientId}/paid-services/{serviceId} — назначение услуги
        mockMvc.perform(post("/api/patients/" + patientId + "/paid-services/" + serviceId))
                .andExpect(status().isCreated())
                // Услуга назначена, но ещё НЕ оплачена
                .andExpect(jsonPath("$.paid").value(false));
    }

    @Test
    void assignServiceToPatient_whenPatientNotFound_returns404() throws Exception {
        // Услуга существует, пациента нет → 404
        Long serviceId = createService("X-Ray", new BigDecimal("1500.00"));

        mockMvc.perform(post("/api/patients/99999/paid-services/" + serviceId))
                .andExpect(status().isNotFound());
    }

    @Test
    void assignServiceToPatient_whenServiceNotFound_returns404() throws Exception {
        // Пациент существует, услуги нет → 404
        Long patientId = createPatient("111-999-888 00");

        mockMvc.perform(post("/api/patients/" + patientId + "/paid-services/99999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void markServicePaid_setsLinkPaidToTrue() throws Exception {
        // Полный бизнес-процесс: создать → назначить → оплатить.
        // Проверяем переход paid: false → true.
        Long serviceId = createService("Blood Test", new BigDecimal("500.00"));
        Long patientId = createPatient("222-333-444 55");

        // Назначаем услугу — получаем id записи PatientPaidService (linkId)
        String assignBody = mockMvc.perform(post("/api/patients/" + patientId + "/paid-services/" + serviceId))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        // linkId — идентификатор записи PatientPaidService (связки пациент↔услуга)
        Long linkId = objectMapper.readTree(assignBody).get("id").asLong();

        // PATCH /api/patients/{patientId}/paid-services/{linkId}/pay — отмечаем оплаченной.
        // PATCH (не PUT) — частичное обновление: меняем только поле paid, не всю запись.
        mockMvc.perform(patch("/api/patients/" + patientId + "/paid-services/" + linkId + "/pay"))
                .andExpect(status().isOk())
                // paid переключился с false на true
                .andExpect(jsonPath("$.paid").value(true));
    }
}
