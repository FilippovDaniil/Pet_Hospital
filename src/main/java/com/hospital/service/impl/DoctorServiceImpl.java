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

/**
 * Реализация сервиса для управления врачами.
 *
 * Отвечает за CRUD операции над врачами и предоставляет возможность
 * просматривать пациентов, прикреплённых к конкретному врачу.
 *
 * Архитектурные паттерны, использованные здесь:
 *
 * @Transactional(readOnly = true) на КЛАССЕ — оптимизация по умолчанию для всех методов чтения.
 * Hibernate не отслеживает изменения сущностей (dirty checking OFF), что ускоряет работу.
 * Методы с записью (@Transactional без readOnly) переопределяют это поведение.
 *
 * Публикация события при создании врача (EventPublisher.publishDoctorEvent):
 * Позволяет другим подсистемам реагировать на появление нового врача без прямой связи.
 * Например, система уведомлений может автоматически создать расписание приёма.
 */
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

    /**
     * Создание нового врача.
     *
     * Опциональная привязка к отделению: врач может быть создан без отделения
     * (departmentId = null), и отделение назначено позже через update().
     * Если departmentId указан — проверяем его существование (fail-fast).
     *
     * После сохранения публикуем событие DOCTOR_CREATED — для интеграции с внешними системами.
     */
    @Override
    @Transactional
    public DoctorResponse create(CreateDoctorRequest request) {
        // Конвертируем DTO → сущность через MapStruct маппер.
        Doctor doctor = doctorMapper.toEntity(request);
        // active = true: новый врач сразу активен. Маппер не устанавливает это поле.
        doctor.setActive(true);

        // Привязываем к отделению, если ID передан. Опциональная операция.
        if (request.getDepartmentId() != null) {
            // Находим отделение или выбрасываем 404 — fail-fast перед сохранением.
            Department dept = departmentRepository.findById(request.getDepartmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Department", request.getDepartmentId()));
            doctor.setDepartment(dept);
        }
        Doctor saved = doctorRepository.save(doctor);

        // Публикуем событие в рамках текущей транзакции (EventPublisher использует MANDATORY propagation).
        // departmentId может быть null если врач создан без отделения — это нормально.
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

    /** Получение врача по ID (только активные). */
    @Override
    public DoctorResponse getById(Long id) {
        return doctorMapper.toResponse(findActiveById(id));
    }

    /**
     * Получение всех активных врачей с пагинацией.
     * findAllByActiveTrue — Spring Data генерирует: SELECT * FROM doctor WHERE active=true + LIMIT/OFFSET.
     */
    @Override
    public PageResponse<DoctorResponse> getAll(Pageable pageable) {
        Page<Doctor> page = doctorRepository.findAllByActiveTrue(pageable);
        return toPageResponse(page.map(doctorMapper::toResponse));
    }

    /**
     * Поиск врачей по специализации с пагинацией.
     * Полезно для назначения: "покажи всех кардиологов" перед тем как выбрать врача пациенту.
     */
    @Override
    public PageResponse<DoctorResponse> getBySpecialty(Specialty specialty, Pageable pageable) {
        Page<Doctor> page = doctorRepository.findBySpecialtyAndActiveTrue(specialty, pageable);
        return toPageResponse(page.map(doctorMapper::toResponse));
    }

    /**
     * Обновление данных врача.
     * Смена отделения: если передан новый departmentId — меняем привязку.
     * MapStruct updateFromRequest() обновляет только non-null поля (NullValuePropertyMappingStrategy.IGNORE).
     */
    @Override
    @Transactional
    public DoctorResponse update(Long id, UpdateDoctorRequest request) {
        Doctor doctor = findActiveById(id);
        // Обновляем простые поля через маппер.
        doctorMapper.updateFromRequest(doctor, request);
        // Обновляем отделение отдельно (маппер не обрабатывает вложенные объекты по ID).
        if (request.getDepartmentId() != null) {
            Department dept = departmentRepository.findById(request.getDepartmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Department", request.getDepartmentId()));
            doctor.setDepartment(dept);
        }
        return doctorMapper.toResponse(doctorRepository.save(doctor));
    }

    /**
     * Мягкое удаление врача (active = false).
     * Физически запись остаётся в БД — важно для ссылочной целостности:
     * история назначений (PatientDoctorHistory) ссылается на врача.
     * Если удалить физически — получим ConstraintViolationException.
     */
    @Override
    @Transactional
    public void softDelete(Long id) {
        Doctor doctor = findActiveById(id);
        doctor.setActive(false); // "невидимый" для бизнес-операций, но остаётся в истории
        doctorRepository.save(doctor);
        log.info("Soft-deleted doctor id={}", id);
    }

    /**
     * Получение всех активных пациентов данного врача с пагинацией.
     * Сначала проверяем существование врача (findActiveById), затем запрашиваем пациентов.
     * Это fail-fast: лучше вернуть 404 для несуществующего врача, чем пустой список.
     */
    @Override
    public PageResponse<PatientResponse> getPatients(Long doctorId, Pageable pageable) {
        findActiveById(doctorId); // проверяем что врач существует и активен
        Page<com.hospital.entity.Patient> page = patientRepository.findByDoctorId(doctorId, pageable);
        return toPageResponse(page.map(patientMapper::toResponse));
    }

    /**
     * Вспомогательный метод: найти активного врача или выбросить 404.
     * findByIdAndActiveTrue — Spring Data: SELECT * FROM doctor WHERE id=? AND active=true.
     */
    private Doctor findActiveById(Long id) {
        return doctorRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor", id));
    }

    /**
     * Вспомогательный метод: конвертирует Page<T> Spring Data в наш PageResponse<T> DTO.
     * Скрывает внутреннее устройство Spring Data Page от клиентов API.
     */
    private <T> PageResponse<T> toPageResponse(Page<T> page) {
        return PageResponse.<T>builder()
                .content(page.getContent())           // данные страницы
                .page(page.getNumber())               // текущая страница (0-based)
                .size(page.getSize())                 // размер страницы
                .totalElements(page.getTotalElements()) // итого записей
                .totalPages(page.getTotalPages())     // итого страниц
                .last(page.isLast())                  // это последняя страница?
                .build();
    }
}
