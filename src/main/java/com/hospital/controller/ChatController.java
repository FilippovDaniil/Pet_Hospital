package com.hospital.controller;

import com.hospital.dto.request.SendMessageRequest;
import com.hospital.dto.response.ChatMessageResponse;
import com.hospital.dto.response.ChatRoomResponse;
import com.hospital.entity.User;
import com.hospital.repository.UserRepository;
import com.hospital.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Tag(name = "Chat", description = "Чат поддержки и чат врача с пациентом")
public class ChatController {

    private final ChatService chatService;
    private final UserRepository userRepository;

    // ─────────────────────────────────────────────
    // SUPPORT CHAT
    // ─────────────────────────────────────────────

    @PostMapping("/support")
    @Operation(summary = "Клиент: получить или создать комнату поддержки")
    public ResponseEntity<ChatRoomResponse> getOrCreateSupportRoom(Authentication auth) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(chatService.getOrCreateSupportRoom(currentUser(auth)));
    }

    @GetMapping("/support")
    @Operation(summary = "Администратор: список всех чатов поддержки")
    public List<ChatRoomResponse> getAllSupportRooms() {
        return chatService.getAllSupportRooms();
    }

    // ─────────────────────────────────────────────
    // DOCTOR CHAT
    // ─────────────────────────────────────────────

    @PostMapping("/doctor/{doctorUserId}")
    @Operation(summary = "Клиент: получить или создать чат с врачом")
    public ResponseEntity<ChatRoomResponse> getOrCreateDoctorRoom(
            @PathVariable Long doctorUserId,
            Authentication auth) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(chatService.getOrCreateDoctorRoom(currentUser(auth), doctorUserId));
    }

    @GetMapping("/doctor/rooms")
    @Operation(summary = "Врач: список чатов со своими пациентами")
    public List<ChatRoomResponse> getDoctorRooms(Authentication auth) {
        return chatService.getDoctorRooms(currentUser(auth));
    }

    // ─────────────────────────────────────────────
    // MY ROOMS (CLIENT)
    // ─────────────────────────────────────────────

    @GetMapping("/my-rooms")
    @Operation(summary = "Клиент: список всех своих чатов")
    public List<ChatRoomResponse> getMyRooms(Authentication auth) {
        return chatService.getMyRooms(currentUser(auth));
    }

    // ─────────────────────────────────────────────
    // MESSAGES (shared)
    // ─────────────────────────────────────────────

    @GetMapping("/rooms/{roomId}/messages")
    @Operation(summary = "Получить все сообщения комнаты")
    public List<ChatMessageResponse> getMessages(@PathVariable Long roomId, Authentication auth) {
        return chatService.getRoomMessages(roomId, currentUser(auth));
    }

    @PostMapping("/rooms/{roomId}/messages")
    @Operation(summary = "Отправить сообщение в комнату")
    public ResponseEntity<ChatMessageResponse> sendMessage(
            @PathVariable Long roomId,
            @RequestBody @Valid SendMessageRequest request,
            Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(chatService.sendMessage(roomId, request, currentUser(auth)));
    }

    @GetMapping("/rooms/{roomId}/messages/poll")
    @Operation(summary = "Опрос новых сообщений (polling) с момента sinceId")
    public List<ChatMessageResponse> pollMessages(
            @PathVariable Long roomId,
            @RequestParam(defaultValue = "0") Long sinceId,
            Authentication auth) {
        return chatService.pollMessages(roomId, sinceId, currentUser(auth));
    }

    // ─────────────────────────────────────────────

    private User currentUser(Authentication auth) {
        return userRepository.findByUsername(auth.getName()).orElseThrow();
    }
}
