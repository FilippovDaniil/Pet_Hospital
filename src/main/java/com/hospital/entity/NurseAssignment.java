package com.hospital.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Назначение процедуры клиенту от медсестры.
 *
 * Связывает:
 *   - clientUser (User, ROLE_CLIENT) — пациент, получающий процедуру.
 *   - nurseUser  (User, ROLE_NURSE)  — медсестра, выдающая назначение.
 *
 * Жизненный цикл статуса: ACTIVE → DONE | CANCELLED.
 * Физическое удаление — мягкого удаления нет.
 *
 * @ToString.Exclude на lazy-ассоциациях — предотвращает LazyInitializationException
 * при вызове toString() вне транзакции (Lombok генерирует toString по умолчанию).
 */
@Entity
@Table(name = "nurse_assignment")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class NurseAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    /**
     * Клиент-получатель назначения.
     * LAZY — пользователь не загружается автоматически с каждым назначением.
     * Репозитории используют JOIN FETCH для загрузки там, где это нужно.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_user_id", nullable = false)
    @ToString.Exclude // исключаем из toString во избежание LazyInitializationException
    private User clientUser;

    /** Медсестра, создавшая назначение. Аналогично clientUser — LAZY + @ToString.Exclude. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nurse_user_id", nullable = false)
    @ToString.Exclude
    private User nurseUser;

    /**
     * Тип процедуры: INJECTION / PILL / DRESSING / PROCEDURE / OTHER.
     * EnumType.STRING — хранится строкой «INJECTION», устойчиво к рефакторингу enum.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "procedure_type", nullable = false, length = 50)
    private ProcedureType procedureType;

    /** Краткое название назначения: «Укол витамина C», «Приём Аспирина 500мг». */
    @Column(nullable = false)
    private String title;

    /** Подробное описание: инструкция, противопоказания. Необязательное. */
    @Column(columnDefinition = "TEXT")
    private String description;

    /** Доза / способ приёма: «1 мл», «1 таб. 2 р/д». Необязательное. */
    @Column(length = 100)
    private String dosage;

    /** Запланированная дата выполнения. Может быть null (без конкретной даты). */
    @Column(name = "scheduled_date")
    private LocalDate scheduledDate;

    /** Запланированное время выполнения. Может быть null. */
    @Column(name = "scheduled_time")
    private LocalTime scheduledTime;

    /**
     * Текущий статус: ACTIVE (активно), DONE (выполнено), CANCELLED (отменено).
     * @Builder.Default — при создании через builder статус ACTIVE по умолчанию.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AssignmentStatus status = AssignmentStatus.ACTIVE;

    /**
     * Дата и время создания назначения.
     * updatable = false — JPA не включает это поле в UPDATE-запросы.
     * Устанавливается один раз в @PrePersist.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * JPA-хук: вызывается автоматически перед первым INSERT.
     * Гарантирует, что createdAt всегда заполнен и status не null.
     */
    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();            // фиксируем момент создания
        if (status == null) status = AssignmentStatus.ACTIVE; // страховка от null через setStatus
    }
}
