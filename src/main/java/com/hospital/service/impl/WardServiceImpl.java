package com.hospital.service.impl;

import com.hospital.dto.request.CreateWardRequest;
import com.hospital.dto.response.WardResponse;
import com.hospital.entity.*;
import com.hospital.exception.BusinessRuleException;
import com.hospital.exception.ResourceNotFoundException;
import com.hospital.mapper.WardMapper;
import com.hospital.repository.*;
import com.hospital.service.WardService;
import com.hospital.service.event.AdmissionEvent;
import com.hospital.service.event.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class WardServiceImpl implements WardService {

    private final WardRepository wardRepository;
    private final DepartmentRepository departmentRepository;
    private final PatientRepository patientRepository;
    private final WardOccupationHistoryRepository occupationHistoryRepository;
    private final WardMapper wardMapper;
    private final EventPublisher eventPublisher;

    @Override
    @Transactional
    public WardResponse create(CreateWardRequest request) {
        Department dept = departmentRepository.findById(request.getDepartmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Department", request.getDepartmentId()));
        Ward ward = wardMapper.toEntity(request);
        ward.setDepartment(dept);
        ward.setCurrentOccupancy(0);
        Ward saved = wardRepository.save(ward);
        log.info("Created ward id={} number={}", saved.getId(), saved.getWardNumber());
        return wardMapper.toResponse(saved);
    }

    @Override
    public WardResponse getById(Long id) {
        return wardMapper.toResponse(findById(id));
    }

    @Override
    public List<WardResponse> getAll() {
        return wardRepository.findAllWithDepartment().stream()
                .map(wardMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public WardResponse admitPatient(Long wardId, Long patientId) {
        Ward ward = findById(wardId);
        Patient patient = patientRepository.findByIdAndActiveTrue(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", patientId));

        if (ward.freeSlots() <= 0) {
            throw new BusinessRuleException(
                    "Ward " + ward.getWardNumber() + " has no free slots (capacity=" + ward.getCapacity() + ")");
        }
        if (patient.getCurrentWard() != null) {
            throw new BusinessRuleException(
                    "Patient " + patientId + " is already in ward " + patient.getCurrentWard().getWardNumber());
        }

        patient.setCurrentWard(ward);
        patientRepository.save(patient);

        ward.setCurrentOccupancy(ward.getCurrentOccupancy() + 1);
        wardRepository.save(ward);

        occupationHistoryRepository.save(WardOccupationHistory.builder()
                .patient(patient)
                .ward(ward)
                .admittedAt(LocalDateTime.now())
                .build());

        eventPublisher.publishAdmissionEvent(AdmissionEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .occurredAt(LocalDateTime.now())
                .patientId(patientId)
                .patientName(patient.getFullName())
                .wardId(wardId)
                .wardNumber(ward.getWardNumber())
                .action(AdmissionEvent.Action.ADMITTED)
                .build());

        log.info("Patient {} admitted to ward {}", patientId, wardId);
        return wardMapper.toResponse(ward);
    }

    @Override
    @Transactional
    public WardResponse dischargePatientFromWard(Long wardId, Long patientId) {
        Ward ward = findById(wardId);
        Patient patient = patientRepository.findByIdAndActiveTrue(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", patientId));

        if (patient.getCurrentWard() == null || !patient.getCurrentWard().getId().equals(wardId)) {
            throw new BusinessRuleException("Patient " + patientId + " is not in ward " + wardId);
        }

        patient.setCurrentWard(null);
        patientRepository.save(patient);

        ward.setCurrentOccupancy(Math.max(0, ward.getCurrentOccupancy() - 1));
        wardRepository.save(ward);

        occupationHistoryRepository.findByPatientIdAndDischargedAtIsNull(patientId)
                .ifPresent(h -> {
                    h.setDischargedAt(LocalDateTime.now());
                    occupationHistoryRepository.save(h);
                });

        eventPublisher.publishAdmissionEvent(AdmissionEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .occurredAt(LocalDateTime.now())
                .patientId(patientId)
                .patientName(patient.getFullName())
                .wardId(wardId)
                .wardNumber(ward.getWardNumber())
                .action(AdmissionEvent.Action.DISCHARGED)
                .build());

        log.info("Patient {} discharged from ward {}", patientId, wardId);
        return wardMapper.toResponse(ward);
    }

    private Ward findById(Long id) {
        return wardRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ward", id));
    }
}
