package com.hospital.repository;

import com.hospital.entity.PatientNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Репозиторий для работы с заметками врача о пациенте ({@link PatientNote}).
 *
 * <p>Ключевые особенности запросов:
 * <ul>
 *   <li><b>Разграничение видимости на уровне запросов</b>: врач получает все заметки
 *       пациента через {@link #findByPatientId}, клиентский портал — только те, у которых
 *       {@code visibleToClient = true} ({@link #findVisibleByClientUserId}). Логика
 *       доступа встроена в SQL-запрос, а не в сервисный слой, что исключает случайное
 *       раскрытие служебных заметок через повторное использование метода.</li>
 *   <li><b>JOIN FETCH n.doctor везде</b>: поле {@code doctor} в {@link PatientNote}
 *       объявлено LAZY, но ФИО врача отображается рядом с каждой заметкой в обоих
 *       интерфейсах. JOIN FETCH загружает врача за один SQL-запрос, предотвращая
 *       N+1-проблему при итерации по списку заметок.</li>
 *   <li><b>Навигация через {@code patient.clientUser.id}</b>: клиентский запрос
 *       использует цепочку связей без необходимости знать внутренний ID записи пациента.
 *       Клиент идентифицируется по своему аккаунту ({@link com.hospital.entity.User}).</li>
 * </ul>
 */
public interface PatientNoteRepository extends JpaRepository<PatientNote, Long> {

    /**
     * Возвращает все заметки по пациенту для просмотра врачом.
     *
     * <p>Врач видит полную историю: как заметки, видимые клиенту, так и служебные
     * ({@code visibleToClient = false}). Это необходимо для полного понимания
     * медицинской ситуации и сохранения контекста наблюдений.
     *
     * <p>{@code JOIN FETCH n.doctor} — загружает врача-автора за один запрос.
     * ФИО врача отображается рядом с каждой заметкой для идентификации источника.
     *
     * <p>Сортировка {@code ORDER BY n.createdAt DESC} — новые заметки первыми,
     * что соответствует стандартному порядку просмотра клинической истории.
     *
     * @param patientId внутренний идентификатор пациента
     * @return список всех заметок пациента, упорядоченный от новых к старым
     */
    @Query("SELECT n FROM PatientNote n JOIN FETCH n.doctor WHERE n.patient.id = :patientId ORDER BY n.createdAt DESC")
    List<PatientNote> findByPatientId(@Param("patientId") Long patientId);

    /**
     * Возвращает только видимые пациенту заметки для клиентского портала.
     *
     * <p>Фильтр {@code visibleToClient = true} ограничивает список только теми
     * записями, которые врач явно пометил как доступные для клиента. Служебные
     * заметки (административные пометки, внутренние наблюдения) остаются скрытыми.
     *
     * <p>Навигация {@code patient.clientUser.id} позволяет клиенту запрашивать
     * свои данные только по ID своего аккаунта, без знания внутреннего ID
     * записи пациента.
     *
     * <p>{@code JOIN FETCH n.doctor} — ФИО врача отображается рядом с заметкой
     * в личном кабинете клиента для понимания источника рекомендации.
     *
     * @param clientUserId идентификатор пользователя с ролью ROLE_CLIENT
     * @return список видимых клиенту заметок, упорядоченный от новых к старым
     */
    @Query("SELECT n FROM PatientNote n JOIN FETCH n.doctor WHERE n.patient.clientUser.id = :clientUserId AND n.visibleToClient = true ORDER BY n.createdAt DESC")
    List<PatientNote> findVisibleByClientUserId(@Param("clientUserId") Long clientUserId);
}
