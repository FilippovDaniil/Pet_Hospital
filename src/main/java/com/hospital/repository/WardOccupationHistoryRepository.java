package com.hospital.repository;

import com.hospital.entity.WardOccupationHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Репозиторий для работы с сущностью {@link WardOccupationHistory} —
 * историей размещения пациентов по палатам.
 *
 * <p>Каждая запись фиксирует факт нахождения пациента в конкретной палате:
 * <ul>
 *   <li>{@code admittedAt} — дата/время заселения в палату</li>
 *   <li>{@code dischargedAt} — дата/время выселения ({@code null}, если пациент
 *       находится в палате в данный момент)</li>
 * </ul>
 * Подобная структура аналогична {@code PatientDoctorHistory} и реализует
 * паттерн "журнал событий" (event log / temporal table).</p>
 */
public interface WardOccupationHistoryRepository extends JpaRepository<WardOccupationHistory, Long> {

    /**
     * Находит текущую (незакрытую) запись о размещении пациента в палате.
     *
     * <p><b>Построение запроса по имени:</b><br>
     * {@code findBy} — SELECT ... WHERE<br>
     * {@code PatientId} — patient_id = ?<br>
     * {@code And} — AND<br>
     * {@code DischargedAtIsNull} — discharged_at IS NULL<br>
     * Итого: {@code SELECT * FROM ward_occupation_history WHERE patient_id = ? AND discharged_at IS NULL}</p>
     *
     * <p><b>Семантика {@code dischargedAt IS NULL}:</b><br>
     * Запись без даты выселения означает, что пациент сейчас находится
     * в данной палате. При переводе в другую палату или при выписке сервис:
     * <ol>
     *   <li>Находит эту запись с помощью данного метода</li>
     *   <li>Проставляет {@code dischargedAt = LocalDateTime.now()}</li>
     *   <li>Создаёт новую запись (если перевод в другую палату)</li>
     *   <li>Обновляет счётчик занятых мест ({@code currentOccupancy}) в обеих палатах</li>
     * </ol>
     * </p>
     *
     * <p>Возврат {@link Optional} позволяет корректно обработать случай,
     * когда пациент ещё не размещён ни в одной палате (например, сразу после
     * регистрации, до госпитализации).</p>
     *
     * @param patientId идентификатор пациента
     * @return {@link Optional} с активной записью размещения, если пациент находится в палате
     */
    Optional<WardOccupationHistory> findByPatientIdAndDischargedAtIsNull(Long patientId);
}
