package com.hospital.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Сущность «Отделение больницы» — структурное подразделение, объединяющее врачей и палаты.
 *
 * <p>Аннотация {@code @Entity} сообщает JPA (Hibernate), что этот класс является управляемой
 * сущностью и должен быть отображён на таблицу в реляционной базе данных.
 *
 * <p>{@code @Table(name = "department")} явно задаёт имя таблицы. Без этой аннотации Hibernate
 * использовал бы имя класса в нижнем регистре, что совпадает в данном случае, но явное
 * указание — лучшая практика: код не ломается при рефакторинге имени класса.
 *
 * <p>Lombok-аннотации:
 * <ul>
 *   <li>{@code @Data} — генерирует геттеры, сеттеры, toString, equals, hashCode.</li>
 *   <li>{@code @Builder} — позволяет создавать объекты через паттерн «строитель»:
 *       {@code Department.builder().name("Кардиология").build()}.</li>
 *   <li>{@code @NoArgsConstructor} и {@code @AllArgsConstructor} — конструкторы без аргументов
 *       (обязателен для JPA — Hibernate создаёт объекты через рефлексию) и со всеми полями.</li>
 *   <li>{@code @EqualsAndHashCode(onlyExplicitlyIncluded = true)} — equals и hashCode
 *       вычисляются только по полям, помеченным {@code @EqualsAndHashCode.Include}.
 *       Это критически важно для JPA: включение связанных сущностей в equals/hashCode
 *       приводит к бесконечной рекурсии и OutOfMemoryError.</li>
 * </ul>
 */
@Entity
@Table(name = "department")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Department {

    /**
     * Суррогатный первичный ключ.
     *
     * <p>{@code @Id} — помечает поле как первичный ключ таблицы.
     * {@code @GeneratedValue(strategy = GenerationType.IDENTITY)} — делегирует генерацию
     * значения базе данных (столбец SERIAL / AUTO_INCREMENT в PostgreSQL/MySQL).
     * Это самая простая стратегия, но требует отдельного запроса INSERT для получения
     * сгенерированного id (Hibernate не может использовать batch insert с этой стратегией).
     *
     * <p>{@code @EqualsAndHashCode.Include} — только id участвует в equals/hashCode,
     * что корректно: два объекта Department равны тогда и только тогда, когда у них один id.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    /**
     * Название отделения (например, «Кардиология», «Хирургия»).
     * {@code nullable = false} транслируется в ограничение NOT NULL на уровне DDL,
     * и Hibernate также проверяет это перед сохранением.
     */
    @Column(nullable = false)
    private String name;

    /**
     * Текстовое описание отделения: профиль, специализация, особенности.
     * {@code columnDefinition = "TEXT"} задаёт тип столбца напрямую через DDL,
     * что позволяет хранить тексты произвольной длины (в отличие от VARCHAR(255)).
     */
    @Column(columnDefinition = "TEXT")
    private String description;

    /** Физическое местоположение отделения в здании больницы (этаж, корпус и т.д.). */
    private String location;

    /**
     * Заведующий отделением — один врач на одно отделение.
     *
     * <p>{@code @OneToOne} — тип связи «один к одному»: у каждого отделения не более
     * одного заведующего, и каждый врач может возглавлять не более одного отделения.
     *
     * <p>{@code fetch = FetchType.LAZY} — ленивая загрузка: объект Doctor не будет загружен
     * из базы данных до тех пор, пока к нему не обратятся явно (getHeadDoctor()).
     * Это оптимизация производительности: при загрузке Department не выполняется
     * лишний JOIN к таблице doctor, если заведующий не нужен.
     *
     * <p>{@code @JoinColumn(name = "head_doctor_id")} — указывает, что внешний ключ хранится
     * в столбце head_doctor_id таблицы department (owning side связи).
     *
     * <p>{@code @ToString.Exclude} — исключает поле из toString(), чтобы избежать
     * бесконечной рекурсии: Department → Doctor → Department → ...
     *
     * <p>Поле nullable (без {@code nullable = false}): отделение может существовать
     * без назначенного заведующего.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "head_doctor_id")
    @ToString.Exclude
    private Doctor headDoctor;
}
