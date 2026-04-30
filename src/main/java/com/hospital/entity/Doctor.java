package com.hospital.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Сущность «Врач» — медицинский работник, закреплённый за отделением.
 *
 * <p>Ключевые архитектурные решения:
 * <ul>
 *   <li><b>Soft delete через флаг {@code active}</b>: врач никогда не удаляется физически
 *       из базы данных. Вместо этого устанавливается {@code active = false}. Это сохраняет
 *       ссылочную целостность: история пациентов (PatientDoctorHistory) и другие записи
 *       продолжают корректно ссылаться на уволенного врача.</li>
 *   <li><b>Связь с Department через @ManyToOne</b>: несколько врачей могут работать
 *       в одном отделении, но каждый врач принадлежит только одному отделению.</li>
 * </ul>
 */
@Entity
@Table(name = "doctor")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Doctor {

    /**
     * Суррогатный первичный ключ, генерируется базой данных.
     * Участвует в equals/hashCode для корректного сравнения сущностей.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    /**
     * Полное имя врача (Фамилия Имя Отчество).
     * Не может быть null — обязательное поле при регистрации.
     */
    @Column(nullable = false)
    private String fullName;

    /**
     * Медицинская специализация врача.
     *
     * <p>{@code @Enumerated(EnumType.STRING)} — значение enum сохраняется в базу как строка
     * (например, "CARDIOLOGIST"), а не как числовой индекс. Это защищает от ошибок:
     * если добавить новую константу в начало enum, числовые индексы сместятся, но
     * строковые значения останутся корректными.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Specialty specialty;

    /** Номер кабинета приёма. Опциональное поле — кабинет может быть не назначен. */
    private String cabinetNumber;

    /** Контактный телефон врача для внутренней связи. */
    private String phone;

    /**
     * Отделение, к которому прикреплён врач.
     *
     * <p>{@code @ManyToOne} — многие врачи могут работать в одном отделении.
     * Это владеющая сторона связи (owning side): внешний ключ department_id
     * хранится в таблице doctor.
     *
     * <p>{@code fetch = FetchType.LAZY} — отделение не загружается автоматически
     * при чтении врача. Это снижает количество JOIN-запросов, когда информация
     * об отделении не нужна.
     *
     * <p>{@code @ToString.Exclude} — предотвращает циклическую рекурсию в toString():
     * Doctor → Department → headDoctor (Doctor) → Department → ...
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    @ToString.Exclude
    private Department department;

    /**
     * Флаг активности врача — реализация паттерна «мягкого удаления» (soft delete).
     *
     * <p>Вместо физического удаления записи ({@code DELETE FROM doctor WHERE id = ?})
     * устанавливается {@code active = false}. Преимущества:
     * <ul>
     *   <li>Сохраняется историческая информация о лечении пациентов.</li>
     *   <li>Нет проблем с нарушением ссылочной целостности (foreign key constraints).</li>
     *   <li>Данные можно восстановить (например, при ошибочном увольнении).</li>
     * </ul>
     *
     * <p>{@code @Builder.Default} — устанавливает значение по умолчанию в Lombok-билдере.
     * Без этой аннотации {@code Doctor.builder().build()} создал бы объект с {@code active = false},
     * потому что Lombok игнорирует инициализаторы полей при использовании @Builder.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
