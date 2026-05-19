package com.hospital.service;

import com.hospital.dto.request.CreateMedicalDocumentRequest;
import com.hospital.dto.request.CreatePatientNoteRequest;
import com.hospital.dto.response.MedicalDocumentResponse;
import com.hospital.dto.response.PatientHistoryResponse;
import com.hospital.dto.response.PatientNoteResponse;
import com.hospital.entity.*;
import com.hospital.exception.ResourceNotFoundException;
import com.hospital.repository.DoctorRepository;
import com.hospital.repository.MedicalDocumentRepository;
import com.hospital.repository.PatientNoteRepository;
import com.hospital.repository.PatientRepository;
import com.hospital.service.impl.MedicalServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Юнит-тесты для MedicalServiceImpl.
 *
 * Стратегия тестирования:
 *   - Все репозитории (зависимости сервиса) заменяются Mockito-заглушками.
 *   - Тестируется только бизнес-логика сервиса без обращения к реальной БД.
 *   - Покрываются «счастливый путь» и граничные случаи:
 *     ненайденный профиль врача, ненайденный пациент, фильтрация visibleToClient.
 *
 * Почему @PrePersist не срабатывает в юнит-тестах:
 *   MedicalDocument.onCreate() и PatientNote.onCreate() вызываются JPA перед INSERT.
 *   В юнит-тестах Hibernate не участвует, поэтому issuedAt и createdAt
 *   устанавливаются вручную при построении объектов в setUp().
 */
@ExtendWith(MockitoExtension.class)
class MedicalServiceTest {

    // ─── Заглушки зависимостей ────────────────────────────────────────────────

    /** Репозиторий медицинских документов — заменяется mock-объектом. */
    @Mock
    private MedicalDocumentRepository medicalDocumentRepository;

    /** Репозиторий заметок врача — заменяется mock-объектом. */
    @Mock
    private PatientNoteRepository patientNoteRepository;

    /** Репозиторий пациентов — заменяется mock-объектом. */
    @Mock
    private PatientRepository patientRepository;

    /** Репозиторий врачей — заменяется mock-объектом. */
    @Mock
    private DoctorRepository doctorRepository;

    /**
     * Тестируемый сервис. Mockito внедряет все @Mock-поля через конструктор
     * (благодаря @RequiredArgsConstructor в MedicalServiceImpl).
     */
    @InjectMocks
    private MedicalServiceImpl medicalService;

    // ─── Тестовые данные ──────────────────────────────────────────────────────

    /** Учётная запись врача в таблице users. */
    private User doctorUser;

    /** Учётная запись клиента в таблице users. */
    private User clientUser;

    /** Профиль врача в таблице doctor (содержит specialty, cabinetNumber и т.д.). */
    private Doctor doctor;

    /** Активный пациент с привязанным clientUser. */
    private Patient patient;

    /** Готовый медицинский документ для использования в ответах mock. */
    private MedicalDocument document;

    /** Готовая заметка врача для использования в ответах mock. */
    private PatientNote note;

