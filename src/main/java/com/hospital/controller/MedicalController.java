package com.hospital.controller;

import com.hospital.dto.request.CreateMedicalDocumentRequest;
import com.hospital.dto.request.CreatePatientNoteRequest;
import com.hospital.dto.response.MedicalDocumentResponse;
import com.hospital.dto.response.PatientHistoryResponse;
import com.hospital.dto.response.PatientNoteResponse;
import com.hospital.entity.User;
import com.hospital.repository.UserRepository;
import com.hospital.service.MedicalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST-контроллер медицинских записей: документы и заметки врача.
 *
 * <p>Функциональность разделена на два домена:
 * <ul>
 *   <li><b>Медицинские документы</b> — формальные документы с юридической силой:
 *       рецепты, направления, больничные листы, заказы на анализы, справки.
 *       Хранятся в таблице {@code medical_document}.</li>
 *   <li><b>Заметки врача</b> — внутренние наблюдения, диагнозы, клинические
 *       записи. Хранятся в таблице {@code patient_note}. Флаг
 *       {@code visible_to_client} управляет видимостью в личном кабинете.</li>
 * </ul>
 *
 * <p>Доступ к данным регулируется ролями:
 * <ul>
 *   <li>{@code ROLE_DOCTOR} — создаёт документы и заметки, читает историю
 *       любого пациента.</li>
 *   <li>{@code ROLE_CLIENT} — читает только свои документы и те заметки,
 *       для которых {@code visible_to_client = true}.</li>
 * </ul>
 *
 * <p>Маршрут: {@code /api/medical}
 */
@RestController
@RequestMapping("/api/medical")
@RequiredArgsConstructor
@Tag(name = "Medical", description = "Медицинские документы и история пациента")
public class MedicalController {

    // MedicalService содержит бизнес-логику, проверки прав доступа и работу с БД.
    private final MedicalService medicalService;

    // UserRepository нужен только для преобразования имени из JWT в полный объект User.
    private final UserRepository userRepository;

    // ─────────────────────────────────────────────
    // DOCUMENTS — DOCTOR
    // ─────────────────────────────────────────────

    /**
     * Создаёт медицинский документ для пациента от имени врача.
     *
     * <p>Типы документов (поле {@code type} в запросе):
     * {@code PRESCRIPTION} — рецепт,
     * {@code REFERRAL} — направление к специалисту,
     * {@code SICK_LEAVE} — больничный лист,
     * {@code ANALYSIS_ORDER} — направление на анализы,
     * {@code CERTIFICATE} — медицинская справка.
     *
     * <p>Сервис автоматически связывает документ с учётной записью врача
     * через {@code doctor.user_id}, поэтому передавать {@code doctorId}
     * в теле запроса не требуется — он определяется из JWT.
     *
     * <p>HTTP 201 Created сигнализирует о том, что ресурс успешно создан
     * и доступен по указанному маршруту.
     *
     * <p>Доступ: {@code ROLE_DOCTOR}.
     *
     * @param request данные нового документа (patientId, type, title, content, validUntil)
     * @param auth    JWT-аутентификация врача-создателя
     */
    @PostMapping("/documents")
    @Operation(summary = "Врач: создать медицинский документ для пациента")
    public ResponseEntity<MedicalDocumentResponse> createDocument(
            @RequestBody @Valid CreateMedicalDocumentRequest request,
            Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(medicalService.createDocument(request, currentUser(auth)));
    }

    /**
     * Возвращает список всех медицинских документов конкретного пациента.
     *
     * <p>Эндпоинт предназначен для врача: позволяет получить полный набор
     * документов пациента независимо от того, кто их выписал. Это важно,
     * например, при первичном приёме — врач видит предыдущие рецепты
     * и направления коллег.
     *
     * <p>Сервис фильтрует по {@code is_active = true}: аннулированные документы
     * не возвращаются по умолчанию.
     *
     * <p>Доступ: {@code ROLE_DOCTOR}.
     *
     * @param patientId идентификатор пациента в таблице {@code patient}
     * @param auth      JWT-аутентификация врача
     */
    @GetMapping("/documents/patient/{patientId}")
    @Operation(summary = "Врач: все документы конкретного пациента")
    public List<MedicalDocumentResponse> getPatientDocuments(
            @PathVariable Long patientId,
            Authentication auth) {
        return medicalService.getPatientDocuments(patientId, currentUser(auth));
    }

    // ─────────────────────────────────────────────
    // DOCUMENTS — CLIENT
    // ─────────────────────────────────────────────

