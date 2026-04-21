package com.hospital.service.impl;

import com.hospital.dto.response.PaidServiceSummaryReport;
import com.hospital.dto.response.PatientResponse;
import com.hospital.dto.response.WardOccupancyReport;
import com.hospital.entity.*;
import com.hospital.exception.ResourceNotFoundException;
import com.hospital.mapper.PatientMapper;
import com.hospital.repository.*;
import com.hospital.service.AdminService;
import com.hospital.service.event.EventPublisher;
import com.hospital.service.event.PatientEvent;
import com.hospital.config.CacheConfig;
import com.hospital.service.strategy.DischargeStrategyFactory;
import com.hospital.service.strategy.DischargeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AdminServiceImpl implements AdminService {

    private final PatientRepository patientRepository;
    private final WardRepository wardRepository;
    private final DepartmentRepository departmentRepository;
    private final PatientPaidServiceRepository ppsRepository;
    private final PatientDoctorHistoryRepository doctorHistoryRepository;
    private final WardOccupationHistoryRepository wardHistoryRepository;
    private final PatientMapper patientMapper;
    private final DischargeStrategyFactory strategyFactory;
    private final EventPublisher eventPublisher;

    @Override
    @Cacheable(CacheConfig.WARD_OCCUPANCY)
    public List<WardOccupancyReport> getWardOccupancyReport() {
        List<Ward> wards = wardRepository.findAllWithDepartment();
        Map<Long, List<Ward>> byDepartment = wards.stream()
                .collect(Collectors.groupingBy(w -> w.getDepartment().getId()));

        return byDepartment.entrySet().stream()
                .map(entry -> {
                    Long deptId = entry.getKey();
                    List<Ward> deptWards = entry.getValue();
                    String deptName = deptWards.get(0).getDepartment().getName();

                    List<WardOccupancyReport.WardOccupancyItem> items = deptWards.stream()
                            .map(w -> WardOccupancyReport.WardOccupancyItem.builder()
                                    .wardId(w.getId())
                                    .wardNumber(w.getWardNumber())
                                    .capacity(w.getCapacity())
                                    .occupied(w.getCurrentOccupancy())
                                    .free(w.freeSlots())
                                    .build())
                            .collect(Collectors.toList());

                    int totalCap = deptWards.stream().mapToInt(Ward::getCapacity).sum();
                    int totalOcc = deptWards.stream().mapToInt(Ward::getCurrentOccupancy).sum();

                    return WardOccupancyReport.builder()
                            .departmentId(deptId)
                            .departmentName(deptName)
                            .wards(items)
                            .totalCapacity(totalCap)
                            .totalOccupied(totalOcc)
                            .totalFree(totalCap - totalOcc)
                            .build();
                })
                .sorted(Comparator.comparing(WardOccupancyReport::getDepartmentId))
                .collect(Collectors.toList());
    }

    @Override
    @Cacheable(CacheConfig.SERVICES_SUMMARY)
    public PaidServiceSummaryReport getPaidServicesSummary() {
        List<PatientPaidService> all = ppsRepository.findAll();
        Map<Long, List<PatientPaidService>> byPatient = all.stream()
                .collect(Collectors.groupingBy(pps -> pps.getPatient().getId()));

        List<PaidServiceSummaryReport.PatientSummary> summaries = byPatient.entrySet().stream()
                .map(entry -> {
                    List<PatientPaidService> services = entry.getValue();
                    BigDecimal total = services.stream()
                            .map(pps -> pps.getPaidService().getPrice())
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return PaidServiceSummaryReport.PatientSummary.builder()
                            .patientId(entry.getKey())
                            .patientName(services.get(0).getPatient().getFullName())
                            .total(total)
                            .serviceCount(services.size())
                            .build();
                })
                .sorted(Comparator.comparing(PaidServiceSummaryReport.PatientSummary::getPatientId))
                .collect(Collectors.toList());

        BigDecimal grandTotal = summaries.stream()
                .map(PaidServiceSummaryReport.PatientSummary::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return PaidServiceSummaryReport.builder()
                .byPatient(summaries)
                .grandTotal(grandTotal)
                .build();
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.WARD_OCCUPANCY, CacheConfig.SERVICES_SUMMARY}, allEntries = true)
    public PatientResponse dischargePatient(Long patientId, DischargeType dischargeType) {
        Patient patient = patientRepository.findByIdAndActiveTrue(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", patientId));

        // Free the ward if occupied
        if (patient.getCurrentWard() != null) {
            Ward ward = patient.getCurrentWard();
            ward.setCurrentOccupancy(Math.max(0, ward.getCurrentOccupancy() - 1));
            wardHistoryRepository.findByPatientIdAndDischargedAtIsNull(patientId)
                    .ifPresent(h -> {
                        h.setDischargedAt(LocalDateTime.now());
                        wardHistoryRepository.save(h);
                    });
            patient.setCurrentWard(null);
        }

        // Close doctor history
        if (patient.getCurrentDoctor() != null) {
            doctorHistoryRepository.findByPatientIdAndAssignedToIsNull(patientId)
                    .ifPresent(h -> {
                        h.setAssignedTo(LocalDateTime.now());
                        doctorHistoryRepository.save(h);
                    });
        }

        // Apply the selected strategy (Strategy pattern)
        strategyFactory.getStrategy(dischargeType).discharge(patient);
        patientRepository.save(patient);

        // Publish discharged event within the same transaction
        eventPublisher.publishPatientEvent(PatientEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("PATIENT_DISCHARGED")
                .occurredAt(LocalDateTime.now())
                .patientId(patientId)
                .patientName(patient.getFullName())
                .newStatus(patient.getStatus())
                .build());

        log.info("Patient {} discharged with type={}", patientId, dischargeType);
        return patientMapper.toResponse(patient);
    }
}