    @BeforeEach
    void setUp() {
        // Учётная запись врача — только для поиска профиля Doctor через linkedUserId.
        doctorUser = User.builder()
                .id(2L).username("doctor1").fullName("Иванов Сергей Петрович")
                .role(Role.ROLE_DOCTOR).active(true).build();

        // Учётная запись клиента — используется в getMyDocuments/getMyHistory.
        clientUser = User.builder()
                .id(3L).username("client1").fullName("Петров Алексей")
                .role(Role.ROLE_CLIENT).active(true).build();

        // Профиль врача — связан с doctorUser через linkedUser.id == doctorUser.id.
        doctor = Doctor.builder()
                .id(10L).fullName("Иванов Сергей Петрович")
                .specialty(Specialty.THERAPIST).cabinetNumber("101")
                .active(true).linkedUser(doctorUser).build();

        // Активный пациент с привязкой к клиентскому аккаунту.
        patient = Patient.builder()
                .id(1L).fullName("Петров Алексей Иванович")
                .birthDate(LocalDate.of(1990, 5, 15))
                .gender(Gender.MALE).snils("123-456-789 00")
                .registrationDate(LocalDate.now())
                .active(true).clientUser(clientUser).build();

        // Документ со всеми обязательными полями (issuedAt задаём вручную: @PrePersist не работает в тестах).
        document = MedicalDocument.builder()
                .id(100L).patient(patient).doctor(doctor)
                .type(MedicalDocumentType.PRESCRIPTION)
                .title("Рецепт: Амоксициллин").content("Принимать по 500мг 3 раза в день")
                .issuedAt(LocalDateTime.now()).active(true).build();

        // Заметка, скрытая от пациента (visibleToClient=false по умолчанию через @Builder.Default).
        note = PatientNote.builder()
                .id(200L).patient(patient).doctor(doctor)
                .type(PatientNoteType.DIAGNOSIS)
                .content("ОРЗ, средней тяжести").visibleToClient(false)
                .createdAt(LocalDateTime.now()).build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // createDocument
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Если у аутентифицированного пользователя нет профиля Doctor (новый сотрудник,
     * не привязан к записи в таблице doctor) — бросаем ResourceNotFoundException.
     * Сервис сначала ищет врача, только потом пациента.
     */
    @Test
    void createDocument_whenDoctorProfileNotFound_throwsResourceNotFoundException() {
        // doctorUser.id = 2L — профиля врача для него нет.
        when(doctorRepository.findByLinkedUserIdAndActiveTrue(2L)).thenReturn(Optional.empty());

        CreateMedicalDocumentRequest request = new CreateMedicalDocumentRequest();
        request.setPatientId(1L);
        request.setType(MedicalDocumentType.PRESCRIPTION);
        request.setTitle("Рецепт");
        request.setContent("Содержание");

        assertThatThrownBy(() -> medicalService.createDocument(request, doctorUser))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("врача");

        // Если врач не найден — до поиска пациента дело не доходит.
        verify(patientRepository, never()).findByIdAndActiveTrue(anyLong());
    }

    /**
     * Если пациент не найден или мягко удалён (active=false) — бросаем ResourceNotFoundException.
     * Сначала находим врача, только потом убеждаемся в существовании пациента.
     */
    @Test
    void createDocument_whenPatientNotFound_throwsResourceNotFoundException() {
        when(doctorRepository.findByLinkedUserIdAndActiveTrue(2L)).thenReturn(Optional.of(doctor));
        // findByIdAndActiveTrue возвращает пустой Optional → пациент не найден.
        when(patientRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.empty());

        CreateMedicalDocumentRequest request = new CreateMedicalDocumentRequest();
        request.setPatientId(1L);
        request.setType(MedicalDocumentType.PRESCRIPTION);
        request.setTitle("Рецепт");
        request.setContent("Содержание");

        assertThatThrownBy(() -> medicalService.createDocument(request, doctorUser))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("1"); // сообщение содержит patientId

        // save() вызываться не должен.
        verify(medicalDocumentRepository, never()).save(any());
    }

    /**
     * «Счастливый путь»: врач и пациент найдены, документ сохраняется.
     * Проверяем, что save() был вызван и ответ содержит корректные данные.
     */
    @Test
    void createDocument_success_savesDocumentAndReturnsResponse() {
        when(doctorRepository.findByLinkedUserIdAndActiveTrue(2L)).thenReturn(Optional.of(doctor));
        when(patientRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(patient));
        // save() возвращает document с заполненным id и issuedAt.
        when(medicalDocumentRepository.save(any(MedicalDocument.class))).thenReturn(document);

        CreateMedicalDocumentRequest request = new CreateMedicalDocumentRequest();
        request.setPatientId(1L);
        request.setType(MedicalDocumentType.PRESCRIPTION);
        request.setTitle("Рецепт: Амоксициллин");
        request.setContent("Принимать по 500мг 3 раза в день");

        MedicalDocumentResponse response = medicalService.createDocument(request, doctorUser);

        // Проверяем, что save вызван ровно один раз.
        verify(medicalDocumentRepository).save(any(MedicalDocument.class));

        // Проверяем содержимое ответа.
        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getPatientName()).isEqualTo("Петров Алексей Иванович");
        assertThat(response.getDoctorName()).isEqualTo("Иванов Сергей Петрович");
        assertThat(response.getType()).isEqualTo("PRESCRIPTION");
        assertThat(response.getTypeLabel()).isEqualTo("Рецепт");
        assertThat(response.getTitle()).isEqualTo("Рецепт: Амоксициллин");
        assertThat(response.isActive()).isTrue();
        assertThat(response.getIssuedAt()).isNotNull();
    }

