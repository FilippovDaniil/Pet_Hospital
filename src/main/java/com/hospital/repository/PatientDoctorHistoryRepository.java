package com.hospital.repository;

import com.hospital.entity.PatientDoctorHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Репозиторий для работы с сущностью {@link PatientDoctorHistory} —
 * историей прикрепления пациентов к врачам.
 *
 * <p>Каждая запись в этой таблице фиксирует факт того, что пациент был
 * прикреплён к определённому врачу в определённый период:
 * <ul>
 *   <li>{@code assignedFrom} — дата/время начала прикрепления</li>
 *   <li>{@code assignedTo} — дата/время окончания прикрепления
 *       ({@code null}, если врач является текущим)</li>
 * </ul>
 * Такой подход позволяет хранить полную хронологию смены врачей.</p>
 */
public interface PatientDoctorHistoryRepository extends JpaRepository<PatientDoctorHistory, Long> {

    /**
     * Находит текущую (незакрытую) запись прикрепления пациента к врачу.
     *
     * <p><b>Построение запроса по имени:</b><br>
     * {@code findBy} — SELECT ... WHERE<br>
     * {@code PatientId} — patient_id = ?<br>
     * {@code And} — AND<br>
     * {@code AssignedToIsNull} — assigned_to IS NULL<br>
     * Итого: {@code SELECT * FROM patient_doctor_history WHERE patient_id = ? AND assigned_to IS NULL}</p>
     *
     * <p><b>Почему {@code assignedTo IS NULL}?</b><br>
     * Запись с {@code assignedTo = null} означает "открытый" период —
     * пациент прикреплён к этому врачу прямо сейчас. Когда пациента
     * переводят к другому врачу, этой записи проставляется {@code assignedTo},
     * и создаётся новая запись с {@code assignedTo = null}.</p>
     *
     * <p>Используется при переводе пациента к другому врачу: сервис находит
     * текущую активную запись и закрывает её, записав текущую дату в {@code assignedTo}.</p>
     *
     * @param patientId идентификатор пациента
     * @return {@link Optional} с активной записью прикрепления, если она существует
     */
    Optional<PatientDoctorHistory> findByPatientIdAndAssignedToIsNull(Long patientId);

    /**
     * Возвращает полную историю прикреплений пациента к врачам, отсортированную
     * по дате начала прикрепления (сначала самые свежие).
     *
     * <p><b>Построение запроса по имени:</b><br>
     * {@code findBy} — SELECT ... WHERE<br>
     * {@code PatientId} — patient_id = ?<br>
     * {@code OrderBy} — ORDER BY<br>
     * {@code AssignedFromDesc} — assigned_from DESC (убывание, т.е. новые сначала)<br>
     * Итого: {@code SELECT * FROM patient_doctor_history WHERE patient_id = ?
     * ORDER BY assigned_from DESC}</p>
     *
     * <p>Используется для отображения истории врачей пациента в его карточке.
     * Сортировка DESC позволяет показать самую актуальную запись первой.</p>
     *
     * @param patientId идентификатор пациента
     * @return список записей истории прикреплений, отсортированный от новых к старым
     */
    java.util.List<PatientDoctorHistory> findByPatientIdOrderByAssignedFromDesc(Long patientId);
}
