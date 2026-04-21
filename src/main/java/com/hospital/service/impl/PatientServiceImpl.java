package com.hospital.service.impl;

import com.hospital.dto.request.CreatePatientRequest;
import com.hospital.dto.request.UpdatePatientRequest;
import com.hospital.dto.response.PageResponse;
import com.hospital.dto.response.PatientPaidServiceResponse;
import com.hospital.dto.response.PatientResponse;
import com.hospital.entity.*;
import com.hospital.exception.BusinessRuleException;
import com.hospital.exception.ResourceNotFoundException;
import com.hospital.mapper.PaidServiceMapper;
import com.hospital.mapper.PatientMapper;
import com.hospital.repository.*;
import com.hospital.service.PatientService;
import com.hospital.service.event.EventPublisher;
import com.hospital.service.event.PatientEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PatientServiceImpl implements PatientService {

    private static final int MAX_PATIENTS_PER_DOCTOR = 20;

    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final PatientDoctorHistoryRepository historyRepository;
    private final PatientPaidServiceRepository ppsRepository;
    private final PatientMapper patientMapper;
    private final PaidServiceMapper paidServiceMapper;
    private final EventPublisher eventPublisher;

    @Override
    @Transactional
    public PatientResponse create(CreatePatientRequest request) {
        if (patientRepository.existsBySnilsAndActiveTrue(request.getSnils())) {
            throw new BusinessRuleException("Patient with SNILS " + request.getSnils() + " already exists");
        }
        Patient patient = patientMapper.toEntity(request);
        patient.setRegistrationDate(LocalDate.now());
        patient.setStatus(PatientStatus.TREATMENT);
        patient.setActive(true);
        Patient saved = patientRepository.save(patient);
        log.info("Created patient id={}", saved.getId());
        return patientMapper.toResponse(saved);
    }

    @Override
    public PatientResponse getById(Long id) {
        return patientMapper.toResponse(findActiveById(id));
    }

    @Override
    public PageResponse<PatientResponse> getAll(Pageable pageable) {
        Page<Patient> page = patientRepository.findAllByActiveTrue(pageable);
        return toPageResponse(page.map(patientMapper::toResponse));
    }

    @Override
    public PageResponse<PatientResponse> search(String q, PatientStatus status, Pageable pageable) {
        String searchQ = (q != null && !q.isBlank()) ? q.trim() : null;
        Page<Patient> page = patientRepository.search(searchQ, status, pageable);
        return toPageResponse(page.map(patientMapper::toResponse));
    }

    @Override
    @Transactional
    public PatientResponse update(Long id, UpdatePatientRequest request) {
        Patient patient = findActiveById(id);
        patientMapper.updateFromRequest(patient, request);
        return patientMapper.toResponse(patientRepository.save(patient));
    }

    @Override
    @Transactional
    public void softDelete(Long id) {
        Patient patient = findActiveById(id);
        patient.setActive(false);
        patientRepository.save(patient);
        log.info("Soft-deleted patient id={}", id);
    }

    @Override
    @Transactional
    public PatientResponse assignDoctor(Long patientId, Long doctorId) {
        Patient patient = findActiveById(patientId);
        Doctor doctor = doctorRepository.findByIdAndActiveTrue(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor", doctorId));

        long currentLoad = patientRepository.countActivePatientsByDoctorId(doctorId);
        if (currentLoad >= MAX_PATIENTS_PER_DOCTOR) {
            throw new BusinessRuleException(
                    "Doctor " + doctorId + " already has " + MAX_PATIENTS_PER_DOCTOR + " patients (maximum reached)");
        }

        Long previousDoctorId = patient.getCurrentDoctor() != null ? patient.getCurrentDoctor().getId() : null;

        // Close current history record
        if (previousDoctorId != null) {
            historyRepository.findByPatientIdAndAssignedToIsNull(patientId)
                    .ifPresent(h -> {
                        h.setAssignedTo(LocalDateTime.now());
                        historyRepository.save(h);
                    });
        }

        patient.setCurrentDoctor(doctor);
        patientRepository.save(patient);

        // Open new history record
        historyRepository.save(PatientDoctorHistory.builder()
                .patient(patient)
                .doctor(doctor)
                .assignedFrom(LocalDateTime.now())
                .build());

        // Publish domain event within the same transaction
        eventPublisher.publishPatientEvent(PatientEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("DOCTOR_CHANGED")
                .occurredAt(LocalDateTime.now())
                .patientId(patientId)
                .patientName(patient.getFullName())
                .previousDoctorId(previousDoctorId)
                .newDoctorId(doctorId)
                .build());

        log.info("Patient {} assigned to doctor {}", patientId, doctorId);
        return patientMapper.toResponse(patient);
    }

    @Override
    public List<PatientPaidServiceResponse> getServices(Long patientId) {
        findActiveById(patientId);
        return ppsRepository.findByPatientId(patientId).stream()
                .map(paidServiceMapper::toLinkResponse)
                .collect(Collectors.toList());
    }

    private Patient findActiveById(Long id) {
        return patientRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", id));
    }

    private <T> PageResponse<T> toPageResponse(Page<T> page) {
        return PageResponse.<T>builder()
                .content(page.getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }
}
