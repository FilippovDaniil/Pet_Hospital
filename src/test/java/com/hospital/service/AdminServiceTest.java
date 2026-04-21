package com.hospital.service;

import com.hospital.dto.response.PaidServiceSummaryReport;
import com.hospital.dto.response.PatientResponse;
import com.hospital.dto.response.WardOccupancyReport;
import com.hospital.entity.*;
import com.hospital.exception.ResourceNotFoundException;
import com.hospital.mapper.PatientMapper;
import com.hospital.repository.*;
import com.hospital.service.event.EventPublisher;
import com.hospital.service.impl.AdminServiceImpl;
import com.hospital.service.strategy.DischargeStrategy;
import com.hospital.service.strategy.DischargeStrategyFactory;
import com.hospital.service.strategy.DischargeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock WardRepository wardRepository;
    @Mock DepartmentRepository departmentRepository;
    @Mock PatientRepository patientRepository;
    @Mock PatientPaidServiceRepository ppsRepository;
    @Mock PatientDoctorHistoryRepository doctorHistoryRepository;
    @Mock WardOccupationHistoryRepository wardHistoryRepository;
    @Mock PatientMapper patientMapper;
    @Mock DischargeStrategyFactory strategyFactory;
    @Mock EventPublisher eventPublisher;

    @InjectMocks
    AdminServiceImpl adminService;

    private Department dept;

    @BeforeEach
    void setUp() {
        dept = Department.builder().id(1L).name("Кардиология").build();
    }

    // --- getWardOccupancyReport ---

    @Test
    void getWardOccupancyReport_singleDept_aggregatesCorrectly() {
        Ward w1 = Ward.builder().id(1L).wardNumber("101").capacity(4).currentOccupancy(2).department(dept).build();
        Ward w2 = Ward.builder().id(2L).wardNumber("102").capacity(3).currentOccupancy(1).department(dept).build();
        when(wardRepository.findAllWithDepartment()).thenReturn(List.of(w1, w2));

        List<WardOccupancyReport> result = adminService.getWardOccupancyReport();

        assertThat(result).hasSize(1);
        WardOccupancyReport report = result.get(0);
        assertThat(report.getDepartmentName()).isEqualTo("Кардиология");
        assertThat(report.getTotalCapacity()).isEqualTo(7);
        assertThat(report.getTotalOccupied()).isEqualTo(3);
        assertThat(report.getTotalFree()).isEqualTo(4);
        assertThat(report.getWards()).hasSize(2);
    }

    @Test
    void getWardOccupancyReport_multipleDepts_sortedByDeptId() {
        Department dept2 = Department.builder().id(2L).name("Хирургия").build();
        Ward w1 = Ward.builder().id(1L).wardNumber("201").capacity(2).currentOccupancy(0).department(dept2).build();
        Ward w2 = Ward.builder().id(2L).wardNumber("101").capacity(3).currentOccupancy(1).department(dept).build();
        when(wardRepository.findAllWithDepartment()).thenReturn(List.of(w1, w2));

        List<WardOccupancyReport> result = adminService.getWardOccupancyReport();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getDepartmentId()).isEqualTo(1L);
        assertThat(result.get(1).getDepartmentId()).isEqualTo(2L);
    }

    @Test
    void getWardOccupancyReport_emptyWards_returnsEmpty() {
        when(wardRepository.findAllWithDepartment()).thenReturn(Collections.emptyList());

        List<WardOccupancyReport> result = adminService.getWardOccupancyReport();

        assertThat(result).isEmpty();
    }

    // --- getPaidServicesSummary ---

    @Test
    void getPaidServicesSummary_singlePatientMultipleServices_aggregatesTotals() {
        Patient p = Patient.builder().id(1L).fullName("Иванов Иван").build();
        PaidService svc1 = PaidService.builder().id(1L).price(new BigDecimal("1000")).build();
        PaidService svc2 = PaidService.builder().id(2L).price(new BigDecimal("2500")).build();
        PatientPaidService pps1 = PatientPaidService.builder().patient(p).paidService(svc1).build();
        PatientPaidService pps2 = PatientPaidService.builder().patient(p).paidService(svc2).build();
        when(ppsRepository.findAll()).thenReturn(List.of(pps1, pps2));

        PaidServiceSummaryReport result = adminService.getPaidServicesSummary();

        assertThat(result.getGrandTotal()).isEqualByComparingTo(new BigDecimal("3500"));
        assertThat(result.getByPatient()).hasSize(1);
        assertThat(result.getByPatient().get(0).getServiceCount()).isEqualTo(2);
        assertThat(result.getByPatient().get(0).getPatientName()).isEqualTo("Иванов Иван");
    }

    @Test
    void getPaidServicesSummary_noServices_returnsZeroTotal() {
        when(ppsRepository.findAll()).thenReturn(Collections.emptyList());

        PaidServiceSummaryReport result = adminService.getPaidServicesSummary();

        assertThat(result.getGrandTotal()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getByPatient()).isEmpty();
    }

    @Test
    void getPaidServicesSummary_multiplePatients_eachHasOwnSummary() {
        Patient p1 = Patient.builder().id(1L).fullName("Иванов").build();
        Patient p2 = Patient.builder().id(2L).fullName("Петрова").build();
        PaidService svc = PaidService.builder().id(1L).price(new BigDecimal("500")).build();
        PatientPaidService pps1 = PatientPaidService.builder().patient(p1).paidService(svc).build();
        PatientPaidService pps2 = PatientPaidService.builder().patient(p2).paidService(svc).build();
        when(ppsRepository.findAll()).thenReturn(List.of(pps1, pps2));

        PaidServiceSummaryReport result = adminService.getPaidServicesSummary();

        assertThat(result.getGrandTotal()).isEqualByComparingTo(new BigDecimal("1000"));
        assertThat(result.getByPatient()).hasSize(2);
    }

    // --- dischargePatient ---

    @Test
    void dischargePatient_whenNotFound_throwsResourceNotFoundException() {
        when(patientRepository.findByIdAndActiveTrue(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.dischargePatient(99L, DischargeType.NORMAL))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void dischargePatient_success_appliesStrategyAndPublishesEvent() {
        Patient patient = Patient.builder()
                .id(1L).fullName("Иванов Иван")
                .status(PatientStatus.TREATMENT)
                .active(true).build();
        DischargeStrategy strategy = mock(DischargeStrategy.class);
        when(patientRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(patient));
        when(strategyFactory.getStrategy(DischargeType.NORMAL)).thenReturn(strategy);
        when(patientRepository.save(any())).thenReturn(patient);
        when(patientMapper.toResponse(patient)).thenReturn(new PatientResponse());

        adminService.dischargePatient(1L, DischargeType.NORMAL);

        verify(strategy).discharge(patient);
        verify(eventPublisher).publishPatientEvent(any());
        verify(patientRepository).save(patient);
    }

    @Test
    void dischargePatient_withWard_clearsWardAssignment() {
        Ward ward = Ward.builder().id(1L).wardNumber("101").capacity(2).currentOccupancy(1).build();
        Patient patient = Patient.builder()
                .id(1L).fullName("Иванов")
                .status(PatientStatus.TREATMENT)
                .currentWard(ward)
                .active(true).build();
        DischargeStrategy strategy = mock(DischargeStrategy.class);
        when(patientRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(patient));
        when(strategyFactory.getStrategy(DischargeType.FORCED)).thenReturn(strategy);
        when(patientRepository.save(any())).thenReturn(patient);
        when(wardHistoryRepository.findByPatientIdAndDischargedAtIsNull(1L)).thenReturn(Optional.empty());
        when(patientMapper.toResponse(patient)).thenReturn(new PatientResponse());

        adminService.dischargePatient(1L, DischargeType.FORCED);

        assertThat(patient.getCurrentWard()).isNull();
        assertThat(ward.getCurrentOccupancy()).isEqualTo(0);
    }
}
