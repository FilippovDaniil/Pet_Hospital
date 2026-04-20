package com.hospital.service.impl;

import com.hospital.dto.request.CreateDepartmentRequest;
import com.hospital.dto.request.UpdateDepartmentRequest;
import com.hospital.dto.response.DepartmentResponse;
import com.hospital.entity.Department;
import com.hospital.entity.Doctor;
import com.hospital.exception.ResourceNotFoundException;
import com.hospital.mapper.DepartmentMapper;
import com.hospital.repository.DepartmentRepository;
import com.hospital.repository.DoctorRepository;
import com.hospital.service.DepartmentService;
import com.hospital.service.event.DepartmentEvent;
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
public class DepartmentServiceImpl implements DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final DoctorRepository doctorRepository;
    private final DepartmentMapper departmentMapper;
    private final EventPublisher eventPublisher;

    @Override
    @Transactional
    public DepartmentResponse create(CreateDepartmentRequest request) {
        Department dept = departmentMapper.toEntity(request);
        if (request.getHeadDoctorId() != null) {
            Doctor doc = doctorRepository.findByIdAndActiveTrue(request.getHeadDoctorId())
                    .orElseThrow(() -> new ResourceNotFoundException("Doctor", request.getHeadDoctorId()));
            dept.setHeadDoctor(doc);
        }
        Department saved = departmentRepository.save(dept);
        eventPublisher.publishDepartmentEvent(DepartmentEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("DEPARTMENT_CREATED")
                .occurredAt(LocalDateTime.now())
                .departmentId(saved.getId())
                .departmentName(saved.getName())
                .build());
        log.info("Created department id={}", saved.getId());
        return departmentMapper.toResponse(saved);
    }

    @Override
    public DepartmentResponse getById(Long id) {
        return departmentMapper.toResponse(findById(id));
    }

    @Override
    public List<DepartmentResponse> getAll() {
        return departmentRepository.findAll().stream()
                .map(departmentMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public DepartmentResponse update(Long id, UpdateDepartmentRequest request) {
        Department dept = findById(id);
        departmentMapper.updateFromRequest(dept, request);
        if (request.getHeadDoctorId() != null) {
            Doctor doc = doctorRepository.findByIdAndActiveTrue(request.getHeadDoctorId())
                    .orElseThrow(() -> new ResourceNotFoundException("Doctor", request.getHeadDoctorId()));
            dept.setHeadDoctor(doc);
        }
        return departmentMapper.toResponse(departmentRepository.save(dept));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Department dept = findById(id);
        eventPublisher.publishDepartmentEvent(DepartmentEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("DEPARTMENT_DELETED")
                .occurredAt(LocalDateTime.now())
                .departmentId(dept.getId())
                .departmentName(dept.getName())
                .build());
        departmentRepository.delete(dept);
        log.info("Deleted department id={}", id);
    }

    private Department findById(Long id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department", id));
    }
}