    /**
     * createDocument с validUntil (рецепт на 30 дней): поле должно присутствовать в ответе.
     */
    @Test
    void createDocument_withValidUntil_includesExpiryDateInResponse() {
        LocalDate expiry = LocalDate.now().plusDays(30);
        MedicalDocument docWithExpiry = MedicalDocument.builder()
                .id(101L).patient(patient).doctor(doctor)
                .type(MedicalDocumentType.PRESCRIPTION)
                .title("Рецепт с датой").content("Принимать 10 дней")
                .issuedAt(LocalDateTime.now()).validUntil(expiry).active(true).build();

        when(doctorRepository.findByLinkedUserIdAndActiveTrue(2L)).thenReturn(Optional.of(doctor));
        when(patientRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(patient));
        when(medicalDocumentRepository.save(any())).thenReturn(docWithExpiry);

        CreateMedicalDocumentRequest request = new CreateMedicalDocumentRequest();
        request.setPatientId(1L);
        request.setType(MedicalDocumentType.PRESCRIPTION);
        request.setTitle("Рецепт с датой");
        request.setContent("Принимать 10 дней");
        request.setValidUntil(expiry);

        MedicalDocumentResponse response = medicalService.createDocument(request, doctorUser);

        assertThat(response.getValidUntil()).isEqualTo(expiry);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getPatientDocuments
    // ─────────────────────────────────────────────────────────────────────────

    /** Врач запрашивает документы пациента — возвращается список. */
    @Test
    void getPatientDocuments_returnsAllDocuments() {
        when(medicalDocumentRepository.findByPatientId(1L)).thenReturn(List.of(document));

        List<MedicalDocumentResponse> result = medicalService.getPatientDocuments(1L, doctorUser);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getType()).isEqualTo("PRESCRIPTION");
        assertThat(result.get(0).getDoctorSpecialty()).isEqualTo("THERAPIST");
    }

    /** Если документов нет — возвращается пустой список, не null. */
    @Test
    void getPatientDocuments_whenNoDocs_returnsEmptyList() {
        when(medicalDocumentRepository.findByPatientId(1L)).thenReturn(List.of());

        List<MedicalDocumentResponse> result = medicalService.getPatientDocuments(1L, doctorUser);

        assertThat(result).isEmpty();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getMyDocuments
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Клиент запрашивает свои документы. Репозиторий ищет по clientUserId (id клиента
     * в таблице users), а не по patientId — клиент не знает свой внутренний ID пациента.
     */
    @Test
    void getMyDocuments_returnsDocumentsForClientUser() {
        // clientUser.id = 3L — поиск по clientUserId.
        when(medicalDocumentRepository.findByPatientClientUserId(3L)).thenReturn(List.of(document));

        List<MedicalDocumentResponse> result = medicalService.getMyDocuments(clientUser);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPatientId()).isEqualTo(1L);
    }

