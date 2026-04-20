package com.hospital.service;

import com.hospital.entity.*;
import com.hospital.exception.BusinessRuleException;
import com.hospital.mapper.WardMapper;
import com.hospital.repository.*;
import com.hospital.service.event.EventPublisher;
import com.hospital.service.impl.WardServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WardServiceTest {

    @Mock
    private WardRepository wardRepository;
    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private PatientRepository patientRepository;
    @Mock
    private WardOccupationHistoryRepository occupationHistoryRepository;
    @Mock
    private WardMapper wardMapper;
    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private WardServiceImpl wardService;

    private Ward fullWard;
    private Ward availableWard;
    private Patient patient;

    @BeforeEach
    void setUp() {
        Department dept = Department.builder().id(1L).name("Cardiology").build();

        fullWard = Ward.builder()
                .id(1L).wardNumber("101A").capacity(2).currentOccupancy(2).department(dept).build();

        availableWard = Ward.builder()
                .id(2L).wardNumber("102A").capacity(3).currentOccupancy(1).department(dept).build();

        patient = Patient.builder()
                .id(1L).fullName("Test Patient")
                .status(PatientStatus.TREATMENT)
                .active(true)
                .build();
    }

    @Test
    void admitPatient_whenNoFreeSlots_throwsBusinessRuleException() {
        when(wardRepository.findById(1L)).thenReturn(Optional.of(fullWard));
        when(patientRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(patient));

        assertThatThrownBy(() -> wardService.admitPatient(1L, 1L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no free slots");
    }

    @Test
    void admitPatient_whenPatientAlreadyInWard_throwsBusinessRuleException() {
        patient.setCurrentWard(availableWard);
        when(wardRepository.findById(2L)).thenReturn(Optional.of(availableWard));
        when(patientRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(patient));

        assertThatThrownBy(() -> wardService.admitPatient(2L, 1L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already in ward");
    }

    @Test
    void admitPatient_success_incrementsOccupancy() {
        when(wardRepository.findById(2L)).thenReturn(Optional.of(availableWard));
        when(patientRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(patient));
        when(patientRepository.save(any())).thenReturn(patient);
        when(wardRepository.save(any())).thenReturn(availableWard);
        when(occupationHistoryRepository.save(any())).thenReturn(null);
        doNothing().when(eventPublisher).publishAdmissionEvent(any());
        when(wardMapper.toResponse(any())).thenReturn(new com.hospital.dto.response.WardResponse());

        wardService.admitPatient(2L, 1L);

        assertThat(availableWard.getCurrentOccupancy()).isEqualTo(2);
        verify(eventPublisher).publishAdmissionEvent(
                argThat(e -> e.getAction() == com.hospital.service.event.AdmissionEvent.Action.ADMITTED));
    }

    @Test
    void dischargePatient_whenPatientNotInWard_throwsBusinessRuleException() {
        // patient is in ward 3, trying to discharge from ward 2
        Ward otherWard = Ward.builder().id(3L).wardNumber("103A").capacity(2).currentOccupancy(1).build();
        patient.setCurrentWard(otherWard);

        when(wardRepository.findById(2L)).thenReturn(Optional.of(availableWard));
        when(patientRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(patient));

        assertThatThrownBy(() -> wardService.dischargePatientFromWard(2L, 1L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not in ward");
    }

    @Test
    void dischargePatient_success_decrementsOccupancy() {
        patient.setCurrentWard(availableWard);
        when(wardRepository.findById(2L)).thenReturn(Optional.of(availableWard));
        when(patientRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(patient));
        when(patientRepository.save(any())).thenReturn(patient);
        when(wardRepository.save(any())).thenReturn(availableWard);
        when(occupationHistoryRepository.findByPatientIdAndDischargedAtIsNull(1L)).thenReturn(Optional.empty());
        doNothing().when(eventPublisher).publishAdmissionEvent(any());
        when(wardMapper.toResponse(any())).thenReturn(new com.hospital.dto.response.WardResponse());

        wardService.dischargePatientFromWard(2L, 1L);

        assertThat(availableWard.getCurrentOccupancy()).isEqualTo(0);
        verify(eventPublisher).publishAdmissionEvent(
                argThat(e -> e.getAction() == com.hospital.service.event.AdmissionEvent.Action.DISCHARGED));
    }
}
