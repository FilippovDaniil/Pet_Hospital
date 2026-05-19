package com.hospital.repository;

import com.hospital.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /** Все сообщения комнаты, хронологически. */
    @Query("SELECT m FROM ChatMessage m JOIN FETCH m.sender WHERE m.room.id = :roomId ORDER BY m.sentAt ASC")
    List<ChatMessage> findByRoomIdOrderBySentAt(@Param("roomId") Long roomId);

    /** Сообщения новее указанного ID (для long-polling / short-polling). */
    @Query("SELECT m FROM ChatMessage m JOIN FETCH m.sender WHERE m.room.id = :roomId AND m.id > :sinceId ORDER BY m.sentAt ASC")
    List<ChatMessage> findByRoomIdAndIdGreaterThan(@Param("roomId") Long roomId, @Param("sinceId") Long sinceId);

    /** Количество непрочитанных сообщений в комнате для данного пользователя. */
    @Query("SELECT COUNT(m) FROM ChatMessage m WHERE m.room.id = :roomId AND m.read = false AND m.sender.id <> :userId")
    long countUnread(@Param("roomId") Long roomId, @Param("userId") Long userId);
}
