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

/**
 * Реализация сервиса для управления платными услугами.
 *
 * Две сущности в зоне ответственности:
 *   PaidService       — справочник услуг (МРТ, УЗИ, консультация и т.д.)
 *   PatientPaidService — назначение конкретной услуги конкретному пациенту
 *
 * Основные операции:
 *   - CRUD справочника платных услуг
 *   - Назначение услуги пациенту (создание записи PatientPaidService)
 *   - Отметка об оплате назначенной услуги
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PaidServiceServiceImpl implements PaidServiceService {

    private final PaidServiceRepository paidServiceRepository;
    private final PatientRepository patientRepository;
    private final PatientPaidServiceRepository ppsRepository; // связка пациент↔услуга
    private final PaidServiceMapper paidServiceMapper;
    private final EventPublisher eventPublisher;

    /** Создание новой платной услуги в справочнике. active=true устанавливается принудительно. */
    @Override
    @Transactional
    public PaidServiceResponse create(CreatePaidServiceRequest request) {
        PaidService service = paidServiceMapper.toEntity(request);
        service.setActive(true); // новая услуга сразу доступна для назначения
        PaidService saved = paidServiceRepository.save(service);
        log.info("Created paid service id={}", saved.getId());
        return paidServiceMapper.toResponse(saved);
    }

    /** Получение услуги по ID (только активные). */
    @Override
    public PaidServiceResponse getById(Long id) {
        return paidServiceMapper.toResponse(findActiveById(id));
    }

    /**
     * Получение всех активных услуг с пагинацией.
     * Обратите внимание на стиль: вместо отдельного метода toPageResponse()
     * (как в PatientServiceImpl) здесь PageResponse строится inline.
     * Оба подхода корректны; выделение в метод предпочтительно при повторном использовании.
     * stream().toList() — Java 16+ метод, создаёт неизменяемый список (аналог Collectors.toUnmodifiableList()).
     */
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

    /**
     * Назначение платной услуги пациенту — создание записи PatientPaidService.
     *
     * Создаёт связку пациент↔услуга:
     *   - assignedDate = текущее время (фиксируем момент назначения)
     *   - paid = false (назначено, но ещё не оплачено)
     *
     * После сохранения публикуем событие SERVICE_ASSIGNED, чтобы:
     *   - Система биллинга знала о новой транзакции
     *   - Бухгалтерия получила уведомление
     *   - Аудит-лог зафиксировал факт назначения
     */
    @Override
    @Transactional
    public PatientPaidServiceResponse assignToPatient(Long patientId, Long serviceId) {
        // Проверяем существование пациента и услуги (fail-fast).
        Patient patient = patientRepository.findByIdAndActiveTrue(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", patientId));
        PaidService service = findActiveById(serviceId);

        // Создаём запись назначения. Builder-паттерн от Lombok делает код читаемым.
        PatientPaidService link = PatientPaidService.builder()
                .patient(patient)
                .paidService(service)
                .assignedDate(LocalDateTime.now())
                .paid(false) // явно указываем, хотя @Builder.Default уже ставит false
                .build();
        PatientPaidService saved = ppsRepository.save(link);

        // Публикуем событие в рамках текущей транзакции.
        eventPublisher.publishPaidServiceEvent(PaidServiceEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .occurredAt(LocalDateTime.now())
                .patientId(patientId)
                .patientName(patient.getFullName())
                .serviceId(serviceId)
                .serviceName(service.getName())
                .price(service.getPrice()) // BigDecimal — точная сумма без ошибок округления
                .linkId(saved.getId())     // ID записи PatientPaidService для трассировки
                .build());

        log.info("Assigned paid service {} to patient {}", serviceId, patientId);
        return paidServiceMapper.toLinkResponse(saved);
    }

    /**
     * Отметка об оплате назначенной услуги.
     *
     * Двойная проверка безопасности:
     * 1. Запись PatientPaidService существует (findById)
     * 2. Она принадлежит именно этому пациенту (link.getPatient().getId().equals(patientId))
     *
     * Вторая проверка важна: без неё врач мог бы отметить оплаченной услугу другого пациента
     * просто зная linkId. Это нарушение авторизации на уровне данных (IDOR — Insecure Direct Object Reference).
     */
    @Override
    @Transactional
    public PatientPaidServiceResponse markPaid(Long patientId, Long linkId) {
        PatientPaidService link = ppsRepository.findById(linkId)
                .orElseThrow(() -> new ResourceNotFoundException("PatientPaidService", linkId));
        // IDOR-защита: проверяем что запись принадлежит именно этому пациенту.
        if (!link.getPatient().getId().equals(patientId)) {
            throw new BusinessRuleException("Service link " + linkId + " does not belong to patient " + patientId);
        }
        link.setPaid(true); // отмечаем оплаченной
        return paidServiceMapper.toLinkResponse(ppsRepository.save(link));
    }

    /** Вспомогательный метод: найти активную услугу или выбросить 404. */
    private PaidService findActiveById(Long id) {
        return paidServiceRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("PaidService", id));
    }
}
