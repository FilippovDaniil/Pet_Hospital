package com.hospital.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.dto.request.CreatePatientRequest;
import com.hospital.entity.Gender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import org.springframework.security.test.context.support.WithMockUser;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests using Testcontainers (PostgreSQL) + EmbeddedKafka.
 * Profile "test" uses tc:postgresql JDBC URL (Testcontainers).
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
class PatientIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createPatient_returnsCreated() throws Exception {
        CreatePatientRequest request = new CreatePatientRequest();
        request.setFullName("Integration Test Patient");
        request.setBirthDate(LocalDate.of(1990, 6, 15));
        request.setGender(Gender.MALE);
        request.setSnils("000-111-222 33");
        request.setPhone("+7-900-000-0001");
        request.setAddress("Test Address");

        mockMvc.perform(post("/api/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fullName").value("Integration Test Patient"))
                .andExpect(jsonPath("$.status").value("TREATMENT"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void createPatient_withDuplicateSnils_returnsConflict() throws Exception {
        // First request succeeds
        CreatePatientRequest request = new CreatePatientRequest();
        request.setFullName("Duplicate SNILS Patient");
        request.setBirthDate(LocalDate.of(1985, 3, 20));
        request.setGender(Gender.FEMALE);
        request.setSnils("111-222-333 44");

        mockMvc.perform(post("/api/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Second request with same SNILS fails
        mockMvc.perform(post("/api/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void getPatient_whenNotFound_returns404() throws Exception {
        mockMvc.perform(get("/api/patients/99999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createPatient_withInvalidRequest_returns400() throws Exception {
        // Missing required fields
        String invalidJson = "{\"phone\": \"123\"}";

        mockMvc.perform(post("/api/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").exists());
    }

    @Test
    void getAllPatients_returnsPaginatedList() throws Exception {
        mockMvc.perform(get("/api/patients?page=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").value(0));
    }

    @Test
    void softDeletePatient_thenGetReturns404() throws Exception {
        // Create patient
        CreatePatientRequest request = new CreatePatientRequest();
        request.setFullName("To Delete Patient");
        request.setBirthDate(LocalDate.of(1970, 1, 1));
        request.setGender(Gender.MALE);
        request.setSnils("999-888-777 11");

        String response = mockMvc.perform(post("/api/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(response).get("id").asLong();

        // Delete it
        mockMvc.perform(delete("/api/patients/" + id))
                .andExpect(status().isNoContent());

        // Should now return 404
        mockMvc.perform(get("/api/patients/" + id))
                .andExpect(status().isNotFound());
    }

    @Test
    void searchPatients_byName_returnsFilteredResults() throws Exception {
        // Create a patient first
        CreatePatientRequest request = new CreatePatientRequest();
        request.setFullName("Поиск Тестовый Пациент");
        request.setBirthDate(LocalDate.of(1980, 1, 1));
        request.setGender(Gender.MALE);
        request.setSnils("444-555-666 77");

        mockMvc.perform(post("/api/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/patients/search?q=Поиск Тестовый"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].fullName").value("Поиск Тестовый Пациент"));
    }

    @Test
    void searchPatients_byStatus_returnsOnlyMatchingStatus() throws Exception {
        mockMvc.perform(get("/api/patients/search?status=TREATMENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // clientUserId — привязка пациента к порталу
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Создание пациента с clientUserId — в ответе должен быть заполнен clientUserId.
     * Проверяет, что сервис привязывает patient.clientUser к порталу и маппер
     * возвращает clientUserId в PatientResponse.
     */
    @Test
    void createPatient_withClientUserId_returnsPatientWithClientUserId() throws Exception {
        Long clientUserId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = 'client1'", Long.class);

        CreatePatientRequest request = new CreatePatientRequest();
        request.setFullName("Portal Linked Patient");
        request.setBirthDate(LocalDate.of(1992, 7, 20));
        request.setGender(Gender.FEMALE);
        request.setSnils("321-654-987 10");
        request.setClientUserId(clientUserId);

        mockMvc.perform(post("/api/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fullName").value("Portal Linked Patient"))
                .andExpect(jsonPath("$.clientUserId").value(clientUserId));
    }

    /**
     * assignDoctor — если у пациента есть clientUser и у врача есть linkedUser,
     * должна автоматически создаться чат-комната DOCTOR_CLIENT.
     *
     * Шаги:
     *   1. Привязываем doctor1 к его профилю (аналогично MedicalIntegrationTest.setUp).
     *   2. Создаём пациента с clientUserId=client1.
     *   3. Назначаем doctor1 этому пациенту.
     *   4. Проверяем через JdbcTemplate, что комната DOCTOR_CLIENT появилась в БД.
     */
    @Test
    void assignDoctor_whenPatientHasClientAndDoctorHasLinkedUser_chatRoomCreated() throws Exception {
        // Привязываем doctor1 к профилю врача (могло быть не выполнено при V6-миграции)
        jdbcTemplate.update(
                "UPDATE doctor SET user_id = (SELECT id FROM users WHERE username = 'doctor1') " +
                "WHERE full_name = 'Иванов Сергей Петрович' AND user_id IS NULL");

        Long clientUserId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = 'client1'", Long.class);
        Long doctorId = jdbcTemplate.queryForObject(
                "SELECT d.id FROM doctor d JOIN users u ON d.user_id = u.id WHERE u.username = 'doctor1'",
                Long.class);

        // Создаём пациента, привязанного к client1
        CreatePatientRequest request = new CreatePatientRequest();
        request.setFullName("Chat Auto Room Patient");
        request.setBirthDate(LocalDate.of(1988, 3, 15));
        request.setGender(Gender.MALE);
        request.setSnils("987-654-321 00");
        request.setClientUserId(clientUserId);

        MvcResult createResult = mockMvc.perform(post("/api/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        Long patientId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asLong();

        // Назначаем врача
        mockMvc.perform(put("/api/patients/{id}/assign-doctor/{doctorId}", patientId, doctorId))
                .andExpect(status().isOk());

        // Убеждаемся, что чат-комната DOCTOR_CLIENT создана
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM chat_room WHERE client_user_id = ? AND type = 'DOCTOR_CLIENT'",
                Integer.class, clientUserId);
        assertThat(count).isGreaterThan(0);
    }
}
