package com.hospital.repository;

import com.hospital.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Репозиторий для работы с сущностью {@link OutboxEvent} — событиями паттерна Outbox.
 *
 * <p><b>Паттерн Transactional Outbox (исходящий ящик):</b><br>
 * Используется для надёжной публикации событий (например, в Kafka или RabbitMQ)
 * без риска потери данных при сбое.<br>
 * Принцип работы:
 * <ol>
 *   <li>В одной транзакции с бизнес-операцией (например, госпитализация пациента)
 *       событие сохраняется в таблицу {@code outbox_events} в БД.</li>
 *   <li>Отдельный фоновый процесс (Outbox Poller) периодически читает
 *       непубликованные события из таблицы и отправляет их в брокер сообщений.</li>
 *   <li>После успешной отправки запись помечается как обработанная или удаляется.</li>
 * </ol>
 * Это гарантирует "ровно одну" (at-least-once) доставку событий даже при падении
 * сервиса после коммита транзакции, но до отправки в брокер.</p>
 *
 * <p>Поле {@code eventId} — уникальный идентификатор события (UUID),
 * позволяет обнаружить дубликаты на стороне получателя (idempotency check).</p>
 */
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * Проверяет, существует ли событие с указанным идентификатором.
     *
     * <p><b>SQL, генерируемый Spring Data JPA:</b><br>
     * {@code SELECT CASE WHEN COUNT(*) > 0 THEN true ELSE false END
     * FROM outbox_events WHERE event_id = ?}</p>
     *
     * <p>Используется для проверки идемпотентности: перед сохранением нового
     * события сервис может убедиться, что событие с таким ID ещё не было
     * записано (защита от дублирования при повторных попытках).</p>
     *
     * @param eventId уникальный идентификатор события (обычно UUID)
     * @return {@code true}, если событие с данным ID уже существует в таблице
     */
    boolean existsByEventId(String eventId);

    /**
     * Ищет событие по его уникальному идентификатору.
     *
     * <p><b>SQL, генерируемый Spring Data JPA:</b><br>
     * {@code SELECT * FROM outbox_events WHERE event_id = ?}</p>
     *
     * <p>Используется Outbox Poller'ом или обработчиком для нахождения
     * конкретного события: например, чтобы пометить его как успешно отправленное
     * (обновить статус или удалить запись).</p>
     *
     * <p>Возврат {@link Optional} позволяет обработать ситуацию, когда событие
     * уже было удалено другим экземпляром сервиса (в кластерной среде).</p>
     *
     * @param eventId уникальный идентификатор события
     * @return {@link Optional} с событием, если оно найдено
     */
    Optional<OutboxEvent> findByEventId(String eventId);
}
