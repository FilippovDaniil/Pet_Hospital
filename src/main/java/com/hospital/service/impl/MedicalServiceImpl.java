package com.hospital.service.impl;

import com.hospital.dto.request.CreateMedicalDocumentRequest;
import com.hospital.dto.request.CreatePatientNoteRequest;
import com.hospital.dto.response.MedicalDocumentResponse;
import com.hospital.dto.response.PatientHistoryResponse;
import com.hospital.dto.response.PatientNoteResponse;
import com.hospital.entity.*;
import com.hospital.exception.ResourceNotFoundException;
import com.hospital.repository.*;
import com.hospital.service.MedicalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Реализация сервиса медицинской документации и истории болезни пациентов.
 *
 * Сервис охватывает два типа медицинских записей:
 *
 *   1. MedicalDocument — официальные документы, выдаваемые врачом:
 *        PRESCRIPTION (рецепт), REFERRAL (направление к специалисту),
 *        SICK_LEAVE (больничный лист), ANALYSIS_ORDER (направление на анализы),
 *        CERTIFICATE (справка). Видны как врачу, так и пациенту-клиенту.
 *
 *   2. PatientNote — записи врача в истории болезни:
 *        DIAGNOSIS (диагноз), OBSERVATION (наблюдение), NOTE (заметка).
 *        Имеют флаг visibleToClient — врач сам решает, показывать ли запись
 *        пациенту через клиентский портал.
 *
 * Разделение между «документом» и «заметкой» — намеренное архитектурное решение:
 *   - Документы имеют юридическую силу, срок действия (validUntil) и стандартизированный
 *     заголовок. Их нельзя редактировать после создания.
 *   - Заметки — рабочие записи врача. Часть из них приватная (visibleToClient=false)
 *     и не доступна пациенту никогда.
 *
 * --- @Transactional(readOnly = true) на уровне класса ---
 * Паттерн «read-by-default»: большинство методов выполняют SELECT. Класс помечен
 * readOnly=true, что даёт Hibernate два преимущества:
 *   а) Пропускает flush-фазу (dirty checking) в конце транзакции — не нужно
 *      сравнивать snapshot каждой загруженной сущности с её текущим состоянием.
 *   б) Сообщает драйверу и connection pool о том, что транзакция read-only —
 *      некоторые СУБД (PostgreSQL, MySQL) оптимизируют план выполнения.
 * Методы с INSERT перегружают аннотацию через @Transactional (полная транзакция).
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MedicalServiceImpl implements MedicalService {

    // ─── Зависимости (внедряются через конструктор, сгенерированный Lombok) ──

    /**
     * Репозиторий медицинских документов. Содержит методы поиска
     * как по ID пациента (для врача), так и по clientUserId (для портала клиента),
     * что позволяет не хранить userId непосредственно в сущности Patient.
     */
    private final MedicalDocumentRepository medicalDocumentRepository;

    /**
     * Репозиторий заметок пациента. Ключевой метод — findVisibleByClientUserId,
     * который фильтрует только записи с visibleToClient=true при запросе
     * со стороны клиентского портала.
     */
    private final PatientNoteRepository patientNoteRepository;

    /**
     * Репозиторий пациентов. Все поиски используют findByIdAndActiveTrue,
     * что реализует soft delete: физически удалённых пациентов нет,
     * есть только пациенты с active=false, которые «невидимы» для всех запросов.
     */
    private final PatientRepository patientRepository;

    /**
     * Репозиторий врачей. Используется для поиска профиля Doctor по linkedUserId.
     * Важно различать: User — учётная запись (логин/пароль/роль),
     * Doctor — профессиональный профиль (специальность, кабинет, отделение).
     * Они связаны через Doctor.linkedUser, но это разные таблицы.
     */
    private final DoctorRepository doctorRepository;

    // ─────────────────────────────────────────────────────────────────────────
    // Публичные методы интерфейса MedicalService
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Создаёт новый медицинский документ от имени врача.
     *
     * Двухэтапная валидация перед сохранением — намеренный порядок:
     *   1. Сначала проверяем врача: если у User нет связанного профиля Doctor,
     *      создание документа лишено смысла — в документе должна быть ссылка
     *      на конкретного врача со специальностью и кабинетом.
     *   2. Потом проверяем пациента: только активные пациенты (active=true)
     *      могут получать новые документы. Запрос к мягко-удалённому пациенту
     *      возвращает 404 как если бы пациента не существовало.
     *
     * Почему используется findByLinkedUserIdAndActiveTrue, а не findByLinkedUserId:
     * Врача могут деактивировать (уволить) в системе без удаления его учётной
     * записи. Проверка activeTrue гарантирует, что уволенный врач не сможет
     * создавать новые документы, даже если его токен ещё действителен (JWT живёт 24ч).
     *
     * @param request    DTO с patientId, type, title, content, validUntil
     * @param doctorUser User сущность из SecurityContext — аутентифицированный врач
     */
    @Override
    @Transactional
    public MedicalDocumentResponse createDocument(CreateMedicalDocumentRequest request, User doctorUser) {
        // Шаг 1: Находим профиль врача по User.id.
        // doctorUser.getId() — это id в таблице users, а не в таблице doctors.
        // findByLinkedUserIdAndActiveTrue выполняет JOIN между doctors и users,
        // возвращая только активных врачей, у которых linked_user_id совпадает.
        Doctor doctor = doctorRepository.findByLinkedUserIdAndActiveTrue(doctorUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Профиль врача для пользователя не найден"));

        // Шаг 2: Находим активного пациента.
        // Метод findByIdAndActiveTrue — это пример именованного запроса Spring Data,
        // транслируемого в: SELECT * FROM patients WHERE id=? AND active=true.
        // Это предпочтительнее findById() + ручной проверки .isActive(), так как
        // в одном запросе и находим, и проверяем soft-delete флаг.
        Patient patient = patientRepository.findByIdAndActiveTrue(request.getPatientId())
                .orElseThrow(() -> new ResourceNotFoundException("Пациент #" + request.getPatientId() + " не найден"));

        // Создаём документ. Builder-паттерн гарантирует явное указание всех
        // обязательных полей. Поля issuedAt и active заполняются автоматически:
        //   issuedAt — через @PrePersist или DEFAULT NOW() в миграции,
        //   active   — DEFAULT true в миграции.
        // validUntil — опциональное поле (null означает бессрочный документ,
        // например для некоторых справок).
        MedicalDocument doc = medicalDocumentRepository.save(
                MedicalDocument.builder()
                        .patient(patient)
                        .doctor(doctor)
                        .type(request.getType())
                        .title(request.getTitle())
                        .content(request.getContent())
                        .validUntil(request.getValidUntil())
                        .build()
        );

        // Конвертируем в DTO и возвращаем. После save() сущность содержит
        // назначенный БД id и значения, выставленные через DEFAULT,
        // поэтому DTO будет полностью заполнен.
        return toDocumentResponse(doc);
    }

    /**
     * Возвращает все документы указанного пациента — для просмотра врачом.
     *
     * Метод не требует дополнительной авторизации по пациенту: контроль доступа
     * уже выполнен на уровне SecurityConfig (только ROLE_DOCTOR и ROLE_ADMIN
     * могут вызывать соответствующий эндпоинт контроллера).
     *
     * Параметр doctorUser передаётся в сигнатуре «для контекста» — в текущей
     * реализации он не используется, но может понадобиться при будущем ограничении:
     * «врач видит только документы пациентов своего отделения».
     *
     * Почему нет проверки activeTrue для пациента здесь:
     * Врач может законно просматривать документы пациента, чья запись была
     * деактивирована (выписан, умер). Документы — исторические данные, которые
     * не должны «исчезать» при soft delete пациента.
     */
    @Override
    public List<MedicalDocumentResponse> getPatientDocuments(Long patientId, User doctorUser) {
        // findByPatientId — метод Spring Data: SELECT * FROM medical_documents WHERE patient_id=?
        // Репозиторий должен использовать JOIN FETCH patient и JOIN FETCH doctor,
        // чтобы toDocumentResponse() мог обращаться к doc.getPatient().getFullName()
        // и doc.getDoctor().getFullName() без дополнительных SELECT.
        return medicalDocumentRepository.findByPatientId(patientId).stream()
                .map(this::toDocumentResponse)
                .toList();
    }

    /**
     * Возвращает все документы текущего клиента — для клиентского портала.
     *
     * Ключевое отличие от getPatientDocuments: поиск ведётся не по patientId,
     * а по clientUserId (id в таблице users). Это намеренная денормализация:
     * клиент может иметь связанную запись Patient, но пациент создаётся персоналом,
     * а clientUser — это аккаунт самого человека на портале.
     *
     * Метод findByPatientClientUserId транслируется репозиторием в:
     *   SELECT d FROM MedicalDocument d
     *   JOIN d.patient p
     *   JOIN p.clientUser u
     *   WHERE u.id = :clientUserId
     * Это JOIN через две связанные сущности (Document → Patient → User).
     *
     * Безопасность: клиент передаёт clientUser из SecurityContext, а не из
     * параметра запроса, поэтому он никогда не может получить документы другого
     * клиента, подставив чужой userId.
     */
    @Override
    public List<MedicalDocumentResponse> getMyDocuments(User clientUser) {
        return medicalDocumentRepository.findByPatientClientUserId(clientUser.getId()).stream()
                .map(this::toDocumentResponse)
                .toList();
    }

    /**
     * Создаёт новую заметку врача в истории болезни пациента.
     *
     * Логика идентична createDocument: двухэтапная валидация (врач → пациент),
     * затем сохранение. Ключевое отличие — поле visibleToClient:
     *   - true:  заметка видна пациенту через клиентский портал
     *   - false: заметка видна только медицинскому персоналу
     *
     * Почему это решение принимает врач, а не администратор:
     * Врач лучше знает, какую информацию можно раскрывать пациенту.
     * Например, «онкологический диагноз» может не показываться пациенту до
     * личной беседы с врачом. Это соответствует принципу медицинской этики.
     *
     * @param request    DTO с patientId, type, content, visibleToClient
     * @param doctorUser аутентифицированный врач из SecurityContext
     */
    @Override
    @Transactional
    public PatientNoteResponse createNote(CreatePatientNoteRequest request, User doctorUser) {
        // Те же два шага валидации, что и в createDocument.
        // Повторение не нарушает DRY: валидация специфична для каждого метода
        // (разные исключения, разные репозитории), а вынос в общий приватный метод
        // лишь скрыл бы логику, усложнив чтение.
        Doctor doctor = doctorRepository.findByLinkedUserIdAndActiveTrue(doctorUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Профиль врача для пользователя не найден"));
        Patient patient = patientRepository.findByIdAndActiveTrue(request.getPatientId())
                .orElseThrow(() -> new ResourceNotFoundException("Пациент #" + request.getPatientId() + " не найден"));

        // Сохраняем заметку. createdAt устанавливается автоматически через
        // @CreationTimestamp Hibernate или DEFAULT NOW() в миграции.
        PatientNote note = patientNoteRepository.save(
                PatientNote.builder()
                        .patient(patient)
                        .doctor(doctor)
                        .type(request.getType())
                        .content(request.getContent())
                        .visibleToClient(request.isVisibleToClient()) // явное решение врача
                        .build()
        );
        return toNoteResponse(note);
    }

    /**
     * Возвращает полную историю болезни пациента для просмотра врачом.
     *
     * Метод агрегирует два типа данных — заметки и документы — в единый объект
     * PatientHistoryResponse. Это удобный «фасад» для UI: врачу не нужно делать
     * два отдельных запроса.
     *
     * Порядок запросов:
     *   1. Загружаем пациента (с проверкой active=true) — чтобы получить имя
     *      и убедиться в корректности patientId.
     *   2. Загружаем заметки (ВСЕ — без фильтра visibleToClient, врач видит всё).
     *   3. Загружаем документы.
     *
     * Оба списка могут быть пустыми — это нормально для нового пациента. В таком
     * случае PatientHistoryResponse будет содержать имя пациента и пустые списки,
     * что корректно отображается в UI как «история болезни пуста».
     *
     * @param patientId  ID пациента (из URL)
     * @param doctorUser аутентифицированный врач (используется для аудита в будущем)
     */
    @Override
    public PatientHistoryResponse getPatientHistory(Long patientId, User doctorUser) {
        // Загружаем пациента для получения имени в ответе.
        // Даже если списки заметок/документов пусты, имя пациента должно быть
        // в ответе, чтобы UI мог отобразить заголовок «История болезни: Иванов И.И.»
        Patient patient = patientRepository.findByIdAndActiveTrue(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Пациент #" + patientId + " не найден"));

        // findByPatientId — без фильтра по visibleToClient. Врач видит ВСЕ заметки,
        // включая приватные. Это принципиальное отличие от getMyHistory (клиентский метод).
        List<PatientNoteResponse> notes = patientNoteRepository.findByPatientId(patientId).stream()
                .map(this::toNoteResponse).toList();

        // Загружаем все документы пациента.
        List<MedicalDocumentResponse> docs = medicalDocumentRepository.findByPatientId(patientId).stream()
                .map(this::toDocumentResponse).toList();

        return PatientHistoryResponse.builder()
                .patientId(patient.getId())
                .patientName(patient.getFullName())
                .notes(notes)
                .documents(docs)
                .build();
    }

    /**
     * Возвращает историю болезни для текущего аутентифицированного клиента.
     *
     * Это «клиентская» версия getPatientHistory с двумя ключевыми ограничениями:
     *   1. Только записи самого клиента (по clientUserId, не по patientId).
     *   2. Только заметки с visibleToClient=true — приватные записи врача скрыты.
     *
     * Особенность определения имени пациента:
     * В отличие от getPatientHistory, здесь нет отдельного запроса к patientRepository.
     * Имя извлекается из первой доступной записи (заметки или документа). Это
     * оптимизация: лишний SELECT к patients не нужен, если данные уже есть в списках.
     * Если и списки пусты — возвращаем пустую строку (новый клиент без истории).
     *
     * Почему patientId отсутствует в ответе для клиента:
     * Клиент не должен знать внутренний ID своей записи в таблице patients.
     * Это внутренний идентификатор HIS, не имеющий смысла для пациента-клиента.
     * Отсутствие patientId в ответе — осознанное решение по минимизации раскрытия
     * внутренней структуры БД во внешнем API (принцип information hiding).
     *
     * getFirst() vs get(0):
     * notes.getFirst() — метод Java 21 SequencedCollection. Семантически идентичен
     * get(0), но читается более выразительно. Безопасен, так как вызывается только
     * после проверки !notes.isEmpty().
     *
     * @param clientUser аутентифицированный клиент из SecurityContext
     */
    @Override
    public PatientHistoryResponse getMyHistory(User clientUser) {
        // findVisibleByClientUserId — кастомный JPQL-запрос в репозитории:
        //   SELECT n FROM PatientNote n
        //   JOIN n.patient p
        //   JOIN p.clientUser u
        //   WHERE u.id = :clientUserId AND n.visibleToClient = true
        // Фильтр visibleToClient=true — это бизнес-правило конфиденциальности.
        List<PatientNoteResponse> notes = patientNoteRepository.findVisibleByClientUserId(clientUser.getId()).stream()
                .map(this::toNoteResponse).toList();

        // findByPatientClientUserId — аналогичный JOIN через Patient → clientUser.
        // Документы показываются все: нет такого понятия как «приватный документ»,
        // документ либо существует для пациента, либо нет.
        List<MedicalDocumentResponse> docs = medicalDocumentRepository.findByPatientClientUserId(clientUser.getId()).stream()
                .map(this::toDocumentResponse).toList();

        // Определяем имя пациента без дополнительного запроса к БД.
        // Алгоритм: если есть хоть одна запись — берём имя из неё.
        // Иначе — пустая строка (клиент ещё не имеет медицинской истории в системе).
        String patientName = notes.isEmpty() && docs.isEmpty() ? ""
                : (!notes.isEmpty() ? notes.get(0).getPatientName() : docs.get(0).getPatientName());

        return PatientHistoryResponse.builder()
                // patientId намеренно не выставляется для клиентского ответа
                .patientName(patientName)
                .notes(notes)
                .documents(docs)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Приватные вспомогательные методы (маппинг entity → DTO)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Преобразует сущность MedicalDocument в DTO MedicalDocumentResponse.
     *
     * Паттерн маппинга «entity → DTO» реализован вручную (без MapStruct).
     * Причина: некоторые поля требуют нетривиальной обработки:
     *   - type.name() — конвертация enum в строку для JSON-сериализации
     *   - typeLabel — человекочитаемое название на русском (через docTypeLabel())
     *   - doctorSpecialty.name() — аналогично для специальности
     *
     * Почему DTO содержит и type (строка enum), и typeLabel (русское название):
     * type используется фронтендом для программной логики (if type === 'PRESCRIPTION'),
     * typeLabel — для отображения в UI. Разделение позволяет менять русские подписи
     * без изменения логики фронтенда.
     *
     * Обращение к doc.getPatient().getFullName() и doc.getDoctor().getFullName()
     * безопасно только при открытой Hibernate-сессии. Если patient и doctor
     * загружены с FetchType.LAZY, первое обращение к getFullName() инициирует
     * SELECT. Чтобы избежать N+1, репозиторий findByPatientId должен использовать
     * JOIN FETCH doctor JOIN FETCH patient.
     *
     * @param doc сущность медицинского документа с загруженными patient и doctor
     */
    private MedicalDocumentResponse toDocumentResponse(MedicalDocument doc) {
        return MedicalDocumentResponse.builder()
                .id(doc.getId())
                .patientId(doc.getPatient().getId())
                .patientName(doc.getPatient().getFullName())
                .doctorId(doc.getDoctor().getId())
                .doctorName(doc.getDoctor().getFullName())
                .doctorSpecialty(doc.getDoctor().getSpecialty().name()) // enum → строка
                .type(doc.getType().name())             // машиночитаемый тип для фронтенда
                .typeLabel(docTypeLabel(doc.getType())) // человекочитаемое название для UI
                .title(doc.getTitle())
                .content(doc.getContent())
                .issuedAt(doc.getIssuedAt())            // дата создания документа (автоматически)
                .validUntil(doc.getValidUntil())        // срок действия (null = бессрочный)
                .active(doc.isActive())                 // false если документ аннулирован
                .build();
    }

    /**
     * Преобразует сущность PatientNote в DTO PatientNoteResponse.
     *
     * Структурно аналогичен toDocumentResponse, но для заметок.
     * Включает поле visibleToClient — это важно для UI врача: в интерфейсе
     * персонала отображается иконка/метка «видно пациенту» рядом с записью,
     * чтобы врач помнил, что написал.
     *
     * Почему в DTO дублируются patientName и doctorName:
     * DTO — это данные для конкретного HTTP-ответа, а не нормализованная структура.
     * Фронтенд не должен делать дополнительные запросы для отображения имён.
     * Денормализация в DTO — это осознанный компромисс между чистотой данных
     * и удобством использования API.
     *
     * @param note сущность заметки с загруженными patient и doctor
     */
    private PatientNoteResponse toNoteResponse(PatientNote note) {
        return PatientNoteResponse.builder()
                .id(note.getId())
                .patientId(note.getPatient().getId())
                .patientName(note.getPatient().getFullName())
                .doctorId(note.getDoctor().getId())
                .doctorName(note.getDoctor().getFullName())
                .type(note.getType().name())             // машиночитаемый тип
                .typeLabel(noteTypeLabel(note.getType())) // русское название для UI
                .content(note.getContent())
                .visibleToClient(note.isVisibleToClient()) // флаг видимости для пациента
                .createdAt(note.getCreatedAt())
                .build();
    }

    /**
     * Возвращает русскоязычное название типа медицинского документа.
     *
     * Вынесено в отдельный метод по принципу Single Responsibility:
     * маппинг enum → локализованная строка — отдельная ответственность,
     * не смешиваемая с логикой построения DTO.
     *
     * Используется exhaustive switch expression (Java 14+): компилятор требует
     * покрыть ВСЕ значения MedicalDocumentType. Если добавить новый тип в enum
     * и забыть добавить его здесь — проект не скомпилируется. Это статическая
     * гарантия полноты маппинга, которую не даёт if-else цепочка.
     *
     * В production следует заменить на ResourceBundle/MessageSource для
     * поддержки нескольких языков (i18n), но для учебного проекта хардкод
     * русских строк допустим.
     *
     * @param type тип документа из enum MedicalDocumentType
     * @return человекочитаемое название на русском языке
     */
    private String docTypeLabel(MedicalDocumentType type) {
        return switch (type) {
            case PRESCRIPTION   -> "Рецепт";
            case REFERRAL       -> "Направление";
            case SICK_LEAVE     -> "Больничный лист";
            case ANALYSIS_ORDER -> "Направление на анализы";
            case CERTIFICATE    -> "Справка";
        };
    }

    /**
     * Возвращает русскоязычное название типа заметки врача.
     *
     * Аналог docTypeLabel для enum PatientNoteType. Те же принципы:
     * exhaustive switch, локализация, вынос в отдельный метод.
     *
     * Три типа заметок различаются по намерению:
     *   DIAGNOSIS   — формальный диагноз, часть медицинской документации
     *   OBSERVATION — динамическое наблюдение (например, «улучшение на 3-й день»)
     *   NOTE        — свободная заметка без строгого формата
     *
     * @param type тип заметки из enum PatientNoteType
     * @return человекочитаемое название на русском языке
     */
    private String noteTypeLabel(PatientNoteType type) {
        return switch (type) {
            case DIAGNOSIS   -> "Диагноз";
            case OBSERVATION -> "Наблюдение";
            case NOTE        -> "Заметка";
        };
    }
}
