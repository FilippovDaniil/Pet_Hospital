package com.hospital.service;

import com.hospital.dto.request.CreatePatientRequest;
import com.hospital.dto.response.PatientResponse;
import com.hospital.entity.*;
import com.hospital.exception.BusinessRuleException;
import com.hospital.exception.ResourceNotFoundException;
import com.hospital.mapper.PaidServiceMapper;
import com.hospital.mapper.PatientMapper;
import com.hospital.repository.*;
import com.hospital.service.event.EventPublisher;
import com.hospital.service.impl.PatientServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hospital.entity.PatientStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PatientServiceTest {

    @Mock
    private PatientRepository patientRepository;
    @Mock
    private DoctorRepository doctorRepository;
    @Mock
    private PatientDoctorHistoryRepository historyRepository;
    @Mock
    private PatientPaidServiceRepository ppsRepository;
    @Mock
    private PatientMapper patientMapper;
    @Mock
    private PaidServiceMapper paidServiceMapper;
    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private PatientServiceImpl patientService;

    private Patient patient;
    private Doctor doctor;

    @BeforeEach
    void setUp() {
        patient = Patient.builder()
                .id(1L).fullName("Test Patient")
                .birthDate(LocalDate.of(1990, 1, 1))
                .gender(Gender.MALE)
                .snils("123-456-789 01")
                .registrationDate(LocalDate.now())
                .status(PatientStatus.TREATMENT)
                .active(true)
                .build();

        doctor = Doctor.builder()
                .id(10L).fullName("Dr Smith")
                .specialty(Specialty.CARDIOLOGIST)
                .active(true)
                .build();
    }

    @Test
    void createPatient_whenSnilsAlreadyExists_throwsBusinessRuleException() {
        when(patientRepository.existsBySnilsAndActiveTrue("123-456-789 01")).thenReturn(true);

        CreatePatientRequest request = new CreatePatientRequest();
        request.setSnils("123-456-789 01");

        assertThatThrownBy(() -> patientService.create(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void createPatient_success() {
        CreatePatientRequest request = new CreatePatientRequest();
        request.setFullName("New Patient");
        request.setSnils("999-888-777 66");
        request.setBirthDate(LocalDate.of(1985, 5, 5));
        request.setGender(Gender.FEMALE);

        when(patientRepository.existsBySnilsAndActiveTrue(anyString())).thenReturn(false);
        when(patientMapper.toEntity(request)).thenReturn(patient);
        when(patientRepository.save(any(Patient.class))).thenReturn(patient);
        PatientResponse response = new PatientResponse();
        response.setId(1L);
        when(patientMapper.toResponse(patient)).thenReturn(response);

        PatientResponse result = patientService.create(request);

        assertThat(result.getId()).isEqualTo(1L);
        verify(patientRepository).save(any(Patient.class));
    }

    @Test
    void assignDoctor_whenDoctorHasMaxPatients_throwsBusinessRuleException() {
        when(patientRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(patient));
        when(doctorRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(doctor));
        when(patientRepository.countActivePatientsByDoctorId(10L)).thenReturn(20L);

        assertThatThrownBy(() -> patientService.assignDoctor(1L, 10L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("maximum reached");
    }

    @Test
    void assignDoctor_success() {
        when(patientRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(patient));
        when(doctorRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(doctor));
        when(patientRepository.countActivePatientsByDoctorId(10L)).thenReturn(5L);
        when(patientRepository.save(any(Patient.class))).thenReturn(patient);
        when(historyRepository.save(any())).thenReturn(null);
        PatientResponse response = new PatientResponse();
        response.setCurrentDoctorId(10L);
        when(patientMapper.toResponse(patient)).thenReturn(response);

        PatientResponse result = patientService.assignDoctor(1L, 10L);

        assertThat(result.getCurrentDoctorId()).isEqualTo(10L);
        verify(eventPublisher).publishPatientEvent(any());
    }

    @Test
    void getById_whenNotFound_throwsResourceNotFoundException() {
        when(patientRepository.findByIdAndActiveTrue(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> patientService.getById(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void softDelete_marksPatientInactive() {
        when(patientRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(patient));
        when(patientRepository.save(any(Patient.class))).thenReturn(patient);

        patientService.softDelete(1L);

        assertThat(patient.isActive()).isFalse();
        verify(patientRepository).save(patient);
    }

    @Test
    void search_withBlankQuery_passesNullToRepository() {
        Pageable pageable = PageRequest.of(0, 10);
        when(patientRepository.search(null, null, pageable))
                .thenReturn(new PageImpl<>(Collections.emptyList(), pageable, 0));

        patientService.search("   ", null, pageable);

        verify(patientRepository).search(null, null, pageable);
    }

    @Test
    void search_withName_passestrimmedQueryToRepository() {
        Pageable pageable = PageRequest.of(0, 10);
        when(patientRepository.search("Иванов", null, pageable))
                .thenReturn(new PageImpl<>(List.of(patient), pageable, 1));
        when(patientMapper.toResponse(patient)).thenReturn(new PatientResponse());

        patientService.search("  Иванов  ", null, pageable);

        verify(patientRepository).search("Иванов", null, pageable);
    }

    @Test
    void search_withStatus_passesStatusToRepository() {
        Pageable pageable = PageRequest.of(0, 10);
        when(patientRepository.search(null, PatientStatus.TREATMENT, pageable))
                .thenReturn(new PageImpl<>(Collections.emptyList(), pageable, 0));

        patientService.search(null, PatientStatus.TREATMENT, pageable);

        verify(patientRepository).search(null, PatientStatus.TREATMENT, pageable);
    }

    @Test
    void search_returnsCorrectPageMetadata() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Patient> page = new PageImpl<>(List.of(patient), pageable, 1);
        when(patientRepository.search("Test", null, pageable)).thenReturn(page);
        when(patientMapper.toResponse(patient)).thenReturn(new PatientResponse());

        var result = patientService.search("Test", null, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getPage()).isEqualTo(0);
        assertThat(result.getContent()).hasSize(1);
    }
}
