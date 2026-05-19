package com.hospital.service;

import com.hospital.dto.request.SendMessageRequest;
import com.hospital.dto.response.ChatMessageResponse;
import com.hospital.dto.response.ChatRoomResponse;
import com.hospital.entity.User;

import java.util.List;

public interface ChatService {

    /** Клиент: получить или создать комнату поддержки. */
    ChatRoomResponse getOrCreateSupportRoom(User client);

    /** Администратор: список всех комнат поддержки. */
    List<ChatRoomResponse> getAllSupportRooms();

    /** Клиент: получить или создать комнату с конкретным врачом. */
    ChatRoomResponse getOrCreateDoctorRoom(User client, Long doctorUserId);

    /** Врач: список комнат с пациентами. */
    List<ChatRoomResponse> getDoctorRooms(User doctorUser);

    /** Клиент: список всех своих чатов. */
    List<ChatRoomResponse> getMyRooms(User client);

    /** Получить все сообщения комнаты. Доступ проверяется на уровне сервиса. */
    List<ChatMessageResponse> getRoomMessages(Long roomId, User requester);

    /** Отправить сообщение в комнату. */
    ChatMessageResponse sendMessage(Long roomId, SendMessageRequest request, User sender);

    /** Вернуть только сообщения новее sinceId (для polling). */
    List<ChatMessageResponse> pollMessages(Long roomId, Long sinceId, User requester);
}
