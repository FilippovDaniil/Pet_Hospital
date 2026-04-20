package com.hospital.service.impl;

import com.hospital.dto.request.CreateDoctorRequest;
import com.hospital.dto.request.UpdateDoctorRequest;
import com.hospital.dto.response.DoctorResponse;
import com.hospital.dto.response.PageResponse;
import com.hospital.dto.response.PatientResponse;
import com.hospital.entity.Department;
import com.hospital.entity.Doctor;
import com.hospital.entity.Specialty;
import com.hospital.exception.ResourceNotFoundException;
import com.hospital.mapper.DoctorMapper;
import com.hospital.mapper.PatientMapper;
import com.hospital.repository.DepartmentRepository;
import com.hospital.repository.DoctorRepository;
import com.hospital.repository.PatientRepository;
import com.hospital.service.DoctorService;
import com.hospital.service.event.DoctorEvent;
import com.hospital.service.event.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class DoctorServiceImpl implements DoctorService {

    private final DoctorRepository doctorRepository;
    private final DepartmentRepository departmentRepository;
    private final PatientRepository patientRepository;
    private final DoctorMapper doctorMapper;
    private final PatientMapper patientMapper;
    private final EventPublisher eventPublisher;

    @Override
    @Transactional
    public DoctorResponse create(CreateDoctorRequest request) {
        Doctor doctor = doctorMapper.toEntity(request);
        doctor.setActive(true);
        if (request.getDepartmentId() != null) {
            Department dept = departmentRepository.findById(request.getDepartmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Department", request.getDepartmentId()));
            doctor.setDepartment(dept);
        }
        Doctor saved = doctorRepository.save(doctor);
        eventPublisher.publishDoctorEvent(DoctorEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("DOCTOR_CREATED")
                .occurredAt(LocalDateTime.now())
                .doctorId(saved.getId())
                .doctorName(saved.getFullName())
                .departmentId(saved.getDepartment() != null ? saved.getDepartment().getId() : null)
                .build());
        log.info("Created doctor id={}", saved.getId());
        return doctorMapper.toResponse(saved);
    }

    @Override
    public DoctorResponse getById(Long id) {
        return doctorMapper.toResponse(findActiveById(id));
    }

    @Override
    public PageResponse<DoctorResponse> getAll(Pageable pageable) {
        Page<Doctor> page = doctorRepository.findAllByActiveTrue(pageable);
        return toPageResponse(page.map(doctorMapper::toResponse));
    }

    @Override
    public PageResponse<DoctorResponse> getBySpecialty(Specialty specialty, Pageable pageable) {
        Page<Doctor> page = doctorRepository.findBySpecialtyAndActiveTrue(specialty, pageable);
        return toPageResponse(page.map(doctorMapper::toResponse));
    }

    @Override
    @Transactional
    public DoctorResponse update(Long id, UpdateDoctorRequest request) {
        Doctor doctor = findActiveById(id);
        doctorMapper.updateFromRequest(doctor, request);
        if (request.getDepartmentId() != null) {
            Department dept = departmentRepository.findById(request.getDepartmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Department", request.getDepartmentId()));
            doctor.setDepartment(dept);
        }
        return doctorMapper.toResponse(doctorRepository.save(doctor));
    }

    @Override
    @Transactional
    public void softDelete(Long id) {
        Doctor doctor = findActiveById(id);
        doctor.setActive(false);
        doctorRepository.save(doctor);
        log.info("Soft-deleted doctor id={}", id);
    }

    @Override
    public PageResponse<PatientResponse> getPatients(Long doctorId, Pageable pageable) {
        findActiveById(doctorId);
        Page<com.hospital.entity.Patient> page = patientRepository.findByDoctorId(doctorId, pageable);
        return toPageResponse(page.map(patientMapper::toResponse));
    }

    private Doctor findActiveById(Long id) {
        return doctorRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor", id));
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
