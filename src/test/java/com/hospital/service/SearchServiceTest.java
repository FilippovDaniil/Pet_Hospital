package com.hospital.service;

import com.hospital.search.DoctorDocument;
import com.hospital.search.PatientDocument;
import com.hospital.search.SearchServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SearchServiceTest {

    private SearchServiceImpl searchService;

    @BeforeEach
    void setUp() {
        // client = null: opensearch disabled (graceful no-op mode)
        searchService = new SearchServiceImpl();
        // searchService.client remains null (field injection)
    }

    @Test
    void indexPatient_whenClientNull_doesNotThrow() {
        assertThatCode(() -> searchService.indexPatient(PatientDocument.builder()
                .id("1").fullName("Иванов Иван").active(true).build()))
                .doesNotThrowAnyException();
    }

    @Test
    void indexDoctor_whenClientNull_doesNotThrow() {
        assertThatCode(() -> searchService.indexDoctor(DoctorDocument.builder()
                .id("1").fullName("Доктор Айболит").specialization("SURGEON").active(true).build()))
                .doesNotThrowAnyException();
    }

    @Test
    void deletePatient_whenClientNull_doesNotThrow() {
        assertThatCode(() -> searchService.deletePatient("1"))
                .doesNotThrowAnyException();
    }

    @Test
    void deleteDoctor_whenClientNull_doesNotThrow() {
        assertThatCode(() -> searchService.deleteDoctor("1"))
                .doesNotThrowAnyException();
    }

    @Test
    void searchPatients_whenClientNull_returnsEmptyList() {
        List<PatientDocument> result = searchService.searchPatients("Иванов");
        assertThat(result).isEmpty();
    }

    @Test
    void searchDoctors_whenClientNull_returnsEmptyList() {
        List<DoctorDocument> result = searchService.searchDoctors("хирург");
        assertThat(result).isEmpty();
    }

    // helper to avoid static import conflict
    private static org.assertj.core.api.AbstractThrowableAssert<?, ?> assertThatCode(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        return org.assertj.core.api.Assertions.assertThatCode(callable);
    }
}
