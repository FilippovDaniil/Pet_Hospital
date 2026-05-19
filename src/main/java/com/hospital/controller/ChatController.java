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

/**
 * REST-контроллер чат-системы.
 *
 * <p>Поддерживает два типа чат-комнат:
 * <ul>
 *   <li><b>SUPPORT</b> — клиент обращается в службу поддержки; все администраторы
 *       видят все комнаты поддержки и могут отвечать.</li>
 *   <li><b>DOCTOR_CLIENT</b> — персональный чат клиента с конкретным врачом;
 *       третьи стороны не имеют доступа к переписке.</li>
 * </ul>
 *
 * <p>Аутентификация — JWT. Текущий пользователь извлекается из токена через
 * вспомогательный метод {@link #currentUser(Authentication)}.
 *
 * <p>Маршрут: {@code /api/chat}
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Tag(name = "Chat", description = "Чат поддержки и чат врача с пациентом")
public class ChatController {

    // ChatService содержит всю бизнес-логику: создание комнат, доступ, отправка.
    private final ChatService chatService;

    // UserRepository нужен только для преобразования имени из JWT в полный объект User.
    private final UserRepository userRepository;

    // ─────────────────────────────────────────────
    // SUPPORT CHAT
    // ─────────────────────────────────────────────

    /**
     * Возвращает существующую комнату поддержки клиента или создаёт новую.
     *
     * <p>Используется паттерн «get-or-create»: если клиент обращается в поддержку
     * впервые, в БД появляется запись {@code chat_room} с типом SUPPORT.
     * При повторных вызовах возвращается та же комната — дублирования нет
     * (гарантирует частичный уникальный индекс {@code uq_support_room}).
     *
     * <p>Метод {@code POST}, а не {@code GET}, потому что вызов может изменять
     * состояние БД (создание комнаты). HTTP 200 — клиент не должен знать,
     * была ли комната создана или уже существовала.
     *
     * <p>Доступ: {@code ROLE_CLIENT}.
     */
    @PostMapping("/support")
    @Operation(summary = "Клиент: получить или создать комнату поддержки")
    public ResponseEntity<ChatRoomResponse> getOrCreateSupportRoom(Authentication auth) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(chatService.getOrCreateSupportRoom(currentUser(auth)));
    }

    /**
     * Возвращает список всех чат-комнат поддержки — для панели администратора.
     *
     * <p>Администратор видит обращения всех клиентов и может отвечать в любой
     * из них. Фильтрация по типу {@code SUPPORT} выполняется в сервисном слое.
     *
     * <p>Доступ: {@code ROLE_ADMIN}.
     */
    @GetMapping("/support")
    @Operation(summary = "Администратор: список всех чатов поддержки")
    public List<ChatRoomResponse> getAllSupportRooms() {
        return chatService.getAllSupportRooms();
    }

    // ─────────────────────────────────────────────
    // DOCTOR CHAT
    // ─────────────────────────────────────────────

    /**
     * Возвращает существующий чат клиента с конкретным врачом или создаёт его.
     *
     * <p>Идентификатор врача передаётся как {@code doctorUserId} — это
     * {@code users.id} (не {@code doctor.id}), потому что отправителем/получателем
     * сообщения является системный пользователь, а не медицинская запись врача.
     *
     * <p>Уникальность пары (клиент, врач) обеспечивается частичным индексом
     * {@code uq_doctor_room}: один клиент не может открыть два чата с одним врачом.
     *
     * <p>Доступ: {@code ROLE_CLIENT}.
     *
     * @param doctorUserId идентификатор учётной записи врача в таблице {@code users}
     * @param auth         JWT-аутентификация текущего клиента
     */
    @PostMapping("/doctor/{doctorUserId}")
    @Operation(summary = "Клиент: получить или создать чат с врачом")
    public ResponseEntity<ChatRoomResponse> getOrCreateDoctorRoom(
            @PathVariable Long doctorUserId,
            Authentication auth) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(chatService.getOrCreateDoctorRoom(currentUser(auth), doctorUserId));
    }

    /**
     * Возвращает список чат-комнат, в которых текущий врач является собеседником.
     *
     * <p>Врач видит только свои чаты (staff_user_id == текущий пользователь),
     * не имея доступа к чатам других врачей или комнатам поддержки.
     *
     * <p>Доступ: {@code ROLE_DOCTOR}.
     */
    @GetMapping("/doctor/rooms")
    @Operation(summary = "Врач: список чатов со своими пациентами")
    public List<ChatRoomResponse> getDoctorRooms(Authentication auth) {
        return chatService.getDoctorRooms(currentUser(auth));
    }

    // ─────────────────────────────────────────────
    // MY ROOMS (CLIENT)
    // ─────────────────────────────────────────────

    /**
     * Возвращает все чат-комнаты текущего клиента: и поддержку, и чаты с врачами.
     *
     * <p>Используется для отображения списка диалогов в клиентском портале.
     * Фильтрация по {@code client_user_id == текущий пользователь} выполняется
     * в сервисном слое.
     *
     * <p>Доступ: {@code ROLE_CLIENT}.
     */
    @GetMapping("/my-rooms")
    @Operation(summary = "Клиент: список всех своих чатов")
    public List<ChatRoomResponse> getMyRooms(Authentication auth) {
        return chatService.getMyRooms(currentUser(auth));
    }

    // ─────────────────────────────────────────────
    // MESSAGES (shared)
    // ─────────────────────────────────────────────

    /**
     * Возвращает полную историю сообщений указанной комнаты.
     *
     * <p>Перед выдачей сообщений сервис проверяет, что текущий пользователь
     * является участником комнаты (клиентом, врачом или администратором).
     * Посторонние получат 403 Forbidden.
     *
     * <p>Для первоначальной загрузки переписки при открытии чата.
     * Для последующего обновления в реальном времени используйте {@code /poll}.
     *
     * <p>Доступ: участник комнаты.
     *
     * @param roomId идентификатор чат-комнаты
     * @param auth   JWT-аутентификация запрашивающего пользователя
     */
    @GetMapping("/rooms/{roomId}/messages")
    @Operation(summary = "Получить все сообщения комнаты")
    public List<ChatMessageResponse> getMessages(@PathVariable Long roomId, Authentication auth) {
        return chatService.getRoomMessages(roomId, currentUser(auth));
    }

    /**
     * Отправляет новое сообщение в указанную чат-комнату.
     *
     * <p>Тело запроса валидируется аннотацией {@code @Valid}: поле {@code content}
     * не может быть пустым. Сервис дополнительно проверяет членство отправителя
     * в комнате.
     *
     * <p>Возвращает HTTP 201 Created с созданным сообщением, чтобы клиент мог
     * немедленно отобразить его без дополнительного запроса.
     *
     * <p>Доступ: участник комнаты.
     *
     * @param roomId  идентификатор чат-комнаты
     * @param request тело с текстом сообщения
     * @param auth    JWT-аутентификация отправителя
     */
    @PostMapping("/rooms/{roomId}/messages")
    @Operation(summary = "Отправить сообщение в комнату")
    public ResponseEntity<ChatMessageResponse> sendMessage(
            @PathVariable Long roomId,
            @RequestBody @Valid SendMessageRequest request,
            Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(chatService.sendMessage(roomId, request, currentUser(auth)));
    }

    /**
     * Возвращает только новые сообщения, появившиеся после {@code sinceId}.
     *
     * <p>Реализует механизм <b>long polling</b> / short polling для обновления
     * чата без WebSocket: клиент периодически запрашивает этот эндпоинт,
     * передавая идентификатор последнего известного сообщения. В ответ приходят
     * только более новые записи, что минимизирует трафик.
     *
     * <p>При значении {@code sinceId = 0} возвращаются все сообщения комнаты
     * (аналогично {@code GET /rooms/{roomId}/messages}).
     *
     * <p>Доступ: участник комнаты.
     *
     * @param roomId  идентификатор чат-комнаты
     * @param sinceId идентификатор последнего полученного сообщения;
     *                по умолчанию 0 (вернуть все)
     * @param auth    JWT-аутентификация запрашивающего пользователя
     */
    @GetMapping("/rooms/{roomId}/messages/poll")
    @Operation(summary = "Опрос новых сообщений (polling) с момента sinceId")
    public List<ChatMessageResponse> pollMessages(
            @PathVariable Long roomId,
            @RequestParam(defaultValue = "0") Long sinceId,
            Authentication auth) {
        return chatService.pollMessages(roomId, sinceId, currentUser(auth));
    }

    // ─────────────────────────────────────────────

    /**
     * Вспомогательный метод: преобразует имя пользователя из JWT в объект {@link User}.
     *
     * <p>Spring Security заполняет {@code Authentication.getName()} значением
     * из поля {@code sub} JWT-токена, которое совпадает с {@code User.username}.
     * Вызов {@code orElseThrow()} безопасен: если токен прошёл валидацию фильтром
     * {@code JwtAuthenticationFilter}, пользователь гарантированно существует в БД.
     *
     * @param auth объект аутентификации Spring Security
     * @return полная сущность пользователя из базы данных
     */
    private User currentUser(Authentication auth) {
        return userRepository.findByUsername(auth.getName()).orElseThrow();
    }
}
