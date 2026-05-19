package com.hospital.service.impl;

import com.hospital.dto.request.SendMessageRequest;
import com.hospital.dto.response.ChatMessageResponse;
import com.hospital.dto.response.ChatRoomResponse;
import com.hospital.entity.*;
import com.hospital.exception.BusinessRuleException;
import com.hospital.exception.ResourceNotFoundException;
import com.hospital.repository.ChatMessageRepository;
import com.hospital.repository.ChatRoomRepository;
import com.hospital.repository.UserRepository;
import com.hospital.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public ChatRoomResponse getOrCreateSupportRoom(User client) {
        ChatRoom room = chatRoomRepository
                .findByTypeAndClientUserId(ChatRoomType.SUPPORT, client.getId())
                .orElseGet(() -> chatRoomRepository.save(
                        ChatRoom.builder()
                                .type(ChatRoomType.SUPPORT)
                                .clientUser(client)
                                .build()
                ));
        return toRoomResponse(room, client.getId());
    }

    @Override
    public List<ChatRoomResponse> getAllSupportRooms() {
        return chatRoomRepository.findAllSupportRooms().stream()
                .map(r -> toRoomResponse(r, null))
                .toList();
    }

    @Override
    @Transactional
    public ChatRoomResponse getOrCreateDoctorRoom(User client, Long doctorUserId) {
        User doctorUser = userRepository.findById(doctorUserId)
                .filter(u -> u.getRole() == Role.ROLE_DOCTOR)
                .orElseThrow(() -> new ResourceNotFoundException("Врач с userId=" + doctorUserId + " не найден"));

        ChatRoom room = chatRoomRepository
                .findByTypeAndClientUserIdAndStaffUserId(ChatRoomType.DOCTOR_CLIENT, client.getId(), doctorUserId)
                .orElseGet(() -> chatRoomRepository.save(
                        ChatRoom.builder()
                                .type(ChatRoomType.DOCTOR_CLIENT)
                                .clientUser(client)
                                .staffUser(doctorUser)
                                .build()
                ));
        return toRoomResponse(room, client.getId());
    }

    @Override
    public List<ChatRoomResponse> getDoctorRooms(User doctorUser) {
        return chatRoomRepository.findDoctorRoomsByStaffUserId(doctorUser.getId()).stream()
                .map(r -> toRoomResponse(r, doctorUser.getId()))
                .toList();
    }

    @Override
    public List<ChatRoomResponse> getMyRooms(User client) {
        return chatRoomRepository.findByClientUserId(client.getId()).stream()
                .map(r -> toRoomResponse(r, client.getId()))
                .toList();
    }

    @Override
    public List<ChatMessageResponse> getRoomMessages(Long roomId, User requester) {
        ChatRoom room = getAccessibleRoom(roomId, requester);
        return chatMessageRepository.findByRoomIdOrderBySentAt(room.getId()).stream()
                .map(this::toMessageResponse)
                .toList();
    }

    @Override
    @Transactional
    public ChatMessageResponse sendMessage(Long roomId, SendMessageRequest request, User sender) {
        ChatRoom room = getAccessibleRoom(roomId, sender);
        ChatMessage message = chatMessageRepository.save(
                ChatMessage.builder()
                        .room(room)
                        .sender(sender)
                        .content(request.getContent())
                        .build()
        );
        return toMessageResponse(message);
    }

    @Override
    public List<ChatMessageResponse> pollMessages(Long roomId, Long sinceId, User requester) {
        ChatRoom room = getAccessibleRoom(roomId, requester);
        return chatMessageRepository.findByRoomIdAndIdGreaterThan(room.getId(), sinceId).stream()
                .map(this::toMessageResponse)
                .toList();
    }

    // ─────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────

    private ChatRoom getAccessibleRoom(Long roomId, User user) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Чат-комната #" + roomId + " не найдена"));

        boolean isParticipant = switch (user.getRole()) {
            case ROLE_ADMIN -> true;                         // админ видит всё
            case ROLE_CLIENT -> room.getClientUser().getId().equals(user.getId());
            case ROLE_DOCTOR -> room.getStaffUser() != null && room.getStaffUser().getId().equals(user.getId());
            default -> false;
        };

        if (!isParticipant) {
            throw new BusinessRuleException("Нет доступа к этой чат-комнате");
        }
        return room;
    }

    private ChatRoomResponse toRoomResponse(ChatRoom room, Long viewerUserId) {
        long unread = viewerUserId != null
                ? chatMessageRepository.countUnread(room.getId(), viewerUserId)
                : 0L;

        List<ChatMessage> messages = chatMessageRepository.findByRoomIdOrderBySentAt(room.getId());
        ChatMessage last = messages.isEmpty() ? null : messages.get(messages.size() - 1);

        return ChatRoomResponse.builder()
                .id(room.getId())
                .type(room.getType().name())
                .clientUserId(room.getClientUser().getId())
                .clientUserName(room.getClientUser().getFullName())
                .staffUserId(room.getStaffUser() != null ? room.getStaffUser().getId() : null)
                .staffUserName(room.getStaffUser() != null ? room.getStaffUser().getFullName() : "Служба поддержки")
                .createdAt(room.getCreatedAt())
                .unreadCount(unread)
                .lastMessage(last != null ? last.getContent() : null)
                .lastMessageAt(last != null ? last.getSentAt() : null)
                .build();
    }

    private ChatMessageResponse toMessageResponse(ChatMessage message) {
        return ChatMessageResponse.builder()
                .id(message.getId())
                .roomId(message.getRoom().getId())
                .senderId(message.getSender().getId())
                .senderName(message.getSender().getFullName())
                .senderRole(message.getSender().getRole().name())
                .content(message.getContent())
                .sentAt(message.getSentAt())
                .read(message.isRead())
                .build();
    }
}
