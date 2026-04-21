package com.hospital.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.dto.request.CreatePatientRequest;
import com.hospital.entity.Gender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.security.test.context.support.WithMockUser;

import java.time.LocalDate;

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
}
