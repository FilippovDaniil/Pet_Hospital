package com.hospital.service;

import com.hospital.dto.request.CreateMedicalDocumentRequest;
import com.hospital.dto.request.CreatePatientNoteRequest;
import com.hospital.dto.response.MedicalDocumentResponse;
import com.hospital.dto.response.PatientHistoryResponse;
import com.hospital.dto.response.PatientNoteResponse;
import com.hospital.entity.User;

import java.util.List;

/**
 * Сервисный интерфейс для управления медицинскими документами и заметками врачей.
 *
 * <p>Ключевые архитектурные решения:
 * <ul>
 *   <li><b>Разделение контрактов по ролям</b>: каждый метод явно предназначен либо
 *       для врача, либо для клиента. Врач получает полные данные, клиент — только
 *       то, что явно помечено как доступное (активные документы,
 *       заметки с {@code visibleToClient = true}).</li>
 *   <li><b>Передача {@link User} вместо ID</b>: все методы принимают объект
 *       {@code User} из Security-контекста. Это позволяет реализации выполнять
 *       авторизационные проверки (например, врач работает только с пациентами
 *       своего отделения) без дополнительных запросов к базе данных.</li>
 *   <li><b>Агрегирующий метод {@code getPatientHistory}</b>: возвращает объединённые
 *       данные (заметки + документы) одним вызовом, что удобно для отображения
 *       полной карточки пациента. Это избегает множественных HTTP-запросов
 *       от фронтенда.</li>
 *   <li><b>Soft delete через флаг {@code active}</b>: документы деактивируются,
 *       но не удаляются физически. Врач видит все документы, клиент — только активные.
 *       Логика фильтрации находится в репозиторном слое.</li>
 * </ul>
 */
public interface MedicalService {

    /**
     * Создаёт новый медицинский документ от имени врача и сохраняет его в БД.
     *
     * <p>Реализация должна найти сущность {@link com.hospital.entity.Doctor},
     * связанную с {@code doctorUser}, и установить её как автора документа.
     * Дата выдачи ({@code issuedAt}) проставляется автоматически через
     * {@code @PrePersist} в сущности.
     *
     * @param request    DTO с данными документа (тип, заголовок, содержимое, ID пациента,
     *                   опциональная дата истечения)
     * @param doctorUser аутентифицированный пользователь с ролью ROLE_DOCTOR
     * @return DTO созданного документа с заполненными {@code id} и {@code issuedAt}
     */
    MedicalDocumentResponse createDocument(CreateMedicalDocumentRequest request, User doctorUser);

    /**
     * Возвращает все медицинские документы пациента для просмотра врачом.
     *
     * <p>Включает все документы, в том числе деактивированные ({@code active = false}),
     * так как врач должен видеть полную медицинскую историю пациента.
     * Список упорядочен от новых документов к старым.
     *
     * @param patientId  внутренний идентификатор пациента
     * @param doctorUser аутентифицированный пользователь с ролью ROLE_DOCTOR
     * @return список всех документов пациента
     */
    List<MedicalDocumentResponse> getPatientDocuments(Long patientId, User doctorUser);

    /**
     * Возвращает собственные медицинские документы клиента через клиентский портал.
     *
     * <p>Возвращаются только активные документы ({@code active = true}),
     * связанные с пациентом, привязанным к данному клиентскому аккаунту.
     * Поиск осуществляется через {@code patient.clientUser.id} — клиент
     * не знает свой внутренний ID пациента.
     *
     * @param clientUser аутентифицированный пользователь с ролью ROLE_CLIENT
     * @return список активных документов клиента, упорядоченный от новых к старым
     */
    List<MedicalDocumentResponse> getMyDocuments(User clientUser);

    /**
     * Создаёт заметку или диагноз врача о пациенте.
     *
     * <p>Реализация должна найти сущность {@link com.hospital.entity.Doctor},
     * связанную с {@code doctorUser}. Флаг {@code visibleToClient} из запроса
     * определяет, будет ли заметка видна пациенту в личном кабинете.
     *
     * @param request    DTO с данными заметки (тип, содержимое, ID пациента,
     *                   признак видимости для клиента)
     * @param doctorUser аутентифицированный пользователь с ролью ROLE_DOCTOR
     * @return DTO созданной заметки с заполненными {@code id} и {@code createdAt}
     */
    PatientNoteResponse createNote(CreatePatientNoteRequest request, User doctorUser);

    /**
     * Возвращает полную историю пациента для врача: все заметки и все документы.
     *
     * <p>Агрегирует данные из двух репозиториев в единый объект {@link PatientHistoryResponse}.
     * Врач видит записи без ограничений по видимости: как служебные заметки
     * ({@code visibleToClient = false}), так и деактивированные документы
     * ({@code active = false}).
     *
     * @param patientId  внутренний идентификатор пациента
     * @param doctorUser аутентифицированный пользователь с ролью ROLE_DOCTOR
     * @return агрегированный объект с заметками и документами пациента
     */
    PatientHistoryResponse getPatientHistory(Long patientId, User doctorUser);

    /**
     * Возвращает собственную медицинскую историю клиента через клиентский портал.
     *
     * <p>Возвращает только данные, разрешённые для клиентского просмотра:
     * <ul>
     *   <li>Заметки с {@code visibleToClient = true}</li>
     *   <li>Активные документы ({@code active = true})</li>
     * </ul>
     * Поиск осуществляется по {@code clientUser.id} без необходимости
     * знать внутренний ID пациента.
     *
     * @param clientUser аутентифицированный пользователь с ролью ROLE_CLIENT
     * @return агрегированный объект с видимыми клиенту заметками и активными документами
     */
    PatientHistoryResponse getMyHistory(User clientUser);
}
