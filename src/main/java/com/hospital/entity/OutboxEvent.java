package com.hospital.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Сущность «Исходящее событие» — реализует паттерн «Transactional Outbox»
 * для надёжной публикации событий в Kafka.
 *
 * <p><b>Проблема, которую решает Outbox:</b>
 * Если записать данные в БД и затем отправить событие в Kafka в одном методе, но в разных
 * транзакциях, возможна ситуация: данные сохранены, но событие Kafka не доставлено
 * (сбой сети, перезапуск приложения). Потребители получат неполную картину.
 *
 * <p><b>Решение — Transactional Outbox:</b>
 * <ol>
 *   <li>В той же транзакции, что изменяет бизнес-данные, создаётся запись OutboxEvent
 *       с {@code processed = false}.</li>
 *   <li>Отдельный поток (Outbox Processor / Scheduler) читает необработанные события
 *       и публикует их в Kafka.</li>
 *   <li>После успешной публикации устанавливает {@code processed = true}.</li>
 * </ol>
 * Таким образом, атомарность гарантируется транзакцией БД, а не распределённой координацией.
 *
 * <p><b>Идемпотентность на стороне потребителя:</b>
 * Поле {@code eventId} (UUID) позволяет потребителю проверить, не обрабатывалось ли
 * это событие ранее, и пропустить дубликат.
 */
@Entity
@Table(name = "outbox_event")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class OutboxEvent {

    /**
     * Суррогатный первичный ключ для внутреннего использования.
     * Используется для сортировки при обработке событий в порядке создания.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    /**
     * Уникальный бизнес-идентификатор события (обычно UUID).
     *
     * <p>Отличается от id: этот идентификатор передаётся вместе с payload в Kafka
     * и используется потребителями для дедупликации (проверка «видел ли я это событие?»).
     * Уникальный индекс гарантирует отсутствие дубликатов в таблице outbox.
     */
    @Column(unique = true, nullable = false)
    private String eventId;

    /**
     * Тип события — определяет, какой обработчик на стороне потребителя должен
     * его обработать (например, "PATIENT_ADMITTED", "DOCTOR_ASSIGNED", "SERVICE_PAID").
     * Потребитель использует eventType для маршрутизации к нужному обработчику.
     */
    @Column(nullable = false)
    private String eventType;

    /**
     * Полезная нагрузка события в формате JSON.
     *
     * <p>Тип TEXT (а не VARCHAR) позволяет хранить JSON произвольного размера.
     * JSON-сериализация выполняется в сервисном слое перед сохранением события.
     * Потребитель десериализует строку обратно в объект нужного типа.
     */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String payload;

    /**
     * Дата и время создания записи события.
     *
     * <p>Используется Outbox Processor для обработки событий в хронологическом порядке
     * и для мониторинга задержки (если createdAt давний, а processed = false —
     * есть проблема с доставкой).
     *
     * <p>{@code @Builder.Default} с {@code LocalDateTime.now()} автоматически фиксирует
     * время создания объекта в Java (до INSERT в БД). Альтернатива — использовать
     * {@code @CreationTimestamp} от Hibernate, которая фиксирует время на уровне БД.
     */
    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * Флаг обработки события.
     *
     * <p>{@code false} — событие ожидает публикации в Kafka.
     * {@code true} — событие успешно опубликовано, можно архивировать или удалять.
     *
     * <p>Outbox Processor выбирает записи с {@code WHERE processed = false ORDER BY id}
     * и после успешной отправки обновляет этот флаг.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean processed = false;
}
