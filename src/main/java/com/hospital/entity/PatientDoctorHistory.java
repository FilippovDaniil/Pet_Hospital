package com.hospital.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Сущность «История прикрепления пациента к врачу» — аудиторская таблица,
 * фиксирующая все смены лечащего врача с указанием временных интервалов.
 *
 * <p>Зачем нужна история, если текущий врач уже хранится в {@link Patient#currentDoctor}?
 * Поле {@code currentDoctor} в Patient — денормализация для быстрого доступа к текущему состоянию.
 * Эта сущность решает другую задачу: хранит полную хронологию лечения, необходимую для:
 * <ul>
 *   <li>Медицинского аудита («кто лечил пациента в период с X по Y»).</li>
 *   <li>Юридических требований (хранение медицинских данных).</li>
 *   <li>Аналитики (нагрузка на врачей, длительность лечения).</li>
 * </ul>
 *
 * <p>Паттерн «интервальная история» (temporal history): каждая запись описывает период
 * [{@code assignedFrom}, {@code assignedTo}). Открытый правый конец (assignedTo = null)
 * означает текущее назначение.
 */
@Entity
@Table(name = "patient_doctor_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class PatientDoctorHistory {

    /**
     * Суррогатный первичный ключ записи истории.
     * Каждая смена врача создаёт новую запись с новым id.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    /**
     * Пациент, к которому относится данная запись истории.
     *
     * <p>Lazy-загрузка: при запросе истории пациент подгружается только при необходимости.
     * В типичном сценарии (вывод истории конкретного пациента) мы уже знаем пациента
     * из контекста запроса, поэтому лишняя загрузка не нужна.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    @ToString.Exclude
    private Patient patient;

    /**
     * Врач, которому был прикреплён пациент в данный период.
     *
     * <p>Важно: врач хранится как ссылка на сущность Doctor, а не как имя строкой.
     * Это корректно, потому что используется soft delete (врач никогда физически не удаляется),
     * и ссылка всегда останется валидной.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id", nullable = false)
    @ToString.Exclude
    private Doctor doctor;

    /**
     * Дата и время начала прикрепления к данному врачу.
     * Устанавливается в момент смены врача (или при первичной госпитализации).
     */
    @Column(nullable = false)
    private LocalDateTime assignedFrom;

    /**
     * Дата и время окончания прикрепления к данному врачу.
     *
     * <p>Значение {@code null} означает, что это текущее (незакрытое) назначение —
     * пациент прикреплён к этому врачу прямо сейчас.
     *
     * <p>При смене врача сервисный слой выполняет две операции в одной транзакции:
     * <ol>
     *   <li>Закрывает текущую запись: устанавливает {@code assignedTo = LocalDateTime.now()}.</li>
     *   <li>Создаёт новую запись с новым врачом и {@code assignedFrom = LocalDateTime.now()}.</li>
     * </ol>
     */
    private LocalDateTime assignedTo;
}