    /**
     * Возвращает медицинские документы текущего клиента (личный кабинет).
     *
     * <p>Клиент видит только свои документы: сервис определяет {@code patient_id}
     * через связь {@code patient.client_user_id == текущий пользователь}.
     * Это исключает возможность просмотра чужих документов даже при
     * прямом переборе идентификаторов.
     *
     * <p>Доступ: {@code ROLE_CLIENT}.
     */
    @GetMapping("/documents/my")
    @Operation(summary = "Клиент: свои медицинские документы")
    public List<MedicalDocumentResponse> getMyDocuments(Authentication auth) {
        return medicalService.getMyDocuments(currentUser(auth));
    }

    // ─────────────────────────────────────────────
    // NOTES — DOCTOR
    // ─────────────────────────────────────────────

    /**
     * Добавляет врачебную заметку к карточке пациента.
     *
     * <p>Типы заметок (поле {@code type}):
     * {@code NOTE} — произвольная клиническая заметка,
     * {@code DIAGNOSIS} — поставленный диагноз,
     * {@code OBSERVATION} — результат наблюдения/осмотра.
     *
     * <p>Флаг {@code visibleToClient} в запросе управляет видимостью:
     * {@code false} — внутренняя запись только для медперсонала,
     * {@code true} — пациент увидит эту запись в разделе «Моя история».
     *
     * <p>В отличие от медицинских документов, заметки не имеют срока действия
     * и не аннулируются — они образуют неизменяемую хронологическую летопись.
     *
     * <p>Доступ: {@code ROLE_DOCTOR}.
     *
     * @param request данные заметки (patientId, type, content, visibleToClient)
     * @param auth    JWT-аутентификация врача
     */
    @PostMapping("/notes")
    @Operation(summary = "Врач: добавить заметку / диагноз пациенту")
    public ResponseEntity<PatientNoteResponse> createNote(
            @RequestBody @Valid CreatePatientNoteRequest request,
            Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(medicalService.createNote(request, currentUser(auth)));
    }

    // ─────────────────────────────────────────────
    // HISTORY
    // ─────────────────────────────────────────────

    /**
     * Возвращает полную медицинскую историю пациента для врача.
     *
     * <p>Агрегирует в одном ответе {@link PatientHistoryResponse}:
     * <ul>
     *   <li>все активные медицинские документы пациента;</li>
     *   <li>все заметки врачей (включая те, что скрыты от клиента).</li>
     * </ul>
     *
     * <p>Сводное представление удобно при приёме: врачу не нужно делать
     * несколько запросов — история пациента загружается за один вызов.
     *
     * <p>Доступ: {@code ROLE_DOCTOR}.
     *
     * @param patientId идентификатор пациента в таблице {@code patient}
     * @param auth      JWT-аутентификация врача
     */
    @GetMapping("/history/patient/{patientId}")
    @Operation(summary = "Врач: полная история пациента (заметки + документы)")
    public PatientHistoryResponse getPatientHistory(
            @PathVariable Long patientId,
            Authentication auth) {
        return medicalService.getPatientHistory(patientId, currentUser(auth));
    }

    /**
     * Возвращает медицинскую историю текущего клиента (личный кабинет).
     *
     * <p>Клиент видит только ту часть истории, которая ему разрешена:
     * <ul>
     *   <li>все активные медицинские документы (рецепты, справки и т.д.);</li>
     *   <li>только те заметки врачей, у которых {@code visible_to_client = true}.</li>
     * </ul>
     *
     * <p>Внутренние клинические заметки ({@code visible_to_client = false})
     * фильтруются в сервисном слое и клиенту не передаются.
     *
     * <p>Доступ: {@code ROLE_CLIENT}.
     */
    @GetMapping("/history/my")
    @Operation(summary = "Клиент: своя медицинская история")
    public PatientHistoryResponse getMyHistory(Authentication auth) {
        return medicalService.getMyHistory(currentUser(auth));
    }

    // ─────────────────────────────────────────────

    /**
     * Вспомогательный метод: преобразует имя пользователя из JWT в объект {@link User}.
     *
     * <p>Spring Security заполняет {@code Authentication.getName()} значением
     * поля {@code sub} JWT-токена, совпадающим с {@code User.username}.
     * Вызов {@code orElseThrow()} безопасен: токен прошёл валидацию в
     * {@code JwtAuthenticationFilter}, значит пользователь гарантированно
     * присутствует в БД.
     *
     * @param auth объект аутентификации Spring Security
     * @return полная сущность пользователя из базы данных
     */
    private User currentUser(Authentication auth) {
        return userRepository.findByUsername(auth.getName()).orElseThrow();
    }
}
