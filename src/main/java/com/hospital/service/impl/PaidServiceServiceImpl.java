package com.hospital.service.impl;

import com.hospital.dto.request.CreatePaidServiceRequest;
import com.hospital.dto.response.PageResponse;
import com.hospital.dto.response.PaidServiceResponse;
import com.hospital.dto.response.PatientPaidServiceResponse;
import com.hospital.entity.PaidService;
import com.hospital.entity.Patient;
import com.hospital.entity.PatientPaidService;
import com.hospital.exception.BusinessRuleException;
import com.hospital.exception.ResourceNotFoundException;
import com.hospital.mapper.PaidServiceMapper;
import com.hospital.repository.PaidServiceRepository;
import com.hospital.repository.PatientPaidServiceRepository;
import com.hospital.repository.PatientRepository;
import com.hospital.service.PaidServiceService;
import com.hospital.service.event.EventPublisher;
import com.hospital.service.event.PaidServiceEvent;
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
public class PaidServiceServiceImpl implements PaidServiceService {

    private final PaidServiceRepository paidServiceRepository;
    private final PatientRepository patientRepository;
    private final PatientPaidServiceRepository ppsRepository;
    private final PaidServiceMapper paidServiceMapper;
    private final EventPublisher eventPublisher;

    @Override
    @Transactional
    public PaidServiceResponse create(CreatePaidServiceRequest request) {
        PaidService service = paidServiceMapper.toEntity(request);
        service.setActive(true);
        PaidService saved = paidServiceRepository.save(service);
        log.info("Created paid service id={}", saved.getId());
        return paidServiceMapper.toResponse(saved);
    }

    @Override
    public PaidServiceResponse getById(Long id) {
        return paidServiceMapper.toResponse(findActiveById(id));
    }

    @Override
    public PageResponse<PaidServiceResponse> getAll(Pageable pageable) {
        Page<PaidService> page = paidServiceRepository.findAllByActiveTrue(pageable);
        return PageResponse.<PaidServiceResponse>builder()
                .content(page.getContent().stream().map(paidServiceMapper::toResponse).toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    @Override
    @Transactional
    public PatientPaidServiceResponse assignToPatient(Long patientId, Long serviceId) {
        Patient patient = patientRepository.findByIdAndActiveTrue(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", patientId));
        PaidService service = findActiveById(serviceId);

        PatientPaidService link = PatientPaidService.builder()
                .patient(patient)
                .paidService(service)
                .assignedDate(LocalDateTime.now())
                .paid(false)
                .build();
        PatientPaidService saved = ppsRepository.save(link);

        eventPublisher.publishPaidServiceEvent(PaidServiceEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .occurredAt(LocalDateTime.now())
                .patientId(patientId)
                .patientName(patient.getFullName())
                .serviceId(serviceId)
                .serviceName(service.getName())
                .price(service.getPrice())
                .linkId(saved.getId())
                .build());

        log.info("Assigned paid service {} to patient {}", serviceId, patientId);
        return paidServiceMapper.toLinkResponse(saved);
    }

    @Override
    @Transactional
    public PatientPaidServiceResponse markPaid(Long patientId, Long linkId) {
        PatientPaidService link = ppsRepository.findById(linkId)
                .orElseThrow(() -> new ResourceNotFoundException("PatientPaidService", linkId));
        if (!link.getPatient().getId().equals(patientId)) {
            throw new BusinessRuleException("Service link " + linkId + " does not belong to patient " + patientId);
        }
        link.setPaid(true);
        return paidServiceMapper.toLinkResponse(ppsRepository.save(link));
    }

    private PaidService findActiveById(Long id) {
        return paidServiceRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("PaidService", id));
    }
}
