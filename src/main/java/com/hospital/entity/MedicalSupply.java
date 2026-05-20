package com.hospital.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "medical_supply")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class MedicalSupply {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private SupplyCategory category;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false, length = 50)
    private String unit;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** Минимальный остаток для предупреждения о нехватке. */
    @Column(name = "min_quantity", nullable = false)
    @Builder.Default
    private int minQuantity = 5;
}
