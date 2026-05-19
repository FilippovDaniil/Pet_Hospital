package com.hospital.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Сущность «Медицинский документ» — официальный документ, выданный врачом пациенту.
 *
 * <p>Ключевые архитектурные решения:
 * <ul>
 *   <li><b>Soft delete через флаг {@code active}</b>: документы никогда не удаляются
 *       физически. Установка {@code active = false} скрывает документ из интерфейса
 *       клиента, сохраняя медицинскую историю в полном объёме для врачей и аудита.</li>
 *   <li><b>Разделение аудитории запросов</b>: врач получает все документы пациента
 *       (включая неактивные) через {@code findByPatientId}, клиент видит только
 *       активные документы через {@code findByPatientClientUserId} — с фильтром
 *       {@code active = true} непосредственно в JPQL-запросе.</li>
 *   <li><b>Опциональный {@code validUntil}</b>: не все документы имеют срок действия.
 *       Справки и выписки действуют бессрочно, рецепты — ограниченное время.
 *       Nullable-поле позволяет использовать одну сущность для обоих случаев.</li>
 *   <li><b>Тип документа через enum {@link MedicalDocumentType}</b>: хранится как строка
 *       ({@code EnumType.STRING}) для читаемости данных в БД и защиты от ошибок
 *       при рефакторинге enum.</li>
 * </ul>
 */
@Entity
@Table(name = "medical_document")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class MedicalDocument {

    /**
     * Суррогатный первичный ключ, генерируется базой данных.
     * Участвует в equals/hashCode для корректного сравнения сущностей.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    /**
     * Пациент, которому выдан документ.
     *
     * <p>{@code fetch = FetchType.LAZY} — пациент не загружается автоматически.
     * Это важно при запросах документов по врачу ({@code findByDoctorId}): в этом
     * случае используется {@code JOIN FETCH d.patient}, загружая данные за один запрос.
     * При запросах по пациенту ({@code findByPatientId}) пациент уже известен и
     * JOIN FETCH не нужен.
     *
     * <p>{@code @ToString.Exclude} — предотвращает циклическую рекурсию в toString():
     * MedicalDocument → Patient → список документов → ...
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    @ToString.Exclude
    private Patient patient;

    /**
     * Врач, создавший документ.
     *
     * <p>{@code fetch = FetchType.LAZY} — врач не загружается автоматически.
     * В запросах по пациенту используется {@code JOIN FETCH d.doctor}, так как
     * ФИО и специальность врача всегда отображаются рядом с документом.
     * JOIN FETCH заменяет N отдельных SELECT одним запросом с JOIN.
     *
     * <p>{@code @ToString.Exclude} — исключает из toString() для предотвращения рекурсии.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id", nullable = false)
    @ToString.Exclude
    private Doctor doctor;

    /**
     * Тип медицинского документа (направление, рецепт, выписка и т.д.).
     *
     * <p>{@code @Enumerated(EnumType.STRING)} — значение enum хранится как строка
     * в базе данных. В отличие от {@code EnumType.ORDINAL}, строковое представление
     * не зависит от порядка констант в enum, что защищает от тихой порчи данных
     * при добавлении или переупорядочивании значений.
     *
     * <p>{@code length = 30} — длина с запасом для текущих и будущих значений enum.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MedicalDocumentType type;

    /**
     * Заголовок документа, отображаемый в списке (например: «Рецепт №123», «Выписной эпикриз»).
     * Обязательное поле — документ без названия невозможно идентифицировать в списке.
     */
    @Column(nullable = false)
    private String title;

    /**
     * Полное содержимое документа в текстовом виде.
     * {@code columnDefinition = "TEXT"} — тип TEXT в PostgreSQL не ограничен по длине,
     * что необходимо для хранения подробных медицинских заключений, анамнеза и назначений.
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /**
     * Дата и время выдачи документа. Заполняется автоматически в {@link #onCreate()}.
     * {@code updatable = false} — временная метка выдачи неизменна после сохранения,
     * что обеспечивает юридическую достоверность документа.
     */
    @Column(nullable = false, updatable = false)
    private LocalDateTime issuedAt;

    /**
     * Дата окончания действия документа. Необязательное поле.
     *
     * <p>Заполняется для документов с ограниченным сроком действия (например,
     * рецепты действительны 30 дней). Для бессрочных документов (справки,
     * выписки) остаётся {@code null}. {@code LocalDate} — без времени, так
     * как срок обычно указывается только датой.
     */
    @Column
    private LocalDate validUntil;

    /**
     * Флаг активности документа — реализация паттерна «мягкого удаления» (soft delete).
     *
     * <p>Документ с {@code active = false} не отображается клиенту в личном кабинете
     * (запрос {@code findByPatientClientUserId} фильтрует по {@code active = true}),
     * но остаётся доступным врачу через {@code findByPatientId}.
     *
     * <p>{@code @Builder.Default} — устанавливает значение {@code true} при создании
     * через Lombok-билдер. Без этой аннотации Lombok игнорирует инициализатор поля,
     * и все новые документы, созданные через билдер, имели бы {@code active = false},
     * что означало бы немедленное скрытие от клиента.
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    /**
     * JPA lifecycle-колбэк, вызываемый Hibernate перед первым сохранением сущности (INSERT).
     * Устанавливает {@code issuedAt} в текущий момент времени сервера.
     * Явный вызов из прикладного кода не требуется.
     */
    @PrePersist
    void onCreate() {
        issuedAt = LocalDateTime.now();
    }
}
