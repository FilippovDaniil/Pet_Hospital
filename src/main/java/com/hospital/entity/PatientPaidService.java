package com.hospital.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Сущность «Назначение платной услуги пациенту» — связующая таблица между пациентом
 * и справочником платных услуг, реализующая связь «многие ко многим» через промежуточную
 * сущность с дополнительными атрибутами.
 *
 * <p>Почему не используется {@code @ManyToMany} напрямую?
 * Аннотация {@code @ManyToMany} генерирует чистую связующую таблицу без дополнительных столбцов.
 * Здесь нужны метаданные назначения: дата ({@code assignedDate}) и статус оплаты ({@code paid}).
 * Поэтому связь разбита на две {@code @ManyToOne}: Patient → PatientPaidService ← PaidService.
 *
 * <p>Паттерн «ассоциативная сущность» (associative entity / junction entity) —
 * стандартная практика при необходимости хранить атрибуты самой связи.
 */
@Entity
@Table(name = "patient_paid_service")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class PatientPaidService {

    /**
     * Суррогатный первичный ключ назначения.
     * Альтернативой мог бы быть составной ключ (patient_id + paid_service_id + assigned_date),
     * но суррогатный id проще в использовании и гибче (пациенту можно назначить одну услугу дважды).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    /**
     * Пациент, которому назначена услуга.
     *
     * <p>{@code @ManyToOne} — одному пациенту может быть назначено несколько услуг.
     * {@code nullable = false} — назначение без пациента не имеет смысла.
     * Lazy-загрузка предотвращает автоматический JOIN при запросе списка назначений.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    @ToString.Exclude
    private Patient patient;

    /**
     * Назначенная платная услуга.
     *
     * <p>{@code @ManyToOne} — одна услуга может быть назначена многим пациентам.
     * Внешний ключ paid_service_id хранится в таблице patient_paid_service (owning side).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paid_service_id", nullable = false)
    @ToString.Exclude
    private PaidService paidService;

    /**
     * Дата и время назначения услуги.
     *
     * <p>{@link LocalDateTime} — дата плюс время без часового пояса. Для медицинских систем
     * в пределах одного часового пояса этого достаточно. Если система работает
     * в нескольких часовых поясах, следует использовать {@code ZonedDateTime} или
     * {@code OffsetDateTime}.
     *
     * <p>{@code @Builder.Default} с {@code LocalDateTime.now()} — при создании объекта
     * через билдер дата назначения автоматически устанавливается на текущий момент.
     */
    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime assignedDate = LocalDateTime.now();

    /**
     * Флаг оплаты услуги.
     *
     * <p>По умолчанию {@code false}: услуга назначена, но ещё не оплачена.
     * После оплаты устанавливается {@code true} через отдельный API-эндпоинт.
     *
     * <p>{@code @Column(name = "is_paid")} — явное указание имени столбца, так как
     * Hibernate по умолчанию мог бы создать столбец с именем "paid", но принято
     * называть булевые столбцы с префиксом "is_".
     */
    @Column(name = "is_paid", nullable = false)
    @Builder.Default
    private boolean paid = false;
}
