package com.hospital.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Сущность «Палата» — физическое помещение в отделении для размещения пациентов.
 *
 * <p>Ключевые архитектурные решения:
 * <ul>
 *   <li><b>Составной уникальный ключ</b>: комбинация ward_number + department_id уникальна,
 *       что позволяет иметь палату №101 одновременно в кардиологии и хирургии.</li>
 *   <li><b>Денормализованный счётчик currentOccupancy</b>: вместо вычисления
 *       {@code COUNT(*) FROM ward_occupation_history WHERE ward_id = ? AND discharged_at IS NULL}
 *       при каждом запросе, текущая заполненность хранится прямо в записи палаты.
 *       Это ускоряет чтение ценой необходимости синхронного обновления при заселении/выписке.</li>
 * </ul>
 */
@Entity
@Table(name = "ward", uniqueConstraints = {
        // Номер палаты уникален в пределах одного отделения (но не по всей больнице)
        @UniqueConstraint(columnNames = {"ward_number", "department_id"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Ward {

    /**
     * Суррогатный первичный ключ.
     * Используется для связей с другими сущностями (Patient, WardOccupationHistory).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    /**
     * Номер палаты (например, "101", "2А").
     * Тип String, а не int, чтобы поддерживать буквенные обозначения (например, "10Б").
     */
    @Column(name = "ward_number", nullable = false)
    private String wardNumber;

    /**
     * Общая вместимость палаты (количество коек).
     * Используется совместно с currentOccupancy для проверки наличия свободных мест.
     */
    @Column(nullable = false)
    private int capacity;

    /**
     * Текущее число занятых коек.
     *
     * <p>Денормализованное поле: обновляется при каждом заселении и выписке пациента
     * в сервисном слое. Позволяет получить количество свободных мест одним простым
     * запросом без агрегации по истории размещений.
     *
     * <p>{@code @Builder.Default} нужен, чтобы Lombok-билдер инициализировал поле
     * нулём, а не игнорировал инициализатор.
     */
    @Column(nullable = false)
    @Builder.Default
    private int currentOccupancy = 0;

    /**
     * Отделение, которому принадлежит палата.
     *
     * <p>{@code nullable = false} — палата обязана принадлежать конкретному отделению,
     * «бесхозных» палат быть не может.
     *
     * <p>Lazy-загрузка: при запросе палаты отделение подгружается только при явном
     * вызове {@code getDepartment()}, что экономит JOIN при массовой выборке палат.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id", nullable = false)
    @ToString.Exclude
    private Department department;

    /**
     * Вычисляет количество свободных коек.
     *
     * <p>Это вспомогательный метод, не хранящийся в базе данных (нет аннотации @Column).
     * Вызывается в сервисном слое перед размещением нового пациента, чтобы убедиться,
     * что в палате есть свободное место.
     *
     * @return число незанятых коек (0 означает, что палата заполнена)
     */
    public int freeSlots() {
        // Простое вычитание: общая ёмкость минус уже занятые места
        return capacity - currentOccupancy;
    }
}
