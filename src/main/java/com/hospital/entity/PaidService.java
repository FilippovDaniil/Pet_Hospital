package com.hospital.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Сущность «Платная услуга» — справочник медицинских услуг, оказываемых за отдельную плату.
 *
 * <p>Это справочная сущность (reference/dictionary entity): её записи создаются администратором
 * и используются как шаблоны при назначении услуг конкретным пациентам через
 * {@link PatientPaidService}.
 *
 * <p>Ключевые архитектурные решения:
 * <ul>
 *   <li><b>Soft delete через {@code active}</b>: услугу нельзя удалить физически, если она
 *       уже была назначена пациентам — это нарушило бы ссылочную целостность.
 *       Деактивация скрывает услугу из списка доступных, не затрагивая историю.</li>
 *   <li><b>{@link BigDecimal} для цены</b>: используется вместо double/float, потому что
 *       числа с плавающей точкой не подходят для денежных расчётов из-за ошибок
 *       округления. BigDecimal обеспечивает точное десятичное представление.</li>
 * </ul>
 */
@Entity
@Table(name = "paid_service")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class PaidService {

    /**
     * Суррогатный первичный ключ.
     * Используется как внешний ключ в таблице patient_paid_service.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    /**
     * Название услуги (например, «МРТ головного мозга», «Консультация кардиолога»).
     * Обязательное поле — без названия услуга не имеет смысла.
     */
    @Column(nullable = false)
    private String name;

    /**
     * Стоимость услуги в рублях.
     *
     * <p>{@code precision = 10} — суммарное число значимых цифр (до 10 цифр).
     * {@code scale = 2} — два знака после запятой (копейки).
     * Таким образом, максимальная цена — 99 999 999,99 руб.
     *
     * <p>Тип BigDecimal на уровне БД отображается на NUMERIC(10, 2) / DECIMAL(10, 2).
     */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    /**
     * Подробное описание услуги: что включает, показания, противопоказания.
     * Необязательное поле; тип TEXT позволяет хранить длинные описания.
     */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Флаг активности услуги (soft delete).
     *
     * <p>Деактивированная услуга не показывается при назначении пациентам,
     * но все существующие записи в PatientPaidService сохраняются корректно
     * (внешние ключи не нарушаются).
     *
     * <p>{@code @Builder.Default} обязателен при использовании @Builder:
     * без него значение {@code true} из инициализатора было бы проигнорировано.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
