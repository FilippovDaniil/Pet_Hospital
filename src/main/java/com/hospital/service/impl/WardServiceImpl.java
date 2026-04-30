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

/**
 * Реализация сервиса для управления палатами (Ward).
 *
 * Основные ответственности сервиса:
 * - Создание палат и привязка их к отделениям (Department).
 * - Госпитализация пациента в палату (admit) с проверкой вместимости.
 * - Выписка пациента из палаты (discharge) с обновлением счётчика мест.
 * - Ведение истории пребывания пациентов в палатах.
 * - Публикация доменных событий о госпитализации/выписке.
 *
 * @Transactional(readOnly = true) на классе — все методы чтения по умолчанию
 * работают в read-only транзакции (оптимизация: Hibernate не отслеживает изменения).
 * Методы записи переопределяют это с помощью собственного @Transactional.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class WardServiceImpl implements WardService {

    private final WardRepository wardRepository;
    private final DepartmentRepository departmentRepository;
    private final PatientRepository patientRepository;

    /**
     * Репозиторий истории пребывания пациентов в палатах.
     * Хранит временные интервалы: admittedAt ... dischargedAt.
     * Открытая запись (dischargedAt = null) означает "пациент сейчас в палате".
     */
    private final WardOccupationHistoryRepository occupationHistoryRepository;

    private final WardMapper wardMapper;
    private final EventPublisher eventPublisher;

    /**
     * Создание новой палаты и привязка к отделению.
     *
     * @Transactional — открывает транзакцию с поддержкой записи.
     *
     * Обратите внимание на порядок операций:
     * 1. Сначала проверяем, что отделение существует (fail-fast).
     * 2. Конвертируем DTO в сущность через маппер.
     * 3. Устанавливаем ссылку на отделение (JPA foreign key).
     * 4. Инициализируем счётчик currentOccupancy = 0 (новая палата пуста).
     * 5. Сохраняем в базу данных.
     */
    @Override
    @Transactional
    public WardResponse create(CreateWardRequest request) {
        // Находим отделение по ID. orElseThrow — если отделение не существует,
        // выбрасываем ResourceNotFoundException (HTTP 404).
        // Это важно делать ДО создания палаты, иначе создадим "потерянную" палату.
        Department dept = departmentRepository.findById(request.getDepartmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Department", request.getDepartmentId()));

        // Конвертируем DTO → сущность через MapStruct маппер.
        Ward ward = wardMapper.toEntity(request);

        // Устанавливаем связь с отделением. JPA запишет department_id в таблицу wards.
        ward.setDepartment(dept);

        // Явно инициализируем счётчик занятых мест. Это защита от null-pointer исключений:
        // если бы мы не установили значение, поле было бы null в базе, и арифметика сломалась бы.
        ward.setCurrentOccupancy(0);

        Ward saved = wardRepository.save(ward);
        log.info("Created ward id={} number={}", saved.getId(), saved.getWardNumber());
        return wardMapper.toResponse(saved);
    }

    /**
     * Получение палаты по ID.
     * Работает в read-only транзакции (унаследованной от класса).
     */
    @Override
    public WardResponse getById(Long id) {
        return wardMapper.toResponse(findById(id));
    }

    /**
     * Получение всех палат со связанными отделениями.
     *
     * findAllWithDepartment() — кастомный запрос с JOIN FETCH.
     * Без JOIN FETCH Hibernate загружал бы отделение для каждой палаты отдельным запросом
     * (проблема N+1 запросов). JOIN FETCH загружает всё одним запросом — это эффективно.
     */
    @Override
    public List<WardResponse> getAll() {
        return wardRepository.findAllWithDepartment().stream()
                .map(wardMapper::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Госпитализация пациента в палату.
     *
     * Это ключевая операция сервиса. Выполняется в одной транзакции и включает:
     * 1. Проверку свободных мест в палате (бизнес-правило).
     * 2. Проверку, что пациент ещё не в палате (бизнес-правило).
     * 3. Привязку пациента к палате.
     * 4. Увеличение счётчика занятых мест.
     * 5. Создание записи в истории пребывания.
     * 6. Публикацию события ADMITTED.
     *
     * @Transactional — все шаги атомарны: либо пациент госпитализирован полностью,
     * либо ничего не изменилось (в случае ошибки будет ROLLBACK).
     */
    @Override
    @Transactional
    public WardResponse admitPatient(Long wardId, Long patientId) {
        Ward ward = findById(wardId);
        Patient patient = patientRepository.findByIdAndActiveTrue(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", patientId));

        // Бизнес-правило 1: в палате должны быть свободные места.
        // freeSlots() — метод сущности Ward: return capacity - currentOccupancy.
        // Проверяем ПЕРЕД изменением данных, чтобы не нарушить инвариант системы.
        if (ward.freeSlots() <= 0) {
            throw new BusinessRuleException(
                    "Ward " + ward.getWardNumber() + " has no free slots (capacity=" + ward.getCapacity() + ")");
        }

        // Бизнес-правило 2: пациент не должен быть уже в какой-либо палате.
        // getCurrentWard() != null означает, что пациент уже госпитализирован.
        // Помещать пациента одновременно в две палаты — логическая ошибка.
        if (patient.getCurrentWard() != null) {
            throw new BusinessRuleException(
                    "Patient " + patientId + " is already in ward " + patient.getCurrentWard().getWardNumber());
        }

        // Шаг 1: Устанавливаем пациенту текущую палату (обновляет FK в таблице patients).
        patient.setCurrentWard(ward);
        patientRepository.save(patient);

        // Шаг 2: Увеличиваем счётчик занятых мест в палате.
        // ВАЖНО: обновляем счётчик и сущность пациента в одной транзакции —
        // это предотвращает race condition, если два запроса госпитализируют
        // пациентов одновременно (транзакционная изоляция).
        ward.setCurrentOccupancy(ward.getCurrentOccupancy() + 1);
        wardRepository.save(ward);

        // Шаг 3: Создаём запись в истории пребывания.
        // admittedAt = текущее время, dischargedAt = null (открытый интервал = "сейчас в палате").
        occupationHistoryRepository.save(WardOccupationHistory.builder()
                .patient(patient)
                .ward(ward)
                .admittedAt(LocalDateTime.now())
                // dischargedAt не устанавливаем — null = пациент ещё в палате
                .build());

        // Шаг 4: Публикуем событие ADMITTED для уведомления других подсистем.
        // Например, система уведомлений может отправить SMS или записать в аудит-лог.
        // UUID.randomUUID() — уникальный ID события для трассировки и идемпотентности.
        eventPublisher.publishAdmissionEvent(AdmissionEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .occurredAt(LocalDateTime.now())
                .patientId(patientId)
                .patientName(patient.getFullName())
                .wardId(wardId)
                .wardNumber(ward.getWardNumber())
                .action(AdmissionEvent.Action.ADMITTED) // тип события: госпитализация
                .build());

        log.info("Patient {} admitted to ward {}", patientId, wardId);
        return wardMapper.toResponse(ward);
    }

    /**
     * Выписка пациента из конкретной палаты.
     *
     * Обратная операция к admitPatient. Включает:
     * 1. Проверку, что пациент действительно находится в указанной палате.
     * 2. Отвязку пациента от палаты.
     * 3. Уменьшение счётчика занятых мест.
     * 4. Закрытие записи в истории (проставление dischargedAt).
     * 5. Публикацию события DISCHARGED.
     *
     * Обратите внимание: dischargePatientFromWard — это выписка из конкретной ПАЛАТЫ.
     * Полная выписка пациента из больницы (со сменой статуса) — в AdminServiceImpl.
     *
     * @Transactional — атомарность всех шагов.
     */
    @Override
    @Transactional
    public WardResponse dischargePatientFromWard(Long wardId, Long patientId) {
        Ward ward = findById(wardId);
        Patient patient = patientRepository.findByIdAndActiveTrue(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", patientId));

        // Проверяем, что пациент находится именно в ЭТОЙ палате.
        // patient.getCurrentWard() == null означает, что пациент вообще не в палате.
        // !equals(wardId) означает, что пациент в другой палате.
        // Оба случая — ошибка запроса (нельзя выписать из палаты, где пациент не лежит).
        if (patient.getCurrentWard() == null || !patient.getCurrentWard().getId().equals(wardId)) {
            throw new BusinessRuleException("Patient " + patientId + " is not in ward " + wardId);
        }

        // Отвязываем пациента от палаты (FK = null в таблице patients).
        patient.setCurrentWard(null);
        patientRepository.save(patient);

        // Уменьшаем счётчик занятых мест.
        // Math.max(0, ...) — защитное программирование: счётчик не может стать отрицательным.
        // Без этой защиты баг в другом месте мог бы привести к отрицательному счётчику.
        ward.setCurrentOccupancy(Math.max(0, ward.getCurrentOccupancy() - 1));
        wardRepository.save(ward);

        // Закрываем открытую запись в истории пребывания.
        // findByPatientIdAndDischargedAtIsNull — находит активную запись (без даты выписки).
        // ifPresent() — выполняет лямбду только если запись найдена.
        // Это безопасная обработка Optional: нет NPE, нет лишних if-проверок.
        occupationHistoryRepository.findByPatientIdAndDischargedAtIsNull(patientId)
                .ifPresent(h -> {
                    h.setDischargedAt(LocalDateTime.now()); // закрываем интервал
                    occupationHistoryRepository.save(h);
                });

        // Публикуем событие DISCHARGED.
        eventPublisher.publishAdmissionEvent(AdmissionEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .occurredAt(LocalDateTime.now())
                .patientId(patientId)
                .patientName(patient.getFullName())
                .wardId(wardId)
                .wardNumber(ward.getWardNumber())
                .action(AdmissionEvent.Action.DISCHARGED) // тип события: выписка из палаты
                .build());

        log.info("Patient {} discharged from ward {}", patientId, wardId);
        return wardMapper.toResponse(ward);
    }

    /**
     * Вспомогательный метод: найти палату по ID или выбросить 404.
     *
     * В отличие от пациентов и врачей, у палат нет флага active (мягкого удаления).
     * Поэтому используем стандартный findById() без условия activeTrue.
     * Палаты удаляются физически (или вообще не удаляются в боевых системах).
     */
    private Ward findById(Long id) {
        return wardRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ward", id));
    }
}
