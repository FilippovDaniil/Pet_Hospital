package com.hospital.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Сущность «Заметка врача о пациенте» — структурированная запись клинического наблюдения,
 * диагноза или назначения, привязанная к конкретному пациенту и создавшему её врачу.
 *
 * <p>Ключевые архитектурные решения:
 * <ul>
 *   <li><b>Флаг {@code visibleToClient}</b>: врач контролирует, какие заметки видит
 *       пациент в личном кабинете. Заметки для служебного пользования (например,
 *       административные пометки) остаются скрытыми ({@code visibleToClient = false}).
 *       Клиентский портал получает только видимые заметки через
 *       {@code findVisibleByClientUserId}.</li>
 *   <li><b>Типизация через {@link PatientNoteType}</b>: enum позволяет фильтровать
 *       заметки по назначению (диагноз, назначение, наблюдение и т.д.) без необходимости
 *       парсить текстовое содержимое.</li>
 *   <li><b>Иммутабельность создания</b>: {@code createdAt} заполняется однократно в
 *       {@link #onCreate()} и заблокирован от обновлений ({@code updatable = false}).
 *       Заметки не редактируются — новая запись создаётся как исправление.</li>
 *   <li><b>Связь через {@code patient.clientUser.id}</b>: клиентский портал запрашивает
 *       заметки по ID своего аккаунта ({@link User}), без необходимости знать
 *       внутренний ID пациента.</li>
 * </ul>
 */
@Entity
@Table(name = "patient_note")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class PatientNote {

    /**
     * Суррогатный первичный ключ, генерируется базой данных.
     * Участвует в equals/hashCode для корректного сравнения сущностей.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    /**
     * Пациент, к которому относится заметка.
     *
     * <p>{@code fetch = FetchType.LAZY} — пациент не загружается автоматически.
     * При запросах заметок по пациенту ({@code findByPatientId}) объект пациента
     * уже известен из контекста вызова, поэтому дополнительный JOIN не нужен.
     *
     * <p>{@code @ToString.Exclude} — предотвращает циклическую рекурсию в toString():
     * PatientNote → Patient → список заметок → PatientNote → ...
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    @ToString.Exclude
    private Patient patient;

    /**
     * Врач, создавший заметку.
     *
     * <p>{@code fetch = FetchType.LAZY} — врач не загружается автоматически.
     * В обоих запросах репозитория ({@code findByPatientId} и {@code findVisibleByClientUserId})
     * используется {@code JOIN FETCH n.doctor}: ФИО врача всегда отображается рядом
     * с заметкой. JOIN FETCH заменяет N отдельных SELECT одним запросом с JOIN,
     * что критично при отображении длинной истории пациента.
     *
     * <p>{@code @ToString.Exclude} — исключает поле из toString() во избежание рекурсии.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id", nullable = false)
    @ToString.Exclude
    private Doctor doctor;

    /**
     * Тип заметки: диагноз, назначение, наблюдение и т.д.
     *
     * <p>{@code @Enumerated(EnumType.STRING)} — значение enum хранится как строка
     * в базе данных, что обеспечивает читаемость данных и устойчивость к изменению
     * порядка констант в enum.
     *
     * <p>{@code length = 20} — достаточная длина для текущих и ожидаемых значений
     * {@link PatientNoteType}.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PatientNoteType type;

    /**
     * Содержимое заметки в произвольном текстовом формате.
     * {@code columnDefinition = "TEXT"} — тип TEXT в PostgreSQL не ограничен по длине,
     * что позволяет хранить подробные клинические описания без усечения.
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /**
     * Признак видимости заметки для пациента в клиентском портале.
     *
     * <p>Значение {@code true} — заметка отображается пациенту в личном кабинете
     * (например, заключение врача, рекомендации). Значение {@code false} — заметка
     * является служебной и видна только медицинскому персоналу.
     *
     * <p>{@code @Builder.Default} — устанавливает значение {@code false} при создании
     * через Lombok-билдер. Без этой аннотации Lombok игнорирует инициализатор поля.
     * Умолчание «скрыто» безопаснее умолчания «видимо»: врач явно выбирает, что
     * показать пациенту, а не случайно раскрывает служебные записи.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean visibleToClient = false;

    /**
     * Момент создания заметки. Заполняется автоматически в {@link #onCreate()}.
     * {@code updatable = false} — временная метка создания неизменна после сохранения,
     * что обеспечивает хронологическую достоверность истории пациента.
     * Используется для сортировки заметок (новые — первыми).
     */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * JPA lifecycle-колбэк, вызываемый Hibernate перед первым сохранением сущности (INSERT).
     * Устанавливает {@code createdAt} в текущий момент времени сервера.
     * Явный вызов из прикладного кода не требуется.
     */
    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
