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

/**
 * Реализация сервиса для управления отделениями больницы.
 *
 * Отличие от Patient и Doctor: отделения физически удаляются (нет soft delete).
 * Причина: отделения — структурные единицы, и удалять их в production следует
 * только после переноса всех врачей и палат в другие отделения.
 * В учебном проекте физическое удаление упрощает реализацию.
 *
 * Особенность — назначение заведующего (@OneToOne с Doctor):
 * При создании и обновлении отделения можно (не обязательно) указать ID заведующего.
 * Поиск врача происходит в сервисе, а не в маппере, потому что маппер не имеет доступа
 * к репозиториям — разделение ответственности.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class DepartmentServiceImpl implements DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final DoctorRepository doctorRepository;   // нужен для назначения заведующего
    private final DepartmentMapper departmentMapper;
    private final EventPublisher eventPublisher;

    /**
     * Создание нового отделения.
     * Опциональное назначение заведующего: если headDoctorId не null — привязываем врача.
     * Маппер toEntity() конвертирует простые поля (name, description, location),
     * но не может обработать headDoctorId → Doctor, потому что не знает о репозиториях.
     * Поэтому связь устанавливается вручную в сервисе.
     */
    @Override
    @Transactional
    public DepartmentResponse create(CreateDepartmentRequest request) {
        Department dept = departmentMapper.toEntity(request);
        if (request.getHeadDoctorId() != null) {
            // Находим врача или выбрасываем 404. Только активные врачи могут быть заведующими.
            Doctor doc = doctorRepository.findByIdAndActiveTrue(request.getHeadDoctorId())
                    .orElseThrow(() -> new ResourceNotFoundException("Doctor", request.getHeadDoctorId()));
            dept.setHeadDoctor(doc);
        }
        Department saved = departmentRepository.save(dept);
        // Публикуем событие DEPARTMENT_CREATED в рамках текущей транзакции.
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

    /** Получение отделения по ID (read-only транзакция из класса). */
    @Override
    public DepartmentResponse getById(Long id) {
        return departmentMapper.toResponse(findById(id));
    }

    /**
     * Получение всех отделений.
     * Здесь нет пагинации — отделений в больнице обычно немного (10-50),
     * поэтому возвращаем полный список. Если отделений много — нужна пагинация.
     * stream().map().collect() — функциональный стиль: маппинг каждого элемента списка.
     */
    @Override
    public List<DepartmentResponse> getAll() {
        return departmentRepository.findAll().stream()
                .map(departmentMapper::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Обновление данных отделения.
     * updateFromRequest() обновляет только simple поля через маппер.
     * Смена заведующего обрабатывается отдельно (нужен поиск врача по ID).
     */
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

    /**
     * Физическое удаление отделения.
     * В отличие от Patient/Doctor здесь нет soft delete.
     * Публикуем событие ПЕРЕД удалением (иначе dept.getId() будет недоступен после delete).
     * В production перед удалением нужно проверить: есть ли активные врачи/палаты в отделении.
     */
    @Override
    @Transactional
    public void delete(Long id) {
        Department dept = findById(id);
        // Публикуем событие до delete() — после удаления объект становится transient (detached).
        eventPublisher.publishDepartmentEvent(DepartmentEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("DEPARTMENT_DELETED")
                .occurredAt(LocalDateTime.now())
                .departmentId(dept.getId())
                .departmentName(dept.getName())
                .build());
        departmentRepository.delete(dept); // физическое DELETE FROM department WHERE id=?
        log.info("Deleted department id={}", id);
    }

    /** Вспомогательный метод: найти отделение или выбросить 404. */
    private Department findById(Long id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department", id));
    }
}
