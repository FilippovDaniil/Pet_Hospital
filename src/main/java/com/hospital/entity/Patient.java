package com.hospital.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * Сущность «Пациент» — центральная сущность системы, вокруг которой строятся
 * все медицинские процессы: назначение врача, размещение в палате, оказание услуг.
 *
 * <p>Ключевые архитектурные решения:
 * <ul>
 *   <li><b>Soft delete (поле {@code active})</b>: пациент не удаляется физически.
 *       Это сохраняет полную историю лечения и не нарушает внешние ключи
 *       в таблицах PatientDoctorHistory, WardOccupationHistory, PatientPaidService.</li>
 *   <li><b>Денормализация текущего врача и палаты</b>: поля {@code currentDoctor}
 *       и {@code currentWard} дублируют «последнюю активную» запись из таблиц истории.
 *       Это позволяет получить текущее состояние пациента одним простым запросом
 *       без подзапросов к истории.</li>
 *   <li><b>СНИЛС как уникальный идентификатор</b>: обеспечивает невозможность
 *       дублирования записей одного пациента в системе.</li>
 * </ul>
 */
@Entity
@Table(name = "patient")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Patient {

    /**
     * Суррогатный первичный ключ, генерируемый базой данных.
     * Единственное поле в equals/hashCode — корректное поведение для JPA-сущностей.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    /** Полное ФИО пациента. Обязательное поле. */
    @Column(nullable = false)
    private String fullName;

    /**
     * Дата рождения пациента.
     * Тип {@link LocalDate} (без времени) достаточен для даты рождения и
     * корректно отображается на тип DATE в базе данных.
     */
    @Column(nullable = false)
    private LocalDate birthDate;

    /**
     * Биологический пол пациента.
     *
     * <p>{@code @Enumerated(EnumType.STRING)} — хранение строкой ("MALE"/"FEMALE")
     * вместо числового индекса делает базу данных читаемой без знания кода приложения.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Gender gender;

    /**
     * СНИЛС — уникальный номер страхового свидетельства.
     *
     * <p>{@code unique = true} создаёт уникальный индекс на уровне БД, гарантируя
     * отсутствие дублирования данных даже при параллельных вставках из разных потоков.
     */
    @Column(unique = true, nullable = false)
    private String snils;

    /** Контактный телефон пациента или его представителя. Необязательное поле. */
    private String phone;

    /**
     * Адрес пациента.
     * Тип TEXT позволяет хранить адрес любой длины без усечения.
     */
    @Column(columnDefinition = "TEXT")
    private String address;

    /**
     * Дата постановки на учёт в больнице (дата первичной госпитализации или регистрации).
     */
    @Column(nullable = false)
    private LocalDate registrationDate;

    /**
     * Текущий статус пациента.
     *
     * <p>По умолчанию при создании устанавливается TREATMENT — пациент поступает на лечение.
     * {@code @Builder.Default} гарантирует, что Lombok-билдер не проигнорирует
     * инициализатор поля и установит значение по умолчанию.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PatientStatus status = PatientStatus.TREATMENT;

    /**
     * Текущий лечащий врач пациента.
     *
     * <p>Денормализованное поле — быстрый доступ к текущему врачу без JOIN с историей.
     * При смене врача: это поле обновляется И одновременно создаётся запись в
     * {@link PatientDoctorHistory} (закрывается предыдущий период, открывается новый).
     *
     * <p>Nullable: пациент может поступить без назначенного врача.
     *
     * <p>Lazy-загрузка: при массовом списке пациентов врач подгружается только
     * когда явно нужен, избегая N+1 запросов.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_doctor_id")
    @ToString.Exclude
    private Doctor currentDoctor;

    /**
     * Текущая палата пациента.
     *
     * <p>Аналогично {@code currentDoctor}: денормализованное поле для быстрого
     * доступа к текущему размещению. При переводе пациента в другую палату
     * это поле обновляется, а в {@link WardOccupationHistory} добавляется новая запись.
     *
     * <p>Nullable: пациент может быть зарегистрирован, но ещё не размещён в палате.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_ward_id")
    @ToString.Exclude
    private Ward currentWard;

    /**
     * Флаг активности записи — реализация мягкого удаления (soft delete).
     *
     * <p>При «удалении» пациента устанавливается {@code active = false}.
     * Все запросы в репозитории должны добавлять условие {@code WHERE active = true}
     * (или использовать @Where от Hibernate) для исключения неактивных записей
     * из стандартных выборок.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