    /** Если у клиента нет документов — пустой список. */
    @Test
    void getMyDocuments_whenNoDocuments_returnsEmptyList() {
        when(medicalDocumentRepository.findByPatientClientUserId(3L)).thenReturn(List.of());

        List<MedicalDocumentResponse> result = medicalService.getMyDocuments(clientUser);

        assertThat(result).isEmpty();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // createNote
    // ─────────────────────────────────────────────────────────────────────────

    /** Врач-пользователь без профиля Doctor не может создавать заметки. */
    @Test
    void createNote_whenDoctorProfileNotFound_throwsResourceNotFoundException() {
        when(doctorRepository.findByLinkedUserIdAndActiveTrue(2L)).thenReturn(Optional.empty());

        CreatePatientNoteRequest request = new CreatePatientNoteRequest();
        request.setPatientId(1L);
        request.setType(PatientNoteType.DIAGNOSIS);
        request.setContent("Диагноз");

        assertThatThrownBy(() -> medicalService.createNote(request, doctorUser))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    /** Если пациент не найден при создании заметки — ResourceNotFoundException. */
    @Test
    void createNote_whenPatientNotFound_throwsResourceNotFoundException() {
        when(doctorRepository.findByLinkedUserIdAndActiveTrue(2L)).thenReturn(Optional.of(doctor));
        when(patientRepository.findByIdAndActiveTrue(99L)).thenReturn(Optional.empty());

        CreatePatientNoteRequest request = new CreatePatientNoteRequest();
        request.setPatientId(99L);
        request.setType(PatientNoteType.NOTE);
        request.setContent("Заметка");

        assertThatThrownBy(() -> medicalService.createNote(request, doctorUser))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    /**
     * Заметка с visibleToClient=false (значение по умолчанию) сохраняется как скрытая.
     * Это важно: только явное решение врача открывает заметку клиенту.
     */
    @Test
    void createNote_withDefaultVisibility_savesHiddenNote() {
        when(doctorRepository.findByLinkedUserIdAndActiveTrue(2L)).thenReturn(Optional.of(doctor));
        when(patientRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(patient));
        when(patientNoteRepository.save(any(PatientNote.class))).thenReturn(note);

        CreatePatientNoteRequest request = new CreatePatientNoteRequest();
        request.setPatientId(1L);
        request.setType(PatientNoteType.DIAGNOSIS);
        request.setContent("ОРЗ, средней тяжести");
        // visibleToClient = false по умолчанию (примитивный boolean в DTO)

        PatientNoteResponse response = medicalService.createNote(request, doctorUser);

        verify(patientNoteRepository).save(any(PatientNote.class));
        assertThat(response.getId()).isEqualTo(200L);
        assertThat(response.getType()).isEqualTo("DIAGNOSIS");
        assertThat(response.getTypeLabel()).isEqualTo("Диагноз");
        assertThat(response.isVisibleToClient()).isFalse();
    }

    /**
     * Заметка с visibleToClient=true: врач явно разрешил пациенту видеть эту запись.
     * Флаг должен сохраниться и отразиться в ответе.
     */
    @Test
    void createNote_withVisibleToClientTrue_savesVisibleNote() {
        PatientNote visibleNote = PatientNote.builder()
                .id(201L).patient(patient).doctor(doctor)
                .type(PatientNoteType.OBSERVATION)
                .content("Улучшение на третий день").visibleToClient(true)
                .createdAt(LocalDateTime.now()).build();

        when(doctorRepository.findByLinkedUserIdAndActiveTrue(2L)).thenReturn(Optional.of(doctor));
        when(patientRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(patient));
        when(patientNoteRepository.save(any(PatientNote.class))).thenReturn(visibleNote);

        CreatePatientNoteRequest request = new CreatePatientNoteRequest();
        request.setPatientId(1L);
        request.setType(PatientNoteType.OBSERVATION);
        request.setContent("Улучшение на третий день");
        request.setVisibleToClient(true);

        PatientNoteResponse response = medicalService.createNote(request, doctorUser);

        assertThat(response.isVisibleToClient()).isTrue();
        assertThat(response.getTypeLabel()).isEqualTo("Наблюдение");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getPatientHistory
    // ─────────────────────────────────────────────────────────────────────────

    /** Если пациент не найден при запросе истории — ResourceNotFoundException (HTTP 404). */
    @Test
    void getPatientHistory_whenPatientNotFound_throwsResourceNotFoundException() {
        when(patientRepository.findByIdAndActiveTrue(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> medicalService.getPatientHistory(99L, doctorUser))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    /**
     * «Счастливый путь»: врач получает агрегированную историю.
     * Возвращаются ВСЕ заметки (включая скрытые от клиента) и все документы.
     */
    @Test
    void getPatientHistory_returnsAggregatedNotesAndDocuments() {
        PatientNote hiddenNote = PatientNote.builder()
                .id(300L).patient(patient).doctor(doctor)
                .type(PatientNoteType.NOTE).content("Служебная заметка")
                .visibleToClient(false).createdAt(LocalDateTime.now()).build();

        when(patientRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(patient));
        when(patientNoteRepository.findByPatientId(1L)).thenReturn(List.of(note, hiddenNote));
        when(medicalDocumentRepository.findByPatientId(1L)).thenReturn(List.of(document));

        PatientHistoryResponse response = medicalService.getPatientHistory(1L, doctorUser);

        // Имя пациента берётся из entity, а не из заметок.
        assertThat(response.getPatientId()).isEqualTo(1L);
        assertThat(response.getPatientName()).isEqualTo("Петров Алексей Иванович");
        // Врач видит обе заметки: и видимую, и служебную.
        assertThat(response.getNotes()).hasSize(2);
        assertThat(response.getDocuments()).hasSize(1);
    }

    /** Пустая история (новый пациент): возвращаются пустые списки, не null. */
    @Test
    void getPatientHistory_newPatient_returnsEmptyListsAndCorrectName() {
        when(patientRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(patient));
        when(patientNoteRepository.findByPatientId(1L)).thenReturn(List.of());
        when(medicalDocumentRepository.findByPatientId(1L)).thenReturn(List.of());

        PatientHistoryResponse response = medicalService.getPatientHistory(1L, doctorUser);

        assertThat(response.getPatientName()).isEqualTo("Петров Алексей Иванович");
        assertThat(response.getNotes()).isEmpty();
        assertThat(response.getDocuments()).isEmpty();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getMyHistory
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Если у клиента нет связанного пациента — возвращается пустая история
     * с пустым именем и пустыми списками. Это нормальное состояние для нового клиента.
     */
    @Test
    void getMyHistory_whenNoData_returnsEmptyHistoryWithEmptyName() {
        when(patientNoteRepository.findVisibleByClientUserId(3L)).thenReturn(List.of());
        when(medicalDocumentRepository.findByPatientClientUserId(3L)).thenReturn(List.of());

        PatientHistoryResponse response = medicalService.getMyHistory(clientUser);

        // patientId не выставляется для клиентского ответа (information hiding).
        assertThat(response.getPatientName()).isEmpty();
        assertThat(response.getNotes()).isEmpty();
        assertThat(response.getDocuments()).isEmpty();
    }

    /**
     * Если у клиента есть видимые заметки — имя пациента берётся из первой заметки.
     * Это оптимизация: не нужен дополнительный SELECT к patients.
     */
    @Test
    void getMyHistory_withVisibleNotes_extractsPatientNameFromNote() {
        // Видимая заметка (visibleToClient=true).
        PatientNote visibleNote = PatientNote.builder()
                .id(400L).patient(patient).doctor(doctor)
                .type(PatientNoteType.DIAGNOSIS).content("Рекомендации врача")
                .visibleToClient(true).createdAt(LocalDateTime.now()).build();

        when(patientNoteRepository.findVisibleByClientUserId(3L)).thenReturn(List.of(visibleNote));
        when(medicalDocumentRepository.findByPatientClientUserId(3L)).thenReturn(List.of());

        PatientHistoryResponse response = medicalService.getMyHistory(clientUser);

        // Имя берётся из заметки → совпадает с patient.fullName.
        assertThat(response.getPatientName()).isEqualTo("Петров Алексей Иванович");
        assertThat(response.getNotes()).hasSize(1);
        assertThat(response.getDocuments()).isEmpty();
    }

    /**
     * Если у клиента нет видимых заметок, но есть документы — имя берётся из первого документа.
     */
    @Test
    void getMyHistory_withOnlyDocs_extractsPatientNameFromDocument() {
        when(patientNoteRepository.findVisibleByClientUserId(3L)).thenReturn(List.of());
        when(medicalDocumentRepository.findByPatientClientUserId(3L)).thenReturn(List.of(document));

        PatientHistoryResponse response = medicalService.getMyHistory(clientUser);

        assertThat(response.getPatientName()).isEqualTo("Петров Алексей Иванович");
        assertThat(response.getNotes()).isEmpty();
        assertThat(response.getDocuments()).hasSize(1);
    }

    /**
     * getMyHistory фильтрует заметки: клиент НЕ видит заметки с visibleToClient=false.
     * Фильтрация реализована в репозитории (findVisibleByClientUserId), поэтому
     * тест проверяет, что правильный метод вызывается, а не findByPatientId.
     */
    @Test
    void getMyHistory_usesVisibleNotesRepository_notFullPatientNotes() {
        when(patientNoteRepository.findVisibleByClientUserId(3L)).thenReturn(List.of());
        when(medicalDocumentRepository.findByPatientClientUserId(3L)).thenReturn(List.of());

        medicalService.getMyHistory(clientUser);

        // Клиентский метод ВСЕГДА использует findVisibleByClientUserId.
        verify(patientNoteRepository).findVisibleByClientUserId(3L);
        // Метод без фильтра (для врачей) НЕ должен вызываться.
        verify(patientNoteRepository, never()).findByPatientId(anyLong());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Локализация typeLabel (docTypeLabel / noteTypeLabel)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Проверяем, что все типы документов имеют русскоязычный label.
     * Exhaustive switch в сервисе гарантирует покрытие всех enum-значений на уровне компилятора,
     * но тест документирует ожидаемые строки для регрессионного контроля.
     */
    @Test
    void createDocument_allDocumentTypes_haveCorrectRussianLabels() {
        // Пары (тип документа → ожидаемый label).
        record TypeLabelPair(MedicalDocumentType type, String expectedLabel) {}
        List<TypeLabelPair> pairs = List.of(
                new TypeLabelPair(MedicalDocumentType.PRESCRIPTION,   "Рецепт"),
                new TypeLabelPair(MedicalDocumentType.REFERRAL,       "Направление"),
                new TypeLabelPair(MedicalDocumentType.SICK_LEAVE,     "Больничный лист"),
                new TypeLabelPair(MedicalDocumentType.ANALYSIS_ORDER, "Направление на анализы"),
                new TypeLabelPair(MedicalDocumentType.CERTIFICATE,    "Справка")
        );

        when(doctorRepository.findByLinkedUserIdAndActiveTrue(2L)).thenReturn(Optional.of(doctor));
        when(patientRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(patient));

        for (TypeLabelPair pair : pairs) {
            // Строим документ нужного типа для каждой итерации.
            MedicalDocument typed = MedicalDocument.builder()
                    .id(99L).patient(patient).doctor(doctor)
                    .type(pair.type()).title("Тест").content("Содержание")
                    .issuedAt(LocalDateTime.now()).active(true).build();

            when(medicalDocumentRepository.save(any())).thenReturn(typed);

            CreateMedicalDocumentRequest req = new CreateMedicalDocumentRequest();
            req.setPatientId(1L);
            req.setType(pair.type());
            req.setTitle("Тест");
            req.setContent("Содержание");

            MedicalDocumentResponse response = medicalService.createDocument(req, doctorUser);
            assertThat(response.getTypeLabel())
                    .as("Неверный label для типа %s", pair.type())
                    .isEqualTo(pair.expectedLabel());
        }
    }

    /** Все типы заметок имеют корректные русскоязычные подписи. */
    @Test
    void createNote_allNoteTypes_haveCorrectRussianLabels() {
        record TypeLabelPair(PatientNoteType type, String expectedLabel) {}
        List<TypeLabelPair> pairs = List.of(
                new TypeLabelPair(PatientNoteType.DIAGNOSIS,   "Диагноз"),
                new TypeLabelPair(PatientNoteType.OBSERVATION, "Наблюдение"),
                new TypeLabelPair(PatientNoteType.NOTE,        "Заметка")
        );

        when(doctorRepository.findByLinkedUserIdAndActiveTrue(2L)).thenReturn(Optional.of(doctor));
        when(patientRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(patient));

        for (TypeLabelPair pair : pairs) {
            PatientNote typed = PatientNote.builder()
                    .id(99L).patient(patient).doctor(doctor)
                    .type(pair.type()).content("Контент")
                    .visibleToClient(false).createdAt(LocalDateTime.now()).build();

            when(patientNoteRepository.save(any())).thenReturn(typed);

            CreatePatientNoteRequest req = new CreatePatientNoteRequest();
            req.setPatientId(1L);
            req.setType(pair.type());
            req.setContent("Контент");

            PatientNoteResponse response = medicalService.createNote(req, doctorUser);
            assertThat(response.getTypeLabel())
                    .as("Неверный label для типа %s", pair.type())
                    .isEqualTo(pair.expectedLabel());
        }
    }
}
