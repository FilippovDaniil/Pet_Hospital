package com.hospital.repository;

import com.hospital.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Репозиторий для работы с сообщениями чата ({@link ChatMessage}).
 *
 * <p>Ключевые особенности запросов:
 * <ul>
 *   <li><b>JOIN FETCH m.sender</b>: поле {@code sender} в {@link ChatMessage} объявлено
 *       LAZY. Все запросы, возвращающие список сообщений, используют JOIN FETCH, так как
 *       имя отправителя требуется при каждом отображении переписки. Без JOIN FETCH
 *       Hibernate выполнял бы отдельный SELECT для каждого отправителя.</li>
 *   <li><b>Polling-запрос по sinceId</b>: метод {@link #findByRoomIdAndIdGreaterThan}
 *       используется для реализации short-polling — клиент периодически запрашивает
 *       только новые сообщения с {@code id > sinceId}, не перезагружая всю историю.</li>
 *   <li><b>Счётчик непрочитанных</b>: метод {@link #countUnread} считает только
 *       сообщения от других участников, исключая собственные сообщения пользователя
 *       ({@code m.sender.id <> :userId}).</li>
 * </ul>
 */
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /**
     * Возвращает все сообщения комнаты в хронологическом порядке (от старых к новым).
     *
     * <p>Используется при первоначальной загрузке чата или при открытии комнаты.
     * {@code JOIN FETCH m.sender} — загружает отправителя за один запрос, предотвращая
     * N+1 при отображении каждого сообщения с именем автора.
     *
     * <p>Сортировка {@code ORDER BY m.sentAt ASC} соответствует стандартному
     * отображению переписки: старые сообщения сверху, новые снизу.
     *
     * @param roomId идентификатор чат-комнаты
     * @return список всех сообщений комнаты, упорядоченный от старых к новым
     */
    @Query("SELECT m FROM ChatMessage m JOIN FETCH m.sender WHERE m.room.id = :roomId ORDER BY m.sentAt ASC")
    List<ChatMessage> findByRoomIdOrderBySentAt(@Param("roomId") Long roomId);

    /**
     * Возвращает сообщения комнаты, поступившие после указанного сообщения (курсорная пагинация).
     *
     * <p>Используется для реализации short-polling: клиент запоминает ID последнего
     * полученного сообщения и периодически запрашивает только новые ({@code id > sinceId}).
     * Такой подход эффективнее повторной загрузки всей истории.
     *
     * <p>ID монотонно возрастает (IDENTITY), поэтому {@code id > sinceId} корректно
     * отражает «новее, чем указанное сообщение» без необходимости сравнивать метки времени.
     *
     * @param roomId  идентификатор чат-комнаты
     * @param sinceId идентификатор сообщения-курсора; возвращаются только сообщения с ID строго больше
     * @return список новых сообщений, упорядоченный от старых к новым
     */
    @Query("SELECT m FROM ChatMessage m JOIN FETCH m.sender WHERE m.room.id = :roomId AND m.id > :sinceId ORDER BY m.sentAt ASC")
    List<ChatMessage> findByRoomIdAndIdGreaterThan(@Param("roomId") Long roomId, @Param("sinceId") Long sinceId);

    /**
     * Считает количество непрочитанных сообщений в комнате для указанного пользователя.
     *
     * <p>Условие {@code m.sender.id <> :userId} исключает собственные сообщения
     * пользователя из подсчёта: пользователь не должен видеть бейдж непрочитанных
     * на своих же сообщениях.
     *
     * <p>Используется для отображения числового бейджа на иконке чата и в списке комнат.
     *
     * @param roomId идентификатор чат-комнаты
     * @param userId идентификатор пользователя, для которого считаются непрочитанные
     * @return количество непрочитанных сообщений от других участников в данной комнате
     */
    @Query("SELECT COUNT(m) FROM ChatMessage m WHERE m.room.id = :roomId AND m.read = false AND m.sender.id <> :userId")
    long countUnread(@Param("roomId") Long roomId, @Param("userId") Long userId);

    /**
     * Помечает входящие сообщения комнаты как прочитанные для указанного пользователя.
     *
     * <p>Условия UPDATE:
     * <ul>
     *   <li>{@code m.room.id = :roomId}      — только сообщения данной комнаты.</li>
     *   <li>{@code m.sender.id <> :userId}   — только входящие (не собственные) сообщения.</li>
     *   <li>{@code m.read = false}            — только ещё непрочитанные (оптимизация: не трогаем уже прочитанные).</li>
     * </ul>
     *
     * <p>{@code @Modifying} — обязателен для DML-запросов (UPDATE/DELETE) в Spring Data JPA.
     * Без него Spring Data ожидает SELECT и выбрасывает исключение.
     *
     * <p>Вызывается в {@code getRoomMessages} и {@code pollMessages} (ChatServiceImpl),
     * что гарантирует: бейдж «непрочитанных» сбрасывается в момент просмотра/опроса.
     *
     * @param roomId идентификатор чат-комнаты
     * @param userId идентификатор пользователя, который читает (его собственные сообщения не трогаем)
     */
    @Modifying
    @Query("UPDATE ChatMessage m SET m.read = true WHERE m.room.id = :roomId AND m.sender.id <> :userId AND m.read = false")
    void markMessagesAsRead(@Param("roomId") Long roomId, @Param("userId") Long userId);
}
