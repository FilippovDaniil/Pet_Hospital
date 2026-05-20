package com.hospital.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Позиция склада медикаментов и расходников.
 *
 * Хранит наименование, категорию, текущий остаток и минимальный порог.
 * Мягкого удаления нет — позиции удаляются физически (DELETE).
 *
 * @EqualsAndHashCode(onlyExplicitlyIncluded = true) + @EqualsAndHashCode.Include на id —
 * безопасный Lombok-паттерн для JPA-сущностей: equals/hashCode зависят только от id,
 * не от ленивых прокси-ассоциаций (что предотвращает LazyInitializationException в Set/Map).
 */
@Entity
@Table(name = "medical_supply")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class MedicalSupply {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // auto-increment из БД
    @EqualsAndHashCode.Include
    private Long id;

    /** Название позиции: «Аспирин», «Шприц 5мл», «Тонометр». */
    @Column(nullable = false)
    private String name;

    /**
     * Категория: MEDICINE / CONSUMABLE / EQUIPMENT.
     * EnumType.STRING — хранится как строка «MEDICINE», а не порядковый номер.
     * Строка устойчива к изменению порядка констант в enum.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private SupplyCategory category;

    /** Текущий остаток в единицах хранения (таб., шт., мл.). */
    @Column(nullable = false)
    private int quantity;

    /** Единица измерения: «таб.», «шт.», «мл.». */
    @Column(nullable = false, length = 50)
    private String unit;

    /** Описание: состав, показания, инструкция. Необязательное поле. */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Порог «низкого остатка»: если quantity <= minQuantity → lowStock=true в DTO.
     * @Builder.Default — устанавливает значение 5 при создании через builder.
     * Без этой аннотации Lombok-builder игнорирует инициализатор поля.
     */
    @Column(name = "min_quantity", nullable = false)
    @Builder.Default
    private int minQuantity = 5;
}
