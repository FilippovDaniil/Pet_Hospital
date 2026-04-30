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

/**
 * Реализация сервиса для работы с пациентами (САМЫЙ ВАЖНЫЙ сервис в системе).
 *
 * Архитектурные решения на уровне класса:
 *
 * @Service — регистрирует этот класс как Spring Bean слоя бизнес-логики.
 *            Spring автоматически обнаруживает его при сканировании компонентов
 *            и управляет его жизненным циклом (создание, внедрение зависимостей, уничтожение).
 *
 * @RequiredArgsConstructor — аннотация Lombok, генерирует конструктор со всеми полями,
 *            помеченными как final. Spring использует этот конструктор для внедрения
 *            зависимостей (Dependency Injection). Это предпочтительный способ DI
 *            (constructor injection), так как делает зависимости явными и неизменяемыми.
 *
 * @Slf4j — аннотация Lombok, создаёт статическое поле log типа Logger.
 *            Позволяет использовать log.info(), log.error() и т.д. для структурированного
 *            логирования без ручного создания логгера.
 *
 * @Transactional(readOnly = true) на КЛАССЕ — ключевое архитектурное решение.
 *            Означает, что ВСЕ методы класса по умолчанию выполняются в транзакции
 *            только для чтения. Это даёт несколько преимуществ:
 *            1. Hibernate не отслеживает изменения сущностей (dirty checking отключён) —
 *               это ускоряет работу при чтении данных.
 *            2. База данных может использовать оптимизации для read-only транзакций
 *               (например, читать с реплики).
 *            3. Если метод чтения случайно попытается изменить данные — получим исключение,
 *               что защищает от случайных мутаций в методах-геттерах.
 *            Методы, которые ИЗМЕНЯЮТ данные, переопределяют это поведение через
 *            собственную аннотацию @Transactional (без readOnly=true).
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PatientServiceImpl implements PatientService {

    /**
     * Бизнес-ограничение: максимальное количество активных пациентов у одного врача.
     * Вынесено в константу, а не "магическое число" в коде — это best practice,
     * так как значение легко найти, изменить в одном месте и понять его смысл.
     */
    private static final int MAX_PATIENTS_PER_DOCTOR = 20;

    // Репозитории — Spring Data JPA интерфейсы для работы с базой данных.
    // Каждый репозиторий отвечает за свою таблицу / сущность.
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final PatientDoctorHistoryRepository historyRepository; // история назначений врачей
    private final PatientPaidServiceRepository ppsRepository;       // связка пациент↔платная услуга

    // Маперы — конвертируют сущности JPA в DTO и обратно (используется MapStruct).
    // Это разделение слоёв: контроллер получает DTO, база данных — сущности.
    private final PatientMapper patientMapper;
    private final PaidServiceMapper paidServiceMapper;

    /**
     * EventPublisher — публикует доменные события (Domain Events).
     * Паттерн позволяет уведомлять другие части системы о значимых событиях
     * (например, "пациенту назначен врач") без прямой зависимости между сервисами.
     * Важно: публикация происходит ВНУТРИ транзакции, чтобы событие и изменение
     * данных были атомарны — либо всё вместе, либо ничего.
     */
    private final EventPublisher eventPublisher;

    /**
     * Создание нового пациента в системе.
     *
     * @Transactional — переопределяет readOnly=true класса и открывает ПОЛНОЦЕННУЮ
     *            транзакцию с поддержкой записи. Все изменения в базе данных в рамках
     *            этого метода либо зафиксируются (COMMIT) при успехе, либо откатятся
     *            (ROLLBACK) при любом исключении.
     *
     * Бизнес-правило 1: СНИЛС должен быть уникальным среди активных пациентов.
     *            СНИЛС — уникальный идентификатор застрахованного лица. Два активных
     *            пациента с одинаковым СНИЛС — это ошибка данных (дубликат).
     *            Мы проверяем только среди АКТИВНЫХ пациентов (activeTrue), потому что
     *            мягко удалённый пациент может быть зарегистрирован снова.
     */
    @Override
    @Transactional
    public PatientResponse create(CreatePatientRequest request) {
        // Проверяем уникальность СНИЛС среди активных пациентов.
        // existsBySnilsAndActiveTrue — Spring Data генерирует этот запрос автоматически
        // по имени метода: SELECT EXISTS(...WHERE snils=? AND active=true).
        // Если пациент с таким СНИЛС уже есть — выбрасываем BusinessRuleException
        // (это наше собственное исключение, которое вернёт HTTP 409 Conflict).
        if (patientRepository.existsBySnilsAndActiveTrue(request.getSnils())) {
            throw new BusinessRuleException("Patient with SNILS " + request.getSnils() + " already exists");
        }

        // Конвертируем DTO запроса в JPA-сущность через маппер.
        // MapStruct генерирует реализацию маппера во время компиляции —
        // это быстрее рефлексии и безопаснее (ошибки обнаруживаются на этапе компиляции).
        Patient patient = patientMapper.toEntity(request);

        // Устанавливаем поля, которые не приходят из запроса, а задаются системой:
        // Дата регистрации — текущая дата (LocalDate.now() без времени).
        patient.setRegistrationDate(LocalDate.now());
        // Начальный статус нового пациента — "на лечении".
        patient.setStatus(PatientStatus.TREATMENT);
        // Пометка "активен" — используется для мягкого удаления (soft delete).
        // active=true означает, что запись "видима" для системы.
        patient.setActive(true);

        // Сохраняем в базу данных. JPA выполнит INSERT и вернёт сущность с заполненным ID.
        Patient saved = patientRepository.save(patient);
        log.info("Created patient id={}", saved.getId());

        // Возвращаем DTO (не сущность!), чтобы не раскрывать внутренние детали сущности
        // на уровне API. PatientResponse содержит только те поля, которые нужны клиенту.
        return patientMapper.toResponse(saved);
    }

    /**
     * Получение пациента по ID.
     *
     * Метод работает в read-only транзакции (унаследованной от класса).
     * Hibernate оптимизирует такой запрос: не отслеживает изменения сущности
     * (нет overhead на dirty checking при завершении транзакции).
     */
    @Override
    public PatientResponse getById(Long id) {
        // findActiveById — вспомогательный приватный метод, который ищет только
        // активных пациентов и выбрасывает ResourceNotFoundException если не найден.
        // Такая инкапсуляция устраняет дублирование кода по всему сервису.
        return patientMapper.toResponse(findActiveById(id));
    }

    /**
     * Получение всех активных пациентов с пагинацией.
     *
     * Pageable — объект, содержащий информацию о номере страницы, размере страницы
     * и сортировке. Spring MVC автоматически создаёт его из query-параметров запроса
     * (?page=0&size=20&sort=lastName,asc).
     *
     * Метод возвращает PageResponse — наш DTO-обёртку над Page<T>, которая содержит
     * как данные, так и метаданные пагинации (totalElements, totalPages и т.д.).
     */
    @Override
    public PageResponse<PatientResponse> getAll(Pageable pageable) {
        // findAllByActiveTrue — Spring Data генерирует запрос: SELECT * FROM patients WHERE active=true
        // с добавлением LIMIT/OFFSET для пагинации и ORDER BY для сортировки.
        Page<Patient> page = patientRepository.findAllByActiveTrue(pageable);
        // page.map() — трансформирует содержимое страницы, сохраняя метаданные пагинации.
        // Мы конвертируем каждую сущность Patient в DTO PatientResponse через маппер.
        return toPageResponse(page.map(patientMapper::toResponse));
    }

    /**
     * Поиск пациентов по строке и/или статусу.
     *
     * Поддерживает комбинированный поиск: по ФИО/СНИЛС (строка q) И фильтрацию по статусу.
     * Оба параметра опциональны — если не переданы, возвращаются все пациенты.
     */
    @Override
    public PageResponse<PatientResponse> search(String q, PatientStatus status, Pageable pageable) {
        // Нормализуем поисковую строку: если пустая или null — передаём null в репозиторий.
        // Это позволяет репозиторию понять: "фильтр по строке не нужен".
        String searchQ = (q != null && !q.isBlank()) ? q.trim() : null;
        // patientRepository.search() — кастомный JPQL/SQL запрос, который умеет
        // работать с nullable параметрами (если null — пропускает соответствующее условие WHERE).
        Page<Patient> page = patientRepository.search(searchQ, status, pageable);
        return toPageResponse(page.map(patientMapper::toResponse));
    }

    /**
     * Обновление данных пациента.
     *
     * @Transactional — полная транзакция с поддержкой записи.
     * MapStruct-маппер updateFromRequest() обновляет только те поля сущности,
     * которые переданы в запросе (null-поля игнорируются при конфигурации NullValuePropertyMappingStrategy).
     * После save() Hibernate выполнит UPDATE только изменённых колонок.
     */
    @Override
    @Transactional
    public PatientResponse update(Long id, UpdatePatientRequest request) {
        // Сначала загружаем существующую сущность из БД (с проверкой active=true).
        // Если пациент не найден — метод упадёт с ResourceNotFoundException до save().
        Patient patient = findActiveById(id);
        // Обновляем поля сущности из DTO запроса через маппер.
        // Так как patient — managed entity (в контексте Hibernate), Hibernate автоматически
        // отследит изменения и выполнит UPDATE при коммите транзакции.
        // Явный вызов save() здесь — хорошая практика для явности намерений.
        patientMapper.updateFromRequest(patient, request);
        return patientMapper.toResponse(patientRepository.save(patient));
    }

    /**
     * Мягкое удаление пациента (Soft Delete).
     *
     * Почему мягкое удаление, а не физическое (DELETE FROM)?
     * 1. Сохраняем историю — данные пациента остаются в БД для аудита и отчётов.
     * 2. Безопасность — случайно удалённого пациента можно восстановить.
     * 3. Целостность — не рвём foreign key связи с историей назначений, палатами и т.д.
     *
     * Физически запись остаётся в таблице, но поле active=false скрывает её
     * от всех запросов типа findAllByActiveTrue / findByIdAndActiveTrue.
     */
    @Override
    @Transactional
    public void softDelete(Long id) {
        Patient patient = findActiveById(id);
        // Просто меняем флаг active с true на false.
        // Теперь этот пациент "невидим" для всех бизнес-операций.
        patient.setActive(false);
        patientRepository.save(patient);
        log.info("Soft-deleted patient id={}", id);
    }

    /**
     * Назначение врача пациенту — САМАЯ СЛОЖНАЯ ОПЕРАЦИЯ в сервисе.
     *
     * Эта операция включает несколько шагов и бизнес-проверок:
     * 1. Проверить существование пациента и врача.
     * 2. Проверить нагрузку врача (не более MAX_PATIENTS_PER_DOCTOR).
     * 3. Закрыть текущую запись в истории (если был предыдущий врач).
     * 4. Назначить нового врача пациенту.
     * 5. Открыть новую запись в истории.
     * 6. Опубликовать доменное событие.
     *
     * Вся операция выполняется в ОДНОЙ транзакции (@Transactional),
     * что гарантирует атомарность: либо все шаги выполнятся, либо ничего не изменится.
     */
    @Override
    @Transactional
    public PatientResponse assignDoctor(Long patientId, Long doctorId) {
        // Шаг 1а: Находим активного пациента.
        // findActiveById выбросит ResourceNotFoundException если пациент не найден или удалён.
        Patient patient = findActiveById(patientId);

        // Шаг 1б: Находим активного врача.
        // orElseThrow — метод Optional, который возвращает значение если оно есть,
        // или выбрасывает исключение через переданный Supplier (лямбда () -> new Exception()).
        // Это идиоматический способ работы с Optional в Java — избегаем явной проверки на null.
        Doctor doctor = doctorRepository.findByIdAndActiveTrue(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor", doctorId));

        // Шаг 2: Проверяем нагрузку врача.
        // Бизнес-правило: один врач не может вести более 20 активных пациентов.
        // countActivePatientsByDoctorId — кастомный запрос COUNT(*) в репозитории.
        // Это важная проверка: без неё врач может получить неограниченную нагрузку,
        // что негативно скажется на качестве медицинской помощи.
        long currentLoad = patientRepository.countActivePatientsByDoctorId(doctorId);
        if (currentLoad >= MAX_PATIENTS_PER_DOCTOR) {
            throw new BusinessRuleException(
                    "Doctor " + doctorId + " already has " + MAX_PATIENTS_PER_DOCTOR + " patients (maximum reached)");
        }

        // Сохраняем ID предыдущего врача для события (нужен для аудита: "кто был до").
        // Тернарный оператор через null-check: если currentDoctor не null — берём его ID,
        // иначе previousDoctorId = null (пациент раньше не имел врача).
        Long previousDoctorId = patient.getCurrentDoctor() != null ? patient.getCurrentDoctor().getId() : null;

        // Шаг 3: Закрываем текущую запись в истории назначений.
        // История назначений ведётся как временные интервалы: assignedFrom ... assignedTo.
        // Открытая запись (assignedTo = null) означает "текущего врача".
        // При смене врача нужно "закрыть" эту запись — проставить дату окончания.
        if (previousDoctorId != null) {
            // findByPatientIdAndAssignedToIsNull — находит открытую запись истории.
            // ifPresent() — безопасная альтернатива проверке != null:
            // выполняет лямбду только если Optional содержит значение.
            historyRepository.findByPatientIdAndAssignedToIsNull(patientId)
                    .ifPresent(h -> {
                        h.setAssignedTo(LocalDateTime.now()); // закрываем интервал текущим временем
                        historyRepository.save(h);
                    });
        }

        // Шаг 4: Назначаем нового врача пациенту.
        // setCurrentDoctor устанавливает внешний ключ в таблице patients.
        patient.setCurrentDoctor(doctor);
        patientRepository.save(patient);

        // Шаг 5: Открываем новую запись в истории назначений.
        // Builder-паттерн (Lombok @Builder): создаём объект через цепочку .field(value).
        // assignedTo намеренно не устанавливаем — null означает "текущий врач".
        historyRepository.save(PatientDoctorHistory.builder()
                .patient(patient)
                .doctor(doctor)
                .assignedFrom(LocalDateTime.now()) // начало нового периода
                .build());                          // assignedTo = null → открытый интервал

        // Шаг 6: Публикуем доменное событие "DOCTOR_CHANGED".
        // EventPublisher.publishPatientEvent() помечен как @Transactional(MANDATORY) —
        // это означает, что метод ТРЕБУЕТ активной транзакции (не создаёт свою).
        // Если вызвать его вне транзакции — получим исключение.
        // Это гарантирует, что событие публикуется ТОЛЬКО внутри транзакции,
        // синхронно с изменениями данных. Таким образом, если транзакция откатится,
        // событие не будет опубликовано (или будет откатано вместе с данными).
        // UUID.randomUUID() создаёт глобально уникальный идентификатор события —
        // используется для идемпотентности и трассировки в распределённых системах.
        eventPublisher.publishPatientEvent(PatientEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("DOCTOR_CHANGED")
                .occurredAt(LocalDateTime.now())
                .patientId(patientId)
                .patientName(patient.getFullName())
                .previousDoctorId(previousDoctorId) // кто был до (null если первый врач)
                .newDoctorId(doctorId)               // кто стал новым врачом
                .build());

        log.info("Patient {} assigned to doctor {}", patientId, doctorId);
        return patientMapper.toResponse(patient);
    }

    /**
     * Получение списка платных услуг, назначенных пациенту.
     *
     * Обратите внимание: перед запросом услуг мы сначала вызываем findActiveById(patientId).
     * Зачем? Это "fail-fast" проверка: если пациент не существует или удалён,
     * мы сразу вернём 404, а не пустой список (что было бы вводящим в заблуждение поведением).
     */
    @Override
    public List<PatientPaidServiceResponse> getServices(Long patientId) {
        // Проверяем что пациент существует и активен (результат не используется).
        findActiveById(patientId);
        // Загружаем все связки пациент↔услуга и конвертируем в DTO.
        // Stream API: stream() → map() → collect() — функциональный стиль обработки коллекций.
        return ppsRepository.findByPatientId(patientId).stream()
                .map(paidServiceMapper::toLinkResponse)
                .collect(Collectors.toList());
    }

    /**
     * Вспомогательный метод: ищет активного пациента по ID.
     *
     * Инкапсулирует повторяющийся паттерн "найди или выброси исключение".
     * Используется во всех методах, требующих существующего активного пациента.
     *
     * findByIdAndActiveTrue — Spring Data автоматически генерирует запрос:
     * SELECT * FROM patients WHERE id=? AND active=true
     *
     * Возвращает Optional<Patient> — контейнер, который либо содержит пациента,
     * либо пуст. orElseThrow() разворачивает Optional:
     * - если значение есть → возвращает его
     * - если пусто → выбрасывает ResourceNotFoundException (маппится в HTTP 404)
     */
    private Patient findActiveById(Long id) {
        return patientRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", id));
    }

    /**
     * Вспомогательный метод: конвертирует Page<T> в наш PageResponse<T>.
     *
     * Page<T> — объект Spring Data, содержащий данные текущей страницы + метаданные.
     * PageResponse<T> — наш DTO, который мы возвращаем клиенту через REST API.
     *
     * Почему не возвращаем Page<T> напрямую? Потому что Page<T> содержит
     * внутренние детали Spring Data, а мы хотим контролировать структуру API
     * и не "протекать" реализацию наружу (принцип инкапсуляции).
     *
     * Builder-паттерн из Lombok (@Builder в PageResponse) — удобный способ
     * создания объектов с множеством полей без длинных конструкторов.
     */
    private <T> PageResponse<T> toPageResponse(Page<T> page) {
        return PageResponse.<T>builder()
                .content(page.getContent())           // данные текущей страницы
                .page(page.getNumber())               // номер текущей страницы (0-based)
                .size(page.getSize())                 // размер страницы
                .totalElements(page.getTotalElements()) // общее количество элементов
                .totalPages(page.getTotalPages())     // общее количество страниц
                .last(page.isLast())                  // это последняя страница?
                .build();
    }
}
