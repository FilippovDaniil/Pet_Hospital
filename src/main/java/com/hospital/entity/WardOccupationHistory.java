package com.hospital.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Сущность «История размещения пациента в палатах» — аудиторская таблица,
 * фиксирующая все переводы пациента между палатами с временными интервалами.
 *
 * <p>Структурно аналогична {@link PatientDoctorHistory}, но отслеживает
 * размещение в палате, а не прикрепление к врачу.
 *
 * <p>Задачи, которые решает эта таблица:
 * <ul>
 *   <li>Статистика заполняемости палат за произвольный период.</li>
 *   <li>Восстановление хронологии лечения пациента (в какой палате находился когда).</li>
 *   <li>Биллинг: расчёт стоимости пребывания в зависимости от длительности и палаты.</li>
 * </ul>
 *
 * <p>Паттерн «интервальная история»: запись описывает период [{@code admittedAt}, {@code dischargedAt}).
 * Открытый правый конец ({@code dischargedAt = null}) означает, что пациент находится в палате сейчас.
 * При этом денормализованное поле {@link Patient#currentWard} содержит ту же актуальную информацию
 * для быстрого доступа без JOIN к этой таблице.
 */
@Entity
@Table(name = "ward_occupation_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class WardOccupationHistory {

    /**
     * Суррогатный первичный ключ записи.
     * Каждый перевод в новую палату создаёт новую запись.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    /**
     * Пациент, которого разместили в палате.
     *
     * <p>Ссылка на Patient (а не строка с именем) гарантирует целостность данных
     * и позволяет делать JOIN при аналитических запросах.
     * Soft delete в Patient гарантирует, что эта ссылка всегда будет валидной.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    @ToString.Exclude
    private Patient patient;

    /**
     * Палата, в которой находился пациент в данный период.
     *
     * <p>Ссылка на Ward сохраняется неизменной даже при переименовании или
     * изменении вместимости палаты — история отражает факт, а не текущее состояние.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ward_id", nullable = false)
    @ToString.Exclude
    private Ward ward;

    /**
     * Дата и время заселения пациента в палату.
     * Устанавливается в момент размещения и никогда не изменяется после создания записи.
     */
    @Column(nullable = false)
    private LocalDateTime admittedAt;

    /**
     * Дата и время выписки или перевода пациента из палаты.
     *
     * <p>{@code null} означает, что пациент находится в этой палате в данный момент
     * (текущая незакрытая запись).
     *
     * <p>При переводе в другую палату сервис выполняет в одной транзакции:
     * <ol>
     *   <li>Закрывает текущую запись: {@code dischargedAt = LocalDateTime.now()}.</li>
     *   <li>Уменьшает {@link Ward#currentOccupancy} старой палаты на 1.</li>
     *   <li>Создаёт новую запись для новой палаты: {@code admittedAt = LocalDateTime.now()}.</li>
     *   <li>Увеличивает {@link Ward#currentOccupancy} новой палаты на 1.</li>
     *   <li>Обновляет {@link Patient#currentWard} на новую палату.</li>
     * </ol>
     * Всё это в одной транзакции (@Transactional), чтобы данные не оказались
     * в несогласованном состоянии при сбое.
     */
    private LocalDateTime dischargedAt;
}
