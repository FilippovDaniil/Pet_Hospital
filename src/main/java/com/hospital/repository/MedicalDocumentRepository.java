package com.hospital.repository;

import com.hospital.entity.MedicalDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Репозиторий для работы с медицинскими документами ({@link MedicalDocument}).
 *
 * <p>Ключевые особенности запросов:
 * <ul>
 *   <li><b>Разграничение доступа врача и клиента на уровне запросов</b>:
 *       врач получает все документы пациента через {@link #findByPatientId}
 *       (без фильтра по {@code active}), тогда как клиентский портал видит только
 *       активные документы через {@link #findByPatientClientUserId}
 *       (с условием {@code active = true}). Логика видимости закреплена в SQL,
 *       а не в сервисном слое, что снижает риск случайного раскрытия скрытых документов.</li>
 *   <li><b>JOIN FETCH для связанных сущностей</b>: все запросы используют JOIN FETCH
 *       для загрузки необходимой связанной сущности за один SQL-запрос. Без JOIN FETCH
 *       Hibernate загружал бы каждого врача или пациента отдельным SELECT (N+1-проблема).</li>
 *   <li><b>Навигация через {@code patient.clientUser.id}</b>: клиентский запрос
 *       использует цепочку связей {@code patient → clientUser → id}, позволяя клиенту
 *       запрашивать свои документы только по ID своего аккаунта, без знания
 *       внутреннего ID записи пациента.</li>
 * </ul>
 */
public interface MedicalDocumentRepository extends JpaRepository<MedicalDocument, Long> {

    /**
     * Возвращает все документы пациента для просмотра врачом, включая неактивные.
     *
     * <p>Врач видит полную историю документов пациента, в том числе деактивированные
     * записи. Это необходимо для медицинского аудита и понимания полной картины лечения.
     *
     * <p>{@code JOIN FETCH d.doctor} — загружает врача-автора за один запрос.
     * ФИО и специальность врача отображаются рядом с каждым документом в интерфейсе.
     *
     * <p>Сортировка {@code ORDER BY d.issuedAt DESC} — новые документы первыми,
     * что соответствует стандартному порядку просмотра медицинской истории.
     *
     * @param patientId внутренний идентификатор пациента
     * @return список всех документов пациента, упорядоченный от новых к старым
     */
    @Query("SELECT d FROM MedicalDocument d JOIN FETCH d.doctor WHERE d.patient.id = :patientId ORDER BY d.issuedAt DESC")
    List<MedicalDocument> findByPatientId(@Param("patientId") Long patientId);

    /**
     * Возвращает активные документы пациента для клиентского портала.
     *
     * <p>Используется когда клиент запрашивает собственные документы в личном кабинете.
     * Фильтр {@code active = true} скрывает отозванные или деактивированные документы.
     * Навигация {@code patient.clientUser.id} связывает аккаунт клиента с записью
     * пациента без необходимости делать дополнительный запрос на поиск patientId.
     *
     * <p>{@code JOIN FETCH d.doctor} — ФИО врача отображается в карточке документа
     * на клиентском портале.
     *
     * @param clientUserId идентификатор пользователя с ролью ROLE_CLIENT
     * @return список активных документов данного клиента, упорядоченный от новых к старым
     */
    @Query("SELECT d FROM MedicalDocument d JOIN FETCH d.doctor WHERE d.patient.clientUser.id = :clientUserId AND d.active = true ORDER BY d.issuedAt DESC")
    List<MedicalDocument> findByPatientClientUserId(@Param("clientUserId") Long clientUserId);

    /**
     * Возвращает все документы, созданные конкретным врачом.
     *
     * <p>Используется врачом для просмотра истории своих выданных документов
     * (например, для контроля рецептов и справок). В отличие от запросов по пациенту,
     * здесь необходим {@code JOIN FETCH d.patient} — в контексте «мои документы»
     * нужно знать, кому выдан каждый документ.
     *
     * @param doctorId идентификатор врача
     * @return список документов данного врача, упорядоченный от новых к старым
     */
    @Query("SELECT d FROM MedicalDocument d JOIN FETCH d.patient WHERE d.doctor.id = :doctorId ORDER BY d.issuedAt DESC")
    List<MedicalDocument> findByDoctorId(@Param("doctorId") Long doctorId);
}
